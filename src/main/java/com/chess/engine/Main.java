package com.chess.engine;

import com.chess.engine.core.Board;
import com.chess.engine.movegen.Perft;
import com.chess.engine.uci.UciProtocol;
import java.io.IOException;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            switch (args[0]) {
                case "--bench", "bench" -> {
                    new UciProtocol().runBenchmark();
                    return;
                }
                case "--perft", "perft" -> {
                    int depth = args.length > 1 ? Integer.parseInt(args[1]) : 5;
                    Board board = new Board();
                    board.setStartPosition();
                    Perft.divide(board, depth);
                    return;
                }
            }
        }
        new UciProtocol().runLoop();
    }
}
