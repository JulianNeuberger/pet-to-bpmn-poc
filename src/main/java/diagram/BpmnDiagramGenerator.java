package diagram;

import data.ModelGraph;
import diagram.path.BpmnEdgePathing;
import exception.GenerationException;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.bpmndi.*;
import org.camunda.bpm.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.bpmn.instance.di.Diagram;
import org.camunda.bpm.model.bpmn.instance.di.DiagramElement;
import org.camunda.bpm.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.ModelInstance;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class BpmnDiagramGenerator implements DiagramGenerator {
    protected Map<FlowNode, BpmnShape> flowNodeToShape = new HashMap<>();
    protected Map<Process, BpmnShape> processToShape = new HashMap<>();
    protected Map<DiagramElement, Collection<BpmnEdge>> relatedEdges = new HashMap<>();

    protected HashMap<DataObjectReference, BpmnShape> dataObjects = new HashMap<>();

    protected Map<Process, Participant> processToParticipant = new HashMap<>();

    protected static final Map<Class<? extends BaseElement>, Vector2> shapeSizes = new HashMap<>();

    static {
        shapeSizes.put(Task.class, new Vector2(100, 80));
        shapeSizes.put(Event.class, new Vector2(36, 36));
        shapeSizes.put(Gateway.class, new Vector2(50, 50));
    }

    public BpmnDiagramGenerator() {
    }

    @Override
    public void addDiagramToModel(BpmnModelInstance modelInstance) {
        Diagram diag = getDiagramForModelInstance(modelInstance);
        Definitions definitions = modelInstance.getDefinitions();
        definitions.addChildElement(diag);
    }

    public Diagram getDiagramForModelInstance(BpmnModelInstance modelInstance) {
        ModelGraph modelGraph = new ModelGraph(modelInstance);

        BpmnDiagram diagram = modelInstance.newInstance(BpmnDiagram.class);
        Definitions definitions = modelInstance.getDefinitions();
        definitions.addChildElement(diagram);

        BpmnPlane plane = modelInstance.newInstance(BpmnPlane.class);
        diagram.addChildElement(plane);

        findParticipants(definitions);

        addProcessesToDiagram(plane, modelInstance);

        Iterator<ModelGraph.TraversalStep> modelTraversal = modelGraph.traversal(ModelGraph.ALL_EDGES, ModelGraph.Direction.FORWARD);

        // layout elements on x axis
        int maxWidth = 0;
        while (modelTraversal.hasNext()) {
            ModelGraph.TraversalStep step = modelTraversal.next();
            FlowNode flowNode = step.node().getModelNode();
            BpmnShape shape = this.addFlowNodeToPlane(flowNode, plane, modelInstance);
            int x = step.depth() * 150;
            // add left margin
            x += 50;
            translateElement(shape, x, 0, modelInstance);
            maxWidth = Math.max(x + 150, maxWidth);
            this.flowNodeToShape.put(flowNode, shape);

            if (flowNode instanceof Task task) {
                addDataAssociations(task, plane, modelInstance);
            }
        }
        // add right margin
        maxWidth += 15;

        // layout elements on y axis
        int padding = 25;

        Map<Integer, Collection<FlowNode>> nodesByDepth = modelGraph.getNodesByDepth(ModelGraph.ALL_EDGES);
        Map<Process, Integer> processHeights = new HashMap<>();
        for (Process process : processToShape.keySet()) {
            Map<Integer, Collection<FlowNode>> processNodesByDepth = new HashMap<>();
            for (Integer depth : nodesByDepth.keySet()) {
                Collection<FlowNode> flowNodes = nodesByDepth.get(depth).stream().filter(n -> n.getParentElement().equals(process)).toList();
                if (flowNodes.size() == 0) {
                    continue;
                }

                processNodesByDepth.put(depth, flowNodes);
            }

            processHeights.put(process, 0);

            Map<Integer, Integer> nodesHeightByDepth = new HashMap<>();
            for (int depth : processNodesByDepth.keySet()) {
                Collection<FlowNode> nodes = processNodesByDepth.get(depth);
                int nodesHeight = nodes.stream()
                        .filter(n -> n.getParentElement().equals(process))
                        .map(n -> flowNodeToShape.get(n))
                        .map(s -> s.getBounds().getHeight() + padding)
                        .map(Double::intValue)
                        .reduce(0, Integer::sum);
                nodesHeightByDepth.put(depth, nodesHeight);
            }

            int maxNodesHeight = nodesHeightByDepth
                    .values()
                    .stream()
                    .max(Integer::compare)
                    .orElse(0);

            int height = Math.max(processHeights.get(process), maxNodesHeight);
            height = Math.max(200, height);
            processHeights.put(process, height);
            System.out.format("Process height is now %d\n", height);

            for (Integer depth : processNodesByDepth.keySet().stream().sorted().toList()) {
                double curHeight = padding + height / 2d - nodesHeightByDepth.get(depth) / 2d;
                Collection<FlowNode> nodes = processNodesByDepth.get(depth);
                for (FlowNode node : nodes) {
                    BpmnShape shape = this.flowNodeToShape.get(node);
                    System.out.format(
                            "Translating shape \"%s\" (node id: %s) (child of %s) by %.2fpx.\n",
                            node.getName(),
                            node.getId(),
                            process.getId(),
                            curHeight
                    );
                    translateElement(shape, 0, curHeight, modelInstance);
                    curHeight += shape.getBounds().getHeight();
                    // padding between shapes
                    curHeight += padding;
                }
            }
        }

        int curPoolOffset = 0;
        for (Process process : processToShape.keySet()) {
            System.out.format("Layouting subgraph for process with \n");

            BpmnShape pool = processToShape.get(process);
            System.out.format(
                    "Translating pool %s (for BPMN element of type %s) by %dpx.\n",
                    pool.getId(),
                    process.getClass().getName(),
                    curPoolOffset
            );

            int innerHeight = processHeights.get(process);
            int outerHeight = innerHeight + padding;

            Bounds poolBounds = pool.getBounds();
            poolBounds.setWidth(maxWidth);
            poolBounds.setHeight(outerHeight);

            double y = curPoolOffset;

            // shift pool element
            translateElement(pool, 0, y, modelInstance);

            // shift pool child elements
            for (FlowNode flowNode : process.getChildElementsByType(FlowNode.class)) {
                BpmnShape shape = flowNodeToShape.get(flowNode);
                translateElement(shape, 0, y, modelInstance);
            }

            curPoolOffset += outerHeight;
            curPoolOffset += 25;
        }

        // add sequence flows
        for (SequenceFlow flow : modelInstance.getModelElementsByType(SequenceFlow.class)) {
            this.addSequenceFlowToPlane(flow, plane, modelInstance);
        }

        // add messages between lanes
        Collaboration collaboration = (Collaboration) definitions.getUniqueChildElementByType(Collaboration.class);
        for (MessageFlow flow : collaboration.getMessageFlows()) {
            this.addMessageFlowToPlane(flow, plane, modelInstance);
        }

        // text annotations
        Map<TextAnnotation, BpmnShape> shapeByTextAnnotation = new HashMap<>();
        Collection<TextAnnotation> textAnnotations = collaboration.getChildElementsByType(TextAnnotation.class);
        for (TextAnnotation textAnnotation : textAnnotations) {
            BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
            shape.setBpmnElement(textAnnotation);
            Bounds shapeBounds = modelInstance.newInstance(Bounds.class);
            shapeBounds.setWidth(100);
            shapeBounds.setHeight(41);
            shapeBounds.setX(0);
            shapeBounds.setY(0);
            shape.setBounds(shapeBounds);
            plane.addChildElement(shape);
            shapeByTextAnnotation.put(textAnnotation, shape);
        }

        Collection<Association> associations = collaboration.getChildElementsByType(Association.class);
        for (Association association : associations) {
            BpmnShape flowNode = flowNodeToShape.get((Task) association.getSource());
            Bounds flowNodeBounds = flowNode.getBounds();
            BpmnShape annotation = shapeByTextAnnotation.get((TextAnnotation) association.getTarget());
            Bounds annotationBounds = annotation.getBounds();
            annotationBounds.setX(flowNodeBounds.getX() - 50);
            annotationBounds.setY(flowNodeBounds.getY() + flowNodeBounds.getHeight() + 15);

            this.addEdgeForElement(
                    association,
                    flowNode,
                    annotation,
                    plane,
                    modelInstance
            );
        }

        return diagram;
    }

    protected BpmnShape addDataObjectToDiagram(
            Task anchorTask,
            DataObjectReference dataObjectRef,
            BpmnPlane plane,
            BpmnModelInstance modelInstance
    ) {
        BpmnShape object = modelInstance.newInstance(BpmnShape.class);
        object.setBpmnElement(dataObjectRef);

        Bounds anchorBounds = anchorTask.getDiagramElement().getBounds();
        Vector2 anchor = new Vector2(anchorBounds.getX() + anchorBounds.getWidth() / 2d, anchorBounds.getY() - 50 - 25);

        Bounds objectBounds = modelInstance.newInstance(Bounds.class);
        objectBounds.setX(anchor.getX());
        objectBounds.setY(anchor.getY());
        objectBounds.setWidth(36);
        objectBounds.setHeight(50);
        object.setBounds(objectBounds);

        plane.addChildElement(object);

        return object;
    }

    protected void addProcessesToDiagram(BpmnPlane plane, BpmnModelInstance modelInstance) {
        for (Process process : modelInstance.getModelElementsByType(Process.class)) {
            System.out.format("Adding pool for process \"%s\".\n", process.getId());

            BpmnShape pool = modelInstance.newInstance(BpmnShape.class);
            Bounds poolBounds = modelInstance.newInstance(Bounds.class);
            poolBounds.setX(0);
            poolBounds.setY(0);
            poolBounds.setWidth(250);
            poolBounds.setHeight(150);
            pool.setBounds(poolBounds);

            pool.setBpmnElement(this.processToParticipant.get(process));
            plane.addChildElement(pool);

            processToShape.put(process, pool);
        }
    }

    protected void translateElement(BpmnShape shape, double tx, double ty, BpmnModelInstance modelInstance) {
        shape.getBounds().setX(shape.getBounds().getX() + tx);
        shape.getBounds().setY(shape.getBounds().getY() + ty);

        for (BpmnEdge edge : relatedEdges.getOrDefault(shape, new ArrayList<>())) {
            this.recalculatePath(edge, modelInstance);
        }
    }

    protected BpmnShape addFlowNodeToPlane(FlowNode node, BpmnPlane plane, BpmnModelInstance modelInstance) {
        BpmnShape shape = modelInstance.newInstance(BpmnShape.class);
        Bounds bounds = modelInstance.newInstance(Bounds.class);

        double width = 0;
        double height = 0;
        boolean foundSize = false;
        for (Class<? extends BaseElement> clazz : shapeSizes.keySet()) {
            if (clazz.isInstance(node)) {
                width = shapeSizes.get(clazz).getX();
                height = shapeSizes.get(clazz).getY();
                foundSize = true;
                break;
            }
        }
        if (!foundSize) {
            throw new GenerationException(String.format("Unknown type of flow node (%s): %s\n", node.getId(), node.getClass().getName()));
        }

        bounds.setX(0);
        bounds.setY(0);
        bounds.setWidth(width);
        bounds.setHeight(height);
        shape.setBounds(bounds);
        shape.setBpmnElement(node);
        plane.addChildElement(shape);
        return shape;
    }

    protected void addSequenceFlowToPlane(SequenceFlow flow, BpmnPlane plane, BpmnModelInstance modelInstance) {
        BpmnShape source = this.flowNodeToShape.get(flow.getSource());
        BpmnShape target = this.flowNodeToShape.get(flow.getTarget());
        if (source.equals(target)) {
            throw new IllegalStateException("Self loop flow!");
        }
        this.addEdgeForElement(flow, source, target, plane, modelInstance);
    }

    protected void addMessageFlowToPlane(MessageFlow flow, BpmnPlane plane, BpmnModelInstance modelInstance) {
        System.out.format("Adding message flow for %s and %s\n", flow.getSource().getId(), flow.getTarget().getId());
        BpmnShape source = this.flowNodeToShape.get((FlowNode) flow.getSource());
        BpmnShape target = this.flowNodeToShape.get((FlowNode) flow.getTarget());
        this.addEdgeForElement(flow, source, target, plane, modelInstance);
    }

    protected Collection<BpmnShape> addDataAssociations(Task task, BpmnPlane plane, BpmnModelInstance modelInstance) {
        Collection<BpmnShape> shapes = new ArrayList<>();
        Collection<DataAssociation> dataAssociations = new ArrayList<>();
        dataAssociations.addAll(task.getDataOutputAssociations());
        dataAssociations.addAll(task.getDataInputAssociations());
        System.out.printf("%d references for task with id %s\n", dataAssociations.size(), task.getName());
        for (DataAssociation dataAssociation : dataAssociations) {
            DataObjectReference dataObjectReference;
            if (dataAssociation instanceof DataInputAssociation) {
                // TODO: can the source be anything other than the data object?
                dataObjectReference = (DataObjectReference) dataAssociation.getSources().stream().findFirst().get();
            } else if (dataAssociation instanceof DataOutputAssociation) {
                dataObjectReference = (DataObjectReference) dataAssociation.getTarget();
            } else {
                throw new GenerationException("Unhandled data association type: " + dataAssociation.getClass().getName());
            }

            if (!this.dataObjects.containsKey(dataObjectReference)) {
                BpmnShape dataObject = this.addDataObjectToDiagram(task, dataObjectReference, plane, modelInstance);
                this.dataObjects.put(dataObjectReference, dataObject);
                shapes.add(dataObject);
            }

            dataAssociation.getSources().forEach(System.out::println);
            System.out.println(dataAssociation.getTarget());

            Collection<BpmnShape> sources = dataAssociation.getSources()
                    .stream()
                    .map(e -> (BpmnShape) e.getDiagramElement())
                    .toList();

            // If no sources are given, the source is the current task
            if (dataAssociation.getSources().size() == 0) {
                sources = new ArrayList<>();
                sources.add(task.getDiagramElement());
            }

            ItemAwareElement refTarget = dataAssociation.getTarget();
            BpmnShape target;
            if (refTarget == null) {
                target = task.getDiagramElement();
            } else if (refTarget instanceof FlowNode node) {
                target = (BpmnShape) node.getDiagramElement();
            } else if (refTarget instanceof Property) {
                target = task.getDiagramElement();
            } else if (refTarget instanceof DataObjectReference) {
                target = (BpmnShape) dataObjectReference.getDiagramElement();
            } else {
                throw new GenerationException("Unknown target reference of type " + refTarget.getClass().getName());
            }

            sources.forEach(s -> this.addEdgeForElement(dataAssociation, s, target, plane, modelInstance));
        }
        return shapes;
    }

    protected void addEdgeForElement(
            BaseElement element,
            BpmnShape source,
            BpmnShape target,
            BpmnPlane plane,
            BpmnModelInstance modelInstance
    ) {
        System.out.printf("Edge between %s, and %s (%s)\n", source.getBpmnElement().getId(), target.getBpmnElement().getId(), element.getId());
        BpmnEdge edge = modelInstance.newInstance(BpmnEdge.class);
        edge.setBpmnElement(element);
        edge.setSourceElement(source);
        edge.setTargetElement(target);
        this.recalculatePath(edge, modelInstance);

        if (!relatedEdges.containsKey(source)) {
            relatedEdges.put(source, new HashSet<>());
        }
        relatedEdges.get(source).add(edge);

        if (!relatedEdges.containsKey(target)) {
            relatedEdges.put(target, new HashSet<>());
        }
        relatedEdges.get(target).add(edge);

        plane.addChildElement(edge);
    }

    protected void recalculatePath(BpmnEdge edge, BpmnModelInstance modelInstance) {
        for (Waypoint child : edge.getChildElementsByType(Waypoint.class)) {
            edge.removeChildElement(child);
        }

        BpmnShape source = (BpmnShape) edge.getSourceElement();
        BpmnShape target = (BpmnShape) edge.getTargetElement();
        for (Waypoint waypoint : BpmnEdgePathing.getBestPath(modelInstance, source, target)) {
            edge.addChildElement(waypoint);
        }
    }

    protected void findParticipants(Definitions definitions) {
        Collaboration collaboration = (Collaboration) definitions.getUniqueChildElementByType(Collaboration.class);
        for (Participant participant : collaboration.getParticipants()) {
            Process process = participant.getProcess();
            this.processToParticipant.put(process, participant);
        }
    }

    public static void main(String[] args) {
        try (InputStream stream = BpmnDiagramGenerator.class.getResourceAsStream("bpmn-model.bpmn")) {
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(stream);
            System.out.format("Num participants: %d\n", modelInstance.getModelElementsByType(Participant.class).size());
            System.out.format("Num message flows: %d\n", modelInstance.getModelElementsByType(MessageFlow.class).size());
            for (MessageFlow messageFlow : modelInstance.getModelElementsByType(MessageFlow.class)) {
                System.out.format("FLOW BEFORE: %s\n", messageFlow.getId());
            }
            BpmnDiagramGenerator gen = new BpmnDiagramGenerator();
            Diagram diag = gen.getDiagramForModelInstance(modelInstance);
            Definitions definitions = modelInstance.getDefinitions();
            definitions.setTargetNamespace("https://camunda.org/examples");
            definitions.addChildElement(diag);
            System.out.println(Arrays.toString(modelInstance.getDefinitions().getChildElementsByType(BpmnLabelStyle.class).toArray()));
            File file = new File("withDiag.bpmn");
            Bpmn.writeModelToFile(file, modelInstance);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
