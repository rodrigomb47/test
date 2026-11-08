package com.factionclash.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {
    @Test
    void trendOverTheLastDayAndDaysLeft() {
        Ledger l = new Ledger();
        assertTrue(Double.isNaN(l.perDay()));
        // food falls 120 a day for two days: only the last day counts
        for (int i = 0; i <= 40; i++) {
            l.record(i * 1200L, 600 - i * 6);
        }
        assertEquals(-120.0, l.perDay(), 1e-6);
        assertEquals(360, l.latest());
        assertEquals(3.0, l.daysLeft(), 1e-6);
    }

    @Test
    void risingStockNeverRunsOutAndRingKeepsOrder() {
        Ledger l = new Ledger();
        for (int i = 0; i < 50; i++) {
            l.record(i * 1200L, i * 10);
        }
        assertEquals(Ledger.CAPACITY, l.size());
        assertTrue(Double.isInfinite(l.daysLeft()));
        long[] t = l.times();
        assertEquals(18 * 1200L, t[0]);
        assertEquals(49 * 1200L, t[t.length - 1]);
        Ledger copy = Ledger.of(l.times(), l.values());
        assertEquals(l.perDay(), copy.perDay(), 1e-9);
    }
}
