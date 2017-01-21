package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;

import org.junit.Before;

public class DeleteHandoverStorageFilesMainnet extends DeleteHandoverStorageFilesAbstract {

    @Before
    public void before() {
        netToUse = "mainnet";
        initPaths();
    }

}
