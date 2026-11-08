package com.chess.engine.core;

import com.chess.engine.fen.FenParser;
import java.util.Arrays;

public final class Board {
    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static final int CASTLE_WK = 1;
    public static final int CASTLE_WQ = 2;
    public static final int CASTLE_BK = 4;
    public static final int CASTLE_BQ = 8;

    private static final int MAX_GAME_PLY = 2048;
    private static final int[] CASTLING_RIGHTS_MASK = new int[64];

    static {
        Arrays.fill(CASTLING_RIGHTS_MASK, 15);
        CASTLING_RIGHTS_MASK[Square.A1] &= ~CASTLE_WQ;
        CASTLING_RIGHTS_MASK[Square.E1] &= ~(CASTLE_WK | CASTLE_WQ);
        CASTLING_RIGHTS_MASK[Square.H1] &= ~CASTLE_WK;
        CASTLING_RIGHTS_MASK[Square.A8] &= ~CASTLE_BQ;
        CASTLING_RIGHTS_MASK[Square.E8] &= ~(CASTLE_BK | CASTLE_BQ);
        CASTLING_RIGHTS_MASK[Square.H8] &= ~CASTLE_BK;
    }

    public final long[][] pieceBitboards = new long[2][7];
    public final long[] colorBitboards = new long[2];
    public long occupiedBitboard = 0L;

    public final int[] pieceAt = new int[64];
    public final int[] colorAt = new int[64];

    public int sideToMove = Color.WHITE;
    public int castlingRights = 0;
    public int enPassantSquare = Square.NONE;
    public int halfMoveClock = 0;
    public int fullMoveNumber = 1;
    public long zobristKey = 0L;

    private final int[] historyCastling = new int[MAX_GAME_PLY];
    private final int[] historyEpSquare = new int[MAX_GAME_PLY];
    private final int[] historyHalfMove = new int[MAX_GAME_PLY];
    private final long[] historyZobrist = new long[MAX_GAME_PLY];
    private int historyPly = 0;

    public Board() {
        clear();
    }

    public void clear() {
        for (int c = 0; c < 2; c++) {
            Arrays.fill(pieceBitboards[c], 0L);
            colorBitboards[c] = 0L;
        }
        occupiedBitboard = 0L;
        Arrays.fill(pieceAt, Piece.NONE);
        Arrays.fill(colorAt, Color.NONE);
        sideToMove = Color.WHITE;
        castlingRights = 0;
        enPassantSquare = Square.NONE;
        halfMoveClock = 0;
        fullMoveNumber = 1;
        zobristKey = 0L;
        historyPly = 0;
    }

    public void setStartPosition() {
        FenParser.loadFen(this, START_FEN);
    }

    public void loadFen(String fen) {
        FenParser.loadFen(this, fen);
    }

    public String toFen() {
        return FenParser.toFen(this);
    }

    public void putPiece(int color, int pieceType, int sq) {
        long bit = 1L << sq;
        pieceBitboards[color][pieceType] |= bit;
        colorBitboards[color] |= bit;
        occupiedBitboard |= bit;
        pieceAt[sq] = pieceType;
        colorAt[sq] = color;
        zobristKey ^= Zobrist.PIECE_KEYS[color][pieceType][sq];
    }

    public void removePiece(int color, int pieceType, int sq) {
        long bit = ~(1L << sq);
        pieceBitboards[color][pieceType] &= bit;
        colorBitboards[color] &= bit;
        occupiedBitboard &= bit;
        pieceAt[sq] = Piece.NONE;
        colorAt[sq] = Color.NONE;
        zobristKey ^= Zobrist.PIECE_KEYS[color][pieceType][sq];
    }

    public long computeZobristKey() {
        long key = 0L;
        for (int sq = 0; sq < 64; sq++) {
            int p = pieceAt[sq];
            if (p != Piece.NONE) {
                int c = colorAt[sq];
                key ^= Zobrist.PIECE_KEYS[c][p][sq];
            }
        }
        key ^= Zobrist.CASTLING_KEYS[castlingRights & 15];
        if (enPassantSquare != Square.NONE) {
            key ^= Zobrist.EN_PASSANT_KEYS[enPassantSquare];
        }
        if (sideToMove == Color.BLACK) {
            key ^= Zobrist.SIDE_KEY;
        }
        return key;
    }

    public int kingSquare(int color) {
        long kingBb = pieceBitboards[color][Piece.KING];
        return kingBb != 0L ? Long.numberOfTrailingZeros(kingBb) : Square.NONE;
    }

    public boolean isSquareAttacked(int sq, int attackerColor) {
        int defenderColor = Color.opposite(attackerColor);
        if ((Bitboard.PAWN_ATTACKS[defenderColor][sq] & pieceBitboards[attackerColor][Piece.PAWN]) != 0L) {
            return true;
        }
        if ((Bitboard.KNIGHT_ATTACKS[sq] & pieceBitboards[attackerColor][Piece.KNIGHT]) != 0L) {
            return true;
        }
        if ((Bitboard.KING_ATTACKS[sq] & pieceBitboards[attackerColor][Piece.KING]) != 0L) {
            return true;
        }
        long bishopsAndQueens = pieceBitboards[attackerColor][Piece.BISHOP] | pieceBitboards[attackerColor][Piece.QUEEN];
        if (bishopsAndQueens != 0L && (Bitboard.getBishopAttacks(sq, occupiedBitboard) & bishopsAndQueens) != 0L) {
            return true;
        }
        long rooksAndQueens = pieceBitboards[attackerColor][Piece.ROOK] | pieceBitboards[attackerColor][Piece.QUEEN];
        if (rooksAndQueens != 0L && (Bitboard.getRookAttacks(sq, occupiedBitboard) & rooksAndQueens) != 0L) {
            return true;
        }
        return false;
    }

    public boolean inCheck() {
        int kSq = kingSquare(sideToMove);
        return kSq != Square.NONE && isSquareAttacked(kSq, Color.opposite(sideToMove));
    }

    public boolean makeMove(int move) {
        int us = sideToMove;
        int them = Color.opposite(us);

        historyCastling[historyPly] = castlingRights;
        historyEpSquare[historyPly] = enPassantSquare;
        historyHalfMove[historyPly] = halfMoveClock;
        historyZobrist[historyPly] = zobristKey;
        historyPly++;

        if (enPassantSquare != Square.NONE) {
            zobristKey ^= Zobrist.EN_PASSANT_KEYS[enPassantSquare];
            enPassantSquare = Square.NONE;
        }

        zobristKey ^= Zobrist.CASTLING_KEYS[castlingRights];

        int from = Move.from(move);
        int to = Move.to(move);
        int flags = Move.flags(move);
        int moved = Move.movedPiece(move);
        int captured = Move.capturedPiece(move);

        removePiece(us, moved, from);

        if (Move.isCapture(move)) {
            if (flags == Move.FLAG_EN_PASSANT) {
                int capSq = (us == Color.WHITE) ? (to - 8) : (to + 8);
                removePiece(them, Piece.PAWN, capSq);
            } else {
                removePiece(them, captured, to);
            }
        }

        if (Move.isPromotion(move)) {
            putPiece(us, Move.promotedPiece(move), to);
        } else {
            putPiece(us, moved, to);
        }

        if (flags == Move.FLAG_KING_CASTLE) {
            if (us == Color.WHITE) {
                removePiece(Color.WHITE, Piece.ROOK, Square.H1);
                putPiece(Color.WHITE, Piece.ROOK, Square.F1);
            } else {
                removePiece(Color.BLACK, Piece.ROOK, Square.H8);
                putPiece(Color.BLACK, Piece.ROOK, Square.F8);
            }
        } else if (flags == Move.FLAG_QUEEN_CASTLE) {
            if (us == Color.WHITE) {
                removePiece(Color.WHITE, Piece.ROOK, Square.A1);
                putPiece(Color.WHITE, Piece.ROOK, Square.D1);
            } else {
                removePiece(Color.BLACK, Piece.ROOK, Square.A8);
                putPiece(Color.BLACK, Piece.ROOK, Square.D8);
            }
        } else if (flags == Move.FLAG_DOUBLE_PAWN_PUSH) {
            enPassantSquare = (us == Color.WHITE) ? (from + 8) : (from - 8);
            zobristKey ^= Zobrist.EN_PASSANT_KEYS[enPassantSquare];
        }

        castlingRights &= CASTLING_RIGHTS_MASK[from] & CASTLING_RIGHTS_MASK[to];
        zobristKey ^= Zobrist.CASTLING_KEYS[castlingRights];

        if (moved == Piece.PAWN || Move.isCapture(move)) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        if (us == Color.BLACK) {
            fullMoveNumber++;
        }

        sideToMove = them;
        zobristKey ^= Zobrist.SIDE_KEY;

        int ourKingSq = kingSquare(us);
        if (ourKingSq == Square.NONE || isSquareAttacked(ourKingSq, them)) {
            unmakeMove(move);
            return false;
        }
        return true;
    }

    public void unmakeMove(int move) {
        int them = sideToMove;
        int us = Color.opposite(them);
        sideToMove = us;

        if (us == Color.BLACK) {
            fullMoveNumber--;
        }

        int from = Move.from(move);
        int to = Move.to(move);
        int flags = Move.flags(move);
        int moved = Move.movedPiece(move);
        int captured = Move.capturedPiece(move);

        if (Move.isPromotion(move)) {
            removePiece(us, Move.promotedPiece(move), to);
        } else {
            removePiece(us, moved, to);
        }

        putPiece(us, moved, from);

        if (Move.isCapture(move)) {
            if (flags == Move.FLAG_EN_PASSANT) {
                int capSq = (us == Color.WHITE) ? (to - 8) : (to + 8);
                putPiece(them, Piece.PAWN, capSq);
            } else {
                putPiece(them, captured, to);
            }
        }

        if (flags == Move.FLAG_KING_CASTLE) {
            if (us == Color.WHITE) {
                removePiece(Color.WHITE, Piece.ROOK, Square.F1);
                putPiece(Color.WHITE, Piece.ROOK, Square.H1);
            } else {
                removePiece(Color.BLACK, Piece.ROOK, Square.F8);
                putPiece(Color.BLACK, Piece.ROOK, Square.H8);
            }
        } else if (flags == Move.FLAG_QUEEN_CASTLE) {
            if (us == Color.WHITE) {
                removePiece(Color.WHITE, Piece.ROOK, Square.D1);
                putPiece(Color.WHITE, Piece.ROOK, Square.A1);
            } else {
                removePiece(Color.BLACK, Piece.ROOK, Square.D8);
                putPiece(Color.BLACK, Piece.ROOK, Square.A8);
            }
        }

        historyPly--;
        castlingRights = historyCastling[historyPly];
        enPassantSquare = historyEpSquare[historyPly];
        halfMoveClock = historyHalfMove[historyPly];
        zobristKey = historyZobrist[historyPly];
    }

    public void makeNullMove() {
        historyCastling[historyPly] = castlingRights;
        historyEpSquare[historyPly] = enPassantSquare;
        historyHalfMove[historyPly] = halfMoveClock;
        historyZobrist[historyPly] = zobristKey;
        historyPly++;

        if (enPassantSquare != Square.NONE) {
            zobristKey ^= Zobrist.EN_PASSANT_KEYS[enPassantSquare];
            enPassantSquare = Square.NONE;
        }
        halfMoveClock++;
        sideToMove = Color.opposite(sideToMove);
        zobristKey ^= Zobrist.SIDE_KEY;
    }

    public void unmakeNullMove() {
        sideToMove = Color.opposite(sideToMove);
        historyPly--;
        castlingRights = historyCastling[historyPly];
        enPassantSquare = historyEpSquare[historyPly];
        halfMoveClock = historyHalfMove[historyPly];
        zobristKey = historyZobrist[historyPly];
    }

    public boolean isRepetition() {
        int limit = Math.max(0, historyPly - halfMoveClock);
        for (int i = historyPly - 2; i >= limit; i -= 2) {
            if (historyZobrist[i] == zobristKey) {
                return true;
            }
        }
        return false;
    }

    public boolean hasNonPawnMaterial(int color) {
        return (pieceBitboards[color][Piece.KNIGHT]
              | pieceBitboards[color][Piece.BISHOP]
              | pieceBitboards[color][Piece.ROOK]
              | pieceBitboards[color][Piece.QUEEN]) != 0L;
    }

    public boolean isInsufficientMaterial() {
        if ((pieceBitboards[Color.WHITE][Piece.PAWN] | pieceBitboards[Color.BLACK][Piece.PAWN]
           | pieceBitboards[Color.WHITE][Piece.ROOK] | pieceBitboards[Color.BLACK][Piece.ROOK]
           | pieceBitboards[Color.WHITE][Piece.QUEEN] | pieceBitboards[Color.BLACK][Piece.QUEEN]) != 0L) {
            return false;
        }
        int whiteMinors = Long.bitCount(pieceBitboards[Color.WHITE][Piece.KNIGHT] | pieceBitboards[Color.WHITE][Piece.BISHOP]);
        int blackMinors = Long.bitCount(pieceBitboards[Color.BLACK][Piece.KNIGHT] | pieceBitboards[Color.BLACK][Piece.BISHOP]);
        return whiteMinors <= 1 && blackMinors <= 1 && (whiteMinors + blackMinors <= 1);
    }

    public int historyPly() {
        return historyPly;
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("  +------------------------+\n");
        for (int r = 7; r >= 0; r--) {
            sb.append(r + 1).append(" | ");
            for (int f = 0; f < 8; f++) {
                int sq = Square.of(f, r);
                char c = Piece.toChar(pieceAt[sq], colorAt[sq]);
                sb.append(c).append("  ");
            }
            sb.append("|\n");
        }
        sb.append("  +------------------------+\n");
        sb.append("    a  b  c  d  e  f  g  h\n");
        sb.append("FEN: ").append(toFen()).append("\n");
        sb.append(String.format("Zobrist: 0x%016XL", zobristKey));
        return sb.toString();
    }
}
