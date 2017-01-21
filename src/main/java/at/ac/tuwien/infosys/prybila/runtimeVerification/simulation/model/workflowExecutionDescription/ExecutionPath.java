package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.Simulator;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.dataGeneration.ExecutionPathGenerator;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.BPElementType;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.BusinessProcessDescription;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.BusinessProcessElement;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;

import java.util.List;

/**
 * Execution path of a process description
 */
public class ExecutionPath {

    private BusinessProcessDescription businessProcessDescription;

    private short instanceId;

    private int numberOfTasks;

    private int numberOfSplits;

    public ExecutionPath(BusinessProcessDescription businessProcessDescription) {
        this.businessProcessDescription = businessProcessDescription;
        ExecutionPathGenerator.instanceIdCounter += Simulator.testNumber;
        this.instanceId = ExecutionPathGenerator.instanceIdCounter;
    }

    /**
     * Sets the numberOfTasks and other similar variables
     */
    public void calculateProcessProperties() {
        numberOfSplits = 0;
        numberOfTasks = 0;
        List<BusinessProcessElement> businessProcessElementList = new RuntimeVerificationUtils().graphToList(businessProcessDescription.getStart());
        for (BusinessProcessElement element : businessProcessElementList) {
            if (element.getType() == BPElementType.ACTIVITY) {
                numberOfTasks++;
            } else if (element.getType() == BPElementType.AND_SPLIT) {
                numberOfSplits++;
            }
            //XOR_SPLITS / Joins are resolved to a singular path
        }
    }

    public BusinessProcessDescription getBusinessProcessDescription() {
        return businessProcessDescription;
    }

    public void setBusinessProcessDescription(BusinessProcessDescription businessProcessDescription) {
        this.businessProcessDescription = businessProcessDescription;
    }

    public short getInstanceId() {
        return instanceId;
    }

    public int getNumberOfTasks() {
        return numberOfTasks;
    }

    public int getNumberOfSplits() {
        return numberOfSplits;
    }

    @Override
    public String toString() {
        return "ExecutionPath{" +
                "businessProcessDescription=" + businessProcessDescription +
                ", instanceId=" + instanceId +
                ", numberOfTasks=" + numberOfTasks +
                ", numberOfSplits=" + numberOfSplits +
                '}';
    }
}
