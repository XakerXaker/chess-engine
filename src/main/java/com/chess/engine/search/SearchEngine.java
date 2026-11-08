package com.chess.engine.search;

import com.chess.engine.core.Board;
import com.chess.engine.core.Move;
import com.chess.engine.core.MoveList;
import com.chess.engine.core.Piece;
import com.chess.engine.eval.Evaluator;
import com.chess.engine.movegen.MoveGenerator;
import java.util.Arrays;

public final class SearchEngine {
    public static final int INFINITY = 30000;
    public static final int MATE_SCORE = 29000;
    public static final int MAX_PLY = 128;

    private static final int SCORE_TT_MOVE = 1_000_000;
    private static final int SCORE_CAPTURE_BASE = 100_000;
    private static final int SCORE_KILLER_1 = 90_000;
    private static final int SCORE_KILLER_2 = 80_000;
    private static final int MAX_HISTORY_SCORE = 70_000;

    private static final int[] VICTIM_SCORES = {0, 100, 300, 320, 500, 900, 10000};

    private final TranspositionTable tt;
    private final int[][] killerMoves = new int[MAX_PLY][2];
    private final int[][][] historyTable = new int[2][64][64];
    private final int[][] pvTable = new int[MAX_PLY][MAX_PLY];
    private final int[] pvLength = new int[MAX_PLY];
    private final MoveList[] plyMoveLists = new MoveList[MAX_PLY];
    private final int[] ttProbeResult = new int[2];

    private volatile boolean stopRequested = false;
    private long startTimeMs;
    private long hardTimeLimitMs;
    private long softTimeLimitMs;
    private long maxNodesLimit;
    private long nodesVisited;
    private boolean printUciInfo = true;

    public record SearchLimits(int maxDepth, long moveTimeMs, long maxNodes) {
        public static SearchLimits depth(int depth) {
            return new SearchLimits(Math.max(1, Math.min(depth, MAX_PLY - 2)), Long.MAX_VALUE / 4, Long.MAX_VALUE);
        }

        public static SearchLimits moveTime(long timeMs) {
            return new SearchLimits(MAX_PLY - 2, Math.max(5L, timeMs), Long.MAX_VALUE);
        }
    }

    public record SearchResult(int bestMove, int score, int depthReached, long nodes, long timeMs, String pv) {}

    public SearchEngine() {
        this(32);
    }

    public SearchEngine(int hashSizeMb) {
        this.tt = new TranspositionTable(hashSizeMb);
        for (int i = 0; i < MAX_PLY; i++) {
            plyMoveLists[i] = new MoveList();
        }
    }

    public void setPrintUciInfo(boolean printUciInfo) {
        this.printUciInfo = printUciInfo;
    }

    public void resizeHash(int sizeMb) {
        tt.resize(sizeMb);
    }

    public void clearState() {
        tt.clear();
        clearHeuristics();
    }

    private void clearHeuristics() {
        for (int i = 0; i < MAX_PLY; i++) {
            killerMoves[i][0] = Move.NONE;
            killerMoves[i][1] = Move.NONE;
            pvLength[i] = 0;
        }
        for (int c = 0; c < 2; c++) {
            for (int f = 0; f < 64; f++) {
                Arrays.fill(historyTable[c][f], 0);
            }
        }
    }

    public void requestStop() {
        this.stopRequested = true;
    }

    public SearchResult findBestMove(Board board, SearchLimits limits) {
        stopRequested = false;
        nodesVisited = 0L;
        startTimeMs = System.currentTimeMillis();
        hardTimeLimitMs = limits.moveTimeMs();
        softTimeLimitMs = hardTimeLimitMs > 1_000_000_000L ? hardTimeLimitMs : Math.max(5L, (hardTimeLimitMs * 6) / 10);
        maxNodesLimit = limits.maxNodes();

        clearHeuristics();

        MoveList rootLegal = new MoveList();
        MoveGenerator.generateLegalMoves(board, rootLegal);
        if (rootLegal.size() == 0) {
            return new SearchResult(Move.NONE, 0, 0, 0L, 0L, "");
        }

        int bestMove = rootLegal.get(0);
        int bestScore = 0;
        int completedDepth = 0;
        String bestPv = Move.toUci(bestMove);

        int maxDepth = Math.min(limits.maxDepth(), MAX_PLY - 2);

        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            int score = negamax(board, currentDepth, -INFINITY, INFINITY, 0, true);

            if (stopRequested && currentDepth > 1) {
                break;
            }

            completedDepth = currentDepth;
            bestScore = score;
            if (pvLength[0] > 0 && pvTable[0][0] != Move.NONE) {
                bestMove = pvTable[0][0];
                bestPv = buildPvString();
            }

            long elapsedMs = Math.max(1L, System.currentTimeMillis() - startTimeMs);
            long nps = (nodesVisited * 1000L) / elapsedMs;

            if (printUciInfo) {
                System.out.printf("info depth %d score %s nodes %d nps %d time %d pv %s%n",
                        completedDepth, formatUciScore(bestScore), nodesVisited, nps, elapsedMs, bestPv);
            }

            if (Math.abs(bestScore) > MATE_SCORE - MAX_PLY) {
                int matePlies = MATE_SCORE - Math.abs(bestScore);
                if (currentDepth >= matePlies + 1) {
                    break;
                }
            }
            if (elapsedMs >= softTimeLimitMs || stopRequested) {
                break;
            }
        }

        long totalTimeMs = Math.max(1L, System.currentTimeMillis() - startTimeMs);
        return new SearchResult(bestMove, bestScore, completedDepth, nodesVisited, totalTimeMs, bestPv);
    }

    private int negamax(Board board, int depth, int alpha, int beta, int ply, boolean allowNullMove) {
        pvLength[ply] = ply;

        if ((nodesVisited & 2047L) == 0L) {
            checkStopConditions();
        }
        if (stopRequested && ply > 0) {
            return 0;
        }

        boolean isRoot = (ply == 0);
        boolean isPvNode = (beta - alpha) > 1;

        if (!isRoot) {
            if (board.halfMoveClock >= 100 || board.isRepetition() || board.isInsufficientMaterial()) {
                return 0;
            }

            alpha = Math.max(alpha, -MATE_SCORE + ply);
            beta = Math.min(beta, MATE_SCORE - ply - 1);
            if (alpha >= beta) {
                return alpha;
            }
        }

        boolean inCheck = board.inCheck();
        if (inCheck && ply < MAX_PLY - 4) {
            depth++;
        }

        if (depth <= 0 || ply >= MAX_PLY - 2) {
            return quiescence(board, alpha, beta, ply);
        }

        nodesVisited++;

        if (tt.probe(board.zobristKey, depth, alpha, beta, ply, ttProbeResult)) {
            if (!isRoot && !isPvNode) {
                return ttProbeResult[0];
            }
        }
        int ttMove = ttProbeResult[1];

        if (!isRoot && !isPvNode && !inCheck) {
            int staticEval = Evaluator.evaluate(board);

            if (depth <= 3 && Math.abs(beta) < MATE_SCORE - MAX_PLY) {
                int margin = 120 * depth;
                if (staticEval - margin >= beta) {
                    return staticEval - margin;
                }
            }

            if (allowNullMove && depth >= 3 && staticEval >= beta && board.hasNonPawnMaterial(board.sideToMove)) {
                int r = 2 + (depth >= 6 ? 1 : 0);
                board.makeNullMove();
                int nullScore = -negamax(board, depth - 1 - r, -beta, -beta + 1, ply + 1, false);
                board.unmakeNullMove();
                if (stopRequested) return 0;
                if (nullScore >= beta) {
                    return nullScore >= MATE_SCORE - MAX_PLY ? beta : nullScore;
                }
            }
        }

        MoveList moves = plyMoveLists[ply];
        MoveGenerator.generatePseudoLegalMoves(board, moves);
        scoreMoves(board, moves, ttMove, ply);

        int legalMovesCount = 0;
        int bestScore = -INFINITY;
        int bestMove = Move.NONE;
        byte ttFlag = TranspositionTable.FLAG_ALPHA;

        for (int i = 0; i < moves.size(); i++) {
            int move = moves.pickNextMove(i);
            if (!board.makeMove(move)) {
                continue;
            }
            legalMovesCount++;

            int score;
            if (legalMovesCount == 1) {
                score = -negamax(board, depth - 1, -beta, -alpha, ply + 1, true);
            } else {

                int reduction = 0;
                if (depth >= 3 && legalMovesCount >= 4 && !inCheck
                        && !Move.isCapture(move) && !Move.isPromotion(move) && !board.inCheck()) {
                    reduction = 1 + (legalMovesCount >= 10 && depth >= 5 ? 1 : 0);
                }

                score = -negamax(board, depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, true);

                if (reduction > 0 && score > alpha) {
                    score = -negamax(board, depth - 1, -alpha - 1, -alpha, ply + 1, true);
                }

                if (score > alpha && score < beta) {
                    score = -negamax(board, depth - 1, -beta, -alpha, ply + 1, true);
                }
            }

            board.unmakeMove(move);

            if (stopRequested && ply > 0) {
                return 0;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;

                if (score > alpha) {
                    alpha = score;
                    ttFlag = TranspositionTable.FLAG_EXACT;

                    pvTable[ply][ply] = move;
                    for (int nextPly = ply + 1; nextPly < pvLength[ply + 1]; nextPly++) {
                        pvTable[ply][nextPly] = pvTable[ply + 1][nextPly];
                    }
                    pvLength[ply] = pvLength[ply + 1];

                    if (alpha >= beta) {
                        ttFlag = TranspositionTable.FLAG_BETA;
                        if (!Move.isCapture(move) && !Move.isPromotion(move)) {
                            updateKillersAndHistory(board.sideToMove, move, depth, ply);
                        }
                        break;
                    }
                }
            }
        }

        if (legalMovesCount == 0) {
            return inCheck ? (-MATE_SCORE + ply) : 0;
        }

        tt.store(board.zobristKey, depth, bestScore, ttFlag, bestMove, ply);
        return bestScore;
    }

    private int quiescence(Board board, int alpha, int beta, int ply) {
        pvLength[ply] = ply;

        if ((nodesVisited & 2047L) == 0L) {
            checkStopConditions();
        }
        if (stopRequested && ply > 0) {
            return 0;
        }

        nodesVisited++;

        if (board.halfMoveClock >= 100 || board.isRepetition() || board.isInsufficientMaterial()) {
            return 0;
        }

        if (ply >= MAX_PLY - 2) {
            return Evaluator.evaluate(board);
        }

        boolean inCheck = board.inCheck();

        if (!inCheck) {
            int standPat = Evaluator.evaluate(board);
            if (standPat >= beta) {
                return beta;
            }

            if (standPat + 975 < alpha) {

                alpha = Math.max(alpha, standPat);
            } else if (standPat > alpha) {
                alpha = standPat;
            }
        }

        MoveList moves = plyMoveLists[ply];
        if (inCheck) {
            MoveGenerator.generatePseudoLegalMoves(board, moves);
        } else {
            MoveGenerator.generateCapturesAndPromotions(board, moves);
        }
        scoreMoves(board, moves, Move.NONE, ply);

        int legalMoves = 0;
        for (int i = 0; i < moves.size(); i++) {
            int move = moves.pickNextMove(i);
            if (!board.makeMove(move)) {
                continue;
            }
            legalMoves++;
            int score = -quiescence(board, -beta, -alpha, ply + 1);
            board.unmakeMove(move);

            if (stopRequested && ply > 0) {
                return 0;
            }

            if (score >= beta) {
                return beta;
            }
            if (score > alpha) {
                alpha = score;
            }
        }

        if (inCheck && legalMoves == 0) {
            return -MATE_SCORE + ply;
        }

        return alpha;
    }

    private void scoreMoves(Board board, MoveList moves, int ttMove, int ply) {
        int side = board.sideToMove;
        int killer1 = killerMoves[ply][0];
        int killer2 = killerMoves[ply][1];

        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            int score;
            if (move == ttMove) {
                score = SCORE_TT_MOVE;
            } else if (Move.isCapture(move) || Move.isPromotion(move)) {
                int victim = Move.capturedPiece(move);
                int attacker = Move.movedPiece(move);
                score = SCORE_CAPTURE_BASE + (VICTIM_SCORES[victim] * 10) - VICTIM_SCORES[attacker];
                if (Move.isPromotion(move)) {
                    score += VICTIM_SCORES[Move.promotedPiece(move)] * 8;
                }
            } else if (move == killer1) {
                score = SCORE_KILLER_1;
            } else if (move == killer2) {
                score = SCORE_KILLER_2;
            } else {
                score = historyTable[side][Move.from(move)][Move.to(move)];
            }
            moves.setScore(i, score);
        }
    }

    private void updateKillersAndHistory(int side, int move, int depth, int ply) {
        if (killerMoves[ply][0] != move) {
            killerMoves[ply][1] = killerMoves[ply][0];
            killerMoves[ply][0] = move;
        }
        int from = Move.from(move);
        int to = Move.to(move);
        int bonus = depth * depth;
        historyTable[side][from][to] = Math.min(MAX_HISTORY_SCORE, historyTable[side][from][to] + bonus);
    }

    private void checkStopConditions() {
        if (nodesVisited >= maxNodesLimit) {
            stopRequested = true;
            return;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed >= hardTimeLimitMs) {
            stopRequested = true;
        }
    }

    private String buildPvString() {
        StringBuilder sb = new StringBuilder();
        int len = pvLength[0];
        for (int i = 0; i < len; i++) {
            if (pvTable[0][i] == Move.NONE) break;
            if (i > 0) sb.append(' ');
            sb.append(Move.toUci(pvTable[0][i]));
        }
        return sb.toString();
    }

    public static String formatUciScore(int score) {
        if (Math.abs(score) > MATE_SCORE - MAX_PLY) {
            int pliesToMate = MATE_SCORE - Math.abs(score);
            int movesToMate = (pliesToMate + 1) / 2;
            return "mate " + (score > 0 ? movesToMate : -movesToMate);
        }
        return "cp " + score;
    }
}
