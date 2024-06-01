package data;

import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.*;
import java.util.stream.Collectors;

public class ModelGraph {
    public static final Collection<Edge.Type> ALL_EDGES =
            Arrays.asList(Edge.Type.MESSAGE, Edge.Type.SEQUENCE);
    public static final Collection<Edge.Type> MESSAGE_ONLY = List.of(Edge.Type.MESSAGE);
    public static final Collection<Edge.Type> SEQUENCE_ONLY = List.of(Edge.Type.SEQUENCE);

    public static class Node {
        private final FlowNode modelNode;
        private final Collection<Edge> incoming = new ArrayList<>();
        private final Collection<Edge> outgoing = new ArrayList<>();

        public Node(FlowNode modelNode) {
            this.modelNode = modelNode;
        }

        public FlowNode getModelNode() {
            return modelNode;
        }
    }

    public record Edge(Node source, Node target, Type type) {
        public enum Type {
            SEQUENCE,
            MESSAGE
        }

        public Node getOpposite(Node node) {
            if (source.equals(node)) {
                return target;
            }
            if (target.equals(node)) {
                return source;
            }
            throw new IllegalArgumentException("Given node is neither source nor target of edge.");
        }
    }

    public enum Direction {
        FORWARD,
        BACKWARD,
        BOTH
    }

    public record TraversalStep(Node node, int depth) {
    }

    static class BreadthFirstTraversal implements Iterator<TraversalStep> {
        private final List<TraversalStep> current = new ArrayList<>();
        private final Collection<Node> visited = new ArrayList<>();
        private final Direction direction;
        private final Collection<Edge.Type> followEdgeTypes;

        public BreadthFirstTraversal(Collection<Node> starts, Direction direction,
                                     Collection<Edge.Type> followEdgeTypes) {
            this.direction = direction;
            this.followEdgeTypes = followEdgeTypes;

            for (Node start : starts) {
                current.add(new TraversalStep(start, 0));
                visited.add(start);
            }
        }

        @Override
        public boolean hasNext() {
            return !current.isEmpty();
        }

        @Override
        public TraversalStep next() {
            TraversalStep next = current.remove(0);

            Collection<Edge> edges;
            switch (direction) {
                case FORWARD -> edges = next.node().outgoing;
                case BACKWARD -> edges = next.node().incoming;
                case BOTH -> {
                    edges = new ArrayList<>();
                    edges.addAll(next.node().incoming);
                    edges.addAll(next.node().outgoing);
                }
                default -> throw new IllegalArgumentException(String.format("Unhandled direction %s", direction));
            }

            edges = edges.stream().filter(e -> followEdgeTypes.contains(e.type)).collect(Collectors.toList());

            for (Edge edge : edges) {
                Node neighbour = edge.getOpposite(next.node);
                if(visited.contains(neighbour)) {
                    continue;
                }
                visited.add(neighbour);
                current.add(new TraversalStep(neighbour, next.depth + 1));
            }

            return next;
        }
    }

    private final HashMap<String, Node> nodes = new HashMap<>();

    public ModelGraph(Collection<Node> nodes) {
        for (Node node : nodes) {
            this.nodes.put(node.modelNode.getId(), node);
        }
    }

    public ModelGraph(ModelInstance modelInstance) {
        for (FlowNode flowNode : modelInstance.getModelElementsByType(FlowNode.class)) {
            nodes.put(flowNode.getId(), new Node(flowNode));
        }
        for (SequenceFlow sequenceFlow : modelInstance.getModelElementsByType(SequenceFlow.class)) {
            Node source = nodes.get(sequenceFlow.getSource().getId());
            Node target = nodes.get(sequenceFlow.getTarget().getId());
            Edge edge = new Edge(source, target, Edge.Type.SEQUENCE);
            source.outgoing.add(edge);
            target.incoming.add(edge);
        }
        for (MessageFlow messageFlow : modelInstance.getModelElementsByType(MessageFlow.class)) {
            Node source = nodes.get(messageFlow.getSource().getId());
            Node target = nodes.get(messageFlow.getTarget().getId());
            Edge edge = new Edge(source, target, Edge.Type.MESSAGE);
            source.outgoing.add(edge);
            target.incoming.add(edge);
        }
    }

    public Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Iterator<TraversalStep> traversal(Collection<Edge.Type> followEdgeTypes, Direction direction) {
        Collection<Node> starts = findStarts(followEdgeTypes, direction);
        if (starts.size() == 0) {
            throw new IllegalArgumentException("Process has no definitive start, this is currently unsupported.");
        }
        return breadthFirstTraversal(starts, direction, followEdgeTypes);
    }

    public Iterator<TraversalStep> traversal(Collection<Node> starts,
                                             Collection<Edge.Type> followEdgeTypes,
                                             Direction direction) {
        return breadthFirstTraversal(starts, direction, followEdgeTypes);
    }

    public Collection<ModelGraph> getSubGraphs(Collection<Edge.Type> followEdgeTypes) {
        Collection<ModelGraph> graphs = new ArrayList<>();
        List<Node> unseen = new ArrayList<>(nodes.values());

        Collection<Node> starts = findStarts(followEdgeTypes, Direction.FORWARD);
        for (Node start : starts) {
            if (!unseen.contains(start)) {
                // start was already seen
                continue;
            }

            ModelGraph subGraph = buildSubGraph(start, followEdgeTypes);
            for (Node value : subGraph.nodes.values()) {
                unseen.remove(value);
            }
            graphs.add(subGraph);
        }

        while (!unseen.isEmpty()) {
            ModelGraph subGraph = buildSubGraph(unseen.get(0), followEdgeTypes);
            for (Node value : subGraph.nodes.values()) {
                unseen.remove(value);
            }
            graphs.add(subGraph);
        }

        return graphs;
    }

    private ModelGraph buildSubGraph(Node start,
                                     Collection<Edge.Type> followEdgeTypes) {
        Iterator<TraversalStep> traversal = traversal(List.of(start), followEdgeTypes, Direction.BOTH);
        Collection<Node> nodes = new ArrayList<>();
        while (traversal.hasNext()) {
            TraversalStep step = traversal.next();
            nodes.add(step.node);
        }

        return new ModelGraph(nodes);
    }

    private Collection<Node> findStarts(Collection<Edge.Type> followEdgeTypes, Direction direction) {
        switch (direction) {
            case FORWARD -> {
                return nodes.values()
                        .stream()
                        .filter(n -> n.incoming.stream().noneMatch(e -> followEdgeTypes.contains(e.type)))
                        .toList();
            }
            case BACKWARD -> {
                return nodes.values()
                        .stream()
                        .filter(n -> n.outgoing.stream().noneMatch(e -> followEdgeTypes.contains(e.type)))
                        .toList();
            }

            case BOTH -> {
                return nodes.values()
                        .stream()
                        .filter(n -> n.outgoing.stream().noneMatch(e -> followEdgeTypes.contains(e.type))
                                && n.incoming.stream().noneMatch(e -> followEdgeTypes.contains(e.type)))
                        .toList();
            }
            default -> throw new IllegalArgumentException(String.format("Unhandled direction %s.\n", direction));
        }
    }

    public Map<Integer, Collection<FlowNode>> getNodesByDepth(Collection<Edge.Type> followEdgeTypes) {
        Iterator<TraversalStep> traversal = traversal(followEdgeTypes, Direction.FORWARD);
        Map<Integer, Collection<FlowNode>> nodesByDepth = new HashMap<>();
        while (traversal.hasNext()) {
            TraversalStep step = traversal.next();
            if (!nodesByDepth.containsKey(step.depth)) {
                nodesByDepth.put(step.depth, new ArrayList<>());
            }
            nodesByDepth.get(step.depth).add(step.node.modelNode);
        }
        return nodesByDepth;
    }

    private Iterator<TraversalStep> breadthFirstTraversal(
            Collection<Node> starts,
            Direction direction,
            Collection<Edge.Type> followEdgeTypes
    ) {
        return new BreadthFirstTraversal(starts, direction, followEdgeTypes);
    }
}
