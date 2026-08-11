package se.spacify.dator.ui;

import jexer.TAction;
import jexer.TField;
import jexer.TKeypress;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.model.DatorApp;

/**
 * Create/edit window for a DatorApp: name, display label and description.
 */
public class AppEditWindow extends TWindow {

    private final DatorApplication app;
    private final DatorApp editing;
    private final boolean isNew;
    private final TAction onSaved;

    private TField nameField;
    private TField labelField;
    private TField descriptionField;

    public AppEditWindow(DatorApplication app, DatorApp existing, TAction onSaved) {
        super(app, existing == null ? "New App" : "Edit App: " + existing.getName(), 66, 12, TWindow.MODAL);
        this.app = app;
        this.isNew = (existing == null);
        this.editing = isNew ? new DatorApp() : existing;
        this.onSaved = onSaved;
        setupWidgets();
    }

    private void setupWidgets() {
        addLabel("App name:", 2, 1);
        nameField = addField(16, 1, 24, false, isNew ? "" : editing.getName());
        addLabel("Display label:", 2, 3);
        labelField = addField(16, 3, 40, false, editing.getLabel() == null ? "" : editing.getLabel());
        addLabel("Description:", 2, 5);
        descriptionField = addField(16, 5, 40, false,
                editing.getDescription() == null ? "" : editing.getDescription());

        addButton("Save", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doSave();
            }
        });
        addButton("Cancel", 13, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private void doSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            app.messageBox("Validation Error", "App name is required.");
            return;
        }
        editing.setName(name);
        editing.setLabel(labelField.getText().trim().isEmpty() ? null : labelField.getText().trim());
        editing.setDescription(
                descriptionField.getText().trim().isEmpty() ? null : descriptionField.getText().trim());
        try {
            app.getAppRepository().saveApp(editing);
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
