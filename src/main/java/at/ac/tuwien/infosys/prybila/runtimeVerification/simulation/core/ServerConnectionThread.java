package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowInstance;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.BasicCryptographyManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.OwnIdentityProvider;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.Simulator;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.exceptions.HandoverFailureException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.exceptions.RecognizedFaultException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.AgentStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.ExecutionPathStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.IdentityStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.Activity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.BusinessProcessElement;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Server functionality for a bitcoin handover
 */
public class ServerConnectionThread extends Thread {

    private Logger logger = LoggerFactory.getLogger(ServerConnectionThread.class);

    private SocketCommunicator socketCommunicator;

    private OwnIdentityProvider ownIdentityProvider;

    private IdentityStorage identityStorage;

    private BasicCryptographyManager basicCryptographyManager;

    private ExecutionPathStorage executionPathStorage;

    private AgentStorage agentStorage;

    private WorkflowHandoverManager workflowHandoverManager;

    private RuntimeVerificationUtils utils;

    boolean shouldPerformRelaxedIdentityCheck;

    public ServerConnectionThread(Socket socket, OwnIdentityProvider ownIdentityProvider,
                                  IdentityStorage identityStorage, WorkflowHandoverManager workflowHandoverManager,
                                  AgentStorage agentStorage, ExecutionPathStorage executionPathStorage) {
        this.ownIdentityProvider = ownIdentityProvider;
        this.identityStorage = identityStorage;
        this.agentStorage = agentStorage;
        this.executionPathStorage = executionPathStorage;
        this.workflowHandoverManager = workflowHandoverManager;
        utils = new RuntimeVerificationUtils();
        basicCryptographyManager = new BasicCryptographyManager(ownIdentityProvider.getOwnIdentity());
        socketCommunicator = new SocketCommunicator(basicCryptographyManager, socket);
    }

    @Override
    public void run() {
        Short instanceId = -1;
        String processOwner = null;
        int wfStepWithHandoverId = -1;
        ExecutionPath currentExecutionPath = null;
        BusinessProcessElement executionReferenceOfCurrentTask = null;
        String logPrefix = "";
        Identity identityOfSender = null;
        try {
            shouldPerformRelaxedIdentityCheck = true;
            socketCommunicator.openConnection();

            //receive identity
            String agentIdOfSender = (String) socketCommunicator.receiveObject();

            identityOfSender = identityStorage.getPublicIdentificationDataOfCompany(agentIdOfSender);
            Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
            socketCommunicator.setPartnerIdentity(identityOfSender);

            //receive business process information
            String businessProcess = (String) socketCommunicator.receiveObject();

            //receive workflow meta data
            instanceId = (Short) socketCommunicator.receiveObject();
            currentExecutionPath = executionPathStorage.getExecutionPathOfInstance(instanceId);
            logPrefix = "ServerConnectionThread(" + ownIdentity.getCompanyName() + ",wfId:" + currentExecutionPath.getInstanceId() + "): ";


            processOwner = (String) socketCommunicator.receiveObject();

            Byte taskIdToProcess = (Byte) socketCommunicator.receiveObject();
            executionReferenceOfCurrentTask = getPlannedExecutionReferenceOfTheCurrentTask(currentExecutionPath, taskIdToProcess);
            socketCommunicator.sendObject("Ok");

            Integer timestamp = (Integer) socketCommunicator.receiveObject();

            //receive encrypted workflow data
            byte[] encryptedWfData = (byte[]) socketCommunicator.receiveObject();
            logger.info("Receiving handover task "
                    + ((Activity) executionReferenceOfCurrentTask).getName() + ":" + taskIdToProcess);

            if (Simulator.useRuntimeVerification) {
                //lock negotiation
                receiveLockSuccess();
                while(!workflowHandoverManager.tryToAcquireLock()) {
                    socketCommunicator.sendObject(SocketCommunicator.lockFail);
                    receiveLockSuccess();
                }
                socketCommunicator.sendObject(SocketCommunicator.lockSuccess);

                //the previous stuff is considered a common requirement for any choreography
                logger.info(logPrefix + "Start receiving handover");

                //receive PKI signed bitcoin address of sender
                byte[] bitcoinPublicKeyOfSender = socketCommunicator.receiveDataWithSignature();
                identityOfSender.setBitcoinPublicKey(bitcoinPublicKeyOfSender);

                //receive data required to unlock p2sh output
                List<byte[]> previousIncludedData = (List<byte[]>) socketCommunicator.receiveObject();

                //init the expected handover.
                byte[] pubKeyOfReceiverForHandover = workflowHandoverManager.initHandoverOnReceiverSide(
                        instanceId,
                        identityOfSender,
                        timestamp,
                        taskIdToProcess,
                        encryptedWfData,
                        previousIncludedData,
                        shouldPerformRelaxedIdentityCheck);

                //return own PKI signed bitcoin address
                socketCommunicator.sendDataWithSignature(pubKeyOfReceiverForHandover);

                //receive transaction template
                byte[] transactionToSign = socketCommunicator.receiveDataWithSignature();
                //receive symKey and decrypt transaction data
                String symKey = (String) socketCommunicator.receiveObject();
                if (encryptedWfData != null && !symKey.equals("")) {
                    workflowHandoverManager.decryptHandoverWorkflowDataWithSymmetricalKeyOnReceiverSide(instanceId, identityOfSender, symKey);
                }

                //validate and sign off chain signature
                //Verifies that Output#1 can be retrieved and that Output#2 contains the negotiated terms
                byte[] offChainSignature = workflowHandoverManager.confirmHandoverWorkflowTemplateOnReceiverSide(instanceId, identityOfSender, transactionToSign);

                SimulationExecutionVerification simulationExecutionVerification = new SimulationExecutionVerification(
                        currentExecutionPath, workflowHandoverManager, false, false);
                try {
                    simulationExecutionVerification.validateExecution();
                } catch (Exception e) {
                    System.out.println();
                }

                socketCommunicator.sendObject(offChainSignature);

                //try three times if the handover was finished
                boolean finishWasSuccessfull = false;
                for (int i = 0; i < 3; i++) {
                    Thread.sleep(2000);
                    wfStepWithHandoverId = workflowHandoverManager.finishHandoverWorkflowTemplateOnReceiverSide(instanceId, identityOfSender);
                    if (wfStepWithHandoverId != -1) {
                        finishWasSuccessfull = true;
                        break;
                    }
                }
                if (!finishWasSuccessfull) {
                    throw new HandoverFailureException("The handover was not published by the sender.");
                }
                if (!Simulator.greedyPublishing) {
                    workflowHandoverManager.waitForConfirmationOnHandoverForInstance(new WorkflowInstance(instanceId), wfStepWithHandoverId);
                }
                logger.info(logPrefix + "End receiving handover");
            }
        } catch (RecognizedFaultException e) {
            logger.error("A faulty handover was recognized.");
            workflowHandoverManager.conditionallyReleaseLock();
            try {
                socketCommunicator.sendHandoverError();
            } catch (IOException e1) {
                //ingore
            }
            return;
        } catch (Exception e) {
            logger.error("An exception occurred during receiving a handover", e);
            try {
                if (wfStepWithHandoverId != -1) {
                    try {
                        workflowHandoverManager.endWorkflow(instanceId, true, true);
                    } catch (Exception e2) {
                        logger.error("Tried to end workflow due to an exception but failed.", e2);
                    }
                } else {
                    socketCommunicator.sendHandoverError();
                    if (instanceId != -1 && identityOfSender != null) {
                        workflowHandoverManager.deleteIntermediateLeafTemplateIfExistsOnReceiverSide(instanceId, identityOfSender);
                    }
                    workflowHandoverManager.conditionallyReleaseLock();
                }
            } catch (IOException e1) {
                //ignore
            }
            return;
        } finally {
            socketCommunicator.closeConnection();
        }

        //############### The normal task execution would start here

        //filler tasks are not executed
        if (executionReferenceOfCurrentTask.getId() >= 0) {
            logger.info(logPrefix + "Start performing current task " + executionReferenceOfCurrentTask.getId());
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                //Ignore
            }
            logger.info(logPrefix + "End performing current task " + executionReferenceOfCurrentTask.getId());
        }
        //###############

        //perform next handover
        BitcoinRuntimeVerifierHandoverClient bitcoinRuntimeVerifierHandoverClient
                = new BitcoinRuntimeVerifierHandoverClient();
        try {
            bitcoinRuntimeVerifierHandoverClient.handleNextStep(
                    workflowHandoverManager,
                    ownIdentityProvider.getOwnIdentity(),
                    identityStorage,
                    agentStorage,
                    currentExecutionPath,
                    processOwner,
                    executionReferenceOfCurrentTask,
                    0,
                    wfStepWithHandoverId,
                    0
            );
        } catch (Exception e) {
            throw new RuntimeVerificationException(logPrefix + "Failed to handle the next workflow step.", e);
        }
    }

    private void receiveLockSuccess() throws HandoverFailureException, IOException, ClassNotFoundException {
        String lockResult = (String) socketCommunicator.receiveObject();
        if(!lockResult.equals(SocketCommunicator.lockSuccess)) {
            logger.warn("Sanity check failed. No LockSuccess was sent.");
        }
    }

    private BusinessProcessElement getPlannedExecutionReferenceOfTheCurrentTask(ExecutionPath executionPath, byte taskId) throws RecognizedFaultException {
        for (BusinessProcessElement businessProcessElement : utils.graphToList(executionPath.getBusinessProcessDescription().getStart())) {
            if (businessProcessElement.getId() == taskId) {
                return businessProcessElement;
            }
        }
        throw new RecognizedFaultException("Could not find task for taskId " + taskId);
    }
}
