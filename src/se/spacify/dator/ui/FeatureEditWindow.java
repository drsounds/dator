package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jexer.TAction;
import jexer.TComboBox;
import jexer.TEditorWidget;
import jexer.TField;
import jexer.TKeypress;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.model.DatorApp;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorFeature;
import se.spacify.dator.model.DatorTable;
import se.spacify.dator.panel.PanelDefinition;
import se.spacify.dator.panel.PanelParseException;
import se.spacify.dator.panel.PanelParser;

/**
 * Create/edit window for a DatorFeature: name, label, shortcut, the
 * DatorTable it binds to, and its ISPF Dialog Manager-style panel source
 * (edited free-form, with Validate and a starter Template button).
 */
public class FeatureEditWindow extends TWindow {

    private final DatorApplication app;
    private final DatorApp datorApp;
    private final DatorFeature editing;
    private final boolean isNew;
    private final TAction onSaved;

    private List<DatorTable> allTables = new ArrayList<>();

    private TField nameField;
    private TField labelField;
    private TField shortcutField;
    private TComboBox tableCombo;
    private TEditorWidget editor;

    public FeatureEditWindow(DatorApplication app, DatorApp datorApp, DatorFeature existing, TAction onSaved) {
        super(app, existing == null ? "New Feature" : "Edit Feature: " + existing.getName(), 100, 34,
                TWindow.MODAL);
        this.app = app;
        this.datorApp = datorApp;
        this.isNew = (existing == null);
        this.editing = isNew ? new DatorFeature() : existing;
        this.onSaved = onSaved;
        loadTables();
        setupWidgets();
    }

    private void loadTables() {
        try {
            allTables = app.getMetaRepository().listTables();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private void setupWidgets() {
        addLabel("Feature name:", 2, 1);
        nameField = addField(17, 1, 20, false, isNew ? "" : editing.getName());
        addLabel("Label:", 40, 1);
        labelField = addField(47, 1, 30, false, editing.getLabel() == null ? "" : editing.getLabel());

        addLabel("Table:", 2, 3);
        List<String> tableNames = new ArrayList<>();
        int selectedIndex = -1;
        for (int i = 0; i < allTables.size(); i++) {
            tableNames.add(allTables.get(i).getName());
            if (!isNew && allTables.get(i).getId() == editing.getTableId()) {
                selectedIndex = i;
            }
        }
        if (isNew && !tableNames.isEmpty()) {
            selectedIndex = 0;
        }
        tableCombo = addComboBox(17, 3, 30, tableNames, selectedIndex, 6, null);

        addLabel("Shortcut:", 52, 3);
        shortcutField = addField(62, 3, 18, false, editing.getShortcut() == null ? "" : editing.getShortcut());

        addLabel("Panel source: )ATTR / )BODY / )INIT / )PROC / )END  (@FIELD binds to a column by name)",
                2, 5);
        editor = addEditor(isNew ? "" : nullToEmpty(editing.getPanelSource()), 2, 6, getWidth() - 4,
                getHeight() - 12);
        if (isNew) {
            editor.setText(defaultTemplate());
        }

        addButton("Save", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doSave();
            }
        });
        addButton("Validate", 13, getHeight() - 3, new TAction() {
            public void DO() {
                doValidate();
            }
        });
        addButton("Template", 27, getHeight() - 3, new TAction() {
            public void DO() {
                doTemplate();
            }
        });
        addButton("Cancel", 41, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private DatorTable selectedTable() {
        String name = tableCombo.getText();
        for (DatorTable t : allTables) {
            if (t.getName().equals(name)) {
                return t;
            }
        }
        return null;
    }

    private void doTemplate() {
        DatorTable t = selectedTable();
        if (t == null) {
            app.messageBox("No Table", "Choose a table first.");
            return;
        }
        try {
            List<DatorColumn> columns = app.getMetaRepository().listColumns(t.getId());
            editor.setText(buildTemplate(columns));
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private String buildTemplate(List<DatorColumn> columns) {
        StringBuilder attr = new StringBuilder();
        StringBuilder body = new StringBuilder();
        StringBuilder proc = new StringBuilder();

        attr.append(")ATTR\n");
        attr.append("  @ TYPE(INPUT) LEN(20)\n");
        attr.append("  % TYPE(OUTPUT) LEN(20)\n");

        body.append(")BODY\n");
        for (DatorColumn c : columns) {
            boolean autoOutput = c.isPk() && "INTEGER".equalsIgnoreCase(c.getDataType());
            char code = autoOutput ? '%' : '@';
            String label = c.getDisplayLabel();
            String padded = label + " ";
            while (padded.length() < 20) {
                padded += ". ";
            }
            if (padded.length() > 20) {
                padded = padded.substring(0, 20);
            }
            body.append(" ").append(padded).append(code).append(c.getName().toUpperCase(Locale.ROOT)).append("\n");
            if (!autoOutput && !c.isNullable()) {
                proc.append("  VER (&").append(c.getName().toUpperCase(Locale.ROOT)).append(",NONBLANK)\n");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(attr);
        sb.append(body);
        sb.append(")INIT\n");
        sb.append(")PROC\n");
        sb.append(proc);
        sb.append(")END\n");
        return sb.toString();
    }

    private String defaultTemplate() {
        return ")ATTR\n" +
                "  @ TYPE(INPUT) LEN(20)\n" +
                "  % TYPE(OUTPUT) LEN(20)\n" +
                ")BODY\n" +
                " Pick a table above, then press Template to generate this panel.\n" +
                ")INIT\n" +
                ")PROC\n" +
                ")END\n";
    }

    private void doValidate() {
        DatorTable t = selectedTable();
        if (t == null) {
            app.messageBox("No Table", "Choose a table first.");
            return;
        }
        try {
            PanelDefinition def = PanelParser.parse(editor.getText());
            List<DatorColumn> columns = app.getMetaRepository().listColumns(t.getId());
            List<String> colNames = new ArrayList<>();
            for (DatorColumn c : columns) {
                colNames.add(c.getName().toUpperCase(Locale.ROOT));
            }
            List<String> unmatched = new ArrayList<>();
            for (se.spacify.dator.panel.PanelField f : def.getFields()) {
                if (!colNames.contains(f.getName())) {
                    unmatched.add(f.getName());
                }
            }
            if (!unmatched.isEmpty()) {
                app.messageBox("Panel Error", "These fields don't match any column on \"" + t.getName() +
                        "\": " + String.join(", ", unmatched));
                return;
            }
            app.messageBox("Panel OK", "Panel is valid: " + def.getFields().size() + " field(s), " +
                    def.getVerifyRules().size() + " VER rule(s).");
        } catch (PanelParseException e) {
            app.messageBox("Panel Error", e.getMessage());
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private void doSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            app.messageBox("Validation Error", "Feature name is required.");
            return;
        }
        DatorTable t = selectedTable();
        if (t == null) {
            app.messageBox("Validation Error", "Choose a table for this feature to bind to.");
            return;
        }
        String panelSource = editor.getText();
        try {
            PanelDefinition def = PanelParser.parse(panelSource);
            List<DatorColumn> columns = app.getMetaRepository().listColumns(t.getId());
            List<String> colNames = new ArrayList<>();
            for (DatorColumn c : columns) {
                colNames.add(c.getName().toUpperCase(Locale.ROOT));
            }
            for (se.spacify.dator.panel.PanelField f : def.getFields()) {
                if (!colNames.contains(f.getName())) {
                    app.messageBox("Validation Error", "Panel field \"" + f.getName() +
                            "\" does not match any column on \"" + t.getName() + "\".");
                    return;
                }
            }
        } catch (PanelParseException e) {
            app.messageBox("Panel Error", e.getMessage());
            return;
        } catch (Exception e) {
            app.showError(e);
            return;
        }

        editing.setAppId(datorApp.getId());
        editing.setTableId(t.getId());
        editing.setName(name);
        editing.setLabel(labelField.getText().trim().isEmpty() ? null : labelField.getText().trim());
        editing.setShortcut(shortcutField.getText().trim().isEmpty() ? null : shortcutField.getText().trim());
        editing.setPanelSource(panelSource);

        try {
            app.getAppRepository().saveFeature(editing);
            if (onSaved != null) {
                onSaved.DO();
            }
            close();
        } catch (Exception e) {
            app.showError(e);
        }
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        if (event.getKey().equals(TKeypress.kbEsc)) {
            close();
            return;
        }
        super.onKeypress(event);
    }
}
