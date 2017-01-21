package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.TransactionReference;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverType;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import org.bitcoinj.script.Script;

import java.util.List;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

/**
 * Verify a transaction for the three common transaction types of the handover framework
 */
public class TransactionStructureVerifier {

    private TransactionReference transaction;
    private WorkflowHandoverType workflowHandoverType;

    /**
     * Verifies the transaction on object creation.
     * Throws a RuntimeVerificationException if the transaction structure does not fit any of the three common transaction types.
     */
    public TransactionStructureVerifier(TransactionReference transaction) {
        this.transaction = transaction;
        verify();
    }

    /**
     * Verifies the transaction.
     * Throws a RuntimeVerificationException if the transaction does not fit any of the three common transaction types.
     */
    private void verify() {
        if (checkForIntermediateTransaction()) {
            workflowHandoverType = WorkflowHandoverType.INTERMEDIATE;
            return;
        }
        if (checkForStartTransaction()) {
            workflowHandoverType = WorkflowHandoverType.START;
            return;
        }
        if (checkForEndTransaction()) {
            workflowHandoverType = WorkflowHandoverType.END;
            return;
        }
        if (checkForSplitTransaction()) {
            workflowHandoverType = WorkflowHandoverType.SPLIT;
            return;
        }
        if (checkForJoinTransaction()) {
            workflowHandoverType = WorkflowHandoverType.JOIN;
            return;
        }
        throw new RuntimeVerificationException("The given transaction " + transaction + " did not fit a common transaction type.");
    }

    /**
     * Returns true if the given transaction has the structure of a WF split transaction
     */
    private boolean checkForSplitTransaction() {
        //Must have exactly one input.
        if (transaction.getInputSize() != 1) {
            return false;
        }
        //Must originate from P2SH, can on this level only be checked if the transaction was initiated by this instance.
        if (transaction.containsBitcoinJTransaction()) {
            if (transaction.getBitcoinJTransaction().getInputs().get(0).getConnectedOutput() != null
                    && !transaction.getBitcoinJTransaction().getInputs().get(0).getConnectedOutput().getScriptPubKey().isPayToScriptHash()) {
                return false;
            }
        }
        //Must have at least three outputs
        if (transaction.getOutputSize() < 3) {
            return false;
        }
        //All but the last must have a P2SH scripts
        for (int i = 0; i < transaction.getOutputSize() - 1; i++) {
            if (!new Script(transaction.getOutputScript(i)).isPayToScriptHash()) {
                return false;
            }
        }
        //Last output must be of type OP_RETURN
        Script dataScript = new Script(transaction.getOutputScript(transaction.getOutputSize() - 1));
        if (dataScript.getChunks().size() != 2) {
            return false;
        }
        if (dataScript.getChunks().get(0).opcode != OP_RETURN) {
            return false;
        }
        //Data block must be of type WorkflowHandoverType.SPLIT
        byte[] dataBlock = dataScript.getChunks().get(1).data;
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(dataBlock);
        WorkflowHandoverType type = workflowDataBlockConverter.getWorkflowHandoverData().getWorkflowHandoverType();
        if (type != WorkflowHandoverType.SPLIT) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the given transaction has the structure of a WF join transaction
     */
    private boolean checkForJoinTransaction() {
        //Must have at least two inputs
        if (transaction.getInputSize() < 2) {
            return false;
        }
        //All inputs must originate from P2SH, can on this level only be checked if the transaction was initiated by this instance.
        if (transaction.containsBitcoinJTransaction()) {
            for (int i = 0; i < transaction.getBitcoinJTransaction().getInputs().size(); i++) {
                if (transaction.getBitcoinJTransaction().getInputs().get(i).getConnectedOutput() != null
                        && !transaction.getBitcoinJTransaction().getInputs().get(i).getConnectedOutput().getScriptPubKey().isPayToScriptHash()) {
                    return false;
                }
            }
        }

        //Must have exactly two outputs.
        if (transaction.getOutputSize() != 2) {
            return false;
        }
        //First output must be of type P2SH
        if (!new Script(transaction.getOutputScript(0)).isPayToScriptHash()) {
            return false;
        }
        //Second output must be of type OP_RETURN
        Script dataScript = new Script(transaction.getOutputScript(1));
        if (dataScript.getChunks().size() != 2) {
            return false;
        }
        if (dataScript.getChunks().get(0).opcode != OP_RETURN) {
            return false;
        }
        //Data block must be of type WorkflowHandoverType.Join
        byte[] dataBlock = dataScript.getChunks().get(1).data;
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(dataBlock);
        WorkflowHandoverType type = workflowDataBlockConverter.getWorkflowHandoverData().getWorkflowHandoverType();
        if (type != WorkflowHandoverType.JOIN) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the given transaction has the structure of a WF start transaction
     */
    private boolean checkForStartTransaction() {
        //Can have an arbitrary amount of inputs
        //Type of input does not matter

        //Must have at most three outputs, one for token, one for OP_RETURN, and one for change.
        if (transaction.getOutputSize() > 3) {
            return false;
        }
        //First output must be of type P2SH
        if (!new Script(transaction.getOutputScript(0)).isPayToScriptHash()) {
            return false;
        }
        //Second output must be of type OP_RETURN
        Script dataScript = new Script(transaction.getOutputScript(1));
        if (dataScript.getChunks().size() != 2) {
            return false;
        }
        if (dataScript.getChunks().get(0).opcode != OP_RETURN) {
            return false;
        }
        //Data block must be of type WorkflowHandoverType.START
        byte[] dataBlock = dataScript.getChunks().get(1).data;
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(dataBlock);
        WorkflowHandoverType type = workflowDataBlockConverter.getWorkflowHandoverData().getWorkflowHandoverType();
        if (type != WorkflowHandoverType.START) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the given transaction has the structure of a WF end transaction
     */
    private boolean checkForEndTransaction() {
        //Must have exactly one input.
        if (transaction.getInputSize() != 1) {
            return false;
        }
        //Must originate from P2SH, can on this level only be checked if the transaction was initiated by this instance.
        if (transaction.containsBitcoinJTransaction()) {
            if (transaction.getBitcoinJTransaction().getInputs().get(0).getConnectedOutput() != null
                    && !transaction.getBitcoinJTransaction().getInputs().get(0).getConnectedOutput().getScriptPubKey().isPayToScriptHash()) {
                return false;
            }
        }
        //Must have exactly two outputs.
        if (transaction.getOutputSize() != 2) {
            return false;
        }
        //One output must be of type OP_RETURN
        byte[] opReturnOutputScript = findOpReturnOutput(transaction.getOutputScripts());
        if (opReturnOutputScript == null) {
            return false;
        }
        Script dataScriptOfOpReturn = new Script(opReturnOutputScript);
        //Data block must be of type WorkflowHandoverType.END
        byte[] dataBlock = dataScriptOfOpReturn.getChunks().get(1).data;
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(dataBlock);
        WorkflowHandoverType type = workflowDataBlockConverter.getWorkflowHandoverData().getWorkflowHandoverType();
        if (type != WorkflowHandoverType.END) {
            return false;
        }
        return true;
    }

    /**
     * Returns the first found instance of type OP_RETURN or null.
     */
    private byte[] findOpReturnOutput(List<byte[]> outputScriptList) {
        for (byte[] transactionOutputScript : outputScriptList) {
            Script dataScript = new Script(transactionOutputScript);
            if (dataScript.getChunks().size() != 2) {
                continue;
            }
            if (dataScript.getChunks().get(0).opcode != OP_RETURN) {
                continue;
            }
            return transactionOutputScript;
        }
        return null;
    }

    /**
     * Returns true if the given transaction has the structure of a WF intermediate transaction
     */
    private boolean checkForIntermediateTransaction() {
        //Must have exactly one input.
        if (transaction.getInputSize() != 1) {
            return false;
        }
        //Must originate from P2SH, can on this level only be checked if the transaction was initiated by this instance.
        if (transaction.containsBitcoinJTransaction()) {
            if (transaction.getBitcoinJTransaction().getInputs().get(0).getConnectedOutput() != null
                    && !transaction.getBitcoinJTransaction().getInputs().get(0).getConnectedOutput().getScriptPubKey().isPayToScriptHash()) {
                return false;
            }
        }
        //Must have exactly two outputs.
        if (transaction.getOutputSize() != 2) {
            return false;
        }
        //First output must be of type P2SH
        if (!new Script(transaction.getOutputScript(0)).isPayToScriptHash()) {
            return false;
        }
        //Second output must be of type OP_RETURN
        Script dataScript = new Script(transaction.getOutputScript(1));
        if (dataScript.getChunks().size() != 2) {
            return false;
        }
        if (dataScript.getChunks().get(0).opcode != OP_RETURN) {
            return false;
        }
        //Data block must be of type WorkflowHandoverType.INTERMEDIATE
        byte[] dataBlock = dataScript.getChunks().get(1).data;
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(dataBlock);
        WorkflowHandoverType type = workflowDataBlockConverter.getWorkflowHandoverData().getWorkflowHandoverType();
        if (type != WorkflowHandoverType.INTERMEDIATE) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if the transaction is a WF start transaction
     */
    public boolean isWFStartTransaction() {
        return transactionTypeCheck(WorkflowHandoverType.START);
    }


    /**
     * Returns true if the transaction is a WF start transaction
     */
    public boolean isWFEndTransaction() {
        return transactionTypeCheck(WorkflowHandoverType.END);
    }

    /**
     * Returns true if the transaction is a WF start transaction
     */
    public boolean isWFHandoverTransaction() {
        return transactionTypeCheck(WorkflowHandoverType.INTERMEDIATE);
    }

    public boolean isWFSplitTransaction() {
        return transactionTypeCheck(WorkflowHandoverType.SPLIT);
    }

    public boolean isWFJoinTransaction() {
        return transactionTypeCheck(WorkflowHandoverType.JOIN);
    }

    /**
     * Returns true if the transaction is of the given type
     */
    private boolean transactionTypeCheck(WorkflowHandoverType typeToCheckAgainst) {
        if (workflowHandoverType == null) {
            return false;
        }
        return workflowHandoverType == typeToCheckAgainst;
    }
}
