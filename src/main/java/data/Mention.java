package data;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public record Mention(String type, List<Integer> tokenDocumentIndices) {
    public Entity getEntity(Document document) {
        for (Entity entity : document.entities()) {
            if (entity.getMentions(document).contains(this)) {
                return entity;
            }
        }
        throw new IllegalArgumentException(
                String.format("Mention \"%s\" is not part of any entity.",
                        this.getText(document)
                )
        );
    }

    public String getText(Document document) {
        return this.tokenDocumentIndices.stream()
                .map(document.tokens()::get)
                .map(Token::text)
                .collect(Collectors.joining(" "));
    }

    public Collection<Relation> getRelations(Document document) {
        return document.relations()
                .stream()
                .filter(r -> r.tail(document).equals(this) || r.head(document).equals(this))
                .toList();
    }

    public Collection<Relation> getRelations(Document document, String ofType) {
        return this.getRelations(document).stream().filter(r -> r.type().equalsIgnoreCase(ofType)).toList();
    }
}
