package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.TransactionStructureVerifier;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.WorkflowDataBlockConverter;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.HandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.TransactionReference;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.BlockChainCrawler;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedTransaction;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.IdProvider;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.WorkflowGraphStep;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script;

import java.io.IOException;
import java.util.*;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

/**
 * Provides logic to update locally stored workflow data with information collected from online APIs.
 */
public class WorkflowUpdater {

    private IdProvider idProvider;
    private BlockChainCrawler blockChainCrawler;
    private WorkflowGraphStep graphRoot;
    private boolean dataWasUpdated;
    private NetworkParameters networkParameters;
    private Map<Integer, WorkflowGraphStep> templateLeafs;
    private boolean shouldLogRequest;

    public WorkflowUpdater(BlockChainCrawler blockChainCrawler, String address, NetworkParameters networkParameters, IdProvider idProvider) throws IOException {
        this.blockChainCrawler = blockChainCrawler;
        this.networkParameters = networkParameters;
        this.idProvider = idProvider;
        RuntimeVerificationUtils utils = new RuntimeVerificationUtils();
        utils.notNull(blockChainCrawler);
        utils.notNull(networkParameters);
        List<String> txHashesToAddress = blockChainCrawler.getTxHashesOfAddress(address);
        if (txHashesToAddress.isEmpty()) {
            throw new RuntimeVerificationException("No transaction was found for the given address " + address);
        }
        graphRoot = findWorkflowHandoverOnline(txHashesToAddress.get(0));
        templateLeafs = new HashMap<>();
    }

    public WorkflowUpdater(BlockChainCrawler blockChainCrawler, WorkflowGraphStep graphRoot, NetworkParameters networkParameters, IdProvider idProvider) {
        this.blockChainCrawler = blockChainCrawler;
        this.graphRoot = graphRoot;
        this.networkParameters = networkParameters;
        this.idProvider = idProvider;
        RuntimeVerificationUtils utils = new RuntimeVerificationUtils();
        utils.notNull(blockChainCrawler);
        utils.notNull(networkParameters);
        utils.notNull(graphRoot);
        templateLeafs = new HashMap<>();
    }

    public WorkflowGraphStep updateWorkflowDataWithOnlineInformation() throws IOException {
        return updateWorkflowDataWithOnlineInformation(true);
    }

    /**
     * Analyses the structure of the provided wfData and returns an updated list containing updated information.
     */
    public WorkflowGraphStep updateWorkflowDataWithOnlineInformation(boolean logRequest) throws IOException {
        this.shouldLogRequest = logRequest;
        blockChainCrawler.activateCache();
        List<WorkflowGraphStep> oldWFElements = graphToList();
        findTemplateLeafs(oldWFElements);
        Identity unknownIdentity = Identity.getUnknownCompanyIdentity();
        dataWasUpdated = false;
        if (!graphRoot.isStart()) {
            graphRoot = findStartOfWF();
            dataWasUpdated = true;
        }
        Queue<WorkflowGraphStep> uncheckedNodes = new LinkedList<>();
        uncheckedNodes.add(graphRoot);
        while (!uncheckedNodes.isEmpty()) {
            WorkflowGraphStep currentWF = uncheckedNodes.poll();
            //parent nodes should be in graph because it is a breadth first search mode
            if (!currentWF.isStart() && parentReferencesNeedToBeUpdated(currentWF)) {
                updateParentReferencesFromStore(currentWF);
                for (WorkflowGraphStep parent : currentWF.getParents()) {
                    propagateIdentityInformationBetweenGraphSteps(parent, currentWF, unknownIdentity);
                }
            }
            if (currentWF.isEnd()) {
                currentWF.setChildren(null);
                continue;
            }
            List<WorkflowGraphStep> children;
            if (childrenReferencesNeedToBeUpdated(currentWF)) {
                children = findFollowingWfSteps(currentWF);
                currentWF.setChildren(children);
                for (WorkflowGraphStep child : currentWF.getChildren()) {
                    //sanity check one child is a start
                    if (child.isStart()) {
                        throw new RuntimeVerificationException("Sanity check failed. Encountered a wf start before a wf end was found.");
                    }
                    //propagate identity between currentStep and childStep
                    propagateIdentityInformationBetweenGraphSteps(currentWF, child, unknownIdentity);
                }
            } else {
                children = currentWF.getChildren();
            }
            if (children == null) {
                children = new ArrayList<>();
            }
            uncheckedNodes.addAll(children);
        }

        //add template leafs if necessary
        addTemplateLeafsToNewGraph();

        //Assert that all old elements are included in the new list
        allOldElementsExistInNewGraph(oldWFElements);
        blockChainCrawler.deactivateCache();
        return graphRoot;
    }

    /**
     * Add template leafs if necessary to graph.
     */
    private void addTemplateLeafsToNewGraph() {
        List<WorkflowGraphStep> newWFSteps = graphToList();
        for (WorkflowGraphStep templateLeaf : templateLeafs.values()) {
            List<WorkflowGraphStep> newParents = new ArrayList<>();
            for (WorkflowGraphStep oldParent : templateLeaf.getParents()) {
                //find the corresponding parent element and attach the templateLeaf.
                boolean found = false;
                for (WorkflowGraphStep newWFStep : newWFSteps) {
                    if (newWFStep.getHandoverData().getTransactionReference().getTxHash().equals(
                            oldParent.getHandoverData().getTransactionReference().getTxHash())) {
                        if (newWFStep.getChildren() == null) {
                            newWFStep.setChildren(new ArrayList<>());
                        }
                        newParents.add(newWFStep);
                        newWFStep.getChildren().add(templateLeaf);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new RuntimeVerificationException("Sanity check of addTemplateLeafs failed. Unable to find a parent reference for task " + templateLeaf.getHandoverData().getWorkflowHandoverData().getIdOfNextTask());
                }
            }
            templateLeaf.setParents(newParents);
            //Note: the id of the leaf must not be updated.
            //The idProvider is set above the highest template id
        }
    }

    /**
     * Finds all leafs that are in a template state and saves them for later usage.
     */
    private void findTemplateLeafs(List<WorkflowGraphStep> oldWFElements) {
        for (WorkflowGraphStep oldElement : oldWFElements) {
            if (oldElement.getHandoverData() != null &&
                    oldElement.getHandoverData().isTemplate()) {
                if (oldElement.getChildren() != null) {
                    throw new RuntimeVerificationException("Sanity check in 'findTemplateLeafs' failed. " +
                            "A template step had child references. This should never happen.");
                }
                while (idProvider.peekId() <= oldElement.getId()) {
                    idProvider.getNextId();
                }
                templateLeafs.put(oldElement.getId(), oldElement);
            }
        }
    }

    /**
     * Update the parent references of the given WorkflowGraphStep.
     * All required information should already be contained in the graph because of BFS.
     */
    private void updateParentReferencesFromStore(WorkflowGraphStep graphStep) {
        List<WorkflowGraphStep> graphAsList = graphToList();
        List<WorkflowGraphStep> parents = new ArrayList<>();
        for (int i = 0; i < graphStep.getHandoverData().getTransactionReference().getInputSize(); i++) {
            String parentTxHash = graphStep.getHandoverData().getTransactionReference().getTxHashOfPreviousTransactionOfInput(i);
            boolean found = false;
            for (WorkflowGraphStep storedStep : graphAsList) {
                if (parentTxHash.equals(storedStep.getHandoverData().getTransactionReference().getTxHash())) {
                    parents.add(storedStep);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeVerificationException("Sanity check failed: Parent tx was not already stored in the graph.");
            }
        }
        graphStep.setParents(parents);
        dataWasUpdated = true;
    }

    private boolean parentReferencesNeedToBeUpdated(WorkflowGraphStep graphStep) {
        if (graphStep.getParents() == null) {
            return true;
        }
        if (graphStep.getHandoverData().getTransactionReference().getInputSize() != graphStep.getParents().size()) {
            return true;
        }
        for (int i = 0; i < graphStep.getHandoverData().getTransactionReference().getInputSize(); i++) {
            String parentTxHash = graphStep.getHandoverData().getTransactionReference().getTxHashOfPreviousTransactionOfInput(i);
            if (parentTxHash == null) {
                return true;
            }
            boolean found = false;
            for (WorkflowGraphStep storedStep : graphStep.getParents()) {
                if (parentTxHash.equals(storedStep.getHandoverData().getTransactionReference().getTxHash())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        return false;
    }

    private boolean childrenReferencesNeedToBeUpdated(WorkflowGraphStep graphStep) {
        if (graphStep.getHandoverData().isTemplate()) {
            return false;
        }
        if (graphStep.getChildren() == null) {
            return true;
        }
        if (graphStep
                .getHandoverData().getTransactionReference().getIndicesOfTokenOutputs().size() != graphStep.getChildren().size()) {
            return true;
        }
        for (int i = 0; i < graphStep.getHandoverData().getTransactionReference().getIndicesOfTokenOutputs().size(); i++) {
            int index = graphStep.getHandoverData().getTransactionReference().getIndicesOfTokenOutputs().get(i);
            String childTxHash = graphStep.getHandoverData().getTransactionReference().getTxHashOfFollowingTransactionOfOutput(index);
            if (childTxHash == null) {
                return true;
            }
            boolean found = false;
            for (WorkflowGraphStep storedStep : graphStep.getChildren()) {
                if (childTxHash.equals(storedStep.getHandoverData().getTransactionReference().getTxHash())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return true;
            }
        }
        return false;
    }

    private void propagateIdentityInformationBetweenGraphSteps(WorkflowGraphStep oldStep, WorkflowGraphStep newStep, Identity unknownIdentity) {
        if (!identityIsUnknown(oldStep.getTo(), unknownIdentity) &&
                identityIsUnknown(newStep.getFrom(), unknownIdentity)) {
            newStep.setFrom(oldStep.getTo());
            dataWasUpdated = true;
        } else if (!identityIsUnknown(newStep.getFrom(), unknownIdentity) &&
                identityIsUnknown(oldStep.getTo(), unknownIdentity)) {
            oldStep.setTo(newStep.getFrom());
            dataWasUpdated = true;
        }
    }

    private boolean identityIsUnknown(Identity identity, Identity unknownIdentity) {
        if (identity == null) {
            return true;
        }
        return identity.equals(unknownIdentity);
    }

    /**
     * Assert that all old elements are included in the new graph
     */
    private void allOldElementsExistInNewGraph(List<WorkflowGraphStep> oldElements) {
        List<WorkflowGraphStep> newWFSteps = graphToList();
        for (WorkflowGraphStep oldExistingStep : oldElements) {
            existsInList(newWFSteps, oldExistingStep);
        }
    }

    private void existsInList(List<WorkflowGraphStep> newList, WorkflowGraphStep oldElement) {
        boolean found = false;
        if (oldElement.getHandoverData().isTemplate()) {
            return;
        }
        for (WorkflowGraphStep newWFStep : newList) {
            if (!newWFStep.getHandoverData().isTemplate()) {
                if (oldElement.getHandoverData().getTransactionReference().getTxHash().equals(
                        newWFStep.getHandoverData().getTransactionReference().getTxHash())) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new RuntimeVerificationException("The old existing WorkflowGraphStep " + oldElement + " was not contained in the " +
                    "updated list.");
        }
    }

    /**
     * Called if the first wf handover in the storage is not the start of the WF.
     */
    private WorkflowGraphStep findStartOfWF() throws IOException {
        WorkflowGraphStep searchPoint = findExistingSearchWFPointInStorage();
        if (searchPoint.getHandoverData().getTransactionReference().bitcoinJTransactionShouldBeUpdatedWithOnlineData()) {
            //update the searchPoint if necessary
            updateSpecificWorkflowHandover(searchPoint);
        }
        while (!searchPoint.isStart()) {
            searchPoint = findPreviousWFStep(searchPoint);
            if (searchPoint.isEnd()) {
                throw new RuntimeVerificationException("Sanity check failed. Encountered a wf end before a wf start was found.");
            }
        }
        return searchPoint;
    }

    /**
     * Search for an existing WF handover, split or join point in the handoverStorage data to start the graph search with.
     * SPECIAL CASE: If only one element is contained in the graph and that element is of type END, this element is also
     * returned.
     * If nothing was found, an exception is thrown.
     */
    private WorkflowGraphStep findExistingSearchWFPointInStorage() {
        List<WorkflowGraphStep> graphAsList = graphToList();
        //first try to find an instance that does not need updating
        for (WorkflowGraphStep currentStep : graphAsList) {
            boolean correctType = currentStep.isIntermediate() || currentStep.isJoin() || currentStep.isSplit();
            if (correctType &&
                    !currentStep
                            .getHandoverData()
                            .getTransactionReference()
                            .bitcoinJTransactionShouldBeUpdatedWithOnlineData()) {
                return currentStep;
            }
        }

        //second just try to find and intermediate instance
        for (WorkflowGraphStep currentStep : graphAsList) {
            boolean correctType = currentStep.isIntermediate() || currentStep.isJoin() || currentStep.isSplit();
            if (correctType) {
                return currentStep;
            }
        }

        //cover special case: only contained instance is the end point
        if (graphAsList.size() == 1 && graphAsList.get(0).isEnd()) {
            return graphAsList.get(0);
        }

        throw new RuntimeVerificationException("No usable element was found in the storage to start the search with.");
    }

    /**
     * Collect all elements in the graph into a list
     */
    private List<WorkflowGraphStep> graphToList() {
        return new RuntimeVerificationUtils().graphToList(graphRoot);
    }

    /**
     * Returns the wf step previous to the given one.
     * First searches in the local storage, then searches in the online api.
     * Assumes that the given searchPoint is an intermediate, split or join handover.
     */
    private WorkflowGraphStep findPreviousWFStep(WorkflowGraphStep searchPoint) throws IOException {
        if (searchPoint
                .getHandoverData()
                .getTransactionReference()
                .getTxHashOfPreviousTransactionOfInput(0) == null) { // in the three types intermediate, split or join there is always a token placed at input[0]
            updateSpecificWorkflowHandover(searchPoint);
        }
        String previousTxHash = searchPoint
                .getHandoverData()
                .getTransactionReference()
                .getTxHashOfPreviousTransactionOfInput(0);
        //check if previous handover is contained in store
        WorkflowGraphStep storedWfStep = findHandoverInWFDataStorage(previousTxHash);
        if (storedWfStep != null) {
            return storedWfStep;
        }
        //fetch the transaction data from the online api instead
        return findWorkflowHandoverOnline(previousTxHash);
    }

    /**
     * Returns the following wfSteps to the given wf step or an empty list if there exist none
     * First searches in the local storage, then searches the handover in the online api.
     * Assumes that the given WorkflowHandover is an intermediate, split and join handover.
     */
    private List<WorkflowGraphStep> findFollowingWfSteps(WorkflowGraphStep currentStep) throws IOException {
        updateSpecificWorkflowHandover(currentStep);
        List<WorkflowGraphStep> children = new ArrayList<>();
        for (int i = 0; i < currentStep.getHandoverData().getTransactionReference().getIndicesOfTokenOutputs().size(); i++) {
            int index = currentStep.getHandoverData().getTransactionReference().getIndicesOfTokenOutputs().get(i);
            String followingHandoverTxHash = currentStep.getHandoverData().getTransactionReference().getTxHashOfFollowingTransactionOfOutput(index);
            if (followingHandoverTxHash != null) {
                //Try to find the information locally
                WorkflowGraphStep storedHandover = findHandoverInWFDataStorage(followingHandoverTxHash);
                if (storedHandover != null) {
                    children.add(storedHandover);
                } else {
                    WorkflowGraphStep onlineHandover = findWorkflowHandoverOnline(followingHandoverTxHash);
                    if (onlineHandover != null) {
                        children.add(onlineHandover);
                    }
                }
                dataWasUpdated = true;
            }
        }
        return children;
    }

    /**
     * Returns the handover stored in the graph to the given txHash.
     * Returns null if no step with the given txHash is not stored.
     */
    private WorkflowGraphStep findHandoverInWFDataStorage(String txHash) {
        List<WorkflowGraphStep> workflowGraphSteps = graphToList();
        for (WorkflowGraphStep graphStep : workflowGraphSteps) {
            if (graphStep.getHandoverData().getTransactionReference().getTxHash().equals(txHash)) {
                return graphStep;
            }
        }
        return null;
    }

    /**
     * Returns true if data was updated during the last run
     */
    public boolean dataWasUpdated() {
        return dataWasUpdated;
    }

    /**
     * Adds the parsedTransaction data from the online API to the given workflowHandover.
     * Assumes that the workflowHandover exists online.
     */
    private void updateSpecificWorkflowHandover(WorkflowGraphStep workflowHandover) throws IOException {
        ParsedTransaction transactionUpdate = blockChainCrawler.getTransactionInformation(workflowHandover.getHandoverData().getTransactionReference().getTxHash(), false, shouldLogRequest);
        workflowHandover.getHandoverData().getTransactionReference().setTransactionFromCrawler(transactionUpdate);
    }

    /**
     * Returns the online available transaction data to the given txHash or null if none was found.
     */
    private WorkflowGraphStep findWorkflowHandoverOnline(String txHash) {
        if (txHash == null) {
            return null;
        }
        //fetch the transaction data from the online api instead
        ParsedTransaction parsedTransaction;
        try {
            parsedTransaction = blockChainCrawler.getTransactionInformation(txHash, false, shouldLogRequest);
        } catch (Exception e) {
            return null;
        }
        //Token output is always at position 0, except of an wf split
        TransactionReference transactionReference = new TransactionReference(parsedTransaction, Arrays.asList(0));
        //Check that we still got some kind of wf transaction
        TransactionStructureVerifier verifier = new TransactionStructureVerifier(transactionReference);
        if (verifier.isWFSplitTransaction()) {
            List<Integer> tokenOutputIndexList = new ArrayList<>();
            for (int i = 1; i < parsedTransaction.getOutputs().size(); i++) {
                tokenOutputIndexList.add((i - 1));
            }
            transactionReference = new TransactionReference(parsedTransaction, tokenOutputIndexList);
        }

        //Collect wf information
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(
                getOpReturnOutputPayload(transactionReference.getOutputScripts()));
        HandoverData handoverData = new HandoverData(
                workflowDataBlockConverter.getWorkflowHandoverData(),
                transactionReference,
                null, null, false, false, networkParameters);
        return new WorkflowGraphStep(Identity.getUnknownCompanyIdentity(),
                Identity.getUnknownCompanyIdentity(),
                handoverData,
                null,
                null,
                idProvider.getNextId());
    }

    /**
     * Returns the first found instance of type OP_RETURN or null.
     */
    private byte[] getOpReturnOutputPayload(List<byte[]> outputScriptList) {
        for (byte[] transactionOutputScript : outputScriptList) {
            Script dataScript = new Script(transactionOutputScript);
            if (dataScript.getChunks().size() != 2) {
                continue;
            }
            if (dataScript.getChunks().get(0).opcode != OP_RETURN) {
                continue;
            }
            return dataScript.getChunks().get(1).data;
        }
        return null;
    }
}
