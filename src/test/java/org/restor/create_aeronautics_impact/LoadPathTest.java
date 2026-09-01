package org.restor.create_aeronautics_impact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadPathTest {

    private static final double STRONG = 1000.0;
    private static final double WEIGHT = 1.0;

    private static LoadPath box(int sx, int sy, int sz) {
        LoadPath path = new LoadPath();
        path.reset(sx, sy, sz);
        return path;
    }

    private static void fill(LoadPath path, int x, int y, int z, boolean anchored) {
        path.set(path.index(x, y, z), WEIGHT, STRONG, anchored);
    }

    private static double at(LoadPath path, int x, int y, int z) {
        return path.load(path.index(x, y, z));
    }

    private static boolean carried(LoadPath path, int x, int y, int z) {
        return path.carried(path.index(x, y, z));
    }

    @Test
    void aColumnCarriesEverythingStackedOnIt() {
        LoadPath path = box(1, 4, 1);
        for (int y = 0; y < 4; y++) {
            fill(path, 0, y, 0, y == 0);
        }

        path.solve(4);

        assertEquals(4.0, at(path, 0, 0, 0), 1.0e-9);
        assertEquals(3.0, at(path, 0, 1, 0), 1.0e-9);
        assertEquals(2.0, at(path, 0, 2, 0), 1.0e-9);
        assertEquals(1.0, at(path, 0, 3, 0), 1.0e-9);
    }

    @Test
    void twoLegsSplitThePlatformBetweenThem() {
        LoadPath path = box(5, 3, 1);
        for (int y = 0; y < 2; y++) {
            fill(path, 0, y, 0, y == 0);
            fill(path, 4, y, 0, y == 0);
        }
        for (int x = 0; x < 5; x++) {
            fill(path, x, 2, 0, false);
        }

        path.solve(8);

        double left = at(path, 0, 0, 0);
        double right = at(path, 4, 0, 0);
        assertEquals(9.0, left + right, 1.0e-9);
        assertTrue(left >= 4.0, "left leg carries its share");
        assertTrue(right >= 4.0, "right leg carries its share");
        assertEquals(1.0, at(path, 2, 2, 0), 1.0e-9);
    }

    @Test
    void weightRestingOnTopReachesTheGround() {
        LoadPath path = box(5, 3, 1);
        for (int y = 0; y < 2; y++) {
            fill(path, 0, y, 0, y == 0);
            fill(path, 4, y, 0, y == 0);
        }
        for (int x = 0; x < 5; x++) {
            fill(path, x, 2, 0, false);
        }
        path.press(path.index(2, 2, 0), 100.0);

        path.solve(8);

        assertEquals(109.0, at(path, 0, 0, 0) + at(path, 4, 0, 0), 1.0e-9);
        assertEquals(101.0, at(path, 2, 2, 0), 1.0e-9);
    }

    @Test
    void aBlockWithNoRouteDownIsNotCarried() {
        LoadPath path = box(3, 3, 1);
        fill(path, 0, 0, 0, true);
        fill(path, 2, 2, 0, false);

        path.solve(8);

        assertTrue(carried(path, 0, 0, 0));
        assertFalse(carried(path, 2, 2, 0));
    }

    @Test
    void nothingIsCarriedWhenThereIsNoAnchor() {
        LoadPath path = box(2, 2, 1);
        fill(path, 0, 0, 0, false);
        fill(path, 0, 1, 0, false);
        fill(path, 1, 1, 0, false);

        path.solve(8);

        assertFalse(carried(path, 0, 0, 0));
        assertFalse(carried(path, 0, 1, 0));
        assertFalse(carried(path, 1, 1, 0));
    }

    @Test
    void spanCutsOffTheOverhangItCannotPayFor() {
        LoadPath path = box(6, 2, 1);
        fill(path, 0, 0, 0, true);
        for (int x = 0; x < 6; x++) {
            fill(path, x, 1, 0, false);
        }

        path.solve(2);

        assertTrue(carried(path, 1, 1, 0));
        assertTrue(carried(path, 2, 1, 0));
        assertFalse(carried(path, 3, 1, 0));
        assertFalse(carried(path, 5, 1, 0));
    }

    @Test
    void stackingUpCostsNothingSoATallColumnStandsOnAShortSpan() {
        LoadPath path = box(1, 12, 1);
        for (int y = 0; y < 12; y++) {
            fill(path, 0, y, 0, y == 0);
        }

        path.solve(0);

        assertTrue(carried(path, 0, 11, 0));
        assertEquals(12.0, at(path, 0, 0, 0), 1.0e-9);
    }

    @Test
    void takingOutTheMiddleLeavesTheFarHalfHanging() {
        LoadPath path = box(7, 2, 1);
        fill(path, 0, 0, 0, true);
        for (int x = 0; x < 7; x++) {
            fill(path, x, 1, 0, false);
        }
        path.solve(8);
        assertTrue(carried(path, 6, 1, 0));

        path.remove(path.index(3, 1, 0));
        path.solve(8);

        assertTrue(carried(path, 2, 1, 0));
        assertFalse(carried(path, 4, 1, 0));
        assertFalse(carried(path, 6, 1, 0));
    }

    @Test
    void aLegGivesWayOnlyOnceTheLoadPassesWhatItHolds() {
        LoadPath path = box(1, 3, 1);
        path.set(path.index(0, 0, 0), WEIGHT, 5.0, true);
        path.set(path.index(0, 1, 0), WEIGHT, STRONG, false);
        path.set(path.index(0, 2, 0), WEIGHT, STRONG, false);
        path.press(path.index(0, 2, 0), 1.0);

        path.solve(4);
        assertEquals(0.0, path.overload(path.index(0, 0, 0)), 1.0e-9);

        path.press(path.index(0, 2, 0), 10.0);
        path.solve(4);
        assertEquals(9.0, path.overload(path.index(0, 0, 0)), 1.0e-9);
    }

    @Test
    void everyGramPutInComesOutAtTheAnchors() {
        LoadPath path = box(4, 4, 4);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                for (int y = 0; y < 4; y++) {
                    fill(path, x, y, z, y == 0);
                }
            }
        }
        path.press(path.index(1, 3, 2), 40.0);

        path.solve(16);

        double grounded = 0.0;
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                grounded += at(path, x, 0, z);
            }
        }
        assertEquals(4 * 4 * 4 * WEIGHT + 40.0, grounded, 1.0e-9);
    }

    @Test
    void anEmptyBoxSolvesToNothing() {
        LoadPath path = box(0, 0, 0);

        path.solve(8);

        assertEquals(0, path.size());
    }

    @Test
    void resetForgetsTheStructureButKeepsTheBuffers() {
        LoadPath path = box(2, 2, 2);
        fill(path, 0, 0, 0, true);
        path.press(path.index(0, 1, 0), 50.0);

        path.reset(2, 2, 2);
        fill(path, 1, 0, 1, true);
        path.solve(4);

        assertFalse(path.solid(path.index(0, 0, 0)));
        assertEquals(WEIGHT, at(path, 1, 0, 1), 1.0e-9);
    }
}
