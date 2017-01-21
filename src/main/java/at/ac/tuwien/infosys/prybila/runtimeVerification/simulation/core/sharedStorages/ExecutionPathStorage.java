package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;

import java.util.List;

/**
 * Distributes the execution path information, predefined by the simulation.
 */
public class ExecutionPathStorage {

    private List<ExecutionPath> definedExecutionPaths;

    public ExecutionPathStorage(List<ExecutionPath> definedExecutionPaths) {
        this.definedExecutionPaths = definedExecutionPaths;
    }

    public ExecutionPath getExecutionPathOfInstance(short instanceId) {
        for (ExecutionPath executionPath : definedExecutionPaths) {
            if (executionPath.getInstanceId() == instanceId) {
                return executionPath;
            }
        }
        return null;
    }
}
