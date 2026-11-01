package com.gathin.tvhome;

/** Physical left/right boundaries for a grid whose first column follows its reading direction. */
final class GridNavigation {
    private GridNavigation() { }

    /**
     * Fork: where a row's Left or Right goes at its end, so a row is a loop. forward is toward the end of the
     * reading order; at the last tile of the row it comes round to the first, and the other way round.
     * count is the number of tiles the row can reach (the dock's filled slots, or every app below it).
     */
    static int wrapTarget(int position, int count, int columns, boolean forward) {
        int start = position - position % columns;
        int end = Math.min(start + columns - 1, count - 1);
        if (forward) return position >= end ? start : position + 1;
        return position <= start ? end : position - 1;
    }

    static boolean atHorizontalEdge(int position, int count, int columns, boolean rtl, boolean left) {
        boolean towardStart = left != rtl;
        if (towardStart) return position % columns == 0;
        return position % columns == columns - 1 || position == count - 1;
    }
}
