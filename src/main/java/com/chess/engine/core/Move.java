package com.chess.engine.core;

public final class Move {
    public static final int NONE = 0;

    public static final int FLAG_QUIET = 0;
    public static final int FLAG_DOUBLE_PAWN_PUSH = 1;
    public static final int FLAG_KING_CASTLE = 2;
    public static final int FLAG_QUEEN_CASTLE = 3;
    public static final int FLAG_CAPTURE = 4;
    public static final int FLAG_EN_PASSANT = 5;

    public static final int FLAG_PROMO_KNIGHT = 8;
    public static final int FLAG_PROMO_BISHOP = 9;
    public static final int FLAG_PROMO_ROOK = 10;
    public static final int FLAG_PROMO_QUEEN = 11;

    public static final int FLAG_PROMO_CAP_KNIGHT = 12;
    public static final int FLAG_PROMO_CAP_BISHOP = 13;
    public static final int FLAG_PROMO_CAP_ROOK = 14;
    public static final int FLAG_PROMO_CAP_QUEEN = 15;

    private Move() {}

    public static int of(int from, int to, int flags, int movedPiece, int capturedPiece) {
        return (from & 0x3F)
             | ((to & 0x3F) << 6)
             | ((flags & 0xF) << 12)
             | ((movedPiece & 0xF) << 16)
             | ((capturedPiece & 0xF) << 20);
    }

    public static int from(int move) {
        return move & 0x3F;
    }

    public static int to(int move) {
        return (move >>> 6) & 0x3F;
    }

    public static int flags(int move) {
        return (move >>> 12) & 0xF;
    }

    public static int movedPiece(int move) {
        return (move >>> 16) & 0xF;
    }

    public static int capturedPiece(int move) {
        return (move >>> 20) & 0xF;
    }

    public static boolean isCapture(int move) {
        return (flags(move) & 4) != 0;
    }

    public static boolean isPromotion(int move) {
        return (flags(move) & 8) != 0;
    }

    public static boolean isEnPassant(int move) {
        return flags(move) == FLAG_EN_PASSANT;
    }

    public static boolean isCastling(int move) {
        int f = flags(move);
        return f == FLAG_KING_CASTLE || f == FLAG_QUEEN_CASTLE;
    }

    public static int promotedPiece(int move) {
        return switch (flags(move) & 11) {
            case FLAG_PROMO_KNIGHT -> Piece.KNIGHT;
            case FLAG_PROMO_BISHOP -> Piece.BISHOP;
            case FLAG_PROMO_ROOK -> Piece.ROOK;
            case FLAG_PROMO_QUEEN -> Piece.QUEEN;
            default -> Piece.NONE;
        };
    }

    public static String toUci(int move) {
        if (move == NONE) return "0000";
        String base = Square.toAlgebraic(from(move)) + Square.toAlgebraic(to(move));
        if (isPromotion(move)) {
            char promoChar = switch (promotedPiece(move)) {
                case Piece.KNIGHT -> 'n';
                case Piece.BISHOP -> 'b';
                case Piece.ROOK -> 'r';
                default -> 'q';
            };
            return base + promoChar;
        }
        return base;
    }
}
