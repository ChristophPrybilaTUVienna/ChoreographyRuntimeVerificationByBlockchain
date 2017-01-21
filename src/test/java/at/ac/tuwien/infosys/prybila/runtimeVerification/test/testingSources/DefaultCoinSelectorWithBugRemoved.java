package at.ac.tuwien.infosys.prybila.runtimeVerification.test.testingSources;

import com.google.common.annotations.VisibleForTesting;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.CoinSelector;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultCoinSelectorWithBugRemoved implements CoinSelector {
    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        ArrayList<TransactionOutput> selected = new ArrayList<TransactionOutput>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<TransactionOutput>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        if (!target.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        for (TransactionOutput output : sortedOutputs) {
            if (total >= target.value) break;
            // Only pick chain-included transactions, or transactions that are ours and pending.
            if (!shouldSelect(output.getParentTransaction())) continue;
            selected.add(output);
            total += output.getValue().value;
        }
        // Total may be lower than target here, if the given candidates were insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(total), selected);
    }

    @VisibleForTesting
    static void sortOutputs(ArrayList<TransactionOutput> outputs) {
        Collections.sort(outputs, new Comparator<TransactionOutput>() {
            @Override
            public int compare(TransactionOutput a, TransactionOutput b) {
                int depth1 = a.getParentTransactionDepthInBlocks();
                int depth2 = b.getParentTransactionDepthInBlocks();
                Coin aValue = a.getValue();
                Coin bValue = b.getValue();
                BigInteger aCoinDepth = BigInteger.valueOf(aValue.value).multiply(BigInteger.valueOf(depth1));
                BigInteger bCoinDepth = BigInteger.valueOf(bValue.value).multiply(BigInteger.valueOf(depth2));
                int c1 = bCoinDepth.compareTo(aCoinDepth);
                if (c1 != 0) return c1;
                // The "coin*days" destroyed are equal, sort by value alone to get the lowest transaction size.
                int c2 = bValue.compareTo(aValue);
                if (c2 != 0) return c2;
                // They are entirely equivalent (possibly pending) so sort by hash to ensure a total ordering.
                BigInteger aHash = a.getParentTransactionHash().toBigInteger();
                BigInteger bHash = b.getParentTransactionHash().toBigInteger();
                return aHash.compareTo(bHash);
            }
        });
    }

    /**
     * Sub-classes can override this to just customize whether transactions are usable, but keep age sorting.
     */
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            return isSelectable(tx);
        }
        return true;
    }

    public static boolean isSelectable(Transaction tx) {
        // Only pick chain-included transactions, or transactions that are ours and pending.
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        if (confidence.getSource().equals(TransactionConfidence.Source.SELF)) {
            return type.equals(TransactionConfidence.ConfidenceType.BUILDING) || type.equals(TransactionConfidence.ConfidenceType.PENDING);
        }
        return type.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                type.equals(TransactionConfidence.ConfidenceType.PENDING) &&
                        (confidence.numBroadcastPeers() > 0 || tx.getParams().getId().equals(NetworkParameters.ID_REGTEST));
    }
}
