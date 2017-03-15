# Runtime Verification for Business Processes utilizing the Blockchain

Scientific research on the topic of runtime verification for business process choreographies is conducted at the [Distributed Systems Group](http://www.infosys.tuwien.ac.at/) (DSG) at TU Vienna. 
During the course of this research, a prototype framework was created which utilizes the Bitcoin Blockchain as independent shared trust basis.
Furthermore, this prototype was tested in an evaluation. 

<!--The underlying proposed concept can be found in the published [paper](TODO_INSERTLINK). --->
Further details about the concept and the prototype can be found in this [master thesis](http://repositum.tuwien.ac.at/obvutwhs/download/pdf/1761635?originalFilename=true).

The sources of the created prototype and the simulation can be found in this repository.

For further information about this specific project, please contact [Stefan Schulte](http://www.infosys.tuwien.ac.at/staff/sschulte/talks.html) from the DSG.

The prototype itself was developed by using the following technologies and frameworks.
- Java Development Kit (JDK) 1.8
- Apache Maven 3.3.9
- Spring Beans 4.2.6 
- Apache HttpClient 4.5.2
- Google Gson 2.7 
- [bitcoinj](https://bitcoinj.github.io/) 0.14.2 

The [bitcoinj](https://bitcoinj.github.io/) framework provides basic management functions to operate a
SPV Bitcoin wallet. 
While originally designed to support slim (mobile) Bitcoin payment applications, this project re-purposed bitcoinj to create and publish custom Bitcoin transactions with P2SH and data outputs on top of existing SPV wallets.
On top of this custom transaction logic, a management framework for runtime verification for business processes is constructed. It is important to note that all created transactions still suffice the _IsStandard()_ check of Bitcoin v0.13.0.

Furthermore, the prototype includes the capability to crawl Bitcoin REST APIs to gather additional information the SPV wallet can not provide. 
We know that this workaround would not be required if a full Bitcoin node would be used instead of a SPV client. We chose this setup because it is much more lightweight and easier to test.

The REST API employed in the simulation is [blockcypher](https://api.blockcypher.com), but it can be replaced with any other API. The only special requirement is that _pending_ transactions must also be retrievable.

The project is structured through the following packages:

- **at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core** - 
This package includes the core features required to support the runtime verification concept. These are amongst other things, transaction building, broadcasting and wallet management. 
The features of the package can be accessed through the class at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.BitcoinConnection.

- **at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.crawler** - 
Enables the retrieval of arbitrary Bitcoin transaction data.
It can be used to retrieve information about any Bitcoin transaction through the REST API of blockcypher.

- **at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework** - 
The features required to perform the proposed runtime verification are supplied in this package. 
It operates on top of the functionality of the two previously described packages. 
The features can be accessed through the class at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager.

- **at.ac.tuwien.infosys.prybila.runtimeVerification.simulation** -
This package contains the logic of the simulation. 
It contains a very simple custom business process management (BPM) logic that enables the routing of business processes.
Each process participant uses this logic to participate in the choreography. 
The runtime verification framework is included in this BPM logic.
Please note that the focus of this prototype lies on the runtime verification of choreographies, further BPM aspects are only implemented to support this.
Business processes are defined directly in the software instead of BPMN process models.

## Getting the Simulation to run

A choreography-oriented interaction between four different process participants is simulated.
Between these participants a single process is enacted.
During the enactment the described runtime verification prototype is used. 

In the simulation package, process participants are referred to as SimulationAgents. 
Sets of agents are configured as Spring beans. 
This way, each simulation only requires a reference to the given Spring applicationContext in order to know which agent set to use.

Each process participant operates its own isolated WorkflowHandoverManager. 
This class monitors and stores the recorded runtime verification activities for the enacted process. 
Furthermore, it enables the companies to participate in the runtime verification activities. 
Received handovers are verified and confirmed, own process steps are recorded and published.

A number of preparations have to be done in order to run the simulation. 
Helper functions (provided in the form of JInit test), help with these preparations:

0. Create the directory PROJECT_DIR/testfiles/simulation/rsaKeys

1. Most Bitcoin REST APIs require a token that has to be appended to each request. 
This token identifies the given payment plan. Blockcypher also offers a free payment plan.
This token has to be configured in _crawler.properties_.

2. The approach also relies on PKI encryption functions. 
Therefore, each process participant requires a RSA-Keypair. 
The keys for all agents can be generated through the method _at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation.GenerateRSAKeyFiles.generateKeyPairsForSimulation()_.
The generated keys must be stored in PROJECT_DIR/testfiles/simulation/rsaKeys

    The following steps prepare the required wallets. These wallets can be generated for the Bitcoin _testnet_ or _mainnet_.
    Therefore, all state names that include a '{X}' are available in both variants. 
    Note that as usual for testnet tests, a local bitcoin node that operates in testnet mode is required. 
    Wallets that operate on the mainnet do not require such a local node. They synchronize directly with the network.

    The bitcoinJ framework was never intended to load multiple wallets at the same time.
    Loading more than four wallets into one JVM at once causes the wallet-synchronization process to fail.
    That is why each agent set only contains four agents. 
    The class _at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation.AbstractIterativeContextLoader_ 
    helps to perform preparation tasks on all wallets.

3. The method _at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation.PrintAllBalances{X}.iterateOverAllWalletsWithAction()_ 
prints the balance for each agent-wallet and a bitcoin address that can be used to send funds to the given wallet.
This methods should be run at least once before the simulation because it initially creates the wallets.
Upon creation, each wallet synchronizes with the network. 
Even though this process is done in the limited SPV fashion and supported by a checkpoint file, it might take some minutes until all wallets are created.
After the initial creation, the wallets are up-to-date and synchronize much faster when loaded during the simulation.

4. For the simulation runs, at least one wallet must contain funds. It is the wallet of the agent that initially starts the choreography enactment (i.e., creates the verification token).

5. The simulation can now be started through the class _at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.Simulator_.
Please note that the simulation only stops after a choreography was enacted. 
A choreography is considered done when all its published transactions have reached a confirmation depth of 1.
Therefore, simulations can take a long time. 

    A simulation expects the following parameters.

    - **testNumber** - Arbitrary number to be listed in the logfiles.
    - **BP_Name** - Business process to enact (see directory _BusinessProcessModels_ for possible BPs).
    - **Variant** - Variant number. Some BPs have different variants (i.e., different XOR paths). If unsure, just use '1'.
    - **Corrupt** - True/False flag if a fault should be included in the BP. Tests if the runtime verification framework recognizes the incorrect behaviour.
    - **Seed** - Seed for the random number generators.
    - **UsingRuntimeVerification** -  True/False flag if runtime verification should be employed.
    - **ImmediatelyWaitForConfirmation** -  True/False flag if the greedy mode (false) should be employed.
    - **AgentSet** -  Name of the AgentSet (ApplicationContext [AgentSetOne|AgentSetTwo|AgentSetThree]) that should be used to load the company definitions.
    - **netToUse** -  Name of the network to be used [testnet|mainnet].
    - **agentWithMoney** -  Index of the agent that has enough funds to start the choreography.

6. After the simulation, a handover-storage file was created for each company. 
Process instances with duplicate ids are not accepted. 
That is why it might become necessary to delete those handover-storage files.
This can be done automatically through 
the method _at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation.DeleteHandoverStorageFiles{X}.deleteAllHandoverStorageFiles()_

7. When something during the simulation fails, it may happen that funds get stuck in P2SH outputs of process transactions.
In this case the funds can be rescued with the following method 
_at.ac.tuwien.infosys.prybila.runtimeVerification.test.simulation.preparation.RescueFunds{X}.rescueMoneyFromP2SHOutputWithoutDataHash()_

    The method requires the following information. 
    
    - The txHash of the corresponding transaction.
    - The used private key (can be retrieved from the graphstorage of the WorkflowHandoverManager).
    - The index of the sending wallet (if unsure, just try different combinations).
    - The index of the wallet the rescued funds should be sent to.

    For some scenarios, also a datahash is required in the redeem script. 
    In these cases, retrieve the datahash from the graphstorage and use the alternative variant of the BitcoinConnectionWithTestMethods.createRedeemScript() method.


## DISCLAIMER
To run the simulation on the Bitcoin mainnet, real Bitcoin funds are required to fuel the transactions. 
Losing the required data to access bitcoins (e.g., private keys, additional locking information employed by the framework) can render funds permanently inaccessible.
The presented framework is still in a prototype state, i.e., we take no responsibility for lost funds.

This program is distributed in the hope that it will be useful, but **WITHOUT ANY WARRANTY**.

## Project License
This project is released under GNU General Public License Version 3.

## Other Licenses
The following software frameworks that have been utilized in this project are published under the  Apache License Version 2.0.

- http://www.bitcoinj.org
- http://graphstream-project.org
- https://hc.apache.org/httpcomponents-core-ga
- https://github.com/google/gson
