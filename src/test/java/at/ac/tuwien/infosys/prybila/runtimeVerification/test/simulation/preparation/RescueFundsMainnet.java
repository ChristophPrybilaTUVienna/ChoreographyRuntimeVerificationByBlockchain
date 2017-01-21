package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;


import org.bitcoinj.params.MainNetParams;
import org.junit.Before;

public class RescueFundsMainnet extends RescueFundsAbstract {

    @Before
    public void before() {
        netToUse = "mainnet";
        networkParameters = MainNetParams.get();
        initPaths();
    }

}
