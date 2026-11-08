package com.chess.engine.movegen;

import com.chess.engine.core.Board;
import com.chess.engine.core.Move;
import com.chess.engine.core.MoveList;

public final class Perft {
    private Perft() {}

    public static long perft(Board board, int depth) {
        if (depth <= 0) return 1L;

        MoveList moves = new MoveList();
        MoveGenerator.generatePseudoLegalMoves(board, moves);

        long nodes = 0L;
        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            if (board.makeMove(move)) {
                nodes += (depth == 1) ? 1L : perft(board, depth - 1);
                board.unmakeMove(move);
            }
        }
        return nodes;
    }

    public static long divide(Board board, int depth) {
        long startNs = System.nanoTime();
        MoveList moves = new MoveList();
        MoveGenerator.generatePseudoLegalMoves(board, moves);

        long totalNodes = 0L;
        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            if (board.makeMove(move)) {
                long branchNodes = (depth <= 1) ? 1L : perft(board, depth - 1);
                board.unmakeMove(move);
                totalNodes += branchNodes;
                System.out.println(Move.toUci(move) + ": " + branchNodes);
            }
        }
        long elapsedMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
        long nps = (totalNodes * 1000L) / elapsedMs;
        System.out.printf("%nNodes searched: %d (%d ms, %d NPS)%n", totalNodes, elapsedMs, nps);
        return totalNodes;
    }
}
