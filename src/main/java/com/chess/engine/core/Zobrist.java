package com.chess.engine.core;

public final class Zobrist {
    public static final long[][][] PIECE_KEYS = new long[2][7][64];
    public static final long[] CASTLING_KEYS = new long[16];
    public static final long[] EN_PASSANT_KEYS = new long[64];
    public static final long SIDE_KEY;

    private static long seed = 0x9E3779B97F4A7C15L;

    static {
        for (int color = 0; color < 2; color++) {
            for (int piece = 1; piece <= 6; piece++) {
                for (int sq = 0; sq < 64; sq++) {
                    PIECE_KEYS[color][piece][sq] = nextLong();
                }
            }
        }
        for (int i = 0; i < 16; i++) {
            CASTLING_KEYS[i] = nextLong();
        }
        for (int sq = 0; sq < 64; sq++) {
            EN_PASSANT_KEYS[sq] = nextLong();
        }
        SIDE_KEY = nextLong();
    }

    private Zobrist() {}

    private static long nextLong() {
        long z = (seed += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
