package model;

import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.xml.ModelInstance;

public class DataAssociationGenerator {
    public DataAssociation generate(Task task, DataObjectReference dataObjectReference, ModelInstance modelInstance) {
        // FIXME: Pet does not specify if uses is an input or output association...
        DataAssociation dataAssociation = modelInstance.newInstance(DataInputAssociation.class);
        dataAssociation.getSources().add(dataObjectReference);
        Property target = modelInstance.newInstance(Property.class);
        task.getProperties().add(target);
        dataAssociation.setTarget(target);
        return dataAssociation;
    }
}
