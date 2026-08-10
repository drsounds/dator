package se.spacify.dator.model;

/**
 * A user-defined table model, backed by a row in the dator_tables meta table
 * and (once saved) a real SQLite table of the same name.
 */
public class DatorTable {

    public static final String SORT_ASC = "asc";
    public static final String SORT_DESC = "desc";

    public static final String VIEW_TABLE = "table";
    public static final String VIEW_CARD = "card";

    private int id;
    private String name;
    private String label;
    /** Column to order list views by; null means the default (number/rowid) ordering. */
    private Integer sortColumnId;
    private String sortDirection = SORT_ASC;
    /** Per-table default list view: "table" (grid) or "card" (stacked cards). */
    private String listViewMode = VIEW_TABLE;

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

    public Integer getSortColumnId() {
        return sortColumnId;
    }

    public void setSortColumnId(Integer sortColumnId) {
        this.sortColumnId = sortColumnId;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public String getListViewMode() {
        return listViewMode;
    }

    public void setListViewMode(String listViewMode) {
        this.listViewMode = listViewMode;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }

    @Override
    public String toString() {
        return getDisplayLabel();
    }
}
