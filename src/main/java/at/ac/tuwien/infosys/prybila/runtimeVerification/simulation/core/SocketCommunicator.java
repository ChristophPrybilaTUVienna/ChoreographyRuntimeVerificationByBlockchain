package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.BasicCryptographyManager;
import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.Identity;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.core.exceptions.HandoverFailureException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Sends and receives data from a socket.
 */
public class SocketCommunicator {

    public static final String lockSuccess = "lockSuccess";
    public static final String lockFail = "lockFail";

    /**
     * Message to send to partner when handover failed.
     */
    private static final String killHandoverMessage = "KILL_COMMAND";

    private Socket socket;
    public ObjectInputStream in;
    public ObjectOutputStream out;
    private BasicCryptographyManager basicCryptographyManager;
    private Identity partnerIdentity;

    public SocketCommunicator(BasicCryptographyManager basicCryptographyManager, Socket socket) {
        this.basicCryptographyManager = basicCryptographyManager;
        this.socket = socket;
    }

    public void openConnection() throws IOException {
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
    }


    public void closeConnection() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                //ignore
            }
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                //ignore
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                //ignore
            }
        }
    }

    public Object receiveObject() throws IOException, ClassNotFoundException, HandoverFailureException {
        Object receivedObject = in.readObject();
        testForCommunicationKillCommand(receivedObject);
        return receivedObject;
    }

    private void testForCommunicationKillCommand(Object receivedObject) throws HandoverFailureException {
        if (receivedObject instanceof String) {
            String receivedString = (String) receivedObject;
            if (receivedString.equals(killHandoverMessage)) {
                throw new HandoverFailureException("The communication was ended by the communication partner " + partnerIdentity);
            }
        }
    }

    public void sendObject(Object object) throws IOException {
        out.writeObject(object);
        out.flush();
    }

    public void sendHandoverError() throws IOException {
        sendObject(killHandoverMessage);
    }

    public void sendDataWithSignature(byte[] data) throws IOException {
        String signatureOfData = basicCryptographyManager.getSignatureOfData(data);
        sendObject(data);
        sendObject(signatureOfData);
    }

    public byte[] receiveDataWithSignature() throws IOException, ClassNotFoundException, HandoverFailureException {
        byte[] receivedData = (byte[]) receiveObject();
        String signatureOfData = (String) receiveObject();
        if (!basicCryptographyManager.verifySignature(receivedData, signatureOfData, partnerIdentity.getPublicKey())) {
            throw new HandoverFailureException("The signature during Bitcoin address sharing did not validate.");
        }
        return receivedData;
    }

    public void setPartnerIdentity(Identity partnerIdentity) {
        this.partnerIdentity = partnerIdentity;
    }
}
