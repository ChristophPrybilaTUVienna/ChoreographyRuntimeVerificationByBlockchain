package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model;

import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.bitcoinj.core.Utils;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

/**
 * Contains the workflow information of a specific workflow handover
 */
public class WorkflowHandoverData implements Serializable {

    /**
     * Reference to workflow instance
     */
    private WorkflowInstance workflowInstance;

    /**
     * Id of the task which now has to be executed.
     */
    private byte idOfNextTask;

    /**
     * Marker indicating at if the execution has now started, ended or is at an intermediate state.
     */
    private WorkflowHandoverType workflowHandoverType;

    /**
     * DateTime, negotiated between one handover's sender and receiver, indicating when the handover took place
     * and the execution of the next task was started.
     */
    private int handoverTimeStamp;
    private String handoverTimeStampPrettyString;

    /**
     * Data of the workflow instance before the start of idOfNextTask;
     */
    private byte[] workflowData;

    /**
     * Hash160 of the workflow instance data.
     */
    private byte[] hash160OfWorkflowData;

    private transient RuntimeVerificationUtils runtimeVerificationUtils;

    public WorkflowHandoverData(WorkflowInstance workflowInstance, byte idOfNextTask, WorkflowHandoverType workflowHandoverType, int handoverTimeStamp) {
        this.workflowInstance = workflowInstance;
        this.idOfNextTask = idOfNextTask;
        this.workflowHandoverType = workflowHandoverType;
        this.handoverTimeStamp = handoverTimeStamp;
        runtimeVerificationUtils = new RuntimeVerificationUtils();
        runtimeVerificationUtils.notNull(workflowInstance);
        runtimeVerificationUtils.notNull(idOfNextTask);
        runtimeVerificationUtils.notNull(workflowHandoverType);
        runtimeVerificationUtils.notNull(handoverTimeStamp);
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(handoverTimeStamp * 1000L);
        handoverTimeStampPrettyString = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss S z").format(calendar.getTime());
    }

    public void setWorkflowData(byte[] workflowData) {
        this.workflowData = workflowData;
        if (runtimeVerificationUtils == null) {
            runtimeVerificationUtils = new RuntimeVerificationUtils();
        }
        runtimeVerificationUtils.notNull(workflowData);
        hash160OfWorkflowData = Utils.sha256hash160(workflowData);
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public byte getIdOfNextTask() {
        return idOfNextTask;
    }

    public WorkflowHandoverType getWorkflowHandoverType() {
        return workflowHandoverType;
    }

    public int getHandoverTimeStamp() {
        return handoverTimeStamp;
    }

    public String getHandoverTimeStampPrettyString() {
        return handoverTimeStampPrettyString;
    }

    public byte[] getWorkflowData() {
        return workflowData;
    }

    public byte[] getHash160OfWorkflowData() {
        return hash160OfWorkflowData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WorkflowHandoverData that = (WorkflowHandoverData) o;

        if (idOfNextTask != that.idOfNextTask) return false;
        if (handoverTimeStamp != that.handoverTimeStamp) return false;
        if (workflowInstance != null ? !workflowInstance.equals(that.workflowInstance) : that.workflowInstance != null)
            return false;
        if (workflowHandoverType != that.workflowHandoverType) return false;
        if (handoverTimeStampPrettyString != null ? !handoverTimeStampPrettyString.equals(that.handoverTimeStampPrettyString) : that.handoverTimeStampPrettyString != null)
            return false;
        if (!Arrays.equals(workflowData, that.workflowData)) return false;
        return Arrays.equals(hash160OfWorkflowData, that.hash160OfWorkflowData);

    }

    @Override
    public int hashCode() {
        int result = workflowInstance != null ? workflowInstance.hashCode() : 0;
        result = 31 * result + (int) idOfNextTask;
        result = 31 * result + (workflowHandoverType != null ? workflowHandoverType.hashCode() : 0);
        result = 31 * result + handoverTimeStamp;
        result = 31 * result + (handoverTimeStampPrettyString != null ? handoverTimeStampPrettyString.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(workflowData);
        result = 31 * result + Arrays.hashCode(hash160OfWorkflowData);
        return result;
    }

    @Override
    public String toString() {
        return "WorkflowHandoverData{" +
                "workflowInstance=" + workflowInstance +
                ", idOfNextTask=" + idOfNextTask +
                ", workflowHandoverType=" + workflowHandoverType.name() +
                ", handoverTimeStampPrettyString='" + handoverTimeStampPrettyString + '\'' +
                ", hash160OfWorkflowData=" + Arrays.toString(hash160OfWorkflowData) +
                '}';
    }
}
