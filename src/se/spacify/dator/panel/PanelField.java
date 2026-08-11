package se.spacify.dator.panel;

/**
 * One field placed in )BODY: an attribute character immediately followed by
 * an identifier, e.g. "@CUSTID". The identifier is later matched against a
 * DatorColumn name (case-insensitively) when the panel is bound to a table.
 */
public class PanelField {

    private final String name;
    private final int row;
    private final int col;
    private final PanelAttr attr;

    public PanelField(String name, int row, int col, PanelAttr attr) {
        this.name = name;
        this.row = row;
        this.col = col;
        this.attr = attr;
    }

    public String getName() {
        return name;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public PanelAttr getAttr() {
        return attr;
    }
}
