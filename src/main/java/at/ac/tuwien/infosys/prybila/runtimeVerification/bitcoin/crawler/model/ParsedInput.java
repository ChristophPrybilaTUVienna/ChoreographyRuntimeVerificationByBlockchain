package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model;

import java.io.Serializable;
import java.util.List;

/**
 * TransactionInput information collected by the crawling api.
 */
public class ParsedInput implements Serializable {

    private String script;

    private Integer output_index;

    private String prev_hash;

    private Integer output_value;

    private List<String> addresses;

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public Integer getOutput_index() {
        return output_index;
    }

    public void setOutput_index(Integer output_index) {
        this.output_index = output_index;
    }

    public String getPrev_hash() {
        return prev_hash;
    }

    public void setPrev_hash(String prev_hash) {
        this.prev_hash = prev_hash;
    }

    public Integer getOutput_value() {
        return output_value;
    }

    public void setOutput_value(Integer output_value) {
        this.output_value = output_value;
    }

    public List<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParsedInput that = (ParsedInput) o;

        if (script != null ? !script.equals(that.script) : that.script != null) return false;
        if (output_index != null ? !output_index.equals(that.output_index) : that.output_index != null) return false;
        if (prev_hash != null ? !prev_hash.equals(that.prev_hash) : that.prev_hash != null) return false;
        if (output_value != null ? !output_value.equals(that.output_value) : that.output_value != null) return false;
        return addresses != null ? addresses.equals(that.addresses) : that.addresses == null;

    }

    @Override
    public int hashCode() {
        int result = script != null ? script.hashCode() : 0;
        result = 31 * result + (output_index != null ? output_index.hashCode() : 0);
        result = 31 * result + (prev_hash != null ? prev_hash.hashCode() : 0);
        result = 31 * result + (output_value != null ? output_value.hashCode() : 0);
        result = 31 * result + (addresses != null ? addresses.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ParsedInput{" +
                "script='" + script + '\'' +
                ", output_index=" + output_index +
                ", prev_hash='" + prev_hash + '\'' +
                ", output_value=" + output_value +
                ", addresses=" + addresses +
                '}';
    }
}
