package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler;

import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedInput;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedOutput;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedTransaction;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.bitcoinj.core.Transaction;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Blockchain crawler using the REST API of https://api.blockcypher.com
 * Free plan as per 19.07.2016: "Classic requests, up to 3 requests/sec and 200 requests/hr"
 */
public abstract class BlockcypherBlockChainCrawler extends BlockChainCrawler {

    private String restURLToRetrieveAddress;
    private int speedThrottle;

    public BlockcypherBlockChainCrawler(String restURLToRetrieveTransactions, String restURLToRetrieveAddress, String token) {
        super(restURLToRetrieveTransactions + "?token=" + token, Arrays.asList("hash", "addresses", "total", "inputs", "outputs"));
        this.restURLToRetrieveAddress = restURLToRetrieveAddress + "?token=" + token;
        this.speedThrottle = new RuntimeVerificationUtils().readCrawlerThrottlingFromProperties();
    }

    @Override
    public boolean transactionIsUnconfirmed(ParsedTransaction parsedTransaction) {
        if (parsedTransaction.getBlockHeight() == null || parsedTransaction.getConfirmations() == null) {
            return true;
        }
        return parsedTransaction.getBlockHeight().equals(-1) &&
                parsedTransaction.getConfirmations().equals(0);
    }

    @Override
    protected ParsedTransaction convertToTransaction(JsonObject jsonObject, boolean simple, boolean logRequest) {
        if (jsonObject == null) {
            return null;
        }
        ParsedTransaction parsedTransaction = new ParsedTransaction();
        parsedTransaction.setHash(getFieldAsString(jsonObject, "hash"));
        parsedTransaction.setConfirmations(getFieldAsInteger(jsonObject, "confirmations"));
        parsedTransaction.setBlockHeight(getFieldAsInteger(jsonObject, "block_height"));
        parsedTransaction.setBlockHash(null); //not supported

        collectOutputs(jsonObject, parsedTransaction, parsedTransaction.getHash(), simple, logRequest);
        collectInputs(jsonObject, parsedTransaction);

        return parsedTransaction;
    }

    private void collectInputs(JsonObject jsonObject, ParsedTransaction parsedTransaction) {
        List<JsonObject> inputs = getObjectsFromArray(jsonObject, "inputs");
        List<ParsedInput> parsedInputs = new ArrayList<>();
        for (JsonObject input : inputs) {
            ParsedInput parsedInput = new ParsedInput();
            parsedInput.setScript(getFieldAsString(input, "script"));
            parsedInput.setAddresses(getEntriesOfPrimitiveArray(input.get("addresses")));
            parsedInput.setOutput_index(getFieldAsInteger(input, "output_index"));
            parsedInput.setOutput_value(getFieldAsInteger(input, "output_value"));
            parsedInput.setPrev_hash(getFieldAsString(input, "prev_hash"));
            parsedInputs.add(parsedInput);
        }
        parsedTransaction.setInputs(parsedInputs);
    }

    private void collectOutputs(JsonObject jsonObject, ParsedTransaction parsedTransaction, String txHash, boolean simple, boolean logRequest) {
        List<JsonObject> outputs = getObjectsFromArray(jsonObject, "outputs");
        List<ParsedOutput> parsedOutputs = new ArrayList<>();
        for (JsonObject output : outputs) {
            ParsedOutput parsedOutput = new ParsedOutput();
            parsedOutput.setAddresses(getEntriesOfPrimitiveArray(output.get("addresses")));
            conditionalSetSpentBy(parsedOutput, output, txHash, simple, logRequest);
            parsedOutput.setValue(getFieldAsInteger(output, "value"));
            parsedOutput.setScriptAsHexString(getFieldAsString(output, "script"));
            parsedOutputs.add(parsedOutput);
        }
        parsedTransaction.setOutputs(parsedOutputs);
    }

    /**
     * Tries to set the spentBy field directly from the json object or, if the following transaction is still
     * unconfirmed, via the address page.
     */
    private void conditionalSetSpentBy(ParsedOutput parsedOutput, JsonObject output, String txHash, boolean simple, boolean logRequest) {
        String spentBy = getSpentByDirectlyFromOutput(output);
        if (spentBy != null) {
            parsedOutput.setSpent_by(spentBy);
            return;
        }
        if (simple) {
            return;
        }
        if (parsedOutput.getAddresses() == null || parsedOutput.getAddresses().isEmpty()) {
            return;
        }
        //take the long way through address requests instead, this is required if the following tx was not yet confirmed
        List<JsonObject> txMetaInformationOfAddresses = null;
        try {
            txMetaInformationOfAddresses = getCompleteTxInformationForAllAddresses(parsedOutput.getAddresses(), logRequest);
        } catch (IOException e) {
            LoggerFactory.getLogger(BlockcypherBlockChainCrawler.class).warn("An error occured while setting the 'spent_by' field.", e);
            return;
        }
        for (JsonObject txRef : txMetaInformationOfAddresses) {
            String txHashInRef = getFieldAsString(txRef, "tx_hash");
            if (txHashInRef != null && txHashInRef.equals(txHash)) {
                parsedOutput.setSpent_by(getFieldAsString(txRef, "spent_by"));
            }
        }
    }

    /**
     * Tries to fetch the spentBy field information directly from the output.
     * This field is only set if the following transaction has already been confirmed.
     */
    private String getSpentByDirectlyFromOutput(JsonObject output) {
        return getFieldAsString(output, "spent_by");
    }

    @Override
    public ParsedTransaction getPublishedTransactionInformationOfHandoverTemplate(Transaction transactionTemplate) throws IOException {
        //it does not matter which input we are using
        String previousTxHash = transactionTemplate.getInput(0).getOutpoint().getHash().toString();
        int outputIndex = (int) transactionTemplate.getInput(0).getOutpoint().getIndex();
        ParsedTransaction previousTxFoundOnline = getTransactionInformation(previousTxHash);
        ParsedOutput connectedOutput = previousTxFoundOnline.getOutputs().get(outputIndex);
        //check if handoverTemplate was already confirmed online
        if (connectedOutput.getSpent_by() != null) {
            return getTransactionInformation(connectedOutput.getSpent_by());
        }
        //take the long way through address requests instead
        List<JsonObject> txMetaInformationOfAddresses = getCompleteTxInformationForAllAddresses(connectedOutput.getAddresses(), true);
        for (JsonObject txRef : txMetaInformationOfAddresses) {
            String txHashInRef = getFieldAsString(txRef, "tx_hash");
            if (txHashInRef != null && txHashInRef.equals(previousTxHash)) {
                //additional sanity check
                int outputIndexInRef = getFieldAsInteger(txRef, "tx_output_n");
                if (outputIndexInRef == outputIndex) {
                    String spentByInRef = getFieldAsString(txRef, "spent_by");
                    if (spentByInRef != null) {
                        return getTransactionInformation(spentByInRef);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public List<String> getTxHashesOfAddress(String address) throws IOException {
        List<String> txHashes = new ArrayList<>();
        for (JsonObject txRef : getCompleteTxInformationForAllAddresses(Arrays.asList(address), true)) {
            String txHashInRef = getFieldAsString(txRef, "tx_hash");
            if (txHashInRef != null) {
                txHashes.add(txHashInRef);
            }
        }
        return txHashes;
    }

    @Override
    protected void throttleRequestSpeed() {
        //throttle the speed according to the paid plan for blockcypher
        sleepForMS(speedThrottle);
    }

    @Override
    protected void throwExceptionOnError(CloseableHttpResponse response) throws RuntimeException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 429) {
            throw new RuntimeException("Exceeded Request limit of BlockCipher");
        }
    }

    @Override
    protected JsonObject applyAdditionalCheckAndFilter(JsonObject parsedObject) {
        return parsedObject;
    }

    /**
     * For a list of addresses, returns meta-information for all confirmed and unconfirmed tx that can be found online.
     */
    private List<JsonObject> getCompleteTxInformationForAllAddresses(List<String> addresses, boolean logRequest) throws IOException {
        List<JsonObject> foundMetaTx = new ArrayList<>();
        for (String address : addresses) {
            JsonObject addressInformation = getJsonObjectFromURL(String.format(restURLToRetrieveAddress, address), logRequest);
            foundMetaTx.addAll(getObjectsFromArray(addressInformation, "txrefs"));
            foundMetaTx.addAll(getObjectsFromArray(addressInformation, "unconfirmed_txrefs"));
        }
        return foundMetaTx;
    }
}
