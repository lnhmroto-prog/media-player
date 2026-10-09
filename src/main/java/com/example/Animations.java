package com.example;

import java.util.Locale;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

public final class Animations {

    private static final String SCALE_KEY = "scale-transition";
    private static final String SLIDE_KEY = "smooth-slider-timeline";

    private Animations() {
    }

    public static void installHover(Node node) {
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> scaleTo(node, 1.1));
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> scaleTo(node, 1.0));
        node.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> scaleTo(node, 0.92));
        node.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> scaleTo(node, node.isHover() ? 1.1 : 1.0));
    }

    private static void scaleTo(Node node, double target) {
        ScaleTransition transition = (ScaleTransition) node.getProperties()
                .computeIfAbsent(SCALE_KEY, key -> new ScaleTransition(Duration.millis(120), node));
        transition.stop();
        transition.setToX(target);
        transition.setToY(target);
        transition.playFromStart();
    }

    public static void fadeIn(Node node) {
        FadeTransition fade = new FadeTransition(Duration.millis(320), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    public static void fadeOut(Node node, Runnable afterwards) {
        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            afterwards.run();
            node.setOpacity(1);
        });
        fade.play();
    }

    public static void smoothSliderTo(Slider slider, double target) {
        Timeline running = (Timeline) slider.getProperties().get(SLIDE_KEY);
        if (running != null) {
            running.stop();
        }
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(160),
                new KeyValue(slider.valueProperty(), target, Interpolator.EASE_BOTH)));
        slider.getProperties().put(SLIDE_KEY, timeline);
        timeline.play();
    }

    
    public static void installFill(Slider slider) {
        Runnable update = () -> {
            Node track = slider.lookup(".track");
            if (track == null) {
                return;
            }
            double range = slider.getMax() - slider.getMin();
            double percent = range <= 0 ? 0 : (slider.getValue() - slider.getMin()) / range * 100;
            percent = Math.max(0, Math.min(100, percent));
            track.setStyle(String.format(Locale.ROOT,
                    "-fx-background-color: linear-gradient(to right, -accent-blue 0%%, "
                            + "-accent-lavender %.2f%%, -track-color %.2f%%, -track-color 100%%);",
                    percent, percent));
        };
        slider.valueProperty().addListener((obs, oldValue, newValue) -> update.run());
        slider.minProperty().addListener((obs, oldValue, newValue) -> update.run());
        slider.maxProperty().addListener((obs, oldValue, newValue) -> update.run());
        slider.skinProperty().addListener((obs, oldSkin, newSkin) -> update.run());
    }
}
