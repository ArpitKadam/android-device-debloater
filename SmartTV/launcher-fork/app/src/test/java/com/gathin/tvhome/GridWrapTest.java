package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GridWrapTest {

    @Test
    public void dockRowLoopsLeftAndRight() {
        // six columns, a dock with 4 filled slots (positions 0-3): Left on 0 goes to 3, Right on 3 goes to 0
        assertEquals(3, GridNavigation.wrapTarget(0, 4, 6, false));
        assertEquals(0, GridNavigation.wrapTarget(3, 4, 6, true));
        assertEquals(1, GridNavigation.wrapTarget(0, 4, 6, true));
        assertEquals(2, GridNavigation.wrapTarget(3, 4, 6, false));
    }

    @Test
    public void lowerRowsLoopWithinTheirOwnRowAndShortLastRows() {
        // 14 apps, six columns: the third row is positions 12-13 only
        assertEquals(11, GridNavigation.wrapTarget(6, 14, 6, false));
        assertEquals(6, GridNavigation.wrapTarget(11, 14, 6, true));
        assertEquals(13, GridNavigation.wrapTarget(12, 14, 6, false));
        assertEquals(12, GridNavigation.wrapTarget(13, 14, 6, true));
    }

    @Test
    public void aRowOfOneStaysPut() {
        assertEquals(0, GridNavigation.wrapTarget(0, 1, 6, true));
        assertEquals(0, GridNavigation.wrapTarget(0, 1, 6, false));
    }
}
