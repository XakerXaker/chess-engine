package com.chess.engine.core;

public final class Bitboard {
    public static final long FILE_A = 0x0101010101010101L;
    public static final long FILE_B = FILE_A << 1;
    public static final long FILE_C = FILE_A << 2;
    public static final long FILE_D = FILE_A << 3;
    public static final long FILE_E = FILE_A << 4;
    public static final long FILE_F = FILE_A << 5;
    public static final long FILE_G = FILE_A << 6;
    public static final long FILE_H = FILE_A << 7;

    public static final long NOT_FILE_A = ~FILE_A;
    public static final long NOT_FILE_H = ~FILE_H;
    public static final long NOT_FILE_AB = ~(FILE_A | FILE_B);
    public static final long NOT_FILE_GH = ~(FILE_G | FILE_H);

    public static final long RANK_1 = 0x00000000000000FFL;
    public static final long RANK_2 = RANK_1 << 8;
    public static final long RANK_3 = RANK_1 << 16;
    public static final long RANK_4 = RANK_1 << 24;
    public static final long RANK_5 = RANK_1 << 32;
    public static final long RANK_6 = RANK_1 << 40;
    public static final long RANK_7 = RANK_1 << 48;
    public static final long RANK_8 = RANK_1 << 56;

    public static final long[] FILE_MASKS = {
        FILE_A, FILE_B, FILE_C, FILE_D, FILE_E, FILE_F, FILE_G, FILE_H
    };

    public static final long[] RANK_MASKS = {
        RANK_1, RANK_2, RANK_3, RANK_4, RANK_5, RANK_6, RANK_7, RANK_8
    };

    public static final long[][] PAWN_ATTACKS = new long[2][64];
    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[] KING_ATTACKS = new long[64];

    private static final int DIR_N = 0;
    private static final int DIR_E = 1;
    private static final int DIR_NE = 2;
    private static final int DIR_NW = 3;
    private static final int DIR_S = 4;
    private static final int DIR_W = 5;
    private static final int DIR_SW = 6;
    private static final int DIR_SE = 7;

    private static final long[][] RAY_ATTACKS = new long[8][64];

    public static final long[] ADJACENT_FILES = new long[8];
    public static final long[][] PASSED_PAWN_MASKS = new long[2][64];

    static {
        initNonSlidingAttacks();
        initRays();
        initPawnStructureMasks();
    }

    private Bitboard() {}

    private static void initNonSlidingAttacks() {
        for (int sq = 0; sq < 64; sq++) {
            long bit = 1L << sq;

            long wp = ((bit & NOT_FILE_A) << 7) | ((bit & NOT_FILE_H) << 9);
            PAWN_ATTACKS[Color.WHITE][sq] = wp;

            long bp = ((bit & NOT_FILE_H) >>> 7) | ((bit & NOT_FILE_A) >>> 9);
            PAWN_ATTACKS[Color.BLACK][sq] = bp;

            long n = 0L;
            n |= (bit & NOT_FILE_A) << 15;
            n |= (bit & NOT_FILE_H) << 17;
            n |= (bit & NOT_FILE_AB) << 6;
            n |= (bit & NOT_FILE_GH) << 10;
            n |= (bit & NOT_FILE_H) >>> 15;
            n |= (bit & NOT_FILE_A) >>> 17;
            n |= (bit & NOT_FILE_GH) >>> 6;
            n |= (bit & NOT_FILE_AB) >>> 10;
            KNIGHT_ATTACKS[sq] = n;

            long k = 0L;
            k |= (bit & NOT_FILE_A) >>> 1;
            k |= (bit & NOT_FILE_H) << 1;
            k |= bit << 8;
            k |= bit >>> 8;
            k |= (bit & NOT_FILE_A) << 7;
            k |= (bit & NOT_FILE_H) << 9;
            k |= (bit & NOT_FILE_H) >>> 7;
            k |= (bit & NOT_FILE_A) >>> 9;
            KING_ATTACKS[sq] = k;
        }
    }

    private static void initRays() {
        int[] df = {0, 1, 1, -1, 0, -1, -1, 1};
        int[] dr = {1, 0, 1, 1, -1, 0, -1, -1};

        for (int sq = 0; sq < 64; sq++) {
            int f0 = Square.fileOf(sq);
            int r0 = Square.rankOf(sq);
            for (int dir = 0; dir < 8; dir++) {
                long mask = 0L;
                int f = f0 + df[dir];
                int r = r0 + dr[dir];
                while (f >= 0 && f < 8 && r >= 0 && r < 8) {
                    mask |= 1L << Square.of(f, r);
                    f += df[dir];
                    r += dr[dir];
                }
                RAY_ATTACKS[dir][sq] = mask;
            }
        }
    }

    private static void initPawnStructureMasks() {
        for (int f = 0; f < 8; f++) {
            long adj = 0L;
            if (f > 0) adj |= FILE_MASKS[f - 1];
            if (f < 7) adj |= FILE_MASKS[f + 1];
            ADJACENT_FILES[f] = adj;
        }

        for (int sq = 0; sq < 64; sq++) {
            int f = Square.fileOf(sq);
            int r = Square.rankOf(sq);
            long fileSpan = FILE_MASKS[f] | ADJACENT_FILES[f];

            long whiteAhead = 0L;
            for (int nr = r + 1; nr < 8; nr++) {
                whiteAhead |= RANK_MASKS[nr];
            }
            PASSED_PAWN_MASKS[Color.WHITE][sq] = fileSpan & whiteAhead;

            long blackAhead = 0L;
            for (int nr = r - 1; nr >= 0; nr--) {
                blackAhead |= RANK_MASKS[nr];
            }
            PASSED_PAWN_MASKS[Color.BLACK][sq] = fileSpan & blackAhead;
        }
    }

    private static long positiveRayAttacks(int dir, int sq, long occupied) {
        long attacks = RAY_ATTACKS[dir][sq];
        long blockers = attacks & occupied;
        if (blockers != 0L) {
            int firstBlocker = Long.numberOfTrailingZeros(blockers);
            attacks ^= RAY_ATTACKS[dir][firstBlocker];
        }
        return attacks;
    }

    private static long negativeRayAttacks(int dir, int sq, long occupied) {
        long attacks = RAY_ATTACKS[dir][sq];
        long blockers = attacks & occupied;
        if (blockers != 0L) {
            int firstBlocker = 63 - Long.numberOfLeadingZeros(blockers);
            attacks ^= RAY_ATTACKS[dir][firstBlocker];
        }
        return attacks;
    }

    public static long getRookAttacks(int sq, long occupied) {
        return positiveRayAttacks(DIR_N, sq, occupied)
             | positiveRayAttacks(DIR_E, sq, occupied)
             | negativeRayAttacks(DIR_S, sq, occupied)
             | negativeRayAttacks(DIR_W, sq, occupied);
    }

    public static long getBishopAttacks(int sq, long occupied) {
        return positiveRayAttacks(DIR_NE, sq, occupied)
             | positiveRayAttacks(DIR_NW, sq, occupied)
             | negativeRayAttacks(DIR_SW, sq, occupied)
             | negativeRayAttacks(DIR_SE, sq, occupied);
    }

    public static long getQueenAttacks(int sq, long occupied) {
        return getRookAttacks(sq, occupied) | getBishopAttacks(sq, occupied);
    }
}
