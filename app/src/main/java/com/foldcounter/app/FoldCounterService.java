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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FoldCounterService extends Service implements SensorEventListener {

    public static final String CHANNEL_ID           = "fold_counter";
    public static final int    NOTIF_ID             = 1001;
    public static final String ACTION_RESET_SESSION = "com.foldcounter.RESET_SESSION";
    public static final String ACTION_RESET_ALL     = "com.foldcounter.RESET_ALL";
    public static final String BROADCAST_UPDATE     = "com.foldcounter.UPDATE";
    public static final String EXTRA_SESSION        = "session";
    public static final String EXTRA_TOTAL          = "total";
    public static final String EXTRA_TODAY          = "today";
    public static final String EXTRA_FOLDED         = "folded";
    public static final String EXTRA_HINGE_AVAIL    = "hinge_available";

    private static final String PREFS            = "FoldCounterPrefs";
    private static final String KEY_TOTAL        = "total_folds";
    private static final String KEY_TODAY        = "today_folds";
    private static final String KEY_TODAY_DATE   = "today_date";
    // 每天历史 key = "day_YYYY-MM-DD"

    private static final float FOLD_THRESHOLD = 30f;
    private static final float OPEN_THRESHOLD = 150f;

    private SensorManager sensorManager;
    private Sensor        hingeSensor;

    // 累计总数（主页大数字，永不自动归零）
    private int     totalCount        = 0;
    // 本次会话（APP 启动后重置）
    private int     sessionCount      = 0;
    // 今日计数（每天单独统计，跨天存档但不清零 totalCount）
    private int     todayCount        = 0;
    private boolean isCurrentlyFolded = false;
    private boolean hingeAvailable    = false;

    private SharedPreferences prefs;
    private Vibrator          vibrator;
    private NotificationManager notifManager;

    public class LocalBinder extends Binder {
        public FoldCounterService getService() { return FoldCounterService.this; }
    }
    private final IBinder binder = new LocalBinder();
    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs        = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        vibrator     = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 读取持久化数据
        totalCount = prefs.getInt(KEY_TOTAL, 0);
        loadTodayCount();

        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        initSensor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_RESET_SESSION.equals(intent.getAction())) {
                // 只重置本次会话计数
                sessionCount = 0;
                broadcastUpdate(); updateNotif();
            } else if (ACTION_RESET_ALL.equals(intent.getAction())) {
                // 手动清空全部：总计、今日、本次、所有历史
                totalCount = 0; sessionCount = 0; todayCount = 0;
                SharedPreferences.Editor editor = prefs.edit();
                // 清除所有 day_* 历史
                for (String key : prefs.getAll().keySet()) {
                    if (key.startsWith("day_")) editor.remove(key);
                }
                editor.putInt(KEY_TOTAL, 0)
                      .putInt(KEY_TODAY, 0)
                      .putString(KEY_TODAY_DATE, todayStr())
                      .apply();
                broadcastUpdate(); updateNotif();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        persist();
    }

    // 加载今日计数，并在跨天时把昨天数据存档（不改 totalCount）
    private void loadTodayCount() {
        String today = todayStr();
        String saved = prefs.getString(KEY_TODAY_DATE, "");
        if (today.equals(saved)) {
            todayCount = prefs.getInt(KEY_TODAY, 0);
        } else {
            // 新的一天：把昨天今日计数写进历史
            if (!saved.isEmpty()) {
                int prev = prefs.getInt(KEY_TODAY, 0);
                prefs.edit().putInt("day_" + saved, prev).apply();
            }
            // 今日重新从 0 开始（totalCount 不动）
            todayCount = 0;
            prefs.edit()
                .putString(KEY_TODAY_DATE, today)
                .putInt(KEY_TODAY, 0)
                .apply();
        }
    }

    private void initSensor() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        hingeSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
        if (hingeSensor != null) {
            hingeAvailable = true;
            sensorManager.registerListener(this, hingeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_HINGE_ANGLE) return;
        float angle = event.values[0];

        if (!isCurrentlyFolded && angle <= FOLD_THRESHOLD) {
            // 合上：计数
            isCurrentlyFolded = true;
            loadTodayCount();   // 检测跨天
            totalCount++;
            sessionCount++;
            todayCount++;
            persist();
            vibrate();
            updateNotif();
            broadcastUpdate();

        } else if (isCurrentlyFolded && angle >= OPEN_THRESHOLD) {
            // 展开
            isCurrentlyFolded = false;
            updateNotif();
            broadcastUpdate();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void persist() {
        prefs.edit()
            .putInt(KEY_TOTAL, totalCount)
            .putInt(KEY_TODAY, todayCount)
            .putString(KEY_TODAY_DATE, todayStr())
            .putInt("day_" + todayStr(), todayCount)  // 今天也实时存档
            .apply();
    }

    private String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    // ── 通知 ──────────────────────────────────────────────────────────────
    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "折叠计数器",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        notifManager.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String state = isCurrentlyFolded ? "已合上" : "已展开";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("折叠计数器  " + state)
                .setContentText("总计：" + totalCount + " 次  今日：" + todayCount + " 次  本次：" + sessionCount + " 次")
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentIntent(pi)
                .setOngoing(true).setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotif() { notifManager.notify(NOTIF_ID, buildNotification()); }

    private void broadcastUpdate() {
        Intent i = new Intent(BROADCAST_UPDATE);
        i.putExtra(EXTRA_SESSION,     sessionCount);
        i.putExtra(EXTRA_TOTAL,       totalCount);
        i.putExtra(EXTRA_TODAY,       todayCount);
        i.putExtra(EXTRA_FOLDED,      isCurrentlyFolded);
        i.putExtra(EXTRA_HINGE_AVAIL, hingeAvailable);
        sendBroadcast(i);
    }

    // ── 供 Activity 读取 ──────────────────────────────────────────────────
    public int  getSessionCount()      { return sessionCount; }
    public int  getTotalCount()        { return totalCount; }
    public int  getTodayCount()        { return todayCount; }
    public boolean isHingeAvailable()  { return hingeAvailable; }
    public boolean isCurrentlyFolded() { return isCurrentlyFolded; }

    // 近 N 天历史（index 0 = 最早那天）
    public int[] getHistory(int days) {
        int[] result = new int[days];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        long DAY = 86400000L;
        long now  = System.currentTimeMillis();
        for (int i = 0; i < days; i++) {
            String d = sdf.format(new Date(now - (long)(days - 1 - i) * DAY));
            result[i] = prefs.getInt("day_" + d, 0);
        }
        return result;
    }

    public String[] getHistoryLabels(int days) {
        String[] labels = new String[days];
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        long DAY = 86400000L;
        long now  = System.currentTimeMillis();
        for (int i = 0; i < days; i++) {
            labels[i] = sdf.format(new Date(now - (long)(days - 1 - i) * DAY));
        }
        return labels;
    }
}
