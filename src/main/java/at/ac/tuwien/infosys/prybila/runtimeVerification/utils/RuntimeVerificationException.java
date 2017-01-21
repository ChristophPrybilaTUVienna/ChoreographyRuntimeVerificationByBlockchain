package at.ac.tuwien.infosys.prybila.runtimeVerification.utils;

public class RuntimeVerificationException extends RuntimeException {
    public RuntimeVerificationException() {
    }

    public RuntimeVerificationException(String message) {
        super(message);
    }

    public RuntimeVerificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeVerificationException(Throwable cause) {
        super(cause);
    }

    public RuntimeVerificationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
