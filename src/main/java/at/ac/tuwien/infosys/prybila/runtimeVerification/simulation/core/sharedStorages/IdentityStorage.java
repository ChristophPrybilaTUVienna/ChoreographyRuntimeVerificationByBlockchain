package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.OwnIdentityProvider;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;

import java.util.List;

/**
 * Distributes the RSA information of all agents
 */
public class IdentityStorage {

    private List<OwnIdentityProvider> availableIdentities;

    public IdentityStorage(List<OwnIdentityProvider> availableIdentities) {
        this.availableIdentities = availableIdentities;
        new RuntimeVerificationUtils().notNull(availableIdentities);
    }

    /**
     * Returns the publicly available identity data of the given company or null.
     */
    public synchronized Identity getPublicIdentificationDataOfCompany(String company) {
        for (OwnIdentityProvider ownIdentityProvider : availableIdentities) {
            Identity publicIdentity =
                    ownIdentityProvider.getOwnIdentityToShareWithPartner();
            if (company.equals(publicIdentity.getCompanyName())) {
                return publicIdentity;
            }
        }
        return null;
    }
}
