package com.chess.engine.core;

public final class MoveList {
    public static final int MAX_MOVES = 256;

    private final int[] moves = new int[MAX_MOVES];
    private final int[] scores = new int[MAX_MOVES];
    private int size = 0;

    public void clear() {
        size = 0;
    }

    public void add(int move) {
        moves[size] = move;
        scores[size] = 0;
        size++;
    }

    public int size() {
        return size;
    }

    public int get(int index) {
        return moves[index];
    }

    public void setScore(int index, int score) {
        scores[index] = score;
    }

    public int getScore(int index) {
        return scores[index];
    }

    public int pickNextMove(int startIndex) {
        int bestIndex = startIndex;
        int bestScore = scores[startIndex];
        for (int i = startIndex + 1; i < size; i++) {
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                bestIndex = i;
            }
        }
        if (bestIndex != startIndex) {
            int tmpMove = moves[startIndex];
            moves[startIndex] = moves[bestIndex];
            moves[bestIndex] = tmpMove;

            int tmpScore = scores[startIndex];
            scores[startIndex] = scores[bestIndex];
            scores[bestIndex] = tmpScore;
        }
        return moves[startIndex];
    }
}
