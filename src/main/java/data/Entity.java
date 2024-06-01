package data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record Entity(List<Integer> mentionIndices) {
    public Collection<Mention> getMentions(Document document) {
        return mentionIndices.stream().map(document.mentions()::get).toList();
    }

    public Mention getRepresentative(Document document) {
        return document.mentions().get(this.mentionIndices.get(0));
    }

    public Collection<Relation> getRelations(Document document) {
        Collection<Relation> relations = new ArrayList<>();
        for (Relation relation : document.relations()) {
            if(this.mentionIndices.contains(relation.headMentionIndex())) {
                relations.add(relation);
                continue;
            }
            if(this.mentionIndices.contains(relation.tailMentionIndex())) {
                relations.add(relation);
            }
        }
        return relations;
    }

    public Entity withMention(Mention mention, Document document) {
        int newIndex = document.mentions().indexOf(mention);
        if(newIndex == -1) {
            throw new IllegalArgumentException();
        }
        List<Integer> newIndices = new ArrayList<>(mentionIndices);
        newIndices.add(newIndex);
        System.out.format("Entity now has %d mentions.\n", newIndices.size());
        return new Entity(newIndices);
    }
}
