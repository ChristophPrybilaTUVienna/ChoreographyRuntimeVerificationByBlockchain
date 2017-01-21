package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Wrapper class representing the identity information about a choreography participant for a specific handover
 * The privateKey can only be filled for the own identity representation.
 */
public class Identity implements Serializable {

    private static final String unknownCompanyName = "Unknown_Company";

    /**
     * Name of the company
     */
    private String companyName;

    /**
     * RSA public key to establish a secure connection
     */
    private RSAPublicKey publicKey;

    /**
     * RSA private key to establish a secure connection
     */
    private RSAPrivateKey privateKey;

    /**
     * Signature received during handover-init to confirm the possession of the Bitcoin private key.
     */
    private byte[] signatureOfBitcoinPublicKey;

    /**
     * Bitcoin public key received during handover-init.
     */
    private byte[] bitcoinPublicKey;

    /**
     * Flag indicating if the identity instance represents us during the handover.
     */
    private boolean isMe;

    public Identity(String companyName, RSAPublicKey publicKey, RSAPrivateKey privateKey, boolean isMe) {
        this.companyName = companyName;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.isMe = isMe;
    }

    public String getCompanyName() {
        return companyName;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public boolean isMe() {
        return isMe;
    }

    public byte[] getSignatureOfBitcoinPublicKey() {
        return signatureOfBitcoinPublicKey;
    }

    public void setSignatureOfBitcoinPublicKey(byte[] signatureOfBitcoinPublicKey) {
        this.signatureOfBitcoinPublicKey = signatureOfBitcoinPublicKey;
    }

    public byte[] getBitcoinPublicKey() {
        return bitcoinPublicKey;
    }

    public void setBitcoinPublicKey(byte[] bitcoinPublicKey) {
        this.bitcoinPublicKey = bitcoinPublicKey;
    }

    public boolean relaxedEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Identity identity = (Identity) o;

        if (isMe != identity.isMe) return false;
        if (companyName != null ? !companyName.equals(identity.companyName) : identity.companyName != null)
            return false;
        if (publicKey != null ? !publicKey.equals(identity.publicKey) : identity.publicKey != null) return false;
        return privateKey != null ? privateKey.equals(identity.privateKey) : identity.privateKey == null;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Identity identity = (Identity) o;

        if (isMe != identity.isMe) return false;
        if (companyName != null ? !companyName.equals(identity.companyName) : identity.companyName != null)
            return false;
        if (publicKey != null ? !publicKey.equals(identity.publicKey) : identity.publicKey != null) return false;
        if (privateKey != null ? !privateKey.equals(identity.privateKey) : identity.privateKey != null) return false;
        if (!Arrays.equals(signatureOfBitcoinPublicKey, identity.signatureOfBitcoinPublicKey)) return false;
        return Arrays.equals(bitcoinPublicKey, identity.bitcoinPublicKey);

    }

    @Override
    public int hashCode() {
        int result = companyName != null ? companyName.hashCode() : 0;
        result = 31 * result + (publicKey != null ? publicKey.hashCode() : 0);
        result = 31 * result + (privateKey != null ? privateKey.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(signatureOfBitcoinPublicKey);
        result = 31 * result + Arrays.hashCode(bitcoinPublicKey);
        result = 31 * result + (isMe ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Identity{" +
                "isMe=" + isMe +
                ", companyName='" + companyName + '\'' +
                '}';
    }

    public static Identity getUnknownCompanyIdentity() {
        return new Identity(unknownCompanyName, null, null, false);
    }
}
