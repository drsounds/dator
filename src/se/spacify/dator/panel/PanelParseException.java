package se.spacify.dator.panel;

/**
 * Thrown for a syntactically or semantically invalid panel definition,
 * including a source line number whenever one is available.
 */
public class PanelParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PanelParseException(String message) {
        super(message);
    }

    public PanelParseException(int lineNumber, String message) {
        super("Line " + lineNumber + ": " + message);
    }
}
