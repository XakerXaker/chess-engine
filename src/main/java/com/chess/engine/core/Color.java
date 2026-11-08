package com.chess.engine.core;

public final class Color {
    public static final int WHITE = 0;
    public static final int BLACK = 1;
    public static final int NONE = -1;

    private Color() {}

    public static int opposite(int color) {
        return color ^ 1;
    }

    public static String toString(int color) {
        return color == WHITE ? "White" : (color == BLACK ? "Black" : "None");
    }
}
