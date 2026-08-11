package se.spacify.dator.panel;

/**
 * A )ATTR definition: a single punctuation character that, when it appears
 * in )BODY immediately before an identifier, marks that identifier as a
 * field of this type/length rather than literal text.
 */
public class PanelAttr {

    public static final String INPUT = "INPUT";
    public static final String OUTPUT = "OUTPUT";

    private final char code;
    private String type = INPUT;
    private int len = 20;
    private boolean caps;
    private boolean required;

    public PanelAttr(char code) {
        this.code = code;
    }

    public char getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    void setType(String type) {
        this.type = type;
    }

    public int getLen() {
        return len;
    }

    void setLen(int len) {
        this.len = len;
    }

    public boolean isCaps() {
        return caps;
    }

    void setCaps(boolean caps) {
        this.caps = caps;
    }

    public boolean isRequired() {
        return required;
    }

    void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isInput() {
        return INPUT.equals(type);
    }
}
