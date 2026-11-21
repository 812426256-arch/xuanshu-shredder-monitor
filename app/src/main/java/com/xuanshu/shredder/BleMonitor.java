package com.xuanshu.shredder;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.Collections;

@SuppressLint("MissingPermission") // MainActivity requests runtime permissions before scanning.
final class BleMonitor {
    enum State { IDLE, SCANNING, CONNECTING, READY, DISCONNECTED }

    interface Listener {
        void onState(State state, String detail);
        void onDevice(BluetoothDevice device, String name);
        void onFrame(byte[] frame);
    }

    private static final long SCAN_MS = 10_000;
    private static final long SETUP_MS = 15_000;
    private static final long POLL_MS = 1_500;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scanTimeout = this::finishScan;
    private final Runnable setupTimeout = () -> close("连接或初始化超时，请重新扫描");
    private final Runnable poll = this::readStatus;

    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private boolean deviceFound;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic statusCharacteristic;
    private BluetoothGattDescriptor subscription;
    private boolean readable;
    private boolean polling;
    private boolean ready;
    private String deviceName;

    BleMonitor(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    BluetoothAdapter adapter() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }

    void startScan() {
        stopScan();
        releaseGatt();
        deviceFound = false;
        try {
            BluetoothAdapter adapter = adapter();
            if (adapter == null || (scanner = adapter.getBluetoothLeScanner()) == null) {
                close("蓝牙不可用，请检查系统蓝牙设置");
                return;
            }
            scanCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    handler.post(() -> {
                        if (scanCallback != this) return;
                        BluetoothDevice device = result.getDevice();
                        String name = result.getScanRecord() == null
                                ? null : result.getScanRecord().getDeviceName();
                        String address = device.getAddress();
                        deviceFound = true;
                        listener.onDevice(device, name == null || name.trim().isEmpty()
                                ? "状态设备 · " + address.substring(address.length() - 5) : name);
                    });
                }

                @Override public void onScanFailed(int errorCode) {
                    handler.post(() -> {
                        if (scanCallback == this) close("扫描失败（" + errorCode + "），请重试");
                    });
                }
            };
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(StatusFrame.SERVICE_UUID)).build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
            listener.onState(State.SCANNING, "正在搜索广播 v1 服务的设备");
            handler.postDelayed(scanTimeout, SCAN_MS);
        } catch (IllegalStateException | SecurityException error) {
            close("无法扫描，请检查蓝牙和权限");
        }
    }

    private void finishScan() {
        stopScan();
        listener.onState(State.IDLE, deviceFound ? "扫描结束，点选设备连接" : "未找到广播 v1 服务的设备");
    }

    private void stopScan() {
        handler.removeCallbacks(scanTimeout);
        ScanCallback callback = scanCallback;
        scanCallback = null;
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback);
            } catch (IllegalStateException | SecurityException ignored) {
                // The adapter can turn off or permission can be revoked during scanning.
            }
        }
        scanner = null;
    }

    void connect(BluetoothDevice device, String name) {
        stopScan();
        releaseGatt();
        deviceName = name;
        listener.onState(State.CONNECTING, "正在连接 " + name);
        try {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                close("无法发起 GATT 连接，请重新扫描");
                return;
            }
            handler.postDelayed(setupTimeout, SETUP_MS);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            close("无法连接，请检查蓝牙和权限");
        }
    }

    void close(String detail) {
        stopScan();
        releaseGatt();
        listener.onState(State.DISCONNECTED, detail);
    }

    private void releaseGatt() {
        handler.removeCallbacks(setupTimeout);
        handler.removeCallbacks(poll);
        BluetoothGatt previous = gatt;
        gatt = null;
        ready = false;
        polling = false;
        readable = false;
        statusCharacteristic = null;
        subscription = null;
        if (previous != null) {
            try {
                previous.disconnect();
            } catch (SecurityException ignored) {
                // Runtime permission may have been revoked.
            }
            try {
                previous.close();
            } catch (SecurityException ignored) {
                // The GATT client is already detached from this monitor.
            }
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source, int result, int state) {
            handler.post(() -> {
                if (source != gatt) return;
                if (result != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) {
                    close("蓝牙连接已断开，可重新扫描");
                } else if (state == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        if (!source.discoverServices()) close("无法启动 GATT 服务发现");
                    } catch (SecurityException error) {
                        close("蓝牙权限不可用");
                    }
                }
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt source, int result) {
            handler.post(() -> {
                if (source != gatt) return;
                if (result == BluetoothGatt.GATT_SUCCESS) prepareStatus(source);
                else close("GATT 服务发现失败，请重新扫描");
            });
        }

        @Override public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor,
                                                int result) {
            handler.post(() -> {
                if (source != gatt || subscription == null || descriptor != subscription) return;
                subscription = null;
                if (result == BluetoothGatt.GATT_SUCCESS) beginReading(false);
                else fallbackToRead();
            });
        }

        @Override public void onCharacteristicChanged(BluetoothGatt source,
                BluetoothGattCharacteristic characteristic, byte[] value) {
            postFrame(source, characteristic, value);
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicChanged(BluetoothGatt source,
                BluetoothGattCharacteristic characteristic) {
            if (Build.VERSION.SDK_INT < 33) postFrame(source, characteristic, characteristic.getValue());
        }

        @Override public void onCharacteristicRead(BluetoothGatt source, BluetoothGattCharacteristic characteristic,
                                                   byte[] value, int result) {
            postRead(source, characteristic, value, result);
        }

        @SuppressWarnings("deprecation")
        @Override public void onCharacteristicRead(BluetoothGatt source, BluetoothGattCharacteristic characteristic,
                                                   int result) {
            if (Build.VERSION.SDK_INT < 33) postRead(source, characteristic, characteristic.getValue(), result);
        }
    };

    @SuppressWarnings("deprecation")
    private void prepareStatus(BluetoothGatt source) {
        BluetoothGattService service = source.getService(StatusFrame.SERVICE_UUID);
        statusCharacteristic = service == null ? null : service.getCharacteristic(StatusFrame.STATUS_UUID);
        if (statusCharacteristic == null) {
            close("设备缺少 v1 状态特征");
            return;
        }
        int properties = statusCharacteristic.getProperties();
        readable = (properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0;
        boolean notify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        boolean indicate = (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
        BluetoothGattDescriptor descriptor = statusCharacteristic.getDescriptor(StatusFrame.CCCD_UUID);
        try {
            if ((notify || indicate) && descriptor != null
                    && source.setCharacteristicNotification(statusCharacteristic, true)) {
                byte[] value = notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                boolean submitted;
                if (Build.VERSION.SDK_INT >= 33) {
                    submitted = source.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS;
                } else {
                    descriptor.setValue(value);
                    submitted = source.writeDescriptor(descriptor);
                }
                if (submitted) {
                    subscription = descriptor;
                    return;
                }
            }
            fallbackToRead();
        } catch (SecurityException error) {
            close("蓝牙权限不可用");
        }
    }

    private void fallbackToRead() {
        if (readable) beginReading(true);
        else close("状态特征无法订阅或读取");
    }

    private void beginReading(boolean usePolling) {
        handler.removeCallbacks(setupTimeout);
        ready = true;
        polling = usePolling;
        listener.onState(State.READY, "已连接 " + deviceName);
        if (polling) readStatus();
    }

    private void readStatus() {
        if (!polling || gatt == null) return;
        try {
            if (!gatt.readCharacteristic(statusCharacteristic)) handler.postDelayed(poll, POLL_MS);
        } catch (IllegalStateException | SecurityException error) {
            close("无法读取状态，请检查蓝牙权限或重新连接");
        }
    }

    private void postFrame(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value) {
        byte[] snapshot = value == null ? null : value.clone();
        handler.post(() -> {
            if (source == gatt && ready && !polling && characteristic == statusCharacteristic) {
                listener.onFrame(snapshot);
            }
        });
    }

    private void postRead(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value, int result) {
        byte[] snapshot = value == null ? null : value.clone();
        handler.post(() -> {
            if (source != gatt || !polling || characteristic != statusCharacteristic) return;
            if (result == BluetoothGatt.GATT_SUCCESS) listener.onFrame(snapshot);
            handler.postDelayed(poll, POLL_MS);
        });
    }
}
