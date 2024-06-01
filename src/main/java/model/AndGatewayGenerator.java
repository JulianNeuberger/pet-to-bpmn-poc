package model;

import org.camunda.bpm.model.bpmn.instance.ExclusiveGateway;
import org.camunda.bpm.model.bpmn.instance.InclusiveGateway;
import org.camunda.bpm.model.xml.ModelInstance;

public class AndGatewayGenerator {

    public InclusiveGateway generate(ModelInstance modelInstance) {
        InclusiveGateway gateway = modelInstance.newInstance(InclusiveGateway.class);
        return gateway;
    }
}
