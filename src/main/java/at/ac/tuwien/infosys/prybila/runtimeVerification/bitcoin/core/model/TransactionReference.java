package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.TransactionSerializer;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedTransaction;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.SendRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper class representing a single transaction.
 * This is used to incorporate different representations of Bitcoin transactinos like from Bitcoinj or other frameworks.
 */
public class TransactionReference {

    private Transaction transactionBitcoinJ;

    private ParsedTransaction transactionFromCrawler;

    /**
     * IndexList of the transactions outputs which hold wf tokens.
     */
    private List<Integer> indicesOfTokenOutputs;

    public TransactionReference(ParsedTransaction transactionFromCrawler, List<Integer> indicesOfTokenOutputs) {
        this.transactionFromCrawler = transactionFromCrawler;
        this.indicesOfTokenOutputs = indicesOfTokenOutputs;
    }

    public TransactionReference(Transaction transactionBitcoinJ, List<Integer> indicesOfTokenOutputs) {
        this.transactionBitcoinJ = transactionBitcoinJ;
        this.indicesOfTokenOutputs = indicesOfTokenOutputs;
    }

    public TransactionReference(SendRequest sendRequest, List<Integer> indicesOfTokenOutputs) {
        this.transactionBitcoinJ = sendRequest.tx;
        this.indicesOfTokenOutputs = indicesOfTokenOutputs;
    }

    public TransactionReference(Transaction transactionBitcoinJ, Coin tokenSize) {
        this(transactionBitcoinJ, (List<Integer>) null);
        this.indicesOfTokenOutputs = Arrays.asList(determineTransactionOutput(transactionBitcoinJ, tokenSize));
    }

    public TransactionReference(SendRequest sendRequest, Coin tokenSize) {
        this(sendRequest, (List<Integer>) null);
        this.indicesOfTokenOutputs = Arrays.asList(determineTransactionOutput(sendRequest.tx, tokenSize));
    }

    /**
     * Assumes that there is only one appropriate output.
     * Searches for the TransactionOutput that holds the token of the WF.
     * Throws a RuntimeException if no output is found.
     */
    private int determineTransactionOutput(Transaction transaction, Coin tokenSize) {
        for (int i = 0; i < transaction.getOutputs().size(); i++) {
            TransactionOutput transactionOutput = transaction.getOutput(i);
            if (transactionOutput.getValue().compareTo(tokenSize) == 0) {
                return i;
            }
        }
        throw new RuntimeVerificationException("No transactionOutput with the tokenSize " + tokenSize.toFriendlyString() + " was found.");
    }

    public Transaction getBitcoinJTransaction() {
        if (transactionBitcoinJ != null) {
            return transactionBitcoinJ;
        }
        return null;
    }

    public ParsedTransaction getParsedTransaction() {
        if (transactionFromCrawler != null) {
            return transactionFromCrawler;
        }
        return null;
    }

    public void setTransactionFromCrawler(ParsedTransaction transactionFromCrawler) {
        this.transactionFromCrawler = transactionFromCrawler;
    }

    public int getOutputSize() {
        if (transactionBitcoinJ != null) {
            return transactionBitcoinJ.getOutputs().size();
        } else {
            return transactionFromCrawler.getOutputs().size();
        }
    }

    public int getInputSize() {
        if (transactionBitcoinJ != null) {
            return transactionBitcoinJ.getInputs().size();
        } else {
            return transactionFromCrawler.getInputs().size();
        }
    }

    public byte[] getOutputScript(int indexOfOutput) {
        if (transactionBitcoinJ != null) {
            return transactionBitcoinJ.getOutput(indexOfOutput).getScriptBytes();
        } else {
            return transactionFromCrawler.getOutputs().get(indexOfOutput).getScriptBytes();
        }
    }

    public String getOutputAddress(int indexOfOutput, NetworkParameters networkParameters) {
        if (transactionBitcoinJ != null) {
            if (transactionBitcoinJ.getOutput(indexOfOutput).getScriptPubKey().isPayToScriptHash()) {
                return transactionBitcoinJ.getOutput(indexOfOutput).getAddressFromP2SH(networkParameters).toBase58();
            } else if (transactionBitcoinJ.getOutput(indexOfOutput).getScriptPubKey().isSentToAddress()) {
                return transactionBitcoinJ.getOutput(indexOfOutput).getAddressFromP2PKHScript(networkParameters).toBase58();
            } else {
                return null;
            }
        } else {
            if (transactionFromCrawler.getOutputs() == null) {
                return null;
            }
            if (transactionFromCrawler.getOutputs().get(indexOfOutput).getAddresses() != null &&
                    !transactionFromCrawler.getOutputs().get(indexOfOutput).getAddresses().isEmpty()) {
                return transactionFromCrawler.getOutputs().get(indexOfOutput).getAddresses().get(0);
            }
            return null;
        }
    }

    /**
     * Assumes the given transaction is an intermediate handover
     */
    public boolean bitcoinJTransactionShouldBeUpdatedWithOnlineData() {
        if (!containsBitcoinJTransaction()) {
            return false;
        }
        return transactionBitcoinJ.getInput(0).getConnectedTransaction() == null;
    }

    /**
     * Returns the txHash of the previous transaction to the given input or null if not available
     */
    public String getTxHashOfPreviousTransactionOfInput(int indexInput) {
        if (containsBitcoinJTransaction() && !bitcoinJTransactionShouldBeUpdatedWithOnlineData()) {
            return transactionBitcoinJ.getInput(indexInput).getConnectedTransaction().getHashAsString();
        } else if (transactionFromCrawler != null) {
            return transactionFromCrawler.getInputs().get(indexInput).getPrev_hash();
        }
        return null;
    }

    /**
     * Returns the txHash of the following transaction to the given output or null if not available
     */
    public String getTxHashOfFollowingTransactionOfOutput(int indexOutput) {
        if (transactionFromCrawler != null) {
            return transactionFromCrawler.getOutputs().get(indexOutput).getSpent_by();
        } else if(containsBitcoinJTransaction() && !bitcoinJTransactionShouldBeUpdatedWithOnlineData()) {
            if(transactionBitcoinJ.getOutput(indexOutput).getSpentBy() != null) {
                return transactionBitcoinJ.getOutput(indexOutput).getSpentBy().getParentTransaction().getHashAsString();
            }
        }
        return null;
    }

    public String getTxHash() {
        if (containsBitcoinJTransaction()) {
            return transactionBitcoinJ.getHashAsString();
        } else {
            return transactionFromCrawler.getHash();
        }
    }


    public List<byte[]> getOutputScripts() {
        List<byte[]> results = new ArrayList<>();
        for (int i = 0; i < getOutputSize(); i++) {
            results.add(getOutputScript(i));
        }
        return results;
    }

    public TransactionOutput getOutputWithToken(int indexOfTokenToUse) {
        if (transactionBitcoinJ == null || indicesOfTokenOutputs == null || indicesOfTokenOutputs.isEmpty()) {
            return null;
        }
        return transactionBitcoinJ.getOutput(indicesOfTokenOutputs.get(indexOfTokenToUse));
    }

    public SendRequest getSendRequest() {
        if (transactionBitcoinJ == null) {
            return null;
        }
        return SendRequest.forTx(transactionBitcoinJ);
    }

    public List<Integer> getIndicesOfTokenOutputs() {
        return indicesOfTokenOutputs;
    }

    /**
     * Support only serialization for References which contain a BitcoinJ transaction.
     * TransactionReferences which contain no bitcoinJ transaction will not be requiring serialization.
     */
    public byte[] serializeTransaction() {
        if (transactionBitcoinJ != null) {
            return new TransactionSerializer().serializeTransaction(transactionBitcoinJ);
        }
        return null;
    }

    /**
     * Support only deserialization for References which contain a BitcoinJ transaction.
     * TransactionReferences which contain no bitcoinJ transaction will not be requiring serialization.
     */
    public static TransactionReference deserializeReference(NetworkParameters networkParameters, byte[] serializedData, List<Integer> indexOfTokenOutput) {
        if (serializedData == null) {
            return null;
        }
        Transaction transaction = new TransactionSerializer().deserializeTransaction(networkParameters, serializedData);
        return new TransactionReference(transaction, indexOfTokenOutput);
    }

    /**
     * Returns a reference that should be used for transaction structure verification only
     */
    public static TransactionReference getReferenceForVerificationOnly(Transaction transaction) {
        return new TransactionReference(transaction, Arrays.asList(-1));
    }

    /**
     * Returns true if the given HandoverData contains a BitcoinJ transaction reference.
     */
    public boolean containsBitcoinJTransaction() {
        return transactionBitcoinJ != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransactionReference that = (TransactionReference) o;

        if (transactionBitcoinJ != null ? !transactionBitcoinJ.equals(that.transactionBitcoinJ) : that.transactionBitcoinJ != null)
            return false;
        if (transactionFromCrawler != null ? !transactionFromCrawler.equals(that.transactionFromCrawler) : that.transactionFromCrawler != null)
            return false;
        return indicesOfTokenOutputs != null ? indicesOfTokenOutputs.equals(that.indicesOfTokenOutputs) : that.indicesOfTokenOutputs == null;

    }

    @Override
    public int hashCode() {
        int result = transactionBitcoinJ != null ? transactionBitcoinJ.hashCode() : 0;
        result = 31 * result + (transactionFromCrawler != null ? transactionFromCrawler.hashCode() : 0);
        result = 31 * result + (indicesOfTokenOutputs != null ? indicesOfTokenOutputs.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TransactionReference{" +
                "transactionBitcoinJ=" + transactionBitcoinJ +
                ", transactionFromCrawler=" + transactionFromCrawler +
                ", indicesOfTokenOutputs=" + indicesOfTokenOutputs +
                '}';
    }
}
