package at.ac.tuwien.infosys.prybila.runtimeVerification.test.testingSources;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.BitcoinConnection;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.WorkflowDataBlockConverter;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverType;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowInstance;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.SendRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.bitcoinj.script.ScriptOpCodes.*;

/**
 * Extends the normal bitcoin connection with test methods
 */
@Component
@Scope("prototype")
public class BitcoinConnectionWithTestMethods extends BitcoinConnection {

    private static final Logger logger = Logger.getLogger(BitcoinConnectionWithTestMethods.class);

    /**
     * Sends an arbitrary free output to the given adress by packing a P2PKH into a P2SH.
     * Can be seen as initial start of WF.
     */
    public SendRequest sendFreeOutputToAddressWithP2SHAndExtraData(String address, byte[] hash160OfWfData, ECKey keyToSignWfData) throws InsufficientMoneyException, IOException, ExecutionException, InterruptedException {
        openConnectionGuard("sentAmountToAddress");

        //Build P2SH send address
        Address payee = Address.fromBase58(networkParameters, address);
        Script redeemScript = new ScriptBuilder()
                .data(hash160OfWfData)
                .op(OP_DROP)
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(payee.getHash160())
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();
        Script outputscript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        Address scriptAddressForP2SH = Address.fromP2SHScript(networkParameters, outputscript);

        // select the smallest single payable P2PKH output
        TransactionOutput spendableOutput = selectSmallestSpendableOutput(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.getValue());

        //Create transaction which sends to P2SH
        SendRequest request = SendRequest.to(scriptAddressForP2SH, spendableOutput.getValue());

        //add data input
        WorkflowInstance workflowInstance = new WorkflowInstance((short) 1524);
        WorkflowHandoverData workflowHandoverData
                = new WorkflowHandoverData(
                workflowInstance,
                (byte) 15,
                WorkflowHandoverType.INTERMEDIATE,
                (int) (Calendar.getInstance().getTimeInMillis() / 1000L));
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(workflowHandoverData);
        byte[] dummySignature = new byte[71];
        workflowDataBlockConverter.setSignature(dummySignature);
        byte[] data = workflowDataBlockConverter.serializeToSignedDataBlock(true);
        Script dataScript = new ScriptBuilder().op(OP_RETURN).data(data).build();
        request.tx.addOutput(Coin.ZERO, dataScript);

        //add input P2PKH that matches output value
        TransactionInput inputToSign = request.tx.addInput(spendableOutput);
        ECKey keyOfSpendableOutput = wallet.findKeyFromPubHash(spendableOutput.getAddressFromP2PKHScript(networkParameters).getHash160());

        //Calculate Fee
        final int size = request.tx.unsafeBitcoinSerialize().length + (1 * Script.SIG_SIZE + keyOfSpendableOutput.getPubKey().length);
        Coin fee = request.feePerKb.multiply(size).divide(1000);
        TransactionOutput output = request.tx.getOutput(0);
        output.setValue(output.getValue().subtract(fee));

        Sha256Hash sighash = request.tx.hashForSignature(0, spendableOutput.getScriptPubKey().getProgram(), Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = keyOfSpendableOutput.sign(sighash);
        TransactionSignature transactionSignature = new TransactionSignature(signature, Transaction.SigHash.ALL, false);
        Script p2PKHRedeemScript = new ScriptBuilder()
                .data(transactionSignature.encodeToBitcoin())
                .data(keyOfSpendableOutput.getPubKey())
                .build();
        inputToSign.setScriptSig(p2PKHRedeemScript);
        inputToSign.verify(spendableOutput);

        wallet.commitTx(request.tx);
        TransactionBroadcast broadcast = peerGroup.broadcastTransaction(request.tx);
        broadcast.future().get();
        //commit after broadcast to make sure it was accepted
        wallet.saveToFile(walletFile);
        return request;
    }

    public SendRequest receiveAndForwardOutputFromP2SH(String addressItWasSentTo, TransactionOutput p2shOutput, String toAddress, Script redeemScript) throws ExecutionException, InterruptedException, IOException, TimeoutException {
        Address addressToWhichP2SHPoints = Address.fromBase58(networkParameters, addressItWasSentTo);
        ECKey key = wallet.findKeyFromPubHash(addressToWhichP2SHPoints.getHash160());
        return receiveAndForwardOutputFromP2SH(addressItWasSentTo, p2shOutput, toAddress, redeemScript, key);
    }

    /**
     * Claims the given output with a P2SH redeem script and forwards it with P2PKH to the given address.
     * Can be seen as a handover in a WF.
     */
    public SendRequest receiveAndForwardOutputFromP2SH(String addressItWasSentTo, TransactionOutput p2shOutput, String toAddress, Script redeemScript, ECKey privateKey) throws ExecutionException, InterruptedException, IOException, TimeoutException {
        //forward output
        Address addressToForwardTo = Address.fromBase58(networkParameters, toAddress);
        SendRequest request = SendRequest.to(addressToForwardTo, p2shOutput.getValue());
        TransactionInput p2shInput = request.tx.addInput(p2shOutput);

        //Calculate Fee
        final int size = request.tx.unsafeBitcoinSerialize().length + (1 * Script.SIG_SIZE + redeemScript.getProgram().length);
        Coin fee = request.feePerKb.multiply(size).divide(1000);
        TransactionOutput output = request.tx.getOutput(0);
        output.setValue(output.getValue().subtract(fee));

        //add signature
        Sha256Hash sighash = request.tx.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);

        ECKey.ECDSASignature signature = privateKey.sign(sighash);
        TransactionSignature transactionSignature = new TransactionSignature(signature, Transaction.SigHash.ALL, false);

        Script redeemScriptWithSignature = new ScriptBuilder()
                .data(transactionSignature.encodeToBitcoin())
                .data(privateKey.getPubKey())
                .data(redeemScript.getProgram())
                .build();
        p2shInput.setScriptSig(redeemScriptWithSignature);
        p2shInput.verify(p2shOutput);

        wallet.commitTx(request.tx);
        TransactionBroadcast broadcast = peerGroup.broadcastTransaction(request.tx);
        //broadcast.future().get(20, TimeUnit.SECONDS);
        //commit after broadcast to make sure it was accepted
        wallet.saveToFile(walletFile);
        return request;
    }

    /**
     * Sends the given amount to the given address.
     * Blocks until the transaction has been broadcast.
     * NOTE: Does not wait for inclusion into a block!
     */
    public SendRequest sentAmountToAddress(Coin amount, String address) throws ExecutionException, InterruptedException, IOException, InsufficientMoneyException {
        return _sentAmountToAddress(amount, address, false);
    }

    public SendRequest sentCompleteAmountToAddress(Coin amount, String address) throws ExecutionException, InterruptedException, IOException, InsufficientMoneyException {
        return _sentAmountToAddress(amount, address, true);
    }

    private SendRequest _sentAmountToAddress(Coin amount, String address, boolean emptyWallet) throws ExecutionException, InterruptedException, IOException, InsufficientMoneyException {
        openConnectionGuard("sentAmountToAddress");
        if (amount.getValue() <= 0) {
            logger.warn("Sending failed. Tried to send " + amount.getValue() + ".");
            return null;
        }
        Address payee = Address.fromBase58(networkParameters, address);
        SendRequest request = SendRequest.to(payee, amount);
        if (emptyWallet) {
            request.emptyWallet = true;
        }

        request.coinSelector = new DefaultCoinSelectorWithBugRemoved();
        wallet.completeTx(request);
        TransactionBroadcast broadcast = peerGroup.broadcastTransaction(request.tx);
        broadcast.future().get();
        logger.info("Broadcast sendAmount transaction " + request.tx.getHashAsString());
        //commit after broadcast to make sure it was accepted
        wallet.saveToFile(walletFile);
        return request;
    }

    /**
     * Blocks until all pending transactions have been confirmed or waitMinutes was exceeded
     */
    public void waitForPendingTransactions(int waitMinutes) {
        List<Sha256Hash> txHashes = new ArrayList<>();
        //avoid ConcurrentModificationException by fetching only the tx hashes first.
        for (Transaction transaction : wallet.getPendingTransactions()) {
            txHashes.add(transaction.getHash());
        }

        for (Sha256Hash txHash : txHashes) {
            Transaction transaction = wallet.getTransaction(txHash);
            ListenableFuture<TransactionConfidence> future = transaction.getConfidence().getDepthFuture(1);
            try {
                future.get(waitMinutes, TimeUnit.MINUTES);
            } catch (Exception e) {
                logger.info("Transaction was not confirmed. " + transaction, e);
                return;
            }
        }
        logger.info("All transaction where confirmed.");
    }

    /**
     * Sends the given amount to the given address.
     * Blocks until the transaction has been broadcast.
     * NOTE: Does not wait for inclusion into a block!
     * <p>
     * Additionally adds an output block with signed data.
     */
    public SendRequest sendAmountToAddressWithExtraData(Coin amount, String address, ECKey keyToSign) throws InsufficientMoneyException, IOException, ExecutionException, InterruptedException {
        openConnectionGuard("sendAmountToAddressWithExtraData");
        if (amount.getValue() <= 0) {
            logger.warn("Sending failed. Tried to send " + amount.getValue() + ".");
            return null;
        }
        Address payee = Address.fromBase58(networkParameters, address);
        SendRequest request = SendRequest.to(payee, amount);
        WorkflowInstance workflowInstance = new WorkflowInstance((short) 1524);
        WorkflowHandoverData workflowHandoverData
                = new WorkflowHandoverData(
                workflowInstance,
                (byte) 15,
                WorkflowHandoverType.INTERMEDIATE,
                (int) (Calendar.getInstance().getTimeInMillis() / 1000L));
        WorkflowDataBlockConverter workflowDataBlockConverter = new WorkflowDataBlockConverter(workflowHandoverData);
        byte[] dummySignature = new byte[71];
        workflowDataBlockConverter.setSignature(dummySignature);
        byte[] data = workflowDataBlockConverter.serializeToSignedDataBlock(true);
        Script dataScript = new ScriptBuilder().op(OP_RETURN).data(data).build();
        request.tx.addOutput(Coin.ZERO, dataScript);
        wallet.completeTx(request);

        TransactionBroadcast broadcast = peerGroup.broadcastTransaction(request.tx);
        broadcast.future().get();
        //commit after broadcast to make sure it was accepted
        wallet.saveToFile(walletFile);
        return request;
    }

    public TransactionOutput getOutputToAddress(String txHash) {
        return getOutputToAddress(txHash, 0);
    }

    public TransactionOutput getOutputToAddress(String txHash, int index) {
        return wallet.getTransaction(Sha256Hash.wrap(txHash)).getOutput(index);
    }


    public Script createRedeemScript(String addressItWasSentTo, byte[] hash160OfWfData) {
        Address addressToWhichP2SHPoints = Address.fromBase58(networkParameters, addressItWasSentTo);
        Script redeemScript = new ScriptBuilder()
                .data(hash160OfWfData)
                .op(OP_DROP)
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(addressToWhichP2SHPoints.getHash160())
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();
        return redeemScript;
    }

    public static Script createRedeemScript(String addressItWasSentTo, NetworkParameters networkParameters) {
        Address addressToWhichP2SHPoints = Address.fromBase58(networkParameters, addressItWasSentTo);
        Script redeemScript = new ScriptBuilder()
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(addressToWhichP2SHPoints.getHash160())
                .op(OP_EQUALVERIFY)
                .op(OP_CHECKSIG)
                .build();
        return redeemScript;
    }

    /**
     * Returns the smallest spendable P2PKH TransactionOutput or null;
     */
    public TransactionOutput selectSmallestSpendableOutput(long threshold) {
        TransactionOutput spendableOutput = null;
        for (TransactionOutput spendCandidates : wallet.calculateAllSpendCandidates()) {
            if (spendCandidates.getValue().getValue() < threshold) {
                continue;
            }
            if (spendableOutput == null) {
                spendableOutput = spendCandidates;
                continue;
            }
            if (spendableOutput.getValue().getValue() > spendCandidates.getValue().getValue()) {
                spendableOutput = spendCandidates;
            }
        }
        return spendableOutput;
    }

    public boolean peerIsLocalhost() {
        if (peerGroup.getConnectedPeers().size() != 1) {
            return false;
        }
        return peerGroup.getConnectedPeers().get(0).getAddress().getAddr().getHostAddress().equals("127.0.0.1");
    }

}
