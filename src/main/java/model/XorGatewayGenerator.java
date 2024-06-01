package model;

import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.xml.ModelInstance;

public class XorGatewayGenerator {

    public ExclusiveGateway generate(ModelInstance modelInstance) {
        ExclusiveGateway gateway = modelInstance.newInstance(ExclusiveGateway.class);
        return gateway;
    }
}
