package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;


import org.bitcoinj.params.TestNet3Params;
import org.junit.Before;

public class RescueFundsTestnet extends RescueFundsAbstract {

    @Before
    public void before() {
        netToUse = "testnet";
        networkParameters = TestNet3Params.get();
        initPaths();
    }

}
