package model;

import data.Document;
import data.Entity;
import org.camunda.bpm.model.bpmn.instance.Participant;
import org.camunda.bpm.model.xml.ModelInstance;

public class ParticipantGenerator {

    public Participant generate(Entity entity, Document document, ModelInstance modelInstance) {
        Participant participant = modelInstance.newInstance(Participant.class);

        String name = "Unknown";
        if(entity != null) {
            name = entity.getRepresentative(document).getText(document);
        }
        participant.setName(name);

        return participant;
    }
}
