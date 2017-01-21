package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.AgentStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.sharedStorages.ExecutionPathStorage;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.dataGeneration.ExecutionPathInitializer;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main class to manage simulation
 */
public class Simulator {

    public static final int waitForFinishIntervalMS = 3000;

    private static Random random;

    private static ApplicationContext context;

    private static List<SimulationAgent> agents;

    private static ExecutionPathStorage executionPathStorage;

    private static Logger logger = LoggerFactory.getLogger(Simulator.class);

    //input parameters
    public static int testNumber;
    public static boolean greedyPublishing;
    private static long seedForRandom;
    private static String bpName;
    private static boolean includeFaultInProcess;
    public static boolean useRuntimeVerification;
    public static int executionPathVariant;
    public static String agentSet;
    public static String netToUse;
    public static int agentWithMoney;

    public static AtomicInteger andJoinPathsWaiting = new AtomicInteger(0);
    public static ConcurrentSkipListSet<Short> workflowsFinished = new ConcurrentSkipListSet<>();

    public static void main(String[] args) throws IOException {
        fetchInputParameters(args);
        configFileLogger();
        logger.info(String.format(
                "Starting the simulation " +
                        "testNumber=%s, " +
                        "BP_Name=%s, " +
                        "Variant=%s, " +
                        "Corrupt=%s, " +
                        "Seed=%s, " +
                        "UsingRuntimeVerification=%s," +
                        "ImmediatelyWaitForConfirmation=%s, " +
                        "AgentSet=%s, netToUse=%s agentWithMoney=%s", testNumber, bpName, executionPathVariant, includeFaultInProcess,
                seedForRandom, useRuntimeVerification, !greedyPublishing, agentSet, netToUse, agentWithMoney));

        executionPathVariant--;

        String appContext = "applicationContext_" + agentSet + File.separator + "applicationContext" + netToUse + "Simulation_" + agentSet + ".xml";
        logger.debug("Loading the application context...@ " + appContext);
        //Load application context, i.e. the simulation configuration.
        context = new ClassPathXmlApplicationContext(appContext);

        //Init main random number generator
        initRandom();

        logger.debug("Preparing the simulation execution data...");
        //Prepare the simulation agents
        initSimulationAgents();

        //Generate execution paths for simulation
        ExecutionPathInitializer executionPathInitializer = new ExecutionPathInitializer(agents, random, agentWithMoney);
        executionPathInitializer.initExpectedProcessExecutions(bpName, includeFaultInProcess, executionPathVariant, testNumber);
        executionPathStorage = new ExecutionPathStorage(executionPathInitializer.getAllExecutionPathsToTake());

        logger.debug(String.format(
                "Starting to execute %s simulation agents with the following execution paths.",
                agents.size()));
        for (ExecutionPath executionPath : executionPathInitializer.getAllExecutionPathsToTake()) {
            logger.info(executionPath.toString());
        }

        //Start the agents
        for (SimulationAgent agent : agents) {
            agent.setExecutionPathStorage(executionPathStorage);
            logger.info("Starting agent " + agent.getAgendId());
            agent.start();
        }

        logger.info("waiting for all agents to finish");
        boolean notAllFinished = true;
        while (notAllFinished) {
            notAllFinished = false;
            for (SimulationAgent agent : agents) {
                if (!agent.isAllOwnedProcessesHaveFinished()) {
                    notAllFinished = true;
                    break;
                }//logger.debug("Agent " + agent.getAgendId() + " has finished all its workflows.");
            }
            if (notAllFinished) {
                try {
                    Thread.sleep(waitForFinishIntervalMS);
                } catch (InterruptedException e) {
                    //Ignore
                    e.printStackTrace();
                }
            }
        }

        logger.info("send stop commands to all agents");
        for (SimulationAgent agent : agents) {
            logger.info("Sending stop command to " + agent.getAgendId());
            agent.stopAgent();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                //Ignore
                e.printStackTrace();
            }
        }

        if (useRuntimeVerification) {
            logger.info("Verify the correctness of the documented execution trace of all agents");
            for (SimulationAgent agent : agents) {
                agent.verifyTheDocumentedWorkflowExecutionAgainstThePredefinedSimulation();
            }
            logger.info("All execution traces where as expected.");
        } else {
            boolean stillRunning = true;
            while (stillRunning) {
                stillRunning = false;
                //wait for all agents to stop
                for (SimulationAgent agent : agents) {
                    if (!agent.isStopped()) {
                        stillRunning = true;
                        break;
                    }
                }
                if (stillRunning) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        //Ignore
                        e.printStackTrace();
                    }
                }
            }
        }
        logger.info("Ending the simulation.");
        System.exit(0);
    }

    private static void initRandom() {
        random = new Random(seedForRandom);
        //mix testruns with their testnumber
        for (int i = 0; i < testNumber; i++) {
            random.nextInt();
        }
    }

    private static void configFileLogger() {
        FileAppender fa = new FileAppender();
        fa.setName("LogFileLogger");
        fa.setFile("SimulationResult_" + testNumber + ".log");
        fa.setLayout(new PatternLayout("%d{ISO8601} %-5p [%t] %c: %m%n"));
        fa.setThreshold(Level.DEBUG);
        fa.setAppend(false);
        fa.activateOptions();
        org.apache.log4j.Logger.getRootLogger().addAppender(fa);
    }

    private static void fetchInputParameters(String[] args) {
        if (args.length != 10) {
            configFileLogger();
            logger.info("Usage: Simulator <testNumber> <BP_Name> <Variant> <Corrupt> <Seed> <UsingRuntimeVerification> <ImmediatelyWaitForConfirmation> <agentSet> <netToUse> <agentWithMoney>");
            System.exit(1);
        }
        testNumber = Integer.parseInt(args[0]);
        bpName = args[1].trim();
        executionPathVariant = Integer.parseInt(args[2]);
        includeFaultInProcess = Boolean.parseBoolean(args[3].trim().toLowerCase());
        seedForRandom = Long.parseLong(args[4]);
        useRuntimeVerification = Boolean.parseBoolean(args[5].trim().toLowerCase());
        greedyPublishing = !Boolean.parseBoolean(args[6].trim().toLowerCase());
        agentSet = args[7].trim();
        netToUse = args[8].trim();
        agentWithMoney = Integer.parseInt(args[9]);
    }

    private static void initSimulationAgents() {
        //Fetch configured Simulation agents, i.e. the different choreography participants
        agents = (List<SimulationAgent>) context.getBean("agents");

        //Fetch general agentStorage to be used as dataprovider by the agents.
        AgentStorage agentStorage = (AgentStorage) context.getBean("agentStorage");

        //init required variables of simulationAgents
        for (SimulationAgent simulationAgent : agents) {
            simulationAgent.setAgentStorage(agentStorage);
        }

        //test if specified start agent has enough money
        if (!agents.get(agentWithMoney).isReadyForSimulation()) {
            throw new RuntimeVerificationException("Agent " + agents.get(agentWithMoney).getAgendId() +
                    " was specified as agent to start the simulation with but the agent did not have enough money.");
        }
    }

}
