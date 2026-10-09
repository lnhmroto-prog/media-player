package com.example;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    private static final String APP_TITLE = "Media Player";
    private static final String EMPTY_HINT = "Drop media files here\nor press Ctrl+O to add some";
    private static final String KEY_HINTS = "Space: play/pause   S: stop   N / P: next / previous   "
            + "\u2191 / \u2193: volume   M: mute   \u2190 / \u2192: seek 5s   F: fullscreen   "
            + "Ctrl+O: add   Delete: remove";

    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of("mp3", "mp4", "m4a", "m4v", "wav", "aif", "aiff", "flv");
    private static final List<String> SUPPORTED_PATTERNS =
            SUPPORTED_EXTENSIONS.stream().map(ext -> "*." + ext).toList();

    private static final String BASE_CSS = "/style.css";
    private static final String PREF_DARK = "darkTheme";

    private static final double VOLUME_STEP = 0.05;
    private static final double SEEK_SECONDS = 5;
    private static final double ART_SIZE = 44;

    private final PlayerService service = new PlayerService();
    private final ObservableList<TrackInfo> playlist = FXCollections.observableArrayList();
    private final Deque<TrackInfo> queue = new ArrayDeque<>();
    private final Map<KeyCode, Runnable> shortcuts = new EnumMap<>(KeyCode.class);
    private final Map<TrackInfo, TrackCell> displayedCells = new HashMap<>();
    private final Preferences prefs = Preferences.userNodeForPackage(App.class);
    private MetadataLoader loader;

    private Stage stage;
    private Scene fullScene;
    private Scene miniScene;
    private boolean miniMode;
    private boolean dark;
    private double savedWidth = 1100;
    private double savedHeight = 680;
    private File lastDirectory;

    private ListView<TrackInfo> playlistView;
    private MultipleSelectionModel<TrackInfo> selection;
    private VBox playlistPanel;
    private Label placeholderLabel;
    private Label statusLabel;
    private Label currentTimeLabel;
    private Label totalTimeLabel;
    private Label volumeLabel;
    private Label miniTitleLabel;
    private Slider seekSlider;
    private Button fullscreenButton;
    private Button miniPlayPause;
    private final List<Button> volumeButtons = new ArrayList<>();
    private final List<Slider> volumeSliders = new ArrayList<>();
    private final List<Button> themeButtons = new ArrayList<>();

    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        loadFonts();
        dark = prefs.getBoolean(PREF_DARK, false);
        loader = new MetadataLoader(this::refreshPlaylist);

        fullScene = new Scene(buildFullRoot(), 1100, 680);
        miniScene = new Scene(buildMiniRoot());

        registerShortcuts();
        for (Scene scene : List.of(fullScene, miniScene)) {
            // A *filter* sees key presses before the focused control does, so the
            // shortcuts work whatever has focus (a ListView would swallow Up/Down/Space).
            scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKey);
        }
        applyTheme();
        wireServiceToUi();

        stage.fullScreenProperty().addListener((obs, was, isFull) -> onFullscreenChanged(isFull));
        stage.setFullScreenExitHint("Press F or Esc to leave fullscreen");
        stage.setTitle(APP_TITLE);
        stage.setMinWidth(820);
        stage.setMinHeight(520);
        stage.setScene(fullScene);
        stage.show();
    }

    @Override
    public void stop() {
        service.unload();
    }

    private void loadFonts() {
        for (String name : List.of("Nunito-Regular.ttf", "Nunito-Bold.ttf", "Poppins-Regular.ttf",
                "Poppins-SemiBold.ttf", "Quicksand-Regular.ttf", "Quicksand-Bold.ttf")) {
            try (InputStream in = getClass().getResourceAsStream("/fonts/" + name)) {
                if (in != null) {
                    Font.loadFont(in, 13);
                }
            } catch (IOException ignored) {
                // font is optional: CSS falls back to the next family in its list
            }
        }
    }

    private void applyTheme() {
        String base = getClass().getResource(BASE_CSS).toExternalForm();
        String theme = getClass().getResource(dark ? "/theme-dark.css" : "/theme-light.css").toExternalForm();
        for (Scene scene : List.of(fullScene, miniScene)) {
            scene.getStylesheets().setAll(base, theme);
        }
        for (Button button : themeButtons) {
            button.setGraphic(dark ? MediaIcons.sun() : MediaIcons.moon());
        }
        prefs.putBoolean(PREF_DARK, dark);
    }

    private void toggleTheme() {
        dark = !dark;
        applyTheme();
    }

    private BorderPane buildFullRoot() {
        StackPane videoArea = buildVideoArea();
        playlistPanel = buildPlaylistPanel();
        VBox controlBar = buildControlBar();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setCenter(videoArea);
        root.setRight(playlistPanel);
        root.setBottom(controlBar);
        BorderPane.setMargin(playlistPanel, new Insets(0, 0, 0, 16));
        BorderPane.setMargin(controlBar, new Insets(14, 0, 0, 0));

        root.setOnDragOver(this::handleDragOver);
        root.setOnDragDropped(this::handleDragDropped);
        return root;
    }

    private StackPane buildVideoArea() {
        MediaView mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.mediaPlayerProperty().bind(service.mediaPlayerProperty());

        placeholderLabel = new Label(EMPTY_HINT);
        placeholderLabel.getStyleClass().add("hint-label");
        placeholderLabel.setTextAlignment(TextAlignment.CENTER);
        placeholderLabel.setWrapText(true);
        placeholderLabel.setMaxWidth(420);
        placeholderLabel.visibleProperty().bind(service.hasVideoProperty().not());

        StackPane pane = new StackPane(placeholderLabel, mediaView);
        pane.getStyleClass().add("video-pane");
        pane.setPrefSize(720, 405);
        pane.setMinSize(0, 0); // lets the video shrink when the window shrinks

        mediaView.fitWidthProperty().bind(pane.widthProperty());
        mediaView.fitHeightProperty().bind(pane.heightProperty());
        return pane;
    }

    private VBox buildPlaylistPanel() {
        Label title = new Label("Playlist");
        title.getStyleClass().add("panel-title");

        playlistView = new ListView<>(playlist);
        playlistView.setPlaceholder(new Label("Playlist is empty"));
        playlistView.setCellFactory(listView -> new TrackCell());
        VBox.setVgrow(playlistView, Priority.ALWAYS);

        selection = playlistView.getSelectionModel();
        selection.selectedItemProperty().addListener((obs, oldTrack, newTrack) -> onSelectionChanged(newTrack));

        Button addButton = textButton("Add files", "Add files (Ctrl+O)", this::chooseFiles);
        Button removeButton = textButton("Remove", "Remove selected (Delete)", this::removeSelected);

        VBox panel = new VBox(10, title, playlistView, new HBox(8, addButton, removeButton));
        panel.setPrefWidth(300);
        panel.setMinWidth(240);
        return panel;
    }

    private VBox buildControlBar() {
        currentTimeLabel = new Label("00:00");
        currentTimeLabel.getStyleClass().add("time-label");
        totalTimeLabel = new Label("--:--");
        totalTimeLabel.getStyleClass().add("time-label");

        seekSlider = new Slider(0, 1, 0);
        seekSlider.setDisable(true);
        seekSlider.setFocusTraversable(false);
        Animations.installFill(seekSlider);
        HBox.setHgrow(seekSlider, Priority.ALWAYS);

        HBox seekRow = new HBox(10, currentTimeLabel, seekSlider, totalTimeLabel);
        seekRow.setAlignment(Pos.CENTER);

        // Row 2: transport buttons, volume, window controls
        Button previous = iconButton(MediaIcons.previous(), "Previous (P)", () -> selectRelative(-1, true));
        Button play = iconButton(MediaIcons.play(), "Play (Space)", service::play);
        play.getStyleClass().add("play-button");
        Button pause = iconButton(MediaIcons.pause(), "Pause (Space)", service::pause);
        Button stopButton = iconButton(MediaIcons.stop(), "Stop (S)", service::stop);
        Button next = iconButton(MediaIcons.next(), "Next (N)", () -> playNext(true));

        Button volumeButton = volumeButton();
        Slider volumeSlider = volumeSlider(110);
        volumeLabel = new Label();
        volumeLabel.getStyleClass().add("time-label");
        volumeLabel.setMinWidth(40);

        fullscreenButton = iconButton(MediaIcons.fullscreenEnter(), "Fullscreen (F)", this::toggleFullscreen);
        Button miniButton = iconButton(MediaIcons.miniPlayer(), "Mini player", () -> setMiniMode(true));
        Button themeButton = themeButton();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(10, previous, play, pause, stopButton, next, spacer,
                volumeButton, volumeSlider, volumeLabel, fullscreenButton, miniButton, themeButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Add some media to get started.");
        statusLabel.getStyleClass().add("status-label");

        VBox bar = new VBox(10, seekRow, controls, statusLabel);
        bar.getStyleClass().add("control-bar");
        return bar;
    }


    private HBox buildMiniRoot() {
        Button previous = iconButton(MediaIcons.previous(), "Previous (P)", () -> selectRelative(-1, true));
        miniPlayPause = iconButton(MediaIcons.play(), "Play / pause (Space)", service::togglePlay);
        miniPlayPause.getStyleClass().add("play-button");
        Button next = iconButton(MediaIcons.next(), "Next (N)", () -> playNext(true));

        miniTitleLabel = new Label("Nothing playing");
        miniTitleLabel.getStyleClass().add("track-name");
        miniTitleLabel.setMinWidth(0);
        miniTitleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(miniTitleLabel, Priority.ALWAYS);

        Button volumeButton = volumeButton();
        Slider volumeSlider = volumeSlider(90);
        Button themeButton = themeButton();
        Button expand = iconButton(MediaIcons.expand(), "Back to full player", () -> setMiniMode(false));

        HBox bar = new HBox(10, previous, miniPlayPause, next, miniTitleLabel,
                volumeButton, volumeSlider, themeButton, expand);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 16, 14, 16));
        bar.setPrefSize(620, 76);
        bar.getStyleClass().add("mini-bar");

        bar.setOnDragOver(this::handleDragOver);
        bar.setOnDragDropped(this::handleDragDropped);
        return bar;
    }

    private void setMiniMode(boolean mini) {
        if (mini == miniMode) {
            return;
        }
        if (mini && stage.isFullScreen()) {
            stage.setFullScreen(false);
        }
        miniMode = mini;
        if (mini) {
            savedWidth = stage.getWidth();
            savedHeight = stage.getHeight();
            stage.setMinWidth(500);
            stage.setMinHeight(0);
            stage.setScene(miniScene);
            stage.sizeToScene();
            stage.setAlwaysOnTop(true);
        } else {
            stage.setAlwaysOnTop(false);
            stage.setMinWidth(820);
            stage.setMinHeight(520);
            stage.setScene(fullScene);
            stage.setWidth(savedWidth);
            stage.setHeight(savedHeight);
        }
    }


    private Button iconButton(Node icon, String tooltip, Runnable action) {
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setOnAction(event -> action.run());
        Animations.installHover(button);
        return button;
    }

    private Button textButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("text-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setOnAction(event -> action.run());
        Animations.installHover(button);
        return button;
    }

    private Button volumeButton() {
        Button button = iconButton(MediaIcons.volume(0.5, false), "Mute / unmute (M)", service::toggleMute);
        volumeButtons.add(button);
        return button;
    }

    private Slider volumeSlider(double width) {
        Slider slider = new Slider(0, 1, service.volumeProperty().get());
        slider.setPrefWidth(width);
        slider.setFocusTraversable(false);
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (slider.isValueChanging() || slider.isPressed()) {
                service.setVolume(newValue.doubleValue());
            }
        });
        Animations.installFill(slider);
        volumeSliders.add(slider);
        return slider;
    }

    private Button themeButton() {
        Button button = iconButton(dark ? MediaIcons.sun() : MediaIcons.moon(), "Switch light / dark theme",
                this::toggleTheme);
        themeButtons.add(button);
        return button;
    }


    private void wireServiceToUi() {
        service.setOnEnded(this::handleEndOfMedia);
        service.setOnError(this::setStatus);

        service.volumeProperty().addListener((obs, oldValue, newValue) -> refreshVolumeUi());
        service.mutedProperty().addListener((obs, oldValue, newValue) -> refreshVolumeUi());
        refreshVolumeUi();

        service.playingProperty().addListener((obs, was, isPlaying) ->
                miniPlayPause.setGraphic(isPlaying ? MediaIcons.pause() : MediaIcons.play()));

        service.totalDurationProperty().addListener((obs, oldTotal, total) -> {
            boolean known = total != null && !total.isUnknown() && !total.isIndefinite() && total.toSeconds() > 0;
            seekSlider.setDisable(!known);
            seekSlider.setMax(known ? total.toSeconds() : 1);
            totalTimeLabel.setText(known ? format(total) : "--:--");
        });

        service.currentTimeProperty().addListener((obs, oldTime, time) -> {
            currentTimeLabel.setText(format(time));
            // Don't fight the user while they are dragging the slider.
            if (!seekSlider.isValueChanging() && !seekSlider.isPressed()) {
                seekSlider.setValue(time.toSeconds());
            }
        });

        seekSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (seekSlider.isValueChanging() || seekSlider.isPressed()) {
                service.seek(Duration.seconds(newValue.doubleValue()));
            }
        });
    }

    private void refreshVolumeUi() {
        double volume = service.volumeProperty().get();
        boolean muted = service.mutedProperty().get();

        for (Button button : volumeButtons) {
            button.setGraphic(MediaIcons.volume(volume, muted));
        }
        for (Slider slider : volumeSliders) {
            if (!slider.isValueChanging() && !slider.isPressed()) {
                Animations.smoothSliderTo(slider, volume);
            }
        }
        volumeLabel.setText(muted ? "Muted" : Math.round(volume * 100) + "%");
    }

    private void onFullscreenChanged(boolean isFull) {
        fullscreenButton.setGraphic(isFull ? MediaIcons.fullscreenExit() : MediaIcons.fullscreenEnter());
        // Give the video the whole screen.
        playlistPanel.setVisible(!isFull);
        playlistPanel.setManaged(!isFull);
    }

    private void toggleFullscreen() {
        if (!miniMode) {
            stage.setFullScreen(!stage.isFullScreen());
        }
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void refreshPlaylist() {
        playlistView.refresh();
    }

    private void onSelectionChanged(TrackInfo track) {
        if (track == null) {
            service.unload();
            placeholderLabel.setText(EMPTY_HINT);
            miniTitleLabel.setText("Nothing playing");
            stage.setTitle(APP_TITLE);
            return;
        }
        if (queue.remove(track)) {
            refreshPlaylist(); 
        }
        String name = track.getFileName();
        placeholderLabel.setText("\u266A  " + name);
        miniTitleLabel.setText(name);
        stage.setTitle(name + " - " + APP_TITLE);
        setStatus("Now playing: " + name);
        service.load(track.getFile());
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add media files");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Media files", SUPPORTED_PATTERNS));
        if (lastDirectory != null && lastDirectory.isDirectory()) {
            chooser.setInitialDirectory(lastDirectory);
        }

        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            lastDirectory = files.get(0).getParentFile();
            addFiles(files);
        }
    }

    private void addFiles(List<File> files) {
        boolean wasEmpty = playlist.isEmpty();
        int added = 0;
        int skipped = 0;
        for (File file : files) {
            if (isSupported(file) && !isInPlaylist(file)) {
                TrackInfo track = new TrackInfo(file);
                playlist.add(track);
                loader.enqueue(track);
                added++;
            } else {
                skipped++;
            }
        }

        String message = "Added " + added + (added == 1 ? " file" : " files");
        if (skipped > 0) {
            message += " (skipped " + skipped + ": unsupported type or already in the playlist)";
        }
        setStatus(message);

        if (wasEmpty && added > 0) {
            selection.selectFirst();
        }
    }

    private boolean isInPlaylist(File file) {
        return playlist.stream().anyMatch(track -> track.getFile().equals(file));
    }

    private void removeSelected() {
        TrackInfo track = selection.getSelectedItem();
        if (track == null) {
            setStatus("Select a file in the playlist first.");
            return;
        }
        removeTrack(track);
    }

    private void removeTrack(TrackInfo track) {
        Runnable remove = () -> {
            if (!playlist.contains(track)) {
                return; 
            }
            queue.remove(track);
            playlist.remove(track);
            refreshPlaylist();
            setStatus("Removed " + track.getFileName());
        };
        TrackCell cell = displayedCells.get(track);
        if (cell != null) {
            Animations.fadeOut(cell, remove);
        } else {
            remove.run();
        }
    }

    private void addToQueue(TrackInfo track) {
        if (track == selection.getSelectedItem()) {
            setStatus("That track is already playing.");
        } else if (queue.contains(track)) {
            setStatus("Already in the queue: " + track.getFileName());
        } else {
            queue.add(track);
            refreshPlaylist();
            setStatus("Queued: " + track.getFileName());
        }
    }

    private int queuePosition(TrackInfo track) {
        int position = 1;
        for (TrackInfo queued : queue) {
            if (queued == track) {
                return position;
            }
            position++;
        }
        return 0;
    }

    private void playNext(boolean wrap) {
        TrackInfo queued = queue.poll();
        if (queued != null) {
            selection.select(queued);
            playlistView.scrollTo(queued);
            refreshPlaylist();
        } else {
            selectRelative(1, wrap);
        }
    }

    private void selectRelative(int step, boolean wrap) {
        int size = playlist.size();
        if (size == 0) {
            return;
        }
        int current = selection.getSelectedIndex();
        int target = (current < 0) ? (step > 0 ? 0 : size - 1) : current + step;
        if (wrap) {
            target = Math.floorMod(target, size);
        } else if (target < 0 || target >= size) {
            return;
        }
        selection.select(target);
        playlistView.scrollTo(target);
    }

    private void handleEndOfMedia() {
        int index = selection.getSelectedIndex();
        if (!queue.isEmpty() || (index >= 0 && index < playlist.size() - 1)) {
            playNext(false);
        } else {
            service.stop();
            setStatus("Reached the end of the playlist.");
        }
    }

    private static boolean isSupported(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(ext -> name.endsWith("." + ext));
    }

    private void registerShortcuts() {
        shortcuts.put(KeyCode.SPACE, service::togglePlay);
        shortcuts.put(KeyCode.S, service::stop);
        shortcuts.put(KeyCode.N, () -> playNext(true));
        shortcuts.put(KeyCode.P, () -> selectRelative(-1, true));
        shortcuts.put(KeyCode.UP, () -> service.changeVolume(VOLUME_STEP));
        shortcuts.put(KeyCode.DOWN, () -> service.changeVolume(-VOLUME_STEP));
        shortcuts.put(KeyCode.M, service::toggleMute);
        shortcuts.put(KeyCode.RIGHT, () -> service.seekBy(SEEK_SECONDS));
        shortcuts.put(KeyCode.LEFT, () -> service.seekBy(-SEEK_SECONDS));
        shortcuts.put(KeyCode.F, this::toggleFullscreen);
        shortcuts.put(KeyCode.DELETE, this::removeSelected);
    }

    private void handleKey(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.O) {
            chooseFiles();
            event.consume();
            return;
        }
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }

        Runnable action = shortcuts.get(event.getCode());
        if (action != null) {
            action.run();
            event.consume();
        }
    }


    private void handleDragOver(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        boolean hasFiles = dragboard.hasFiles();
        if (hasFiles) {
            addFiles(dragboard.getFiles());
        }
        event.setDropCompleted(hasFiles);
        event.consume();
    }

    private static String format(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()) {
            return "--:--";
        }
        int total = (int) Math.floor(duration.toSeconds());
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    private final class TrackCell extends ListCell<TrackInfo> {

        private final ImageView artView = new ImageView();
        private final Node noteIcon = MediaIcons.musicNote();
        private final Label nameLabel = new Label();
        private final Label detailLabel = new Label();
        private final Label durationLabel = new Label();
        private final Label queueBadge = new Label();
        private final HBox content;
        private final ContextMenu menu = new ContextMenu();
        private TrackInfo shown;

        TrackCell() {
            artView.setFitWidth(ART_SIZE);
            artView.setFitHeight(ART_SIZE);
            artView.setPreserveRatio(false);

            StackPane artBox = new StackPane(noteIcon, artView);
            artBox.getStyleClass().add("art-box");
            artBox.setMinSize(ART_SIZE, ART_SIZE);
            artBox.setPrefSize(ART_SIZE, ART_SIZE);
            artBox.setMaxSize(ART_SIZE, ART_SIZE);
            Rectangle clip = new Rectangle(ART_SIZE, ART_SIZE);
            clip.setArcWidth(20);
            clip.setArcHeight(20);
            artBox.setClip(clip);

            nameLabel.getStyleClass().add("track-name");
            detailLabel.getStyleClass().add("track-detail");
            VBox texts = new VBox(2, nameLabel, detailLabel);
            texts.setAlignment(Pos.CENTER_LEFT);
            texts.setMinWidth(0);
            HBox.setHgrow(texts, Priority.ALWAYS);

            durationLabel.getStyleClass().add("track-detail");
            queueBadge.getStyleClass().add("queue-badge");
            VBox right = new VBox(4, durationLabel, queueBadge);
            right.setAlignment(Pos.CENTER_RIGHT);

            content = new HBox(10, artBox, texts, right);
            content.setAlignment(Pos.CENTER_LEFT);

            MenuItem queueItem = new MenuItem("Add to Queue");
            queueItem.setOnAction(event -> {
                if (getItem() != null) {
                    addToQueue(getItem());
                }
            });
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(event -> {
                if (getItem() != null) {
                    removeTrack(getItem());
                }
            });
            menu.getItems().addAll(queueItem, deleteItem);

            setPrefWidth(0);
        }

        @Override
        protected void updateItem(TrackInfo track, boolean empty) {
            super.updateItem(track, empty);

            if (shown != null && displayedCells.get(shown) == this) {
                displayedCells.remove(shown);
            }
            shown = empty ? null : track;

            if (empty || track == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            displayedCells.put(track, this);

            nameLabel.setText(track.getFileName());
            detailLabel.setText(track.getAlbum().isBlank()
                    ? track.getArtist()
                    : track.getArtist() + " \u00B7 " + track.getAlbum());
            durationLabel.setText(format(track.getDuration()));

            Image art = track.getAlbumArt();
            artView.setImage(art);
            artView.setVisible(art != null);
            noteIcon.setVisible(art == null);

            int position = queuePosition(track);
            queueBadge.setText("Up next #" + position);
            queueBadge.setVisible(position > 0);
            queueBadge.setManaged(position > 0);

            setText(null);
            setGraphic(content);
            setContextMenu(menu);

            if (track.consumeFresh()) {
                Animations.fadeIn(this);
            }
        }
    }
}
