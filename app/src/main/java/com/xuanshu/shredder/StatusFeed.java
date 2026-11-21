package com.xuanshu.shredder;

final class StatusFeed {
    static final long STALE_AFTER_MS = 5_000;
    private StatusFrame last;
    private long receivedAt;
    private long readyAt;

    void open(long now) {
        close();
        readyAt = now;
    }

    void close() {
        last = null;
        receivedAt = 0;
        readyAt = 0;
    }

    void receive(byte[] bytes, long now) {
        StatusFrame frame = StatusFrame.parse(bytes);
        if (frame == null) return;
        if (last != null) {
            int forward = (frame.sequence - last.sequence) & 0xffff;
            if (forward == 0 || (forward >= 0x8000 && !isStale(now))) return;
        }
        last = frame;
        receivedAt = now;
    }

    StatusFrame current(long now) {
        return isStale(now) ? null : last;
    }

    boolean isStale(long now) {
        return now - (last == null ? readyAt : receivedAt) >= STALE_AFTER_MS;
    }
}
