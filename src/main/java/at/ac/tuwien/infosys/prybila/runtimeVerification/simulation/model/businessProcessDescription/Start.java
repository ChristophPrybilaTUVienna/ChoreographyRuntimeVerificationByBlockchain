package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

public class Start extends BusinessProcessElement {

    public Start() {
        super(idCounter++, BPElementType.START, -1, 1);
    }

    /*############### Meta data for execution ############### */

    /**
     * Agent which performs this task
     */
    private String agentId;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

}
