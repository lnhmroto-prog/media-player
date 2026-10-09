package com.example;

import java.io.File;
import java.util.function.Consumer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

public class PlayerService {

    private final ObjectProperty<MediaPlayer> mediaPlayer = new SimpleObjectProperty<>();
    private final BooleanProperty playing = new SimpleBooleanProperty(false);
    private final BooleanProperty hasVideo = new SimpleBooleanProperty(false);
    private final BooleanProperty muted = new SimpleBooleanProperty(false);
    private final DoubleProperty volume = new SimpleDoubleProperty(0.5);
    private final ObjectProperty<Duration> currentTime = new SimpleObjectProperty<>(Duration.ZERO);
    private final ObjectProperty<Duration> totalDuration = new SimpleObjectProperty<>(Duration.ZERO);

    private Runnable onEnded = () -> { };
    private Consumer<String> onError = message -> { };

    public void load(File file) {
        disposeCurrent();
        try {
            Media media = new Media(file.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);

            player.volumeProperty().bind(volume);
            player.muteProperty().bind(muted);

            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> currentTime.set(newTime));
            player.statusProperty().addListener(
                    (obs, oldStatus, newStatus) -> playing.set(newStatus == MediaPlayer.Status.PLAYING));

            player.setOnReady(() -> {
                totalDuration.set(player.getTotalDuration());
                hasVideo.set(media.getWidth() > 0 && media.getHeight() > 0);
            });
            player.setOnEndOfMedia(() -> onEnded.run());
            player.setOnError(() -> reportError(file, player.getError()));
            media.setOnError(() -> reportError(file, media.getError()));

            mediaPlayer.set(player);
            player.play();
        } catch (MediaException | IllegalArgumentException e) {
            reportError(file, e);
        }
    }

    public void unload() {
        disposeCurrent();
    }

    private void disposeCurrent() {
        MediaPlayer old = mediaPlayer.get();
        mediaPlayer.set(null);
        if (old != null) {
            old.stop();
            old.dispose();
        }
        playing.set(false);
        hasVideo.set(false);
        currentTime.set(Duration.ZERO);
        totalDuration.set(Duration.ZERO);
    }

    private void reportError(File file, Exception error) {
        String reason = (error != null && error.getMessage() != null) ? error.getMessage() : "unknown error";
        onError.accept("Could not play " + file.getName() + " (" + reason + ")");
    }

    public void play() {
        MediaPlayer player = mediaPlayer.get();
        if (player != null) {
            player.play();
        }
    }

    public void pause() {
        MediaPlayer player = mediaPlayer.get();
        if (player != null) {
            player.pause();
        }
    }

    public void togglePlay() {
        MediaPlayer player = mediaPlayer.get();
        if (player == null) {
            return;
        }
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
        } else {
            player.play();
        }
    }

    public void stop() {
        MediaPlayer player = mediaPlayer.get();
        if (player != null) {
            player.stop();
            currentTime.set(Duration.ZERO);
        }
    }

    public void seek(Duration target) {
        MediaPlayer player = mediaPlayer.get();
        if (player != null) {
            player.seek(target);
        }
    }

    public void seekBy(double seconds) {
        MediaPlayer player = mediaPlayer.get();
        if (player == null) {
            return;
        }
        Duration target = player.getCurrentTime().add(Duration.seconds(seconds));
        Duration total = player.getTotalDuration();
        if (target.lessThan(Duration.ZERO)) {
            target = Duration.ZERO;
        }
        if (!total.isUnknown() && !total.isIndefinite() && target.greaterThan(total)) {
            target = total;
        }
        player.seek(target);
    }

    public void changeVolume(double delta) {
        double value = Math.max(0.0, Math.min(1.0, volume.get() + delta));
        volume.set(Math.round(value * 100) / 100.0);
        if (delta > 0 && muted.get()) {
            muted.set(false);
        }
    }

    public void setVolume(double value) {
        volume.set(Math.max(0.0, Math.min(1.0, value)));
        if (value > 0 && muted.get()) {
            muted.set(false);
        }
    }

    public void toggleMute() {
        muted.set(!muted.get());
    }

    public void setOnEnded(Runnable onEnded) {
        this.onEnded = onEnded;
    }

    public void setOnError(Consumer<String> onError) {
        this.onError = onError;
    }

    public ReadOnlyObjectProperty<MediaPlayer> mediaPlayerProperty() {
        return mediaPlayer;
    }

    public ReadOnlyBooleanProperty playingProperty() {
        return playing;
    }

    public ReadOnlyBooleanProperty hasVideoProperty() {
        return hasVideo;
    }

    public ReadOnlyBooleanProperty mutedProperty() {
        return muted;
    }

    public ReadOnlyDoubleProperty volumeProperty() {
        return volume;
    }

    public ReadOnlyObjectProperty<Duration> currentTimeProperty() {
        return currentTime;
    }

    public ReadOnlyObjectProperty<Duration> totalDurationProperty() {
        return totalDuration;
    }
}
