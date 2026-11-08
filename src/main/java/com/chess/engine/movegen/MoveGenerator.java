package com.chess.engine.movegen;

import com.chess.engine.core.Bitboard;
import com.chess.engine.core.Board;
import com.chess.engine.core.Color;
import com.chess.engine.core.Move;
import com.chess.engine.core.MoveList;
import com.chess.engine.core.Piece;
import com.chess.engine.core.Square;

public final class MoveGenerator {
    private MoveGenerator() {}

    public static void generatePseudoLegalMoves(Board board, MoveList list) {
        generateMoves(board, list, false);
    }

    public static void generateCapturesAndPromotions(Board board, MoveList list) {
        generateMoves(board, list, true);
    }

    public static void generateLegalMoves(Board board, MoveList list) {
        list.clear();
        MoveList pseudo = new MoveList();
        generatePseudoLegalMoves(board, pseudo);
        for (int i = 0; i < pseudo.size(); i++) {
            int move = pseudo.get(i);
            if (board.makeMove(move)) {
                list.add(move);
                board.unmakeMove(move);
            }
        }
    }

    private static void generateMoves(Board board, MoveList list, boolean capturesOnly) {
        list.clear();
        int us = board.sideToMove;
        int them = Color.opposite(us);
        long ourPieces = board.colorBitboards[us];
        long theirPieces = board.colorBitboards[them];
        long occupied = board.occupiedBitboard;
        long empty = ~occupied;

        generatePawnMoves(board, list, us, them, theirPieces, empty, capturesOnly);
        generateKnightMoves(board, list, us, ourPieces, theirPieces, capturesOnly);
        generateBishopMoves(board, list, us, occupied, ourPieces, theirPieces, capturesOnly);
        generateRookMoves(board, list, us, occupied, ourPieces, theirPieces, capturesOnly);
        generateQueenMoves(board, list, us, occupied, ourPieces, theirPieces, capturesOnly);
        generateKingMoves(board, list, us, them, occupied, ourPieces, theirPieces, capturesOnly);
    }

    private static void generatePawnMoves(
            Board board, MoveList list, int us, int them,
            long theirPieces, long empty, boolean capturesOnly) {
        long pawns = board.pieceBitboards[us][Piece.PAWN];
        if (pawns == 0L) return;

        if (us == Color.WHITE) {

            long singlePush = (pawns << 8) & empty;
            long promotions = singlePush & Bitboard.RANK_8;
            long quietPushes = singlePush & ~Bitboard.RANK_8;

            while (promotions != 0L) {
                int to = Long.numberOfTrailingZeros(promotions);
                int from = to - 8;
                addPromotions(list, from, to, Piece.NONE, false);
                promotions &= promotions - 1;
            }

            if (!capturesOnly) {
                long doublePush = ((quietPushes & Bitboard.RANK_3) << 8) & empty;
                while (quietPushes != 0L) {
                    int to = Long.numberOfTrailingZeros(quietPushes);
                    list.add(Move.of(to - 8, to, Move.FLAG_QUIET, Piece.PAWN, Piece.NONE));
                    quietPushes &= quietPushes - 1;
                }
                while (doublePush != 0L) {
                    int to = Long.numberOfTrailingZeros(doublePush);
                    list.add(Move.of(to - 16, to, Move.FLAG_DOUBLE_PAWN_PUSH, Piece.PAWN, Piece.NONE));
                    doublePush &= doublePush - 1;
                }
            }

            long leftCaptures = ((pawns & Bitboard.NOT_FILE_A) << 7) & theirPieces;
            long rightCaptures = ((pawns & Bitboard.NOT_FILE_H) << 9) & theirPieces;

            while (leftCaptures != 0L) {
                int to = Long.numberOfTrailingZeros(leftCaptures);
                int from = to - 7;
                int cap = board.pieceAt[to];
                if (Square.rankOf(to) == 7) {
                    addPromotions(list, from, to, cap, true);
                } else {
                    list.add(Move.of(from, to, Move.FLAG_CAPTURE, Piece.PAWN, cap));
                }
                leftCaptures &= leftCaptures - 1;
            }

            while (rightCaptures != 0L) {
                int to = Long.numberOfTrailingZeros(rightCaptures);
                int from = to - 9;
                int cap = board.pieceAt[to];
                if (Square.rankOf(to) == 7) {
                    addPromotions(list, from, to, cap, true);
                } else {
                    list.add(Move.of(from, to, Move.FLAG_CAPTURE, Piece.PAWN, cap));
                }
                rightCaptures &= rightCaptures - 1;
            }

            if (board.enPassantSquare != Square.NONE) {
                long epAttackers = Bitboard.PAWN_ATTACKS[Color.BLACK][board.enPassantSquare] & pawns;
                while (epAttackers != 0L) {
                    int from = Long.numberOfTrailingZeros(epAttackers);
                    list.add(Move.of(from, board.enPassantSquare, Move.FLAG_EN_PASSANT, Piece.PAWN, Piece.PAWN));
                    epAttackers &= epAttackers - 1;
                }
            }
        } else {

            long singlePush = (pawns >>> 8) & empty;
            long promotions = singlePush & Bitboard.RANK_1;
            long quietPushes = singlePush & ~Bitboard.RANK_1;

            while (promotions != 0L) {
                int to = Long.numberOfTrailingZeros(promotions);
                int from = to + 8;
                addPromotions(list, from, to, Piece.NONE, false);
                promotions &= promotions - 1;
            }

            if (!capturesOnly) {
                long doublePush = ((quietPushes & Bitboard.RANK_6) >>> 8) & empty;
                while (quietPushes != 0L) {
                    int to = Long.numberOfTrailingZeros(quietPushes);
                    list.add(Move.of(to + 8, to, Move.FLAG_QUIET, Piece.PAWN, Piece.NONE));
                    quietPushes &= quietPushes - 1;
                }
                while (doublePush != 0L) {
                    int to = Long.numberOfTrailingZeros(doublePush);
                    list.add(Move.of(to + 16, to, Move.FLAG_DOUBLE_PAWN_PUSH, Piece.PAWN, Piece.NONE));
                    doublePush &= doublePush - 1;
                }
            }

            long leftCaptures = ((pawns & Bitboard.NOT_FILE_A) >>> 9) & theirPieces;
            long rightCaptures = ((pawns & Bitboard.NOT_FILE_H) >>> 7) & theirPieces;

            while (leftCaptures != 0L) {
                int to = Long.numberOfTrailingZeros(leftCaptures);
                int from = to + 9;
                int cap = board.pieceAt[to];
                if (Square.rankOf(to) == 0) {
                    addPromotions(list, from, to, cap, true);
                } else {
                    list.add(Move.of(from, to, Move.FLAG_CAPTURE, Piece.PAWN, cap));
                }
                leftCaptures &= leftCaptures - 1;
            }

            while (rightCaptures != 0L) {
                int to = Long.numberOfTrailingZeros(rightCaptures);
                int from = to + 7;
                int cap = board.pieceAt[to];
                if (Square.rankOf(to) == 0) {
                    addPromotions(list, from, to, cap, true);
                } else {
                    list.add(Move.of(from, to, Move.FLAG_CAPTURE, Piece.PAWN, cap));
                }
                rightCaptures &= rightCaptures - 1;
            }

            if (board.enPassantSquare != Square.NONE) {
                long epAttackers = Bitboard.PAWN_ATTACKS[Color.WHITE][board.enPassantSquare] & pawns;
                while (epAttackers != 0L) {
                    int from = Long.numberOfTrailingZeros(epAttackers);
                    list.add(Move.of(from, board.enPassantSquare, Move.FLAG_EN_PASSANT, Piece.PAWN, Piece.PAWN));
                    epAttackers &= epAttackers - 1;
                }
            }
        }
    }

    private static void addPromotions(MoveList list, int from, int to, int capturedPiece, boolean isCapture) {
        if (isCapture) {
            list.add(Move.of(from, to, Move.FLAG_PROMO_CAP_QUEEN, Piece.PAWN, capturedPiece));
            list.add(Move.of(from, to, Move.FLAG_PROMO_CAP_ROOK, Piece.PAWN, capturedPiece));
            list.add(Move.of(from, to, Move.FLAG_PROMO_CAP_BISHOP, Piece.PAWN, capturedPiece));
            list.add(Move.of(from, to, Move.FLAG_PROMO_CAP_KNIGHT, Piece.PAWN, capturedPiece));
        } else {
            list.add(Move.of(from, to, Move.FLAG_PROMO_QUEEN, Piece.PAWN, Piece.NONE));
            list.add(Move.of(from, to, Move.FLAG_PROMO_ROOK, Piece.PAWN, Piece.NONE));
            list.add(Move.of(from, to, Move.FLAG_PROMO_BISHOP, Piece.PAWN, Piece.NONE));
            list.add(Move.of(from, to, Move.FLAG_PROMO_KNIGHT, Piece.PAWN, Piece.NONE));
        }
    }

    private static void generateKnightMoves(
            Board board, MoveList list, int us, long ourPieces, long theirPieces, boolean capturesOnly) {
        long knights = board.pieceBitboards[us][Piece.KNIGHT];
        long targetMask = capturesOnly ? theirPieces : ~ourPieces;
        while (knights != 0L) {
            int from = Long.numberOfTrailingZeros(knights);
            long attacks = Bitboard.KNIGHT_ATTACKS[from] & targetMask;
            addPieceMoves(board, list, from, Piece.KNIGHT, attacks);
            knights &= knights - 1;
        }
    }

    private static void generateBishopMoves(
            Board board, MoveList list, int us, long occupied, long ourPieces, long theirPieces, boolean capturesOnly) {
        long bishops = board.pieceBitboards[us][Piece.BISHOP];
        long targetMask = capturesOnly ? theirPieces : ~ourPieces;
        while (bishops != 0L) {
            int from = Long.numberOfTrailingZeros(bishops);
            long attacks = Bitboard.getBishopAttacks(from, occupied) & targetMask;
            addPieceMoves(board, list, from, Piece.BISHOP, attacks);
            bishops &= bishops - 1;
        }
    }

    private static void generateRookMoves(
            Board board, MoveList list, int us, long occupied, long ourPieces, long theirPieces, boolean capturesOnly) {
        long rooks = board.pieceBitboards[us][Piece.ROOK];
        long targetMask = capturesOnly ? theirPieces : ~ourPieces;
        while (rooks != 0L) {
            int from = Long.numberOfTrailingZeros(rooks);
            long attacks = Bitboard.getRookAttacks(from, occupied) & targetMask;
            addPieceMoves(board, list, from, Piece.ROOK, attacks);
            rooks &= rooks - 1;
        }
    }

    private static void generateQueenMoves(
            Board board, MoveList list, int us, long occupied, long ourPieces, long theirPieces, boolean capturesOnly) {
        long queens = board.pieceBitboards[us][Piece.QUEEN];
        long targetMask = capturesOnly ? theirPieces : ~ourPieces;
        while (queens != 0L) {
            int from = Long.numberOfTrailingZeros(queens);
            long attacks = Bitboard.getQueenAttacks(from, occupied) & targetMask;
            addPieceMoves(board, list, from, Piece.QUEEN, attacks);
            queens &= queens - 1;
        }
    }

    private static void generateKingMoves(
            Board board, MoveList list, int us, int them,
            long occupied, long ourPieces, long theirPieces, boolean capturesOnly) {
        long kingBb = board.pieceBitboards[us][Piece.KING];
        if (kingBb == 0L) return;
        int from = Long.numberOfTrailingZeros(kingBb);
        long targetMask = capturesOnly ? theirPieces : ~ourPieces;
        long attacks = Bitboard.KING_ATTACKS[from] & targetMask;
        addPieceMoves(board, list, from, Piece.KING, attacks);

        if (capturesOnly) return;

        if (us == Color.WHITE && from == Square.E1) {
            if ((board.castlingRights & Board.CASTLE_WK) != 0
                    && board.pieceAt[Square.H1] == Piece.ROOK
                    && board.colorAt[Square.H1] == Color.WHITE
                    && (occupied & ((1L << Square.F1) | (1L << Square.G1))) == 0L
                    && !board.isSquareAttacked(Square.E1, Color.BLACK)
                    && !board.isSquareAttacked(Square.F1, Color.BLACK)
                    && !board.isSquareAttacked(Square.G1, Color.BLACK)) {
                list.add(Move.of(Square.E1, Square.G1, Move.FLAG_KING_CASTLE, Piece.KING, Piece.NONE));
            }
            if ((board.castlingRights & Board.CASTLE_WQ) != 0
                    && board.pieceAt[Square.A1] == Piece.ROOK
                    && board.colorAt[Square.A1] == Color.WHITE
                    && (occupied & ((1L << Square.B1) | (1L << Square.C1) | (1L << Square.D1))) == 0L
                    && !board.isSquareAttacked(Square.E1, Color.BLACK)
                    && !board.isSquareAttacked(Square.D1, Color.BLACK)
                    && !board.isSquareAttacked(Square.C1, Color.BLACK)) {
                list.add(Move.of(Square.E1, Square.C1, Move.FLAG_QUEEN_CASTLE, Piece.KING, Piece.NONE));
            }
        } else if (us == Color.BLACK && from == Square.E8) {
            if ((board.castlingRights & Board.CASTLE_BK) != 0
                    && board.pieceAt[Square.H8] == Piece.ROOK
                    && board.colorAt[Square.H8] == Color.BLACK
                    && (occupied & ((1L << Square.F8) | (1L << Square.G8))) == 0L
                    && !board.isSquareAttacked(Square.E8, Color.WHITE)
                    && !board.isSquareAttacked(Square.F8, Color.WHITE)
                    && !board.isSquareAttacked(Square.G8, Color.WHITE)) {
                list.add(Move.of(Square.E8, Square.G8, Move.FLAG_KING_CASTLE, Piece.KING, Piece.NONE));
            }
            if ((board.castlingRights & Board.CASTLE_BQ) != 0
                    && board.pieceAt[Square.A8] == Piece.ROOK
                    && board.colorAt[Square.A8] == Color.BLACK
                    && (occupied & ((1L << Square.B8) | (1L << Square.C8) | (1L << Square.D8))) == 0L
                    && !board.isSquareAttacked(Square.E8, Color.WHITE)
                    && !board.isSquareAttacked(Square.D8, Color.WHITE)
                    && !board.isSquareAttacked(Square.C8, Color.WHITE)) {
                list.add(Move.of(Square.E8, Square.C8, Move.FLAG_QUEEN_CASTLE, Piece.KING, Piece.NONE));
            }
        }
    }

    private static void addPieceMoves(Board board, MoveList list, int from, int pieceType, long targets) {
        while (targets != 0L) {
            int to = Long.numberOfTrailingZeros(targets);
            int cap = board.pieceAt[to];
            if (cap != Piece.NONE) {
                list.add(Move.of(from, to, Move.FLAG_CAPTURE, pieceType, cap));
            } else {
                list.add(Move.of(from, to, Move.FLAG_QUIET, pieceType, Piece.NONE));
            }
            targets &= targets - 1;
        }
    }

    public static int parseUciMove(Board board, String uciMove) {
        if (uciMove == null) return Move.NONE;
        String trimmed = uciMove.trim().toLowerCase();
        MoveList legal = new MoveList();
        generateLegalMoves(board, legal);
        for (int i = 0; i < legal.size(); i++) {
            int m = legal.get(i);
            if (Move.toUci(m).equals(trimmed)) {
                return m;
            }
        }
        return Move.NONE;
    }
}
