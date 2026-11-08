package com.chess.engine.core;

public final class Piece {
    public static final int NONE = 0;
    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    private Piece() {}

    public static char toChar(int pieceType, int color) {
        char c = switch (pieceType) {
            case PAWN -> 'P';
            case KNIGHT -> 'N';
            case BISHOP -> 'B';
            case ROOK -> 'R';
            case QUEEN -> 'Q';
            case KING -> 'K';
            default -> '.';
        };
        return color == Color.BLACK ? Character.toLowerCase(c) : c;
    }

    public static int typeFromChar(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'P' -> PAWN;
            case 'N' -> KNIGHT;
            case 'B' -> BISHOP;
            case 'R' -> ROOK;
            case 'Q' -> QUEEN;
            case 'K' -> KING;
            default -> NONE;
        };
    }

    public static int colorFromChar(char c) {
        if (c >= 'A' && c <= 'Z') return Color.WHITE;
        if (c >= 'a' && c <= 'z') return Color.BLACK;
        return Color.NONE;
    }
}
