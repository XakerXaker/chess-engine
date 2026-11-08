package com.chess.engine.fen;

import com.chess.engine.core.Board;
import com.chess.engine.core.Color;
import com.chess.engine.core.Piece;
import com.chess.engine.core.Square;

public final class FenParser {
    private FenParser() {}

    public static void loadFen(Board board, String fen) {
        if (fen == null || fen.isBlank()) {
            throw new FenParseException("FEN string cannot be null or empty.");
        }

        String[] tokens = fen.trim().split("\\s+");
        if (tokens.length < 4) {
            throw new FenParseException("Invalid FEN: expected at least 4 fields, got " + tokens.length + " in '" + fen + "'");
        }

        board.clear();

        String[] ranks = tokens[0].split("/", -1);
        if (ranks.length != 8) {
            throw new FenParseException("Invalid FEN piece placement: expected 8 ranks, got " + ranks.length);
        }

        for (int r = 0; r < 8; r++) {
            int boardRank = 7 - r;
            int file = 0;
            String rankStr = ranks[r];
            if (rankStr.isEmpty()) {
                throw new FenParseException("Empty rank in FEN at rank " + (boardRank + 1));
            }
            for (int i = 0; i < rankStr.length(); i++) {
                char c = rankStr.charAt(i);
                if (c >= '1' && c <= '8') {
                    file += (c - '0');
                } else {
                    int pieceType = Piece.typeFromChar(c);
                    int color = Piece.colorFromChar(c);
                    if (pieceType == Piece.NONE || color == Color.NONE) {
                        throw new FenParseException("Invalid piece character '" + c + "' in FEN");
                    }
                    if (file >= 8) {
                        throw new FenParseException("Rank " + (boardRank + 1) + " exceeds 8 files in FEN");
                    }
                    board.putPiece(color, pieceType, Square.of(file, boardRank));
                    file++;
                }
            }
            if (file != 8) {
                throw new FenParseException("Rank " + (boardRank + 1) + " has " + file + " files instead of 8");
            }
        }

        if (Long.bitCount(board.pieceBitboards[Color.WHITE][Piece.KING]) != 1 ||
            Long.bitCount(board.pieceBitboards[Color.BLACK][Piece.KING]) != 1) {
            throw new FenParseException("Invalid FEN: each side must have exactly one King");
        }

        String sideToken = tokens[1];
        if ("w".equals(sideToken)) {
            board.sideToMove = Color.WHITE;
        } else if ("b".equals(sideToken)) {
            board.sideToMove = Color.BLACK;
        } else {
            throw new FenParseException("Invalid active color '" + sideToken + "' in FEN (expected 'w' or 'b')");
        }

        String castlingToken = tokens[2];
        int rights = 0;
        if (!"-".equals(castlingToken)) {
            for (int i = 0; i < castlingToken.length(); i++) {
                char c = castlingToken.charAt(i);
                switch (c) {
                    case 'K' -> rights |= Board.CASTLE_WK;
                    case 'Q' -> rights |= Board.CASTLE_WQ;
                    case 'k' -> rights |= Board.CASTLE_BK;
                    case 'q' -> rights |= Board.CASTLE_BQ;
                    default -> throw new FenParseException("Invalid castling flag '" + c + "' in FEN");
                }
            }
        }
        board.castlingRights = rights;

        String epToken = tokens[3];
        if ("-".equals(epToken)) {
            board.enPassantSquare = Square.NONE;
        } else {
            int epSq = Square.fromAlgebraic(epToken);
            int epRank = Square.rankOf(epSq);
            if (epSq == Square.NONE || (epRank != 2 && epRank != 5)) {
                throw new FenParseException("Invalid en passant square '" + epToken + "' in FEN");
            }
            board.enPassantSquare = epSq;
        }

        if (tokens.length >= 5) {
            try {
                board.halfMoveClock = Integer.parseInt(tokens[4]);
                if (board.halfMoveClock < 0) {
                    throw new FenParseException("Negative halfmove clock in FEN: " + tokens[4]);
                }
            } catch (NumberFormatException e) {
                throw new FenParseException("Invalid halfmove clock in FEN: " + tokens[4]);
            }
        } else {
            board.halfMoveClock = 0;
        }

        if (tokens.length >= 6) {
            try {
                board.fullMoveNumber = Integer.parseInt(tokens[5]);
                if (board.fullMoveNumber < 1) {
                    throw new FenParseException("Fullmove number must be >= 1 in FEN: " + tokens[5]);
                }
            } catch (NumberFormatException e) {
                throw new FenParseException("Invalid fullmove number in FEN: " + tokens[5]);
            }
        } else {
            board.fullMoveNumber = 1;
        }

        board.zobristKey = board.computeZobristKey();
    }

    public static String toFen(Board board) {
        StringBuilder sb = new StringBuilder(64);

        for (int r = 7; r >= 0; r--) {
            int emptyCount = 0;
            for (int f = 0; f < 8; f++) {
                int sq = Square.of(f, r);
                int piece = board.pieceAt[sq];
                if (piece == Piece.NONE) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount);
                        emptyCount = 0;
                    }
                    sb.append(Piece.toChar(piece, board.colorAt[sq]));
                }
            }
            if (emptyCount > 0) {
                sb.append(emptyCount);
            }
            if (r > 0) {
                sb.append('/');
            }
        }

        sb.append(' ').append(board.sideToMove == Color.WHITE ? 'w' : 'b').append(' ');

        if (board.castlingRights == 0) {
            sb.append('-');
        } else {
            if ((board.castlingRights & Board.CASTLE_WK) != 0) sb.append('K');
            if ((board.castlingRights & Board.CASTLE_WQ) != 0) sb.append('Q');
            if ((board.castlingRights & Board.CASTLE_BK) != 0) sb.append('k');
            if ((board.castlingRights & Board.CASTLE_BQ) != 0) sb.append('q');
        }

        sb.append(' ').append(board.enPassantSquare == Square.NONE ? "-" : Square.toAlgebraic(board.enPassantSquare));
        sb.append(' ').append(board.halfMoveClock);
        sb.append(' ').append(board.fullMoveNumber);

        return sb.toString();
    }
}
