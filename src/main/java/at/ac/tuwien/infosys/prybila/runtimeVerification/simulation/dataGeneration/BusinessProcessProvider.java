package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.dataGeneration;

import at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription.*;

import java.util.*;

/**
 * Defines the well known business process models that are used in the simulation.
 */
public class BusinessProcessProvider {

    public static final String SIMPLE_BP = "Simple_BP";
    public static final String MEDIUM_ANDSPLIT_BP = "Medium_BP_AndSplit";
    public static final String MEDIUM_XORSPLIT_BP = "Medium_BP_XORSplit";
    public static final String COMPLEX_BP = "Complex_BP";

    private Map<String, BusinessProcessDescription> businessProcessDescriptions;

    public BusinessProcessProvider() {
        businessProcessDescriptions = new HashMap<>();

        //Complex BP
        BusinessProcessDescription complexBP = getComplexBP();
        businessProcessDescriptions.put(complexBP.getName(), complexBP);

        //Medicore BP XOR_SPLIT variant
        BusinessProcessDescription mediocreXORSplitBP = getMediumXORSplitBP();
        businessProcessDescriptions.put(mediocreXORSplitBP.getName(), mediocreXORSplitBP);

        //Medicore BP AND_SPLIT variant
        BusinessProcessDescription mediocreAndSplitBP = getMediumAndSplitBP();
        businessProcessDescriptions.put(mediocreAndSplitBP.getName(), mediocreAndSplitBP);

        //Simple BP
        BusinessProcessDescription simpleBP = getSimpleBP();
        businessProcessDescriptions.put(simpleBP.getName(), simpleBP);
    }

    public BusinessProcessDescription getBusinessProcessDescription(String bpName) {
        return businessProcessDescriptions.get(bpName);
    }

    public BusinessProcessDescription getBusinessProcessDescriptionClone(String bpName) {
        switch (bpName) {
            case COMPLEX_BP:
                return getComplexBP();
            case SIMPLE_BP:
                return getSimpleBP();
            case MEDIUM_ANDSPLIT_BP:
                return getMediumAndSplitBP();
            case MEDIUM_XORSPLIT_BP:
                return getMediumXORSplitBP();
        }
        return null;
    }

    private BusinessProcessDescription getComplexBP() {
        BusinessProcessElement.idCounter = 0;
        Start startOfComplex = new Start();
        AndSplit andSplit = startOfComplex.addActivity("Task A").addAndSplit(2);

        //Path A
        List<BusinessProcessElement> childrenAfterXORPathA = createXORSplitAndAddActivities(andSplit, "Task B", "Task C");
        XORJoin pathA = createXORJoinFromOriginatingElements(0, childrenAfterXORPathA);

        //Path B
        Activity pathB = andSplit.addActivity("Task D", 1);

        AndJoin andJoin = createAndJoinFromOriginatingElements(0, Arrays.asList(pathA, pathB));
        andJoin.addActivity("Task E").addEnd();
        BusinessProcessDescription complexBP = new BusinessProcessDescription(COMPLEX_BP);
        complexBP.setStart(startOfComplex);
        return complexBP;
    }

    private BusinessProcessDescription getMediumXORSplitBP() {
        BusinessProcessElement.idCounter = 0;
        Start startOfMediumXOR = new Start();

        List<BusinessProcessElement> childActivities
                = createXORSplitAndAddActivities(startOfMediumXOR.addActivity("Task A"), "Task B", "Task C");

        XORJoin xorJoinOfMediumAnd = createXORJoinFromOriginatingElements(0, childActivities);
        xorJoinOfMediumAnd.addActivity("Task D").addEnd();

        BusinessProcessDescription mediocreXORSplitBP = new BusinessProcessDescription(MEDIUM_XORSPLIT_BP);
        mediocreXORSplitBP.setStart(startOfMediumXOR);
        return mediocreXORSplitBP;
    }

    private BusinessProcessDescription getMediumAndSplitBP() {
        BusinessProcessElement.idCounter = 0;
        Start startOfMediumAnd = new Start();

        List<BusinessProcessElement> childActivities
                = createAndSplitAndAddActivities(startOfMediumAnd.addActivity("Task A"), "Task B", "Task C");
        AndJoin andJoinOfMediumAnd = createAndJoinFromOriginatingElements(0, childActivities);

        andJoinOfMediumAnd.addActivity("Task D").addEnd();

        BusinessProcessDescription mediocreAndSplitBP = new BusinessProcessDescription(MEDIUM_ANDSPLIT_BP);
        mediocreAndSplitBP.setStart(startOfMediumAnd);
        return mediocreAndSplitBP;
    }

    private BusinessProcessDescription getSimpleBP() {
        BusinessProcessElement.idCounter = 0;
        Start startOfSimple = new Start();
        startOfSimple
                .addActivity("Task A")
                .addActivity("Task B")
                .addActivity("Task C")
                .addEnd();

        BusinessProcessDescription simpleBP = new BusinessProcessDescription(SIMPLE_BP);
        simpleBP.setStart(startOfSimple);
        return simpleBP;
    }

    private List<BusinessProcessElement> createXORSplitAndAddActivities(BusinessProcessElement originatingElement, String... tasks) {
        XORSplit xorSplit = originatingElement.addXORSplit(tasks.length);
        return addChildrenActivitiesToElement(xorSplit, tasks);
    }

    private List<BusinessProcessElement> createAndSplitAndAddActivities(BusinessProcessElement originatingElement, String... tasks) {
        AndSplit andSplit = originatingElement.addAndSplit(tasks.length);
        return addChildrenActivitiesToElement(andSplit, tasks);
    }

    private AndJoin createAndJoinFromOriginatingElements(int precedingIndex, List<BusinessProcessElement> parents) {
        AndJoin andJoin = null;
        for (int i = 0; i < parents.size(); i++) {
            if (andJoin == null) {
                andJoin = parents.get(i).addAndJoin(i, precedingIndex, parents.size());
            } else {
                andJoin.addPrecedingElement(i, precedingIndex, parents.get(i));
            }
        }
        return andJoin;
    }

    private XORJoin createXORJoinFromOriginatingElements(int precedingIndex, List<BusinessProcessElement> parents) {
        XORJoin xorJoin = null;
        for (int i = 0; i < parents.size(); i++) {
            if (xorJoin == null) {
                xorJoin = parents.get(i).addXORJoin(i, precedingIndex, parents.size());
            } else {
                xorJoin.addPrecedingElement(i, precedingIndex, parents.get(i));
            }
        }
        return xorJoin;
    }

    private List<BusinessProcessElement> addChildrenActivitiesToElement(BusinessProcessElement splitElement, String... tasks) {
        List<BusinessProcessElement> children = new ArrayList<>();
        for (int i = 0; i < tasks.length; i++) {
            children.add(splitElement.addActivity(tasks[i], i));
        }
        return children;
    }
}
