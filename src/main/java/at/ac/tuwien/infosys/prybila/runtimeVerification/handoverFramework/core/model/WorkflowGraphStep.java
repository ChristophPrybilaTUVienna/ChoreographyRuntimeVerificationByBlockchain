package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.HandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverType;

import java.io.Serializable;
import java.util.List;

/**
 * Execution graph of a workflow instance
 */
public class WorkflowGraphStep implements Serializable {

    /**
     * Unique id for this graphstep
     */
    private int id;

    //########## Handover conducted on reach this specific node.
    /**
     * Sender of handover
     */
    private Identity from;

    /**
     * Receiver of handover
     */
    private Identity to;

    private HandoverData handoverData;
    //########## Handover conducted on reach this specific node.

    private List<WorkflowGraphStep> parents;

    private List<WorkflowGraphStep> children;

    public WorkflowGraphStep() {

    }

    public WorkflowGraphStep(Identity from, Identity to, HandoverData handoverData, List<WorkflowGraphStep> parents, List<WorkflowGraphStep> children, int id) {
        this.from = from;
        this.to = to;
        this.handoverData = handoverData;
        this.parents = parents;
        this.children = children;
        this.id = id;
    }

    public HandoverData getHandoverData() {
        return handoverData;
    }

    public void setHandoverData(HandoverData handoverData) {
        this.handoverData = handoverData;
    }

    public List<WorkflowGraphStep> getParents() {
        return parents;
    }

    public void setParents(List<WorkflowGraphStep> parents) {
        this.parents = parents;
    }

    public List<WorkflowGraphStep> getChildren() {
        return children;
    }

    public void setChildren(List<WorkflowGraphStep> children) {
        this.children = children;
    }

    public Identity getFrom() {
        return from;
    }

    public void setFrom(Identity from) {
        this.from = from;
    }

    public Identity getTo() {
        return to;
    }

    public void setTo(Identity to) {
        this.to = to;
    }

    public boolean wasInitiatedByUs() {
        return handoverData.isSender();
    }

    public boolean isStart() {
        return handoverData.getWorkflowHandoverData().getWorkflowHandoverType() == WorkflowHandoverType.START;
    }

    public boolean isEnd() {
        return handoverData.getWorkflowHandoverData().getWorkflowHandoverType() == WorkflowHandoverType.END;
    }

    public boolean isIntermediate() {
        return handoverData.getWorkflowHandoverData().getWorkflowHandoverType() == WorkflowHandoverType.INTERMEDIATE;
    }

    public boolean isJoin() {
        return handoverData.getWorkflowHandoverData().getWorkflowHandoverType() == WorkflowHandoverType.JOIN;
    }

    public boolean isSplit() {
        return handoverData.getWorkflowHandoverData().getWorkflowHandoverType() == WorkflowHandoverType.SPLIT;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WorkflowGraphStep that = (WorkflowGraphStep) o;

        if (from != null ? !from.equals(that.from) : that.from != null) return false;
        if (to != null ? !to.equals(that.to) : that.to != null) return false;
        if (id != that.id) return false;
        if (handoverData != null ? !handoverData.equals(that.handoverData) : that.handoverData != null) return false;
        if (parents != null && that.parents == null) return false;
        if (parents == null && that.parents != null) return false;
        if (parents != null && that.parents != null) {
            if (parents.size() != that.parents.size()) {
                return false;
            }
        }
        return children != null ? children.equals(that.children) : that.children == null;

    }

    @Override
    public int hashCode() {
        int result = from != null ? from.hashCode() : 0;
        result = 31 * result + (to != null ? to.hashCode() : 0);
        result = 31 * result + (handoverData != null ? handoverData.hashCode() : 0);
        result = 31 * result + (parents != null ? parents.hashCode() : 0);
        result = 31 * result + id;
        result = 31 * result + (children != null ? children.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WorkflowGraphStep{" +
                "id=" + id +
                "from=" + from +
                ", to=" + to +
                ", handoverData=" + handoverData +
                ", parents=" + (parents != null ? "" + parents.size() : "null") +
                ", children=" + (children != null ? "" + children.size() : "null") +
                '}';
    }
}
