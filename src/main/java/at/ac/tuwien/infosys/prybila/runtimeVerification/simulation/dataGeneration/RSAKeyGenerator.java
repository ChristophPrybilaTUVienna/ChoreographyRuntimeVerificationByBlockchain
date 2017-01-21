package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.dataGeneration;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * Generates and saves RSA key-pairs for the simulation.
 */
public class RSAKeyGenerator {

    public void generateAndSaveKeyPair(String prefix, int keyLength) throws IOException, InvalidKeySpecException {
        KeyPairGenerator kpg = null;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        }
        kpg.initialize(keyLength);
        KeyPair kp = kpg.genKeyPair();
        saveKeyToFile(prefix, kp);
    }

    private void saveKeyToFile(String prefix, KeyPair kp) throws IOException, InvalidKeySpecException {
        KeyFactory fact;
        try {
            fact = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        }
        RSAPublicKeySpec pub = fact.getKeySpec(kp.getPublic(),
                RSAPublicKeySpec.class);
        RSAPrivateKeySpec priv = fact.getKeySpec(kp.getPrivate(),
                RSAPrivateKeySpec.class);

        saveToKeySepcsToFile(prefix + "public.key", pub.getModulus(),
                pub.getPublicExponent());
        saveToKeySepcsToFile(prefix + "private.key", priv.getModulus(),
                priv.getPrivateExponent());
    }

    public void saveToKeySepcsToFile(String fileName,
                                     BigInteger mod, BigInteger exp) throws IOException {
        ObjectOutputStream oout = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(fileName)));
        try {
            oout.writeObject(mod);
            oout.writeObject(exp);
        } catch (Exception e) {
            throw new IOException("Unexpected error", e);
        } finally {
            oout.close();
        }
    }

}
