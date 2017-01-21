package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.dataGeneration;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.SimulationAgent;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.*;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Initializes the generated execution paths with meta data for the simulation
 */
public class ExecutionPathInitializer {

    private static Random random;

    private static Logger logger = LoggerFactory.getLogger(ExecutionPathInitializer.class);

    private BusinessProcessProvider businessProcessProvider;

    private RuntimeVerificationUtils utils = new RuntimeVerificationUtils();

    private List<SimulationAgent> agents;

    private List<ExecutionPath> allExecutionPathsToTake;

    private ExecutionPathGenerator executionPathGenerator;

    private int indexOfStartAgent;

    public ExecutionPathInitializer(List<SimulationAgent> agents, Random random, int indexOfStartAgent) {
        this.businessProcessProvider = new BusinessProcessProvider();
        executionPathGenerator = new ExecutionPathGenerator(businessProcessProvider, random);
        this.agents = agents;
        this.random = random;
        this.indexOfStartAgent = indexOfStartAgent;
    }

    public void initExpectedProcessExecutions(String bpName, boolean faulty, int variant, int testnumber) {
        ExecutionPathGenerator.instanceIdCounter = (short) (1000 + variant + testnumber);
        allExecutionPathsToTake = new ArrayList<>();

        if (!faulty) {
            //########## Add successful execution paths
            allExecutionPathsToTake.add(
                    executionPathGenerator.generateExecutionPathsForBusinessProcessDescription(
                            bpName).get(variant));
        } else {
            //########## Add faulty execution paths
            allExecutionPathsToTake.add(
                    executionPathGenerator.generateFaultyExecutionPathsForBusinessProcessDescription(
                            bpName).get(variant));
        }

        addExecutionResultsToExecutionPaths();
        distributeAgentsAcrossActivities();
        addFillerTasksWhereNecessary();

        //sanity check of agent distribution
        sanityCheckAgentDistribution();

        //Calculate the executionPath properties
        for (ExecutionPath executionPath : allExecutionPathsToTake) {
            executionPath.calculateProcessProperties();
        }

        //Set the executions to the agents
        for (SimulationAgent agent : agents) {
            agent.setOurExecutionsFromAllExecutions(allExecutionPathsToTake);
        }
    }

    /**
     * Recheck if agent distribution is really alternating
     */
    private void sanityCheckAgentDistribution() {
        for (ExecutionPath executionPath : allExecutionPathsToTake) {
            Queue<Activity> activityQueue = new LinkedList<>();
            activityQueue.addAll(getDirectlyFollowingActivities(executionPath.getBusinessProcessDescription().getStart()));
            while(!activityQueue.isEmpty()) {
                Activity stepOfExecution = activityQueue.poll();
                for(Activity activityFollowingActivity : getDirectlyFollowingActivities(stepOfExecution)) {
                    if(stepOfExecution.getAgentId().equals(activityFollowingActivity.getAgentId())) {
                        logger.info(executionPath.toString());
                        throw new RuntimeVerificationException("Agent distribution is not correctly alternating");
                    }
                }
            }
        }
    }

    /**
     * generate random execution results (1-300 chars) and distribute them to the activities
     */
    private void addExecutionResultsToExecutionPaths() {
        for (ExecutionPath executionPath : allExecutionPathsToTake) {
            for (BusinessProcessElement stepOfExecution : utils.graphToList(executionPath.getBusinessProcessDescription().getStart())) {
                if (stepOfExecution instanceof Activity) {
                    ((Activity) stepOfExecution).setExecutionResult(utils.generateRandomStringWithLength(random, 1 + random.nextInt(300)));
                }
            }
        }
    }

    /**
     * distribute agents across activities of expected execution. This is only a rough approximated distribution and must be
     * corrected in the following steps to ensure correct alteration of the agents
     */
    private void distributeAgentsAcrossActivities() {
        for (ExecutionPath executionPath : allExecutionPathsToTake) {
            executionPath.getBusinessProcessDescription().getStart().setAgentId(agents.get(indexOfStartAgent).getAgendId());
            for (BusinessProcessElement stepOfExecution : utils.graphToList(executionPath.getBusinessProcessDescription().getStart())) {
                if (stepOfExecution instanceof Activity) {
                    Set<String> directlyPrecedingAgents = getDirectlyPrecedingAgents((Activity) stepOfExecution);
                    String agentToSet = getRandomAgent();
                    int tries = 0;
                    while (tries < agents.size() && directlyPrecedingAgents.contains(agentToSet)) {
                        agentToSet = getRandomAgent();
                        tries++;
                    }
                    ((Activity) stepOfExecution).setAgentId(agentToSet);
                }
            }
        }
    }

    private Set<String> getDirectlyPrecedingAgents(Activity currentActivity) {
        Set<String> directlyPrecedingAgents = new HashSet<>();
        Queue<BusinessProcessElement> elementsToCheck = new LinkedList<>();
        elementsToCheck.addAll(Arrays.asList(currentActivity.getPrecedingElements()));
        while (!elementsToCheck.isEmpty()) {
            BusinessProcessElement precedingElement = elementsToCheck.poll();
            if (precedingElement instanceof Start) {
                directlyPrecedingAgents.add(((Start) precedingElement).getAgentId());
            } else if (precedingElement instanceof Activity) {
                directlyPrecedingAgents.add(((Activity) precedingElement).getAgentId());
            } else if (precedingElement instanceof AndJoin ||
                    precedingElement instanceof AndSplit ||
                    precedingElement instanceof XORJoin ||
                    precedingElement instanceof XORSplit) {
                elementsToCheck.addAll(Arrays.asList(precedingElement.getPrecedingElements()));
            }
        }
        return directlyPrecedingAgents;
    }

    private Set<Activity> getDirectlyFollowingActivities(BusinessProcessElement element) {
        Set<Activity> directlyFollowingActivities = new HashSet<>();
        Queue<BusinessProcessElement> elementsToCheck = new LinkedList<>();
        elementsToCheck.addAll(Arrays.asList(element.getFollowingElements()));
        while (!elementsToCheck.isEmpty()) {
            BusinessProcessElement followingElement = elementsToCheck.poll();
            if (followingElement instanceof Activity) {
                directlyFollowingActivities.add((Activity) followingElement);
            } else if(followingElement instanceof XORSplit) {
                elementsToCheck.add(followingElement.getFollowingElements()[((XORSplit) followingElement).getIndexOfPathToTake()]);
            } else if (followingElement instanceof AndJoin ||
                    followingElement instanceof AndSplit ||
                    followingElement instanceof XORJoin) {
                elementsToCheck.addAll(Arrays.asList(followingElement.getFollowingElements()));
            }
        }
        return directlyFollowingActivities;
    }

    /**
     * insert filler activities, if need be.
     */
    private void addFillerTasksWhereNecessary() {
        ExecutionPathGenerator.fillerTaskCounter = 0;
        for (ExecutionPath executionPath : allExecutionPathsToTake) {
            String processOwner = executionPath.getBusinessProcessDescription().getStart().getAgentId();
            BusinessProcessElement processEnd = null;
            List<BusinessProcessElement> andJoins = new ArrayList<>();
            Start start = executionPath.getBusinessProcessDescription().getStart();
            for (BusinessProcessElement element : utils.graphToList(start)) {
                if (element instanceof End) {
                    processEnd = element;
                } else if (element instanceof AndJoin) {
                    andJoins.add(element);
                }
            }
            //#1 ensure that start is followed by activities different to the process owner
            for(Activity activityFollowingStart : getDirectlyFollowingActivities(start)) {
                if(activityFollowingStart.getAgentId().equals(processOwner)) {
                    executionPathGenerator.insetFillerTaskBeforeElement(activityFollowingStart, 0, getRandomAgent(processOwner));
                }
            }

            //#2 ensure correct agent-setting for end of workflow
            //if activity before end is not owned by process owner, a filler task is required.
            //it is expected that before each end there is directly an activity.
            if (!((Activity) processEnd.getPrecedingElements()[0]).getAgentId().equals(processOwner)) {
                executionPathGenerator.insetFillerTaskBeforeElement(processEnd, 0, processOwner);
            }

            //#3 ensure correct agent-setting for join of workflow
            //each and_join is expected to only have incoming paths belonging to the same agent
            for (BusinessProcessElement andJoinElement : andJoins) {
                AndJoin andJoin = (AndJoin) andJoinElement;
                //each and_join must have at least one incoming path that is an activity
                String agentOfJoin = null;
                for (BusinessProcessElement element : andJoin.getPrecedingElements()) {
                    if (element instanceof Activity) {
                        agentOfJoin = ((Activity) element).getAgentId();
                        break;
                    }
                }
                if (agentOfJoin == null) {
                    throw new RuntimeVerificationException("each and_join must have at least one incoming path that is an activity");
                }
                for (int i = 0; i < andJoin.getPrecedingElements().length; i++) {
                    BusinessProcessElement element = andJoin.getPrecedingElements()[i];
                    if (element instanceof Activity) {
                        if (!((Activity) element).getAgentId().equals(agentOfJoin)) {
                            executionPathGenerator.insetFillerTaskBeforeElement(andJoin, i, agentOfJoin);
                        }
                    } else {
                        executionPathGenerator.insetFillerTaskBeforeElement(andJoin, i, agentOfJoin);
                    }
                }
            }
            //#4 ensure correct iterating agent-setting for all activities of workflow
            Queue<Activity> activitiesToCheck = new LinkedList<>();
            activitiesToCheck.addAll(getDirectlyFollowingActivities(start));
            while(!activitiesToCheck.isEmpty()) {
                Activity activityToCheck = activitiesToCheck.poll();
                for(Activity activityFollowingCurrentActivity : getDirectlyFollowingActivities(activityToCheck)) {
                    if(activityToCheck.getAgentId().equals(activityFollowingCurrentActivity.getAgentId())) {
                        executionPathGenerator.insetFillerTaskBeforeElement(
                                activityFollowingCurrentActivity, 0, getRandomAgent(activityToCheck.getAgentId()));
                    }
                }
                activitiesToCheck.addAll(getDirectlyFollowingActivities(activityToCheck));
            }
        }
    }

    private String lastAgent = "";

    /**
     * Returns the next random agent but never the same agent twice in a row.
     */
    private String getRandomAgent() {
        String nextAgent = agents.get(random.nextInt(agents.size())).getAgendId();
        while (nextAgent.equals(lastAgent)) {
            nextAgent = agents.get(random.nextInt(agents.size())).getAgendId();
        }
        lastAgent = nextAgent;
        return nextAgent;
    }

    /**
     * Returns the next random agent not equal to the given agent, but never the same agent twice
     */
    private String getRandomAgent(String notEqualAgent) {
        String nextAgent = getRandomAgent();
        while (nextAgent.equals(notEqualAgent)) {
            nextAgent = getRandomAgent();
        }
        return nextAgent;
    }

    public List<ExecutionPath> getAllExecutionPathsToTake() {
        return allExecutionPathsToTake;
    }
}
