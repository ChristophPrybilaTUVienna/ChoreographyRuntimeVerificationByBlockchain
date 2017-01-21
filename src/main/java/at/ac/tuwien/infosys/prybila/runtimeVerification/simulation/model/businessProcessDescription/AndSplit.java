package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

/**
 * AndSplit in a business process.
 */
public class AndSplit extends BusinessProcessElement {

    public AndSplit(byte id, int splitNumber) {
        super(id, BPElementType.AND_SPLIT, 1, splitNumber);
    }

}
