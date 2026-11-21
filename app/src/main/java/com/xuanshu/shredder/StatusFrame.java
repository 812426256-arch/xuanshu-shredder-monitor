package com.xuanshu.shredder;

import java.util.Locale;
import java.util.UUID;

final class StatusFrame {
    static final UUID SERVICE_UUID = UUID.fromString("a5836ead-504e-4d53-a845-856260567e5a");
    static final UUID STATUS_UUID = UUID.fromString("a5836ead-504e-4d53-a845-856260567e5b");
    static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    static final long UNKNOWN_TIME = 0xffff_ffffL;
    static final long MAX_SECONDS = 359_999;

    enum Straw {
        UNKNOWN("未提供"), WHEAT("小麦秸秆"), RICE("水稻秸秆"), CORN("玉米秸秆");
        final String label;
        Straw(String label) { this.label = label; }
    }

    enum Gear {
        UNKNOWN("未提供"), LOW("低档"), MEDIUM("中档"), HIGH("高档");
        final String label;
        Gear(String label) { this.label = label; }
    }

    enum Run {
        IDLE("待机"), RUNNING("运行中"), COMPLETE("已完成"), FAULT("故障"), UNKNOWN("未提供");
        final String label;
        Run(String label) { this.label = label; }
    }

    final Straw straw;
    final Gear gear;
    final Run run;
    final long remainingSeconds;
    final int sequence;

    StatusFrame(Straw straw, Gear gear, Run run, long remainingSeconds, int sequence) {
        this.straw = straw;
        this.gear = gear;
        this.run = run;
        this.remainingSeconds = remainingSeconds;
        this.sequence = sequence;
    }

    static StatusFrame parse(byte[] frame) {
        if (frame == null || frame.length != 10 || unsigned(frame[0]) != 1) return null;
        Straw[] straws = Straw.values();
        Gear[] gears = Gear.values();
        Run[] states = Run.values();
        int straw = unsigned(frame[1]);
        int gear = unsigned(frame[2]);
        int run = unsigned(frame[3]);
        if (straw >= straws.length || gear >= gears.length || run >= states.length) return null;

        long seconds = 0;
        for (int i = 0; i < 4; i++) {
            seconds |= (long) unsigned(frame[4 + i]) << (8 * i);
        }
        if (seconds != UNKNOWN_TIME && seconds > MAX_SECONDS) return null;

        int sequence = unsigned(frame[8]) | (unsigned(frame[9]) << 8);
        return new StatusFrame(straws[straw], gears[gear], states[run], seconds, sequence);
    }

    static String formatTime(long seconds) {
        if (seconds == UNKNOWN_TIME) return "--:--";
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }
}
