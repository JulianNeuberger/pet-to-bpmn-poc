package data;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Relation {

    protected final Integer headMentionIndex;
    protected final Integer tailMentionIndex;
    protected final String type;

    public Relation(
            @JsonProperty("headMentionIndex") Integer headMentionIndex,
            @JsonProperty("tailMentionIndex") Integer tailMentionIndex,
            @JsonProperty("type") String type
    ) {
        this.headMentionIndex = headMentionIndex;
        this.tailMentionIndex = tailMentionIndex;
        this.type = type;
    }

    public Integer headMentionIndex() {
        return headMentionIndex;
    }

    public Integer tailMentionIndex() {
        return tailMentionIndex;
    }

    public String type() {
        return type;
    }

    public Mention head(Document document) {
        return document.mentions().get(this.headMentionIndex);
    }

    public Mention tail(Document document) {
        return document.mentions().get(this.tailMentionIndex);
    }

    public Relation withNewHeadIndex(int headIndex) {
        return new Relation(
                headIndex,
                tailMentionIndex,
                type
        );
    }

    public Relation withNewTailIndex(int tailIndex) {
        return new Relation(
                headMentionIndex,
                tailIndex,
                type
        );
    }
}
