package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

/**
 * End in a business process to be executed by a single actor.
 */
public class End extends BusinessProcessElement {

    public End(byte id) {
        super(id, BPElementType.END, 1, -1);
    }

}
