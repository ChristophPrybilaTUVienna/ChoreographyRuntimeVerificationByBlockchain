package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.BasicCryptographyManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.Simulator;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.exceptions.HandoverFailureException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.AgentStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.IdentityStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.*;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Client functionality for a bitcoin handover
 */
public class BitcoinRuntimeVerifierHandoverClient {

    private Logger logger = LoggerFactory.getLogger(BitcoinRuntimeVerifierHandoverClient.class);

    private SocketCommunicator socketCommunicator;

    private BasicCryptographyManager basicCryptographyManager;

    private String logPrefix;

    public BitcoinRuntimeVerifierHandoverClient() {

    }

    public void handleNextStep(WorkflowHandoverManager workflowHandoverManager,
                               Identity ownIdentity,
                               IdentityStorage identityStorage,
                               AgentStorage agentStorage,
                               ExecutionPath executionPath,
                               String processOwner,
                               BusinessProcessElement lastTaskFinishedByUs,
                               int followingElementIndexToUse,
                               int previousStepId,
                               int outputIndexOfPreviousStepToUse) {
        logPrefix = "BitcoinRuntimeVerifierHandoverClient(" + ownIdentity.getCompanyName() + ",wfId:" + executionPath.getInstanceId() + "): ";
        logger.debug(logPrefix + "Handle next step of workflow.");
        basicCryptographyManager = new BasicCryptographyManager(ownIdentity);
        BusinessProcessElement followingElementToHandle = lastTaskFinishedByUs.getFollowingElements()[followingElementIndexToUse];
        //If a trivial task comes after an activity, they must have the same owner.
        //If a trivial task comes after a trivial task they must have the same owner
        switch (followingElementToHandle.getType()) {
            case START:
                break;
            case ACTIVITY:
                logger.debug(logPrefix + "Next step is an activity to handover(" + ((Activity) followingElementToHandle).getName() + ":" + followingElementToHandle.getId() + ").");
                //an activity is to be handed over.
                //Note: this simulation performs a handover, even if they are both handled by the same agent.
                //Currently, this is avoided by the simulation
                handoverWorkflowTo(workflowHandoverManager,
                        ownIdentity,
                        identityStorage,
                        agentStorage,
                        executionPath,
                        processOwner,
                        lastTaskFinishedByUs,
                        (Activity) followingElementToHandle,
                        previousStepId,
                        outputIndexOfPreviousStepToUse);
                break;
            case XOR_SPLIT:
                logger.debug(logPrefix + "Next step is an xor split which was already handled by the simulation.");
                //XOR split decision is already resolved by the simulation
                XORSplit xor = (XORSplit) followingElementToHandle;
                //XOR split does not require a documentation transaction.
                handleNextStep(workflowHandoverManager, ownIdentity, identityStorage,
                        agentStorage, executionPath,
                        processOwner, xor, xor.getIndexOfPathToTake(),
                        previousStepId, outputIndexOfPreviousStepToUse);
                break;
            case XOR_JOIN:
                logger.debug(logPrefix + "Next step is an xor join which was already handled by the simulation.");
                //XOR split decision is already resolved by the simulation (see above)
                //XOR join does not require a documentation transaction.
                handleNextStep(workflowHandoverManager, ownIdentity, identityStorage,
                        agentStorage, executionPath,
                        processOwner, followingElementToHandle, 0,
                        previousStepId, outputIndexOfPreviousStepToUse);
                break;
            case AND_SPLIT:
                int andSplitStepId = -1;
                AndSplit andSplit = (AndSplit) followingElementToHandle;
                logger.info(logPrefix + "Placing And_Split");
                if (Simulator.useRuntimeVerification) {
                    try {
                        logger.info(logPrefix + "Start publishing andSplit-marker");
                        if (Simulator.greedyPublishing) {
                            andSplitStepId = workflowHandoverManager.splitWorkflowAsync(executionPath.getInstanceId(), previousStepId,
                                    outputIndexOfPreviousStepToUse, andSplit.getFollowingElements().length);
                        } else {
                            andSplitStepId = workflowHandoverManager.splitWorkflow(executionPath.getInstanceId(), previousStepId,
                                    outputIndexOfPreviousStepToUse, andSplit.getFollowingElements().length);
                        }
                        logger.info(logPrefix + "End publishing andSplit-marker");
                    } catch (Exception e) {
                        throw new RuntimeVerificationException("Failed to split workflow.", e);
                    }
                }
                for (int i = 0; i < andSplit.getFollowingElements().length; i++) {
                    handleNextStep(workflowHandoverManager, ownIdentity, identityStorage,
                            agentStorage, executionPath,
                            processOwner, andSplit, i, andSplitStepId, i);
                }
                break;
            case AND_JOIN:
                //each and_join is expected to only have incoming paths belonging to the same agent
                AndJoin andJoin = (AndJoin) followingElementToHandle;
                //determine which path we are on
                int indexOfIncomingPath = -1;
                for (int i = 0; i < andJoin.getPrecedingElements().length; i++) {
                    if (andJoin.getPrecedingElements()[i].getId() == lastTaskFinishedByUs.getId()) {
                        indexOfIncomingPath = i;
                        break;
                    }
                }
                //only the "first" incoming path must wait for the others
                if (indexOfIncomingPath != 0) {
                    if (!Simulator.useRuntimeVerification) {
                        Simulator.andJoinPathsWaiting.incrementAndGet();
                    }
                    logger.info(logPrefix + "We are not the first incoming path of the And_Join, therefore we stop.");
                    return;
                }
                int andJoinStepId = -1;
                logger.info(logPrefix + "Joining different And_Paths");
                //determine task_ids to wait on.
                List<Byte> idsToWaitOn = new ArrayList<>();
                //all previous elements should be activities
                for (BusinessProcessElement businessProcessElement : andJoin.getPrecedingElements()) {
                    Activity precedingActivity = (Activity) businessProcessElement;
                    idsToWaitOn.add(precedingActivity.getId());
                }
                if (Simulator.useRuntimeVerification) {
                    try {
                        logger.info(logPrefix + "And_Join: wait until all incoming paths have been sent");
                        List<Integer> incomingJoinPathIds = new ArrayList<>();
                        List<Integer> outputIndexOfIncomingJoinPaths = new ArrayList<>();
                        while (true) {
                            logger.info(logPrefix + "And_Join: check our leafs");
                            workflowHandoverManager.acquireLock();
                            List<Integer> leafIds = workflowHandoverManager.getLeafIdsThatBelongToUs(executionPath.getInstanceId(), false);
                            incomingJoinPathIds.clear();
                            outputIndexOfIncomingJoinPaths.clear();
                            for (Integer idOfOurLeaf : leafIds) {
                                if (workflowHandoverManager.isIncomingPathOfAndJoin(executionPath.getInstanceId(), idOfOurLeaf, idsToWaitOn)) {
                                    incomingJoinPathIds.add(idOfOurLeaf);
                                    outputIndexOfIncomingJoinPaths.add(0);
                                }
                            }
                            if (idsToWaitOn.size() == incomingJoinPathIds.size()) {
                                break;
                            }
                            logger.info(logPrefix + "And_Join: only found " + incomingJoinPathIds.size() + " incoming paths. Expected " + idsToWaitOn.size());
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                //ignore
                            }
                        }
                        logger.info(logPrefix + "Start publishing andJoin-marker");
                        if (Simulator.greedyPublishing) {
                            andJoinStepId = workflowHandoverManager.joinWorkflowAsync(
                                    executionPath.getInstanceId(),
                                    incomingJoinPathIds,
                                    new RuntimeVerificationUtils().getCurrentTimeInUnixTimestamp(),
                                    outputIndexOfIncomingJoinPaths);
                        } else {
                            andJoinStepId = workflowHandoverManager.joinWorkflow(
                                    executionPath.getInstanceId(),
                                    incomingJoinPathIds,
                                    new RuntimeVerificationUtils().getCurrentTimeInUnixTimestamp(),
                                    outputIndexOfIncomingJoinPaths);
                        }
                        logger.info(logPrefix + "End publishing andJoin-marker");
                    } catch (Exception e) {
                        throw new RuntimeVerificationException(logPrefix + "Failed to join workflow.", e);
                    }
                } else {
                    while (Simulator.andJoinPathsWaiting.get() != idsToWaitOn.size() - 1) {
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    }
                }
                //proceed working
                handleNextStep(workflowHandoverManager, ownIdentity, identityStorage,
                        agentStorage, executionPath,
                        processOwner, andJoin, 0, andJoinStepId, 0);
                break;
            case END:
                logger.info(logPrefix + "Publishing end of workflow");
                if (Simulator.useRuntimeVerification) {
                    logger.debug(logPrefix + "Next step is the end of the workflow.");
                    try {
                        logger.info(logPrefix + "Start publishing end-marker");
                        if (Simulator.greedyPublishing) {
                            workflowHandoverManager.endWorkflowAsync(executionPath.getInstanceId());
                        } else {
                            workflowHandoverManager.endWorkflow(executionPath.getInstanceId());
                        }
                        logger.info(logPrefix + "End publishing end-marker");
                    } catch (Exception e) {
                        throw new RuntimeVerificationException("Failed to end workflow.", e);
                    }
                } else {
                    Simulator.workflowsFinished.add(executionPath.getInstanceId());
                }
                logger.debug(logPrefix + "Workflow ended.");
                break;
            default:
                logger.warn(logPrefix + "Encountered task with unknown type.");
        }
    }

    private void openConnection(InetAddress partner, int port) throws IOException {
        Socket socket = new Socket(partner, port);
        socketCommunicator = new SocketCommunicator(basicCryptographyManager, socket);
        socketCommunicator.openConnection();
    }

    private void closeConnection() throws IOException {
        socketCommunicator.closeConnection();
    }

    public void handoverWorkflowTo(WorkflowHandoverManager workflowHandoverManager,
                                   Identity ownIdentity,
                                   IdentityStorage identityStorage,
                                   AgentStorage agentStorage,
                                   ExecutionPath executionPath,
                                   String processOwner,
                                   BusinessProcessElement previousTask,
                                   Activity activityToHandover,
                                   int previousStepId,
                                   int outputIndexOfPreviousStepToUse) {
        try {
            //Search activity is skipped since it was predetermined by the simulation.
            String agentToHandOver = activityToHandover.getAgentId();
            openConnection(InetAddress.getByName("0.0.0.0"), agentStorage.getPortOfAgent(agentToHandOver));

            //Mutual identification
            Identity identityOfHandoverPartner = identityStorage.getPublicIdentificationDataOfCompany(agentToHandOver);
            if (agentToHandOver.equals(ownIdentity.getCompanyName())) {
                identityOfHandoverPartner = ownIdentity;
            }
            mutualIdentification(ownIdentity, identityOfHandoverPartner);

            //negotiate handover meta data
            int timestamp = new RuntimeVerificationUtils().getCurrentTimeInUnixTimestamp();
            negotiateTakeoverOfWorkflowInstance(executionPath, activityToHandover, timestamp, processOwner);

            byte[] previousExecutionResult = null;
            byte[] encryptedData = null;
            String usedSymKey = "";
            if (previousTask instanceof Activity) {
                Activity previousTaskAsActivity = (Activity) previousTask;
                if (previousTaskAsActivity.getExecutionResult() != null && !previousTaskAsActivity.getExecutionResult().equals("")) {
                    previousExecutionResult = previousTaskAsActivity.getExecutionResult().getBytes();
                    usedSymKey = basicCryptographyManager.getRandomSymmetricEncryptionKey();
                    encryptedData = basicCryptographyManager.symmetricallyEncryptData(previousExecutionResult, usedSymKey);
                }
            }
            transferEncryptedWorkflowData(encryptedData);

            if (Simulator.useRuntimeVerification) {
                //lock negotiation
                workflowHandoverManager.acquireLock();
                socketCommunicator.sendObject(SocketCommunicator.lockSuccess);
                String resultOfPartner = (String) socketCommunicator.receiveObject();
                while (!resultOfPartner.equals(SocketCommunicator.lockSuccess)) {
                    workflowHandoverManager.conditionallyReleaseLock();
                    workflowHandoverManager.acquireLock();
                    socketCommunicator.sendObject(SocketCommunicator.lockSuccess);
                    resultOfPartner = (String) socketCommunicator.receiveObject();
                }

                //the previous stuff is considered a common requirement for any choreography
                logger.info(logPrefix + "Start performing handover");

                //send the own Bitcoin address
                byte[] bitcoinPubKeyOfStep = workflowHandoverManager.getBitcoinPublicKeyToWFStepOutput(executionPath.getInstanceId(), previousStepId, outputIndexOfPreviousStepToUse);
                socketCommunicator.sendDataWithSignature(bitcoinPubKeyOfStep);

                //send the previously included data that is recorded in the P2SH
                List<byte[]> previousIncludedData = workflowHandoverManager.getDataIncludedInWFStepOutput(executionPath.getInstanceId(), previousStepId, outputIndexOfPreviousStepToUse);
                socketCommunicator.sendObject(previousIncludedData);

                //receive the other Bitcoin address
                byte[] bitcoinPublicKeyOfReceiver = socketCommunicator.receiveDataWithSignature();
                identityOfHandoverPartner.setBitcoinPublicKey(bitcoinPublicKeyOfReceiver);

                byte[] transactionToSign = workflowHandoverManager.createHandoverWorkflowTemplate(
                        executionPath.getInstanceId(), previousStepId, identityOfHandoverPartner,
                        timestamp, activityToHandover.getId(), previousExecutionResult, false, outputIndexOfPreviousStepToUse);

                socketCommunicator.sendDataWithSignature(transactionToSign);
                socketCommunicator.sendObject(usedSymKey);

                byte[] offChainSignature = (byte[]) socketCommunicator.receiveObject();

                if (Simulator.greedyPublishing) {
                    workflowHandoverManager.finishAndPublishHandoverWorkflowTemplateAsync(
                            executionPath.getInstanceId(), identityOfHandoverPartner, offChainSignature, outputIndexOfPreviousStepToUse);
                } else {
                    workflowHandoverManager.finishAndPublishHandoverWorkflowTemplate(
                            executionPath.getInstanceId(), identityOfHandoverPartner, offChainSignature, outputIndexOfPreviousStepToUse);
                }
                logger.info(logPrefix + "End performing handover");
            }
        } catch (HandoverFailureException e) {
            if (activityToHandover.isPerformedIncorrectly()) {
                logger.warn(logPrefix + "The other client noticed a fault. This is expected behaviour");
            } else {
                logger.warn(logPrefix + "The other client noticed a fault. This was unexpected.", e);
            }
            workflowHandoverManager.conditionallyReleaseLock();

            //if a faulty behaviour is conducted and encountered by the other party,
            // the end of the wf is published to free the funds.
            if (Simulator.useRuntimeVerification) {
                try {
                    if (Simulator.greedyPublishing) {
                        workflowHandoverManager.endWorkflowAsync(executionPath.getInstanceId(), true, true);
                    } else {
                        workflowHandoverManager.endWorkflow(executionPath.getInstanceId(), true, true);
                    }
                } catch (Exception e1) {
                    logger.error(logPrefix + "Failed to publish end of workflow after faulty behaviour.", e1);
                }
            } else {
                Simulator.workflowsFinished.add(executionPath.getInstanceId());
            }
        } catch (Exception e) {
            logger.warn(logPrefix + "An exception occurred during a handover.", e);
            workflowHandoverManager.conditionallyReleaseLock();
            //if a faulty behaviour is conducted and encountered by the other party,
            // the end of the wf is published to free the funds.
            if (Simulator.useRuntimeVerification) {
                try {
                    if (Simulator.greedyPublishing) {
                        workflowHandoverManager.endWorkflowAsync(executionPath.getInstanceId(), true, true);
                    } else {
                        workflowHandoverManager.endWorkflow(executionPath.getInstanceId(), true, true);
                    }
                } catch (Exception e2) {
                    logger.error(logPrefix + "Failed to publish end of workflow after error in handover.", e2);
                }
            } else {
                Simulator.workflowsFinished.add(executionPath.getInstanceId());
            }
        } finally {
            try {
                closeConnection();
            } catch (IOException e) {
                //Ignore
            }
        }
    }

    /**
     * Mutually identifies both participants
     */
    private void mutualIdentification(Identity ownIdentity, Identity otherIdentity) throws IOException {
        //security features not required in this simplified simulation, just send own identity
        socketCommunicator.sendObject(ownIdentity.getCompanyName());
        socketCommunicator.setPartnerIdentity(otherIdentity);
    }


    /**
     * Negotiates the handover of the given workflow instance to the other company.
     * NOTE: In the simulation this is only a placeholder, instead the required information is directly exchanged
     */
    protected void negotiateTakeoverOfWorkflowInstance(ExecutionPath executionPath,
                                                       Activity activityToHandover,
                                                       int timestamp,
                                                       String processOwner) throws IOException, HandoverFailureException, ClassNotFoundException {
        socketCommunicator.sendObject(executionPath.getBusinessProcessDescription().getName());
        socketCommunicator.sendObject(executionPath.getInstanceId());
        socketCommunicator.sendObject(processOwner);
        if (activityToHandover.isPerformedIncorrectly()) {
            socketCommunicator.sendObject(((byte) -120));
        } else {
            socketCommunicator.sendObject(activityToHandover.getId());
        }
        String receiveOk = (String) socketCommunicator.receiveObject();
        socketCommunicator.sendObject(timestamp);
    }

    /**
     * Transfers the symmetrically-encrypted workflow data to the other company.
     */
    protected void transferEncryptedWorkflowData(byte[] encryptedData) throws IOException {
        socketCommunicator.sendObject(encryptedData);
    }
}
