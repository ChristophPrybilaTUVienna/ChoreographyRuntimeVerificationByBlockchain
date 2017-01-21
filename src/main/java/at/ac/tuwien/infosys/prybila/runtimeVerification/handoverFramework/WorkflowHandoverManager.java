package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.BitcoinConnection;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.WorkflowExecutionPointFactory;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.HandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverType;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowInstance;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.BlockChainCrawler;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.BlockcypherMainnetCrawler;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.BlockcypherTestnetCrawler;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedOutput;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedTransaction;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.BasicCryptographyManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.OwnIdentityProvider;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.TokenSizeEstimator;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.WorkflowUpdater;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.IdProvider;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.WorkflowGraphStep;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Stores and manages Handoverpoints.
 * Operates on top off a single BitcoinConnection.
 * This class is thread safe.
 */
public class WorkflowHandoverManager {

    private final Logger logger = LoggerFactory.getLogger(WorkflowHandoverManager.class);

    private IdProvider idProvider;

    @Autowired
    private BitcoinConnection bitcoinConnection;

    private OwnIdentityProvider ownIdentityProvider;

    private RuntimeVerificationUtils runtimeVerificationUtils;
    private NetworkParameters networkParameters;
    private File storeFile;
    private File walletFile;
    private File checkpointFile;
    private File handoverStorageFile;
    private TokenSizeEstimator tokenSizeEstimator;
    private BlockChainCrawler blockChainCrawler;
    private BasicCryptographyManager basicCryptographyManager;

    private Map<WorkflowInstance, WorkflowGraphStep> graphStorage;

    private static final int maxBroadcastTries = 10;
    private static final int depthUntilConfirmed = 1;
    private static final int waitMaxMinutesForConfirmation = 90;
    private static final int waitMaxSecondsForBroadcast = 30;

    private final ReentrantLock handoverInitPhaseLock =  new ReentrantLock(true);

    public WorkflowHandoverManager(
            String networkParametersId,
            String pathToStoreFile,
            String pathToWalletFile,
            String pathToCheckpointFile,
            String pathToHandoverStorageFile,
            boolean failIfBitcoinFilesNotExists) {
        runtimeVerificationUtils = new RuntimeVerificationUtils();
        //fetch NetworkParameters
        runtimeVerificationUtils.notNull(networkParametersId);
        networkParameters = NetworkParameters.fromID(networkParametersId);
        runtimeVerificationUtils.notNull(networkParameters);
        //fetch BlockStore file
        storeFile = getFileForPath(pathToStoreFile, failIfBitcoinFilesNotExists);
        runtimeVerificationUtils.notNull(storeFile);
        //fetch Wallet file
        walletFile = getFileForPath(pathToWalletFile, failIfBitcoinFilesNotExists);
        runtimeVerificationUtils.notNull(walletFile);
        //fetch graphStorage file
        handoverStorageFile = getFileForPath(pathToHandoverStorageFile, false);
        //fetch Checkpoint file
        checkpointFile = getFileForPath(pathToCheckpointFile, false);

        tokenSizeEstimator = new TokenSizeEstimator(networkParameters);

        String token = new RuntimeVerificationUtils().readCrawlerTokenFromProperties();

        idProvider = new IdProvider();

        //Select the appropriate crawler
        if (networkParameters instanceof TestNet3Params) {
            blockChainCrawler = new BlockcypherTestnetCrawler(token);
        } else if (networkParameters instanceof MainNetParams) {
            blockChainCrawler = new BlockcypherMainnetCrawler(token);
        }
    }

    /**
     * Returns the file to the given path. If something fails, null is returned.
     * If #failIfBitcoinFilesNotExists is set to false and the file at the given path does not exist,
     * the file is created and deleted again. This tests if the application has write rights at the given path.
     */
    private File getFileForPath(String path, boolean failIfBitcoinFilesNotExists) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!runtimeVerificationUtils.fileExists(file)) {
            if (failIfBitcoinFilesNotExists) {
                return null;
            } else {
                try {
                    boolean success = file.createNewFile();
                    if (!success) {
                        return null;
                    }
                    success = file.delete();
                    if (!success) {
                        return null;
                    }
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return file;
    }

    @PostConstruct
    private void init() throws Exception {
        //Context.getOrCreate(params);
        if (runtimeVerificationUtils.fileExists(handoverStorageFile)) {
            if (handoverStorageFile.length() > 0) {
                graphStorage = loadHandoverStorage();
            }
        }
        if (graphStorage == null) {
            graphStorage = new HashMap<>();
        }
        bitcoinConnection.openConnection(networkParameters, storeFile, walletFile, checkpointFile);
    }

    @PreDestroy
    private void close() {
        try {
            saveHandoverStorage();
        } catch (IOException e) {
            logger.error("An exception occurred while saving the HandoverStorage", e);
        }
        try {
            bitcoinConnection.closeConnection();
        } catch (Exception e) {
            logger.error("An exception occurred while closing the BitcoinConnection", e);
        }
    }

    public synchronized Coin getBalanceOfStorage() {
        return bitcoinConnection.getBalance();
    }

    /**
     * Returns true if handoverData is stored for the given Workflowinstance
     */
    public synchronized boolean workflowInstanceDataIsStored(short workflowId) {
        return graphStorage.containsKey(new WorkflowInstance(workflowId));
    }

    /**
     * Returns the stored handoverData for the given workflow instance as clone or null
     */
    public synchronized WorkflowGraphStep getWorkflowInstanceDataAsClone(short workflowId) {
        WorkflowGraphStep wfGraphRoot = graphStorage.get(new WorkflowInstance(workflowId));
        if (wfGraphRoot != null) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput out = new ObjectOutputStream(bos);
                out.writeObject(wfGraphRoot);
                out.flush();
                byte[] graphAsBytes = bos.toByteArray();
                ByteArrayInputStream bis = new ByteArrayInputStream(graphAsBytes);
                ObjectInput in = new ObjectInputStream(bis);
                return (WorkflowGraphStep) in.readObject();
            } catch (Exception e) {
                logger.error("Failed to clone workflow " + workflowId, e);
            }
        }
        return null;
    }

    public synchronized String getTxHashOfWorkflowGraphStep(short wfId, int wfStepId) {
        WorkflowGraphStep wfStep = getStepForId(graphStorage.get(new WorkflowInstance(wfId)), wfStepId);
        return wfStep.getHandoverData().getTransactionReference().getTxHash();
    }

    /**
     * Creates and publishes a start marker for the given workflow instance.
     * Does not wait for the published transaction to be confirmed.
     * The workflow with the given id must not yet exist.
     * Returns the id of the newly created step.
     */
    public synchronized int startWorkflowAsync(short workflowId, int numOfExpectedSteps, int numOfExpectedSplits) throws InsufficientMoneyException, InterruptedException, ExecutionException, IOException {
        logger.debug(String.format(
                "Starting workflow async (workflowId=%s, numOfExpectedSteps=%s, numOfExpectedSplits=%s)",
                "" + workflowId, "" + numOfExpectedSteps, "" + numOfExpectedSplits));
        //workflow must not yet exist
        workflowNotExists(workflowId);
        WorkflowInstance newWF = new WorkflowInstance(workflowId);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        Coin tokenSizeForWF = tokenSizeEstimator.calculateAppropriateTokenSizeForWF(numOfExpectedSteps, numOfExpectedSplits);
        HandoverData startOfWF = bitcoinConnection.createStartWFTransaction(
                newWF,
                runtimeVerificationUtils.getCurrentTimeInUnixTimestamp(),
                tokenSizeForWF);
        WorkflowGraphStep startHandover = new WorkflowGraphStep(ownIdentity, ownIdentity, startOfWF, null, null, idProvider.getNextId());
        graphStorage.put(newWF, startHandover);
        saveHandoverStorage();
        bitcoinConnection.publishHandoverPoint(startOfWF, false, maxBroadcastTries, waitMaxSecondsForBroadcast, depthUntilConfirmed);
        logger.debug("Finished workflow starting async");
        return startHandover.getId();
    }

    /**
     * Creates and publishes a start marker for the given workflow instance.
     * Blocks until the published transaction is confirmed.
     * The workflow with the given id must not yet exist.
     * Returns the id of the newly created step.
     */
    public synchronized int startWorkflow(short workflowId, int numOfExpectedSteps, int numOfExpectedSplits) throws InsufficientMoneyException, ExecutionException, InterruptedException, IOException {
        logger.debug(String.format(
                "Starting workflow (workflowId=%s, numOfExpectedSteps=%s, numOfExpectedSplits=%s)",
                "" + workflowId, "" + numOfExpectedSteps, "" + numOfExpectedSplits));
        int startStepId = startWorkflowAsync(workflowId, numOfExpectedSteps, numOfExpectedSplits);
        HandoverData startOfWF = graphStorage.get(new WorkflowInstance(workflowId)).getHandoverData();
        logger.debug(String.format(
                "Waiting for workflow start to reach depth (depthUntilConfirmed=%s)",
                "" + depthUntilConfirmed));
        saveHandoverStorage();
        bitcoinConnection.performAggressivePublishing(startOfWF, depthUntilConfirmed, waitMaxMinutesForConfirmation, true, waitMaxSecondsForBroadcast);
        logger.debug("Finished workflow starting");
        return startStepId;
    }

    /**
     * Creates and publishes an end marker for the given workflow instance.
     * Does not wait for the published transaction to be confirmed.
     * The workflow with the given id must already exist, it must have been started by us
     * and it must exclusively be under our control.
     * The relaxedOwnerShip flag enables the publishing of an end marker, even if the wf was not started by us.
     * The reactOnPreviousTemplate flag enables the publishing of an end marker, even if the last stored step has not been completed. The next previous step is tried instead.
     */
    public synchronized void endWorkflowAsync(short workflowId, boolean relaxedOwnerShip, boolean reactOnPreviousTemplate) throws InterruptedException, ExecutionException, IOException {
        logger.debug(String.format(
                "Ending workflow async (workflowId=%s, relaxedOwnerShip=%s)", workflowId, relaxedOwnerShip));
        //workflow must exist
        workflowExists(workflowId);
        WorkflowInstance wfToEnd = new WorkflowInstance(workflowId);
        /*if (!reactOnPreviousTemplate) {
            updateWorkflowDataWithOnlineInformation(wfToEnd);
        }*/
        WorkflowGraphStep workflowDataRoot = graphStorage.get(wfToEnd);
        workflowWasNotEnded(workflowDataRoot);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        WorkflowGraphStep singleLeafOfGraph;
        if (!relaxedOwnerShip) {
            //workflow must have been started by us
            workflowWasStartedBy(workflowDataRoot, ownIdentity);
            //workflow must belong to us
            singleLeafOfGraph = workflowHasSingleLeafAndCurrentlyBelongsTo(workflowDataRoot, ownIdentity);
        } else {
            singleLeafOfGraph = workflowHasSingleLeaf(workflowDataRoot, ownIdentity);
        }

        if (!reactOnPreviousTemplate) {
            //the current handover is not in a template state (i.e. was finished)
            workflowStepIsNotInTemplateState(singleLeafOfGraph);
        } else {
            if (singleLeafOfGraph.getHandoverData().isTemplate()) {
                //this is most likely a handover and therefore has only a single parent.
                singleLeafOfGraph = singleLeafOfGraph.getParents().get(0);
            }
        }
        HandoverData endOfWF = bitcoinConnection.createEndWFTransaction(
                singleLeafOfGraph.getHandoverData(),
                runtimeVerificationUtils.getCurrentTimeInUnixTimestamp());
        WorkflowGraphStep endHandover = new WorkflowGraphStep(ownIdentity, ownIdentity, endOfWF, Arrays.asList(singleLeafOfGraph), null, idProvider.getNextId());
        singleLeafOfGraph.setChildren(Arrays.asList(endHandover));
        graphStorage.put(wfToEnd, workflowDataRoot);
        saveHandoverStorage();
        bitcoinConnection.publishHandoverPoint(endOfWF, false, maxBroadcastTries, waitMaxSecondsForBroadcast, depthUntilConfirmed);
        logger.debug("Finished workflow ending async");
    }

    public synchronized void endWorkflowAsync(short workflowId) throws InterruptedException, ExecutionException, IOException {
        endWorkflowAsync(workflowId, false, false);
    }

    /**
     * Creates and publishes an end marker for the given workflow instance.
     * Blocks until the published transaction is confirmed.
     * The workflow with the given id must already exist, it must have been started by us
     * and it must exclusively be under our control.
     * The relaxedOwnerShip flag enables the publishing of an end marker, even if the wf was not started by us.
     * The reactOnPreviousTemplate flag enables the publishing of an end marker, even if the last stored step has not been completed. The next previous step is tried instead.
     */
    public synchronized void endWorkflow(short workflowId, boolean relaxedOwnerShip, boolean reactOnPreviousTemplate) throws InterruptedException, ExecutionException, IOException {
        logger.debug(String.format(
                "Ending workflow (workflowId=%s, relaxedOwnerShip=%s)", workflowId, relaxedOwnerShip));
        endWorkflowAsync(workflowId, relaxedOwnerShip, reactOnPreviousTemplate);
        WorkflowGraphStep workflowDataRoot = graphStorage.get(new WorkflowInstance(workflowId));
        HandoverData endOfWf = workflowHasSingleLeafAndCurrentlyBelongsTo(workflowDataRoot, ownIdentityProvider.getOwnIdentity()).getHandoverData();
        saveHandoverStorage();
        logger.debug(String.format(
                "Waiting for workflow end to reach depth (depthUntilConfirmed=%s)",
                "" + depthUntilConfirmed));
        bitcoinConnection.performAggressivePublishing(endOfWf, depthUntilConfirmed, waitMaxMinutesForConfirmation, true, waitMaxSecondsForBroadcast);
        logger.debug("Finished workflow ending");
    }

    public synchronized void endWorkflow(short workflowId) throws InterruptedException, ExecutionException, IOException {
        endWorkflow(workflowId, false, false);
    }

    /**
     * Creates and publishes a split marker for the given workflow instance.
     * Does not wait for the published transaction to be confirmed.
     * The workflow with the given id must already exist and the provided
     * fromStep must be a leaf under our control.
     * Returns the id of the newly created step.
     */
    public synchronized int splitWorkflowAsync(
            short workflowId,
            int fromStepId,
            int outputIndexOfPreviousTransactionToUse,
            int numberOfSplitPaths) throws IOException, ExecutionException, InterruptedException {
        logger.debug(String.format(
                "Creating async split for workflow (workflowId=%s, fromStepId=%s, " +
                        "outputIndexOfPreviousTransactionToUse=%s, " +
                        "numberOfSplitPaths=%s)",
                "" + workflowId, fromStepId,
                "" + outputIndexOfPreviousTransactionToUse,
                "" + numberOfSplitPaths));
        //workflow must exist
        workflowExists(workflowId);
        WorkflowInstance wfToSplit = new WorkflowInstance(workflowId);
        WorkflowGraphStep workflowRoot = graphStorage.get(wfToSplit);
        workflowWasNotEnded(workflowRoot);
        WorkflowGraphStep fromStep = getStepForId(workflowRoot, fromStepId);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        //originating handover step is leaf.
        isLeafOfWorkflow(workflowRoot, fromStep);
        //originating handover step belongs to us
        workflowStepBelongsTo(fromStep, ownIdentity, false);
        //originating handover step is not in a template state (i.e. was finished)
        workflowStepIsNotInTemplateState(fromStep);
        HandoverData splitPoint = bitcoinConnection.createSplitWFTransaction(
                wfToSplit,
                runtimeVerificationUtils.getCurrentTimeInUnixTimestamp(),
                fromStep.getHandoverData(),
                outputIndexOfPreviousTransactionToUse,
                numberOfSplitPaths);

        WorkflowGraphStep splitStep = new WorkflowGraphStep(ownIdentity, ownIdentity, splitPoint, Arrays.asList(fromStep), null, idProvider.getNextId());
        addChildStep(fromStep, splitStep);
        graphStorage.put(wfToSplit, workflowRoot);
        saveHandoverStorage();
        bitcoinConnection.publishHandoverPoint(splitPoint, false, maxBroadcastTries, waitMaxSecondsForBroadcast, depthUntilConfirmed);
        logger.debug("Finished creating async split workflow");
        return splitStep.getId();
    }

    /**
     * Creates and publishes a split marker for the given workflow instance.
     * Blocks until the published transaction is confirmed.
     * The workflow with the given id must already exist and the provided
     * fromStep must be a leaf under our control.
     * Returns the id of the newly created step.
     */
    public synchronized int splitWorkflow(
            short workflowId,
            int fromStepId,
            int outputIndexOfPreviousTransactionToUse,
            int numberOfSplitPaths) throws IOException, ExecutionException, InterruptedException {
        logger.debug(String.format(
                "Creating split for workflow (workflowId=%s, fromStepId=%s, " +
                        "outputIndexOfPreviousTransactionToUse=%s, " +
                        "numberOfSplitPaths=%s)",
                "" + workflowId, fromStepId,
                "" + outputIndexOfPreviousTransactionToUse,
                "" + numberOfSplitPaths));
        int splitOfWFId = splitWorkflowAsync(workflowId, fromStepId, outputIndexOfPreviousTransactionToUse, numberOfSplitPaths);
        logger.debug(String.format(
                "Waiting for workflow split to reach depth (depthUntilConfirmed=%s)",
                "" + depthUntilConfirmed));
        bitcoinConnection.performAggressivePublishing(
                getStepForId(graphStorage.get(
                        new WorkflowInstance(workflowId)), splitOfWFId).getHandoverData(),
                depthUntilConfirmed, waitMaxMinutesForConfirmation, true, waitMaxSecondsForBroadcast);
        logger.debug("Finished creating split workflow");
        return splitOfWFId;
    }

    /**
     * Creates and publishes a join marker for the given workflow instance.
     * Does not wait for the published transaction to be confirmed.
     * The workflow with the given id must already exist and the provided
     * fromStep must be a leafs under our control.
     * Returns the id of the newly created step.
     */
    public synchronized int joinWorkflowAsync(
            short workflowId,
            List<Integer> fromStepIds,
            int timestamp,
            List<Integer> outputIndicesOfPreviousTransactionToUse) throws IOException, ExecutionException, InterruptedException {
        logger.debug(String.format(
                "Creating async join for workflow (workflowId=%s, fromStepIds=%s, timestamp=%s, " +
                        "outputIndicesOfPreviousTransactionToUse=%s)",
                "" + workflowId, fromStepIds.toArray(), "" + timestamp,
                "" + outputIndicesOfPreviousTransactionToUse.toArray()));
        //workflow must exist
        workflowExists(workflowId);
        WorkflowInstance wfToJoin = new WorkflowInstance(workflowId);
        WorkflowGraphStep workflowRoot = graphStorage.get(wfToJoin);
        workflowWasNotEnded(workflowRoot);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        if (fromStepIds.size() != outputIndicesOfPreviousTransactionToUse.size()) {
            throw new RuntimeVerificationException(
                    "Originating WfStepList and outputIndicesOfPreviousTransactionToUseList " +
                            "must be of same size.");
        }
        List<HandoverData> previousTransactionData = new ArrayList<>();
        List<WorkflowGraphStep> fromSteps = new ArrayList<>();
        for (int fromStepId : fromStepIds) {
            WorkflowGraphStep fromStep = getStepForId(workflowRoot, fromStepId);
            fromSteps.add(fromStep);
            //originating handover step is leaf.
            isLeafOfWorkflow(workflowRoot, fromStep);
            //originating handover step belongs to us
            workflowStepBelongsTo(fromStep, ownIdentity, false);
            //originating handover step is not in a template state (i.e. was finished)
            workflowStepIsNotInTemplateState(fromStep);
            previousTransactionData.add(fromStep.getHandoverData());
        }
        HandoverData joinPoint = bitcoinConnection.createJoinWFTransaction(
                wfToJoin,
                timestamp,
                previousTransactionData,
                outputIndicesOfPreviousTransactionToUse);

        WorkflowGraphStep handover = new WorkflowGraphStep(ownIdentity, ownIdentity, joinPoint, fromSteps, null, idProvider.getNextId());
        for (WorkflowGraphStep fromStep : fromSteps) {
            addChildStep(fromStep, handover);
        }
        graphStorage.put(wfToJoin, workflowRoot);
        saveHandoverStorage();
        bitcoinConnection.publishHandoverPoint(joinPoint, false, maxBroadcastTries, waitMaxSecondsForBroadcast, depthUntilConfirmed);
        logger.debug("Finished creating async join workflow");
        return handover.getId();
    }

    /**
     * Creates and publishes a join marker for the given workflow instance.
     * Blocks until the published transaction is confirmed.
     * The workflow with the given id must already exist and the provided
     * fromStep must be a leafs under our control.
     * Returns the id of the newly created step.
     */
    public synchronized int joinWorkflow(
            short workflowId,
            List<Integer> fromStepIds,
            int timestamp,
            List<Integer> outputIndicesOfPreviousTransactionToUse) throws IOException, ExecutionException, InterruptedException {
        logger.debug(String.format(
                "Creating join for workflow (workflowId=%s, fromStepIds=%s, timestamp=%s, " +
                        "outputIndicesOfPreviousTransactionToUse=%s)",
                "" + workflowId, fromStepIds.toArray(), "" + timestamp,
                "" + outputIndicesOfPreviousTransactionToUse.toArray()));
        int joinOfWFId = joinWorkflowAsync(workflowId, fromStepIds, timestamp, outputIndicesOfPreviousTransactionToUse);
        logger.debug(String.format(
                "Waiting for workflow join to reach depth (depthUntilConfirmed=%s)",
                "" + depthUntilConfirmed));
        WorkflowGraphStep joinOfWF = getStepForId(graphStorage.get(
                new WorkflowInstance(workflowId)), joinOfWFId);
        bitcoinConnection.performAggressivePublishing(joinOfWF.getHandoverData(), depthUntilConfirmed, waitMaxMinutesForConfirmation, true, waitMaxSecondsForBroadcast);
        logger.debug("Finished creating join workflow");
        return joinOfWF.getId();
    }

    /**
     * Init a workflow handover on the receiver side.
     * Documents the wfHandover data which is expected to be included in the transaction by the sender.
     * Returns a bitcoin public key from the wallet.
     */
    public synchronized byte[] initHandoverOnReceiverSide(short workflowId, Identity from, int timestamp, byte taskId, byte[] dataToDocument, List<byte[]> previouslyIncludedData, boolean relaxOwnerCheck) throws IOException {
        logger.debug(String.format(
                "Init handover of workflow on receiver side (workflowId=%s, from=%s, timestamp=%s, taskId=%s, dataToDocument=%s, previouslyIncludedData=%s, relaxOwnerCheck=%s)",
                "" + workflowId, "" + from, "" + timestamp, "" + taskId, Arrays.toString(dataToDocument), previouslyIncludedData, "" + relaxOwnerCheck));
        checkIfLockIsOwned();
        WorkflowGraphStep wfDataRoot = updateWFHandoverDataIfExists(workflowId, true);
        Address p2SHAddressOfSender = getExposedP2SHAddress(from, previouslyIncludedData);
        if (wfDataRoot == null) {
            wfDataRoot = initWorkflowWithOnlineDataThroughPublicKeyOfIdentity(p2SHAddressOfSender);
        }
        //it must not have been already ended
        workflowWasNotEnded(wfDataRoot);
        WorkflowGraphStep wfStepPrecedingTheHandover = findRelatedLeafStepToPublicKeyOfIdentity(wfDataRoot, p2SHAddressOfSender);
        workflowStepBelongsTo(wfStepPrecedingTheHandover, from, relaxOwnerCheck);
        WorkflowInstance expectedInstance = new WorkflowInstance(workflowId);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        ECKey receiverKey = bitcoinConnection.getNewKeyFromWallet();
        WorkflowHandoverData expectedWorkflowHandoverData = new WorkflowExecutionPointFactory()
                .createWorkflowHandoverPoint(expectedInstance, timestamp, taskId);
        if (dataToDocument != null) {
            expectedWorkflowHandoverData.setWorkflowData(dataToDocument);
        }
        List<ECKey> keyList = new ArrayList<>();
        keyList.add(receiverKey);
        List<List<byte[]>> handoverDataList = null;
        if (dataToDocument != null) {
            List<byte[]> handoverDataListToInclude = new ArrayList<>();
            handoverDataListToInclude.add(expectedWorkflowHandoverData.getHash160OfWorkflowData());
            handoverDataList = Collections.singletonList(handoverDataListToInclude);
        }
        HandoverData expectedHandoverpoint = new HandoverData(
                expectedWorkflowHandoverData,
                null,
                keyList,
                handoverDataList,
                false,
                true,
                networkParameters
        );
        WorkflowGraphStep workflowHandoverAboutToBeReceived =
                new WorkflowGraphStep(from, ownIdentity, expectedHandoverpoint, Arrays.asList(wfStepPrecedingTheHandover), null, idProvider.getNextId());
        addChildStep(wfStepPrecedingTheHandover, workflowHandoverAboutToBeReceived);
        graphStorage.put(expectedInstance, wfDataRoot);
        saveHandoverStorage();
        logger.debug("Finished init handover of workflow on receiver side (Returning publicKey)");
        return receiverKey.getPubKey();
    }

    /**
     * If the workflow already exists in the local storage, update and return the graph's root.
     * If the workflow does not yet exist, null is returned.
     */
    private WorkflowGraphStep updateWFHandoverDataIfExists(short workflowId, boolean logRequest) throws IOException {
        if (workflowInstanceDataIsStored(workflowId)) {
            updateWorkflowDataWithOnlineInformation(new WorkflowInstance(workflowId), logRequest);
            return graphStorage.get(new WorkflowInstance(workflowId));
        } else {
            return null;
        }
    }

    public synchronized void updateWFHandoverDataIfExistsForWorflow(short workflowId) throws IOException {
        checkIfLockIsOwned();
        updateWFHandoverDataIfExists(workflowId, false);
        releaseLock();
    }


    /**
     * Creates and returns handover marker template for the given workflow instance.
     * The workflow with the given id must already exist and the provided
     * fromStep must be a leaf under our control.
     */
    public synchronized byte[] createHandoverWorkflowTemplate(
            short workflowId,
            int fromStepId,
            Identity to,
            int timestamp,
            byte taskId,
            byte[] dataToDocument,
            boolean updateFirst,
            int outputIndexOfPreviousTransactionToUse) throws IOException {
        logger.debug(String.format(
                "Creating handover template of workflow (workflowId=%s, fromStepId=%s, to=%s, " +
                        "timestamp=%s, taskId=%s, dataToDocument=%s, updateFirst=%s, " +
                        "outputIndexOfPreviousTransactionToUse=%s)",
                "" + workflowId, fromStepId, "" + to, "" + timestamp, "" + taskId, Arrays.toString(dataToDocument), "" + updateFirst, "" + outputIndexOfPreviousTransactionToUse));
        checkIfLockIsOwned();
        //workflow must exist
        workflowExists(workflowId);
        WorkflowInstance wfToHandover = new WorkflowInstance(workflowId);
        if (updateFirst) {
            updateWorkflowDataWithOnlineInformation(wfToHandover);
        }
        WorkflowGraphStep workflowRoot = graphStorage.get(wfToHandover);
        workflowWasNotEnded(workflowRoot);
        WorkflowGraphStep fromStep = getStepForId(workflowRoot, fromStepId);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        //originating handover step is leaf.
        isLeafOfWorkflow(workflowRoot, fromStep);
        //originating handover step belongs to us
        workflowStepBelongsTo(fromStep, ownIdentity, false);
        //originating handover step is not in a template state (i.e. was finished)
        workflowStepIsNotInTemplateState(fromStep);
        ECKey publicKeyOfReceiver = ECKey.fromPublicOnly(to.getBitcoinPublicKey());
        HandoverData handoverTemplate = bitcoinConnection.prepareHandoverTemplate(
                timestamp,
                taskId,
                dataToDocument,
                fromStep.getHandoverData(),
                publicKeyOfReceiver,
                outputIndexOfPreviousTransactionToUse);
        WorkflowGraphStep handover = new WorkflowGraphStep(ownIdentity, to, handoverTemplate, Arrays.asList(fromStep), null, idProvider.getNextId());
        addChildStep(fromStep, handover);
        graphStorage.put(wfToHandover, workflowRoot);
        saveHandoverStorage();
        logger.debug("Finished creating handover template of workflow (Returning handoverTemplate)");
        return bitcoinConnection.serializeTransaction(handoverTemplate);
    }

    /**
     * Decrypts the previously received decrypted data to document in the given handover
     * Updates the previously initialized expected handover.
     * The workflow with the given id must already exist and one of the graph's leafs must correspond to the
     * public key provided by the sender.
     */
    public synchronized void decryptHandoverWorkflowDataWithSymmetricalKeyOnReceiverSide(short workflowId, Identity from, String symKey) throws IOException {
        logger.debug(String.format(
                "Decrypt data to document in handover of workflow on receiver side (workflowId=%s, from=%s, symKey=%s)",
                "" + workflowId, "" + from, symKey));
        checkIfLockIsOwned();
        //workflow must exist
        workflowExists(workflowId);
        WorkflowGraphStep wfStepWithHandover = getLeafTemplateBelongingToUsAndOriginatingFromSender(workflowId, from);

        WorkflowHandoverData expectedWorkflowHandoverData = wfStepWithHandover.getHandoverData().getWorkflowHandoverData();
        byte[] encryptedData = expectedWorkflowHandoverData.getWorkflowData();
        byte[] decryptedData = basicCryptographyManager.symmetricallyDecryptData(encryptedData, symKey);

        wfStepWithHandover.getHandoverData().getWorkflowHandoverData().setWorkflowData(decryptedData);
        List<byte[]> handoverDataList = new ArrayList<>();
        handoverDataList.add(expectedWorkflowHandoverData.getHash160OfWorkflowData());
        wfStepWithHandover.getHandoverData().setDataToRedeemP2SHOutput(Collections.singletonList(handoverDataList));
        logger.debug("Finished decrypting data to document in handover of workflow on receiver side");
    }

    /**
     * Confirms a workflow handover on the receiver side.
     * Updates the previously created handover template.
     * The workflow with the given id must already exist and one of the graph's leafs must correspond to the
     * public key provided by the sender.
     * The received transaction template must match the expected handover template.
     * Returns an off-chain signature of the serialized transactionTemplate
     */
    public synchronized byte[] confirmHandoverWorkflowTemplateOnReceiverSide(short workflowId, Identity from, byte[] serializedTransaction) throws IOException {
        logger.debug(String.format(
                "Confirming handover template of workflow on receiver side (workflowId=%s, from=%s, serializedTransaction=%s)",
                "" + workflowId, "" + from, Arrays.toString(serializedTransaction)));
        checkIfLockIsOwned();
        //workflow must exist
        workflowExists(workflowId);
        WorkflowGraphStep wfStepWithHandover = getLeafTemplateBelongingToUsAndOriginatingFromSender(workflowId, from);
        ECKey receiverKey = wfStepWithHandover.getHandoverData().getKeyToRedeemP2SHOutput(0); //This is a handover on the receiver side. The token is always on position 0
        byte[] dataToInclude = wfStepWithHandover.getHandoverData().getWorkflowHandoverData().getWorkflowData();
        HandoverData receivedHandover = bitcoinConnection.deserializeHandoverTransactionToHandoverPoint(serializedTransaction, receiverKey, dataToInclude);
        WorkflowHandoverData expectedWFMetaData = wfStepWithHandover.getHandoverData().getWorkflowHandoverData();
        WorkflowHandoverData receivedWFMetaData = receivedHandover.getWorkflowHandoverData();
        //Verifies that Output#2 contains the negotiated terms
        //Verifies that Output#1 can be retrieved
        //Verifies that Input#1 contains valid redeem script
        if (!expectedWFMetaData.equals(receivedWFMetaData) ||
                !bitcoinConnection.hasMatchingP2SHScript(receivedHandover, 0) //This is a handover on the receiver side. The token is always on position 0
                || !transactionTemplateInputContainsValidRedeemScript(receivedHandover.getTransactionReference().getBitcoinJTransaction())) {
            throw new RuntimeVerificationException("The supplied template contained information which was not agreed on.");
        }
        wfStepWithHandover.setHandoverData(receivedHandover);
        saveHandoverStorage();
        logger.debug("Finished confirming handover template of workflow on receiver side (Returning off-chain signature)");
        return bitcoinConnection.offChainSignTransaction(receivedHandover, receiverKey);
    }

    /**
     * Throws an exception if handoverInitPhaseLock is not owned.
     * This check is used for methods that must be chained together in a specific order.
     */
    private void checkIfLockIsOwned() {
        if(!handoverInitPhaseLock.isHeldByCurrentThread()) {
            throw new RuntimeVerificationException("Tried to access critical method without owning handoverInitPhaseLock.");
        }
    }

    public boolean tryToAcquireLock() {
        return handoverInitPhaseLock.tryLock();
    }

    public void acquireLock() {
        logger.debug("Waiting for lock.");
        handoverInitPhaseLock.lock();
        logger.debug("Acquired lock.");
    }

    public void conditionallyReleaseLock() {
        if(handoverInitPhaseLock.isHeldByCurrentThread()) {
            releaseLock();
        }
    }

    private void releaseLock() {
        logger.debug("Releasing lock.");
        handoverInitPhaseLock.unlock();
    }

    /**
     * Returns true, if input#1 of the given transaction template contains a valid redeem script for its referenced
     * output.
     */
    private boolean transactionTemplateInputContainsValidRedeemScript(Transaction transactionTemplate) throws IOException {
        TransactionInput inputContainingRedeemScriptAndPubKey = transactionTemplate.getInput(0);
        Script unlockingScriptWithPubKeyAndRedeemScript = inputContainingRedeemScriptAndPubKey.getScriptSig();
        String previousTxHash = transactionTemplate.getInput(0).getOutpoint().getHash().toString();
        int outputIndexOfPreviousTxHash = (int) transactionTemplate.getInput(0).getOutpoint().getIndex();
        ParsedTransaction previousTxFoundOnline = blockChainCrawler.getTransactionInformation(previousTxHash);
        ParsedOutput connectedOutput = previousTxFoundOnline.getOutputs().get(outputIndexOfPreviousTxHash);
        Script placedP2SHScript = new Script(connectedOutput.getScriptBytes());
        //expect the pubKey and the redeem script
        if (unlockingScriptWithPubKeyAndRedeemScript.getChunks().size() != 2) {
            return false;
        }
        ScriptChunk scriptPartContainingRedeemScript = unlockingScriptWithPubKeyAndRedeemScript.getChunks().get(1);
        Script redeemScript = new Script(scriptPartContainingRedeemScript.data);
        Script expectedP2SHScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        return expectedP2SHScript.equals(placedP2SHScript);
    }

    /**
     * Finishes and publishes a workflow handover for the given workflow
     * instance with the given received offChainSignature.
     * Does not wait for the published transaction to be confirmed.
     * The workflow with the given id must already exist
     * and the latest workflow entry must be an handover template.
     */
    public synchronized void finishAndPublishHandoverWorkflowTemplateAsync(
            short workflowId,
            Identity to,
            byte[] offChainSignature,
            int outputIndexOfPreviousTransactionToUse) throws InterruptedException, ExecutionException, IOException {
        logger.debug(String.format(
                "Finalize and publish handover template of workflow async (workflowId=%s, to=%s, offChainSignature=%s, outputIndexOfPreviousTransactionToUse=%s)",
                "" + workflowId, "" + to, Arrays.toString(offChainSignature), "" + outputIndexOfPreviousTransactionToUse));
        checkIfLockIsOwned();
        //workflow must exist
        workflowExists(workflowId);
        WorkflowGraphStep wfTemplate = getLeafOriginatingFromUsAndDirectedAtReceiver(workflowId, to);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        //last entry must be an expected template
        if (!wfTemplate.getHandoverData().isTemplate() ||
                !(wfTemplate.getFrom().equals(ownIdentity) && wfTemplate.getTo().equals(to))) {
            throw new RuntimeVerificationException("The last workflow step in the storage is " +
                    "not the expected incomplete wf handover.");
        }
        HandoverData previousTransaction = wfTemplate.getParents().get(0).getHandoverData(); //This is handover transaction. It should only have one input.
        HandoverData completeHandover = bitcoinConnection.finishHandoverTemplate(previousTransaction, wfTemplate.getHandoverData(), offChainSignature, outputIndexOfPreviousTransactionToUse);
        wfTemplate.setHandoverData(completeHandover);
        saveHandoverStorage();
        bitcoinConnection.publishHandoverPoint(completeHandover, false, BitcoinConnection.maxNumOfBroadCastTries, waitMaxSecondsForBroadcast, depthUntilConfirmed);
        releaseLock();
        logger.debug("Finished finalizing and publishing handover template of workflow async");
    }

    /**
     * Finishes and publishes a workflow handover for the given workflow
     * instance with the given received offChainSignature.
     * Blocks until the published transaction is confirmed.
     * The workflow with the given id must already exist
     * and the latest workflow entry must be an handover template.
     */
    public synchronized void finishAndPublishHandoverWorkflowTemplate(
            short workflowId,
            Identity to,
            byte[] offChainSignature,
            int outputIndexOfPreviousTransactionToUse) throws InsufficientMoneyException, ExecutionException, InterruptedException, IOException {
        logger.debug(String.format(
                "Finalize and publish handover template of workflow (workflowId=%s, to=%s, offChainSignature=%s, outputIndexOfPreviousTransactionToUse=%s)",
                "" + workflowId, "" + to, Arrays.toString(offChainSignature), "" + outputIndexOfPreviousTransactionToUse));
        checkIfLockIsOwned();
        finishAndPublishHandoverWorkflowTemplateAsync(workflowId, to, offChainSignature, outputIndexOfPreviousTransactionToUse);
        HandoverData handoverOfWF = getLeafOriginatingFromUsAndDirectedAtReceiver(workflowId, to).getHandoverData();
        saveHandoverStorage();
        logger.debug(String.format(
                "Waiting for workflow handover to reach depth (depthUntilConfirmed=%s)",
                "" + depthUntilConfirmed));
        bitcoinConnection.performAggressivePublishing(handoverOfWF, depthUntilConfirmed, waitMaxMinutesForConfirmation, true, waitMaxSecondsForBroadcast);
        logger.debug("Finished finalizing and publishing handover template of workflow");
    }

    /**
     * Finishes a received workflow handover for the given workflow
     * instance and the given sender. The sender must have already published the workflow handover.
     * The local data is then updated with the published data found online.
     * The workflow with the given id must already exist
     * and the latest workflow entry must be a received handover template.
     * Returns the id of the finalized WorkflowGraphStep if the published handover was found and the local data was successfully updated.
     * Returns -1 if the published handover was not found.
     */
    public synchronized int finishHandoverWorkflowTemplateOnReceiverSide(short workflowId, Identity from) throws IOException {
        logger.debug(String.format(
                "Finalize handover template of workflow on receiver side (workflowId=%s, from=%s)",
                "" + workflowId, "" + from));
        checkIfLockIsOwned();
        //workflow must exist
        workflowExists(workflowId);
        WorkflowGraphStep wfStepWithHandover = getLeafTemplateBelongingToUsAndOriginatingFromSender(workflowId, from);
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        //last entry must be an expected template including
        if (!wfStepWithHandover.getHandoverData().isTemplate() ||
                !(wfStepWithHandover.getFrom().equals(from) && wfStepWithHandover.getTo().equals(ownIdentity)) ||
                wfStepWithHandover.getHandoverData().getTransactionReference() == null) {
            throw new RuntimeVerificationException("The last workflow step in the storage is " +
                    "not the expected incomplete wf handover.");
        }
        HandoverData incompleteReceivedHandover = wfStepWithHandover.getHandoverData();
        ParsedTransaction transactionPublishedBySender = blockChainCrawler.getPublishedTransactionInformationOfHandoverTemplate(
                incompleteReceivedHandover.getTransactionReference().getBitcoinJTransaction());
        if (transactionPublishedBySender == null) {
            logger.debug("The transaction was not yet published. " +
                    "Finished finalizing handover template of workflow on receiver side (Returning false)");
            return -1;
        }
        String publishedSigScriptInHex = transactionPublishedBySender.getInputs().get(0).getScript();
        byte[] publishedSigScript = new BigInteger(publishedSigScriptInHex, 16).toByteArray();
        byte[] offChainSignature = bitcoinConnection.offChainSignTransaction(incompleteReceivedHandover, incompleteReceivedHandover.getKeyToRedeemP2SHOutput(0)); //This is handover transaction. It should only have one input.
        HandoverData adjustedReceivedHandoverData = bitcoinConnection.finishHandoverTemplateOnReceiverSide(incompleteReceivedHandover, offChainSignature, publishedSigScript);
        //sanity check
        if (!transactionPublishedBySender.getHash().equals(
                adjustedReceivedHandoverData.getTransactionReference().getBitcoinJTransaction().getHashAsString())) {
            //should never happen
            throw new RuntimeVerificationException("Something went wrong during the finishing of " +
                    "the handover workflow template on the receiver side.");
        }
        adjustedReceivedHandoverData.getTransactionReference().setTransactionFromCrawler(transactionPublishedBySender);
        wfStepWithHandover.setHandoverData(adjustedReceivedHandoverData);
        saveHandoverStorage();
        releaseLock();
        logger.debug("Finished finalizing handover template of workflow on receiver side (Returning true)");
        return wfStepWithHandover.getId();
    }

    /**
     * Republishes all owned handovers if they where not found online.
     */
    public synchronized void republishAllOwnedHandovers() throws InterruptedException, ExecutionException, IOException {
        logger.debug("Starting to republish all owned handovers.");
        for (WorkflowInstance workflowInstance : graphStorage.keySet()) {
            republishAllOwnedHandoversForInstance(workflowInstance);
        }
        logger.debug("Finished republishing all owned handovers.");
    }

    /**
     * Republishes all owned handovers of the given workflow if they where not found online.
     */
    public synchronized void republishAllOwnedHandoversForInstance(WorkflowInstance workflowInstance) throws InterruptedException, ExecutionException, IOException {
        logger.debug(String.format(
                "Starting to republish owned handovers for workflow instance (workflowInstance=%s)", workflowInstance));
        workflowExists(workflowInstance.getId());
        for (WorkflowGraphStep workflowGraphStep : runtimeVerificationUtils.graphToList(graphStorage.get(workflowInstance))) {
            if (workflowGraphStep.wasInitiatedByUs()) {
                bitcoinConnection.publishHandoverPoint(workflowGraphStep.getHandoverData(), true, maxBroadcastTries, waitMaxSecondsForBroadcast, depthUntilConfirmed);
            }
        }
        saveHandoverStorage();
        logger.debug("Finished republishing owned handovers for workflow instance.");
    }

    /**
     * Iterates over all WorkflowHandovers that contain a complete BitcoinJ transaction.
     * Performs an performAggressivePublishing (3x waiting and publishing iterations)
     * on all those transactions that are confirmed.
     * Throws a RuntimeVerificationException if the publishing does not succeed or throws an error.
     */
    public synchronized void waitForConfirmationOnAllPossibleHandovers() throws IOException {
        logger.debug("Starting wait for confirmation on all possible handovers for which BitcoinJ transactions are accessible.");
        for (WorkflowInstance workflowInstance : graphStorage.keySet()) {
            waitForConfirmationOnPossibleHandoversForInstance(workflowInstance);
        }
        logger.debug("Finished wait for confirmation on all possible handovers for which BitcoinJ transactions are accessible.");
    }

    /**
     * Iterates over all WorkflowHandovers of the given WorkflowInstance that contain a complete BitcoinJ transaction.
     * Performs an performAggressivePublishing (3x waiting and publishing iterations)
     * on all those transactions that are confirmed.
     * Throws a RuntimeVerificationException if the publishing does not succeed or throws an error.
     */
    public synchronized void waitForConfirmationOnPossibleHandoversForInstance(WorkflowInstance workflowInstance) throws IOException {
        logger.debug(String.format(
                "Starting to wait for confirmation on workflow instance (workflowInstance=%s) for which BitcoinJ transactions are accessible", workflowInstance));
        workflowExists(workflowInstance.getId());
        for (WorkflowGraphStep workflowHandover : runtimeVerificationUtils.graphToList(graphStorage.get(workflowInstance))) {
            if (workflowHandover.getHandoverData().containsBitcoinJTransaction() &&
                    !workflowHandover.getHandoverData().isTemplate()) {
                boolean worked;
                try {
                    worked = bitcoinConnection.performAggressivePublishing(
                            workflowHandover.getHandoverData(),
                            depthUntilConfirmed,
                            waitMaxMinutesForConfirmation,
                            true,
                            waitMaxSecondsForBroadcast);
                } catch (Exception e) {
                    throw new RuntimeVerificationException("Waiting for confirmation on WorkflowHandover " + workflowHandover + " of the WorkflowInstance " + workflowInstance + " failed.", e);
                }
                if (!worked) {
                    throw new RuntimeVerificationException("Waiting for confirmation on WorkflowHandover " + workflowHandover + " of the WorkflowInstance " + workflowInstance + " failed.");
                }
            }
        }
        saveHandoverStorage();
        logger.debug("Finished waiting for confirmation on workflow instance.");
    }

    /**
     * Fetches the given workflowStep from the given workflow instance.
     * Performs an performAggressivePublishing (3x waiting and publishing iterations)
     * on the contained BitcoinJ transaction.
     * Throws a RuntimeVerificationException if the publishing does not succeed or throws an error.
     */
    public synchronized void waitForConfirmationOnHandoverForInstance(WorkflowInstance workflowInstance, int wfStepWithHandoverId) throws IOException {
        logger.debug(String.format(
                "Starting to wait for confirmation on handover of workflow instance (workflowInstance=%s,wfStepWithHandoverId=%s).", workflowInstance, wfStepWithHandoverId));
        workflowExists(workflowInstance.getId());
        WorkflowGraphStep graphStepToWaitOn = getStepForId(graphStorage.get(workflowInstance), wfStepWithHandoverId);
        if (graphStepToWaitOn == null) {
            throw new RuntimeVerificationException("Step with id " + wfStepWithHandoverId + " does not exist in workflow " + workflowInstance.getId());
        }
        if (!graphStepToWaitOn.getHandoverData().containsBitcoinJTransaction() ||
                graphStepToWaitOn.getHandoverData().isTemplate()) {
            throw new RuntimeVerificationException("Step with id " + wfStepWithHandoverId + " from workflow " + workflowInstance.getId() +
                    " can not be waited on. There is not enough information available.");
        }
        boolean worked;
        try {
            worked = bitcoinConnection.performAggressivePublishing(
                    graphStepToWaitOn.getHandoverData(),
                    depthUntilConfirmed,
                    waitMaxMinutesForConfirmation,
                    true,
                    waitMaxSecondsForBroadcast);
        } catch (Exception e) {
            throw new RuntimeVerificationException("Waiting for confirmation on WorkflowHandover " + graphStepToWaitOn + " of the WorkflowInstance " + workflowInstance + " failed.", e);
        }
        if (!worked) {
            throw new RuntimeVerificationException("Waiting for confirmation on WorkflowHandover " + graphStepToWaitOn + " of the WorkflowInstance " + workflowInstance + " failed.");
        }
        saveHandoverStorage();
        logger.debug("Finished waiting for confirmation on handover of workflow instance.");
    }

    private void updateWorkflowDataWithOnlineInformation(WorkflowInstance workflowInstance) throws IOException {
        updateWorkflowDataWithOnlineInformation(workflowInstance, true);
    }

    /**
     * Updates the workflow data from the store of the given instance.
     * Only transaction which are not available.
     */
    private void updateWorkflowDataWithOnlineInformation(WorkflowInstance workflowInstance, boolean logRequest) throws IOException {
        logger.debug(String.format(
                "Starting to update workflow data with online information of workflow instance (workflowInstance=%s,logRequest=%s)", workflowInstance, logRequest));
        workflowExists(workflowInstance.getId());
        WorkflowGraphStep graphRoot = graphStorage.get(workflowInstance);
        WorkflowUpdater workflowUpdater = new WorkflowUpdater(blockChainCrawler, graphRoot, networkParameters, idProvider);
        WorkflowGraphStep updatedGraphRoot = workflowUpdater.updateWorkflowDataWithOnlineInformation(logRequest);
        if (workflowUpdater.dataWasUpdated()) {
            logger.debug("Graphdata was updated through the REST API.");
            graphStorage.put(workflowInstance, updatedGraphRoot);
        }
        saveHandoverStorage();
        logger.debug("Finished update workflow data with online information of workflow instance.");
    }

    /**
     * The given address should be part of a workflow. With this information the workflow data can be fetched.
     * Returns the root from this online fetched workflow.
     */
    private WorkflowGraphStep initWorkflowWithOnlineDataThroughPublicKeyOfIdentity(Address p2shAddress) throws IOException {
        WorkflowUpdater workflowUpdater = new WorkflowUpdater(blockChainCrawler, p2shAddress.toBase58(), networkParameters, idProvider);
        return workflowUpdater.updateWorkflowDataWithOnlineInformation();
    }

    /**
     * Together with the included data, the bitcoin public key of the provided identity
     * can be converted to a bitcoin p2sh address.
     */
    private Address getExposedP2SHAddress(Identity identityWithPublicKey, List<byte[]> dataToDocument) {
        ECKey publicKey = ECKey.fromPublicOnly(identityWithPublicKey.getBitcoinPublicKey());
        if (dataToDocument == null) {
            return bitcoinConnection.getP2SHAddressToKeyAndData(publicKey);
        } else {
            return bitcoinConnection.getP2SHAddressToKeyAndData(publicKey, dataToDocument.toArray(new byte[0][0]));
        }
    }

    private void workflowExists(short workflowId) {
        if (!workflowInstanceDataIsStored(workflowId)) {
            throw new RuntimeVerificationException("The given workflow does not exist in this store.");
        }
    }

    private void workflowNotExists(short workflowId) {
        if (workflowInstanceDataIsStored(workflowId)) {
            throw new RuntimeVerificationException("Another workflow with this id already exists.");
        }
    }

    private void workflowWasStartedBy(WorkflowGraphStep workflowDataRoot, Identity expectedOwner) {
        if (!workflowDataRoot.isStart()) {
            throw new RuntimeVerificationException("The workflow data to the given workflow is not complete. ");
        }
        if (!workflowDataRoot.getFrom().equals(expectedOwner)) {
            throw new RuntimeVerificationException("The given workflow is not owned by " + expectedOwner + ". ");
        }
    }

    /**
     * Returns the leaf of the graph which is related to to the given p2SHAddress.
     * Throws RuntimeVerificationException if nothing is found.
     * Assumes that the graph storage was updated.
     */
    private WorkflowGraphStep findRelatedLeafStepToPublicKeyOfIdentity(WorkflowGraphStep wfRoot, Address p2SHAddress) {
        List<WorkflowGraphStep> leafs = getLeafsOfWorkflow(wfRoot);
        String addressAsString = p2SHAddress.toBase58();
        for (WorkflowGraphStep graphStep : leafs) {
            if (graphStep.getHandoverData().getTransactionReference() != null) {
                for (int i = 0; i < graphStep.getHandoverData().getTransactionReference().getOutputSize(); i++) {
                    String addressInOutput = graphStep.getHandoverData().getTransactionReference().getOutputAddress(i, networkParameters);
                    if (addressInOutput != null && addressAsString.equals(addressInOutput)) {
                        return graphStep;
                    }
                }
            }
        }
        throw new RuntimeVerificationException("No leaf with the given address " + addressAsString + " as output was found.");
    }

    /**
     * Checks if the given graph has just one leaf node and asserts the owner of this leaf node.
     * Throws a RuntimeVerificationException if more than one leaf is found or if the leaf belongs to an
     * identity that is different to the given expectedOwner or the unknownCompanyIdentity.
     * Expects the graph's root as input.
     * Returns the single leaf node.
     */
    private WorkflowGraphStep workflowHasSingleLeafAndCurrentlyBelongsTo(WorkflowGraphStep workflowDataRoot, Identity expectedOwner) {
        WorkflowGraphStep latestWFStep = workflowHasSingleLeaf(workflowDataRoot, expectedOwner);
        workflowStepBelongsTo(latestWFStep, expectedOwner, false);
        return latestWFStep;
    }

    private WorkflowGraphStep workflowHasSingleLeaf(WorkflowGraphStep workflowDataRoot, Identity expectedOwner) {
        List<WorkflowGraphStep> leafsOfWorkflow = getLeafsOfWorkflow(workflowDataRoot);
        if (leafsOfWorkflow.size() != 1) {
            throw new RuntimeVerificationException("The given workflow has more than one leaf node.");
        }
        return leafsOfWorkflow.get(0);
    }

    /**
     * Returns the wfGraph leaf that is a template directed to us and originating from the given identity.
     * Throws a RuntimeVerificationException if nothing was found.
     */
    private WorkflowGraphStep getLeafTemplateBelongingToUsAndOriginatingFromSender(short wfId, Identity from) throws IOException {
        for (WorkflowGraphStep wfLeafFromUs : getLeafsThatBelongToUs(wfId, false)) {
            if (wfLeafFromUs.getHandoverData().isTemplate() && wfLeafFromUs.getFrom().equals(from)) {
                return wfLeafFromUs;
            }
        }
        throw new RuntimeException("No fitting leaf belonging to us and originating from " + from + " was found.");
    }

    private void isLeafOfWorkflow(WorkflowGraphStep workflowRoot, WorkflowGraphStep workflowStep) {
        if (!getLeafsOfWorkflow(workflowRoot).contains(workflowStep)) {
            throw new RuntimeVerificationException("The given workflow " + workflowStep + " is not a leaf or root " + workflowRoot);
        }
    }

    /**
     * Checks if the given step is owned by the given expectedOwner or the unknownCompanyIdentity.
     * Throws a RuntimeVerificationException if the check fails.
     */
    private void workflowStepBelongsTo(WorkflowGraphStep workflowStep, Identity expectedOwner, boolean relaxOwnerCheck) {
        if (relaxOwnerCheck) {
            if (!workflowStep.getTo().equals(Identity.getUnknownCompanyIdentity()) &&
                    !workflowStep.getTo().relaxedEquals(expectedOwner)) {
                throw new RuntimeVerificationException("The given workflow " + workflowStep + " is not yet owned by " + expectedOwner);
            }
            return;
        }
        if (!workflowStep.getTo().equals(Identity.getUnknownCompanyIdentity()) &&
                !workflowStep.getTo().equals(expectedOwner)) {
            throw new RuntimeVerificationException("The given workflow " + workflowStep + " is not yet owned by " + expectedOwner);
        }
    }

    /**
     * Checks if the given wfStep is still in a template state.
     * Throws a RuntimeVerificationException if one is found.
     */
    private void workflowStepIsNotInTemplateState(WorkflowGraphStep wfStep) {
        if (wfStep.getHandoverData().isTemplate()) {
            throw new RuntimeVerificationException("The latest step of the given workflow was still in a template state.");
        }
    }

    /**
     * Searches the graph for an END transaction.
     * Throws a RuntimeVerificationException if one is found.
     * Expects the graph's root as input.
     */
    private void workflowWasNotEnded(WorkflowGraphStep workflowDataRoot) {
        Queue<WorkflowGraphStep> uncheckedNodes = new LinkedList<>();
        uncheckedNodes.add(workflowDataRoot);
        while (!uncheckedNodes.isEmpty()) {
            WorkflowGraphStep currentStep = uncheckedNodes.poll();
            if (currentStep.isEnd()) {
                throw new RuntimeVerificationException("The given workflow data contained a END state.");
            }
            if (currentStep.getChildren() != null) {
                uncheckedNodes.addAll(currentStep.getChildren());
            }
        }
    }

    /**
     * Deletes a leaf of type INTERMEDIATE of the given workflow,
     * that is still in a template state, belongs to us and originates from the given sender.
     */
    public synchronized void deleteIntermediateLeafTemplateIfExistsOnReceiverSide(short workflowId, Identity from) throws IOException {
        logger.debug(String.format(
                "Deleting handover template of workflow on receiver side if exists (workflowId=%s, from=%s)",
                "" + workflowId, "" + from));
        checkIfLockIsOwned();
        //workflow must exist
        workflowExists(workflowId);
        WorkflowGraphStep wfStepWithHandover = getLeafTemplateBelongingToUsAndOriginatingFromSender(workflowId, from);
        if (!wfStepWithHandover.isIntermediate()) {
            logger.debug("Leaf was not of type INTERMEDIATE");
            return;
        }
        //remove connections to graph
        if (wfStepWithHandover.getHandoverData().isTemplate()) {
            //A handover only has one parent
            WorkflowGraphStep parent = wfStepWithHandover.getParents().get(0);
            for (int i = 0; i < parent.getChildren().size(); i++) {
                WorkflowGraphStep childOfParent = parent.getChildren().get(i);
                if (childOfParent.getTo().relaxedEquals(ownIdentityProvider.getOwnIdentity())) {
                    parent.getChildren().remove(i);
                    logger.debug("Found position to delete handover template at parent.");
                    break;
                }
            }
        }
        wfStepWithHandover.setParents(null);
        wfStepWithHandover.setChildren(null);
        saveHandoverStorage();
        logger.debug("Finished deleting handover template of workflow on receiver side if exists.");
    }

    /**
     * Searches the graph for an END transaction.
     * Returns true if one is found.
     */
    public synchronized boolean workflowWasEnded(short wfId) {
        workflowExists(wfId);
        List<WorkflowGraphStep> graphAsList = runtimeVerificationUtils.graphToList(graphStorage.get(new WorkflowInstance(wfId)));
        for (WorkflowGraphStep graphStep : graphAsList) {
            if (graphStep.isEnd()) {
                return true;
            }
        }
        return false;
    }


    /**
     * Returns all the leafs of the graph in a list.
     * Expects the graph's root as input.
     */
    private List<WorkflowGraphStep> getLeafsOfWorkflow(WorkflowGraphStep wfDataRoot) {
        List<WorkflowGraphStep> leafs = new ArrayList<>();
        for (WorkflowGraphStep graphStep : runtimeVerificationUtils.graphToList(wfDataRoot)) {
            if (graphStep.getChildren() == null || graphStep.getChildren().isEmpty()) {
                if (graphStep.isSplit()) {
                    for (int i = 0; i < graphStep.getHandoverData().getTransactionReference().getOutputSize() - 1; i++) { // do not forget OP_RETURN output of split
                        leafs.add(graphStep);
                    }
                } else {
                    leafs.add(graphStep);
                }
            }
        }
        leafs.addAll(getUnfinishedSplitsOfWorkflow(wfDataRoot));
        return leafs;
    }

    /**
     * Returns all splits with children but have an unclaimed output left.
     * Expects the graph's root as input.
     * NOTE: does not return leafs.
     */
    private List<WorkflowGraphStep> getUnfinishedSplitsOfWorkflow(WorkflowGraphStep wfDataRoot) {
        List<WorkflowGraphStep> unfinishedSplits = new ArrayList<>();
        for (WorkflowGraphStep graphStep : runtimeVerificationUtils.graphToList(wfDataRoot)) {
            if (graphStep.isSplit() &&
                    (graphStep.getChildren() != null && !graphStep.getChildren().isEmpty() &&
                            graphStep.getChildren().size() != graphStep.getHandoverData().getTransactionReference().getOutputSize())) {
                for (int i = 0; (graphStep.getChildren().size() + i) < graphStep.getHandoverData().getTransactionReference().getOutputSize() - 1; i++) { // do not forget OP_RETURN output of split
                    unfinishedSplits.add(graphStep);
                }
            }
        }
        return unfinishedSplits;
    }


    /**
     * Saves the HandoverStorage map as java object.
     */
    private void saveHandoverStorage() throws IOException {
        handoverStorageFile.createNewFile();
        FileOutputStream fOut = new FileOutputStream(handoverStorageFile);
        ObjectOutputStream objOut = new ObjectOutputStream(fOut);
        objOut.writeObject(graphStorage);
        objOut.close();
        fOut.close();
    }

    /**
     * Loads the HandoverStorage from the handoverStorageFile.
     * Assumes that the handoverStorageFile exists.
     * Returns null if something goes wrong.
     */
    private Map<WorkflowInstance, WorkflowGraphStep> loadHandoverStorage() {
        try {
            FileInputStream fIn = new FileInputStream(handoverStorageFile);
            ObjectInputStream objIn = new ObjectInputStream(fIn);
            Object obj = objIn.readObject();
            objIn.close();
            fIn.close();
            return (Map<WorkflowInstance, WorkflowGraphStep>) obj;
        } catch (Exception e) {
            logger.error("An exception occurred while loading the HandoverStorage", e);
            return null;
        }
    }

    public synchronized void setOwnIdentityProvider(OwnIdentityProvider ownIdentityProvider) {
        this.ownIdentityProvider = ownIdentityProvider;
        basicCryptographyManager = new BasicCryptographyManager(ownIdentityProvider.getOwnIdentity());
    }

    public synchronized OwnIdentityProvider getOwnIdentityProvider() {
        return ownIdentityProvider;
    }

    /**
     * Writes all stored wf information to the logger.
     * If includeTechnicalData is true, technical information from the stored transaction will also be appended.
     */
    public synchronized void printAllWorkflowInformation(boolean includeTechnicalData) {
        logger.debug(String.format(
                "Starting to print all stored workflowInstance information (includeTechnicalData=%s)", includeTechnicalData));
        for (WorkflowInstance workflowInstance : graphStorage.keySet()) {
            printWorkflowInstanceInformation(workflowInstance, includeTechnicalData);
        }
        logger.debug("Finished to print all stored workflowInstance information.");
    }

    /**
     * Writes the stored information from the given workflowId to the logger.
     * If includeTechnicalData is true, technical information from the stored transaction will also be appended.
     */
    public synchronized void printWorkflowInstanceInformation(WorkflowInstance workflowInstance, boolean includeTechnicalData) {
        logger.debug(String.format(
                "Starting to print workflowInstance information (workflowInstance=%s, includeTechnicalData=%s)",
                workflowInstance,
                includeTechnicalData));
        if (!graphStorage.containsKey(workflowInstance)) {
            logger.debug("Workflow instance is not contained in store.");
            return;
        }
        logger.info("Printing stored information about workflow instance " + workflowInstance.getId());
        List<WorkflowGraphStep> workflowData = runtimeVerificationUtils.graphToList(graphStorage.get(workflowInstance));
        StringBuffer wfDataAsString = new StringBuffer();
        wfDataAsString.append("\n");
        for (WorkflowGraphStep workflowHandover : workflowData) {
            String handOverAsString;
            if (includeTechnicalData) {
                handOverAsString = getWorkflowHandoverComplexString(workflowHandover);
            } else {
                handOverAsString = getWorkflowHandoverSimpleString(workflowHandover);
            }
            wfDataAsString.append(handOverAsString).append("\n");
        }
        logger.info(wfDataAsString.toString());
        logger.debug("Finished to print workflowInstance information.");
    }

    /**
     * Prints the advanced information of a workflow step.
     */
    private String getWorkflowHandoverComplexString(WorkflowGraphStep workflowHandover) {
        String msgFormat = "%s Sender:%s Template:%s TxInformatin:%s";
        String simpleString = getWorkflowHandoverSimpleString(workflowHandover);
        String sender = "" + workflowHandover.getHandoverData().isSender();
        String template = "" + workflowHandover.getHandoverData().isTemplate();
        String txInformation = null;
        if (workflowHandover.getHandoverData().containsBitcoinJTransaction()) {
            txInformation = workflowHandover.getHandoverData().getTransactionReference().getBitcoinJTransaction().getHashAsString();
        } else {
            if (workflowHandover.getHandoverData().getTransactionReference() != null) {
                txInformation = workflowHandover.getHandoverData().getTransactionReference().getTxHash();
            }
        }
        return String.format(msgFormat,
                simpleString,
                sender,
                template,
                txInformation);
    }

    /**
     * Prints the basic information of a workflow step.
     */
    private String getWorkflowHandoverSimpleString(WorkflowGraphStep workflowHandover) {
        String msgFormat = "%s : [%s -> %s] %s";
        String timeStamp = workflowHandover.getHandoverData()
                .getWorkflowHandoverData().getHandoverTimeStampPrettyString();
        String from = workflowHandover.getFrom().getCompanyName() + " isMe:" + workflowHandover.getFrom().isMe();
        String to = workflowHandover.getTo().getCompanyName() + " isMe:" + workflowHandover.getTo().isMe();
        String type = workflowHandover.getHandoverData().getWorkflowHandoverData().getWorkflowHandoverType().name();
        String simpleString = String.format(msgFormat, timeStamp, from, to, type);
        if (workflowHandover.getHandoverData().getWorkflowHandoverData().getWorkflowHandoverType() == WorkflowHandoverType.INTERMEDIATE) {
            String taskId = "" + workflowHandover.getHandoverData().getWorkflowHandoverData().getIdOfNextTask();
            simpleString += " Task:" + taskId;
        }
        return simpleString;
    }

    /**
     * Returns all ids of leafs related to the workflow instance that belong to us.
     */
    private List<WorkflowGraphStep> getLeafsThatBelongToUs(short workflowInstanceId, boolean updateFirst) throws IOException {
        List<WorkflowGraphStep> leafsBelongingToUs = new ArrayList<>();
        workflowExists(workflowInstanceId);
        if (updateFirst) {
            updateWorkflowDataWithOnlineInformation(new WorkflowInstance(workflowInstanceId));
        }
        WorkflowGraphStep wfRoot = graphStorage.get(new WorkflowInstance(workflowInstanceId));
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        for (WorkflowGraphStep leaf : getLeafsOfWorkflow(wfRoot)) {
            if (leaf.getTo().equals(ownIdentity)) {
                leafsBelongingToUs.add(leaf);
            }
        }
        return leafsBelongingToUs;
    }

    public synchronized List<Integer> getLeafIdsThatBelongToUs(short workflowInstanceId, boolean updateFirst) throws IOException {
        List<Integer> leafIds = new ArrayList<>();
        checkIfLockIsOwned();
        for (WorkflowGraphStep leaf : getLeafsThatBelongToUs(workflowInstanceId, updateFirst)) {
            leafIds.add(leaf.getId());
        }
        releaseLock();
        return leafIds;
    }

    /**
     * Performs the necessary check if the given workflow step is an incoming path of a workflow id.
     */
    public synchronized boolean isIncomingPathOfAndJoin(short wfId, int wfStepId, List<Byte> idsToWaitOn) {
        WorkflowGraphStep ourLeaf = getStepForId(graphStorage.get(new WorkflowInstance(wfId)), wfStepId);
        return ourLeaf.isIntermediate() &&
                !ourLeaf.getHandoverData().isTemplate() &&
                idsToWaitOn.contains(ourLeaf.getHandoverData().getWorkflowHandoverData().getIdOfNextTask());
    }

    /**
     * Returns the leaf related to the workflow instance that originate from us and belong to the given identity.
     * Throws a RuntimeVerificationException if none is found.
     */
    private WorkflowGraphStep getLeafOriginatingFromUsAndDirectedAtReceiver(short workflowInstanceId, Identity to) throws IOException {
        workflowExists(workflowInstanceId);
        WorkflowGraphStep wfRoot = graphStorage.get(new WorkflowInstance(workflowInstanceId));
        Identity ownIdentity = ownIdentityProvider.getOwnIdentity();
        for (WorkflowGraphStep leaf : getLeafsOfWorkflow(wfRoot)) {
            if (leaf.getFrom().equals(ownIdentity) && leaf.getTo().equals(to)) {
                return leaf;
            }
        }
        throw new RuntimeVerificationException("No leaf originating from us and directed at " + to + " was found.");
    }

    /**
     * Returns the bitcoin public key to the given wfStep if it is available.
     */
    public synchronized byte[] getBitcoinPublicKeyToWFStepOutput(short wfId, int wfStepId, int outputIndex) {
        WorkflowGraphStep wfStep = getStepForId(graphStorage.get(new WorkflowInstance(wfId)), wfStepId);
        return wfStep.getHandoverData()
                .getKeyToRedeemP2SHOutput(outputIndex).getPubKey();
    }

    /**
     * Returns the data included in the given wfStep if it is available.
     */
    public synchronized List<byte[]> getDataIncludedInWFStepOutput(short wfId, int wfStepId, int outputIndex) {
        WorkflowGraphStep wfStep = getStepForId(graphStorage.get(new WorkflowInstance(wfId)), wfStepId);
        return wfStep.getHandoverData()
                .getDataToRedeemP2SHOutput(outputIndex);
    }

    private void addChildStep(WorkflowGraphStep fromStep, WorkflowGraphStep child) {
        if (fromStep.getChildren() == null) {
            fromStep.setChildren(new ArrayList<>());
        }
        fromStep.getChildren().add(child);
    }

    /**
     * Returns a bitcoin address which can be used to pay money to the underlying wallet.
     */
    public String getAddressToPayMoneyTo() {
        return bitcoinConnection.getNewReceivingAddressInBase58();
    }

    public NetworkParameters getNetworkParameters() {
        return networkParameters;
    }

    private WorkflowGraphStep getStepForId(WorkflowGraphStep graphRoot, int id) {
        if (graphRoot.getId() == id) {
            return graphRoot;
        }
        for (WorkflowGraphStep workflowGraphStep : runtimeVerificationUtils.graphToList(graphRoot)) {
            if (workflowGraphStep.getId() == id) {
                return workflowGraphStep;
            }
        }
        return null;
    }

    public synchronized boolean runPredicateOnWorkflowStep(short workflowId, int wfStepId, Predicate<WorkflowGraphStep> predicate) {
        WorkflowGraphStep wfStep = getStepForId(graphStorage.get(new WorkflowInstance(workflowId)), wfStepId);
        if (wfStep != null) {
            return predicate.test(wfStep);
        }
        return false;
    }

}
