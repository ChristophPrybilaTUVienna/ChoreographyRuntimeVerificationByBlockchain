package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

/**
 * Enables the the complete de-serialization of a transaction.
 */
public class TransactionSerializer {

    /**
     * Serializes a Bitcoinj transaction.
     */
    public byte[] serializeTransaction(Transaction transaction) {
        return transaction.bitcoinSerialize();
    }

    /**
     * Deserialize a Bitcoinj transaction.
     */
    public Transaction deserializeTransaction(NetworkParameters networkParameters, byte[] transactionBytes) {
        return networkParameters.getDefaultSerializer().makeTransaction(transactionBytes);
    }

}
