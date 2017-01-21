package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.exceptions;

/**
 * Exception to be thrown if something during the runtime verification handover fails.
 */
public class HandoverFailureException extends Exception {

    public HandoverFailureException() {
        super();
    }

    public HandoverFailureException(String message) {
        super(message);
    }

    public HandoverFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public HandoverFailureException(Throwable cause) {
        super(cause);
    }

    protected HandoverFailureException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
