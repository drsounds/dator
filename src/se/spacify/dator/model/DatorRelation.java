package se.spacify.dator.model;

/**
 * A foreign-key style relation from a column on one table model to a
 * (usually primary-key) column on another, stored in dator_table_relations.
 */
public class DatorRelation {

    public static final String MANY_TO_ONE = "many-to-one";
    public static final String ONE_TO_ONE = "one-to-one";

    private int id;
    private int tableId;
    private int columnId;
    private int refTableId;
    private Integer refColumnId;
    private String relationType = MANY_TO_ONE;
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

    public int getRefTableId() {
        return refTableId;
    }

    public void setRefTableId(int refTableId) {
        this.refTableId = refTableId;
    }

    public Integer getRefColumnId() {
        return refColumnId;
    }

    public void setRefColumnId(Integer refColumnId) {
        this.refColumnId = refColumnId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
