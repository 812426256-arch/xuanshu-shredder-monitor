package com.xuanshu.shredder;

import org.junit.Test;

import static com.xuanshu.shredder.StatusFrameTest.message;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StatusFeedTest {
    @Test public void becomesStaleEvenWithoutFirstFrame() {
        StatusFeed feed = new StatusFeed();
        feed.open(500);
        assertNull(feed.current(500));
        assertFalse(feed.isStale(5_499));
        assertTrue(feed.isStale(5_500));
    }

    @Test public void invalidDuplicateAndOutOfOrderFramesDoNotExtendFreshness() {
        StatusFeed feed = new StatusFeed();
        feed.open(0);
        feed.receive(message(3, 2, 1, 75, 1234), 500);
        feed.receive(message(3, 2, 1, 10, 1234), 3_000);
        feed.receive(message(3, 2, 1, 9, 1233), 4_000);
        feed.receive(new byte[10], 4_500);
        assertEquals(75, feed.current(5_499).remainingSeconds);
        assertNull(feed.current(5_500));
        assertTrue(feed.isStale(5_500));
        feed.receive(message(3, 2, 1, 8, 1234), 6_000);
        assertNull(feed.current(6_000));
    }

    @Test public void wrappedSequenceAndNewFramesRecoverFreshness() {
        StatusFeed feed = new StatusFeed();
        feed.open(0);
        feed.receive(message(3, 2, 1, 5, 0xffff), 100);
        feed.receive(message(3, 2, 1, 4, 0), 1_100);
        assertEquals(4, feed.current(1_100).remainingSeconds);
        assertNull(feed.current(6_100));
        feed.receive(message(3, 2, 2, 0, 1), 8_000);
        assertFalse(feed.isStale(8_000));
        assertEquals(StatusFrame.Run.COMPLETE, feed.current(8_000).run);
    }

    @Test public void staleFeedCanRecoverAfterDeviceSequenceReset() {
        StatusFeed feed = new StatusFeed();
        feed.open(0);
        feed.receive(message(3, 2, 1, 50, 50), 100);
        feed.receive(message(3, 2, 1, 11, 1), 1_000);
        assertEquals(50, feed.current(1_000).remainingSeconds);
        feed.receive(message(3, 2, 1, 6, 2), 6_000);
        assertEquals(6, feed.current(6_000).remainingSeconds);
    }

    @Test public void disconnectAndReconnectDiscardPreviousData() {
        StatusFeed feed = new StatusFeed();
        feed.open(0);
        feed.receive(message(3, 2, 1, 75, 20), 100);
        assertNotNull(feed.current(100));
        feed.close();
        assertNull(feed.current(200));
        feed.open(250);
        assertNull(feed.current(250));
        feed.receive(message(3, 2, 1, 20, 20), 300);
        assertEquals(20, feed.current(300).remainingSeconds);
    }
}
