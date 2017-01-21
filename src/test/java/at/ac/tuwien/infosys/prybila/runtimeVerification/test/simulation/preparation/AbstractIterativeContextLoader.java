package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.SimulationAgent;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.util.List;

/**
 * Loads iteratively all partial contexts. Avoids to load all wallets at once.
 *
 * The bitcoinJ framework was never intended to load multiple wallets at the same time.
 * Loading more than four wallets into one JVM at once causes the wallet-synchronization process to fail.
 */
public abstract class AbstractIterativeContextLoader {

    protected List<SimulationAgent> agents;

    private ApplicationContext context;

    protected String netToUse;

    @Test
    public void iterateOverAllWalletsWithAction() {
        selectivelyPerformNotReadyCheck("AgentSetOne");
        selectivelyPerformNotReadyCheck("AgentSetTwo");
        selectivelyPerformNotReadyCheck("AgentSetThree");
    }

    @Test
    public void iterateOverAgentSetOneWithAction() {
        selectivelyPerformNotReadyCheck("AgentSetOne");
    }

    @Test
    public void iterateOverAgentSetTwoWithAction() {
        selectivelyPerformNotReadyCheck("AgentSetTwo");
    }

    @Test
    public void iterateOverAgentSetThreeWithAction() {
        selectivelyPerformNotReadyCheck("AgentSetThree");
    }

    private void selectivelyPerformNotReadyCheck(String agentSet) {
        openContext(agentSet);
        performAction();
        closeContext();
    }

    private void openContext(String agentSet) {
        String appContext = "applicationContext_" + agentSet + File.separator + "applicationContext" + netToUse + "Simulation_" + agentSet + ".xml";
        context = new ClassPathXmlApplicationContext(appContext);
        agents = (List<SimulationAgent>) context.getBean("agents");
    }

    private void closeContext() {
        ((ConfigurableApplicationContext) context).close();
    }

    public abstract void performAction();

}
