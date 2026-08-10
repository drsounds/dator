package se.spacify.dator.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jexer.TTableWidget;
import jexer.TWidget;

import se.spacify.dator.db.DataRepository;
import se.spacify.dator.db.MetaRepository;
import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorRelation;
import se.spacify.dator.model.DatorSummary;
import se.spacify.dator.model.DatorTable;

/** Small rendering helpers shared by the data-grid windows. */
final class GridUtil {

    private GridUtil() {
    }

    /** A resolved belongs-to reference: what to show, and where Enter should navigate. */
    static class FkInfo {
        final String displayText;
        final DatorTable targetTable;
        final long targetRowid;

        FkInfo(String displayText, DatorTable targetTable, long targetRowid) {
            this.displayText = displayText;
            this.targetTable = targetTable;
            this.targetRowid = targetRowid;
        }
    }

    /** First TEXT column other than the referenced value column - used as a human-readable label. */
    static DatorColumn pickDisplayColumn(List<DatorColumn> refColumns, DatorColumn refValueColumn) {
        for (DatorColumn c : refColumns) {
            if ("TEXT".equalsIgnoreCase(c.getDataType())
                    && (refValueColumn == null || c.getId() != refValueColumn.getId())) {
                return c;
            }
        }
        return refValueColumn;
    }

    /**
     * For every belongs-to (FK) column on {@code table}, builds a lookup from
     * that column's raw stored value to a human-readable label plus the
     * referenced row's rowid (for navigation), keyed by column name.
     */
    static Map<String, Map<Object, FkInfo>> buildFkIndex(DatorApplication app, DatorTable table,
            List<DatorColumn> columns) {
        Map<String, Map<Object, FkInfo>> result = new HashMap<>();
        try {
            for (DatorRelation rel : app.getMetaRepository().listRelations(table.getId())) {
                DatorColumn fkColumn = findColumnById(columns, rel.getColumnId());
                if (fkColumn == null) {
                    continue;
                }
                DatorTable refTable = app.getMetaRepository().getTable(rel.getRefTableId());
                if (refTable == null) {
                    continue;
                }
                List<DatorColumn> refColumns = app.getMetaRepository().listColumns(refTable.getId());
                DatorColumn refValueColumn = rel.getRefColumnId() == null
                        ? null : findColumnById(refColumns, rel.getRefColumnId());
                DatorColumn displayColumn = pickDisplayColumn(refColumns, refValueColumn);

                Map<Object, FkInfo> byValue = new HashMap<>();
                for (Map<String, Object> row : app.getDataRepository().listRows(refTable, refColumns)) {
                    Object rowidObj = row.get(DataRepository.ROWID);
                    if (rowidObj == null) {
                        continue;
                    }
                    long targetRowid = ((Number) rowidObj).longValue();
                    Object value = refValueColumn != null ? row.get(refValueColumn.getName()) : rowidObj;
                    Object display = displayColumn != null ? row.get(displayColumn.getName()) : null;
                    String text = (display == null) ? String.valueOf(value) : value + ": " + display;
                    if (value != null) {
                        byValue.put(value, new FkInfo(text, refTable, targetRowid));
                    }
                }
                result.put(fkColumn.getName(), byValue);
            }
        } catch (Exception e) {
            // Best effort: fall back to raw values if relation lookups fail.
        }
        return result;
    }

    private static DatorColumn findColumnById(List<DatorColumn> columns, int id) {
        for (DatorColumn c : columns) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    /** Formats a column's value for display, resolving belongs-to columns to a readable label. */
    static String formatCell(Map<String, Map<Object, FkInfo>> fkIndex, DatorColumn column, Object rawValue) {
        Map<Object, FkInfo> byValue = fkIndex.get(column.getName());
        if (byValue != null && rawValue != null) {
            FkInfo info = byValue.get(rawValue);
            if (info != null) {
                return info.displayText;
            }
        }
        return formatValue(rawValue);
    }

    /**
     * Columns to actually show in a grid/list: the standard bookkeeping
     * columns (slug, uuid, number, created, updated, deleted, parent_id) and
     * LONGTEXT columns are hidden by default to declutter list views. They
     * remain fully present and usable everywhere else (forms, detail views,
     * repository calls) - this filter only affects grid rendering.
     */
    static List<DatorColumn> visibleColumns(List<DatorColumn> columns) {
        List<DatorColumn> visible = new ArrayList<>();
        for (DatorColumn c : columns) {
            boolean hiddenStandard = MetaRepository.HIDDEN_LIST_COLUMNS
                    .contains(c.getName().toLowerCase(java.util.Locale.ROOT));
            boolean longText = "LONGTEXT".equalsIgnoreCase(c.getDataType());
            if (!hiddenStandard && !longText) {
                visible.add(c);
            }
        }
        return visible;
    }

    static boolean hasSummaryAt(List<DatorSummary> summaries, String position) {
        for (DatorSummary s : summaries) {
            if (s.showsAt(position)) {
                return true;
            }
        }
        return false;
    }

    static String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[]) {
            return "<blob:" + ((byte[]) value).length + "b>";
        }
        return value.toString();
    }

    /**
     * Builds a single-row, read-only grid aligned with {@code columns} that
     * shows each summary's aggregate value under its target column, for
     * summaries active at the given position ("top" or "bottom"). Returns
     * null (and creates nothing) if no summary applies at that position.
     */
    static TTableWidget buildSummaryRow(TWidget parent, int x, int y, int width, List<DatorColumn> columns,
            List<DatorSummary> summaries, Map<Integer, Object> values, String position) {
        if (columns.isEmpty()) {
            return null;
        }

        String[] cellText = new String[columns.size()];
        boolean any = false;
        for (DatorSummary s : summaries) {
            if (!s.showsAt(position)) {
                continue;
            }
            int colIndex = -1;
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).getId() == s.getColumnId()) {
                    colIndex = i;
                    break;
                }
            }
            if (colIndex < 0) {
                continue;
            }
            Object value = values.get(s.getId());
            String label = (s.getLabel() != null && !s.getLabel().isEmpty()) ? s.getLabel() : s.getAggregate();
            String text = label + ": " + formatValue(value);
            cellText[colIndex] = (cellText[colIndex] == null) ? text : cellText[colIndex] + " / " + text;
            any = true;
        }
        if (!any) {
            return null;
        }

        TTableWidget grid = new TTableWidget(parent, x, y, width, 3, columns.size(), 1);
        grid.setShowColumnLabels(false);
        for (int i = 0; i < columns.size(); i++) {
            int colWidth = Math.max(10, Math.min(24, columns.get(i).getDisplayLabel().length() + 2));
            grid.setColumnWidth(i, colWidth);
            grid.setCellText(i, 0, cellText[i] == null ? "" : cellText[i]);
            grid.setColumnReadOnly(i, true);
        }
        // Display-only: keep it out of Tab order entirely rather than
        // making it a dead end you have to tab past uselessly.
        grid.setEnabled(false);
        return grid;
    }
}
