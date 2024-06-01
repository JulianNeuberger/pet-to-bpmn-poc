package image;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface ImageGenerator {
    Optional<InputStream> imageFromBpmn(BpmnModelInstance modelInstance) throws IOException;
}
