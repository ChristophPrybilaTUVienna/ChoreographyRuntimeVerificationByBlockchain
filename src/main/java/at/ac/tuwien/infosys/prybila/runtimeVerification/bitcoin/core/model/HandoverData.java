package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedTransaction;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Contains all data of a specific workflow handover except for the identities of the participants.
 */
public class HandoverData implements Serializable {

    private WorkflowHandoverData workflowHandoverData;

    private TransactionReference transactionReference;

    /**
     * Keys to redeem the p2sh outputs of the given transaction.
     * A handover-split can have multiple p2sh outputs, all other handover-types have only at most one p2sh outputs.
     * The index of the key in the list corresponds to the p2sh output of the transaction.
     */
    private List<ECKey> keyToRedeemP2SHOutput;

    /**
     * DataLists to redeem the p2sh outputs of the given transaction.
     * A handover-split can have multiple p2sh outputs, all other handover-types have only at most one p2sh outputs.
     * The index of the dataList in the list corresponds to the p2sh output of the transaction.
     */
    private List<List<byte[]>> dataToRedeemP2SHOutput;

    /**
     * Flag indicating if the execution framework was the sending part during the handover.
     */
    private boolean sender;

    /**
     * Flag indicating that the data contained in this instance is still unfinished.
     */
    private boolean template;

    /**
     * Needed for deserialization
     */
    private NetworkParameters networkParameters;

    public HandoverData(
            WorkflowHandoverData workflowHandoverData,
            TransactionReference transactionReference,
            List<ECKey> keyToRedeemP2SHOutput,
            List<List<byte[]>> dataToRedeemP2SHOutput,
            boolean sender,
            boolean template,
            NetworkParameters networkParameters) {
        RuntimeVerificationUtils utils = new RuntimeVerificationUtils();
        utils.notNull(workflowHandoverData);
        utils.notNull(networkParameters);
        this.workflowHandoverData = workflowHandoverData;
        this.transactionReference = transactionReference;
        this.keyToRedeemP2SHOutput = keyToRedeemP2SHOutput;
        this.sender = sender;
        this.template = template;
        this.dataToRedeemP2SHOutput = dataToRedeemP2SHOutput;
        this.networkParameters = networkParameters;
        //sanity checks
        List<Integer> tokenOutputIndexList = null;
        if (transactionReference != null) {
            tokenOutputIndexList = transactionReference.getIndicesOfTokenOutputs();
        }
        if (keyToRedeemP2SHOutput != null) {
            if (dataToRedeemP2SHOutput != null &&
                    keyToRedeemP2SHOutput.size() != dataToRedeemP2SHOutput.size()) {
                throw new RuntimeVerificationException("KeyList and dataToRedeemP2SHList must be of same size.");
            }
            if (tokenOutputIndexList != null &&
                    keyToRedeemP2SHOutput.size() != tokenOutputIndexList.size()) {
                throw new RuntimeVerificationException("KeyList and tokenOutputIndexList must be of same size.");
            }
        } else if (dataToRedeemP2SHOutput != null) {
            if (keyToRedeemP2SHOutput != null &&
                    dataToRedeemP2SHOutput.size() != keyToRedeemP2SHOutput.size()) {
                throw new RuntimeVerificationException("KeyList and dataToRedeemP2SHList must be of same size.");
            }
            if (tokenOutputIndexList != null &&
                    dataToRedeemP2SHOutput.size() != tokenOutputIndexList.size()) {
                throw new RuntimeVerificationException("DataToRedeemP2SHList and tokenOutputIndexList must be of same size.");
            }
        }
    }

    public HandoverData cloneWithUpdatedTransactionReference(TransactionReference transactionReference) {
        return new HandoverData(workflowHandoverData, transactionReference, keyToRedeemP2SHOutput, dataToRedeemP2SHOutput, sender, template, networkParameters);
    }

    public WorkflowHandoverData getWorkflowHandoverData() {
        return workflowHandoverData;
    }

    public TransactionReference getTransactionReference() {
        return transactionReference;
    }

    public List<byte[]> getDataToRedeemP2SHOutput(int i) {
        if (dataToRedeemP2SHOutput == null || i >= dataToRedeemP2SHOutput.size()) {
            return null;
        }
        return dataToRedeemP2SHOutput.get(i);
    }

    public ECKey getKeyToRedeemP2SHOutput(int i) {
        if (keyToRedeemP2SHOutput == null || i >= keyToRedeemP2SHOutput.size()) {
            return null;
        }
        return keyToRedeemP2SHOutput.get(i);
    }

    public boolean isSender() {
        return sender;
    }

    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    /**
     * Returns true if the given HandoverData contains a BitcoinJ transaction reference.
     */
    public boolean containsBitcoinJTransaction() {
        if (transactionReference == null) {
            return false;
        }
        return transactionReference.containsBitcoinJTransaction();
    }

    @Override
    public String toString() {
        return "HandoverData{" +
                "template=" + template +
                ", sender=" + sender +
                ", transactionReference=" + transactionReference +
                ", workflowHandoverData=" + workflowHandoverData +
                '}';
    }

    private void writeObject(ObjectOutputStream o)
            throws IOException {
        o.writeObject(workflowHandoverData);
        o.writeObject(networkParameters.getId());
        if (transactionReference != null) {
            o.writeObject(transactionReference.getIndicesOfTokenOutputs());
            o.writeObject(transactionReference.serializeTransaction());
            o.writeObject(transactionReference.getParsedTransaction());
        } else {
            o.writeObject(null);
            o.writeObject(null);
            o.writeObject(null);
        }
        if (keyToRedeemP2SHOutput == null) {
            o.writeObject(null);
        } else {
            o.writeObject(keyToRedeemP2SHOutput.size());
            for (ECKey key : keyToRedeemP2SHOutput) {
                if (key != null) {
                    if (key.isPubKeyOnly()) {
                        o.writeObject(null);
                    } else {
                        o.writeObject(key.getPrivKeyBytes());
                    }
                    o.writeObject(key.getPubKey());
                } else {
                    o.writeObject(null);
                    o.writeObject(null);
                }
            }
        }
        o.writeObject(dataToRedeemP2SHOutput);
        o.writeObject(sender);
        o.writeObject(template);
    }

    private void readObject(ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        workflowHandoverData = (WorkflowHandoverData) o.readObject();
        String networkParamsId = (String) o.readObject();
        networkParameters = NetworkParameters.fromID(networkParamsId);
        List<Integer> indexOfOutputTokenOfTransaction = (List<Integer>) o.readObject();
        byte[] bytesOfTransaction = (byte[]) o.readObject();
        ParsedTransaction parsedTransaction = (ParsedTransaction) o.readObject();
        transactionReference = TransactionReference.deserializeReference(networkParameters, bytesOfTransaction, indexOfOutputTokenOfTransaction);
        if(parsedTransaction != null) {
            if(transactionReference == null) {
                transactionReference = new TransactionReference(parsedTransaction, indexOfOutputTokenOfTransaction);
            } else {
                transactionReference.setTransactionFromCrawler(parsedTransaction);
            }
        }
        Integer keyListSize = (Integer) o.readObject();
        if (keyListSize == null) {
            keyToRedeemP2SHOutput = null;
        } else {
            keyToRedeemP2SHOutput = new ArrayList<>();
            for (int i = 0; i < keyListSize; i++) {
                byte[] privKey = (byte[]) o.readObject();
                byte[] pubKey = (byte[]) o.readObject();
                ECKey key;
                if (privKey != null && pubKey != null) {
                    key = ECKey.fromPrivateAndPrecalculatedPublic(privKey, pubKey);
                } else if (privKey == null && pubKey != null) {
                    key = ECKey.fromPublicOnly(pubKey);
                } else if (privKey != null && pubKey == null) {
                    key = ECKey.fromPrivate(privKey);
                } else {
                    key = null;
                }
                keyToRedeemP2SHOutput.add(key);
            }
        }
        dataToRedeemP2SHOutput = (List<List<byte[]>>) o.readObject();
        sender = (boolean) o.readObject();
        template = (boolean) o.readObject();
        validate();
    }

    private void validate() {
        //further validation required?
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HandoverData that = (HandoverData) o;

        if (workflowHandoverData != null ? !workflowHandoverData.equals(that.workflowHandoverData) : that.workflowHandoverData != null)
            return false;
        if (transactionReference != null ? !transactionReference.equals(that.transactionReference) : that.transactionReference != null)
            return false;
        if (keyToRedeemP2SHOutput == null && that.keyToRedeemP2SHOutput != null) {
            return false;
        }
        if (keyToRedeemP2SHOutput != null && that.keyToRedeemP2SHOutput == null) {
            return false;
        }
        if (keyToRedeemP2SHOutput != null && that.keyToRedeemP2SHOutput != null) {
            if (keyToRedeemP2SHOutput.size() != that.keyToRedeemP2SHOutput.size()) {
                return false;
            }
            for (int i = 0; i < keyToRedeemP2SHOutput.size(); i++) {
                if (!keysAreEqual(keyToRedeemP2SHOutput.get(i), that.keyToRedeemP2SHOutput.get(i))) {
                    return false;
                }
            }
        }
        if (sender != that.sender) return false;
        if (template != that.template) return false;
        if (dataToRedeemP2SHOutput == null && that.dataToRedeemP2SHOutput != null) {
            return false;
        }
        if (dataToRedeemP2SHOutput != null && that.dataToRedeemP2SHOutput == null) {
            return false;
        }
        if (dataToRedeemP2SHOutput != null) {
            if (dataToRedeemP2SHOutput.size() != that.dataToRedeemP2SHOutput.size()) {
                return false;
            }
            for (int i = 0; i < dataToRedeemP2SHOutput.size(); i++) {
                List<byte[]> dataForOutput = dataToRedeemP2SHOutput.get(i);
                List<byte[]> otherDataForOutput = that.dataToRedeemP2SHOutput.get(i);
                if (dataForOutput.size() != otherDataForOutput.size()) {
                    return false;
                }
                for (int f = 0; f < dataForOutput.size(); f++) {
                    byte[] arrayToCompare = dataForOutput.get(f);
                    byte[] otherArrayToCompare = otherDataForOutput.get(f);
                    if (!Arrays.equals(arrayToCompare, otherArrayToCompare)) {
                        return false;
                    }
                }
            }
        }
        return networkParameters != null ? networkParameters.equals(that.networkParameters) : that.networkParameters == null;

    }

    /**
     * Assumes both keys are not null
     */
    private boolean keysAreEqual(ECKey thisKey, ECKey thatKey) {
        if (thisKey.isPubKeyOnly() != thatKey.isPubKeyOnly()) {
            return false;
        }
        boolean pubKeysAreEqual = Arrays.equals(thisKey.getPubKey(), thatKey.getPubKey());
        if (thisKey.isPubKeyOnly()) {
            return pubKeysAreEqual;
        }
        boolean privKeysAreEqual = thisKey.getPrivKey().equals(thatKey.getPrivKey());
        return pubKeysAreEqual && privKeysAreEqual;
    }

    @Override
    public int hashCode() {
        int result = workflowHandoverData != null ? workflowHandoverData.hashCode() : 0;
        result = 31 * result + (transactionReference != null ? transactionReference.hashCode() : 0);
        result = 31 * result + (keyToRedeemP2SHOutput != null ? keyToRedeemP2SHOutput.hashCode() : 0);
        result = 31 * result + (dataToRedeemP2SHOutput != null ? dataToRedeemP2SHOutput.hashCode() : 0);
        result = 31 * result + (networkParameters != null ? networkParameters.hashCode() : 0);
        result = 31 * result + (sender ? 1 : 0);
        result = 31 * result + (template ? 1 : 0);
        return result;
    }

    public void setDataToRedeemP2SHOutput(List<List<byte[]>> dataToRedeemP2SHOutput) {
        this.dataToRedeemP2SHOutput = dataToRedeemP2SHOutput;
    }
}
