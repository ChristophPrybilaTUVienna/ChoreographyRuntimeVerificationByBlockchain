package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler.model;

import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;

import java.io.Serializable;
import java.util.List;

/**
 * TransactionOutput information collected by the crawling api.
 */
public class ParsedOutput implements Serializable {

    private String spent_by;

    private Integer value;

    private List<String> addresses;

    /**
     * Encoded in Hex
     */
    private String scriptAsHexString;

    private byte[] scriptBytes;

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public List<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }

    public String getScriptAsHexString() {
        return scriptAsHexString;
    }

    public byte[] getScriptBytes() {
        return scriptBytes;
    }

    public void setScriptBytes(byte[] scriptBytes) {
        this.scriptBytes = scriptBytes;
        if (scriptBytes == null) {
            scriptAsHexString = null;
            return;
        }
        scriptAsHexString = new RuntimeVerificationUtils().byteArrayToHexString(scriptBytes);
    }

    public void setScriptAsHexString(String scriptAsHexString) {
        this.scriptAsHexString = scriptAsHexString;
        if (scriptAsHexString == null) {
            scriptBytes = null;
            return;
        }
        scriptBytes = new RuntimeVerificationUtils().hexStringToByteArray(scriptAsHexString);
    }

    public String getSpent_by() {
        return spent_by;
    }

    public void setSpent_by(String spent_by) {
        this.spent_by = spent_by;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParsedOutput that = (ParsedOutput) o;

        if (spent_by != null ? !spent_by.equals(that.spent_by) : that.spent_by != null) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        if (addresses != null ? !addresses.equals(that.addresses) : that.addresses != null) return false;
        return scriptAsHexString != null ? scriptAsHexString.equals(that.scriptAsHexString) : that.scriptAsHexString == null;

    }

    @Override
    public int hashCode() {
        int result = spent_by != null ? spent_by.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (addresses != null ? addresses.hashCode() : 0);
        result = 31 * result + (scriptAsHexString != null ? scriptAsHexString.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ParsedOutput{" +
                "spent_by='" + spent_by + '\'' +
                ", value=" + value +
                ", addresses=" + addresses +
                ", scriptAsHexString='" + scriptAsHexString + '\'' +
                '}';
    }
}
