package se.spacify.dator.model;

/**
 * A per-table aggregate row (SUM/AVG/COUNT/MIN/MAX over one column) shown at
 * the top and/or bottom of a data grid, stored in dator_table_summaries.
 */
public class DatorSummary {

    public static final String SUM = "SUM";
    public static final String AVG = "AVG";
    public static final String COUNT = "COUNT";
    public static final String MIN = "MIN";
    public static final String MAX = "MAX";

    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";
    public static final String BOTH = "both";

    private int id;
    private int tableId;
    private int columnId;
    private String aggregate = SUM;
    private String position = BOTTOM;
    private String label;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTableId() {
        return tableId;
    }

    public void setTableId(int tableId) {
        this.tableId = tableId;
    }

    public int getColumnId() {
        return columnId;
    }

    public void setColumnId(int columnId) {
        this.columnId = columnId;
    }

    public String getAggregate() {
        return aggregate;
    }

    public void setAggregate(String aggregate) {
        this.aggregate = aggregate;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** Whether this summary should be rendered at the given position ("top"/"bottom"). */
    public boolean showsAt(String pos) {
        return BOTH.equals(position) || pos.equals(position);
    }
}
