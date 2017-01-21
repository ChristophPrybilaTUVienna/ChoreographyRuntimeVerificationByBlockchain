package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;

import org.junit.Before;

public class DeleteHandoverStorageFilesTestnet extends DeleteHandoverStorageFilesAbstract {

    @Before
    public void before() {
        netToUse = "testnet";
        initPaths();
    }

}
