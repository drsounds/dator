package se.spacify.dator.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parsed form of an ISPF Dialog Manager-style panel: )ATTR attribute
 * characters, the )BODY layout split into fields and literal text runs,
 * )INIT default-value assignments and )PROC VER validation rules.
 */
public class PanelDefinition {

    private final Map<Character, PanelAttr> attrs = new LinkedHashMap<>();
    private final List<PanelField> fields = new ArrayList<>();
    private final List<PanelLiteral> literals = new ArrayList<>();
    private final List<InitAssignment> initAssignments = new ArrayList<>();
    private final List<VerifyRule> verifyRules = new ArrayList<>();
    private int bodyRows;
    private int bodyCols;

    void putAttr(PanelAttr attr) {
        attrs.put(attr.getCode(), attr);
    }

    void addField(PanelField field) {
        fields.add(field);
    }

    void addLiteral(PanelLiteral literal) {
        literals.add(literal);
    }

    void addInit(InitAssignment assignment) {
        initAssignments.add(assignment);
    }

    void addVerify(VerifyRule rule) {
        verifyRules.add(rule);
    }

    void growBounds(int row, int lineLength) {
        bodyRows = Math.max(bodyRows, row + 1);
        bodyCols = Math.max(bodyCols, lineLength);
    }

    public Map<Character, PanelAttr> getAttrs() {
        return attrs;
    }

    public List<PanelField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public List<PanelLiteral> getLiterals() {
        return Collections.unmodifiableList(literals);
    }

    public List<InitAssignment> getInitAssignments() {
        return Collections.unmodifiableList(initAssignments);
    }

    public List<VerifyRule> getVerifyRules() {
        return Collections.unmodifiableList(verifyRules);
    }

    public int getBodyRows() {
        return bodyRows;
    }

    public int getBodyCols() {
        return bodyCols;
    }

    public PanelField findField(String name) {
        for (PanelField f : fields) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }
}
