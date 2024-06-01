package model;

import data.*;
import exception.GenerationException;
import org.camunda.bpm.model.bpmn.AssociationDirection;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.xml.ModelInstance;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class BpmnModelGenerator implements ModelGenerator {
    @Override
    public BpmnModelInstance getModelInstance(Document document) {
        System.out.format("Generating model for document \"%s\".\n", document.name());

        int numUnaryRelations = document.findUnaryRelations().size();
        if(numUnaryRelations > 0) {
            throw new IllegalStateException("There are unary relations!");
        }

        System.out.format("Resolving exclusive gateway flows ...\n");
        document = nameExclusiveGatewayFlows(document);
        if(numUnaryRelations != document.findUnaryRelations().size()) {
            throw new IllegalStateException("Naming flows connected to exclusive gateways introduced self loop!");
        }

        System.out.format("Resolving same gateway relations ...\n");
        document = resolveSameGateway(document);
        if(numUnaryRelations != document.findUnaryRelations().size()) {
            throw new IllegalStateException("Resolving same gateways introduced self loop!");
        }

        System.out.format("Resolving condition specifications ...\n");
        document = resolveConditionSpecifications(document);
        if(numUnaryRelations != document.findUnaryRelations().size()) {
            throw new IllegalStateException("Resolving condition specifications introduced self loop!");
        }

        System.out.format("Generating model ...\n");
        return this.generateModel(document);
    }

    private BpmnModelInstance generateModel(Document document) {

        BpmnModelInstance modelInstance = Bpmn.createEmptyModel();
        Definitions definitions = modelInstance.newInstance(Definitions.class);
        definitions.setTargetNamespace("https://camunda.org/examples");
        modelInstance.setDefinitions(definitions);

        Collaboration collaboration = modelInstance.newInstance(Collaboration.class);
        definitions.addChildElement(collaboration);

        Map<Entity, Gateway> gateways = new HashMap<>();
        Map<Entity, Task> tasks = new HashMap<>();

        Map<Entity, Process> processByActor = new HashMap<>();
        Map<Entity, List<Mention>> flowNodesByActor = this.partitionFlowNodes(document);
        for (Entity actor : flowNodesByActor.keySet()) {
            System.out.format(
                    "Actor %s has %d flow nodes.\n",
                    actor == null ? "unknown" : actor.getRepresentative(document).getText(document),
                    flowNodesByActor.get(actor).size()
            );
            Participant participant = new ParticipantGenerator().generate(actor, document, modelInstance);
            collaboration.addChildElement(participant);

            List<Mention> flowNodes = flowNodesByActor.get(actor);
            Process process = getProcess(flowNodes, document, modelInstance, definitions, gateways, tasks);
            participant.setProcess(process);

            processByActor.put(actor, process);
        }

        Map<Entity, DataObjectReference> dataObjectReferences = new HashMap<>();
        for (Mention mention : document.mentions()) {
            if (!mention.type().equalsIgnoreCase("activity data")) {
                continue;
            }

            System.out.format("Generating data object reference for %s\n", mention.getText(document));
            if (dataObjectReferences.containsKey(mention.getEntity(document))) {
                System.out.println("Already generated for different mention, skipping");
                continue;
            }

            Optional<Relation> usedBy = mention.getEntity(document).getRelations(document).stream().findFirst();
            if (usedBy.isEmpty()) {
                System.out.println("Unused data object, not generating in process!");
                continue;
            }

            Mention activity = usedBy.get().head(document);
            Entity actor = null;
            for (Entity k : flowNodesByActor.keySet()) {
                List<Mention> values = flowNodesByActor.get(k);
                if (values.contains(activity)) {
                    actor = k;
                    break;
                }
            }
            if (actor == null) {
                System.out.format(
                        "Could not find actor for task \"%s\", which uses data object \"%s\", skipping.\n",
                        activity.getText(document),
                        mention.getText(document)
                );
                continue;
            }

            Process process = processByActor.get(actor);

            DataObject dataObject = new DataObjectGenerator().generate(modelInstance);
            process.addChildElement(dataObject);
            DataObjectReference dataObjectReference = new DataObjectReferenceGenerator()
                    .generate(mention, dataObject, modelInstance, document);
            process.addChildElement(dataObjectReference);
            dataObjectReferences.put(mention.getEntity(document), dataObjectReference);
        }

        for(Mention mention : document.mentions()) {
            if(!mention.type().equalsIgnoreCase("further specification")) {
                continue;
            }

            TextAnnotation textAnnotation = modelInstance.newInstance(TextAnnotation.class);
            collaboration.addChildElement(textAnnotation);


            Text text = modelInstance.newInstance(Text.class);
            text.setTextContent(mention.getText(document));

            textAnnotation.setText(text);
            textAnnotation.addChildElement(text);

            List<Relation> furtherSpecificationRelations = mention.getRelations(document)
                    .stream()
                    .filter(r -> r.type().equalsIgnoreCase("further specification"))
                    .toList();

            for (Relation furtherSpecificationRelation : furtherSpecificationRelations) {
                Task task = tasks.get(furtherSpecificationRelation.head(document).getEntity(document));
                Association association = modelInstance.newInstance(Association.class);
                association.setSource(task);
                association.setTarget(textAnnotation);
                association.setAssociationDirection(AssociationDirection.None);
                collaboration.addChildElement(association);
            }
        }

        for (Relation relation : document.relations()) {
            if(!relation.type().equalsIgnoreCase("uses")) {
                continue;
            }

            Mention activity = relation.head(document);
            Task task = tasks.get(activity.getEntity(document));
            if (task == null) {
                System.out.format(
                        "ERROR: Expected a task for head of uses relation, but there was none created for %s (\"%s\")\n",
                        activity.type(),
                        activity.getText(document)
                );
                break;
            }
            Mention dataObject = relation.tail(document);
            DataObjectReference dataObjectReference = dataObjectReferences.get(dataObject.getEntity(document));
            DataAssociation dataAssociation = new DataAssociationGenerator().generate(
                    task,
                    dataObjectReference,
                    modelInstance
            );
            task.addChildElement(dataAssociation);
        }

        System.out.format(
                "Generating message flows between %d participants: %s.\n",
                flowNodesByActor.size(),
                flowNodesByActor.keySet()
                        .stream()
                        .map(e -> e != null ? e.getRepresentative(document).getText(document) : "unknown")
                        .collect(Collectors.joining(", "))
        );
        for (Entity actor : flowNodesByActor.keySet()) {
            List<Mention> flowNodes = flowNodesByActor.get(actor);

            System.out.format("Message flows for actor \"%s\" with %d tasks.\n", actor != null ? actor.getRepresentative(document).getText(document) : "unknown", tasks.size());

            List<Relation> messageFlowRelations = document.relations().stream()
                    .filter(r -> r.type().equalsIgnoreCase("flow"))
                    // relations that are connected to either head or tail
                    .filter(r -> flowNodes.contains(r.tail(document)))
                    // only relations that are connected to exactly one task in this process
                    .filter(r -> !flowNodes.contains(r.head(document)))
                    .toList();

            for (Relation messageFlowRelation : messageFlowRelations) {
                Mention head = messageFlowRelation.head(document);
                Mention tail = messageFlowRelation.tail(document);

                System.out.format("%s [%s from %d] --(message_flow)-> %s [%s from %d]\n",
                        head.getText(document),
                        head.type(),
                        head.tokenDocumentIndices().get(0),
                        tail.getText(document),
                        tail.type(),
                        tail.tokenDocumentIndices().get(0)
                );

                Task headTask;
                if (!head.type().equalsIgnoreCase("activity")) {
                    System.out.format(
                            "Adding auxiliary task for head \"%s\" (%s from %d) of message flow to \"%s\" (%s, from %d)\n",
                            head.getText(document),
                            head.type(),
                            head.tokenDocumentIndices().get(0),
                            tail.getText(document),
                            tail.type(),
                            tail.tokenDocumentIndices().get(0)
                    );
                    System.out.println(head.type());
                    // create an auxiliary task
                    headTask = modelInstance.newInstance(Task.class);
                    headTask.setName("Auxiliary Outgoing");
                    if (!gateways.containsKey(head.getEntity(document))) {
                        throw new GenerationException(
                                String.format(
                                        "Entity \"%s\" is not something we generated a gateway for!",
                                        head.getEntity(document).getRepresentative(document).getText(document)
                                )
                        );
                    }
                    Process process = (Process) gateways.get(head.getEntity(document)).getParentElement();
                    process.addChildElement(headTask);
                    String name = null;
                    if (messageFlowRelation instanceof NamedRelation namedRelation) {
                        name = namedRelation.name();
                    }
                    SequenceFlow auxiliaryFlow = new SequenceFlowGenerator().generate(
                            gateways.get(head.getEntity(document)),
                            headTask,
                            name,
                            modelInstance
                    );
                    process.addChildElement(auxiliaryFlow);
                    headTask.getIncoming().add(auxiliaryFlow);
                    gateways.get(head.getEntity(document)).getOutgoing().add(auxiliaryFlow);
                } else {
                    headTask = tasks.get(head.getEntity(document));
                }

                Task tailTask;
                if (!tail.type().equalsIgnoreCase("activity")) {
                    System.out.format(
                            "Adding auxiliary task for tail \"%s\" (%s, from %d) of message flow from \"%s\" (%s, from %d)\n",
                            tail.getText(document),
                            tail.type(),
                            tail.tokenDocumentIndices().get(0),
                            head.getText(document),
                            head.type(),
                            head.tokenDocumentIndices().get(0)
                    );
                    // create an auxiliary task
                    tailTask = modelInstance.newInstance(Task.class);
                    tailTask.setName("Auxiliary Incoming");
                    Process process = (Process) gateways.get(tail.getEntity(document)).getParentElement();
                    process.addChildElement(tailTask);
                    String name = null;
                    if (messageFlowRelation instanceof NamedRelation namedRelation) {
                        name = namedRelation.name();
                    }
                    SequenceFlow auxiliaryFlow = new SequenceFlowGenerator().generate(
                            tailTask,
                            gateways.get(tail.getEntity(document)),
                            name,
                            modelInstance
                    );
                    process.addChildElement(auxiliaryFlow);
                    tailTask.getOutgoing().add(auxiliaryFlow);
                    gateways.get(tail.getEntity(document)).getIncoming().add(auxiliaryFlow);
                } else {
                    tailTask = tasks.get(tail.getEntity(document));
                }

                MessageFlow messageFlow = new MessageFlowGenerator().generate(headTask, tailTask, modelInstance);
                collaboration.addChildElement(messageFlow);
            }
            System.out.println("--------------");
        }

        return modelInstance;
    }

    private List<Relation> getRelationsForArgument(Document document, String type, Mention argument) {
        return document.relations().stream()
                .filter(r -> r.type().equalsIgnoreCase(type))
                .filter(r -> r.tail(document).equals(argument)
                        || r.head(document).equals(argument))
                .toList();
    }

    private Document resolveConditionSpecifications(Document document) {
        List<Mention> conditionSpecifications = document.mentions().stream()
                .filter(m -> m.type().equalsIgnoreCase("condition specification"))
                .toList();
        System.out.format("Document has %d condition specifications.\n", conditionSpecifications.size());

        for (Mention conditionSpecification : conditionSpecifications) {
            System.out.println("Loop iteration start.");
            List<Relation> flows = getRelationsForArgument(document, "flow", conditionSpecification);
            if (flows.size() > 2) {
                throw new GenerationException(
                        String.format(
                                "Got more than 2 flows (%d) for condition specification.",
                                flows.size()
                        )
                );
            }

            if (flows.size() == 0) {
                throw new GenerationException("Got no flows for condition specification.");
            }

            System.out.println("Getting super head and super tail ...");
            Integer conditionSpecIndex = document.mentions().indexOf(conditionSpecification);

            // resolve superHead -flow-> conditionSpec -flow-> superTail
            Mention superHead = flows.get(0).head(document);
            Mention superTail;

            if (flows.size() == 1) {
                if (superHead.equals(conditionSpecification)) {
                    throw new GenerationException(
                            "Only got one flow, expected it to have condition " +
                                    "specification as target, but it is the source!"
                    );
                }

                System.out.format(
                        "Adding auxiliary task and flow for missing elements after %s \"%s\".",
                        flows.get(0).head(document).type(),
                        flows.get(0).head(document).getText(document)
                );
                // add auxiliary task
                // FIXME: this is a hack, we need a separate model that is not connected to the actual text...
                Mention auxiliary = new Mention("activity", new ArrayList<>());
                document = document.withMention(auxiliary);

                int auxiliaryIndex = document.mentions().indexOf(auxiliary);
                Entity auxiliaryEntity = new Entity(List.of(auxiliaryIndex));
                document = document.withEntity(auxiliaryEntity);

                document = document.withRelation(new NamedRelation(conditionSpecIndex, auxiliaryIndex, "flow", "autogenerated flow"));

                superTail = auxiliary;
            } else {
                superTail = flows.get(1).tail(document);
            }


            Relation gatewayConditionRelation = flows.get(0);
            if (superHead.equals(conditionSpecification)) {
                superHead = flows.get(1).head(document);
                superTail = flows.get(0).tail(document);
                gatewayConditionRelation = flows.get(1);
            }

            if (!(gatewayConditionRelation instanceof NamedRelation)) {
                throw new GenerationException(
                        "Expected the flow from gateway to condition specification " +
                                "to be a named relation, containing the gateway name."
                );
            }

            String name = getGatewayPathCondition(
                    conditionSpecification,
                    (NamedRelation) gatewayConditionRelation,
                    document
            );

            System.out.format(
                    "Resolving condition spec. to named relation \"%s\" between \"%s\" and \"%s\"\n",
                    name,
                    superHead.getText(document),
                    superTail.getText(document)
            );
            document = document.withoutMention(conditionSpecification);
            document = document.withRelation(new NamedRelation(
                    document.mentions().indexOf(superHead),
                    document.mentions().indexOf(superTail),
                    flows.get(0).type(),
                    name
            ));
        }

        System.out.println("Done with resolving condition specifications!");
        return document;
    }

    private String getGatewayPathCondition(Mention condition, NamedRelation flowToCondition, Document document) {
        return String.format("%s %s", flowToCondition.name(), condition.getText(document));
    }

    private Document resolveSameGateway(Document document) {
        List<Relation> sameGatewayRelations = document.relations().stream()
                .filter(r -> r.type().equalsIgnoreCase("same gateway"))
                .toList();
        System.out.format("Document has %d same gateway relations.\n", sameGatewayRelations.size());
        List<Mention> mentions = document.mentions();
        List<Entity> entities = document.entities();
        List<Relation> relations = document.relations();

        for (Relation sameGatewayRelation : sameGatewayRelations) {
            Mention head = document.mentions().get(sameGatewayRelation.headMentionIndex());
            Entity headEntity = head.getEntity(document);
            int headIndex = entities.indexOf(headEntity);

            if (headIndex == -1) {
                throw new GenerationException("Could not find head of same gateway.");
            }

            Mention tail = document.mentions().get(sameGatewayRelation.tailMentionIndex());
            Entity tailEntity = tail.getEntity(document);
            int tailIndex = entities.indexOf(tailEntity);

            if (tailIndex == -1) {
                throw new GenerationException("Could not find tail of same gateway.");
            }

            System.out.format(
                    "Resolving same gateway relation between \"%s\" and \"%s\".\n",
                    head.getText(document),
                    tail.getText(document)
            );

            headEntity = headEntity.withMention(tail, document);

            System.out.format(
                    "Resolving %s \"%s\" to cluster of %s \"%s\"\n",
                    tail.type(), tail.getText(document),
                    head.type(), head.getText(document)
            );

            entities.set(headIndex, headEntity);
            entities.remove(tailIndex);
        }

        return new Document(
                document.text(),
                document.name(),
                document.id(),
                document.category(),
                mentions,
                entities,
                relations,
                document.tokens()
        );
    }

    private Document nameExclusiveGatewayFlows(Document document) {
        List<Mention> exclusiveGateways = document.mentions()
                .stream()
                .filter(m -> m.type().equalsIgnoreCase("xor gateway"))
                .toList();

        List<Relation> relations = new ArrayList<>();
        for (Relation relation : document.relations()) {
            if (!relation.type().equalsIgnoreCase("flow")) {
                relations.add(relation);
                continue;
            }
            Mention relationHead = relation.head(document);
            if (!exclusiveGateways.contains(relationHead)) {
                relations.add(relation);
                continue;
            }
            System.out.format(
                    "Naming flow from %s \"%s\" to %s \"%s\"\n",
                    relation.head(document).type(),
                    relation.head(document).getText(document),
                    relation.tail(document).type(),
                    relation.tail(document).getText(document)
            );
            relations.add(new NamedRelation(
                    relation.headMentionIndex(),
                    relation.tailMentionIndex(),
                    relation.type(),
                    relationHead.getText(document)
            ));
        }

        return new Document(
                document.text(),
                document.name(),
                document.id(),
                document.category(),
                document.mentions(),
                document.entities(),
                relations,
                document.tokens()
        );
    }

    private Process getProcess(Collection<Mention> nodes,
                               Document document,
                               ModelInstance modelInstance,
                               Definitions definitions,
                               Map<Entity, Gateway> gateways,
                               Map<Entity, Task> tasks) {
        Map<Entity, FlowNode> flowNodes = new HashMap<>();

        Process process = modelInstance.newInstance(Process.class);
        definitions.addChildElement(process);

        for (Mention node : nodes) {
            System.out.format("Handling mention with text \"%s\" (%s)\n", node.getText(document), node.type());
            switch (node.type().toLowerCase()) {
                case "activity" -> {
                    Task task = new TaskGenerator().generate(modelInstance, node, document);
                    flowNodes.put(node.getEntity(document), task);
                    process.addChildElement(task);
                    tasks.put(node.getEntity(document), task);
                }
                case "xor gateway" -> {
                    if (gateways.containsKey(node.getEntity(document))) {
                        flowNodes.put(node.getEntity(document), gateways.get(node.getEntity(document)));
                        break;
                    }
                    Gateway gateway = new XorGatewayGenerator().generate(modelInstance);
                    process.addChildElement(gateway);
                    flowNodes.put(node.getEntity(document), gateway);
                    gateways.put(node.getEntity(document), gateway);
                }
                case "and gateway" -> {
                    Gateway gateway = new AndGatewayGenerator().generate(modelInstance);
                    process.addChildElement(gateway);
                    flowNodes.put(node.getEntity(document), gateway);
                    gateways.put(node.getEntity(document), gateway);
                }
                default -> System.out.format("WARN: Unknown type \"%s\"!\n", node.type().toLowerCase());
            }

        }

        for (Relation relation : document.relations()) {
            if(!relation.type().equalsIgnoreCase("flow")) {
                continue;
            }

            if(relation.headMentionIndex().equals(relation.tailMentionIndex())) {
                throw new IllegalArgumentException("Unary relation");
            }
            Mention head = document.mentions().get(relation.headMentionIndex());
            if (!flowNodes.containsKey(head.getEntity(document))) {
                // source is not part of process
                // some other process will render this
                System.out.format(
                        "Head \"%s\" is not in flow nodes, skipping.\n",
                        head.getText(document)
                );
                continue;
            }

            Mention tail = document.mentions().get(relation.tailMentionIndex());
            if (!flowNodes.containsKey(tail.getEntity(document))) {
                // target is not part of process
                // will be rendered as a message flow later!
                System.out.format(
                        "Head \"%s\" is not in flow nodes, skipping.\n",
                        tail.getText(document)
                );
                continue;
            }

            String name = null;
            if (relation instanceof NamedRelation namedRelation) {
                name = namedRelation.name();
            }

            if(head.equals(tail)) {
                throw new IllegalStateException(
                        String.format("Same head and tail for %d and %d", relation.headMentionIndex(), relation.tailMentionIndex())
                );
            }

            SequenceFlow flow = new SequenceFlowGenerator().generate(
                    flowNodes.get(head.getEntity(document)),
                    flowNodes.get(tail.getEntity(document)),
                    name,
                    modelInstance
            );

            process.addChildElement(flow);
            flowNodes.get(head.getEntity(document)).getOutgoing().add(flow);
            flowNodes.get(tail.getEntity(document)).getIncoming().add(flow);
        }
        return process;
    }

    private Map<Entity, List<Mention>> partitionFlowNodes(Document document) {
        Map<Entity, List<Mention>> partitions = new HashMap<>();
        Collection<String> flowNodeTypes = List.of(new String[]{"activity", "xor gateway", "and gateway"});
        for (Mention mention : document.mentions()) {
            if (!flowNodeTypes.contains(mention.type().toLowerCase())) {
                continue;
            }

            Collection<Relation> performerRelations = mention.getRelations(document).stream()
                    .filter(r -> r.type().equalsIgnoreCase("actor performer"))
                    .toList();
            Collection<Relation> recipientRelations = mention.getRelations(document).stream()
                    .filter(r -> r.type().equalsIgnoreCase("actor recipient"))
                    .toList();
            Entity performer = null;
            if (performerRelations.size() > 0) {
                if (performerRelations.size() > 1) {
                    System.out.format(
                            "More than one performer for flow node %s: %s\n",
                            mention.getText(document),
                            performerRelations.stream()
                                    .map(r -> String.format(
                                            "%s -%s-> %s", r.head(document).getText(document),
                                            r.type(), r.tail(document).getText(document)
                                    ))
                                    .collect(Collectors.joining(", "))
                    );
                }
                Relation performerRelation = performerRelations.stream().findFirst().get();
                performer = document.mentions().get(performerRelation.tailMentionIndex()).getEntity(document);
            } else if (recipientRelations.size() > 0) {
                if (recipientRelations.size() > 1) {
                    System.out.format(
                            "More than one recipient for flow node %s: %s\n",
                            mention.getText(document),
                            recipientRelations.stream()
                                    .map(r -> String.format(
                                            "%s -%s-> %s", r.head(document).getText(document),
                                            r.type(), r.tail(document).getText(document)
                                    ))
                                    .collect(Collectors.joining(", "))
                    );
                }
                Relation performerRelation = recipientRelations.stream().findFirst().get();
                performer = document.mentions().get(performerRelation.tailMentionIndex()).getEntity(document);
            }

            if (!partitions.containsKey(performer)) {
                partitions.put(performer, new ArrayList<>());
            }
            partitions.get(performer).add(mention);
        }

        if (partitions.containsKey(null)) {
            System.out.format(
                    "Trying to resolve actors for %s.\n",
                    partitions.get(null)
                            .stream()
                            .map(m -> m.getText(document))
                            .collect(Collectors.joining(", "))
            );
            resolveUnknownActors(partitions, document);
        }

        return partitions;
    }

    private void resolveUnknownActors(Map<Entity, List<Mention>> partitions, Document document) {

        boolean foundNew = true;
        while (foundNew) {
            Collection<Mention> mentions = new ArrayList<>(partitions.get(null));
            foundNew = false;
            for (Mention mention : mentions) {
                System.out.format(
                        "No actor performer marked for \"%s\" (%s), trying to guess...\n",
                        mention.getText(document),
                        mention.type()
                );

                // part of an entity?
                Mention neighbourActivity = null;
                for (Mention other : mention.getEntity(document).getMentions(document)) {
                    if (other.equals(mention)) {
                        continue;
                    }
                    for (Entity actor : partitions.keySet()) {
                        if (actor == null) {
                            continue;
                        }
                        if (partitions.get(actor).contains(other)) {
                            neighbourActivity = other;
                            break;
                        }
                    }
                }

                // search to left
                if (neighbourActivity == null) {
                    Document.TraversalNode step = document.breadFirstSearch(
                            mention,
                            "flow",
                            true,
                            s -> !partitions.get(null).contains(s.mention())
                    );
                    if (step != null) {
                        neighbourActivity = step.mention();
                    }
                }

                // search to right
                if (neighbourActivity == null) {
                    System.out.println("No activity to the left, looking to the right.");
                    Document.TraversalNode step = document.breadFirstSearch(
                            mention,
                            "flow",
                            false,
                            s -> !partitions.get(null).contains(s.mention())
                    );
                    if (step != null) {
                        neighbourActivity = step.mention();
                    }
                }

                Entity guessedActor = null;
                if (neighbourActivity != null) {
                    for (Entity actor : partitions.keySet()) {
                        if (actor == null) continue;
                        if (partitions.get(actor).contains(neighbourActivity)) {
                            partitions.get(actor).add(mention);
                            partitions.get(null).remove(mention);
                            foundNew = true;
                            guessedActor = actor;
                            break;
                        }
                    }
                }

                if (guessedActor != null) {
                    System.out.format(
                            "Guessing %s \"%s\" (%s) is performed by previous actor \"%s\", who performed \"%s\" (%s)!\n",
                            mention.type(),
                            mention.getText(document),
                            mention.tokenDocumentIndices().stream().map(String::valueOf).collect(Collectors.joining(",")),
                            guessedActor.getRepresentative(document).getText(document),
                            neighbourActivity.getText(document),
                            neighbourActivity.tokenDocumentIndices().stream().map(String::valueOf).collect(Collectors.joining(","))
                    );
                } else {
                    System.out.format(
                            "Could not guess performer for \"%s\" (%s)\n",
                            mention.getText(document),
                            mention.type()
                    );
                }
            }
        }

        if (partitions.get(null).size() == 0) {
            partitions.remove(null);
        }
    }

    public static void main(String[] args) throws IOException {
        InputStream stream = BpmnModelGenerator.class.getResourceAsStream("../data/test.json");
        if (stream == null) {
            throw new IOException("Stream could not be opened.");
        }
        Document document = Document.fromJson(stream);
        BpmnModelInstance modelInstance = new BpmnModelGenerator().getModelInstance(document);

        //Bpmn.validateModel(modelInstance);
        File file = new File("bpmn-model.bpmn");
        if (file.createNewFile()) {
            System.out.println("Created new file.");
        } else {
            System.out.println("Overwriting file.");
        }

        Bpmn.validateModel(modelInstance);
        Bpmn.writeModelToFile(file, modelInstance);
    }
}
