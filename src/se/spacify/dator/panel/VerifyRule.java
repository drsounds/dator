package se.spacify.dator.panel;

import java.util.List;

/**
 * A )PROC statement: "VER (&amp;FIELD,RULE[,args...])". Checked against the
 * entered field values before a save is allowed to proceed, in declared
 * order, mirroring ISPF Dialog Manager's VER built-in.
 */
public class VerifyRule {

    public static final String NONBLANK = "NONBLANK";
    public static final String NUM = "NUM";
    public static final String RANGE = "RANGE";

    private final String field;
    private final String kind;
    private final List<String> args;

    public VerifyRule(String field, String kind, List<String> args) {
        this.field = field;
        this.kind = kind;
        this.args = args;
    }

    public String getField() {
        return field;
    }

    public String getKind() {
        return kind;
    }

    public List<String> getArgs() {
        return args;
    }
}
