package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model;

public class IdProvider {

    private int idCounter;

    public IdProvider() {
        idCounter = 0;
    }

    public int getNextId() {
        idCounter++;
        return idCounter;
    }

    public int peekId() {
        return idCounter;
    }

}
