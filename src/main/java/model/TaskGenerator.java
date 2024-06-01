package model;

import data.Document;
import data.Entity;
import data.Mention;
import data.Relation;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.ModelInstance;

import java.util.Collection;

public class TaskGenerator {
    public Task generate(ModelInstance modelInstance, Mention activity, Document document) {
        Task userTask = modelInstance.newInstance(Task.class);
        userTask.setName(this.generateTaskLabel(activity, document));
        return userTask;
    }

    private String generateTaskLabel(Mention activity, Document document) {
        Entity entity = activity.getEntity(document);
        Mention representative = entity.getRepresentative(document);
        Collection<Relation> usesRelations = entity.getRelations(document).stream()
                .filter(r -> r.type().toLowerCase().equals("uses"))
                .toList();

        String predicate = representative.getText(document);
        if(usesRelations.size() == 0) {
            return predicate;
        }

        Relation usesRelation = usesRelations.stream().findFirst().get();
        Entity object = document.mentions().get(usesRelation.headMentionIndex()).getEntity(document);
        if (object.equals(entity)) {
            object = document.mentions().get(usesRelation.tailMentionIndex()).getEntity(document);
        }

        return predicate + " " + object.getRepresentative(document).getText(document);
    }
}
