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

    public static final String CHANNEL_ID       = "fold_counter";
    public static final int    NOTIF_ID         = 1001;
    public static final String ACTION_RESET_SESSION = "com.foldcounter.RESET_SESSION";
    public static final String ACTION_RESET_TOTAL   = "com.foldcounter.RESET_TOTAL";
    public static final String BROADCAST_UPDATE     = "com.foldcounter.UPDATE";
    public static final String EXTRA_SESSION        = "session";
    public static final String EXTRA_TOTAL          = "total";
    public static final String EXTRA_FOLDED         = "folded";
    public static final String EXTRA_HINGE_AVAIL    = "hinge_available";

    private static final String PREFS  = "FoldCounterPrefs";
    private static final String KEY_TOTAL = "total_folds";
    private static final float  FOLD_THRESHOLD = 30f;
    private static final float  OPEN_THRESHOLD = 150f;

    private SensorManager sensorManager;
    private Sensor        hingeSensor;
    private int     sessionCount      = 0;
    private int     totalCount        = 0;
    private boolean isCurrentlyFolded = false;
    private boolean hingeAvailable    = false;
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private NotificationManager notifManager;

    public class LocalBinder extends Binder {
        public FoldCounterService getService() { return FoldCounterService.this; }
    }
    private final IBinder binder = new LocalBinder();

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs       = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        vibrator    = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        totalCount  = prefs.getInt(KEY_TOTAL, 0);
        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        initSensor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_RESET_SESSION.equals(intent.getAction())) {
                sessionCount = 0; broadcastUpdate(); updateNotif();
            } else if (ACTION_RESET_TOTAL.equals(intent.getAction())) {
                sessionCount = 0; totalCount = 0;
                prefs.edit().putInt(KEY_TOTAL, 0).apply();
                broadcastUpdate(); updateNotif();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        prefs.edit().putInt(KEY_TOTAL, totalCount).apply();
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
            isCurrentlyFolded = true;
            sessionCount++; totalCount++;
            prefs.edit().putInt(KEY_TOTAL, totalCount).apply();
            if (vibrator != null && vibrator.hasVibrator())
                vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
            updateNotif(); broadcastUpdate();
        } else if (isCurrentlyFolded && angle >= OPEN_THRESHOLD) {
            isCurrentlyFolded = false;
            updateNotif(); broadcastUpdate();
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "FoldCounter",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        notifManager.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent resetSession = new Intent(this, FoldCounterService.class);
        resetSession.setAction(ACTION_RESET_SESSION);
        PendingIntent piReset = PendingIntent.getService(this, 1, resetSession,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FoldCounter is running")
                .setContentText("Session: " + sessionCount + "  |  Total: " + totalCount)
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_menu_revert, "Reset Session", piReset)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotif() { notifManager.notify(NOTIF_ID, buildNotification()); }

    private void broadcastUpdate() {
        Intent i = new Intent(BROADCAST_UPDATE);
        i.putExtra(EXTRA_SESSION, sessionCount);
        i.putExtra(EXTRA_TOTAL, totalCount);
        i.putExtra(EXTRA_FOLDED, isCurrentlyFolded);
        i.putExtra(EXTRA_HINGE_AVAIL, hingeAvailable);
        sendBroadcast(i);
    }

    public int getSessionCount()      { return sessionCount; }
    public int getTotalCount()        { return totalCount; }
    public boolean isHingeAvailable() { return hingeAvailable; }
    public boolean isCurrentlyFolded(){ return isCurrentlyFolded; }
}
