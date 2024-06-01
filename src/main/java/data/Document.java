package data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public record Document(String text,
                       String name,
                       String id,
                       String category,
                       List<Mention> mentions,
                       List<Entity> entities,
                       List<Relation> relations,
                       List<Token> tokens) {
    public static Document fromJson(String json) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, Document.class);
    }

    public static Document fromJson(InputStream stream) throws IOException {
        String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, Document.class);
    }

    public Document withMention(Mention mention) {
        List<Mention> mentions = new ArrayList<>(this.mentions);
        mentions.add(mention);
        return new Document(
                this.text,
                this.name,
                this.id,
                this.category,
                mentions,
                this.entities(),
                this.relations(),
                this.tokens()
        );
    }

    public Document withEntity(Entity entity) {
        List<Entity> entities = new ArrayList<>(this.entities);
        entities.add(entity);
        return new Document(
                this.text,
                this.name,
                this.id,
                this.category,
                this.mentions(),
                entities,
                this.relations(),
                this.tokens()
        );
    }

    public Document withRelation(Relation relation) {
        List<Relation> relations = new ArrayList<>(this.relations);
        relations.add(relation);
        return new Document(
                this.text,
                this.name,
                this.id,
                this.category,
                this.mentions(),
                this.entities(),
                relations,
                this.tokens()
        );
    }

    public Document withoutMention(Mention mention) {
        List<Mention> mentions = new ArrayList<>(this.mentions);
        Integer mentionIndex = this.mentions.indexOf(mention);
        boolean removed = mentions.remove(mention);
        if (!removed) {
            throw new IllegalArgumentException("Mention not present");
        }

        // move all relation head and tail indices if they are affected by removing the mention
        System.out.println("Fixing relations ...");
        List<Relation> fixedRelations = new ArrayList<>();
        for (Relation relation : relations) {
            if(relation.headMentionIndex().equals(mentionIndex) || relation.tailMentionIndex().equals(mentionIndex)) {
                // do not add this relation, as it refers to a deleted mention
                continue;
            }

            if (relation.headMentionIndex() > mentionIndex) {
                relation = relation.withNewHeadIndex(relation.headMentionIndex() - 1);
            }
            if (relation.tailMentionIndex() > mentionIndex) {
                relation = relation.withNewTailIndex(relation.tailMentionIndex() - 1);
            }
            fixedRelations.add(relation);
        }

        System.out.println("Fixing entities ...");
        // remove mention index from entities, and remove entity if it is empty
        List<Entity> fixedEntities = new ArrayList<>();
        for (Entity entity : entities) {
            List<Integer> fixedMentionIndices = new ArrayList<>();
            for (Integer index : entity.mentionIndices()) {
                if(index.equals(mentionIndex)) {
                    // do not include
                    continue;
                }
                if(index > mentionIndex) {
                    index -= 1;
                }
                fixedMentionIndices.add(index);
            }
            if(!fixedMentionIndices.isEmpty()) {
                fixedEntities.add(new Entity(fixedMentionIndices));
            }
        }
        System.out.format("Length of entities was %d, now there are %d.\n", this.entities.size(), fixedEntities.size());

        return new Document(
                this.text,
                this.name,
                this.id,
                this.category,
                mentions,
                fixedEntities,
                fixedRelations,
                this.tokens()
        );
    }

    public Document withoutRelation(Relation relation) {
        List<Relation> relations = new ArrayList<>(this.relations);

        return new Document(
                this.text,
                this.name,
                this.id,
                this.category,
                this.mentions(),
                this.entities(),
                relations,
                this.tokens()
        );
    }

    public static void main(String[] args) throws IOException {
        try (InputStream stream = Document.class.getResourceAsStream("test.json")) {
            if (stream == null) return;
            Document doc = Document.fromJson(stream);
            System.out.println(doc);
            System.out.println(doc.mentions.size());
        }
    }

    public TraversalNode breadFirstSearch(Mention start, String relationType, boolean reverse, Predicate<TraversalNode> predicate) {
        List<Mention> visited = new ArrayList<>();
        List<TraversalNode> current = new ArrayList<>();
        visited.add(start);
        current.add(new TraversalNode(start, 0));

        while (!current.isEmpty()) {
            TraversalNode next = bfsStep(current, visited, relationType, reverse);
            if (predicate.test(next)) {
                return next;
            }
        }
        return null;
    }

    public int getDepth(Mention start, String relationType, boolean reverse) {
        List<Mention> visited = new ArrayList<>();
        List<TraversalNode> current = new ArrayList<>();
        visited.add(start);
        current.add(new TraversalNode(start, 0));

        int depth = 0;
        while (!current.isEmpty()) {
            TraversalNode next = bfsStep(current, visited, relationType, reverse);
            depth = Math.max(depth, next.depth);
        }
        return depth;
    }

    private TraversalNode bfsStep(List<TraversalNode> current, List<Mention> visited, String relationType, boolean reverse) {
        TraversalNode next = current.remove(0);
        Collection<Relation> edges = next.mention.getRelations(this, relationType);
        if (reverse) {
            edges = edges.stream().filter(r -> r.tail(this).equals(next.mention)).toList();
        } else {
            edges = edges.stream().filter(r -> r.head(this).equals(next.mention)).toList();
        }

        for (Relation edge : edges) {
            Mention neighbour;
            if (reverse) {
                neighbour = edge.head(this);
            } else {
                neighbour = edge.tail(this);
            }
            if (visited.contains(neighbour)) {
                continue;
            }
            visited.add(neighbour);
            current.add(new TraversalNode(neighbour, next.depth + 1));
        }

        return next;
    }

    public record TraversalNode(Mention mention, int depth) {
    }

    public Collection<Relation> findUnaryRelations() {
        return relations.stream().filter(r -> Objects.equals(r.headMentionIndex, r.tailMentionIndex)).toList();
    }
}
