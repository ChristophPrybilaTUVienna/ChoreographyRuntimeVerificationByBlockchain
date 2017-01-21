package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

import java.util.Arrays;

public abstract class BusinessProcessElement {

    public static byte idCounter;

    protected byte id;

    private BPElementType type;

    protected BusinessProcessElement[] precedingElements;

    protected BusinessProcessElement[] followingElements;

    public BusinessProcessElement(byte id, BPElementType type, int sizePreceding, int sizeFollowing) {
        this.type = type;
        this.id = id;
        if (sizePreceding > 0) {
            precedingElements = new BusinessProcessElement[sizePreceding];
        }
        if (sizeFollowing > 0) {
            followingElements = new BusinessProcessElement[sizeFollowing];
        }
    }

    public BusinessProcessElement(BusinessProcessElement clone) {
        this.type = clone.getType();
    }

    public BPElementType getType() {
        return type;
    }

    public Activity addActivity(String name) {
        return addActivity(name, 0);
    }

    public Activity addActivity(String name, int index) {
        Activity activity = new Activity(idCounter++, name);
        followingElements[index] = activity;
        activity.precedingElements[0] = this;
        return activity;
    }

    public End addEnd() {
        return addEnd(0);
    }

    public End addEnd(int index) {
        End end = new End(idCounter++);
        followingElements[index] = end;
        end.precedingElements[0] = this;
        return end;
    }

    public AndSplit addAndSplit(int splitNumber) {
        return addAndSplit(0, splitNumber);
    }

    public AndSplit addAndSplit(int index, int splitNumber) {
        AndSplit andSplit = new AndSplit(idCounter++, splitNumber);
        followingElements[index] = andSplit;
        andSplit.precedingElements[0] = this;
        return andSplit;
    }

    public XORSplit addXORSplit(int splitNumber) {
        return addXORSplit(0, splitNumber);
    }

    public XORSplit addXORSplit(int index, int splitNumber) {
        XORSplit xorSplit = new XORSplit(idCounter++, splitNumber);
        followingElements[index] = xorSplit;
        xorSplit.precedingElements[0] = this;
        return xorSplit;
    }

    public AndJoin addAndJoin(int joinIndex, int joinNumber) {
        return addAndJoin(0, joinIndex, joinNumber);
    }

    public AndJoin addAndJoin(int index, int joinIndex, int joinNumber) {
        AndJoin andJoin = new AndJoin(idCounter++, joinNumber);
        followingElements[index] = andJoin;
        andJoin.precedingElements[joinIndex] = this;
        return andJoin;
    }

    public XORJoin addXORJoin(int joinIndex, int joinNumber) {
        return addXORJoin(0, joinIndex, joinNumber);
    }

    public XORJoin addXORJoin(int index, int joinIndex, int joinNumber) {
        XORJoin xorJoin = new XORJoin(idCounter++, joinNumber);
        followingElements[index] = xorJoin;
        xorJoin.precedingElements[joinIndex] = this;
        return xorJoin;
    }

    @Override
    public String toString() {
        String precedingElementsAsString;
        String followingElementsAsString;
        if (precedingElements == null) {
            precedingElementsAsString = "none";
        } else {
            precedingElementsAsString = getArrayElementsAsString(precedingElements);
            if(precedingElementsAsString == null || precedingElementsAsString.equals("")) {
                precedingElementsAsString = "none";
            }
        }
        if (followingElements == null) {
            followingElementsAsString = "none";
        } else {
            followingElementsAsString = getArrayElementsAsString(followingElements);
            if(followingElementsAsString == null || followingElementsAsString.equals("")) {
                followingElementsAsString = "none";
            }
        }
        String nameAddition = "";
        if (this instanceof Activity) {
            nameAddition = ", name='" + ((Activity) this).getName() + "'";
        }
        String agentIdAddition = "";
        if (this instanceof Activity) {
            agentIdAddition = ", agentId='" + ((Activity) this).getAgentId() + "'";
        } else if (this instanceof Start) {
            agentIdAddition = ", agentId='" + ((Start) this).getAgentId() + "'";
        }
        String executionResultAddition = "";
        if (this instanceof Activity) {
            executionResultAddition = ", executionResult='" + ((Activity) this).getExecutionResult() + "'";
        }
        String isFaulty = "";
        if (this instanceof Activity) {
            executionResultAddition = ", isPerformedIncorrectly='" + ((Activity) this).isPerformedIncorrectly() + "'";
        }
        String pathTakenAddition = "";
        if (this instanceof XORSplit) {
            pathTakenAddition = ", pathTaken='" + ((XORSplit) this).getIndexOfPathToTake() + "'";
        }
        return "BusinessProcessElement{" +
                "id=" + id +
                ", type=" + type.name() +
                nameAddition +
                agentIdAddition +
                executionResultAddition +
                isFaulty +
                pathTakenAddition +
                ", precedingElements=" + precedingElementsAsString +
                ", followingElements=" + followingElementsAsString +
                '}';
    }

    private String getArrayElementsAsString(BusinessProcessElement[] businessProcessElements) {
        String arrayAsString = "";
        for (BusinessProcessElement preceding : businessProcessElements) {
            if(preceding == null) {
                continue;
            }
            String newElement;
            if (preceding instanceof Activity) {
                newElement = ((Activity) preceding).getName();
            } else {
                newElement = preceding.getType().name();
            }
            if (arrayAsString.equals("")) {
                arrayAsString = newElement;
            } else {
                arrayAsString += "," + newElement;
            }
        }
        return arrayAsString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BusinessProcessElement that = (BusinessProcessElement) o;

        if (id != that.id) return false;
        if (type != that.type) return false;
        if (!Arrays.deepEquals(precedingElements, that.precedingElements)) return false;
        return followingElements.length == that.followingElements.length;

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(precedingElements);
        result = 31 * result + followingElements.length;
        return result;
    }

    public byte getId() {
        return id;
    }

    public BusinessProcessElement[] getFollowingElements() {
        return followingElements;
    }

    public BusinessProcessElement[] getPrecedingElements() {
        return precedingElements;
    }
}
