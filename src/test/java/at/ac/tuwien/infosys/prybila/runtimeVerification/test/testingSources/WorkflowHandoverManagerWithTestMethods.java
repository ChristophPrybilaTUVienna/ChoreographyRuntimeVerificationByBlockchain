package at.ac.tuwien.infosys.prybila.runtimeVerification.test.testingSources;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager;


public class WorkflowHandoverManagerWithTestMethods extends WorkflowHandoverManager {

    public WorkflowHandoverManagerWithTestMethods(String networkParametersId, String pathToStoreFile, String pathToWalletFile, String pathToCheckpointFile, String pathToHandoverStorageFile, boolean createBitcoinWalletIfNotExists) {
        super(networkParametersId, pathToStoreFile, pathToWalletFile, pathToCheckpointFile, pathToHandoverStorageFile, createBitcoinWalletIfNotExists);
    }

}
