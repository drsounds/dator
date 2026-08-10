package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jexer.TAction;
import jexer.TCheckBox;
import jexer.TKeypress;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.db.DataRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorRelation;
import se.spacify.dator.model.DatorTable;

/**
 * Lets the user filter a data list by one or more belongs-to (FK) columns,
 * each offered as a multi-select checklist of referenced rows. A row passes
 * the filter if, for every column with at least one checked value, its
 * value is among those checked (AND across columns, OR within a column).
 */
public class FilterWindow extends TWindow {

    private static final int MAX_OPTIONS_PER_COLUMN = 15;

    private final DatorApplication app;
    private final DataListWindow owner;
    private final DatorTable table;
    private final Map<String, Set<String>> currentSelections;

    private final Map<String, List<TCheckBox>> checkBoxesByColumn = new LinkedHashMap<>();
    private final Map<TCheckBox, String> valueByCheckBox = new LinkedHashMap<>();

    public FilterWindow(DatorApplication app, DataListWindow owner, DatorTable table,
            Map<String, Set<String>> currentSelections) {
        super(app, "Filter: " + table.getDisplayLabel(), 70, computeHeight(app, table), TWindow.MODAL);
        this.app = app;
        this.owner = owner;
        this.table = table;
        this.currentSelections = currentSelections;
        setupWidgets();
    }

    private static int computeHeight(DatorApplication app, DatorTable table) {
        int rows = 0;
        try {
            for (DatorRelation rel : app.getMetaRepository().listRelations(table.getId())) {
                List<DatorColumn> cols = app.getMetaRepository().listColumns(table.getId());
                if (findColumnById(cols, rel.getColumnId()) == null) {
                    continue;
                }
                List<DatorColumn> refColumns = app.getMetaRepository().listColumns(rel.getRefTableId());
                int optionCount = Math.min(MAX_OPTIONS_PER_COLUMN,
                        app.getDataRepository().listRows(app.getMetaRepository().getTable(rel.getRefTableId()),
                                refColumns).size());
                rows += 1 + Math.max(1, optionCount) + 1;
            }
        } catch (Exception e) {
            rows = 10;
        }
        return Math.min(40, 8 + Math.max(3, rows));
    }

    private static DatorColumn findColumnById(List<DatorColumn> cols, int id) {
        for (DatorColumn c : cols) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private void setupWidgets() {
        addLabel("Check values to include; leave a column unchecked to skip filtering it.", 2, 1);

        int y = 3;
        List<DatorRelation> relations;
        List<DatorColumn> myColumns;
        try {
            relations = app.getMetaRepository().listRelations(table.getId());
            myColumns = app.getMetaRepository().listColumns(table.getId());
        } catch (Exception e) {
            app.showError(e);
            relations = List.of();
            myColumns = List.of();
        }

        if (relations.isEmpty()) {
            addLabel("This table has no belongs-to columns to filter by.", 2, y);
            y += 2;
        }

        for (DatorRelation rel : relations) {
            DatorColumn fkColumn = findColumnById(myColumns, rel.getColumnId());
            if (fkColumn == null) {
                continue;
            }
            addLabel(fkColumn.getDisplayLabel() + ":", 2, y);
            y++;

            List<TCheckBox> boxes = new ArrayList<>();
            try {
                DatorTable refTable = app.getMetaRepository().getTable(rel.getRefTableId());
                if (refTable != null) {
                    List<DatorColumn> refColumns = app.getMetaRepository().listColumns(refTable.getId());
                    DatorColumn refValueColumn = rel.getRefColumnId() == null
                            ? null : findColumnById(refColumns, rel.getRefColumnId());
                    DatorColumn displayColumn = GridUtil.pickDisplayColumn(refColumns, refValueColumn);
                    List<Map<String, Object>> refRows = app.getDataRepository().listRows(refTable, refColumns);
                    Set<String> already = currentSelections.getOrDefault(fkColumn.getName(), Set.of());

                    int shown = 0;
                    for (Map<String, Object> row : refRows) {
                        if (shown >= MAX_OPTIONS_PER_COLUMN) {
                            addLabel("  ... and " + (refRows.size() - shown) + " more not shown", 4, y);
                            y++;
                            break;
                        }
                        Object value = refValueColumn != null
                                ? row.get(refValueColumn.getName()) : row.get(DataRepository.ROWID);
                        Object display = displayColumn != null ? row.get(displayColumn.getName()) : null;
                        String label = (display == null) ? String.valueOf(value) : value + ": " + display;
                        String valueStr = String.valueOf(value);

                        TCheckBox cb = addCheckBox(4, y, label, already.contains(valueStr));
                        boxes.add(cb);
                        valueByCheckBox.put(cb, valueStr);
                        y++;
                        shown++;
                    }
                }
            } catch (Exception e) {
                app.showError(e);
            }
            checkBoxesByColumn.put(fkColumn.getName(), boxes);
            y++;
        }

        addButton("Apply", 2, getHeight() - 3, new TAction() {
            public void DO() {
                doApply();
            }
        });
        addButton("Clear All", 13, getHeight() - 3, new TAction() {
            public void DO() {
                clearAll();
            }
        });
        addButton("Close", getWidth() - 12, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private void clearAll() {
        for (List<TCheckBox> boxes : checkBoxesByColumn.values()) {
            for (TCheckBox cb : boxes) {
                cb.setChecked(false);
            }
        }
    }

    private void doApply() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<TCheckBox>> entry : checkBoxesByColumn.entrySet()) {
            Set<String> chosen = new LinkedHashSet<>();
            for (TCheckBox cb : entry.getValue()) {
                if (cb.isChecked()) {
                    chosen.add(valueByCheckBox.get(cb));
                }
            }
            if (!chosen.isEmpty()) {
                result.put(entry.getKey(), chosen);
            }
        }
        owner.applyFilter(result);
        close();
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
