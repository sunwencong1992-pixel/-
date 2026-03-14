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
    public static final String EXTRA_ANGLE          = "angle";
    public static final String EXTRA_IS_FOLD_EVENT  = "is_fold_event";
    public static final String EXTRA_HINGE_AVAIL    = "hinge_available";

    private static final String PREFS          = "FoldCounterPrefs";
    private static final String KEY_TOTAL      = "total_folds";
    private static final String KEY_TODAY      = "today_folds";
    private static final String KEY_TODAY_DATE = "today_date";
    private static final float  FOLD_THRESHOLD = 30f;
    private static final float  OPEN_THRESHOLD = 150f;

    // 角度广播节流：最快 200ms 一次
    private static final long ANGLE_THROTTLE_MS = 200;
    // 里程碑：每隔多少次触发特殊广播
    private static final int  MILESTONE_INTERVAL = 100;
    public  static final String BROADCAST_MILESTONE = "com.foldcounter.MILESTONE";
    public  static final String EXTRA_MILESTONE_COUNT = "milestone_count";

    private SensorManager sensorManager;
    private Sensor        hingeSensor;
    private int     totalCount        = 0;
    private int     sessionCount      = 0;
    private int     todayCount        = 0;
    private float   currentAngle      = -1f;
    private boolean isCurrentlyFolded = false;
    private boolean hingeAvailable    = false;
    private long    lastAngleBroadcast = 0;

    private SharedPreferences prefs;
    private Vibrator          vibrator;
    private NotificationManager notifManager;

    public class LocalBinder extends Binder {
        public FoldCounterService getService() { return FoldCounterService.this; }
    }
    private final IBinder binder = new LocalBinder();
    @Override public IBinder onBind(Intent i) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs        = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        vibrator     = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        totalCount   = prefs.getInt(KEY_TOTAL, 0);
        loadToday();
        createChannel();
        startForeground(NOTIF_ID, buildNotif());
        initSensor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_RESET_SESSION.equals(intent.getAction())) {
                sessionCount = 0;
                broadcastUpdate(false);
                updateNotif();
            } else if (ACTION_RESET_ALL.equals(intent.getAction())) {
                totalCount = 0; sessionCount = 0; todayCount = 0;
                SharedPreferences.Editor e = prefs.edit();
                for (String k : prefs.getAll().keySet())
                    if (k.startsWith("day_")) e.remove(k);
                e.putInt(KEY_TOTAL, 0).putInt(KEY_TODAY, 0)
                 .putString(KEY_TODAY_DATE, todayStr()).apply();
                broadcastUpdate(false);
                updateNotif();
            }
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        persist();
    }

    private void loadToday() {
        String today = todayStr();
        String saved = prefs.getString(KEY_TODAY_DATE, "");
        if (today.equals(saved)) {
            todayCount = prefs.getInt(KEY_TODAY, 0);
        } else {
            if (!saved.isEmpty())
                prefs.edit().putInt("day_" + saved, prefs.getInt(KEY_TODAY, 0)).apply();
            todayCount = 0;
            prefs.edit().putString(KEY_TODAY_DATE, today).putInt(KEY_TODAY, 0).apply();
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
        currentAngle = event.values[0];

        if (!isCurrentlyFolded && currentAngle <= FOLD_THRESHOLD) {
            // 合上：计数 + 立即广播
            isCurrentlyFolded = true;
            loadToday();
            totalCount++; sessionCount++; todayCount++;
            persist();
            vibrate();
            updateNotif();
            broadcastUpdate(true);   // isFoldEvent = true，触发动画
            // 里程碑检测
            if (totalCount > 0 && totalCount % MILESTONE_INTERVAL == 0) {
                broadcastMilestone(totalCount);
            }

        } else if (isCurrentlyFolded && currentAngle >= OPEN_THRESHOLD) {
            // 展开：立即广播
            isCurrentlyFolded = false;
            updateNotif();
            broadcastUpdate(false);

        } else {
            // 角度变化：节流广播，只更新角度显示
            long now = System.currentTimeMillis();
            if (now - lastAngleBroadcast >= ANGLE_THROTTLE_MS) {
                lastAngleBroadcast = now;
                broadcastUpdate(false);
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void persist() {
        prefs.edit()
            .putInt(KEY_TOTAL, totalCount)
            .putInt(KEY_TODAY, todayCount)
            .putString(KEY_TODAY_DATE, todayStr())
            .putInt("day_" + todayStr(), todayCount)
            .apply();
    }

    private String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private void vibrate() {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(48, 180));
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "折叠计数器", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        notifManager.createNotificationChannel(ch);
    }

    private Notification buildNotif() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String state = isCurrentlyFolded ? "已合上" : "已展开";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("折叠计数器  " + state)
                .setContentText("总计 " + totalCount + "  ·  今日 " + todayCount + "  ·  本次 " + sessionCount)
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentIntent(pi)
                .setOngoing(true).setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotif() { notifManager.notify(NOTIF_ID, buildNotif()); }

    private void broadcastUpdate(boolean isFoldEvent) {
        Intent i = new Intent(BROADCAST_UPDATE);
        i.putExtra(EXTRA_SESSION,       sessionCount);
        i.putExtra(EXTRA_TOTAL,         totalCount);
        i.putExtra(EXTRA_TODAY,         todayCount);
        i.putExtra(EXTRA_FOLDED,        isCurrentlyFolded);
        i.putExtra(EXTRA_ANGLE,         currentAngle);
        i.putExtra(EXTRA_IS_FOLD_EVENT, isFoldEvent);
        i.putExtra(EXTRA_HINGE_AVAIL,   hingeAvailable);
        sendBroadcast(i);
    }

    private void broadcastMilestone(int count) {
        Intent i = new Intent(BROADCAST_MILESTONE);
        i.putExtra(EXTRA_MILESTONE_COUNT, count);
        sendBroadcast(i);
    }

    public int  getSessionCount()       { return sessionCount; }
    public int  getTotalCount()         { return totalCount; }
    public int  getTodayCount()         { return todayCount; }
    public float getCurrentAngle()      { return currentAngle; }
    public boolean isHingeAvailable()   { return hingeAvailable; }
    public boolean isCurrentlyFolded()  { return isCurrentlyFolded; }

    public int[] getHistory(int days) {
        int[] r = new int[days];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        long DAY = 86400000L, now = System.currentTimeMillis();
        for (int i = 0; i < days; i++)
            r[i] = prefs.getInt("day_" + sdf.format(new Date(now - (long)(days-1-i)*DAY)), 0);
        return r;
    }

    public String[] getHistoryLabels(int days) {
        String[] l = new String[days];
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
        long DAY = 86400000L, now = System.currentTimeMillis();
        for (int i = 0; i < days; i++)
            l[i] = sdf.format(new Date(now - (long)(days-1-i)*DAY));
        return l;
    }
}
