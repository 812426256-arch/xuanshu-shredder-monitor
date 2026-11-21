package com.xuanshu.shredder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DemoCycleTest {
    @Test public void startsFromIdleCountsDownAndStaysComplete() {
        assertEquals(StatusFrame.Run.IDLE, DemoCycle.snapshot(DemoCycle.NOT_STARTED, 100).run);
        assertEquals(StatusFrame.UNKNOWN_TIME, DemoCycle.snapshot(DemoCycle.NOT_STARTED, 100).remainingSeconds);
        StatusFrame first = DemoCycle.snapshot(100, 100);
        assertEquals(StatusFrame.Run.RUNNING, first.run);
        assertEquals(15, first.remainingSeconds);
        assertEquals(14, DemoCycle.snapshot(100, 1_100).remainingSeconds);
        assertEquals(1, DemoCycle.snapshot(100, 15_099).remainingSeconds);
        assertEquals(StatusFrame.Run.COMPLETE, DemoCycle.snapshot(100, 15_100).run);
        assertEquals(0, DemoCycle.snapshot(100, 30_000).remainingSeconds);
    }
}
