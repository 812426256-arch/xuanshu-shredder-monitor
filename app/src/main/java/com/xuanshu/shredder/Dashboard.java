package com.xuanshu.shredder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class Dashboard extends ScrollView {
    private static final int BACKGROUND = 0xff0b141b;
    private static final int PANEL = 0xff14212c;
    private static final int LINE = 0xff2a3d49;
    private static final int TEXT = 0xffedf4f5;
    private static final int MUTED = 0xffacbec5;
    private static final int MINT = 0xff91ebc8;
    private static final int MINT_DARK = 0xff17392f;
    private static final int AMBER = 0xffffd19a;
    private static final int AMBER_DARK = 0xff3b3025;

    final Button demoTab;
    final Button bluetoothTab;
    final Button action;
    private final TextView source;
    private final TextView time;
    private final Dial dial;
    private final TextView straw;
    private final TextView gear;
    private final TextView run;
    private final TextView connection;
    private final TextView detail;
    private final LinearLayout devices;

    Dashboard(Context context) {
        super(context);
        setBackgroundColor(BACKGROUND);
        setFillViewport(true);
        setClipToPadding(false);
        setVerticalScrollBarEnabled(false);
        LinearLayout body = column();
        body.setPadding(dp(20), dp(14), dp(20), dp(24));
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.ic_launcher);
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout title = column();
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        titleSize.leftMargin = dp(12);
        header.addView(title, titleSize);
        title.addView(text("玄枢聚能", 23, TEXT, true));
        title.addView(text("粉碎机状态监测", 13, MUTED, false));
        append(body, header, 0);

        LinearLayout modes = row();
        modes.setPadding(dp(4), dp(4), dp(4), dp(4));
        modes.setBackground(shape(PANEL, LINE, 16));
        demoTab = button("演示模式");
        bluetoothTab = button("蓝牙模式");
        addWeighted(modes, demoTab, 0);
        addWeighted(modes, bluetoothTab, 4);
        append(body, modes, 18);

        LinearLayout timerPanel = column();
        timerPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        timerPanel.setPadding(dp(10), dp(14), dp(10), dp(10));
        timerPanel.setBackground(shape(PANEL, LINE, 22));
        source = text("", 13, MINT, true);
        source.setPadding(dp(11), dp(4), dp(11), dp(4));
        timerPanel.addView(source, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        FrameLayout clock = new FrameLayout(context);
        dial = new Dial(context);
        clock.addView(dial, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LinearLayout readout = column();
        readout.setGravity(Gravity.CENTER);
        TextView caption = text("剩余时间", 15, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        readout.addView(caption);
        time = text("--:--", 53, TEXT, true);
        time.setGravity(Gravity.CENTER);
        time.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        time.setSingleLine(true);
        readout.addView(time);
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        clock.addView(readout, center);
        LinearLayout.LayoutParams clockSize = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(182));
        clockSize.topMargin = dp(3);
        timerPanel.addView(clock, clockSize);
        append(body, timerPanel, 14);

        append(body, text("作业状态", 17, TEXT, true), 17);
        LinearLayout first = row();
        straw = addMetric(first, "秸秆类型", 0);
        gear = addMetric(first, "工作档位", 9);
        append(body, first, 8);
        LinearLayout second = row();
        run = addMetric(second, "运行状态", 0);
        connection = addMetric(second, "连接状态", 9);
        append(body, second, 9);

        action = button("");
        action.setMinHeight(dp(50));
        styleButton(action, MINT, BACKGROUND, 15);
        append(body, action, 16);
        detail = text("", 13, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        append(body, detail, 8);
        devices = column();
        append(body, devices, 2);
    }

    void showMode(boolean bluetooth) {
        demoTab.setSelected(!bluetooth);
        bluetoothTab.setSelected(bluetooth);
        styleButton(demoTab, bluetooth ? PANEL : MINT_DARK, bluetooth ? MUTED : MINT, 12);
        styleButton(bluetoothTab, bluetooth ? MINT_DARK : PANEL, bluetooth ? MINT : MUTED, 12);
        source.setText(bluetooth ? "蓝牙待实机联调" : "演示数据");
        source.setTextColor(bluetooth ? AMBER : MINT);
        source.setBackground(shape(bluetooth ? AMBER_DARK : MINT_DARK, Color.TRANSPARENT, 10));
        devices.setVisibility(bluetooth ? VISIBLE : GONE);
    }

    void showStatus(StatusFrame frame, String link, boolean attention, float demoProgress) {
        String countdown = StatusFrame.formatTime(frame == null ? StatusFrame.UNKNOWN_TIME : frame.remainingSeconds);
        if (!countdown.contentEquals(time.getText())) {
            time.setTextSize(countdown.length() > 5 ? 36 : 53);
            time.setText(countdown);
        }
        dial.update(demoProgress, frame != null && frame.run == StatusFrame.Run.COMPLETE);
        setText(straw, frame == null ? "—" : frame.straw.label);
        setText(gear, frame == null ? "—" : frame.gear.label);
        setText(run, frame == null ? "—" : frame.run.label);
        run.setTextColor(frame == null ? TEXT : frame.run == StatusFrame.Run.FAULT ? AMBER : MINT);
        setText(connection, link);
        connection.setTextColor(attention ? AMBER : TEXT);
    }

    void showAction(String label, String hint) {
        setText(action, label);
        setText(detail, hint);
    }

    void clearDevices() {
        devices.removeAllViews();
    }

    void addDevice(String label, Runnable connect) {
        Button device = button(label);
        device.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        device.setPadding(dp(14), 0, dp(14), 0);
        device.setMaxLines(2);
        device.setContentDescription("连接 " + label);
        styleButton(device, PANEL, TEXT, 12);
        device.setOnClickListener(view -> connect.run());
        append(devices, device, 8);
    }

    private TextView addMetric(LinearLayout row, String label, int margin) {
        LinearLayout metric = column();
        metric.setPadding(dp(13), dp(8), dp(13), dp(8));
        metric.setMinimumHeight(dp(64));
        metric.setGravity(Gravity.CENTER_VERTICAL);
        metric.setBackground(shape(PANEL, LINE, 14));
        metric.addView(text(label, 12, MUTED, false));
        TextView value = text("—", 18, TEXT, true);
        LinearLayout.LayoutParams valueSize = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        valueSize.topMargin = dp(2);
        metric.addView(value, valueSize);
        addWeighted(row, metric, margin);
        return value;
    }

    private void append(LinearLayout column, View view, int margin) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        size.topMargin = dp(margin);
        column.addView(view, size);
    }

    private void addWeighted(LinearLayout row, View view, int margin) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        size.leftMargin = dp(margin);
        row.addView(view, size);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String label, float size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button view = new Button(getContext());
        view.setAllCaps(false);
        view.setText(label);
        view.setTextSize(15);
        view.setMinHeight(dp(48));
        view.setMinWidth(0);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setStateListAnimator(null);
        return view;
    }

    private void styleButton(Button button, int fill, int text, int corner) {
        button.setTextColor(text);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3391ebc8),
                shape(fill, Color.TRANSPARENT, corner), null));
    }

    private GradientDrawable shape(int fill, int border, int corner) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(corner));
        if (border != Color.TRANSPARENT) drawable.setStroke(dp(1), border);
        return drawable;
    }

    private static void setText(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private final class Dial extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private float progress = -1;
        private boolean complete;

        Dial(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void update(float fraction, boolean finished) {
            if (progress == fraction && complete == finished) return;
            progress = fraction;
            complete = finished;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(6);
            if (radius <= 0) return;
            oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setColor(LINE);
            canvas.drawArc(oval, 133, 274, false, paint);
            if (progress > 0 || complete) {
                paint.setColor(MINT);
                canvas.drawArc(oval, 133, 274 * (complete ? 1 : progress), false, paint);
            }
        }
    }
}
