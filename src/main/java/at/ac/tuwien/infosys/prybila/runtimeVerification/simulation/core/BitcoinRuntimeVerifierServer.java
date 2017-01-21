package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.OwnIdentityProvider;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.AgentStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.ExecutionPathStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.IdentityStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Server listener for a bitcoin handover
 */
public class BitcoinRuntimeVerifierServer extends Thread {

    private Logger logger = LoggerFactory.getLogger(BitcoinRuntimeVerifierServer.class);

    private int port;

    private boolean shouldRun;

    private ServerSocket serverSocket;

    private OwnIdentityProvider ownIdentityProvider;

    private IdentityStorage identityStorage;

    private WorkflowHandoverManager workflowHandoverManager;

    private ExecutionPathStorage executionPathStorage;

    private AgentStorage agentStorage;

    public BitcoinRuntimeVerifierServer(int port, OwnIdentityProvider ownIdentityProvider,
                                        WorkflowHandoverManager workflowHandoverManager) {
        this.port = port;
        this.ownIdentityProvider = ownIdentityProvider;
        this.workflowHandoverManager = workflowHandoverManager;
        shouldRun = false;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            logger.error("Failed to open server socket", e);
            return;
        }
        String logPrefix = "VerificationServer (" + ownIdentityProvider.getOwnIdentity().getCompanyName() + "): ";
        logger.info(logPrefix + "started to listen on port");
        shouldRun = true;
        while (shouldRun) {
            Socket connectionSocket;
            try {
                connectionSocket = serverSocket.accept();
                logger.info(logPrefix + "Opened a new connection.");
                ServerConnectionThread serverConnectionThread
                        = new ServerConnectionThread(connectionSocket, ownIdentityProvider,
                        identityStorage, workflowHandoverManager, agentStorage, executionPathStorage);
                serverConnectionThread.start();
            } catch (IOException e) {
                if (!e.getMessage().equals("socket closed")) {
                    logger.warn("Error while waiting for communication channel.", e);
                }
                continue;
            }
        }
        logger.info(logPrefix + "stopped to listen on port");
    }

    public void stopThread() {
        shouldRun = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
        }
    }

    public boolean isRunning() {
        return shouldRun;
    }

    public void setIdentityStorage(IdentityStorage identityStorage) {
        this.identityStorage = identityStorage;
    }

    public void setExecutionPathStorage(ExecutionPathStorage executionPathStorage) {
        this.executionPathStorage = executionPathStorage;
    }

    public void setAgentStorage(AgentStorage agentStorage) {
        this.agentStorage = agentStorage;
    }
}
