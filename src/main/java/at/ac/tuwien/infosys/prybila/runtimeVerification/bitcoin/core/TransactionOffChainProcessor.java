package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.TransactionReference;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import org.bitcoinj.core.*;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Enables off chain transaction signing and validation as defined in the workflow runtime crawler concept.
 */
public class TransactionOffChainProcessor {

    /**
     * Signs the content of transaction with the given key.
     * Returns the signature as byte[] encoded in DER.
     */
    public byte[] signTransactionOffline(ECKey privateKey, Transaction transaction, NetworkParameters parameters) throws IOException {
        Sha256Hash hash = getOffChainHashOfTransaction(transaction, parameters);
        return privateKey.sign(hash).encodeToDER();
    }

    private Sha256Hash getOffChainHashOfTransaction(Transaction transaction, NetworkParameters parameters) throws IOException {
        Transaction transactionCopy = copyAndAdjustTransaction(transaction, parameters);
        ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(
                transactionCopy.getMessageSize() == Message.UNKNOWN_LENGTH ? 256 : transactionCopy.getMessageSize());
        transactionCopy.bitcoinSerialize(bos);
        Sha256Hash hash = Sha256Hash.twiceOf(bos.toByteArray());
        bos.close();
        return hash;
    }

    /**
     * Creates a copy and adjusts the transaction before hashing, if necessary.
     * Removes the on-chain signature in the sigScript if present.
     * Removes the off-chain signature in the OP_RETURN data block if present.
     * Throws a RuntimeVerificationException if the transaction does not fit the expected handover-transaction structure.
     */
    private Transaction copyAndAdjustTransaction(Transaction transaction, NetworkParameters parameters) {
        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(transaction)
        );
        if (!transactionStructureVerifier.isWFHandoverTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + transaction + " is not a common handover transaction.");
        }

        //create copy
        Transaction copy = parameters.getDefaultSerializer().makeTransaction(transaction.bitcoinSerialize());

        //check and adjust the input sigScript
        TransactionInput transactionInput = copy.getInput(0);
        ScriptChunk lastChunkOfScript = transactionInput.getScriptSig().getChunks().get(0);
        //If the last part of the input-sigScript does not have the size of a public key it is considered to be a signature.
        if (lastChunkOfScript.data.length != 33) {
            ScriptBuilder scriptBuilderForReducedScript = new ScriptBuilder();
            //Ignore the topmost script part
            List<ScriptChunk> chunks = transactionInput.getScriptSig().getChunks();
            for (int i = 1; i < chunks.size(); i++) {
                scriptBuilderForReducedScript.addChunk(chunks.get(i));
            }
            transactionInput.setScriptSig(scriptBuilderForReducedScript.build());
        }

        TransactionOutput opReturnOutput = copy.getOutput(1);
        byte[] opReturnData = opReturnOutput.getScriptPubKey().getChunks().get(1).data;
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(opReturnData);
        if (workflowDataBlockConverter.getSignature() != null) {
            byte[] dataBlockWithoutSignature = workflowDataBlockConverter.serializeToSignedDataBlock(false);
            new TransactionBuilder().replaceOpReturnBlockWithNewOne(dataBlockWithoutSignature, copy);
        }
        return copy;
    }

    /**
     * Validates if the signature with the given key matches the datablock.
     * Throws a RuntimeVerificationException if the validation fails.
     */
    public void validateSignatureOffline(byte[] publicKeyOfPartnerAsBytes, byte[] signature, Transaction transaction, NetworkParameters parameters) throws IOException {
        ECKey publicKeyOfPartner = ECKey.fromPublicOnly(publicKeyOfPartnerAsBytes);
        Sha256Hash hash = getOffChainHashOfTransaction(transaction, parameters);
        boolean valid = publicKeyOfPartner.verify(hash, ECKey.ECDSASignature.decodeFromDER(signature));
        if (!valid) {
            throw new RuntimeVerificationException("The contained signature did not match the contained dataBlock.");
        }
    }

}
