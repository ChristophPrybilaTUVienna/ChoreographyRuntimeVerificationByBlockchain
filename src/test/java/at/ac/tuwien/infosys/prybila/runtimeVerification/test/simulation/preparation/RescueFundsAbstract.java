package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;

import at.ac.tuwien.infosys.prybila.runtimeVerification.test.testingSources.BitcoinConnectionWithTestMethods;
import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertTrue;

public class RescueFundsAbstract {

    private int walletCount = 12;
    private int firstSetIndex = 0;
    private int secondSetIndex = 4;
    private int thirdSetIndex = 8;

    private String pathToTestFilesStore;
    private String pathToTestFilesWallet;

    protected String netToUse;

    protected NetworkParameters networkParameters;

    private List<BitcoinConnectionWithTestMethods> wallets;

    private List<String> receiveAddresses;

    public void initPaths() {
        pathToTestFilesStore = "./testfiles/simulation/svp_" + netToUse + "_company_%s.store";
        pathToTestFilesWallet = "./testfiles/simulation/svp_" + netToUse + "_company_%s.wallet";
    }

    @Test
    public void rescueMoneyFromP2SHOutputWithoutDataHash() throws UnreadableWalletException, IOException, BlockStoreException, ExecutionException, InterruptedException, TimeoutException {
        openFourWallets(firstSetIndex);

        int outputIndexOfTransaction = 1;
        BitcoinConnectionWithTestMethods sendingWallet = wallets.get(2);
        BitcoinConnectionWithTestMethods receivingWallet = wallets.get(3);
        String addressToForwardP2PKH = receivingWallet.getNewReceivingAddressInBase58();
        NetworkParameters networkParameters = MainNetParams.get();
        String p2SHTxHash = "d449b82684d3d76975ed8e317469a419bcc3339e89be5eb6505b11504cca5a55";
        ECKey privateKey = ECKey.fromPrivate(new BigInteger("64812134667444779439669623575680409654716553456567386000765176488223571830640"));
        Address addressP2SHPoints = privateKey.toAddress(networkParameters);
        String originatingAddress = addressP2SHPoints.toBase58();
        Script redeemScript = BitcoinConnectionWithTestMethods.createRedeemScript(originatingAddress,networkParameters);
        //To test redeem Script
        Address.fromP2SHScript(networkParameters, new ScriptBuilder().createP2SHOutputScript(redeemScript)).toBase58();
        TransactionOutput p2shOutput = sendingWallet.getOutputToAddress(p2SHTxHash, outputIndexOfTransaction);

        sendingWallet.receiveAndForwardOutputFromP2SH(originatingAddress, p2shOutput, addressToForwardP2PKH, redeemScript, privateKey);

        closeAllWallets();
    }

    private void openFourWallets(int startIndex) throws UnreadableWalletException, IOException, BlockStoreException {
        wallets = new ArrayList<>();
        for (int i = startIndex; i < startIndex + 4 && i < walletCount; i++) {
            BitcoinConnectionWithTestMethods bitcoinConnection = new BitcoinConnectionWithTestMethods();
            String pathToStoreFile = String.format(pathToTestFilesStore, (char) (i + 65));
            String pathToWalletFile = String.format(pathToTestFilesWallet, (char) (i + 65));
            bitcoinConnection.openConnection(networkParameters, new File(pathToStoreFile), new File(pathToWalletFile), null);
            wallets.add(bitcoinConnection);
        }
    }

    private void closeAllWallets() throws IOException, BlockStoreException {
        if(wallets == null || wallets.isEmpty()) {
            return;
        }
        for(BitcoinConnectionWithTestMethods wallet : wallets) {
            wallet.closeConnection();
        }
    }

}
