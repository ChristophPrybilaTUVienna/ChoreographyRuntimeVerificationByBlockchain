package at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation;

import org.junit.Test;

import java.io.File;

public abstract class DeleteHandoverStorageFilesAbstract {

    private int walletCount = 12;

    private String pathToTestFilesHandoverStore;

    protected String netToUse;

    protected void initPaths() {
        pathToTestFilesHandoverStore = "./testfiles/simulation/svp_" + netToUse + "_company_%s.handoverStore";
    }


    @Test
    public void deleteAllHandoverStorageFiles() {
        for (int i = 0; i < walletCount; i++) {
            String pathToHandoverStoreFile = String.format(pathToTestFilesHandoverStore, (char) (i + 65));
            new File(pathToHandoverStoreFile).delete();
        }
    }

}
