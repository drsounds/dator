package se.spacify.dator.panel;

/**
 * A run of literal (non-field) text from )BODY, positioned at a row/column
 * so it can be redrawn verbatim as a label.
 */
public class PanelLiteral {

    private final String text;
    private final int row;
    private final int col;

    public PanelLiteral(String text, int row, int col) {
        this.text = text;
        this.row = row;
        this.col = col;
    }

    public String getText() {
        return text;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }
}
