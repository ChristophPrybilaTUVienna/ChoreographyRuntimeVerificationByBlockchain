package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.*;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.HandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.TransactionReference;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowInstance;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.BlockChainCrawler;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.BlockcypherMainnetCrawler;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.BlockcypherTestnetCrawler;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Holds a connection to the Bitcoin Network and the local files corresponding to it.
 * Can be open during the entire execution or open/closed for each transaction.
 * The internal wallet and blockStore are saved periodically to a file.
 * <p>
 * start transaction = openConnection() open connection includes loading local block chain and syncing it with online block chain
 * commit transaction = closeConnection() close connection includes persisting of local block chain copy to file
 */
@Component
@Scope("prototype")
public class BitcoinConnection {

    private final Logger logger = LoggerFactory.getLogger(BitcoinConnection.class);
    private final Logger logger2 = LoggerFactory.getLogger("at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.BitcoinConnectionJustBroadCast");

    private boolean connectionOpen;

    protected File checkpointFile;
    protected File storeFile;
    protected File walletFile;
    protected NetworkParameters networkParameters;

    protected Wallet wallet;
    protected BlockStore blockStore;
    protected BlockChain blockChain;
    protected PeerGroup peerGroup;

    private RuntimeVerificationUtils runtimeVerificationUtils;
    private TransactionBuilder transactionBuilder;
    private WorkflowExecutionPointFactory workflowExecutionPointFactory;
    private TransactionOffChainProcessor transactionOffChainProcessor;
    private TransactionSerializer transactionSerializer;
    private BlockChainCrawler blockChainCrawler;
    private int minBroadcastPeers;

    public static final int maxNumOfBroadCastTries = 10;

    public BitcoinConnection() {
        connectionOpen = false;
        runtimeVerificationUtils = new RuntimeVerificationUtils();
        transactionBuilder = new TransactionBuilder();
        workflowExecutionPointFactory = new WorkflowExecutionPointFactory();
        transactionOffChainProcessor = new TransactionOffChainProcessor();
        transactionSerializer = new TransactionSerializer();
        minBroadcastPeers = 5;
    }

    /**
     * Returns the balance of the internal wallet.
     */
    public Coin getBalance() {
        openConnectionGuard("getBalance");
        Coin balance = wallet.getBalance();
        logger.debug("Returning balance of wallet " + balance.toFriendlyString());
        return balance;
    }

    public ECKey getNewKeyFromWallet() {
        openConnectionGuard("getNewKeyFromWallet");
        ECKey receiveKey = new ECKey();
        wallet.importKey(receiveKey);
        logger.debug("Returning new key for wallet " + receiveKey);
        return receiveKey;
    }

    /**
     * Resets the internal wallet. Should only be used in emergencies.
     * CAVE: Results in loss of internal balance.
     */
    public void cleanUpStuff() {
        wallet.reset();
    }

    /**
     * Prepares and opens the connection to the bitcoin network.
     * <p>
     * Note: can only be called once.
     */
    public void openConnection(NetworkParameters networkParameters, File storeFile, File walletFile, File checkpointFile) throws UnreadableWalletException, IOException, BlockStoreException {
        closedConnectionGuard("openConnection");
        logger.debug("Starting to open a new Bitcoin connection.");
        setInputParams(checkpointFile, storeFile, walletFile, networkParameters);

        String token = new RuntimeVerificationUtils().readCrawlerTokenFromProperties();

        //Select the appropriate crawler
        if (networkParameters instanceof TestNet3Params) {
            blockChainCrawler = new BlockcypherTestnetCrawler(token);
        } else if (networkParameters instanceof MainNetParams) {
            blockChainCrawler = new BlockcypherMainnetCrawler(token);
        }
        logger.debug("Loading wallet and blockstore if existing.");
        //Init API objects and load data from files.
        initConnection();
        logger.debug("Starting peergroup synchronization.");
        //Open connections to Bitcoin peers and perform initial download of block chain.
        startConnection();
        connectionOpen = true;
        logger.debug("A new Bitcoin connection was successfully opened.");
    }

    /**
     * Must only be called if @PreDestroy is not called by the context.
     * Closes the connection to the bitcoin network.
     */
    public void closeConnection() throws IOException, BlockStoreException {
        openConnectionGuard("closeConnection");
        logger.debug("Starting to close the Bitcoin connection.");
        stopBitcoinConnection();
        connectionOpen = false;
        logger.debug("The Bitcoin connection was closed.");
    }

    /**
     * Broadcasts the given Transaction to the network.
     * If the checkFirst flag is set, the blockChainCrawler checks if the transaction can be found online before
     * performing a broadcast.
     * Waits waitMaxSeconds seconds for an initial broadcast confirmation.
     * If maxTries is exceeded a RuntimeVerificationException is thrown.
     * Note: Execution is blocked until a broadcast confirmation is received.
     * Throws a java.util.concurrent.ExecutionException: org.bitcoinj.core.RejectedTransactionException if the transaction is not accepted.
     */
    public SendRequest publishTransaction(Transaction tx, boolean checkFirst, int maxTries, int waitMaxSeconds, int depthUntilConfirmed) throws IOException, ExecutionException, InterruptedException {
        openConnectionGuard("publishTransaction");
        SendRequest sendRequest = SendRequest.forTx(tx);
        publishSendRequest(sendRequest, checkFirst, maxTries, waitMaxSeconds, depthUntilConfirmed);
        return sendRequest;
    }

    private void publishSendRequest(SendRequest sendRequest, boolean checkFirst, int maxTries, int waitMaxSeconds, int depthUntilConfirmed) throws IOException, ExecutionException, InterruptedException {
        publishSendRequest(sendRequest, checkFirst, maxTries, waitMaxSeconds, depthUntilConfirmed, true);
    }

    /**
     * Broadcasts the given SendRequest to the network until it can be seen by the crawler APIy
     * If the checkFirst flag is set, the blockChainCrawler checks if the transaction can be found online before
     * performing a broadcast.
     * Waits waitMaxSeconds seconds for an initial broadcast confirmation.
     * If maxTries is exceeded a RuntimeVerificationException is thrown.
     * Installs a callback for the confirmation to log the successful confirmation. Requires the depthUntilConfirmed parameter for this.
     * Note: Execution is blocked until a broadcast confirmation is received.
     * Throws a java.util.concurrent.ExecutionException: org.bitcoinj.core.RejectedTransactionException if the transaction is not accepted.
     */
    private void publishSendRequest(SendRequest sendRequest, boolean checkFirst, int maxTries, int waitMaxSeconds, int depthUntilConfirmed, boolean logRequest) throws IOException, ExecutionException, InterruptedException {
        openConnectionGuard("publishSendRequest");
        boolean isFoundOnline = false;
        if (checkFirst) {
            isFoundOnline = performOnlineCheck(sendRequest, logRequest);
        }
        int numTries = 0;
        while (!isFoundOnline) {
            if (numTries > maxTries) {
                throw new RuntimeVerificationException("Number of broadcast tries exceeded maxTries " + maxTries + ".");
            }
            performBroadcast(sendRequest, waitMaxSeconds);
            //give the api time to update
            sleepForMS(800);
            isFoundOnline = performOnlineCheck(sendRequest, logRequest);
            numTries++;
        }
        installCallbackListenerForConfirmationOfTransaction(sendRequest, depthUntilConfirmed);
        logger.debug("The sendRequest " + sendRequest + " with the included transaction " + sendRequest.tx + " was published.");
    }

    private void installCallbackListenerForConfirmationOfTransaction(SendRequest sendRequest, int depthUntilConfirmed) {
        final String txHash = sendRequest.tx.getHashAsString();
        Futures.addCallback(sendRequest.tx.getConfidence().getDepthFuture(depthUntilConfirmed), new FutureCallback<TransactionConfidence>() {
            @Override
            public void onSuccess(TransactionConfidence result) {
                logger2.debug("Received confirmation for block depth for transaction " + txHash);
            }

            @Override
            public void onFailure(Throwable throwable) {
                logger2.warn("Something went wrong while waiting for confirmation for block depth for transaction", throwable);
            }
        });
    }

    private void sleepForMS(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean performOnlineCheck(SendRequest sendRequest, boolean logRequest) throws IOException {
        String txHash = sendRequest.tx.getHashAsString();
        boolean isFoundOnline;
        try {
            isFoundOnline = blockChainCrawler.getTransactionInformation(txHash, true, logRequest) != null;
        } catch (RuntimeVerificationException e) {
            //logger.warn("An exception occurred during the online check of the transaction broadcast", e);
            isFoundOnline = false;
        }
        if (isFoundOnline) {
            logger.debug("Result of online check through API crawler: The transaction tx " + txHash + " was found online.");
        }
        return isFoundOnline;
    }

    private void performBroadcast(SendRequest sendRequest, int waitMaxSeconds) throws IOException, ExecutionException, InterruptedException {
        String txHash = sendRequest.tx.getHashAsString();
        logger.debug("Broadcasting tx " + txHash);
        logger2.debug("Broadcasting transaction with txHash " + txHash);
        wallet.maybeCommitTx(sendRequest.tx);
        TransactionBroadcast broadcast = peerGroup.broadcastTransaction(sendRequest.tx);
        //block until broadcast confirmation.
        try {
            broadcast.future().get(waitMaxSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warn("Initial broadcast of " + sendRequest.tx.getHashAsString() + " did not return a confirmation in " + waitMaxSeconds + " seconds.");
        }
        logger2.debug("Transaction was broadcast with txHash " + txHash);
        wallet.saveToFile(walletFile);
    }

    /**
     * Broadcasts the SendRequest contained in the given Handover to the network.
     * If the checkFirst flag is set, the blockChainCrawler checks if the transaction can be found online before
     * performing a broadcast.
     * Waits waitMaxSeconds seconds for an initial broadcast confirmation.
     * If maxTries is exceeded a RuntimeVerificationException is thrown.
     * Note: Execution is blocked until a broadcast confirmation is received.
     * Throws a java.util.concurrent.ExecutionException: org.bitcoinj.core.RejectedTransactionException if the transaction is not accepted.
     */
    public void publishHandoverPoint(HandoverData handoverData, boolean checkFirst, int maxTries, int waitMaxSeconds, int depthUntilConfirmed, boolean logRequest) throws IOException, ExecutionException, InterruptedException {
        openConnectionGuard("publishHandoverPoint");
        publishSendRequest(handoverData.getTransactionReference().getSendRequest(), checkFirst, maxTries, waitMaxSeconds, depthUntilConfirmed, logRequest);
    }

    public void publishHandoverPoint(HandoverData handoverData, boolean checkFirst, int maxTries, int waitMaxSeconds, int depthUntilConfirmed) throws IOException, ExecutionException, InterruptedException {
        publishHandoverPoint(handoverData, checkFirst, maxTries, waitMaxSeconds, depthUntilConfirmed, true);
    }

    /**
     * Blocks until the transaction contained in the given handoverData has reached the given confirmation depth or the
     * waitMaxMinutes timeout expired.
     * To be more multithreading friendly. The waiting for confirmation is done iteratively with sleep() calls in between.
     * Returns true if the task was completed.
     */
    public boolean waitForConfirmationDepth(HandoverData handoverData, int depth, int waitMaxMinutes) throws ExecutionException, InterruptedException {
        ListenableFuture<TransactionConfidence> future = handoverData.getTransactionReference().getSendRequest().tx.getConfidence().getDepthFuture(depth);
        logger.debug("Starting to wait for " + waitMaxMinutes + " minutes to reach " + depth + " confirmations for handoverData " + handoverData);
        int secondsToWait = waitMaxMinutes * 60;
        boolean gotConfirmation = false;
        while(secondsToWait > 0 && !gotConfirmation) {
            try {
                future.get(3, TimeUnit.SECONDS);
                gotConfirmation = true;
            } catch (TimeoutException e) {
                secondsToWait -= 3;
            }
            if (gotConfirmation) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignore) {
                }
                secondsToWait -= 3;
            }
        }
        return gotConfirmation;
    }

    /**
     * Performs a series of transaction broadcast actions and confirmation waiting actions.
     * If the checkOnlineBeforePublishing flag is set, the blockChainCrawler checks if the transaction
     * can be found online before performing a broadcast.
     * Returns true if the given transaction has reached its confirmation depth during this procedure.
     */
    public boolean performAggressivePublishing(HandoverData handoverData, int depth, int waitMaxMinutesForDepthConfirmation, boolean checkOnlineBeforePublishing, int waitMaxSecondsForBroadcastConfirmation) throws IOException, ExecutionException, InterruptedException {
        //publish for the first time
        logger.debug("Starting aggressive publishing.");
        logger.debug("First try of aggressive publishing.");
        publishHandoverPoint(handoverData, checkOnlineBeforePublishing, maxNumOfBroadCastTries, waitMaxSecondsForBroadcastConfirmation, depth, false);
        boolean confirmationReached = waitForConfirmationDepth(handoverData, depth, waitMaxMinutesForDepthConfirmation);
        if (!confirmationReached) {
            //publish for the second time
            logger.debug("Second try of aggressive publishing.");
            publishHandoverPoint(handoverData, checkOnlineBeforePublishing, maxNumOfBroadCastTries, waitMaxSecondsForBroadcastConfirmation, depth, false);
            confirmationReached = waitForConfirmationDepth(handoverData, depth, waitMaxMinutesForDepthConfirmation);
            if (!confirmationReached) {
                //publish for the third time
                logger.debug("Third try of aggressive publishing.");
                publishHandoverPoint(handoverData, checkOnlineBeforePublishing, maxNumOfBroadCastTries, waitMaxSecondsForBroadcastConfirmation, depth, false);
                confirmationReached = waitForConfirmationDepth(handoverData, depth, waitMaxMinutesForDepthConfirmation);
            }
        }
        return confirmationReached;
    }

    /**
     * Signs the content of transaction with the given key.
     * Returns the signature as byte[] encoded in DER.
     */
    public byte[] offChainSignTransaction(HandoverData handoverData, ECKey receiverKey) throws IOException {
        logger.debug("Performing offChainSigning of handoverData " + handoverData);
        return transactionOffChainProcessor.signTransactionOffline(
                receiverKey,
                handoverData.getTransactionReference().getBitcoinJTransaction(),
                networkParameters);
    }

    /**
     * Serializes the Transaction of the given HandoverData
     */
    public byte[] serializeTransaction(HandoverData handoverData) {
        return transactionSerializer.serializeTransaction(handoverData.getTransactionReference().getBitcoinJTransaction());
    }

    /**
     * Deserializes a received Handover-Transaction to a HandoverData.
     * Must be a transaction of type INTERMEDIATE
     */
    public HandoverData deserializeHandoverTransactionToHandoverPoint(
            byte[] receivedTransaction,
            ECKey keyForPreviousTransaction,
            byte[] dataDocumentedInThisHandover) {
        Transaction deserializedTransaction = transactionSerializer.deserializeTransaction(networkParameters, receivedTransaction);
        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(new TransactionReference(deserializedTransaction, Arrays.asList(0)));
        if (!transactionStructureVerifier.isWFHandoverTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + deserializedTransaction + " is not a common handover transaction.");
        }
        TransactionOutput opReturnOutput = deserializedTransaction.getOutput(1);
        byte[] dataBlock = opReturnOutput.getScriptPubKey().getChunks().get(1).data;
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(dataBlock);
        WorkflowHandoverData workflowHandoverData = workflowDataBlockConverter.getWorkflowHandoverData();
        if (dataDocumentedInThisHandover != null) {
            workflowHandoverData.setWorkflowData(dataDocumentedInThisHandover);
        }
        logger.debug("Deserialized data to WorkflowHandoverData " + workflowHandoverData + " and (potentially unfinished) transaction " + deserializedTransaction);
        List<ECKey> keyList = new ArrayList<>();
        keyList.add(keyForPreviousTransaction);
        List<List<byte[]>> handoverDataList = null;
        if (dataDocumentedInThisHandover != null) {
            List<byte[]> handoverDataListToInclude = new ArrayList<>();
            handoverDataListToInclude.add(workflowHandoverData.getHash160OfWorkflowData());
            handoverDataList = Arrays.asList(handoverDataListToInclude);
        }
        return new HandoverData(
                workflowHandoverData,
                new TransactionReference(deserializedTransaction, Arrays.asList(0)),
                keyList,
                handoverDataList,
                false,
                true,
                networkParameters);
    }

    /**
     * Returns true if the TransactionOutput contains a matching P2SH script.
     */
    public boolean hasMatchingP2SHScript(HandoverData handoverData,
                                         int indexOfOutputToUse) {
        Script placedP2SHScript = handoverData.getTransactionReference().getOutputWithToken(indexOfOutputToUse).getScriptPubKey();
        Address receivingAddress = handoverData.getKeyToRedeemP2SHOutput(indexOfOutputToUse).toAddress(networkParameters);
        Script redeemScript = transactionBuilder.createRedeemScript(
                receivingAddress,
                convertDataToUnlockPreviousTransactionAsList(
                        handoverData.getDataToRedeemP2SHOutput(indexOfOutputToUse)));
        Script expectedP2SHScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        return expectedP2SHScript.equals(placedP2SHScript);
    }

    /**
     * Creates a transaction to mark the start of a new workflow instance.
     * NOTE: The included SendRequest must still be broadcast to the network.
     */
    public HandoverData createStartWFTransaction(WorkflowInstance workflowInstance, int timestamp, Coin tokenSize) throws InsufficientMoneyException {
        openConnectionGuard("createStartWFTransaction");
        ECKey keyToSendWFStartTo = getNewKeyFromWallet();
        Address addressStartWFIsSentTo = keyToSendWFStartTo.toAddress(networkParameters);

        WorkflowHandoverData workflowHandoverDataStart
                = workflowExecutionPointFactory.createWorkflowStartPoint(workflowInstance, timestamp);

        SendRequest sendRequest = transactionBuilder.createWorkflowStartTransaction(
                addressStartWFIsSentTo,
                tokenSize,
                workflowHandoverDataStart,
                networkParameters);

        //let the framework choose appropriate inputs and config the fee and change output
        sendRequest.shuffleOutputs = false;
        wallet.completeTx(sendRequest);

        HandoverData wfStartPoint = new HandoverData(
                workflowHandoverDataStart,
                new TransactionReference(sendRequest, tokenSize),
                Arrays.asList(keyToSendWFStartTo),
                null,
                true,
                false,
                networkParameters);
        logger.debug("Created workflow start handoverPoint " + wfStartPoint);
        return wfStartPoint;
    }

    /**
     * Creates a handover transaction template without off- and on-chain signatures.
     * The transaction contained in the returned HandoverData must be signed off-chain by the handover partner.
     */
    public HandoverData prepareHandoverTemplate(
            int timestamp,
            byte taskId,
            byte[] dataToDocumentForNewHandover,
            HandoverData previousTransaction,
            ECKey publicKeyOfReceiver,
            int indexOfTokenOutputToUse) {
        openConnectionGuard("prepareHandoverTemplate");
        Address addressHandoverWFIsSentTo = publicKeyOfReceiver.toAddress(networkParameters);

        //Prepare the unlocking of the previous transaction output.
        ECKey keyToUnlockPreviousTransaction = previousTransaction.getKeyToRedeemP2SHOutput(indexOfTokenOutputToUse);
        Address addressOfPreviousTransaction = keyToUnlockPreviousTransaction.toAddress(networkParameters);

        List<byte[]> dataToUnlockPreviousTransactionAsList = previousTransaction.getDataToRedeemP2SHOutput(indexOfTokenOutputToUse);
        Script incompleteRedeemScriptForPreviousTransaction
                = transactionBuilder.createP2SHRedeemScriptCombinedWithPublicKeyButWithoutSignature(
                addressOfPreviousTransaction,
                keyToUnlockPreviousTransaction,
                convertDataToUnlockPreviousTransactionAsList(dataToUnlockPreviousTransactionAsList));
        WorkflowHandoverData workflowHandoverDataHandover
                = workflowExecutionPointFactory.createWorkflowHandoverPoint(
                previousTransaction.getWorkflowHandoverData().getWorkflowInstance(), timestamp, taskId);
        if (dataToDocumentForNewHandover != null) {
            workflowHandoverDataHandover.setWorkflowData(dataToDocumentForNewHandover);
        }

        //This transactionTemplate is sent to the receiver side
        Transaction transactionTemplate = transactionBuilder.createWorkflowHandOverTransactionTemplate(
                workflowHandoverDataHandover,
                previousTransaction.getTransactionReference().getOutputWithToken(indexOfTokenOutputToUse),
                incompleteRedeemScriptForPreviousTransaction,
                addressHandoverWFIsSentTo,
                networkParameters,
                workflowHandoverDataHandover.getHash160OfWorkflowData()
        );
        List<ECKey> keyList = new ArrayList<>();
        keyList.add(publicKeyOfReceiver);

        List<List<byte[]>> handoverDataList = null;
        if (dataToDocumentForNewHandover != null) {
            List<byte[]> handoverDataListToInclude = new ArrayList<>();
            handoverDataListToInclude.add(workflowHandoverDataHandover.getHash160OfWorkflowData());
            handoverDataList = Arrays.asList(handoverDataListToInclude);
        }
        HandoverData templateHandoverData = new HandoverData(
                workflowHandoverDataHandover,
                new TransactionReference(transactionTemplate, Arrays.asList(0)),
                keyList,
                handoverDataList,
                true,
                true,
                networkParameters);
        logger.debug("Created workflow handover template in handoverPoint " + templateHandoverData);
        return templateHandoverData;
    }

    /**
     * Completes a given handover transaction template with a given off-chain signature and by generating an
     * on-chain signature.
     * Verifies the given redeemScript against the connected output and throws a VerificationException on failure.
     * NOTE: The included SendRequest must still be broadcast to the network.
     */
    public HandoverData finishHandoverTemplate(HandoverData previousTransaction, HandoverData handoverDataOfTemplate, byte[] offlineSignatureOfPartner, int indexOfConnectedOutputToUse) {
        openConnectionGuard("finishHandoverTemplate");
        Address addressToRedeemPreviousP2SHOutput = previousTransaction.getKeyToRedeemP2SHOutput(indexOfConnectedOutputToUse).toAddress(networkParameters);
        Script redeemScriptForPreviousTransaction = transactionBuilder.createRedeemScript(
                addressToRedeemPreviousP2SHOutput,
                convertDataToUnlockPreviousTransactionAsList(
                        previousTransaction.getDataToRedeemP2SHOutput(indexOfConnectedOutputToUse)));

        ECKey.ECDSASignature receivedOfflineSignature = ECKey.ECDSASignature.decodeFromDER(offlineSignatureOfPartner);
        SendRequest sendRequest = transactionBuilder.finishWorkflowHandOverTransactionFromTemplate(
                handoverDataOfTemplate.getTransactionReference().getBitcoinJTransaction(),
                redeemScriptForPreviousTransaction,
                previousTransaction.getKeyToRedeemP2SHOutput(indexOfConnectedOutputToUse),
                receivedOfflineSignature);

        Transaction finishedTransaction = sendRequest.tx;

        try {
            transactionOffChainProcessor.validateSignatureOffline(
                    handoverDataOfTemplate.getKeyToRedeemP2SHOutput(0).getPubKey(), //the current step is a handover. The token resides at position 0
                    receivedOfflineSignature.encodeToDER(), finishedTransaction, networkParameters);
        } catch (Exception e) {
            throw new RuntimeVerificationException("The received offline-siganture did not validate.", e);
        }

        HandoverData completedHandoverpoint = handoverDataOfTemplate.cloneWithUpdatedTransactionReference(
                new TransactionReference(
                        sendRequest,
                        handoverDataOfTemplate.getTransactionReference().getIndicesOfTokenOutputs()));
        completedHandoverpoint.setTemplate(false);
        logger.debug("Completed workflow handoverPoint " + completedHandoverpoint);
        return completedHandoverpoint;
    }

    /**
     * Adapts a given handover transaction template with a given off-chain signature.
     * This must be performed by the receiver of a handover transaction in order to receive the same transaction hash as
     * published by the sender of the handover.
     */
    public HandoverData finishHandoverTemplateOnReceiverSide(
            HandoverData handoverDataOfTemplate,
            byte[] _offlineSignature,
            byte[] sigScriptOfPreviousInput) {
        ECKey.ECDSASignature offlineSignature = ECKey.ECDSASignature.decodeFromDER(_offlineSignature);
        SendRequest sendRequest = transactionBuilder.finishHandoverTemplateOnReceiverSide(
                handoverDataOfTemplate.getTransactionReference().getBitcoinJTransaction(),
                offlineSignature,
                sigScriptOfPreviousInput);
        HandoverData completedHandoverpointOnReceiverSide = handoverDataOfTemplate.cloneWithUpdatedTransactionReference(
                new TransactionReference(
                        sendRequest,
                        handoverDataOfTemplate.getTransactionReference().getIndicesOfTokenOutputs()));
        completedHandoverpointOnReceiverSide.setTemplate(false);
        logger.debug("Completed workflow handoverPoint on receiver side " + completedHandoverpointOnReceiverSide);
        return completedHandoverpointOnReceiverSide;
    }


    /**
     * Creates a transaction to mark the split of a workflow instance.
     * NOTE: The included SendRequest must still be broadcast to the network.
     * NOTE: Distributes the token equally.
     */
    public HandoverData createSplitWFTransaction(
            WorkflowInstance workflowInstance,
            int timestamp,
            HandoverData previousTransaction,
            int indexOfConnectedOutputToUse,
            int numberOfSplits) {
        openConnectionGuard("createSplitWFTransaction");

        List<Address> addressesToSendTheTokensTo = new ArrayList<>();
        List<ECKey> keysToSendTheTokensTo = new ArrayList<>();
        List<Integer> indicesOfTokenOutputs = new ArrayList<>();
        for (int i = 0; i < numberOfSplits; i++) {
            ECKey keyToSendWFSplitTo = getNewKeyFromWallet();
            addressesToSendTheTokensTo.add(keyToSendWFSplitTo.toAddress(networkParameters));
            keysToSendTheTokensTo.add(keyToSendWFSplitTo);
            indicesOfTokenOutputs.add(i);
        }

        WorkflowHandoverData workflowHandoverDataSplit
                = workflowExecutionPointFactory.createWorkflowSplitPoint(workflowInstance, timestamp);

        //Prepare the unlocking of the previous transaction output.
        ECKey keyToUnlockPreviousTransaction = previousTransaction.getKeyToRedeemP2SHOutput(indexOfConnectedOutputToUse);
        Address addressOfPreviousTransaction = keyToUnlockPreviousTransaction.toAddress(networkParameters);

        List<byte[]> dataToUnlockPreviousTransactionAsList = previousTransaction.getDataToRedeemP2SHOutput(indexOfConnectedOutputToUse);
        Script redeemScriptForPreviousTransaction = transactionBuilder.createRedeemScript(
                addressOfPreviousTransaction,
                convertDataToUnlockPreviousTransactionAsList(dataToUnlockPreviousTransactionAsList));

        TransactionOutput outputToUseAsInput = previousTransaction.getTransactionReference().getOutputWithToken(indexOfConnectedOutputToUse);
        List<Coin> tokenDistribution = runtimeVerificationUtils
                .distributeCoinEqually(outputToUseAsInput.getValue(), numberOfSplits);

        SendRequest sendRequest = transactionBuilder.createWorkflowSplitTransaction(
                workflowHandoverDataSplit,
                addressOfPreviousTransaction,
                outputToUseAsInput,
                redeemScriptForPreviousTransaction,
                keyToUnlockPreviousTransaction,
                addressesToSendTheTokensTo,
                tokenDistribution,
                networkParameters);

        HandoverData wfJoinPoint = new HandoverData(
                workflowHandoverDataSplit,
                new TransactionReference(sendRequest, indicesOfTokenOutputs),
                keysToSendTheTokensTo,
                null,
                true,
                false,
                networkParameters);
        logger.debug("Created workflow split handoverPoint " + wfJoinPoint);
        return wfJoinPoint;
    }

    /**
     * Creates a transaction to mark the join of different paths of a workflow instance.
     * NOTE: The included SendRequest must still be broadcast to the network.
     */
    public HandoverData createJoinWFTransaction(
            WorkflowInstance workflowInstance,
            int timestamp,
            List<HandoverData> previousTransactions,
            List<Integer> indicesOfConnectedOutputToUse) {
        openConnectionGuard("createJoinWFTransaction");
        //sanity checks
        if (previousTransactions.size() != indicesOfConnectedOutputToUse.size()) {
            throw new RuntimeVerificationException("TransactionList and indexOfConnectedOutputList must be of same size.");
        }

        ECKey keyToSendWFJoinTo = getNewKeyFromWallet();
        Address addressJoinWFIsSentTo = keyToSendWFJoinTo.toAddress(networkParameters);

        WorkflowHandoverData workflowHandoverDataJoin
                = workflowExecutionPointFactory.createWorkflowJoinPoint(workflowInstance, timestamp);

        List<Address> addressesToRedeemPreviousP2SHOutput = new ArrayList<>();
        List<TransactionOutput> p2shOutputsFromPreviousTransactions = new ArrayList<>();
        List<Script> redeemScriptsForPreviousTransaction = new ArrayList<>();
        List<ECKey> keysForPreviousTransaction = new ArrayList<>();

        for (int i = 0; i < previousTransactions.size(); i++) {
            HandoverData previousTransaction = previousTransactions.get(i);
            Integer indexOfConnectedOutputToUse = indicesOfConnectedOutputToUse.get(i);

            //Prepare the unlocking of the previous transaction output.
            ECKey keyToUnlockPreviousTransaction = previousTransaction.getKeyToRedeemP2SHOutput(indexOfConnectedOutputToUse);
            keysForPreviousTransaction.add(keyToUnlockPreviousTransaction);

            Address addressOfPreviousTransaction = keyToUnlockPreviousTransaction.toAddress(networkParameters);
            addressesToRedeemPreviousP2SHOutput.add(addressOfPreviousTransaction);

            List<byte[]> dataToUnlockPreviousTransactionAsList = previousTransaction.getDataToRedeemP2SHOutput(indexOfConnectedOutputToUse);
            Script redeemScriptForPreviousTransaction = transactionBuilder.createRedeemScript(
                    addressOfPreviousTransaction,
                    convertDataToUnlockPreviousTransactionAsList(dataToUnlockPreviousTransactionAsList));
            redeemScriptsForPreviousTransaction.add(redeemScriptForPreviousTransaction);

            TransactionOutput outputToUseAsInput = previousTransaction.getTransactionReference().getOutputWithToken(indexOfConnectedOutputToUse);
            p2shOutputsFromPreviousTransactions.add(outputToUseAsInput);
        }

        SendRequest sendRequest = transactionBuilder.createWorkflowJoinTransaction(
                workflowHandoverDataJoin,
                addressesToRedeemPreviousP2SHOutput,
                p2shOutputsFromPreviousTransactions,
                redeemScriptsForPreviousTransaction,
                keysForPreviousTransaction,
                addressJoinWFIsSentTo,
                networkParameters);

        List<ECKey> keyList = new ArrayList<>();
        keyList.add(keyToSendWFJoinTo);
        HandoverData wfJoinPoint = new HandoverData(
                workflowHandoverDataJoin,
                new TransactionReference(sendRequest, Arrays.asList(0)),
                keyList,
                null,
                true,
                false,
                networkParameters);

        logger.debug("Created workflow join handoverPoint " + wfJoinPoint);
        return wfJoinPoint;
    }

    /**
     * Creates a HandoverData to mark the end a workflow instance.
     * NOTE: The included SendRequest must still be broadcast to the network.
     * NOTE: An end must be preceded by a start, intermediate or join. All of them have the token at position 0.
     */
    public HandoverData createEndWFTransaction(HandoverData previousTransaction, int timestamp) {
        openConnectionGuard("createEndWFTransaction");
        final int fixedTokenOutputPosition = 0;
        TransactionOutput p2shOutputFromPreviousTransaction = previousTransaction.getTransactionReference().getOutputWithToken(fixedTokenOutputPosition);

        WorkflowHandoverData workflowExecutionEnd
                = workflowExecutionPointFactory.createWorkflowEndPoint(
                previousTransaction.getWorkflowHandoverData().getWorkflowInstance(),
                timestamp);

        ECKey receiverKeyOfEndTransaction = getNewKeyFromWallet();
        Address addressEndWFIsSentTo = receiverKeyOfEndTransaction.toAddress(networkParameters);
        Address addressOfPreviousTransaction = previousTransaction.getKeyToRedeemP2SHOutput(fixedTokenOutputPosition).toAddress(networkParameters);

        SendRequest sendRequest = transactionBuilder.createWorkflowEndTransaction(
                workflowExecutionEnd,
                addressEndWFIsSentTo,
                p2shOutputFromPreviousTransaction,
                addressOfPreviousTransaction,
                previousTransaction.getKeyToRedeemP2SHOutput(fixedTokenOutputPosition),
                convertDataToUnlockPreviousTransactionAsList(
                        previousTransaction.getDataToRedeemP2SHOutput(fixedTokenOutputPosition)));

        HandoverData endPoint = new HandoverData(
                workflowExecutionEnd,
                new TransactionReference(sendRequest, (List<Integer>) null),
                Arrays.asList(receiverKeyOfEndTransaction),
                null,
                true,
                false,
                networkParameters);
        logger.debug("Created workflow end handoverPoint " + endPoint);

        return endPoint;
    }

    public Address getP2SHAddressToKeyAndData(ECKey key, byte[]... dataToDocument) {
        Address addressToSendToken = key.toAddress(networkParameters);
        Script redeemScriptToBeContainedInP2SH = transactionBuilder.
                createPay2PublicKeyHashScriptWithOptionalDataAttached(addressToSendToken, dataToDocument);
        return transactionBuilder.createP2SHAdressFromScript(redeemScriptToBeContainedInP2SH, networkParameters);
    }

    private void setInputParams(File checkpointFile, File storeFile, File walletFile, NetworkParameters networkParameters) {
        this.checkpointFile = checkpointFile;
        this.storeFile = storeFile;
        this.walletFile = walletFile;
        this.networkParameters = networkParameters;

        runtimeVerificationUtils.notNull(networkParameters, "The supplied networkParameters where null.");
        runtimeVerificationUtils.notNull(storeFile, "The supplied storeFile was null.");
        runtimeVerificationUtils.notNull(walletFile, "The supplied walletFile was null.");
    }

    /**
     * Initializes the connection to the supplied network and loads the files supplied in the Constructor.
     */
    private void initConnection() throws BlockStoreException, IOException, UnreadableWalletException {
        loadWallet();
        loadBlockStore();
        //SPV implementation of the block chain
        blockChain = new BlockChain(networkParameters, wallet, blockStore);
        initPeerGroup();
    }

    /**
     * Starts the connection to the supplied network
     * and synchronises the Block Chain.
     * <p>
     * IMPORTANT: The connection must be closed by stopBitcoinConnection()
     */
    private void startConnection() {
        //connect to Bitcoin Network peers
        peerGroup.start();
        //download chain headers
        peerGroup.downloadBlockChain();
    }

    /**
     * Stops the connection to the bitcion network and
     */
    private void stopBitcoinConnection() throws IOException, BlockStoreException {
        peerGroup.stop();
        wallet.saveToFile(walletFile);
        blockStore.close();
        blockChain = null;
        peerGroup = null;
        wallet = null;
        blockStore = null;
        checkpointFile = null;
        storeFile = null;
        walletFile = null;
        networkParameters = null;
        blockChainCrawler = null;
    }

    /**
     * Loads the wallet from walletFile or creates a new one.
     */
    private void loadWallet() throws UnreadableWalletException {
        if (runtimeVerificationUtils.fileExists(walletFile)) {
            logger.debug("Loading wallet file " + walletFile.toString());
            wallet = Wallet.loadFromFile(walletFile);
        } else {
            wallet = new Wallet(networkParameters);
        }
        wallet.autosaveToFile(walletFile, 60, TimeUnit.SECONDS, null);
    }

    /**
     * Loads the blockStore from storeFile or creates a new one.
     * Synchronises the blockStore with the checkpointFile if it was supplied.
     */
    private void loadBlockStore() throws IOException, BlockStoreException {
        boolean storeFileIsFresh = !storeFile.exists();
        if (!storeFileIsFresh) {
            logger.debug("Loading store file " + storeFile.toString());
        }
        blockStore = new SPVBlockStore(networkParameters, storeFile);
        if (storeFileIsFresh && runtimeVerificationUtils.fileExists(checkpointFile)) {
            if (storeFileIsFresh) {
                logger.debug("Loading checkpoint file to update block store before synchronization. File: " + checkpointFile.toString());
            }
            //load checkpoint into file
            InputStream checkpointFileIS = new FileInputStream(checkpointFile);
            CheckpointManager.checkpoint(networkParameters, checkpointFileIS, blockStore, wallet.getEarliestKeyCreationTime());
        }
    }

    /**
     * Sets up a PeerGroup which uses DnsDiscovery.
     */
    private void initPeerGroup() {
        peerGroup = new PeerGroup(networkParameters, blockChain);
        peerGroup.addWallet(wallet);
        peerGroup.addPeerDiscovery(new DnsDiscovery(networkParameters));
        //ensure that at least minBroadcastPeers peers are used for broadcast
        if (minBroadcastPeers > peerGroup.getMinBroadcastConnections()) {
            peerGroup.setMinBroadcastConnections(minBroadcastPeers);
        }
    }

    /**
     * Closes the connection to the network.
     */
    @PreDestroy
    private void cleanUpConnection() {
        if (connectionOpen) {
            logger.info("Closing connection to Bitcoin network.");
            try {
                closeConnection();
            } catch (IOException | BlockStoreException e) {
                logger.warn("Error while cleaning up resources.", e);
            }
        }
    }

    /**
     * Throws a RuntimeVerificationException if the connection is not open.
     */
    protected void openConnectionGuard(String methodName) {
        if (!connectionOpen) {
            String msg = methodName + "(): The connection was not open.";
            logger.warn(msg);
            throw new RuntimeVerificationException(msg);
        }
    }

    /**
     * Throws a RuntimeVerificationException if the connection is open.
     */
    protected void closedConnectionGuard(String methodName) {
        if (connectionOpen) {
            String msg = methodName + "(): The connection was already open.";
            logger.warn(msg);
            throw new RuntimeVerificationException(msg);
        }
    }

    private byte[][] convertDataToUnlockPreviousTransactionAsList(List<byte[]> dataToUnlockPreviousTransactionAsList) {
        byte[][] dataToUnlockPreviousTransaction = null;
        if (dataToUnlockPreviousTransactionAsList != null) {
            dataToUnlockPreviousTransaction = dataToUnlockPreviousTransactionAsList.toArray(new byte[0][0]);
        }
        return dataToUnlockPreviousTransaction;
    }

    public String getNewReceivingAddressInBase58() {
        openConnectionGuard("getNewReceivingAddress");
        Address receiveAddress = wallet.freshReceiveAddress();
        return receiveAddress.toBase58();
    }

}
