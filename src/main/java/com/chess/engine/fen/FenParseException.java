package com.chess.engine.fen;

public final class FenParseException extends IllegalArgumentException {
    public FenParseException(String message) {
        super(message);
    }
}
