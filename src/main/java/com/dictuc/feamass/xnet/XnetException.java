package com.dictuc.feamass.xnet;

public final class XnetException extends Exception {

    public enum Kind {
        NETWORK,
        UNAUTHENTICATED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        VALIDATION,
        SERVER_ERROR
    }

    private final Kind kind;
    private final int httpStatus;

    public XnetException(Kind kind, int httpStatus, String message) {
        super(message);
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public XnetException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = -1;
    }

    public Kind kind() {
        return kind;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
