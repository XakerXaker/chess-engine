package com.chess.engine;

import com.chess.engine.core.Board;
import com.chess.engine.core.Move;
import com.chess.engine.core.MoveList;
import com.chess.engine.eval.Evaluator;
import com.chess.engine.fen.FenParseException;
import com.chess.engine.movegen.MoveGenerator;
import com.chess.engine.movegen.Perft;
import com.chess.engine.search.SearchEngine;

public final class EngineTestSuite {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running JavaChess Engine Test Suite ===");
        long startMs = System.currentTimeMillis();

        testFenParsingAndSerialization();
        testZobristIncrementalConsistency();
        testPerftSuite();
        testTacticalSearchAndMates();
        testDrawAndStalemateDetection();

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.printf("%n=== Test Summary: %d passed, %d failed (%d ms) ===%n", passed, failed, elapsedMs);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(String name, boolean condition, String details) {
        if (condition) {
            passed++;
            System.out.println("[PASS] " + name);
        } else {
            failed++;
            System.err.println("[FAIL] " + name + " -> " + details);
        }
    }

    private static void assertEquals(String name, long expected, long actual) {
        assertTrue(name, expected == actual, "Expected " + expected + ", got " + actual);
    }

    private static void assertStringEquals(String name, String expected, String actual) {
        assertTrue(name, expected.equals(actual), "Expected '" + expected + "', got '" + actual + "'");
    }

    private static void testFenParsingAndSerialization() {
        Board board = new Board();

        String[] validFens = {
            Board.START_FEN,
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 14 42",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"
        };

        for (String fen : validFens) {
            board.loadFen(fen);
            assertStringEquals("FEN round-trip: " + fen, fen, board.toFen());
        }

        board.setStartPosition();
        int e2e4 = MoveGenerator.parseUciMove(board, "e2e4");
        board.makeMove(e2e4);
        assertStringEquals("FEN after 1. e4",
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                board.toFen());

        String[] invalidFens = {
            "",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN2 w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR w KQkq - 0 1"
        };

        for (String badFen : invalidFens) {
            boolean threw = false;
            try {
                board.loadFen(badFen);
            } catch (FenParseException e) {
                threw = true;
            }
            assertTrue("Reject invalid FEN: '" + badFen + "'", threw, "Expected FenParseException");
        }
    }

    private static void testZobristIncrementalConsistency() {
        Board board = new Board();
        board.loadFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        boolean ok = verifyZobristTree(board, 3);
        assertTrue("Incremental Zobrist key matches full recomputation across tree (depth 3)", ok, "Mismatch detected");
    }

    private static boolean verifyZobristTree(Board board, int depth) {
        if (board.zobristKey != board.computeZobristKey()) {
            return false;
        }
        if (depth == 0) return true;

        MoveList moves = new MoveList();
        MoveGenerator.generatePseudoLegalMoves(board, moves);
        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            long beforeKey = board.zobristKey;
            if (board.makeMove(move)) {
                if (!verifyZobristTree(board, depth - 1)) {
                    return false;
                }
                board.unmakeMove(move);
            }
            if (board.zobristKey != beforeKey) {
                return false;
            }
        }
        return true;
    }

    private static void testPerftSuite() {
        Board board = new Board();

        board.loadFen(Board.START_FEN);
        assertEquals("Perft Startpos Depth 1", 20L, Perft.perft(board, 1));
        assertEquals("Perft Startpos Depth 2", 400L, Perft.perft(board, 2));
        assertEquals("Perft Startpos Depth 3", 8902L, Perft.perft(board, 3));
        assertEquals("Perft Startpos Depth 4", 197281L, Perft.perft(board, 4));
        assertEquals("Perft Startpos Depth 5", 4865609L, Perft.perft(board, 5));

        board.loadFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        assertEquals("Perft Kiwipete Depth 1", 48L, Perft.perft(board, 1));
        assertEquals("Perft Kiwipete Depth 2", 2039L, Perft.perft(board, 2));
        assertEquals("Perft Kiwipete Depth 3", 97862L, Perft.perft(board, 3));
        assertEquals("Perft Kiwipete Depth 4", 4085603L, Perft.perft(board, 4));

        board.loadFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        assertEquals("Perft Position 3 Depth 1", 14L, Perft.perft(board, 1));
        assertEquals("Perft Position 3 Depth 2", 191L, Perft.perft(board, 2));
        assertEquals("Perft Position 3 Depth 3", 2812L, Perft.perft(board, 3));
        assertEquals("Perft Position 3 Depth 4", 43238L, Perft.perft(board, 4));
        assertEquals("Perft Position 3 Depth 5", 674624L, Perft.perft(board, 5));

        board.loadFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1");
        assertEquals("Perft Position 4 Depth 1", 6L, Perft.perft(board, 1));
        assertEquals("Perft Position 4 Depth 2", 264L, Perft.perft(board, 2));
        assertEquals("Perft Position 4 Depth 3", 9467L, Perft.perft(board, 3));
        assertEquals("Perft Position 4 Depth 4", 422333L, Perft.perft(board, 4));

        board.loadFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8");
        assertEquals("Perft Position 5 Depth 1", 44L, Perft.perft(board, 1));
        assertEquals("Perft Position 5 Depth 2", 1486L, Perft.perft(board, 2));
        assertEquals("Perft Position 5 Depth 3", 62379L, Perft.perft(board, 3));
        assertEquals("Perft Position 5 Depth 4", 2103487L, Perft.perft(board, 4));
    }

    private static void testTacticalSearchAndMates() {
        Board board = new Board();
        SearchEngine engine = new SearchEngine(16);
        engine.setPrintUciInfo(false);

        board.loadFen("6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1");
        SearchEngine.SearchResult res1 = engine.findBestMove(board, SearchEngine.SearchLimits.depth(4));
        assertStringEquals("Find Mate in 1 (Re8#)", "e1e8", Move.toUci(res1.bestMove()));

        engine.clearState();
        board.loadFen("r1bqk2r/pppp1ppp/2n2n2/2b1p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4");
        SearchEngine.SearchResult resScholar = engine.findBestMove(board, SearchEngine.SearchLimits.depth(4));
        assertStringEquals("Find Scholar's Mate (Qxf7#)", "h5f7", Move.toUci(resScholar.bestMove()));

        engine.clearState();
        board.loadFen("4kb1r/p2n1ppp/4q3/4p1B1/4P3/1Q6/PPP2PPP/2KR4 w k - 1 16");
        SearchEngine.SearchResult resMorphy = engine.findBestMove(board, SearchEngine.SearchLimits.depth(5));
        assertStringEquals("Find Morphy's Opera Game Queen Sacrifice Mate in 2 (Qb8+!)", "b3b8", Move.toUci(resMorphy.bestMove()));
        assertTrue("Verify Morphy Mate in 2 score",
                resMorphy.score() > SearchEngine.MATE_SCORE - 10,
                "Expected mate score, got " + resMorphy.score());

        engine.clearState();
        board.loadFen("2r3k1/5ppp/8/8/8/8/4QPPP/4R1K1 w - - 0 1");
        SearchEngine.SearchResult resMate2 = engine.findBestMove(board, SearchEngine.SearchLimits.depth(5));
        assertTrue("Find back-rank forced Mate in 2 score",
                resMate2.score() > SearchEngine.MATE_SCORE - 10,
                "Expected mate score, got " + resMate2.score());

        engine.clearState();
        board.loadFen("rnb1kbnr/pppp1ppp/8/4p3/3qP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 4");
        SearchEngine.SearchResult resCap = engine.findBestMove(board, SearchEngine.SearchLimits.depth(5));
        assertStringEquals("Capture free black queen on d4 (Nxd4)", "f3d4", Move.toUci(resCap.bestMove()));
    }

    private static void testDrawAndStalemateDetection() {
        Board board = new Board();

        board.setStartPosition();
        board.makeMove(MoveGenerator.parseUciMove(board, "g1f3"));
        board.makeMove(MoveGenerator.parseUciMove(board, "g8f6"));
        board.makeMove(MoveGenerator.parseUciMove(board, "f3g1"));
        board.makeMove(MoveGenerator.parseUciMove(board, "f6g8"));
        assertTrue("Detect position repetition after Ng1-f3-g1 / Ng8-f6-g8",
                board.isRepetition(), "Expected isRepetition() == true");

        board.loadFen("8/8/4k3/8/8/4K3/8/8 w - - 0 1");
        assertEquals("Insufficient material K vs K evaluates to 0", 0L, Evaluator.evaluate(board));
    }
}
