package model;

import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.xml.ModelInstance;

public class DataObjectGenerator {
    public DataObject generate(ModelInstance modelInstance) {
        return modelInstance.newInstance(DataObject.class);
    }
}
