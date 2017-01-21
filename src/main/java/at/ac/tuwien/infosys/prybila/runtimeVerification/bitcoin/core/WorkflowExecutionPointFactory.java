package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverType;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowInstance;

/**
 * Factory to create correct WorkflowHandoverData instances
 */
public class WorkflowExecutionPointFactory {

    public WorkflowHandoverData createWorkflowStartPoint(WorkflowInstance workflowInstance, int timestamp) {
        return new WorkflowHandoverData(
                workflowInstance,
                (byte) 0,
                WorkflowHandoverType.START,
                timestamp);
    }

    public WorkflowHandoverData createWorkflowEndPoint(WorkflowInstance workflowInstance, int timestamp) {
        return new WorkflowHandoverData(
                workflowInstance,
                (byte) 0,
                WorkflowHandoverType.END,
                timestamp);
    }

    public WorkflowHandoverData createWorkflowSplitPoint(WorkflowInstance workflowInstance, int timestamp) {
        return new WorkflowHandoverData(
                workflowInstance,
                (byte) 0,
                WorkflowHandoverType.SPLIT,
                timestamp);
    }

    public WorkflowHandoverData createWorkflowJoinPoint(WorkflowInstance workflowInstance, int timestamp) {
        return new WorkflowHandoverData(
                workflowInstance,
                (byte) 0,
                WorkflowHandoverType.JOIN,
                timestamp);
    }

    public WorkflowHandoverData createWorkflowHandoverPoint(WorkflowInstance workflowInstance, int timestamp, byte taskId) {
        return new WorkflowHandoverData(
                workflowInstance,
                taskId,
                WorkflowHandoverType.INTERMEDIATE,
                timestamp);

    }

}
