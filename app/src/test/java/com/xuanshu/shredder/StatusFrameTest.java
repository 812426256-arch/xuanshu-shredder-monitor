package com.xuanshu.shredder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class StatusFrameTest {
    @Test public void parsesRunningFrameAndLittleEndianValues() {
        StatusFrame frame = StatusFrame.parse(message(3, 2, 1, 75, 0x1234));
        assertNotNull(frame);
        assertEquals(StatusFrame.Straw.CORN, frame.straw);
        assertEquals(StatusFrame.Gear.MEDIUM, frame.gear);
        assertEquals(StatusFrame.Run.RUNNING, frame.run);
        assertEquals(75, frame.remainingSeconds);
        assertEquals(0x1234, frame.sequence);
        assertEquals("01:15", StatusFrame.formatTime(frame.remainingSeconds));
    }

    @Test public void distinguishesUnknownTimeFromZero() {
        StatusFrame unknown = StatusFrame.parse(message(0, 0, 4, StatusFrame.UNKNOWN_TIME, 5));
        StatusFrame complete = StatusFrame.parse(message(2, 1, 2, 0, 6));
        assertNotNull(unknown);
        assertNotNull(complete);
        assertEquals(StatusFrame.Straw.UNKNOWN, unknown.straw);
        assertEquals(StatusFrame.Gear.UNKNOWN, unknown.gear);
        assertEquals(StatusFrame.Run.UNKNOWN, unknown.run);
        assertEquals("--:--", StatusFrame.formatTime(unknown.remainingSeconds));
        assertEquals(StatusFrame.Run.COMPLETE, complete.run);
        assertEquals("00:00", StatusFrame.formatTime(complete.remainingSeconds));
    }

    @Test public void acceptsMaximumAndRejectsOutOfRangeUnsignedTime() {
        StatusFrame largest = StatusFrame.parse(message(1, 3, 1, StatusFrame.MAX_SECONDS, 0xffff));
        assertNotNull(largest);
        assertEquals("5999:59", StatusFrame.formatTime(largest.remainingSeconds));
        assertEquals(0xffff, largest.sequence);
        assertNull(StatusFrame.parse(message(1, 3, 1, StatusFrame.MAX_SECONDS + 1, 0)));
        assertNull(StatusFrame.parse(message(1, 3, 1, 0x8000_0000L, 0)));
    }

    @Test public void rejectsInvalidLengthVersionAndEnumeration() {
        assertNull(StatusFrame.parse(null));
        assertNull(StatusFrame.parse(new byte[9]));
        assertNull(StatusFrame.parse(new byte[11]));
        byte[] versionTwo = message(3, 2, 1, 75, 0);
        versionTwo[0] = 2;
        assertNull(StatusFrame.parse(versionTwo));
        assertNull(StatusFrame.parse(message(4, 2, 1, 75, 0)));
        assertNull(StatusFrame.parse(message(3, 4, 1, 75, 0)));
        assertNull(StatusFrame.parse(message(3, 2, 5, 75, 0)));
    }

    static byte[] message(int straw, int gear, int state, long seconds, int sequence) {
        return new byte[] { 1, (byte) straw, (byte) gear, (byte) state,
                (byte) seconds, (byte) (seconds >> 8), (byte) (seconds >> 16), (byte) (seconds >> 24),
                (byte) sequence, (byte) (sequence >> 8) };
    }
}
