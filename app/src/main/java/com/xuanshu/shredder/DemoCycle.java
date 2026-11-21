package com.xuanshu.shredder;

final class DemoCycle {
    static final int DURATION_SECONDS = 15;
    static final long NOT_STARTED = -1;

    private DemoCycle() { }

    static StatusFrame snapshot(long startedAt, long now) {
        if (startedAt == NOT_STARTED) {
            return frame(StatusFrame.Run.IDLE, StatusFrame.UNKNOWN_TIME);
        }
        long elapsedSeconds = Math.max(0, now - startedAt) / 1_000;
        long remaining = Math.max(0, DURATION_SECONDS - elapsedSeconds);
        return frame(remaining == 0 ? StatusFrame.Run.COMPLETE : StatusFrame.Run.RUNNING, remaining);
    }

    private static StatusFrame frame(StatusFrame.Run run, long remaining) {
        return new StatusFrame(StatusFrame.Straw.CORN, StatusFrame.Gear.MEDIUM, run, remaining, 0);
    }
}
