package se.spacify.dator.panel;

/**
 * A )INIT statement: "&FIELD = value". Applied as the field's default text
 * when opening the panel for a brand-new row.
 */
public class InitAssignment {

    private final String field;
    private final String value;

    public InitAssignment(String field, String value) {
        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }
}
