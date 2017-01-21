package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

/**
 * AndJoin in a business process.
 */
public class XORJoin extends BusinessProcessElement {

    public XORJoin(byte id, int joinNumber) {
        super(id, BPElementType.XOR_JOIN, joinNumber, 1);
    }

    public void addPrecedingElement(int joinIndex, BusinessProcessElement element) {
        addPrecedingElement(joinIndex, 0, element);
    }

    public void addPrecedingElement(int joinIndex, int followingIndex, BusinessProcessElement element) {
        precedingElements[joinIndex] = element;
        element.followingElements[followingIndex] = this;
    }

}
