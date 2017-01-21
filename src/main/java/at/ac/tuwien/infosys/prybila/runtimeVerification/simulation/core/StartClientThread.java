package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.Simulator;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.AgentStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.IdentityStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import org.bitcoinj.core.InsufficientMoneyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client functionality to start a workflow.
 * If an InsufficientMoneyException occurs the action is retried repeatedly.
 */
public class StartClientThread extends Thread {

    private WorkflowHandoverManager workflowHandoverManager;

    private Identity ownIdenty;

    private IdentityStorage identityStorage;

    private AgentStorage agentStorage;

    private ExecutionPath executionPath;

    private Logger logger = LoggerFactory.getLogger(StartClientThread.class);

    public StartClientThread(
            WorkflowHandoverManager workflowHandoverManager,
            Identity ownIdenty,
            IdentityStorage identityStorage,
            AgentStorage agentStorage,
            ExecutionPath executionPath) {
        this.workflowHandoverManager = workflowHandoverManager;
        this.ownIdenty = ownIdenty;
        this.identityStorage = identityStorage;
        this.agentStorage = agentStorage;
        this.executionPath = executionPath;
    }

    @Override
    public void run() {
        //start of workflow
        boolean shouldTry = true;
        int startingStepId = -1;

        String logPrefix = "StartClientThread(" + ownIdenty.getCompanyName() + ",wfId:" + executionPath.getInstanceId() + "): ";

        logger.debug(logPrefix + "Trying to start workflow");
        int startTryCount = 20;
        if (Simulator.useRuntimeVerification) {
            while (shouldTry) {
                shouldTry = false;
                try {
                    logger.info(logPrefix + "Start publishing start-marker");
                    if (Simulator.greedyPublishing) {
                        startingStepId = workflowHandoverManager.startWorkflowAsync(executionPath.getInstanceId(),
                                executionPath.getNumberOfTasks(), executionPath.getNumberOfSplits());
                    } else {
                        startingStepId = workflowHandoverManager.startWorkflow(executionPath.getInstanceId(),
                                executionPath.getNumberOfTasks(), executionPath.getNumberOfSplits());
                    }
                    logger.info(logPrefix + "End publishing start-marker");
                    logger.info(logPrefix + "Starting transaction of workflow is " + workflowHandoverManager.getTxHashOfWorkflowGraphStep(executionPath.getInstanceId(), startingStepId));
                } catch (InsufficientMoneyException e) {
                    logger.warn(logPrefix + "Not enough money to start workflow " + executionPath.getInstanceId() + ". Retrying shortly.");
                    startTryCount--;
                    if (startTryCount <= 0) {
                        logger.warn("Stopped trying to start the workflow. This will cause problems in the simulation.");
                        return;
                    }
                    shouldTry = true;
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } catch (Exception e) {
                    throw new RuntimeVerificationException("Failed to start workflow.", e);
                }
            }
        }
        //start first handover
        BitcoinRuntimeVerifierHandoverClient bitcoinRuntimeVerifierHandoverClient
                = new BitcoinRuntimeVerifierHandoverClient();
        try {
            bitcoinRuntimeVerifierHandoverClient.handleNextStep(
                    workflowHandoverManager,
                    ownIdenty,
                    identityStorage,
                    agentStorage,
                    executionPath,
                    ownIdenty.getCompanyName(),
                    executionPath.getBusinessProcessDescription().getStart(),
                    0,
                    startingStepId,
                    0
            );
        } catch (Exception e) {
            throw new RuntimeVerificationException(logPrefix + "Failed to handle the next workflow step.", e);
        }
    }

}
