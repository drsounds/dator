package se.spacify.dator.model;

/**
 * A user-defined table model, backed by a row in the dator_tables meta table
 * and (once saved) a real SQLite table of the same name.
 */
public class DatorTable {

    private int id;
    private String name;
    private String label;

    public DatorTable() {
    }

    public DatorTable(int id, String name, String label) {
        this.id = id;
        this.name = name;
        this.label = label;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDisplayLabel() {
        return (label == null || label.isEmpty()) ? name : label;
    }

    @Override
    public String toString() {
        return getDisplayLabel();
    }
}
