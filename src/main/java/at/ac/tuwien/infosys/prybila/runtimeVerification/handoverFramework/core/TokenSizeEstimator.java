package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;

/**
 * Helper class to estimate the required token size for a given number of WF steps
 */
public class TokenSizeEstimator {

    private NetworkParameters networkParameters;

    private int feeSizeStartWF;

    private int feeSizeIntermediateWF;

    private int feeSizeEndWF;

    private int feeSizeTwoSplit;

    private int feeSizeTwoJoin;

    public TokenSizeEstimator(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        if (networkParameters instanceof TestNet3Params) {
            feeSizeStartWF = 13100;
            feeSizeIntermediateWF = 15500;
            feeSizeEndWF = 13850;
            feeSizeTwoSplit = 14350;
            feeSizeTwoJoin = 21500;
        } else if (networkParameters instanceof MainNetParams) {
            feeSizeStartWF = 13100;
            feeSizeIntermediateWF = 15500;
            feeSizeEndWF = 13850;
            feeSizeTwoSplit = 14350;
            feeSizeTwoJoin = 21500;
        } else {
            throw new RuntimeVerificationException("Unsupported networkParameter type");
        }
    }

    /**
     * Calculate an appropriate token size to be able to pay all occurring fees during the execution of a WF.
     */
    public Coin calculateAppropriateTokenSizeForWF(int numOfSteps, int numOfSplits) {
        long satoshis = feeSizeStartWF + feeSizeEndWF;

        //estimate the size increase for splits and joins
        satoshis += numOfSteps * feeSizeIntermediateWF;

        //use a rough estimate increase of 20% for a split and a join
        long extraSizeForASplitAndJoin = (long) (feeSizeTwoSplit + feeSizeTwoJoin);

        satoshis += extraSizeForASplitAndJoin * numOfSplits;

        // add 200% extra just to avoid running into dust exception
        satoshis = (satoshis * 3);

        return Coin.valueOf(satoshis);
    }

}
