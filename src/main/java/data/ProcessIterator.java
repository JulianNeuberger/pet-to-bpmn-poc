package data;

import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

import java.util.*;
import java.util.stream.Collectors;

public class ProcessIterator implements Iterator<ProcessIterator.Step> {
    public static class Step {
        public FlowNode flowNode;
        public int level;

        public Step(FlowNode flowNode, int level) {
            this.flowNode = flowNode;
            this.level = level;
        }
    }

    private final List<Step> current = new ArrayList<>();
    private final Set<String> visited = new HashSet<>();

    public ProcessIterator(Process process) {
        for (FlowNode flowNode : this.getProcessStart(process)) {
            this.current.add(new Step(flowNode, 0));
            this.visited.add(flowNode.getId());
        }
    }

    private Collection<FlowNode> getProcessStart(Process process) {
        Collection<FlowNode> nodes = process.getChildElementsByType(FlowNode.class);
        List<FlowNode> starts = nodes.stream().filter(n -> n.getIncoming().size() == 0).toList();
        for (FlowNode node : nodes) {
            System.out.println(node.getIncoming().size());
            System.out.println(node.getPreviousNodes().list().stream().map(FlowElement::getName).collect(Collectors.joining(", ")));
        }
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("Process has no start elements. We do not account for this currently!");
        }
        return starts;
    }

    @Override
    public boolean hasNext() {
        return !this.current.isEmpty();
    }

    @Override
    public Step next() {
        Step next = this.current.remove(0);

        for (SequenceFlow flow : next.flowNode.getOutgoing()) {
            if (this.visited.contains(flow.getTarget().getId())) {
                continue;
            }

            this.current.add(new Step(flow.getTarget(), next.level + 1));
            this.visited.add(flow.getTarget().getId());
        }

        return next;
    }
}
