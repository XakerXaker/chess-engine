package com.chess.engine.core;

public final class Square {
    public static final int NONE = -1;

    public static final int A1 = 0, B1 = 1, C1 = 2, D1 = 3, E1 = 4, F1 = 5, G1 = 6, H1 = 7;
    public static final int A8 = 56, B8 = 57, C8 = 58, D8 = 59, E8 = 60, F8 = 61, G8 = 62, H8 = 63;

    private Square() {}

    public static int of(int file, int rank) {
        return (rank << 3) | file;
    }

    public static int fileOf(int sq) {
        return sq & 7;
    }

    public static int rankOf(int sq) {
        return sq >>> 3;
    }

    public static int mirror(int sq) {
        return sq ^ 56;
    }

    public static int fromAlgebraic(String s) {
        if (s == null || s.length() != 2) return NONE;
        char fileChar = Character.toLowerCase(s.charAt(0));
        char rankChar = s.charAt(1);
        if (fileChar < 'a' || fileChar > 'h' || rankChar < '1' || rankChar > '8') {
            return NONE;
        }
        return of(fileChar - 'a', rankChar - '1');
    }

    public static String toAlgebraic(int sq) {
        if (sq < 0 || sq >= 64) return "-";
        char file = (char) ('a' + fileOf(sq));
        char rank = (char) ('1' + rankOf(sq));
        return new String(new char[]{file, rank});
    }
}
