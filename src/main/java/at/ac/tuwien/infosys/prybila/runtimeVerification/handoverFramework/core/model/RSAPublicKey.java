package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model;

import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

/**
 * Wrapper class representing a RSA key.
 */
public class RSAPublicKey implements Serializable {

    private PublicKey key;

    public RSAPublicKey(PublicKey key) {
        this.key = key;
        new RuntimeVerificationUtils().notNull(key);
    }

    public PublicKey getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSAPublicKey rsaPublicKey = (RSAPublicKey) o;

        return key != null ? key.equals(rsaPublicKey.key) : rsaPublicKey.key == null;

    }

    @Override
    public int hashCode() {
        return key != null ? key.hashCode() : 0;
    }

    private void writeObject(ObjectOutputStream o)
            throws IOException {
        KeyFactory fact;
        try {
            fact = KeyFactory.getInstance("RSA");
            RSAPublicKeySpec pub = fact.getKeySpec(key,
                    RSAPublicKeySpec.class);
            o.writeObject(pub.getModulus());
            o.writeObject(pub.getPublicExponent());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeVerificationException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeVerificationException(e);
        }
    }

    private void readObject(ObjectInputStream o)
            throws IOException, ClassNotFoundException {
        BigInteger m = (BigInteger) o.readObject();
        BigInteger e = (BigInteger) o.readObject();
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(m, e);
        KeyFactory fact;
        try {
            fact = KeyFactory.getInstance("RSA");
            key = fact.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException e1) {
            throw new RuntimeVerificationException(e1);
        } catch (InvalidKeySpecException e1) {
            throw new RuntimeVerificationException(e1);
        }
    }
}
