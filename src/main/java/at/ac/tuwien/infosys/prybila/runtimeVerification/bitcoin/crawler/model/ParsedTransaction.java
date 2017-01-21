package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model;

import java.io.Serializable;
import java.util.List;

/**
 * Transaction information collected by the crawling api.
 */
public class ParsedTransaction implements Serializable {

    private String hash;

    private Integer confirmations;

    private Integer blockHeight;

    private String blockHash;

    private List<ParsedOutput> outputs;

    private List<ParsedInput> inputs;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Integer getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(Integer confirmations) {
        this.confirmations = confirmations;
    }

    public Integer getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(Integer blockHeight) {
        this.blockHeight = blockHeight;
    }

    public List<ParsedOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ParsedOutput> outputs) {
        this.outputs = outputs;
    }

    public List<ParsedInput> getInputs() {
        return inputs;
    }

    public void setInputs(List<ParsedInput> inputs) {
        this.inputs = inputs;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParsedTransaction that = (ParsedTransaction) o;

        if (hash != null ? !hash.equals(that.hash) : that.hash != null) return false;
        if (confirmations != null ? !confirmations.equals(that.confirmations) : that.confirmations != null)
            return false;
        if (blockHeight != null ? !blockHeight.equals(that.blockHeight) : that.blockHeight != null) return false;
        if (blockHash != null ? !blockHash.equals(that.blockHash) : that.blockHash != null) return false;
        if (outputs != null ? !outputs.equals(that.outputs) : that.outputs != null) return false;
        return inputs != null ? inputs.equals(that.inputs) : that.inputs == null;

    }

    @Override
    public int hashCode() {
        int result = hash != null ? hash.hashCode() : 0;
        result = 31 * result + (confirmations != null ? confirmations.hashCode() : 0);
        result = 31 * result + (blockHeight != null ? blockHeight.hashCode() : 0);
        result = 31 * result + (blockHash != null ? blockHash.hashCode() : 0);
        result = 31 * result + (outputs != null ? outputs.hashCode() : 0);
        result = 31 * result + (inputs != null ? inputs.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ParsedTransaction{" +
                "hash='" + hash + '\'' +
                ", confirmations=" + confirmations +
                ", blockHeight=" + blockHeight +
                ", blockHash='" + blockHash + '\'' +
                ", outputs=" + outputs +
                ", inputs=" + inputs +
                '}';
    }
}
