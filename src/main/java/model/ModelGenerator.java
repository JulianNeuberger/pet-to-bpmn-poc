package model;

import data.Document;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;

public interface ModelGenerator {
    BpmnModelInstance getModelInstance(Document document);
}
