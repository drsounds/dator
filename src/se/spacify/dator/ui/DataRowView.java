package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jexer.TAction;
import jexer.TButton;
import jexer.TKeypress;
import jexer.TLabel;
import jexer.TTableWidget;
import jexer.TWidget;
import jexer.TWindow;
import jexer.event.TKeypressEvent;

import se.spacify.dator.db.DataRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorRelation;
import se.spacify.dator.model.DatorSummary;
import se.spacify.dator.model.DatorTable;

/**
 * Read-focused detail view of a single row: a header (table label + the
 * row's "name" column), an overview of every field, relations configured as
 * DISPLAY_TABLE shown inline below the fields, and relations configured as
 * DISPLAY_TAB reachable through a simple tab bar. Enter on any related-row
 * grid drills into that row's own view.
 */
public class DataRowView extends TWindow {

    private static final int FIELD_GRID_MAX_ROWS = 10;
    private static final int CHILD_GRID_MAX_ROWS = 6;

    private final DatorApplication app;
    private final DatorTable table;

    private long rowid;
    private List<DatorColumn> columns = new ArrayList<>();
    private Map<String, Object> row = new LinkedHashMap<>();
    private List<DatorRelation> tabRelations = new ArrayList<>();
    private List<DatorRelation> inlineRelations = new ArrayList<>();
    private List<String> tabNames = new ArrayList<>();
    private int selectedTab = 0;
    private int contentTop = 4;

    private TLabel rowNameLabel;
    private final List<TButton> tabButtons = new ArrayList<>();
    private final List<TWidget> contentWidgets = new ArrayList<>();
    private final Map<TTableWidget, List<Map<String, Object>>> gridChildRows = new HashMap<>();
    private final Map<TTableWidget, DatorTable> gridChildTable = new HashMap<>();

    public DataRowView(DatorApplication app, DatorTable table, long rowid) {
        super(app, "View: " + table.getDisplayLabel(), 100, 30);
        this.app = app;
        this.table = table;
        loadRow(rowid);
        setupChrome();
        rebuildContent();
    }

    private void loadRow(long rowid) {
        this.rowid = rowid;
        try {
            columns = app.getMetaRepository().listColumns(table.getId());
            Map<String, Object> loaded = app.getDataRepository().getRow(table, columns, rowid);
            row = (loaded == null) ? new LinkedHashMap<>() : loaded;

            tabRelations.clear();
            inlineRelations.clear();
            for (DatorRelation r : app.getMetaRepository().listRelationsReferencing(table.getId())) {
                if (r.getTableId() == table.getId()) {
                    // Self-referencing parent_id "Children" relation, auto-wired on
                    // every table: hidden here, it's rarely used and mostly empty.
                    continue;
                }
                if (DatorRelation.DISPLAY_TABLE.equals(r.getDisplayMode())) {
                    inlineRelations.add(r);
                } else {
                    tabRelations.add(r);
                }
            }

            tabNames.clear();
            tabNames.add("Overview");
            for (DatorRelation r : tabRelations) {
                tabNames.add(tabLabelFor(r));
            }
            if (selectedTab >= tabNames.size()) {
                selectedTab = 0;
            }
        } catch (Exception e) {
            app.showError(e);
        }
    }

    private String tabLabelFor(DatorRelation r) {
        if (r.getLabel() != null && !r.getLabel().isEmpty()) {
            return r.getLabel();
        }
        try {
            DatorTable t = app.getMetaRepository().getTable(r.getTableId());
            return t == null ? ("#" + r.getTableId()) : t.getDisplayLabel();
        } catch (Exception e) {
            return "#" + r.getTableId();
        }
    }

    private String computeRowName() {
        for (DatorColumn c : columns) {
            if (c.getName().equalsIgnoreCase("name")) {
                Object v = row.get(c.getName());
                return v == null ? "" : v.toString();
            }
        }
        return "#" + rowid;
    }

    private void setupChrome() {
        addLabel(table.getDisplayLabel(), 2, 1);
        rowNameLabel = addLabel(computeRowName(), 2, 2);

        rebuildTabBar();

        addButton("Edit", 2, getHeight() - 3, new TAction() {
            public void DO() {
                editRow();
            }
        });
        addButton("Refresh", 13, getHeight() - 3, new TAction() {
            public void DO() {
                reload();
            }
        });
        addButton("Close", getWidth() - 12, getHeight() - 3, new TAction() {
            public void DO() {
                close();
            }
        });
    }

    private void rebuildTabBar() {
        for (TButton b : tabButtons) {
            b.remove();
        }
        tabButtons.clear();

        if (tabRelations.isEmpty()) {
            contentTop = 4;
            return;
        }
        int x = 2;
        for (int i = 0; i < tabNames.size(); i++) {
            final int idx = i;
            String label = (i == selectedTab) ? "[" + tabNames.get(i) + "]" : " " + tabNames.get(i) + " ";
            TButton b = addButton(label, x, 4, new TAction() {
                public void DO() {
                    selectedTab = idx;
                    rebuildTabBar();
                    rebuildContent();
                }
            });
            tabButtons.add(b);
            x += label.length() + 2;
        }
        contentTop = 6;
    }

    private void rebuildContent() {
        for (TWidget w : contentWidgets) {
            w.remove();
        }
        contentWidgets.clear();
        gridChildRows.clear();
        gridChildTable.clear();

        int left = 2;
        int top = contentTop;
        int width = getWidth() - 4;

        if (selectedTab == 0) {
            top = buildFieldGrid(left, top, width);
            for (DatorRelation r : inlineRelations) {
                top = buildChildSection(left, top, width, r, false);
            }
        } else {
            DatorRelation r = tabRelations.get(selectedTab - 1);
            buildChildSection(left, top, width, r, true);
        }
    }

    private int buildFieldGrid(int x, int y, int width) {
        int rowCount = Math.max(columns.size(), 1);
        int height = Math.min(rowCount, FIELD_GRID_MAX_ROWS) + 2;
        TTableWidget grid = new TabbableTable(this, x, y, width, height, 2, rowCount);
        grid.setColumnLabel(0, "Field");
        grid.setColumnLabel(1, "Value");
        grid.setColumnWidth(0, 22);
        grid.setColumnWidth(1, Math.max(10, width - 26));
        if (columns.isEmpty()) {
            grid.setCellText(0, 0, "(no columns)");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                DatorColumn c = columns.get(i);
                grid.setCellText(0, i, c.getDisplayLabel());
                grid.setCellText(1, i, GridUtil.formatValue(row.get(c.getName())));
            }
        }
        grid.setColumnReadOnly(0, true);
        grid.setColumnReadOnly(1, true);
        grid.setHighlightRow(true);
        contentWidgets.add(grid);
        activate(grid);
        return y + height + 1;
    }

    private int buildChildSection(int x, int y, int width, DatorRelation rel, boolean fillRemaining) {
        try {
            DatorTable childTable = app.getMetaRepository().getTable(rel.getTableId());
            if (childTable == null) {
                return y;
            }
            List<DatorColumn> childColumns = app.getMetaRepository().listColumns(childTable.getId());
            DatorColumn fkColumn = findColumnById(childColumns, rel.getColumnId());
            Object matchValue = (rel.getRefColumnId() != null)
                    ? row.get(columnNameById(columns, rel.getRefColumnId()))
                    : row.get(DataRepository.ROWID);

            List<Map<String, Object>> allRows = app.getDataRepository().listRows(childTable, childColumns);
            List<Map<String, Object>> filtered = new ArrayList<>();
            if (fkColumn != null && matchValue != null) {
                for (Map<String, Object> r : allRows) {
                    Object v = r.get(fkColumn.getName());
                    if (v != null && String.valueOf(v).equals(String.valueOf(matchValue))) {
                        filtered.add(r);
                    }
                }
            }

            String label = (rel.getLabel() != null && !rel.getLabel().isEmpty())
                    ? rel.getLabel() : childTable.getDisplayLabel();
            TLabel sectionLabel = addLabel(label + " (" + filtered.size() + ")  [Enter=view row]", x, y);
            contentWidgets.add(sectionLabel);
            y += 1;

            List<DatorSummary> childSummaries = app.getMetaRepository().listSummaries(childTable.getId());
            Map<Integer, Object> summaryValues = computeChildSummaries(childColumns, childSummaries, filtered);

            TTableWidget topSummary = GridUtil.buildSummaryRow(this, x, y, width, childColumns, childSummaries,
                    summaryValues, DatorSummary.TOP);
            if (topSummary != null) {
                contentWidgets.add(topSummary);
                y += 4;
            }

            int rowCount = Math.max(filtered.size(), 1);
            int colCount = Math.max(childColumns.size(), 1);
            boolean hasBottomSummary = GridUtil.hasSummaryAt(childSummaries, DatorSummary.BOTTOM);
            int height = fillRemaining
                    ? Math.max(3, getHeight() - y - 4 - (hasBottomSummary ? 4 : 0))
                    : Math.min(rowCount, CHILD_GRID_MAX_ROWS) + 2;

            TTableWidget grid = new TabbableTable(this, x, y, width, height, colCount, rowCount);
            if (childColumns.isEmpty()) {
                grid.setColumnLabel(0, "(no columns)");
            } else {
                for (int c = 0; c < childColumns.size(); c++) {
                    DatorColumn cc = childColumns.get(c);
                    grid.setColumnLabel(c, cc.getDisplayLabel());
                    grid.setColumnWidth(c, Math.max(10, Math.min(20, cc.getDisplayLabel().length() + 2)));
                }
            }
            if (filtered.isEmpty()) {
                if (!childColumns.isEmpty()) {
                    grid.setCellText(0, 0, "(none)");
                }
            } else {
                for (int r = 0; r < filtered.size(); r++) {
                    for (int c = 0; c < childColumns.size(); c++) {
                        grid.setCellText(c, r, GridUtil.formatValue(filtered.get(r).get(childColumns.get(c).getName())));
                    }
                }
            }
            for (int c = 0; c < colCount; c++) {
                grid.setColumnReadOnly(c, true);
            }
            grid.setHighlightRow(true);
            contentWidgets.add(grid);
            gridChildRows.put(grid, filtered);
            gridChildTable.put(grid, childTable);
            if (fillRemaining) {
                activate(grid);
            }
            y += height + 1;

            TTableWidget bottomSummary = GridUtil.buildSummaryRow(this, x, y, width, childColumns, childSummaries,
                    summaryValues, DatorSummary.BOTTOM);
            if (bottomSummary != null) {
                contentWidgets.add(bottomSummary);
                y += 4;
            }

            return y;
        } catch (Exception e) {
            app.showError(e);
            return y + 1;
        }
    }

    private DatorColumn findColumnById(List<DatorColumn> cols, int id) {
        for (DatorColumn c : cols) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private String columnNameById(List<DatorColumn> cols, int id) {
        DatorColumn c = findColumnById(cols, id);
        return c == null ? null : c.getName();
    }

    /** In-memory aggregation over an already-filtered set of child rows (parent-scoped, not table-wide). */
    private Map<Integer, Object> computeChildSummaries(List<DatorColumn> childColumns,
            List<DatorSummary> summaries, List<Map<String, Object>> rows) {
        Map<Integer, Object> result = new HashMap<>();
        for (DatorSummary s : summaries) {
            DatorColumn col = findColumnById(childColumns, s.getColumnId());
            if (col == null) {
                continue;
            }
            String agg = s.getAggregate();
            double sum = 0;
            int count = 0;
            Double min = null;
            Double max = null;
            for (Map<String, Object> r : rows) {
                Object v = r.get(col.getName());
                if (DatorSummary.COUNT.equals(agg)) {
                    if (v != null) {
                        count++;
                    }
                    continue;
                }
                Double d = toDouble(v);
                if (d == null) {
                    continue;
                }
                sum += d;
                count++;
                min = (min == null || d < min) ? d : min;
                max = (max == null || d > max) ? d : max;
            }
            Object value;
            switch (agg) {
                case DatorSummary.SUM:
                    value = sum;
                    break;
                case DatorSummary.AVG:
                    value = (count == 0) ? 0.0 : sum / count;
                    break;
                case DatorSummary.COUNT:
                    value = count;
                    break;
                case DatorSummary.MIN:
                    value = min;
                    break;
                case DatorSummary.MAX:
                    value = max;
                    break;
                default:
                    value = null;
            }
            result.put(s.getId(), value);
        }
        return result;
    }

    private Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void editRow() {
        new DataRecordWindow(app, table, columns, row, new TAction() {
            public void DO() {
                reload();
            }
        });
    }

    public void reload() {
        loadRow(rowid);
        rowNameLabel.setLabel(computeRowName());
        rebuildTabBar();
        rebuildContent();
    }

    private void drillDown(TTableWidget grid) {
        List<Map<String, Object>> rows = gridChildRows.get(grid);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int idx = grid.getSelectedRowNumber();
        if (idx < 0 || idx >= rows.size()) {
            return;
        }
        Object ridObj = rows.get(idx).get(DataRepository.ROWID);
        if (ridObj == null) {
            return;
        }
        DatorTable childTable = gridChildTable.get(grid);
        new DataRowView(app, childTable, ((Number) ridObj).longValue());
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        TKeypress key = event.getKey();
        if (key.equals(TKeypress.kbEsc)) {
            close();
            return;
        } else if (key.equals(TKeypress.kbF4)) {
            editRow();
            return;
        } else if (key.equals(TKeypress.kbF5)) {
            reload();
            return;
        } else if (key.equals(TKeypress.kbEnter)) {
            TWidget active = getActiveChild();
            if (active instanceof TTableWidget && gridChildRows.containsKey(active)) {
                drillDown((TTableWidget) active);
                return;
            }
        }
        super.onKeypress(event);
    }
}
