package at.ac.tuwien.infosys.prybila.runtimeVerification.utils;

import at.ac.tuwien.infosys.prybila.runtimeVerification.handoverFramework.core.model.WorkflowGraphStep;
import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.BusinessProcessElement;
import org.bitcoinj.core.Coin;

import java.io.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

/**
 * Utils class containing common usage methods.
 * Should be kept small.
 */
public class RuntimeVerificationUtils {

    /**
     * Throws a RuntimeVerificationException when the given Object is null.
     */
    public void notNull(Object object, String msg) {
        if (object == null) {
            throw new RuntimeVerificationException(msg);
        }
    }

    /**
     * Throws a RuntimeVerificationException when the given Object is null.
     */
    public void notNull(Object object) {
        notNull(object, "The supplied object was null.");
    }

    public void notEmpty(List list, String msg) {
        if (list.isEmpty()) {
            throw new RuntimeVerificationException(msg);
        }
    }

    public void notEmpty(List list) {
        notEmpty(list, "The supplied list was empty.");
    }

    public void notEmpty(Map map, String msg) {
        if (map.isEmpty()) {
            throw new RuntimeVerificationException(msg);
        }
    }

    public void notEmpty(Map map) {
        notEmpty(map, "The supplied map was empty.");
    }

    /**
     * Returns true if the given file exists.
     */
    public boolean fileExists(File file) {
        return file != null && file.exists();
    }

    public byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public String byteArrayToHexString(byte[] array) {
        StringBuilder sb = new StringBuilder();
        for (byte b : array) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public String generateRandomStringWithLength(Random random, int length) {
        //courtesies to http://www.baeldung.com/java-random-string
        int leftLimit = 97;
        int rightLimit = 122;
        int targetStringLength = length;
        StringBuilder buffer = new StringBuilder(targetStringLength);
        for (int i = 0; i < targetStringLength; i++) {
            int randomLimitedInt = leftLimit + (int)
                    (random.nextFloat() * (rightLimit - leftLimit));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }

    /**
     * Sets the private field of an instance with the given fieldvalue.
     */
    public <T> void setPrivateFieldOfObject(T instance, String privateFieldName, Object fieldValue) throws NoSuchFieldException, IllegalAccessException {
        Field privateField = findPrivateFieldOfClass(instance.getClass(), privateFieldName);
        privateField.setAccessible(true);
        privateField.set(instance, fieldValue);
    }

    /**
     * Searches for a private field in the given class and its ancestors.
     * Returns null if none is found.
     */
    private Field findPrivateFieldOfClass(Class<?> clazz, String privateFieldName) {
        try {
            return clazz.getDeclaredField(privateFieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findPrivateFieldOfClass(clazz.getSuperclass(), privateFieldName);
            }
        }
        return null;
    }

    /**
     * Returns the current time as a unix timestamp
     */
    public int getCurrentTimeInUnixTimestamp() {
        return (int) (Calendar.getInstance().getTimeInMillis() / 1000L);
    }

    /**
     * Creates a list which is an equal distribution of the given token #splitNum times.
     */
    public List<Coin> distributeCoinEqually(Coin token, int splitNum) {
        List<Coin> result = new ArrayList<>();
        Coin totalValue = Coin.ZERO;
        Coin portion = token.div(splitNum);
        while (token.minus(totalValue).compareTo(portion) >= 0) {
            result.add(portion);
            totalValue = totalValue.add(portion);
        }
        if (totalValue.isLessThan(token)) {
            Coin rest = token.minus(totalValue);
            Coin newLatestCoin = result.get(result.size() - 1).add(rest);
            result.remove(result.size() - 1);
            result.add(newLatestCoin);
        }
        return result;
    }

    /**
     * Collect all elements of the graph into a list
     */
    public List<BusinessProcessElement> graphToList(BusinessProcessElement graphRoot) {
        List<BusinessProcessElement> list = new ArrayList<>();
        Set<Byte> foundTx = new HashSet<>();
        Queue<BusinessProcessElement> uncheckedNodes = new LinkedList<>();
        uncheckedNodes.add(graphRoot);
        addToSet(graphRoot, foundTx);
        list.add(graphRoot);
        //first try to find an instance that does not need updating
        while (!uncheckedNodes.isEmpty()) {
            BusinessProcessElement currentStep = uncheckedNodes.poll();
            if (!isInSet(currentStep, foundTx)) {
                list.add(currentStep);
                addToSet(currentStep, foundTx);
            }
            if (currentStep.getFollowingElements() != null) {
                uncheckedNodes.addAll(Arrays.asList(currentStep.getFollowingElements()));
            }
        }
        return list;
    }

    private boolean isInSet(BusinessProcessElement businessProcessElement, Set<Byte> set) {
        if (businessProcessElement != null) {
            return set.contains(businessProcessElement.getId());
        }
        return false;
    }

    private void addToSet(BusinessProcessElement businessProcessElement, Set<Byte> set) {
        if (businessProcessElement != null) {
            set.add(businessProcessElement.getId());
        }
    }

    /**
     * Collect all elements of the graph into a list
     */
    public List<WorkflowGraphStep> graphToList(WorkflowGraphStep graphRoot) {
        List<WorkflowGraphStep> list = new ArrayList<>();
        if (graphRoot == null) {
            return list;
        }
        Set<Integer> foundIds = new HashSet<>();
        Queue<WorkflowGraphStep> uncheckedNodes = new LinkedList<>();
        uncheckedNodes.add(graphRoot);
        addToSet(graphRoot, foundIds);
        list.add(graphRoot);
        //first try to find an instance that does not need updating
        while (!uncheckedNodes.isEmpty()) {
            WorkflowGraphStep currentStep = uncheckedNodes.poll();
            if (!isInSet(currentStep, foundIds)) {
                list.add(currentStep);
                addToSet(currentStep, foundIds);
            }
            if (currentStep.getChildren() != null) {
                uncheckedNodes.addAll(currentStep.getChildren());
            }
        }
        return list;
    }

    private boolean isInSet(WorkflowGraphStep graphStep, Set<Integer> set) {
        if (graphStep != null) {
            return set.contains(graphStep.getId());
        }
        return false;
    }

    private void addToSet(WorkflowGraphStep graphStep, Set<Integer> set) {
        if (graphStep != null) {
            set.add(graphStep.getId());
        }
    }

    /**
     * Copies the given byte[] list to the first byte[].
     * Remaining free bytes are not touched.
     */
    public byte[] copyByteArray(byte[] to, byte[]... from) {
        ByteBuffer target = ByteBuffer.wrap(to);
        for (byte[] bytesToCopy : from) {
            if (bytesToCopy != null) {
                target.put(bytesToCopy);
            }
        }
        return target.array();
    }

    public PublicKey readPublicKeyFromFile(String pathToPublicKey) throws IOException {
        ObjectInputStream oin =
                new ObjectInputStream(new BufferedInputStream(new FileInputStream(pathToPublicKey)));
        try {
            BigInteger m = (BigInteger) oin.readObject();
            BigInteger e = (BigInteger) oin.readObject();
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(m, e);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            PublicKey pubKey = fact.generatePublic(keySpec);
            return pubKey;
        } catch (Exception e) {
            throw new RuntimeException("Spurious serialisation error", e);
        } finally {
            oin.close();
        }
    }

    public PrivateKey readPrivateKeyFromFile(String pathToPublicKey) throws IOException {
        ObjectInputStream oin =
                new ObjectInputStream(new BufferedInputStream(new FileInputStream(pathToPublicKey)));
        try {
            BigInteger m = (BigInteger) oin.readObject();
            BigInteger e = (BigInteger) oin.readObject();
            RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(m, e);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            PrivateKey privKey = fact.generatePrivate(keySpec);
            return privKey;
        } catch (Exception e) {
            throw new RuntimeException("Spurious serialisation error", e);
        } finally {
            oin.close();
        }
    }

    public String readCrawlerTokenFromProperties() {
        try {
            ResourceBundle propertyFile = ResourceBundle.getBundle("crawler");
            return propertyFile.getString("token");
        } catch (Exception e) {
            return null;
        }
    }

    public int readCrawlerThrottlingFromProperties() {
        try {
            ResourceBundle propertyFile = ResourceBundle.getBundle("crawler");
            return Integer.parseInt(propertyFile.getString("waitForMS"));
        } catch (Exception e) {
            return 0;
        }
    }
}
