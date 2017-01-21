package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.TransactionReference;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverType;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;

import java.util.ArrayList;
import java.util.List;

import static org.bitcoinj.script.ScriptOpCodes.*;

/**
 * Creates the transactions for the start of a workflow instance,
 * an intermediate handover and the end of a workflow instance.
 */
public class TransactionBuilder {

    private WorkflowDataBlockConverter workflowDataBlockConverter;

    public TransactionBuilder() {
    }

    /**
     * Creates a transaction template to start a new workflow instance.
     * Does not specify any inputs. These inputs must still be set by the framework.
     * This is not a handover to another participant, the addressToSendToken should still be under
     * the control of the process owner.
     * For compatibility reasons this transaction already uses a P2SH output.
     * NOTE: This transaction must still be finished with "wallet.completeTx(request);"
     */
    public SendRequest createWorkflowStartTransaction(Address addressToSendToken, Coin tokenSize, WorkflowHandoverData workflowHandoverData, NetworkParameters networkParameters) {
        if (workflowHandoverData.getWorkflowHandoverType() != WorkflowHandoverType.START) {
            throw new RuntimeVerificationException("The given workflowHandoverData is not of type WorkflowHandoverType.START.");
        }
        Script redeemScriptToBeContainedInP2SH = createPay2PublicKeyHashScriptWithOptionalDataAttached(addressToSendToken);
        Address p2shAddress = createP2SHAdressFromScript(redeemScriptToBeContainedInP2SH, networkParameters);
        SendRequest request = SendRequest.to(p2shAddress, tokenSize);
        workflowDataBlockConverter = new WorkflowDataBlockConverter(workflowHandoverData);
        addDatablockOutputToTransaction(request.tx, workflowDataBlockConverter.serializeToWorkflowStartBlock());

        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(request.tx));
        if (!transactionStructureVerifier.isWFStartTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + request.tx + " is not a common wfStart transaction.");
        }

        return request;
    }

    /**
     * Creates a handover transaction template without off- and on-chain signatures.
     * Expects to retrieve its input from a given p2sh output.
     * Forwards the token also as p2sh output.
     * Returns a transaction template which must be signed by the receiving partner of the handover.
     */
    public Transaction createWorkflowHandOverTransactionTemplate(
            WorkflowHandoverData workflowHandoverData,
            TransactionOutput p2shOutputFromPreviousTransaction,
            Script redeemScriptAndPubKeyForPreviousTransactionButWithoutSignature,
            Address addressToSendToken,
            NetworkParameters networkParameters,
            byte[]... dataToAppendToNewRedeemScript) {
        if (workflowHandoverData.getWorkflowHandoverType() != WorkflowHandoverType.INTERMEDIATE) {
            throw new RuntimeVerificationException("The given workflowHandoverData is not of type WorkflowHandoverType.INTERMEDIATE.");
        }
        //Prepare forward address
        Script newRedeemScript = createPay2PublicKeyHashScriptWithOptionalDataAttached(addressToSendToken, dataToAppendToNewRedeemScript);
        Address p2shAddress = createP2SHAdressFromScript(newRedeemScript, networkParameters);
        //send token to forward address as p2sh
        SendRequest request = SendRequest.to(p2shAddress, p2shOutputFromPreviousTransaction.getValue());
        //connect to prevous transaction
        TransactionInput transactionInput = request.tx.addInput(p2shOutputFromPreviousTransaction);
        //add workflow data block without off-chain signature
        workflowDataBlockConverter = new WorkflowDataBlockConverter(workflowHandoverData);
        byte[] wfDatablockExclSignature = workflowDataBlockConverter.serializeToSignedDataBlock(false);
        addDatablockOutputToTransaction(request.tx, wfDatablockExclSignature);
        //subtract a fee
        subtractFeeFromRequest(
                request,
                redeemScriptAndPubKeyForPreviousTransactionButWithoutSignature.getProgram().length);
        //adds the p2sh redeem script to the input but without the necessary signature parameter
        transactionInput.setScriptSig(redeemScriptAndPubKeyForPreviousTransactionButWithoutSignature);

        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(request.tx));
        if (!transactionStructureVerifier.isWFHandoverTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + request.tx + " is not a common handover transaction.");
        }
        return request.tx;
    }

    /**
     * Finishes a given handover transaction template with a given off-chain signature and a generated on-chain signature.
     * Verifies the given redeemScript against the connected output and throws a VerificationException on failure.
     * Returns a SendRequest, ready to be published.
     */
    public SendRequest finishWorkflowHandOverTransactionFromTemplate(Transaction template,
                                                                     Script redeemScriptForPreviousTransaction,
                                                                     ECKey keyForPreviousTransaction,
                                                                     ECKey.ECDSASignature offChainSignature) {
        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(template));
        if (!transactionStructureVerifier.isWFHandoverTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + template + " is not a common handover transaction.");
        }

        //replace OP_RETURN with signed one
        WorkflowDataBlockConverter wdbc = new WorkflowDataBlockConverter(getDataFromOpReturnOutputOfIntermediateTransaction(template));
        wdbc.setSignature(offChainSignature.encodeToDER());
        byte[] signedDataBlock = wdbc.serializeToSignedDataBlock(true);
        replaceOpReturnBlockWithNewOne(signedDataBlock, template);

        //Re-create Sendrequest
        SendRequest sendRequest = SendRequest.forTx(template);

        //Sign transaction
        Sha256Hash hashForSignature = sendRequest.tx.hashForSignature(0, redeemScriptForPreviousTransaction, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = keyForPreviousTransaction.sign(hashForSignature);
        Script validSigScript = createP2SHRedeemScriptCombinedWithPublicKeyAndSignature(redeemScriptForPreviousTransaction, keyForPreviousTransaction, signature);

        TransactionInput transactionInput = sendRequest.tx.getInput(0);
        transactionInput.setScriptSig(validSigScript);
        transactionInput.verify(transactionInput.getConnectedOutput());

        transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(sendRequest.tx)
        );
        if (!transactionStructureVerifier.isWFHandoverTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + sendRequest.tx + " is not a common handover transaction.");
        }
        return sendRequest;
    }

    /**
     * Adapts a given transaction with a given off-chain signature and sigScript.
     * This must be performed by the receiver of a handover transaction in order to receive the same transaction hash as
     * published by the sender of the handover.
     * Returns a SendRequest containing the transaction.
     */
    public SendRequest finishHandoverTemplateOnReceiverSide(Transaction template,
                                                            ECKey.ECDSASignature offChainSignature,
                                                            byte[] sigScriptOfPreviousInput) {
        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(template)
        );
        if (!transactionStructureVerifier.isWFHandoverTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + template + " is not a common handover transaction.");
        }

        //add finalized SigScript to input
        template.getInput(0).setScriptSig(new Script(sigScriptOfPreviousInput));

        //replace OP_RETURN with signed one
        WorkflowDataBlockConverter wdbc = new WorkflowDataBlockConverter(getDataFromOpReturnOutputOfIntermediateTransaction(template));
        wdbc.setSignature(offChainSignature.encodeToDER());
        byte[] signedDataBlock = wdbc.serializeToSignedDataBlock(true);
        replaceOpReturnBlockWithNewOne(signedDataBlock, template);

        //Re-create Sendrequest
        return SendRequest.forTx(template);
    }

    /**
     * Creates a split transaction.
     * Expects to retrieve its input from a given p2sh output.
     * Forwards the token to a number of p2sh outputs.
     * The fee is subtracted equally from every output.
     * This is not a handover to another participant, the addressesToSendTokenTo should still be under
     * the control of the process owner.
     * Returns a SendRequest, ready to be published.
     */
    public SendRequest createWorkflowSplitTransaction(
            WorkflowHandoverData workflowHandoverData,
            Address addressToRedeemPreviousP2SHOutput,
            TransactionOutput p2shOutputFromPreviousTransaction,
            Script redeemScriptForPreviousTransaction,
            ECKey keyForPreviousTransaction,
            List<Address> addressesToSendToken,
            List<Coin> tokenDistribution,
            NetworkParameters networkParameters) {
        if (workflowHandoverData.getWorkflowHandoverType() != WorkflowHandoverType.SPLIT) {
            throw new RuntimeVerificationException("The given workflowHandoverData is not of type WorkflowHandoverType.SPLIT.");
        }
        if (addressesToSendToken.size() != tokenDistribution.size()) {
            throw new RuntimeVerificationException("Token is not correctly distributed to the required outputs.");
        }
        if (!totalValueOf(tokenDistribution).equals(p2shOutputFromPreviousTransaction.getValue())) {
            throw new RuntimeVerificationException("Token is not correctly distributed to the required outputs.");
        }
        SendRequest request = null;
        for (int i = 0; i < addressesToSendToken.size(); i++) {
            Coin tokenPortion = tokenDistribution.get(i);
            Address addressToSendToken = addressesToSendToken.get(i);
            //Prepare forward address
            Script newRedeemScript = createPay2PublicKeyHashScriptWithOptionalDataAttached(
                    addressToSendToken);
            Address p2shAddress = createP2SHAdressFromScript(newRedeemScript, networkParameters);
            //send token to forward address as p2sh
            if (request == null) {
                request = SendRequest.to(p2shAddress, tokenPortion);
            } else {
                request.tx.addOutput(tokenPortion, p2shAddress);
            }
        }
        //connect to prevous transaction
        TransactionInput transactionInput = request.tx.addInput(p2shOutputFromPreviousTransaction);
        //add workflow data block without off-chain signature
        workflowDataBlockConverter = new WorkflowDataBlockConverter(workflowHandoverData);
        byte[] wfDatablockExclSignature = workflowDataBlockConverter.serializeToWorkflowSplitBlock();
        addDatablockOutputToTransaction(request.tx, wfDatablockExclSignature);

        //subtract a fee
        subtractFeeFromRequestDistributedAcrossP2SH(
                request,
                createP2SHRedeemScriptCombinedWithPublicKeyButWithoutSignature(
                        addressToRedeemPreviousP2SHOutput,
                        keyForPreviousTransaction).getProgram(),
                addressesToSendToken.size());

        //Sign transaction
        Sha256Hash hashForSignature = request.tx.hashForSignature(0, redeemScriptForPreviousTransaction, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = keyForPreviousTransaction.sign(hashForSignature);
        Script validSigScript = createP2SHRedeemScriptCombinedWithPublicKeyAndSignature(redeemScriptForPreviousTransaction, keyForPreviousTransaction, signature);

        transactionInput.setScriptSig(validSigScript);
        transactionInput.verify(transactionInput.getConnectedOutput());

        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(request.tx));
        if (!transactionStructureVerifier.isWFSplitTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + request.tx + " is not a common split transaction.");
        }
        return request;
    }

    /**
     * Creates a join transaction.
     * Expects to retrieve its inputs from a given list of p2sh outputs.
     * Forwards the token to one p2sh outputs.
     * This is not a handover to another participant, the addressToSendTokenTo should still be under
     * the control of the process owner.
     * Returns a SendRequest, ready to be published.
     */
    public SendRequest createWorkflowJoinTransaction(
            WorkflowHandoverData workflowHandoverData,
            List<Address> addressesToRedeemPreviousP2SHOutput,
            List<TransactionOutput> p2shOutputsFromPreviousTransactions,
            List<Script> redeemScriptsForPreviousTransaction,
            List<ECKey> keysForPreviousTransaction,
            Address addressToSendToken,
            NetworkParameters networkParameters,
            byte[]... dataToAppendToNewRedeemScript) {
        if (workflowHandoverData.getWorkflowHandoverType() != WorkflowHandoverType.JOIN) {
            throw new RuntimeVerificationException("The given workflowHandoverData is not of type WorkflowHandoverType.JOIN.");
        }
        if (addressesToRedeemPreviousP2SHOutput.size() != p2shOutputsFromPreviousTransactions.size()) {
            throw new RuntimeVerificationException("Not enough p2shOutputs available for the required inputs.");
        }
        if (addressesToRedeemPreviousP2SHOutput.size() != redeemScriptsForPreviousTransaction.size()) {
            throw new RuntimeVerificationException("Not enough redeemScripts available for the required inputs.");
        }
        if (addressesToRedeemPreviousP2SHOutput.size() != keysForPreviousTransaction.size()) {
            throw new RuntimeVerificationException("Not enough keys available for the required inputs.");
        }
        Coin token = Coin.ZERO;
        for (TransactionOutput transactionOutput : p2shOutputsFromPreviousTransactions) {
            token = token.add(transactionOutput.getValue());
        }
        //Prepare forward address
        Script newRedeemScript = createPay2PublicKeyHashScriptWithOptionalDataAttached(addressToSendToken, dataToAppendToNewRedeemScript);
        Address p2shAddress = createP2SHAdressFromScript(newRedeemScript, networkParameters);
        //send token to forward address as p2sh
        SendRequest request = SendRequest.to(p2shAddress, token);
        List<TransactionInput> transactionInputs = new ArrayList<>();
        for (TransactionOutput p2shOutputFromPreviousTransaction : p2shOutputsFromPreviousTransactions) {
            //connect to previous transactions
            transactionInputs.add(request.tx.addInput(p2shOutputFromPreviousTransaction));
        }
        //add workflow data block without off-chain signature
        workflowDataBlockConverter = new WorkflowDataBlockConverter(workflowHandoverData);
        byte[] wfDatablockExclSignature = workflowDataBlockConverter.serializeToWorkflowJoinBlock();
        addDatablockOutputToTransaction(request.tx, wfDatablockExclSignature);

        //subtract a fee
        int allInputsScriptLength = 0;
        for (int i = 0; i < addressesToRedeemPreviousP2SHOutput.size(); i++) {
            allInputsScriptLength += createP2SHRedeemScriptCombinedWithPublicKeyButWithoutSignature(
                    addressesToRedeemPreviousP2SHOutput.get(i),
                    keysForPreviousTransaction.get(i)).getProgram().length;
        }
        subtractFeeFromRequest(
                request, allInputsScriptLength, addressesToRedeemPreviousP2SHOutput.size());

        //Sign inputs of transaction
        for (int i = 0; i < addressesToRedeemPreviousP2SHOutput.size(); i++) {
            Script redeemScriptForPreviousTransaction = redeemScriptsForPreviousTransaction.get(i);
            ECKey keyForPreviousTransaction = keysForPreviousTransaction.get(i);
            TransactionInput transactionInput = transactionInputs.get(i);
            Sha256Hash hashForSignature = request.tx.hashForSignature(i, redeemScriptForPreviousTransaction, Transaction.SigHash.ALL, false);
            ECKey.ECDSASignature signature = keyForPreviousTransaction.sign(hashForSignature);
            Script validSigScript = createP2SHRedeemScriptCombinedWithPublicKeyAndSignature(redeemScriptForPreviousTransaction, keyForPreviousTransaction, signature);

            transactionInput.setScriptSig(validSigScript);
            transactionInput.verify(transactionInput.getConnectedOutput());
        }

        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(request.tx));
        if (!transactionStructureVerifier.isWFJoinTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + request.tx + " is not a common join transaction.");
        }
        return request;
    }

    /**
     * Creates a transaction template to end a workflow instance.
     * Expects to retrieve its input from a given p2sh output.
     * This is not a handover to another participant, the addressToSendTokenTo should still be under
     * the control of the process owner.
     * In order to make the wallet recognize the received token, the output is forwarded in a P2PKH.
     */
    public SendRequest createWorkflowEndTransaction(
            WorkflowHandoverData workflowHandoverData,
            Address addressToSendTokenTo,
            TransactionOutput p2shOutputFromPreviousTransaction,
            Address addressToReceiveTokenFrom,
            ECKey keyForPreviousTransaction,
            byte[]... dataToAppendToRedeemScriptForInput) {
        if (addressToSendTokenTo.isP2SHAddress()) {
            throw new RuntimeVerificationException("WF_End transaction must point to a P2PKH address.");
        }
        if (workflowHandoverData.getWorkflowHandoverType() != WorkflowHandoverType.END) {
            throw new RuntimeVerificationException("The given workflowHandoverData is not of type WorkflowHandoverType.END.");
        }
        Script redeemScriptForPreviousTransaction = createRedeemScript(addressToReceiveTokenFrom, dataToAppendToRedeemScriptForInput);
        Script redeemScriptAndPubKeyForPreviousTransactionButWithoutSignature
                = createP2SHRedeemScriptCombinedWithPublicKeyButWithoutSignature(
                addressToReceiveTokenFrom,
                keyForPreviousTransaction,
                dataToAppendToRedeemScriptForInput);

        SendRequest sendRequest = SendRequest.to(addressToSendTokenTo, p2shOutputFromPreviousTransaction.getValue());
        TransactionInput p2shInput = sendRequest.tx.addInput(p2shOutputFromPreviousTransaction);

        //add OP_RETURN data
        workflowDataBlockConverter = new WorkflowDataBlockConverter(workflowHandoverData);
        addDatablockOutputToTransaction(sendRequest.tx, workflowDataBlockConverter.serializeToWorkflowEndBlock());

        //subtract a fee
        subtractFeeFromRequest(
                sendRequest,
                redeemScriptAndPubKeyForPreviousTransactionButWithoutSignature.getProgram().length);

        //Sign transaction
        Sha256Hash hashForSignature = sendRequest.tx.hashForSignature(0, redeemScriptForPreviousTransaction, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = keyForPreviousTransaction.sign(hashForSignature);
        Script validSigScript = createP2SHRedeemScriptCombinedWithPublicKeyAndSignature(redeemScriptForPreviousTransaction, keyForPreviousTransaction, signature);

        p2shInput.setScriptSig(validSigScript);
        p2shInput.verify(p2shInput.getConnectedOutput());

        TransactionStructureVerifier transactionStructureVerifier = new TransactionStructureVerifier(
                TransactionReference.getReferenceForVerificationOnly(sendRequest.tx));
        if (!transactionStructureVerifier.isWFEndTransaction()) {
            throw new RuntimeVerificationException("The given transaction " + sendRequest.tx + " is not a common wfEnd transaction.");
        }
        return sendRequest;
    }

    /**
     * Returns the OP_RETURN data of the given transaction.
     */
    private byte[] getDataFromOpReturnOutputOfIntermediateTransaction(Transaction transaction) {
        TransactionOutput opReturnWithoutSignature = transaction.getOutput(1);
        return opReturnWithoutSignature.getScriptPubKey().getChunks().get(1).data;
    }

    /**
     * Replaces the OP_RETURN Block with the given data.
     */
    public void replaceOpReturnBlockWithNewOne(byte[] newDatablock, Transaction transaction) {
        //Reset output list to size "1"
        TransactionOutput p2SHOutput = transaction.getOutput(0);
        transaction.clearOutputs();
        transaction.addOutput(p2SHOutput);
        addDatablockOutputToTransaction(transaction, newDatablock);
    }

    /**
     * Combines the P2SH redeem script with the pubKey and the signature in order to be a valid sigScript for the
     * Handover transaction input.
     */
    public Script createP2SHRedeemScriptCombinedWithPublicKeyAndSignature(Script redeemScript,
                                                                          ECKey publicKey,
                                                                          ECKey.ECDSASignature signature) {
        TransactionSignature transactionSignature = new TransactionSignature(signature, Transaction.SigHash.ALL, false);
        return new ScriptBuilder()
                .data(transactionSignature.encodeToBitcoin())
                .data(publicKey.getPubKey())
                .data(redeemScript.getProgram())
                .build();
    }

    /**
     * Returns a P2SH redeem script without the signature to be included in a handover transaction template
     */
    public Script createP2SHRedeemScriptCombinedWithPublicKeyButWithoutSignature(Address addressContainedInRedeemScript,
                                                                                 ECKey publicKey,
                                                                                 byte[]... dataToAppendToOldScript) {
        Script redeemScript = createRedeemScript(addressContainedInRedeemScript, dataToAppendToOldScript);
        return new ScriptBuilder()
                .data(publicKey.getPubKey())
                .data(redeemScript.getProgram())
                .build();
    }

    /**
     * Create the standard P2SH redeem script for this use case.
     */
    public Script createRedeemScript(Address payee, byte[]... data) {
        return createPay2PublicKeyHashScriptWithOptionalDataAttached(payee, data);
    }

    /**
     * Create a P2PKH script with optional data blocks attached
     */
    public Script createPay2PublicKeyHashScriptWithOptionalDataAttached(Address payee, byte[]... data) {
        ScriptBuilder p2PKHWithData = new ScriptBuilder();
        if (data != null && data.length > 0) {
            int dropCount = 0;
            for (byte[] dataBlock : data) {
                if (dataBlock != null) {
                    p2PKHWithData.data(dataBlock);
                    dropCount++;
                }
            }
            for (int i = 0; i < dropCount; i++) {
                p2PKHWithData.op(OP_DROP);
            }
        }
        p2PKHWithData
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(payee.getHash160())
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();
        return p2PKHWithData.build();
    }

    /**
     * Estimates the size of the transaction and subtracts an appropriate fee from the first output.
     * Expects to receive the complete sigScript except the final signature appended.
     */
    private void subtractFeeFromRequest(SendRequest sendRequest, int sigScriptLength) {
        subtractFeeFromRequest(sendRequest, sigScriptLength, 1);
    }

    private void subtractFeeFromRequest(SendRequest sendRequest, int sigScriptLength, int numOfScripts) {
        final int size = sendRequest.tx.unsafeBitcoinSerialize().length + (numOfScripts * Script.SIG_SIZE + sigScriptLength);
        Coin fee = sendRequest.feePerKb.multiply(size).divide(1000);
        TransactionOutput output = sendRequest.tx.getOutput(0);
        output.setValue(output.getValue().subtract(fee));
    }

    /**
     * Estimates the size of the transaction and subtracts an appropriate fee from all P2SH outputs equally.
     * Expects to receive the complete sigScript except the final signature appended.
     */
    private void subtractFeeFromRequestDistributedAcrossP2SH(SendRequest sendRequest, byte[] sigScript, int numOfP2SHOutputs) {
        final int size = sendRequest.tx.unsafeBitcoinSerialize().length + (1 * Script.SIG_SIZE + sigScript.length);
        Coin fee = sendRequest.feePerKb.multiply(size).divide(1000);
        List<Coin> distribution = new RuntimeVerificationUtils().distributeCoinEqually(fee, numOfP2SHOutputs);
        for (int i = 0; i < numOfP2SHOutputs; i++) {
            TransactionOutput output = sendRequest.tx.getOutput(i);
            output.setValue(output.getValue().subtract(distribution.get(i)));
        }
    }

    /**
     * Returns a P2SH address with the given redeem script.
     */
    public Address createP2SHAdressFromScript(Script redeemScript, NetworkParameters networkParameters) {
        Script pubKeyScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        return Address.fromP2SHScript(networkParameters, pubKeyScript);
    }

    /**
     * Adds OP_RETURN data block to a transaction.
     * As of 30.06.2016 this block must not be longer than 80 bytes.
     * Note: Every transaction must only contain one output of this type.
     */
    public void addDatablockOutputToTransaction(Transaction transaction, byte[] data) {
        if (data == null || data.length > 80) {
            throw new RuntimeVerificationException("The provided datablock was not well formed.");
        }
        Script dataScript = new ScriptBuilder().op(OP_RETURN).data(data).build();
        transaction.addOutput(Coin.ZERO, dataScript);
    }

    private Coin totalValueOf(List<Coin> coins) {
        Coin result = Coin.ZERO;
        for (Coin coin : coins) {
            result = result.add(coin);
        }
        return result;
    }

}
