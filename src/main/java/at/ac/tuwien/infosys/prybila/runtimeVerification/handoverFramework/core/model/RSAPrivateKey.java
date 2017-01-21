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
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;

/**
 * Wrapper class representing a RSA key.
 */
public class RSAPrivateKey implements Serializable {

    private PrivateKey key;

    public RSAPrivateKey(PrivateKey key) {
        this.key = key;
        new RuntimeVerificationUtils().notNull(key);
    }

    public PrivateKey getKey() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) o;

        return key != null ? key.equals(rsaPrivateKey.key) : rsaPrivateKey.key == null;

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
            RSAPrivateKeySpec priv = fact.getKeySpec(key,
                    RSAPrivateKeySpec.class);
            o.writeObject(priv.getModulus());
            o.writeObject(priv.getPrivateExponent());
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
        RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(m, e);
        KeyFactory fact;
        try {
            fact = KeyFactory.getInstance("RSA");
            key = fact.generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException e1) {
            throw new RuntimeVerificationException(e1);
        } catch (InvalidKeySpecException e1) {
            throw new RuntimeVerificationException(e1);
        }
    }
}
