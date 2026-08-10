package se.spacify.dator.model;

/**
 * A user-defined column belonging to a DatorTable model, stored in
 * dator_table_columns and materialized as a real column on the physical
 * SQLite table.
 */
public class DatorColumn {

    private int id;
    private int tableId;
    private String name;
    private String label;
    private String dataType;
    private boolean pk;
    private boolean nullable = true;
    private boolean unique;
    private String defaultValue;
    private int ordinal;

    public DatorColumn() {
    }

    public DatorColumn(String name, String dataType) {
        this.name = name;
        this.dataType = dataType;
    }

    public DatorColumn copy() {
        DatorColumn c = new DatorColumn();
        c.id = id;
        c.tableId = tableId;
        c.name = name;
        c.label = label;
        c.dataType = dataType;
        c.pk = pk;
        c.nullable = nullable;
        c.unique = unique;
        c.defaultValue = defaultValue;
        c.ordinal = ordinal;
        return c;
    }

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

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public boolean isPk() {
        return pk;
    }

    public void setPk(boolean pk) {
        this.pk = pk;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    @Override
    public String toString() {
        return getDisplayLabel();
    }
}
