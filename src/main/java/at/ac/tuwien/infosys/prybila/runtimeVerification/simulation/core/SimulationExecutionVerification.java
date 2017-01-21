package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.WorkflowHandoverManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.WorkflowGraphStep;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.*;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.workflowExecutionDescription.ExecutionPath;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;

/**
 * Validates the documented execution of the runtime verification framework against the workflow model.
 */
public class SimulationExecutionVerification {

    private BusinessProcessDescription businessProcessDescription;

    private short workflowInstance;

    private WorkflowHandoverManager workflowHandoverManager;

    private boolean shouldBeComplete;

    private boolean useForSimulationVerification;

    public SimulationExecutionVerification(ExecutionPath executionPath, WorkflowHandoverManager workflowHandoverManager,
                                           boolean shouldBeComplete, boolean useForSimulationVerification) {
        this.workflowHandoverManager = workflowHandoverManager;
        this.shouldBeComplete = shouldBeComplete;
        this.useForSimulationVerification = useForSimulationVerification;
        businessProcessDescription = executionPath.getBusinessProcessDescription();
        workflowInstance = executionPath.getInstanceId();
    }

    /**
     * Validates that the given documented execution fits, the model of the workflow.
     * Throws a RuntimeVerificationException if the validation fails.
     */
    public void validateExecution() {
        WorkflowGraphStep executionRoot = workflowHandoverManager.getWorkflowInstanceDataAsClone(workflowInstance);
        BusinessProcessElement modelStart = businessProcessDescription.getStart();
        followPaths(executionRoot, modelStart);
    }

    /**
     * Follows the execution paths, while simultaneously following the modeled path.
     * This should always be possible, if the execution adhered the workflow model.
     */
    private void followPaths(WorkflowGraphStep verifiedStep, BusinessProcessElement modeledStep) {
        checkDocumentedId(verifiedStep);
        if (modeledStep instanceof XORJoin) {
            modeledStep = modeledStep.getFollowingElements()[0];
        }
        if (modeledStep instanceof XORSplit) {
            if (useForSimulationVerification) {
                modeledStep = modeledStep.getFollowingElements()[((XORSplit) modeledStep).getIndexOfPathToTake()];
            } else {
                //one path must work
                int workingPaths = 0;
                for (BusinessProcessElement modeledChild : modeledStep.getFollowingElements()) {
                    try {
                        followPaths(verifiedStep, modeledChild);
                        workingPaths++;
                    } catch (RuntimeVerificationException e) {
                        //ignore
                    }
                }
                if (workingPaths != 1) {
                    quitWithError("Number of matching paths found for XORSplit where not 1. Instead the number was " + workingPaths);
                }
                return;
            }
        }
        //check for faulty elements
        if (useForSimulationVerification &&
                modeledStep instanceof Activity &&
                ((Activity) modeledStep).isPerformedIncorrectly()) {
            //the current activity is faulty and therefore should have been documented as an end marker
            // instead of a handover
            if (!verifiedStep.isEnd()) {
                quitWithError(
                        "The documented workflow is not ended due to a faulty handover even though this was expected" +
                                " by the predefined simulation.");
            }
            return;
        }

        //No more XOR elements at this stage
        checkIfTypesAndContentMatch(verifiedStep, modeledStep);

        if (verifiedStep.getChildren() != null) {
            if (useForSimulationVerification) {
                if (verifiedStep.getChildren().size() != modeledStep.getFollowingElements().length) {
                    quitWithError(
                            "The documented workflow splits into different paths than allowed by the workflow model.");
                }
            } else {
                if (verifiedStep.getChildren().size() > modeledStep.getFollowingElements().length) {
                    quitWithError(
                            "The documented workflow splits into more paths than allowed by the workflow model.");
                }
            }
            for (int i = 0; i < verifiedStep.getChildren().size(); i++) {
                if (verifiedStep.getChildren().get(i).getHandoverData().isTemplate()) {
                    continue;
                }
                //the child order is not fixed. Therefore exactly one path combination must fit
                int matchingPaths = 0;
                for (BusinessProcessElement expectedFollowingElement : modeledStep.getFollowingElements()) {
                    try {
                        followPaths(verifiedStep.getChildren().get(i), expectedFollowingElement);
                        matchingPaths++;
                    } catch (RuntimeVerificationException e) {
                        //ignore
                    }
                }
                if (matchingPaths != 1) {
                    quitWithError("Number of matching subpaths found for Element was not 1. Instead the number was " + matchingPaths);
                }
            }
        } else {
            if (shouldBeComplete && modeledStep.getFollowingElements() != null) {
                quitWithError(
                        "The documented workflow ended but the workflow model has not yet reached its end.");
            }
        }
    }

    /**
     * Checks if the documented step contains the correct wf-id.
     */
    private void checkDocumentedId(WorkflowGraphStep verifiedStep) {
        if (verifiedStep.getHandoverData().getWorkflowHandoverData().getWorkflowInstance().getId() != workflowInstance) {
            quitWithError("The documented workflow id was not correct");
        }
    }

    /**
     * Checks, if the types of the documented step and the modeled step match and if their structure/content matches the expected one.
     */
    private void checkIfTypesAndContentMatch(WorkflowGraphStep verifiedStep, BusinessProcessElement modeledStep) {
        if (verifiedStep.isStart()) {
            if (!(modeledStep instanceof Start)) {
                quitWithError("A wf-start was documented in the Block Chain, but the model did not show a wf-start");
            }
        } else if (verifiedStep.isIntermediate()) {
            if (!(modeledStep instanceof Activity)) {
                quitWithError("A wf-activity was documented in the Block Chain, but the model did not show a wf-activity");
            } else {
                Activity activity = (Activity) modeledStep;
                if (activity.getId() != verifiedStep.getHandoverData().getWorkflowHandoverData().getIdOfNextTask()) {
                    quitWithError("A wf-activity was documented in the Block Chain with a task id not fitting the modeled workflow. " +
                            activity.getId() + " != " + verifiedStep.getHandoverData().getWorkflowHandoverData().getIdOfNextTask());
                }
                if (useForSimulationVerification) {
                    byte[] wfDataHash = verifiedStep.getChildren().get(0).getHandoverData().getWorkflowHandoverData().getWorkflowData();
                    if (wfDataHash != null) {
                        String documentedExecutionResult = new String(wfDataHash);
                        if (!activity.getExecutionResult().equals(documentedExecutionResult)) {
                            quitWithError("A wf-activity was documented in the Block Chain with an execution result not fitting the predefined simulation.");
                        }
                    }
                    //If identity information is available, compare it to the expected one
                    if (!verifiedStep.getTo().equals(Identity.getUnknownCompanyIdentity())) {
                        if (!verifiedStep.getTo().getCompanyName().equals(((Activity) modeledStep).getAgentId())) {
                            quitWithError("A wf-activity was documented in the Block Chain with an executing company not fitting the predefined simulation.");
                        }
                    }
                }
            }
        } else if (verifiedStep.isEnd()) {
            if (!(modeledStep instanceof End)) {
                quitWithError("A wf-end was documented in the Block Chain, but the model did not show a wf-end");
            }
        } else if (verifiedStep.isSplit()) {
            if (!(modeledStep instanceof AndSplit)) {
                quitWithError("A wf-andSplit was documented in the Block Chain, but the model did not show a wf-andSplit");
            }
            if (useForSimulationVerification) {
                if (modeledStep.getFollowingElements().length != verifiedStep.getChildren().size()) {
                    quitWithError("A wf-andSplit was documented in the Block Chain with a number of outgoing paths different to the workflow model.");
                }
            } else {
                if (verifiedStep.getChildren() != null &&
                        verifiedStep.getChildren().size() > modeledStep.getFollowingElements().length) {
                    quitWithError("A wf-andSplit was documented in the Block Chain with a number of outgoing paths greater than the workflow model.");
                }
            }
        } else if (verifiedStep.isJoin()) {
            if (!(modeledStep instanceof AndJoin)) {
                quitWithError("A wf-andJoin was documented in the Block Chain, but the model did not show a wf-andJoin");
            }
            if (modeledStep.getPrecedingElements().length != verifiedStep.getParents().size()) {
                quitWithError("A wf-andJoin was documented in the Block Chain with a number of incoming paths different to the workflow model.");
            }
        }
    }

    private void quitWithError(String msg) {
        throw new RuntimeVerificationException(msg);
    }

}
