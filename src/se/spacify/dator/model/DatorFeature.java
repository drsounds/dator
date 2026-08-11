package se.spacify.dator.model;

/**
 * A feature owned by a DatorApp: binds an ISPF Dialog Manager-style panel
 * (see se.spacify.dator.panel) to a DatorTable, with an optional menu
 * shortcut for jumping straight to it. Backed by dator_features.
 */
public class DatorFeature {

    private int id;
    private int appId;
    private int tableId;
    private String name;
    private String label;
    private String shortcut;
    private String panelSource;
    private int ordinal;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAppId() {
        return appId;
    }

    public void setAppId(int appId) {
        this.appId = appId;
    }

    public int getTableId() {
        return tableId;
    }

    public void setTableId(int tableId) {
        this.tableId = tableId;
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

    public String getShortcut() {
        return shortcut;
    }

    public void setShortcut(String shortcut) {
        this.shortcut = shortcut;
    }

    public String getPanelSource() {
        return panelSource;
    }

    public void setPanelSource(String panelSource) {
        this.panelSource = panelSource;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public String getDisplayLabel() {
        return (label == null || label.isEmpty()) ? name : label;
    }

    @Override
    public String toString() {
        return getDisplayLabel();
    }
}
