package com.example;

import java.io.File;

import javafx.scene.image.Image;
import javafx.util.Duration;

public class TrackInfo {

    private final File file;
    private String artist = "Unknown artist";
    private String album = "";
    private Duration duration = Duration.UNKNOWN;
    private Image albumArt;
    private boolean fresh = true;

    public TrackInfo(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return file.getName();
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Image getAlbumArt() {
        return albumArt;
    }

    public void setAlbumArt(Image albumArt) {
        this.albumArt = albumArt;
    }

    public boolean consumeFresh() {
        boolean wasFresh = fresh;
        fresh = false;
        return wasFresh;
    }
}
