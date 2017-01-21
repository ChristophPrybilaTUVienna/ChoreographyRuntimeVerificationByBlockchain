package at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.RSAPrivateKey;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.RSAPublicKey;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;

import java.io.IOException;

/**
 * Bean to provide the application access to the operating company's identity information
 */
public class OwnIdentityProvider {

    private String companyName;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    public OwnIdentityProvider(String companyName, String pathToPublicKey, String pathToPrivateKey) {
        this.companyName = companyName;
        RuntimeVerificationUtils utils = new RuntimeVerificationUtils();
        if (pathToPublicKey != null) {
            try {
                publicKey = new RSAPublicKey(utils.readPublicKeyFromFile(pathToPublicKey));
            } catch (IOException e) {
                throw new RuntimeVerificationException(e);
            }
        }
        if (pathToPrivateKey != null) {
            try {
                privateKey = new RSAPrivateKey(utils.readPrivateKeyFromFile(pathToPrivateKey));
            } catch (IOException e) {
                throw new RuntimeVerificationException(e);
            }
        }
    }

    public Identity getOwnIdentity() {
        return new Identity(companyName, publicKey, privateKey, true);
    }

    public Identity getOwnIdentityToShareWithPartner() {
        return new Identity(companyName, publicKey, null, false);
    }

    public Identity getOwnIdentityToShareWithPartner(byte[] bitcoinPublicKey) {
        Identity ownIdentity = getOwnIdentityToShareWithPartner();
        ownIdentity.setBitcoinPublicKey(bitcoinPublicKey);
        return ownIdentity;
    }

}
