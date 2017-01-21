package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model;

import java.io.Serializable;

/**
 * Identification data for a workflow instance.
 */
public class WorkflowInstance implements Serializable {

    private short id;

    public WorkflowInstance(short id) {
        this.id = id;
    }

    public short getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WorkflowInstance that = (WorkflowInstance) o;

        return id == that.id;

    }

    @Override
    public int hashCode() {
        return (int) id;
    }

    @Override
    public String toString() {
        return "WorkflowInstance{" +
                "id='" + id + '\'' +
                '}';
    }
}
