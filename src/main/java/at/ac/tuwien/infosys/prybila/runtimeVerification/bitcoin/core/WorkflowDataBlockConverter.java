package at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core;


import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverData;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowHandoverType;
import at.ac.tuwien.infosys.prybila.runtimeVerification.bitcoin.core.model.WorkflowInstance;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationException;
import at.ac.tuwien.infosys.prybila.runtimeVerification.utils.RuntimeVerificationUtils;
import org.bitcoinj.core.ECKey;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Converts the relevant workflow data from/to an OP_RETURN byte block
 */
public class WorkflowDataBlockConverter implements Serializable {

    /**
     * Dicussed by Bitcoin developer Pieter Wuille in
     * http://bitcoin.stackexchange.com/questions/12554/why-the-signature-is-always-65-13232-bytes-long
     */
    public static final int DEC_SIGNATURE_MIN_BYTE_LENGTH = 69;

    private static final String wfStartMarker = "Start of workflow.";

    private static final String wfEndMarker = "End of workflow.";

    private static final String wfSplitMarker = "Split of workflow.";

    private static final String wfJoinMarker = "Join of workflow.";

    private static final int sizeOfHandoverBlock = 80;
    private static final int sizeOfLengthField = 1;
    private static final int sizeOfDatablockField = 7;

    /**
     * Workflow data to be serialized or de-serialized
     */
    private WorkflowHandoverData workflowHandoverData;

    /**
     * ECKey signature of the transaction template provided by the receiving partner of the handover.
     */
    private byte[] signature;

    /**
     * Seven byte datablock to store the WorkflowHandoverData information.
     */
    private byte[] dataBlock;

    private RuntimeVerificationUtils runtimeVerificationUtils;

    public WorkflowDataBlockConverter(WorkflowHandoverData workflowHandoverData) {
        this.workflowHandoverData = workflowHandoverData;
        runtimeVerificationUtils = new RuntimeVerificationUtils();
        runtimeVerificationUtils.notNull(workflowHandoverData);
        dataBlock = new byte[sizeOfDatablockField];
        signature = null;
        variablesToDataBlock();
    }

    public WorkflowDataBlockConverter(byte[] receivedDataBlock) {
        //sanity checks
        runtimeVerificationUtils = new RuntimeVerificationUtils();
        runtimeVerificationUtils.notNull(receivedDataBlock);
        int lengthOfDataBlockData = receivedDataBlock[0];
        if (lengthOfDataBlockData > receivedDataBlock.length) {
            throw new RuntimeVerificationException("Length field of datablock does not fit received datablock");
        }

        dataBlock = new byte[sizeOfDatablockField];
        //The Datablock can be of type Start, End, Join, AndSplit, Handover or unsigned Handover
        WorkflowHandoverType workflowHandoverType = determineDataBlockType(receivedDataBlock);

        dataBlockToVariables(receivedDataBlock, workflowHandoverType);
        if (workflowHandoverType == WorkflowHandoverType.INTERMEDIATE) {
            getSignatureFromSignedDataBlock(receivedDataBlock, lengthOfDataBlockData);
        }
    }

    /**
     * Fetches the signature from the given signedDataBlock.
     */
    private void getSignatureFromSignedDataBlock(byte[] signedDataBlock, int lengthOfDataBlockData) {
        int lengthOfDataPrecedingSignature = sizeOfLengthField + sizeOfDatablockField;
        //Remaining length is 0, therefore no signature is contained
        if (lengthOfDataPrecedingSignature == lengthOfDataBlockData) {
            signature = null;
            return;
        }
        //check if signature is empty on purpose (i.e. in a handover template)
        if (signedDataBlock.length == sizeOfHandoverBlock
                && byteArrayEndsWith(signedDataBlock, new byte[(signedDataBlock.length - sizeOfDatablockField - sizeOfLengthField)])) {
            signature = null;
            return;
        }
        byte[] signatureAsByteArray = Arrays.copyOfRange(signedDataBlock, lengthOfDataPrecedingSignature, lengthOfDataBlockData + 1);
        signature = ECKey.ECDSASignature.decodeFromDER(signatureAsByteArray).encodeToDER();
    }

    /**
     * Fills the byte[] DataBlock with the values set in the corresponding variables.
     */
    private void variablesToDataBlock() {
        byte[] idOfWFAsArray = ByteBuffer.allocate(2).putShort(workflowHandoverData.getWorkflowInstance().getId()).array();
        byte[] taskIdAsArray = new byte[1];
        taskIdAsArray[0] = workflowHandoverData.getIdOfNextTask();
        byte[] unixTimestampBytes = ByteBuffer.allocate(4).putInt(workflowHandoverData.getHandoverTimeStamp()).array();
        dataBlock = runtimeVerificationUtils.copyByteArray(dataBlock, idOfWFAsArray, taskIdAsArray, unixTimestampBytes);
    }

    /**
     * Fills the variables with the values set in the corresponding byte[] DataBlock .
     */
    private void dataBlockToVariables(byte[] receivedDataBlock, final WorkflowHandoverType workflowHandoverType) {
        dataBlock = Arrays.copyOfRange(receivedDataBlock, sizeOfLengthField, (sizeOfLengthField + sizeOfDatablockField));
        short idOfWF = getShortOfBytes(dataBlock[0], dataBlock[1]);
        byte idOfNextTask = 0;
        //Ignore task field if it is of any other that intermediate
        if (workflowHandoverType == WorkflowHandoverType.INTERMEDIATE) {
            idOfNextTask = dataBlock[2];
        }
        int unixTimestamp = getIntOfBytes(
                dataBlock[3],
                dataBlock[4],
                dataBlock[5],
                dataBlock[6]);
        workflowHandoverData = new WorkflowHandoverData(
                new WorkflowInstance(idOfWF),
                idOfNextTask,
                workflowHandoverType,
                unixTimestamp);
    }

    /**
     * Converts two bytes into a short
     */
    private short getShortOfBytes(byte byte1, byte byte2) {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(byte1);
        bb.put(byte2);
        return bb.getShort(0);
    }

    /**
     * Converts four bytes into an integer
     */
    private int getIntOfBytes(byte byte1, byte byte2, byte byte3, byte byte4) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(byte1);
        bb.put(byte2);
        bb.put(byte3);
        bb.put(byte4);
        return bb.getInt(0);
    }

    /**
     * Returns the the signed data block of length 80bytes to be included into a handover transaction.
     * {lengthOfData:1byte}{wfDataBlock:7byte}{offChainSignature:~71byte}
     * Note: If no signature was added, a data block of data-length seven padded up with 0s to 80 will be added.
     */
    public byte[] serializeToSignedDataBlock(boolean shouldContainSignature) {
        if (shouldContainSignature && (signature == null || signature.length < DEC_SIGNATURE_MIN_BYTE_LENGTH)) {
            throw new RuntimeVerificationException("No signature information available.");
        }
        byte[] signedDataBlock = new byte[sizeOfHandoverBlock];
        byte[] lengthAsByteArray = new byte[sizeOfLengthField];
        int lengthOfSignature = 0;
        byte[] signatureToUse = signature;
        if (shouldContainSignature) {
            lengthOfSignature = signature == null ? 0 : signature.length;
        }
        if (!shouldContainSignature) {
            signatureToUse = null;
        }
        //always between 78-79 (data excl. length field), well below the maximum number of 127 that can be stored in a byte.
        lengthAsByteArray[0] = (byte) (dataBlock.length + lengthOfSignature);
        signedDataBlock = runtimeVerificationUtils.copyByteArray(signedDataBlock, lengthAsByteArray, dataBlock, signatureToUse);
        return signedDataBlock;
    }

    /**
     * Returns a workflow data block with a start marker attached.
     * {lengthOfData:1byte}{wfDataBlock:7byte}{wfStartMarker:wfStartMarker.getBytes().length}
     */
    public byte[] serializeToWorkflowStartBlock() {
        return serializeWorkflowBlockWithMarker(wfStartMarker);
    }

    /**
     * Returns a workflow data block with an end marker attached.
     * {lengthOfData:1byte}{wfDataBlock:7byte}{wfStartMarker:wfEndMarker.getBytes().length}
     */
    public byte[] serializeToWorkflowEndBlock() {
        return serializeWorkflowBlockWithMarker(wfEndMarker);
    }

    /**
     * Returns a workflow data block with an split marker attached.
     * {lengthOfData:1byte}{wfDataBlock:7byte}{wfStartMarker:wfEndMarker.getBytes().length}
     */
    public byte[] serializeToWorkflowSplitBlock() {
        return serializeWorkflowBlockWithMarker(wfSplitMarker);
    }

    /**
     * Returns a workflow data block with an join marker attached.
     * {lengthOfData:1byte}{wfDataBlock:7byte}{wfStartMarker:wfEndMarker.getBytes().length}
     */
    public byte[] serializeToWorkflowJoinBlock() {
        return serializeWorkflowBlockWithMarker(wfJoinMarker);
    }

    /**
     * Returns a workflow data block with a marker attached.
     * {lengthOfData:1byte}{wfDataBlock:7byte}{wfStartMarker:marker.getBytes().length}
     */
    private byte[] serializeWorkflowBlockWithMarker(String marker) {
        int lengthOfMarker = marker.getBytes().length;
        byte[] lengthAsByteArray = new byte[sizeOfLengthField];
        int lengthOfDataBlock = (dataBlock.length + lengthOfMarker);
        lengthAsByteArray[0] = (byte) lengthOfDataBlock;
        byte[] wfStartDataBlock = new byte[lengthOfDataBlock + 1];
        wfStartDataBlock = runtimeVerificationUtils.copyByteArray(wfStartDataBlock, lengthAsByteArray, dataBlock, marker.getBytes());
        return wfStartDataBlock;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public WorkflowHandoverData getWorkflowHandoverData() {
        return workflowHandoverData;
    }

    /**
     * Returns the determined DatablockType or throws a RuntimeVerificationException if no fitting type was found.
     */
    private WorkflowHandoverType determineDataBlockType(byte[] receivedDataBlock) {
        //check if datablock is of type start
        int startBlockLength = sizeOfLengthField + sizeOfDatablockField + wfStartMarker.getBytes().length;
        if (receivedDataBlock.length == startBlockLength && byteArrayEndsWith(receivedDataBlock, wfStartMarker.getBytes())) {
            return WorkflowHandoverType.START;
        }
        //check if datablock is of type end
        int endBlockLength = sizeOfLengthField + sizeOfDatablockField + wfEndMarker.getBytes().length;
        if (receivedDataBlock.length == endBlockLength && byteArrayEndsWith(receivedDataBlock, wfEndMarker.getBytes())) {
            return WorkflowHandoverType.END;
        }
        //check if datablock is of type join
        int joinBlockLength = sizeOfLengthField + sizeOfDatablockField + wfJoinMarker.getBytes().length;
        if (receivedDataBlock.length == joinBlockLength && byteArrayEndsWith(receivedDataBlock, wfJoinMarker.getBytes())) {
            return WorkflowHandoverType.JOIN;
        }
        //check if datablock is of type split
        int splitBlockLength = sizeOfLengthField + sizeOfDatablockField + wfSplitMarker.getBytes().length;
        if (receivedDataBlock.length == splitBlockLength && byteArrayEndsWith(receivedDataBlock, wfSplitMarker.getBytes())) {
            return WorkflowHandoverType.SPLIT;
        }
        if (receivedDataBlock.length == sizeOfHandoverBlock) {
            return WorkflowHandoverType.INTERMEDIATE;
        }
        throw new RuntimeVerificationException("Datablock did not fit a common datablockType.");
    }

    /**
     * Returns true if the second argument is a suffix to the first one.
     */
    private boolean byteArrayEndsWith(byte[] arrayToCheck, byte[] suffix) {
        if (arrayToCheck.length < suffix.length) {
            return false;
        }
        int startIndexOfSuffix = arrayToCheck.length - suffix.length;
        byte[] suffixToCompare = Arrays.copyOfRange(arrayToCheck, startIndexOfSuffix, arrayToCheck.length);
        return Arrays.equals(suffixToCompare, suffix);
    }
}
