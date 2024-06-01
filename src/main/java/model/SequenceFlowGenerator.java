package model;

import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.ModelInstance;

public class SequenceFlowGenerator {

    public SequenceFlow generate(FlowNode head, FlowNode tail, String name, ModelInstance modelInstance) {
        SequenceFlow flow = modelInstance.newInstance(SequenceFlow.class);
        if(head.equals(tail)) {
            throw new IllegalArgumentException("Head equals tail");
        }
        flow.setSource(head);
        flow.setTarget(tail);
        flow.setName(name);
        return flow;
    }
}
