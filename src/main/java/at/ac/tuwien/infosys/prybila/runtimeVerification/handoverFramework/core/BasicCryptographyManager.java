package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.RSAPrivateKey;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.RSAPublicKey;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;

/**
 * Provides basic cryptographic functionality like hashing, symmetrical encryption
 * and RSA-based asymmetrical signing.
 */
public class BasicCryptographyManager {

    private static final int lengthOfSymKey = 16; //16 byte String equals to 128 bit

    private SecureRandom random;

    private RSAPrivateKey privateKey;
    
    private RuntimeVerificationUtils utils;

    public BasicCryptographyManager(Identity ownIdentity) {
        this.privateKey = ownIdentity.getPrivateKey();
        random = new SecureRandom();
        utils = new RuntimeVerificationUtils();
    }

    /**
     * Returns the RSA signature of the given String as hex.
     */
    public String getSignatureOfData(byte[] data) {
        try {
            Signature dsa = Signature.getInstance("SHA256withRSA");
            dsa.initSign(privateKey.getKey());
            dsa.update(data);
            return utils.byteArrayToHexString(dsa.sign());
        } catch (Exception e) {
            throw new RuntimeVerificationException("Signing failed.", e);
        }
    }

    public boolean verifySignature(byte[] data, String signature, RSAPublicKey publicKeyOfSender) {
        try {
            Signature dsa = Signature.getInstance("SHA256withRSA");
            dsa.initVerify(publicKeyOfSender.getKey());
            dsa.update(data);
            return dsa.verify(utils.hexStringToByteArray(signature));
        } catch (Exception e) {
            throw new RuntimeVerificationException("Signing failed.", e);
        }
    }

    /**
     * Returns a random string with the given length.
     */
    public String getRandomSymmetricEncryptionKey() {
        return utils.generateRandomStringWithLength(random, lengthOfSymKey);
    }

    /**
     * Symmetrically encrypts the given data with the given key.
     */
    public byte[] symmetricallyEncryptData(byte[] data, String symKey) {
        try {
            Cipher c = Cipher.getInstance("AES");
            SecretKeySpec k =
                    new SecretKeySpec(symKey.getBytes(), "AES");
            c.init(Cipher.ENCRYPT_MODE, k);
            return c.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeVerificationException("Symmetrical encryption failed.", e);
        }
    }

    /**
     * Symmetrically encrypts the given data with the given key.
     */
    public byte[] symmetricallyDecryptData(byte[] encryptedData, String symKey) {
        try {
            Cipher c = Cipher.getInstance("AES");
            SecretKeySpec k =
                    new SecretKeySpec(symKey.getBytes(), "AES");
            c.init(Cipher.DECRYPT_MODE, k);
            return c.doFinal(encryptedData);
        } catch (Exception e) {
            throw new RuntimeVerificationException("Symmetrical decryption failed.", e);
        }
    }

    /**
     * Returns the hash of the given data in hex
     */
    public String hashData(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data);
            return utils.byteArrayToHexString(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeVerificationException("Hashing failed.", e);
        }
    }

}
