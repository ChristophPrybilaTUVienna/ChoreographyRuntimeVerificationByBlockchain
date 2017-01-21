package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

/**
 * Simple Activity in a business process to be executed by a single actor.
 */
public class Activity extends BusinessProcessElement {

    private String name;

    public Activity(byte id, String name) {
        super(id, BPElementType.ACTIVITY, 1, 1);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /*############### Meta data for execution ############### */

    /**
     * Agent which performs this task
     */
    private String agentId;

    /**
     * Result of the execution of this activity.
     */
    private String executionResult;

    /**
     * The agent handing over this activity must try to perform incorrect behaviour.
     */
    private boolean performedIncorrectly;

    public String getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public boolean isPerformedIncorrectly() {
        return performedIncorrectly;
    }

    public void setPerformedIncorrectly(boolean performedIncorrectly) {
        this.performedIncorrectly = performedIncorrectly;
    }
}
