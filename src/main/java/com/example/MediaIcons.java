package com.example;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;

public final class MediaIcons {

    private static final double SIZE = 24;

    private static final String SPEAKER = "M2 9 L6 9 L11 5 L11 19 L6 15 L2 15 Z";
    private static final String WAVE_1 = "M14 9.5 Q16 12 14 14.5";
    private static final String WAVE_2 = "M16.5 7 Q20 12 16.5 17";
    private static final String WAVE_3 = "M19 4.5 Q23.5 12 19 19.5";

    private MediaIcons() {
    }

    public static Node play() {
        return wrap(filled(path("M8 5 L19 12 L8 19 Z")));
    }

    public static Node pause() {
        Rectangle left = new Rectangle(6, 5, 4, 14);
        Rectangle right = new Rectangle(14, 5, 4, 14);
        for (Rectangle bar : List.of(left, right)) {
            bar.setArcWidth(2);
            bar.setArcHeight(2);
        }
        return wrap(filled(left), filled(right));
    }

    public static Node stop() {
        Rectangle square = new Rectangle(6, 6, 12, 12);
        square.setArcWidth(4);
        square.setArcHeight(4);
        return wrap(filled(square));
    }

    public static Node next() {
        return wrap(filled(path("M6 5 L16 12 L6 19 Z M17 5 L19.5 5 L19.5 19 L17 19 Z")));
    }

    public static Node previous() {
        return wrap(filled(path("M18 5 L8 12 L18 19 Z M7 5 L4.5 5 L4.5 19 L7 19 Z")));
    }

    public static Node volume(double volume, boolean muted) {
        SVGPath speaker = filled(path(SPEAKER));
        if (muted || volume <= 0) {
            return wrap(speaker, stroked(new Line(15, 9, 21, 15)), stroked(new Line(21, 9, 15, 15)));
        }
        List<Node> parts = new ArrayList<>();
        parts.add(speaker);
        parts.add(stroked(path(WAVE_1)));
        if (volume >= 0.34) {
            parts.add(stroked(path(WAVE_2)));
        }
        if (volume >= 0.67) {
            parts.add(stroked(path(WAVE_3)));
        }
        return wrap(parts.toArray(new Node[0]));
    }

    public static Node fullscreenEnter() {
        return wrap(stroked(path("M4 9 L4 4 L9 4 M15 4 L20 4 L20 9 M20 15 L20 20 L15 20 M9 20 L4 20 L4 15")));
    }

    public static Node fullscreenExit() {
        return wrap(stroked(path("M9 4 L9 9 L4 9 M15 4 L15 9 L20 9 M20 15 L15 15 L15 20 M4 15 L9 15 L9 20")));
    }

    public static Node miniPlayer() {
        Rectangle window = new Rectangle(3, 5, 18, 14);
        window.setArcWidth(4);
        window.setArcHeight(4);
        Rectangle mini = new Rectangle(12, 12, 7, 5);
        mini.setArcWidth(2);
        mini.setArcHeight(2);
        return wrap(stroked(window), filled(mini));
    }

    public static Node expand() {
        return wrap(stroked(path("M14 4 L20 4 L20 10 M20 4 L13 11 M10 20 L4 20 L4 14 M4 20 L11 13")));
    }

    public static Node moon() {
        return wrap(stroked(path("M21 12.79 A9 9 0 1 1 11.21 3 A7 7 0 0 0 21 12.79 Z")));
    }

    public static Node sun() {
        SVGPath rays = path("M12 2 L12 4 M12 20 L12 22 M2 12 L4 12 M20 12 L22 12 "
                + "M4.9 4.9 L6.3 6.3 M17.7 17.7 L19.1 19.1 M4.9 19.1 L6.3 17.7 M17.7 6.3 L19.1 4.9");
        return wrap(stroked(new Circle(12, 12, 4)), stroked(rays));
    }

    public static Node musicNote() {
        return wrap(stroked(path("M9 18 L9 6 L19 4 L19 16")),
                filled(new Circle(7, 18, 2.5)),
                filled(new Circle(17, 16, 2.5)));
    }

    private static SVGPath path(String content) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        return path;
    }

    private static <T extends Shape> T filled(T shape) {
        shape.getStyleClass().add("icon");
        return shape;
    }

    private static <T extends Shape> T stroked(T shape) {
        shape.getStyleClass().add("icon-line");
        return shape;
    }

    private static Node wrap(Node... shapes) {
        Rectangle frame = new Rectangle(SIZE, SIZE);
        frame.setFill(Color.TRANSPARENT);
        Group group = new Group(frame);
        group.getChildren().addAll(shapes);

        StackPane box = new StackPane(group);
        box.setMinSize(SIZE, SIZE);
        box.setPrefSize(SIZE, SIZE);
        box.setMaxSize(SIZE, SIZE);
        box.setMouseTransparent(true);
        return box;
    }
}
