package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.exceptions;

/**
 * Exception to throw if the handover receiver recognizes a fault.
 */
public class RecognizedFaultException extends Exception {
    public RecognizedFaultException() {
        super();
    }

    public RecognizedFaultException(String message) {
        super(message);
    }

    public RecognizedFaultException(String message, Throwable cause) {
        super(message, cause);
    }

    public RecognizedFaultException(Throwable cause) {
        super(cause);
    }

    protected RecognizedFaultException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
