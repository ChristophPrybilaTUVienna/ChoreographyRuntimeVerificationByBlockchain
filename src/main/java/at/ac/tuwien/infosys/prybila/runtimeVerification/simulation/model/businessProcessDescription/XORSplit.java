package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

/**
 * AndSplit in a business process.
 */
public class XORSplit extends BusinessProcessElement {

    public XORSplit(byte id, int splitNumber) {
        super(id, BPElementType.XOR_SPLIT, 1, splitNumber);
    }

    /*############### Meta data for execution ############### */

    private int indexOfPathToTake = -1;

    public int getIndexOfPathToTake() {
        return indexOfPathToTake;
    }

    public void setIndexOfPathToTake(int indexOfPathToTake) {
        this.indexOfPathToTake = indexOfPathToTake;
    }
}
