package model;

import data.Document;
import data.Mention;
import org.camunda.bpm.model.bpmn.instance.DataObject;
import org.camunda.bpm.model.bpmn.instance.DataObjectReference;
import org.camunda.bpm.model.xml.ModelInstance;

public class DataObjectReferenceGenerator {
    public DataObjectReference generate(Mention mention, DataObject dataObject, ModelInstance modelInstance, Document document) {
        DataObjectReference dataObjectReference = modelInstance.newInstance(DataObjectReference.class);
        dataObjectReference.setDataObject(dataObject);
        dataObjectReference.setName(mention.getEntity(document).getRepresentative(document).getText(document));
        return dataObjectReference;
    }
}
