package diagram;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.xml.ModelInstance;

public interface DiagramGenerator {
    void addDiagramToModel(BpmnModelInstance modelInstance);
}
