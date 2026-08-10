package se.spacify.dator.ui;

import java.util.List;
import java.util.Map;

import jexer.TTableWidget;
import jexer.TWidget;

import se.spacify.dator.model.DatorColumn;
import se.spacify.dator.model.DatorSummary;

/** Small rendering helpers shared by the data-grid windows. */
final class GridUtil {

    private GridUtil() {
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
