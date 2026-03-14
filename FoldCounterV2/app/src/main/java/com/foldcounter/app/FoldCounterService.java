package com.foldcounter.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;

public class FoldCounterService extends Service implements SensorEventListener {

    // ── 常量 ──────────────────────────────────────────────────────────────
    public static final String CHANNEL_ID      = "fold_counter_channel";
    public static final int    NOTIFICATION_ID  = 1001;

    public static final String ACTION_RESET_SESSION = "com.foldcounter.RESET_SESSION";
    public static final String ACTION_RESET_TOTAL   = "com.foldcounter.RESET_TOTAL";

    private static final String PREFS_NAME  = "FoldCounterPrefs";
    private static final String KEY_TOTAL   = "total_folds";

    // 铰链角度阈值
    private static final float FOLD_THRESHOLD = 30f;   // ≤30° 视为合上
    private static final float OPEN_THRESHOLD = 150f;  // ≥150° 视为展开

    // ── 状态 ──────────────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor        hingeSensor;

    private int     sessionCount       = 0;
    private int     totalCount         = 0;
    private boolean isCurrentlyFolded  = false;
    private boolean hingeAvailable     = false;

    private SharedPreferences prefs;
    private Vibrator          vibrator;
    private NotificationManager notificationManager;

    // 用于向 Activity 广播数据更新
    public static final String BROADCAST_UPDATE = "com.foldcounter.UPDATE";
    public static final String EXTRA_SESSION     = "session";
    public static final String EXTRA_TOTAL       = "total";
    public static final String EXTRA_FOLDED      = "folded";
    public static final String EXTRA_HINGE_AVAIL = "hinge_available";

    // ── Binder（Activity 绑定用）────────────────────────────────────────
    public class LocalBinder extends Binder {
        public FoldCounterService getService() { return FoldCounterService.this; }
    }
    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ── 生命周期 ──────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        prefs               = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        vibrator            = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        totalCount          = prefs.getInt(KEY_TOTAL, 0);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        initSensor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_RESET_SESSION.equals(action)) {
                sessionCount = 0;
                broadcastUpdate();
                updateNotification();
            } else if (ACTION_RESET_TOTAL.equals(action)) {
                sessionCount = 0;
                totalCount   = 0;
                prefs.edit().putInt(KEY_TOTAL, 0).apply();
                broadcastUpdate();
                updateNotification();
            }
        }
        // START_STICKY：服务被系统杀死后会自动重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        prefs.edit().putInt(KEY_TOTAL, totalCount).apply();
    }

    // ── 传感器初始化 ──────────────────────────────────────────────────────
    private void initSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        hingeSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);

        if (hingeSensor != null) {
            hingeAvailable = true;
            // SENSOR_DELAY_NORMAL 够用，节省电量
            sensorManager.registerListener(this, hingeSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    // ── 传感器回调 ────────────────────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_HINGE_ANGLE) return;
        float angle = event.values[0];

        if (!isCurrentlyFolded && angle <= FOLD_THRESHOLD) {
            // 合上
            isCurrentlyFolded = true;
            onFoldDetected();
        } else if (isCurrentlyFolded && angle >= OPEN_THRESHOLD) {
            // 展开
            isCurrentlyFolded = false;
            broadcastUpdate();
            updateNotification();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── 翻折事件 ──────────────────────────────────────────────────────────
    private void onFoldDetected() {
        sessionCount++;
        totalCount++;
        prefs.edit().putInt(KEY_TOTAL, totalCount).apply();

        vibrateShort();
        updateNotification();
        broadcastUpdate();
    }

    // ── 通知 ──────────────────────────────────────────────────────────────
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "折叠计数器",
                NotificationManager.IMPORTANCE_LOW  // 低优先级，不响铃
        );
        channel.setDescription("后台持续统计折叠次数");
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        // 点击通知 → 打开主界面
        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 重置本次按钮
        Intent resetSession = new Intent(this, FoldCounterService.class);
        resetSession.setAction(ACTION_RESET_SESSION);
        PendingIntent piResetSession = PendingIntent.getService(this, 1, resetSession,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String statusText = hingeAvailable
                ? (isCurrentlyFolded ? "屏幕已合上" : "屏幕已展开")
                : "未检测到铰链传感器";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("折叠计数器运行中")
                .setContentText("本次：" + sessionCount + " 次  |  累计：" + totalCount + " 次")
                .setSubText(statusText)
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_menu_revert, "重置本次", piResetSession)
                .setOngoing(true)        // 不可手动划掉
                .setSilent(true)         // 静默，不发声
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification());
    }

    // ── 广播给 Activity ───────────────────────────────────────────────────
    private void broadcastUpdate() {
        Intent intent = new Intent(BROADCAST_UPDATE);
        intent.putExtra(EXTRA_SESSION,     sessionCount);
        intent.putExtra(EXTRA_TOTAL,       totalCount);
        intent.putExtra(EXTRA_FOLDED,      isCurrentlyFolded);
        intent.putExtra(EXTRA_HINGE_AVAIL, hingeAvailable);
        sendBroadcast(intent);
    }

    // ── 供 Activity 直接读取 ──────────────────────────────────────────────
    public int  getSessionCount()      { return sessionCount; }
    public int  getTotalCount()        { return totalCount; }
    public boolean isHingeAvailable()  { return hingeAvailable; }
    public boolean isCurrentlyFolded() { return isCurrentlyFolded; }

    private void vibrateShort() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(60,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }
}
