package data;

public class NamedRelation extends Relation {
    protected final String name;

    public NamedRelation(Integer headMentionIndex, Integer tailMentionIndex, String type, String name) {
        super(headMentionIndex, tailMentionIndex, type);
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Mention head(Document document) {
        return document.mentions().get(this.headMentionIndex);
    }

    public Mention tail(Document document) {
        return document.mentions().get(this.tailMentionIndex);
    }

    @Override
    public Relation withNewHeadIndex(int headIndex) {
        return new NamedRelation(
                headIndex,
                tailMentionIndex,
                type,
                name
        );
    }

    @Override
    public Relation withNewTailIndex(int tailIndex) {
        return new NamedRelation(
                headMentionIndex,
                tailIndex,
                type,
                name
        );
    }
}
