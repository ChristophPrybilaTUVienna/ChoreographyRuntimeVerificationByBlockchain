package at.ac.tuwien.infosys.prybila.runtimeVerification.simulation.model.businessProcessDescription;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;

import java.util.*;

/**
 * Simplified representation of a business process model for simulation purposes.
 * The model is limited to one BP-start, one BP-end, and an arbitrary number of steps between them.
 * This steps can be of type activity, and-split, and-join, xor-split and xor join.
 */
public class BusinessProcessDescription {

    private String name;

    private Start start;

    public BusinessProcessDescription(String name) {
        this.name = name;
    }

    public Start getStart() {
        return start;
    }

    public void setStart(Start start) {
        this.start = start;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BusinessProcessDescription that = (BusinessProcessDescription) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return start != null ? start.equals(that.start) : that.start == null;

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (start != null ? start.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        String process = getProcessAsString();
        return "BusinessProcessDescription{" +
                "name='" + name + '\'' +
                "processPaths=" + process +
                '}';
    }

    private String getProcessAsString() {
        if (start == null) {
            return null;
        }
        StringBuffer buffer = new StringBuffer();
        Stack<BusinessProcessElement> elementStack = new Stack<>();
        elementStack.push(start);
        while (!elementStack.empty()) {
            buffer.append("\n");
            BusinessProcessElement currentElement = elementStack.pop();
            if(currentElement == null) {
                continue;
            }
            buffer.append(currentElement.toString());
            if (currentElement.followingElements != null) {
                if(currentElement instanceof XORSplit && ((XORSplit)currentElement).getIndexOfPathToTake() >= 0) {
                    elementStack.push(currentElement.followingElements[((XORSplit)currentElement).getIndexOfPathToTake()]);
                } else {
                    for (BusinessProcessElement child : currentElement.followingElements) {
                        elementStack.push(child);
                    }
                }
            }
        }
        return buffer.toString();
    }

    public Graph getVisualization() {
        Graph graph = new SingleGraph(name);
        List<BusinessProcessElement> graphAsList = getElementsOfGraphAsList();
        for (BusinessProcessElement currentElement : graphAsList) {
            Node n = graph.addNode("" + currentElement.id);
            String label;
            if (currentElement instanceof Activity) {
                label = ((Activity) currentElement).getName();
            } else {
                label = currentElement.getType().name();
            }
            n.addAttribute("ui.label", label);
        }

        for (BusinessProcessElement currentElement : graphAsList) {
            if (currentElement.followingElements != null) {
                for (BusinessProcessElement child : currentElement.followingElements) {
                    graph.addEdge("" + currentElement.id + "" + child.id, "" + currentElement.id, "" + child.id);
                }
            }
        }
        return graph;
    }

    private List<BusinessProcessElement> getElementsOfGraphAsList() {
        List<BusinessProcessElement> elements = new ArrayList<>();
        Set<Byte> foundIds = new HashSet<>();
        Queue<BusinessProcessElement> elementsToProcess = new LinkedList<>();
        elementsToProcess.add(start);
        while (!elementsToProcess.isEmpty()) {
            BusinessProcessElement currentElement = elementsToProcess.poll();
            if (foundIds.contains(currentElement.id)) {
                continue;
            }
            foundIds.add(currentElement.id);
            elements.add(currentElement);
            if (currentElement.followingElements != null) {
                for (BusinessProcessElement child : currentElement.followingElements) {
                    if (!elementsToProcess.contains(child)) {
                        elementsToProcess.add(child);
                    }
                }
            }
        }

        return elements;
    }
}
