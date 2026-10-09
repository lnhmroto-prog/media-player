package com.example;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

final class MetadataLoader {

    private final Deque<TrackInfo> pending = new ArrayDeque<>();
    private final Runnable onUpdate;
    private MediaPlayer current;

    MetadataLoader(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    void enqueue(TrackInfo track) {
        pending.add(track);
        startNext();
    }

    private void startNext() {
        if (current != null || pending.isEmpty()) {
            return;
        }
        TrackInfo track = pending.poll();
        try {
            Media media = new Media(track.getFile().toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            current = player;
            player.setOnReady(() -> {
                read(track, media);
                finish(player);
            });
            player.setOnError(() -> finish(player));
            media.setOnError(() -> finish(player));
        } catch (RuntimeException e) {
            current = null;
            startNext(); 
        }
    }

    private void read(TrackInfo track, Media media) {
        Map<String, Object> metadata = media.getMetadata();
        if (metadata.get("artist") instanceof String artist && !artist.isBlank()) {
            track.setArtist(artist);
        }
        if (metadata.get("album") instanceof String album && !album.isBlank()) {
            track.setAlbum(album);
        }
        if (metadata.get("image") instanceof Image image) {
            track.setAlbumArt(image);
        }
        Duration duration = media.getDuration();
        if (duration != null) {
            track.setDuration(duration);
        }
    }

    private void finish(MediaPlayer player) {
        Platform.runLater(() -> {
            if (player != current) {
                return; 
            }
            current = null;
            player.dispose();
            onUpdate.run();
            startNext();
        });
    }
}
