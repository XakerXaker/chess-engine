package com.chess.engine.uci;

import com.chess.engine.core.Board;
import com.chess.engine.core.Color;
import com.chess.engine.core.Move;
import com.chess.engine.core.MoveList;
import com.chess.engine.eval.Evaluator;
import com.chess.engine.movegen.MoveGenerator;
import com.chess.engine.movegen.Perft;
import com.chess.engine.search.SearchEngine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class UciProtocol {
    public static final String ENGINE_NAME = "JavaChess 1.0 (Java 17)";
    public static final String ENGINE_AUTHOR = "Developer";

    private final Board board = new Board();
    private final SearchEngine engine = new SearchEngine(32);

    public UciProtocol() {
        board.setStartPosition();
    }

    public void runLoop() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!handleCommand(line)) {
                break;
            }
        }
    }

    public boolean handleCommand(String line) {
        String[] tokens = line.trim().split("\\s+");
        String cmd = tokens[0];

        switch (cmd) {
            case "uci" -> {
                System.out.println("id name " + ENGINE_NAME);
                System.out.println("id author " + ENGINE_AUTHOR);
                System.out.println("option name Hash type spin default 32 min 1 max 1024");
                System.out.println("uciok");
            }
            case "isready" -> System.out.println("readyok");
            case "ucinewgame" -> {
                engine.clearState();
                board.setStartPosition();
            }
            case "setoption" -> handleSetOption(tokens);
            case "position" -> handlePosition(tokens);
            case "go" -> handleGo(tokens);
            case "stop" -> engine.requestStop();
            case "d", "display" -> System.out.println(board.prettyPrint());
            case "fen" -> {
                if (tokens.length == 1) {
                    System.out.println(board.toFen());
                } else {
                    String fenStr = line.substring(line.indexOf(' ') + 1).trim();
                    board.loadFen(fenStr);
                    System.out.println("Loaded FEN: " + board.toFen());
                }
            }
            case "eval" -> {
                int eval = Evaluator.evaluate(board);
                System.out.printf("Static evaluation (%s to move): %+d cp%n",
                        Color.toString(board.sideToMove), eval);
            }
            case "moves" -> {
                MoveList legal = new MoveList();
                MoveGenerator.generateLegalMoves(board, legal);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < legal.size(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(Move.toUci(legal.get(i)));
                }
                System.out.printf("Legal moves (%d): %s%n", legal.size(), sb);
            }
            case "perft" -> {
                int depth = tokens.length > 1 ? Integer.parseInt(tokens[1]) : 4;
                long start = System.nanoTime();
                long nodes = Perft.perft(board, depth);
                long ms = Math.max(1L, (System.nanoTime() - start) / 1_000_000L);
                System.out.printf("Perft(%d) = %d nodes (%d ms, %d NPS)%n",
                        depth, nodes, ms, (nodes * 1000L) / ms);
            }
            case "divide" -> {
                int depth = tokens.length > 1 ? Integer.parseInt(tokens[1]) : 4;
                Perft.divide(board, depth);
            }
            case "bench" -> runBenchmark();
            case "quit", "exit" -> {
                return false;
            }
            default -> System.out.println("Unknown command: " + cmd);
        }
        return true;
    }

    private void handleSetOption(String[] tokens) {
        for (int i = 1; i < tokens.length - 1; i++) {
            if ("Hash".equalsIgnoreCase(tokens[i])) {
                for (int j = i + 1; j < tokens.length - 1; j++) {
                    if ("value".equalsIgnoreCase(tokens[j])) {
                        try {
                            int mb = Integer.parseInt(tokens[j + 1]);
                            engine.resizeHash(mb);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    private void handlePosition(String[] tokens) {
        if (tokens.length < 2) return;
        int movesIdx = -1;

        if ("startpos".equals(tokens[1])) {
            board.setStartPosition();
            for (int i = 2; i < tokens.length; i++) {
                if ("moves".equals(tokens[i])) {
                    movesIdx = i + 1;
                    break;
                }
            }
        } else if ("fen".equals(tokens[1])) {
            StringBuilder fenBuilder = new StringBuilder();
            for (int i = 2; i < tokens.length; i++) {
                if ("moves".equals(tokens[i])) {
                    movesIdx = i + 1;
                    break;
                }
                if (fenBuilder.length() > 0) fenBuilder.append(' ');
                fenBuilder.append(tokens[i]);
            }
            board.loadFen(fenBuilder.toString());
        }

        if (movesIdx != -1) {
            for (int i = movesIdx; i < tokens.length; i++) {
                int move = MoveGenerator.parseUciMove(board, tokens[i]);
                if (move != Move.NONE) {
                    board.makeMove(move);
                }
            }
        }
    }

    private void handleGo(String[] tokens) {
        int maxDepth = 64;
        long moveTimeMs = -1;
        long wtime = -1, btime = -1, winc = 0, binc = 0;
        long maxNodes = Long.MAX_VALUE;
        int movesToGo = 30;

        for (int i = 1; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "perft" -> {
                    int d = (i + 1 < tokens.length) ? Integer.parseInt(tokens[i + 1]) : 4;
                    Perft.divide(board, d);
                    return;
                }
                case "depth" -> {
                    if (i + 1 < tokens.length) maxDepth = Integer.parseInt(tokens[++i]);
                }
                case "movetime" -> {
                    if (i + 1 < tokens.length) moveTimeMs = Long.parseLong(tokens[++i]);
                }
                case "wtime" -> {
                    if (i + 1 < tokens.length) wtime = Long.parseLong(tokens[++i]);
                }
                case "btime" -> {
                    if (i + 1 < tokens.length) btime = Long.parseLong(tokens[++i]);
                }
                case "winc" -> {
                    if (i + 1 < tokens.length) winc = Long.parseLong(tokens[++i]);
                }
                case "binc" -> {
                    if (i + 1 < tokens.length) binc = Long.parseLong(tokens[++i]);
                }
                case "movestogo" -> {
                    if (i + 1 < tokens.length) movesToGo = Math.max(1, Integer.parseInt(tokens[++i]));
                }
                case "nodes" -> {
                    if (i + 1 < tokens.length) maxNodes = Long.parseLong(tokens[++i]);
                }
                case "infinite" -> moveTimeMs = Long.MAX_VALUE / 4;
            }
        }

        if (moveTimeMs < 0) {
            long remaining = (board.sideToMove == Color.WHITE) ? wtime : btime;
            long inc = (board.sideToMove == Color.WHITE) ? winc : binc;
            if (remaining > 0) {
                moveTimeMs = Math.max(20L, (remaining / Math.max(10, movesToGo)) + (inc * 3 / 4));
                moveTimeMs = Math.min(moveTimeMs, Math.max(20L, remaining - 50L));
            } else {

                if (maxDepth == 64) {
                    maxDepth = 7;
                }
                moveTimeMs = Long.MAX_VALUE / 4;
            }
        }

        SearchEngine.SearchLimits limits = new SearchEngine.SearchLimits(maxDepth, moveTimeMs, maxNodes);
        SearchEngine.SearchResult result = engine.findBestMove(board, limits);
        System.out.println("bestmove " + Move.toUci(result.bestMove()));
    }

    public void runBenchmark() {
        String[] benchFens = {
            Board.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        };
        long totalNodes = 0L;
        long startMs = System.currentTimeMillis();
        engine.clearState();
        for (String fen : benchFens) {
            board.loadFen(fen);
            SearchEngine.SearchResult res = engine.findBestMove(board, SearchEngine.SearchLimits.depth(6));
            totalNodes += res.nodes();
        }
        long elapsedMs = Math.max(1L, System.currentTimeMillis() - startMs);
        long nps = (totalNodes * 1000L) / elapsedMs;
        System.out.printf("Benchmark complete: %d nodes in %d ms (%d NPS)%n", totalNodes, elapsedMs, nps);
    }
}
