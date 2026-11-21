package com.xuanshu.shredder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.HashSet;
import java.util.Set;

public final class MainActivity extends Activity implements BleMonitor.Listener {
    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH = 2;
    private static final long REFRESH_MS = 200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StatusFeed feed = new StatusFeed();
    private final Set<String> discovered = new HashSet<>();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private Dashboard dashboard;
    private BleMonitor ble;
    private BleMonitor.State bleState = BleMonitor.State.IDLE;
    private String bleDetail = "搜索附近广播 v1 服务的设备";
    private boolean bluetoothMode;
    private long demoStarted = DemoCycle.NOT_STARTED;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            bluetoothMode = saved.getBoolean("bluetoothMode");
            demoStarted = saved.getLong("demoStarted", DemoCycle.NOT_STARTED);
        }
        ble = new BleMonitor(this, this);
        dashboard = new Dashboard(this);
        dashboard.demoTab.setOnClickListener(view -> switchMode(false));
        dashboard.bluetoothTab.setOnClickListener(view -> switchMode(true));
        dashboard.action.setOnClickListener(view -> onAction());
        setContentView(dashboard);
        dashboard.showMode(bluetoothMode);
        render();
    }

    @Override protected void onStart() {
        super.onStart();
        handler.post(refresh);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        if (bleState == BleMonitor.State.SCANNING || bleState == BleMonitor.State.CONNECTING
                || bleState == BleMonitor.State.READY || !discovered.isEmpty()) {
            clearDevices();
            ble.close("已离开监测页面，可重新扫描");
        }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("bluetoothMode", bluetoothMode);
        out.putLong("demoStarted", demoStarted);
        super.onSaveInstanceState(out);
    }

    private void switchMode(boolean targetBluetooth) {
        if (targetBluetooth == bluetoothMode) return;
        bluetoothMode = targetBluetooth;
        clearDevices();
        if (targetBluetooth) onState(BleMonitor.State.IDLE, "搜索附近广播 v1 服务的设备");
        else ble.close("已切换到演示模式");
        dashboard.showMode(bluetoothMode);
        render();
    }

    private void onAction() {
        if (!bluetoothMode) {
            demoStarted = SystemClock.elapsedRealtime();
            render();
            return;
        }
        switch (bleState) {
            case SCANNING:
                clearDevices();
                ble.close("已停止扫描，可重新扫描");
                break;
            case CONNECTING:
            case READY:
                ble.close("已主动断开，可重新扫描");
                break;
            default:
                requestScan();
        }
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };
        }
        return new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
    }

    @SuppressLint("MissingPermission")
    private void requestScan() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || ble.adapter() == null) {
            onState(BleMonitor.State.DISCONNECTED, "此设备不支持 BLE，仍可使用演示模式");
            return;
        }
        String[] required = requiredPermissions();
        for (String permission : required) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(required, REQUEST_PERMISSIONS);
                return;
            }
        }
        try {
            if (!ble.adapter().isEnabled()) {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
                return;
            }
            if (Build.VERSION.SDK_INT < 31 && !locationEnabled()) {
                onState(BleMonitor.State.DISCONNECTED, "请在系统设置中打开位置服务后重新扫描");
                return;
            }
            clearDevices();
            ble.startScan();
        } catch (ActivityNotFoundException | SecurityException error) {
            onState(BleMonitor.State.DISCONNECTED, "请在系统设置中检查蓝牙开关和权限");
        }
    }

    private boolean locationEnabled() {
        LocationManager location = getSystemService(LocationManager.class);
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return location.isLocationEnabled();
        return location.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != REQUEST_PERMISSIONS || !bluetoothMode) return;
        for (String permission : requiredPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                onState(BleMonitor.State.DISCONNECTED, "权限未授予，可在系统应用设置中开启后重试");
                return;
            }
        }
        requestScan();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_ENABLE_BLUETOOTH || !bluetoothMode) return;
        if (result == RESULT_OK) requestScan();
        else onState(BleMonitor.State.DISCONNECTED, "蓝牙未开启，可开启后重新扫描");
    }

    @Override public void onState(BleMonitor.State state, String detail) {
        bleState = state;
        bleDetail = detail;
        if (state == BleMonitor.State.READY) feed.open(SystemClock.elapsedRealtime());
        else feed.close();
        if (state == BleMonitor.State.DISCONNECTED) clearDevices();
        render();
    }

    @SuppressLint("MissingPermission")
    @Override public void onDevice(BluetoothDevice device, String name) {
        if (!bluetoothMode || !discovered.add(device.getAddress())) return;
        dashboard.addDevice(name + "  ·  " + device.getAddress(), () -> {
            clearDevices();
            ble.connect(device, name);
        });
    }

    @Override public void onFrame(byte[] frame) {
        if (bluetoothMode && bleState == BleMonitor.State.READY) {
            feed.receive(frame, SystemClock.elapsedRealtime());
            render();
        }
    }

    private void clearDevices() {
        discovered.clear();
        dashboard.clearDevices();
    }

    private void render() {
        long now = SystemClock.elapsedRealtime();
        if (!bluetoothMode) {
            StatusFrame frame = DemoCycle.snapshot(demoStarted, now);
            float demoProgress = frame.run == StatusFrame.Run.RUNNING
                    ? (float) frame.remainingSeconds / DemoCycle.DURATION_SECONDS : -1;
            dashboard.showStatus(frame, "模拟连接", false, demoProgress);
            String label;
            switch (frame.run) {
                case RUNNING: label = "从头演示"; break;
                case COMPLETE: label = "再演示一次"; break;
                default: label = "开始演示";
            }
            dashboard.showAction(label, "15 秒模拟周期");
            return;
        }
        StatusFrame frame = bleState == BleMonitor.State.READY ? feed.current(now) : null;
        String link;
        String action;
        String detail = bleDetail;
        boolean attention = bleState == BleMonitor.State.DISCONNECTED;
        switch (bleState) {
            case SCANNING:
                link = "扫描中";
                action = "停止扫描";
                break;
            case CONNECTING:
                link = "连接中";
                action = "取消连接";
                break;
            case READY:
                action = "断开蓝牙";
                if (feed.isStale(now)) {
                    link = "数据未更新";
                    detail = "已连接，至少 5 秒未收到有效的新状态";
                    attention = true;
                } else {
                    link = "已连接";
                    if (frame == null) detail = "已连接，等待设备上报状态";
                }
                break;
            case DISCONNECTED:
                link = "已断开";
                action = "扫描设备";
                break;
            default:
                link = "未连接";
                action = "扫描设备";
        }
        dashboard.showStatus(frame, link, attention, -1);
        dashboard.showAction(action, detail);
    }
}
