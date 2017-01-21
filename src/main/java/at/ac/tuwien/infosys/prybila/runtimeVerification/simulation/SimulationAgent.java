package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowInstance;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.OwnIdentityProvider;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.BitcoinRuntimeVerifierServer;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.SimulationExecutionVerification;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.StartClientThread;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.AgentStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.ExecutionPathStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.IdentityStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent thread, representing a single company.
 */
public class SimulationAgent extends Thread {

    private static final double testnetBalanceThreshold = 2000000;
    private static final double mainnetBalanceThreshold = 200000;

    private static final String msgFormatter = "Agent %s (%s): %s";

    /**
     * Id of company
     */
    private String agendId;

    /**
     * Runtime verification framework of company
     */
    private WorkflowHandoverManager workflowHandoverManager;

    /**
     * RSA information provider of company
     */
    private OwnIdentityProvider ownIdentityProvider;

    /**
     * RSA information of company
     */
    private Identity ownIdentity;

    /**
     * Storage providing public RSA information of all agents.
     */
    @Autowired
    private IdentityStorage identityStorage;

    /**
     * Storage providing public network contact information of all agents.
     */
    private AgentStorage agentStorage;

    /**
     * Each company starts a server to listen for incoming handover requests
     */
    private BitcoinRuntimeVerifierServer bitcoinRuntimeVerifierServer;

    private Logger logger = LoggerFactory.getLogger(SimulationAgent.class);

    private RuntimeVerificationUtils utils;

    private int portToUse;

    private List<ExecutionPath> ourExecutions;

    private AtomicBoolean stopServer = new AtomicBoolean();

    private AtomicBoolean stopped = new AtomicBoolean();

    private AtomicBoolean allOwnedProcessesHaveFinished = new AtomicBoolean();

    private ExecutionPathStorage executionPathStorage;

    public SimulationAgent(WorkflowHandoverManager workflowHandoverManager, int portToUse) {
        this.workflowHandoverManager = workflowHandoverManager;
        utils = new RuntimeVerificationUtils();
        utils.notNull(workflowHandoverManager);
        ownIdentityProvider = workflowHandoverManager.getOwnIdentityProvider();
        ownIdentity = ownIdentityProvider.getOwnIdentity();
        agendId = ownIdentity.getCompanyName();
        this.portToUse = portToUse;
        bitcoinRuntimeVerifierServer = new BitcoinRuntimeVerifierServer(
                portToUse, ownIdentityProvider, workflowHandoverManager);
    }

    @Override
    public void run() {
        stopped.set(false);
        bitcoinRuntimeVerifierServer.setAgentStorage(agentStorage);
        bitcoinRuntimeVerifierServer.setIdentityStorage(identityStorage);
        bitcoinRuntimeVerifierServer.setExecutionPathStorage(executionPathStorage);

        String logPrefix = String.format(msgFormatter, agendId, portToUse, "");

        stopServer.set(false);
        allOwnedProcessesHaveFinished.set(false);
        logger.info(logPrefix + "start listening to incoming handovers");
        bitcoinRuntimeVerifierServer.start();

        //wait for others to start
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info(logPrefix + "start the execution of our own workflows");
        for (ExecutionPath executionPath : ourExecutions) {
            logger.info(logPrefix + "run thread to handle workflow start of wfId " + executionPath.getInstanceId());
            StartClientThread startClientThread =
                    new StartClientThread(workflowHandoverManager,
                            ownIdentity,
                            identityStorage,
                            agentStorage,
                            executionPath);
            startClientThread.start();
        }

        logger.info(logPrefix + "Start checking, if our own processes have finished.");
        int tryCounter = 0;
        while (!allOwnedProcessesHaveFinished.get()) {
            try {
                Thread.sleep(Simulator.waitForFinishIntervalMS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            tryCounter++;
            boolean processHaveFinishedCheck = true;
            if (Simulator.useRuntimeVerification) {
                for (ExecutionPath ownedWorkflow : ourExecutions) {
                    boolean wfEnded;
                    try {
                        if (tryCounter % 30 == 0) {
                            workflowHandoverManager.acquireLock();
                            workflowHandoverManager.updateWFHandoverDataIfExistsForWorflow(ownedWorkflow.getInstanceId());
                        }
                        wfEnded = workflowHandoverManager.workflowWasEnded(ownedWorkflow.getInstanceId());
                    } catch (Exception e) {
                        logger.warn("An exception occurred during the wfWasEnded check of workflow " + ownedWorkflow.getInstanceId(), e);
                        if (tryCounter > 5) {
                            logger.warn("The wasEnd check failed over five times. Ending the agent.");
                            break;
                        }
                        continue;
                    }
                    if (wfEnded) {
                        String logPrefix2 = "SimulationAgent(" + agendId + ",wfId:" + ownedWorkflow.getInstanceId() + "): ";
                        logger.info(logPrefix2 + "Workflow end was reached for. Waiting for confirmation.");
                        try {
                            workflowHandoverManager
                                    .waitForConfirmationOnPossibleHandoversForInstance(
                                            new WorkflowInstance(ownedWorkflow.getInstanceId()));
                            logger.info(logPrefix2 + "All transactions have been confirmed.");
                        } catch (Exception e) {
                            logger.warn("An error occurred during waiting for confirmation on ended wf. Maybe it was not yet completely confirmed", e);
                            processHaveFinishedCheck = false;
                            break;
                        }
                    } else {
                        processHaveFinishedCheck = false;
                        break;
                    }
                }
            } else {
                List<Short> ourWorkflowIds = new ArrayList<>();
                for (ExecutionPath ourWorkflow : ourExecutions) {
                    ourWorkflowIds.add(ourWorkflow.getInstanceId());
                }
                processHaveFinishedCheck = Simulator.workflowsFinished.containsAll(ourWorkflowIds);
            }
            allOwnedProcessesHaveFinished.set(processHaveFinishedCheck);
        }
        logger.info(logPrefix + "all our processes have finished");

        logger.info(logPrefix + "wait for stopping command.");
        while (!stopServer.get()) {
            try {
                if (Simulator.useRuntimeVerification) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                //Ignore
                e.printStackTrace();
            }
        }
        logger.info(logPrefix + "stopping server");
        bitcoinRuntimeVerifierServer.stopThread();
        stopped.set(true);
    }

    public boolean isReadyForSimulation() {
        if (workflowHandoverManager.getNetworkParameters() instanceof TestNet3Params) {
            return workflowHandoverManager.getBalanceOfStorage().getValue() >= testnetBalanceThreshold;
        } else if (workflowHandoverManager.getNetworkParameters() instanceof MainNetParams) {
            return workflowHandoverManager.getBalanceOfStorage().getValue() >= mainnetBalanceThreshold;
        }
        return false;
    }

    public void printBalanceOfWalletAndReceiveAddress() {
        logger.info(String.format(msgFormatter, agendId, portToUse, "with balance " + workflowHandoverManager.getBalanceOfStorage().toFriendlyString()));
        logger.info(String.format(msgFormatter, agendId, portToUse, "Send money to " + workflowHandoverManager.getAddressToPayMoneyTo()));
    }

    public String getAgendId() {
        return agendId;
    }

    public int getPortToUse() {
        return portToUse;
    }

    public void setAgentStorage(AgentStorage agentStorage) {
        this.agentStorage = agentStorage;
    }

    /**
     * Collects all required executions, owned by this company.
     */
    public void setOurExecutionsFromAllExecutions(List<ExecutionPath> runningExecutions) {
        ourExecutions = new ArrayList<>();
        for (ExecutionPath executionPath : runningExecutions) {
            if (executionPath.getBusinessProcessDescription().getStart().getAgentId() != null &&
                    executionPath.getBusinessProcessDescription().getStart().getAgentId().equals(agendId)) {
                ourExecutions.add(executionPath);
            }
        }
    }

    public void stopAgent() {
        stopServer.set(true);
    }

    public boolean isAllOwnedProcessesHaveFinished() {
        return allOwnedProcessesHaveFinished.get();
    }

    public void setExecutionPathStorage(ExecutionPathStorage executionPathStorage) {
        this.executionPathStorage = executionPathStorage;
    }

    public void verifyTheDocumentedWorkflowExecutionAgainstThePredefinedSimulation() {
        for (ExecutionPath ourExecution : ourExecutions) {
            SimulationExecutionVerification simulationExecutionVerification
                    = new SimulationExecutionVerification(ourExecution, workflowHandoverManager, true, true);
            simulationExecutionVerification.validateExecution();
        }
    }

    public boolean isStopped() {
        return stopped.get();
    }
}
