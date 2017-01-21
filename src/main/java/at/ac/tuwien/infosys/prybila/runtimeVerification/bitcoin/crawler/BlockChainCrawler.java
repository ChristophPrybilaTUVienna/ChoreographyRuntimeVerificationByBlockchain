package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler;


import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model.ParsedTransaction;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import com.google.gson.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Abstract interfaces and utility methods for the needed BlockChain crawling functionality.
 * This crawler should be used to collect information about broadcast transactions and given WF instances.
 */
public abstract class BlockChainCrawler {

    private String restURLToRetrieveTransactions;
    protected Logger logger;
    protected Map<String, JsonObject> cache;
    protected boolean useCache;

    /**
     * Instead of matching the complete schema of a transaction json, the existence of this list`s element will be confirmed.
     * If all elements are present, the json object will be considered a transaction object.
     */
    private List<String> topLevelElementsConfirmingThatObjectIsATransaction;

    public BlockChainCrawler(
            String restURLToRetrieveTransactions,
            List<String> topLevelElementsConfirmingThatObjectIsATransaction) {
        RuntimeVerificationUtils utils = new RuntimeVerificationUtils();
        cache = new HashMap<>();
        useCache = false;
        utils.notNull(restURLToRetrieveTransactions);
        utils.notNull(topLevelElementsConfirmingThatObjectIsATransaction);
        utils.notEmpty(topLevelElementsConfirmingThatObjectIsATransaction);
        this.restURLToRetrieveTransactions = restURLToRetrieveTransactions;
        this.topLevelElementsConfirmingThatObjectIsATransaction = topLevelElementsConfirmingThatObjectIsATransaction;
    }

    /**
     * Returns true if the given transaction was found online at the API´s server and is still unconfirmed.
     */
    public abstract boolean transactionIsUnconfirmed(ParsedTransaction parsedTransaction);

    /**
     * Convert the given JSON object to a ParsedTransaction.
     */
    protected abstract ParsedTransaction convertToTransaction(JsonObject jsonObject, boolean simple, boolean logRequest);

    /**
     * Returns for one transactionTemplate the updated transaction information found online.
     * The txHash of the transaction found online is expected to be different to the txHash of the transactionTemplate.
     * Expects an unfinished handover transaction template as a parameter.
     * Queries one previous transaction through an arbitrary input.
     * On the previous transaction, select the output that points to the transaction template.
     * Returns the actual published transaction to this output.
     */
    public abstract ParsedTransaction getPublishedTransactionInformationOfHandoverTemplate(Transaction transactionTemplate) throws IOException;

    /**
     * Returns the online-information about the transaction with the given hash as JSON object.
     * Throws a RuntimeVerificationException if the transaction was not found.
     */
    public ParsedTransaction getTransactionInformation(String txHash) throws IOException {
        return getTransactionInformation(txHash, false, true);
    }

    /**
     * Returns the online-information about the transaction with the given hash as JSON object.
     * Throws a RuntimeVerificationException if the transaction was not found.
     * If the simple flag is set to true, no child elements to a transaction will be fetched.
     * This can be used to limit the request rate.
     * Accepts a flag if the resulting REST requests should be logged.
     */
    public ParsedTransaction getTransactionInformation(String txHash, boolean simple, boolean logRequest) throws IOException {
        JsonObject jsonObject = getTransactionInformationAsJson(txHash, logRequest);
        if (jsonObject == null) {
            throw new RuntimeVerificationException("The given transaction " + txHash + " was not found by the crawler.");
        }
        return convertToTransaction(jsonObject, simple, logRequest);
    }

    /**
     * Returns the online-information about the transaction with the given hash as JSON object or null.
     * Accepts a flag if the resulting REST requests should be logged.
     */
    protected JsonObject getTransactionInformationAsJson(String txHash, boolean logRequest) throws IOException {
        JsonObject jsonObject = getJsonObjectFromURL(String.format(restURLToRetrieveTransactions, txHash), logRequest);
        if (jsonObject == null) {
            jsonObject = new JsonObject();
        }
        if (!isTransaction(jsonObject)) {
            return null;
        }
        return jsonObject;
    }

    /**
     * Returns the online-information about the transaction with the given hash as JSON object or null.
     * Logs the resulting REST requests.
     */
    protected JsonObject getTransactionInformationAsJson(String txHash) throws IOException {
        return getTransactionInformationAsJson(txHash, true);
    }


    private boolean isTransaction(JsonObject txObject) {
        for (String toplevelElement : topLevelElementsConfirmingThatObjectIsATransaction) {
            if (txObject.get(toplevelElement) == null) {
                return false;
            }
        }
        return true;
    }

    protected JsonObject getJsonObjectFromURL(String url, boolean logRequest) throws IOException {
        if (useCache && cache.containsKey(url)) {
            return cache.get(url);
        }
        String txJsonAsString = getContentOfUrl(url, logRequest);
        try {
            JsonParser parser = new JsonParser();
            JsonObject parsedObject = parser.parse(txJsonAsString).getAsJsonObject();
            parsedObject = applyAdditionalCheckAndFilter(parsedObject);
            if (useCache) {
                cache.put(url, parsedObject);
            }
            return parsedObject;
        } catch (Exception e) {
            //response was not a valid JSON object
        }
        return null;
    }

    /**
     * Returns all confirmed and unconfirmed transactions related to this address.
     */
    public abstract List<String> getTxHashesOfAddress(String address) throws IOException;

    /**
     * Applies an additional check if the json response is well formed.
     * Returns the actual payload of the json object or null.
     */
    protected abstract JsonObject applyAdditionalCheckAndFilter(JsonObject parsedObject);

    /**
     * Expects an jsonArray of objects.
     * Returns the objects as a list.
     */
    protected List<JsonObject> getObjectsFromArray(JsonObject parent, String arrayName) {
        List<JsonObject> objectsOfArray = new ArrayList<>();
        if (parent != null &&
                arrayName != null &&
                parent.has(arrayName) &&
                parent.get(arrayName).isJsonArray()) {
            JsonArray array = parent.getAsJsonArray(arrayName);
            Iterator<JsonElement> iterator = array.iterator();
            while (iterator.hasNext()) {
                JsonElement element = iterator.next();
                if (!element.isJsonObject()) {
                    continue;
                }
                objectsOfArray.add(element.getAsJsonObject());
            }
        }
        return objectsOfArray;
    }

    /**
     * Expects an jsonArray of primitive string elements.
     * Returns the primitive string elements as list.
     * Returns null if something is not correct.
     */
    protected List<String> getEntriesOfPrimitiveArray(JsonElement arrayAsElement) {
        if (arrayAsElement != null && arrayAsElement.isJsonArray()) {
            JsonArray array = arrayAsElement.getAsJsonArray();
            if (array.size() > 0) {
                List<String> primitiveStrings = new ArrayList<>();
                Iterator<JsonElement> iterator = array.iterator();
                while (iterator.hasNext()) {
                    JsonElement element = iterator.next();
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        primitiveStrings.add(element.getAsJsonPrimitive().getAsString());
                    }
                }
                return primitiveStrings;
            }
        }
        return null;
    }

    /**
     * Simple method to submit GET requests and return the result.
     * Accepts a flag if the request should be logged.
     */
    protected String getContentOfUrl(String url, boolean logRequest) throws IOException {
        if (logRequest) {
            logger.debug("Submitting get request on " + url);
        }
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        CloseableHttpResponse response1 = httpclient.execute(httpGet);
        throwExceptionOnError(response1);
        String body;
        try {
            HttpEntity entity = response1.getEntity();
            body = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
        } finally {
            response1.close();
        }
        throttleRequestSpeed();
        if (logRequest) {
            logger.debug("Returning result of request on " + url);
        }
        return body;
    }

    /**
     * Returns true if the response is an error
     */
    protected abstract void throwExceptionOnError(CloseableHttpResponse response) throws RuntimeException;

    /**
     * Throttle the request speed if necessary
     */
    protected abstract void throttleRequestSpeed();

    protected void sleepForMS(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the value of the given field from the given object or null.
     */
    protected Integer getFieldAsInteger(JsonObject jsonObject, String fieldName) {
        if (jsonObject == null || fieldName == null) {
            return null;
        }
        return getPrimitiveIntegerOrNull(jsonObject.get(fieldName));
    }

    /**
     * Returns the value of the given field from the given object or null.
     */
    protected String getFieldAsString(JsonObject jsonObject, String fieldName) {
        if (jsonObject == null || fieldName == null) {
            return null;
        }
        return getPrimitiveStringOrNull(jsonObject.get(fieldName));
    }

    /**
     * Returns the given JSONElement as value of a primitive field of the type string
     */
    private String getPrimitiveStringOrNull(JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
        if (!jsonPrimitive.isString()) {
            return null;
        }
        return jsonPrimitive.getAsString();
    }

    /**
     * Returns the given JSONElement as value of a primitive field of the type Integer
     */
    private Integer getPrimitiveIntegerOrNull(JsonElement jsonElement) {
        if (jsonElement == null || !jsonElement.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
        if (!jsonPrimitive.isNumber()) {
            return null;
        }
        Integer value = null;
        try {
            value = jsonPrimitive.getAsInt();
        } catch (Exception e) {
            //ignore
        }
        return value;
    }

    public void activateCache() {
        useCache = true;
        cache.clear();
    }

    public void deactivateCache() {
        useCache = false;
    }
}
