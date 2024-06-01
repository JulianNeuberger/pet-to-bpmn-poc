package model;

import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.InteractionNode;
import org.camunda.bpm.model.bpmn.instance.MessageFlow;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.ModelInstance;

public class MessageFlowGenerator {

    public MessageFlow generate(InteractionNode head, InteractionNode tail, ModelInstance modelInstance) {
        MessageFlow flow = modelInstance.newInstance(MessageFlow.class);
        flow.setSource(head);
        flow.setTarget(tail);
        return flow;
    }
}
