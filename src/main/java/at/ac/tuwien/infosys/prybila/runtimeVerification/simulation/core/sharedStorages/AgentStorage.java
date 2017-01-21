package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.SimulationAgent;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Distributes the network contact information of all agents
 */
public class AgentStorage {


    private Map<String, Integer> portsToCompanies;

    public AgentStorage(List<SimulationAgent> agents) {
        new RuntimeVerificationUtils().notNull(agents);
        portsToCompanies = new HashMap<>();
        for (SimulationAgent simulationAgent : agents) {
            portsToCompanies.put(simulationAgent.getAgendId(), simulationAgent.getPortToUse());
        }
    }

    /**
     * Returns the publicly available identity data of the given companies.
     */
    public synchronized Set<String> getAvailableAgents() {
        return portsToCompanies.keySet();
    }

    /**
     * Returns the publicly available port data of the given company or -1.
     */
    public synchronized int getPortOfAgent(String company) {
        if (portsToCompanies.containsKey(company)) {
            return portsToCompanies.get(company);
        }
        return -1;
    }
}
