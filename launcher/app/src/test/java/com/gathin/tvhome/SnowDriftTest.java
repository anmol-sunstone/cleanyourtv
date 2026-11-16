package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class SnowDriftTest {

    private static SnowDrift ledge() {
        SnowDrift d = new SnowDrift();
        d.resize(800f);
        return d;
    }

    @Test
    public void aLandedFlakeAddsItsOwnAreaPackedDown() {
        SnowDrift d = ledge();
        float before = d.lying();
        d.land(400f, 2f);
        assertEquals(SnowDrift.PACKING * Math.PI * 4.0, d.lying() - before, 0.05);
    }

    @Test
    public void snowOffTheLedgeDoesNotLand() {
        SnowDrift d = ledge();
        float before = d.lying();
        d.land(-3f, 2f);
        d.land(d.width() + 1f, 2f);
        assertEquals(before, d.lying(), 0.0001);
    }

    /** A long snowfall with the plough coming whenever it is called, which is how the ledge is used. */
    @Test
    public void noSlopeIsSteeperThanTheAngleOfRepose() {
        SnowDrift d = ledge();
        Random rnd = new Random(7);
        float step = SnowDrift.REPOSE * SnowDrift.BIN + 0.05f;
        int passes = 0;
        for (int flake = 0; flake < 6000; flake++) {
            d.land(rnd.nextFloat() * d.width(), 1.3f + rnd.nextFloat() * 1.9f);
            for (int i = 0; i < d.bins() - 1; i++) {
                assertTrue("flake " + flake + ", columns " + i + " and " + (i + 1),
                        Math.abs(d.column(i) - d.column(i + 1)) <= step);
            }
            assertTrue("flake " + flake + ", left end", d.column(0) <= step);
            assertTrue("flake " + flake + ", right end", d.column(d.bins() - 1) <= step);
            if (d.due()) {
                passes++;
                d.startPlough(-80f, d.width() + 140f);
                while (d.ploughing()) d.advance(0.033f);
            }
        }
        assertTrue("the plough came " + passes + " times", passes >= 3);
    }

    @Test
    public void snowInFrontOfTheBladeDoesNotSlumpBackOverClearedGround() {
        SnowDrift d = ledge();
        Random rnd = new Random(9);
        while (!d.due()) d.land(rnd.nextFloat() * d.width(), 2.4f);
        d.startPlough(-80f, d.width() + 140f);
        while (d.blade() < 400f) d.advance(0.033f);
        for (int i = 0; i < 40; i++) d.land(410f + rnd.nextFloat() * 300f, 2.4f);
        for (int i = 0; i < d.bins(); i++) {
            if ((i + 0.5f) * SnowDrift.BIN < d.blade() - SnowDrift.BIN) {
                assertTrue("column " + i + " behind the blade", d.column(i) <= SnowDrift.RESIDUE + 0.0001f);
            }
        }
        assertTrue(d.depth(500f) > 5f);
    }

    /** A little over two flakes a second reach the dock with the skin's 46 flakes; the plough follows the pile. */
    @Test
    public void thePloughIsCalledAfterAFewMinutesOfTheSkinsSnowfall() {
        SnowDrift d = ledge();
        assertFalse(d.due());
        Random rnd = new Random(3);
        float seconds = 0f;
        while (!d.due() && seconds < 3600f) {
            seconds += 0.44f;
            // SnowfallView's sizes: every flake over the dock lands
            d.land(rnd.nextFloat() * d.width(), 1.3f + rnd.nextFloat() * rnd.nextFloat() * 3.6f);
        }
        assertTrue("called after " + seconds + " s", seconds > 150f && seconds < 480f);
    }

    @Test
    public void heavierSnowCallsThePloughSooner() {
        assertTrue(secondsUntilDue(0.25f) < secondsUntilDue(0.53f));
    }

    private static float secondsUntilDue(float gap) {
        SnowDrift d = ledge();
        Random rnd = new Random(3);
        float seconds = 0f;
        while (!d.due() && seconds < 7200f) {
            seconds += gap;
            d.land(rnd.nextFloat() * d.width(), 2f);
        }
        return seconds;
    }

    @Test
    public void aPassScrapesEveryColumnAndThrowsExactlyWhatItScraped() {
        SnowDrift d = ledge();
        Random rnd = new Random(11);
        while (!d.due()) d.land(rnd.nextFloat() * d.width(), 2.4f);
        float before = d.lying();
        d.startPlough(-80f, d.width() + 140f);
        assertTrue(d.ploughing());
        assertFalse(d.due());
        float thrown = 0f, tallest = 0f;
        int steps = 0;
        while (d.ploughing() && steps++ < 10000) {
            thrown += d.advance(0.033f);
            tallest = Math.max(tallest, d.heapHeight());
        }
        assertFalse(d.ploughing());
        for (int i = 0; i < d.bins(); i++) assertTrue(d.column(i) <= SnowDrift.RESIDUE + 0.0001f);
        // nothing is lost: what was lying is still lying, was thrown, or was thrown onto the tree
        assertEquals(before, d.lying() + thrown + d.treeLoad() * SnowDrift.TREE_HOLDS, before * 0.001f);
        assertEquals(SnowDrift.TREE_DUSTING, d.treeLoad(), 0.001f);
        assertEquals(0f, d.heap(), 0.0001f);
        assertTrue("snow piled up against the blade", tallest > 4f);
        assertTrue(tallest <= SnowDrift.BLADE_HEIGHT);
    }

    @Test
    public void thePloughWorksFromTheRightEndToo() {
        SnowDrift d = ledge();
        Random rnd = new Random(5);
        while (!d.due()) d.land(rnd.nextFloat() * d.width(), 2.4f);
        d.startPlough(d.width() + 80f, -140f);
        assertEquals(-1, d.direction());
        d.advance(2f);
        assertTrue(d.blade() < d.width());
        assertTrue(d.column(d.bins() - 1) <= SnowDrift.RESIDUE + 0.0001f);
        assertTrue("the far end is still covered", d.column(0) > 0f && d.depth(200f) > SnowDrift.RESIDUE);
    }

    @Test
    public void theTreeStandsNearTheRightEndAndFlakesMeetItsOutline() {
        SnowDrift d = ledge();
        assertTrue(d.hasTree());
        assertEquals(d.width() - SnowDrift.TREE_INSET, d.treeX(), 0.001f);
        assertEquals(SnowDrift.TREE_H, d.treeTop(d.treeX()), 0.001f);
        assertEquals(SnowDrift.TREE_H / 2f, d.treeTop(d.treeX() - SnowDrift.TREE_W / 4f), 0.01f);
        assertEquals(0f, d.treeTop(d.treeX() - SnowDrift.TREE_W / 2f - 1f), 0.0001f);
        SnowDrift narrow = new SnowDrift();
        narrow.resize(200f);
        assertFalse(narrow.hasTree());
        assertEquals(0f, narrow.treeTop(150f), 0.0001f);
    }

    @Test
    public void snowOnTheTreeSlidesOffOntoTheDriftWhenTheBranchesAreFull() {
        SnowDrift d = ledge();
        float lying = d.lying(), foot = d.depth(d.treeX() - 12f);
        int flakes = 0;
        while (!d.takeShed() && flakes < 1000) {
            assertEquals("snow on the tree is not snow on the ledge", lying, d.lying(), 0.0001f);
            d.landOnTree(2f);
            flakes++;
        }
        // 420 dp² of room, 0.78 x pi x 4 dp² a flake
        assertEquals(43, flakes);
        assertEquals(SnowDrift.TREE_KEEPS, d.treeLoad(), 0.001f);
        assertTrue(d.depth(d.treeX() - 12f) > foot + 1f);
        assertTrue(d.lying() > lying + 0.7f * SnowDrift.TREE_HOLDS);
        assertFalse(d.takeShed());
    }

    @Test
    public void aNarrowerLedgeKeepsItsSnow() {
        SnowDrift d = ledge();
        Random rnd = new Random(2);
        for (int i = 0; i < 400; i++) d.land(rnd.nextFloat() * d.width(), 2.4f);
        float mean = d.mean();
        d.resize(600f);
        assertEquals(mean, d.mean(), mean * 0.1f);
    }

    /** The screensaver's ground: 960 dp of screen at 1.8 times the dock's scale, with its 70 flakes. */
    @Test
    public void aWalledLedgeLiesLevelToItsEndsAndFillsAtTheDocksPace() {
        SnowDrift d = new SnowDrift();
        d.walled(60f);
        d.resize(960f / 1.8f);
        assertTrue(d.column(0) > 1f && d.column(d.bins() - 1) > 1f);
        Random rnd = new Random(4);
        float seconds = 0f;
        while (!d.due() && seconds < 3600f) {
            // 70 flakes, each about 17 s from top to bottom, all of them landing
            seconds += 17f / 70f;
            float size = 1.3f + (float) Math.pow(rnd.nextFloat() * rnd.nextFloat(), 1.5) * 4.8f;
            float x = rnd.nextFloat() * d.width();
            if (d.treeTop(x) > 0f) d.landOnTree(size / 1.8f);
            else d.land(x, size / 1.8f);
        }
        assertTrue("called after " + seconds + " s", seconds > 200f && seconds < 330f);
        float lying = d.lying();
        d.startPlough(-12f, d.width() + 92f);
        float crossing = 0f;
        while (d.ploughing()) {
            d.advance(0.033f);
            crossing += 0.033f;
        }
        assertTrue("crossed in " + crossing + " s", crossing > 9f && crossing < 12f);
        assertTrue(d.lying() < lying * 0.2f);
    }
}
