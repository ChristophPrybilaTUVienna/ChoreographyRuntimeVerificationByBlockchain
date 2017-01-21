package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.dataGeneration;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.Activity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.BusinessProcessElement;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.Start;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.XORSplit;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates possible execution paths out of given process descriptions
 */
public class ExecutionPathGenerator {

    public static short instanceIdCounter = 0;
    public static short fillerTaskCounter = 0;

    private BusinessProcessProvider businessProcessProvider;
    private Random random;

    public ExecutionPathGenerator(BusinessProcessProvider businessProcessProvider, Random random) {
        this.businessProcessProvider = businessProcessProvider;
        this.random = random;
    }

    /**
     * Returns a list of all possible execution paths to a given business process description
     */
    public List<ExecutionPath> generateExecutionPathsForBusinessProcessDescription(String businessProcessDescriptionName) {
        List<ExecutionPath> possibleExecutionPaths = new ArrayList<>();
        switch (businessProcessDescriptionName) {
            case BusinessProcessProvider.SIMPLE_BP:
            case BusinessProcessProvider.MEDIUM_ANDSPLIT_BP:
                ExecutionPath executionPath = new ExecutionPath(
                        businessProcessProvider.getBusinessProcessDescriptionClone(businessProcessDescriptionName));
                possibleExecutionPaths.add(executionPath);
                break;
            case BusinessProcessProvider.MEDIUM_XORSPLIT_BP:
                //Execution to take the "upper" Path
                ExecutionPath executionPath1 = new ExecutionPath(
                        businessProcessProvider.getBusinessProcessDescriptionClone(businessProcessDescriptionName));
                XORSplit xorSplit = (XORSplit) executionPath1.getBusinessProcessDescription().getStart().getFollowingElements()[0].getFollowingElements()[0];
                xorSplit.setIndexOfPathToTake(0);
                possibleExecutionPaths.add(executionPath1);


                //Execution to take the "lower" Path
                ExecutionPath executionPath2 = new ExecutionPath(
                        businessProcessProvider.getBusinessProcessDescriptionClone(businessProcessDescriptionName));
                xorSplit = (XORSplit) executionPath2.getBusinessProcessDescription().getStart().getFollowingElements()[0].getFollowingElements()[0];
                xorSplit.setIndexOfPathToTake(1);
                possibleExecutionPaths.add(executionPath2);
                break;
            case BusinessProcessProvider.COMPLEX_BP:
                possibleExecutionPaths.add(generateCompexBPExecutionPathWithXORIndices(true));
                possibleExecutionPaths.add(generateCompexBPExecutionPathWithXORIndices(false));
                break;
        }
        return possibleExecutionPaths;
    }

    private ExecutionPath generateCompexBPExecutionPathWithXORIndices(boolean pathA) {
        ExecutionPath executionPathComplex = new ExecutionPath(
                businessProcessProvider.getBusinessProcessDescriptionClone(BusinessProcessProvider.COMPLEX_BP));
        XORSplit xor1 = (XORSplit) executionPathComplex.getBusinessProcessDescription().getStart().getFollowingElements()[0].getFollowingElements()[0].getFollowingElements()[0];
        if (pathA) {
            xor1.setIndexOfPathToTake(0);
        } else {
            xor1.setIndexOfPathToTake(1);
        }
        return executionPathComplex;
    }

    public List<ExecutionPath> generateFaultyExecutionPathsForBusinessProcessDescription(String businessProcessDescriptionName) {
        List<ExecutionPath> possibleExecutionPaths = new ArrayList<>();
        switch (businessProcessDescriptionName) {
            case BusinessProcessProvider.SIMPLE_BP:
                ExecutionPath executionPath = new ExecutionPath(
                        businessProcessProvider.getBusinessProcessDescriptionClone(businessProcessDescriptionName));
                Start start = executionPath.getBusinessProcessDescription().getStart();
                //this BP has 3 linear activities, choose one randomly.
                int indexOfActivityToChoose = random.nextInt(3);
                Activity faultyActivity = (Activity) start.getFollowingElements()[0];
                for (int i = 0; i < indexOfActivityToChoose; i++) {
                    faultyActivity = (Activity) faultyActivity.getFollowingElements()[0];
                }
                faultyActivity.setPerformedIncorrectly(true);
                possibleExecutionPaths.add(executionPath);
                break;
            case BusinessProcessProvider.MEDIUM_ANDSPLIT_BP:
                executionPath = new ExecutionPath(
                        businessProcessProvider.getBusinessProcessDescriptionClone(businessProcessDescriptionName));
                start = executionPath.getBusinessProcessDescription().getStart();
                //this BP has one starting and one ending activity, choose one.
                indexOfActivityToChoose = random.nextInt(2);
                if (indexOfActivityToChoose == 0) {
                    faultyActivity = (Activity) start.getFollowingElements()[0];
                } else {
                    faultyActivity = (Activity) start
                            .getFollowingElements()[0]
                            .getFollowingElements()[0]
                            .getFollowingElements()[0]
                            .getFollowingElements()[0]
                            .getFollowingElements()[0];
                }
                faultyActivity.setPerformedIncorrectly(true);
                possibleExecutionPaths.add(executionPath);
                break;
            case BusinessProcessProvider.MEDIUM_XORSPLIT_BP:
                //these BPs resolve to 3 linear activities, choose one randomly.
                indexOfActivityToChoose = random.nextInt(3);

                //Execution to take the "upper" Path
                ExecutionPath executionPath1 = new ExecutionPath(
                        businessProcessProvider.getBusinessProcessDescriptionClone(businessProcessDescriptionName));
                XORSplit xorSplit = (XORSplit) executionPath1.getBusinessProcessDescription().getStart().getFollowingElements()[0].getFollowingElements()[0];
                xorSplit.setIndexOfPathToTake(0);
                Activity activity1 = (Activity) executionPath1.getBusinessProcessDescription().getStart().getFollowingElements()[0];
                Activity activity2 = (Activity) xorSplit.getFollowingElements()[0];
                Activity activity3 = (Activity) activity2.getFollowingElements()[0].getFollowingElements()[0];
                if (indexOfActivityToChoose == 0) {
                    activity1.setPerformedIncorrectly(true);
                } else if (indexOfActivityToChoose == 1) {
                    activity2.setPerformedIncorrectly(true);
                } else {
                    activity3.setPerformedIncorrectly(true);
                }
                possibleExecutionPaths.add(executionPath1);


                //Execution to take the "lower" Path
                ExecutionPath executionPath2 = new ExecutionPath(
                        businessProcessProvider.getBusinessProcessDescriptionClone(businessProcessDescriptionName));
                xorSplit = (XORSplit) executionPath2.getBusinessProcessDescription().getStart().getFollowingElements()[0].getFollowingElements()[0];
                xorSplit.setIndexOfPathToTake(1);
                activity1 = (Activity) executionPath2.getBusinessProcessDescription().getStart().getFollowingElements()[0];
                activity2 = (Activity) xorSplit.getFollowingElements()[1];
                activity3 = (Activity) activity2.getFollowingElements()[0].getFollowingElements()[0];
                if (indexOfActivityToChoose == 0) {
                    activity1.setPerformedIncorrectly(true);
                } else if (indexOfActivityToChoose == 1) {
                    activity2.setPerformedIncorrectly(true);
                } else {
                    activity3.setPerformedIncorrectly(true);
                }
                possibleExecutionPaths.add(executionPath2);
                break;
            case BusinessProcessProvider.COMPLEX_BP:
                boolean faultyAtPositionZero = random.nextBoolean();
                possibleExecutionPaths.add(generateComplexBPExecutionPathWithXORIndicesAndFaultyElement(true, faultyAtPositionZero));
                possibleExecutionPaths.add(generateComplexBPExecutionPathWithXORIndicesAndFaultyElement(false, faultyAtPositionZero));
                break;
        }
        return possibleExecutionPaths;
    }

    private ExecutionPath generateComplexBPExecutionPathWithXORIndicesAndFaultyElement(boolean pathA, boolean faultyElementIsFirst) {
        ExecutionPath executionPath = generateCompexBPExecutionPathWithXORIndices(pathA);
        Activity activity1 = (Activity) executionPath.getBusinessProcessDescription().getStart().getFollowingElements()[0];
        Activity activity2 = (Activity) activity1.getFollowingElements()[0].getFollowingElements()[1].getFollowingElements()[0].getFollowingElements()[0];
        if (faultyElementIsFirst) {
            activity1.setPerformedIncorrectly(true);
        } else {
            activity2.setPerformedIncorrectly(true);
        }
        return executionPath;
    }

    public void insetFillerTaskBeforeElement(BusinessProcessElement businessProcessElement,
                                             int indexOfPrecedingTask, String requiredAgentId) {
        byte taskId = (byte) (-127 + fillerTaskCounter);
        fillerTaskCounter++;
        Activity fillerTask = new Activity(taskId, "FillerTask");
        BusinessProcessElement beforeElement = businessProcessElement.getPrecedingElements()[indexOfPrecedingTask];
        fillerTask.setAgentId(requiredAgentId);
        //wire the fillerTask
        fillerTask.getFollowingElements()[0] = businessProcessElement;
        fillerTask.getPrecedingElements()[0] = beforeElement;
        businessProcessElement.getPrecedingElements()[indexOfPrecedingTask] = fillerTask;
        for (int i = 0; i < beforeElement.getFollowingElements().length; i++) {
            if (beforeElement.getFollowingElements()[i].getId() == businessProcessElement.getId()) {
                beforeElement.getFollowingElements()[i] = fillerTask;
            }
        }
    }

}
