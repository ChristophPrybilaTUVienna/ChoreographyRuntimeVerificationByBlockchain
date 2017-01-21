package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.dataGeneration.RSAKeyGenerator;
import org.junit.Test;

import java.io.IOException;
import java.security.spec.InvalidKeySpecException;

public class GenerateRSAKeyFiles {

    private int agentCount = 12;
    private int keysize = 2048;

    @Test
    public void generateKeyPairsForSimulation() throws IOException, InvalidKeySpecException {
        RSAKeyGenerator rsaKeyGenerator = new RSAKeyGenerator();
        for (int i = 0; i < agentCount; i++) {
            rsaKeyGenerator.generateAndSaveKeyPair("Company_" + (char) (i + 65) + "_", keysize);
        }
    }

}
