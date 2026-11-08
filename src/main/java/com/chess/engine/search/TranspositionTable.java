package com.chess.engine.search;

import com.chess.engine.core.Move;
import java.util.Arrays;

public final class TranspositionTable {
    public static final byte FLAG_NONE = 0;
    public static final byte FLAG_EXACT = 1;
    public static final byte FLAG_ALPHA = 2;
    public static final byte FLAG_BETA = 3;

    private static final int MATE_THRESHOLD = 28000;

    private int mask;
    private long[] keys;
    private int[] moves;
    private short[] scores;
    private byte[] depths;
    private byte[] flags;

    public TranspositionTable(int sizeMb) {
        resize(sizeMb);
    }

    public void resize(int sizeMb) {
        int clampedMb = Math.max(1, Math.min(sizeMb, 1024));
        long bytes = (long) clampedMb * 1024L * 1024L;

        long rawEntries = bytes / 16L;
        int pow2 = Integer.highestOneBit((int) Math.min(rawEntries, 1 << 26));
        if (pow2 < 1024) pow2 = 1024;

        this.mask = pow2 - 1;
        this.keys = new long[pow2];
        this.moves = new int[pow2];
        this.scores = new short[pow2];
        this.depths = new byte[pow2];
        this.flags = new byte[pow2];
    }

    public void clear() {
        Arrays.fill(keys, 0L);
        Arrays.fill(moves, Move.NONE);
        Arrays.fill(scores, (short) 0);
        Arrays.fill(depths, (byte) 0);
        Arrays.fill(flags, FLAG_NONE);
    }

    private int index(long zobristKey) {
        return (int) (zobristKey ^ (zobristKey >>> 32)) & mask;
    }

    public void store(long zobristKey, int depth, int score, byte flag, int bestMove, int ply) {
        int idx = index(zobristKey);

        if (keys[idx] != zobristKey || depth >= depths[idx] || flag == FLAG_EXACT) {
            if (bestMove == Move.NONE && keys[idx] == zobristKey) {
                bestMove = moves[idx];
            }
            keys[idx] = zobristKey;
            moves[idx] = bestMove;
            scores[idx] = (short) scoreToTT(score, ply);
            depths[idx] = (byte) depth;
            flags[idx] = flag;
        }
    }

    public int probeMove(long zobristKey) {
        int idx = index(zobristKey);
        if (keys[idx] == zobristKey && flags[idx] != FLAG_NONE) {
            return moves[idx];
        }
        return Move.NONE;
    }

    public boolean probe(long zobristKey, int depth, int alpha, int beta, int ply, int[] outScoreAndMove) {
        int idx = index(zobristKey);
        if (keys[idx] == zobristKey && flags[idx] != FLAG_NONE) {
            outScoreAndMove[1] = moves[idx];
            if (depths[idx] >= depth) {
                int score = scoreFromTT(scores[idx], ply);
                byte flag = flags[idx];
                if (flag == FLAG_EXACT) {
                    outScoreAndMove[0] = score;
                    return true;
                }
                if (flag == FLAG_ALPHA && score <= alpha) {
                    outScoreAndMove[0] = alpha;
                    return true;
                }
                if (flag == FLAG_BETA && score >= beta) {
                    outScoreAndMove[0] = beta;
                    return true;
                }
            }
        } else {
            outScoreAndMove[1] = Move.NONE;
        }
        return false;
    }

    private static int scoreToTT(int score, int ply) {
        if (score > MATE_THRESHOLD) return score + ply;
        if (score < -MATE_THRESHOLD) return score - ply;
        return score;
    }

    private static int scoreFromTT(int score, int ply) {
        if (score > MATE_THRESHOLD) return score - ply;
        if (score < -MATE_THRESHOLD) return score + ply;
        return score;
    }
}
