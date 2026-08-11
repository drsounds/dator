package se.spacify.dator.model;

/**
 * A user-defined "app": a named group of features, backed by a row in the
 * dator_apps meta table.
 */
public class DatorApp {

    private int id;
    private String name;
    private String label;
    private String description;

    public DatorApp() {
    }

    public DatorApp(int id, String name, String label, String description) {
        this.id = id;
        this.name = name;
        this.label = label;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayLabel() {
        return (label == null || label.isEmpty()) ? name : label;
    }

    @Override
    public String toString() {
        return getDisplayLabel();
    }
}
