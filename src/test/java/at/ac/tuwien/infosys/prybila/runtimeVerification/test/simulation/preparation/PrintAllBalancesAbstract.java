package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.SimulationAgent;

/**
 * Prints the balances of all testnet agents
 */
public abstract class PrintAllBalancesAbstract extends AbstractIterativeContextLoader {

    @Override
    public void performAction() {
        for (SimulationAgent simulationAgent : agents) {
            simulationAgent.printBalanceOfWalletAndReceiveAddress();
        }
    }

}
