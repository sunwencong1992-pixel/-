package com.foldcounter.app;

import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView tvCount, tvSession, tvTotal, tvStatus, tvServiceStatus;
    private View ringPulse;

    private FoldCounterService boundService;
    private boolean isBound = false;
    private int lastDisplayedCount = 0;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            boundService = ((FoldCounterService.LocalBinder) b).getService();
            isBound = true;
            refresh(false);
        }
        @Override public void onServiceDisconnected(ComponentName n) { isBound = false; }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            int  session  = i.getIntExtra(FoldCounterService.EXTRA_SESSION, 0);
            int  total    = i.getIntExtra(FoldCounterService.EXTRA_TOTAL, 0);
            boolean fold  = i.getBooleanExtra(FoldCounterService.EXTRA_FOLDED, false);
            boolean avail = i.getBooleanExtra(FoldCounterService.EXTRA_HINGE_AVAIL, false);
            boolean isNew = session > lastDisplayedCount;
            updateUI(session, total, fold, avail, isNew);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCount         = findViewById(R.id.tv_count);
        tvSession       = findViewById(R.id.tv_session);
        tvTotal         = findViewById(R.id.tv_total);
        tvStatus        = findViewById(R.id.tv_status);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        ringPulse       = findViewById(R.id.ring_pulse);

        Intent svc = new Intent(this, FoldCounterService.class);
        ContextCompat.startForegroundService(this, svc);
        bindService(svc, conn, Context.BIND_AUTO_CREATE);

        findViewById(R.id.btn_reset).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Reset Session")
                .setMessage("Reset this session's fold count?")
                .setPositiveButton("Reset", (d, w) -> {
                    Intent r = new Intent(this, FoldCounterService.class);
                    r.setAction(FoldCounterService.ACTION_RESET_SESSION);
                    startService(r);
                }).setNegativeButton("Cancel", null).show());

        findViewById(R.id.btn_reset_total).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Clear Total")
                .setMessage("Clear all-time fold history? Cannot be undone.")
                .setPositiveButton("Clear", (d, w) -> {
                    Intent r = new Intent(this, FoldCounterService.class);
                    r.setAction(FoldCounterService.ACTION_RESET_TOTAL);
                    startService(r);
                }).setNegativeButton("Cancel", null).show());

        findViewById(R.id.btn_stop).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Stop Service")
                .setMessage("Stop background counting?")
                .setPositiveButton("Stop", (d, w) -> {
                    stopService(new Intent(this, FoldCounterService.class));
                    finish();
                }).setNegativeButton("Cancel", null).show());
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(FoldCounterService.BROADCAST_UPDATE),
                Context.RECEIVER_NOT_EXPORTED);
        if (isBound) refresh(false);
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isBound) { unbindService(conn); isBound = false; }
    }

    private void refresh(boolean animate) {
        if (!isBound || boundService == null) return;
        updateUI(boundService.getSessionCount(), boundService.getTotalCount(),
                 boundService.isCurrentlyFolded(), boundService.isHingeAvailable(), animate);
    }

    private void updateUI(int session, int total, boolean folded,
                          boolean avail, boolean animate) {
        lastDisplayedCount = session;
        tvCount.setText(String.valueOf(session));
        tvSession.setText("Session: " + session);
        tvTotal.setText("Total: " + total);
        tvStatus.setText(avail ? (folded ? "Closed" : "Open") : "No hinge sensor detected");
        tvServiceStatus.setText(avail ? "Running in background" : "Run on a foldable device");
        if (animate) { bounce(); pulse(); }
    }

    private void bounce() {
        ObjectAnimator sx = ObjectAnimator.ofFloat(tvCount, "scaleX", 1f, 1.4f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(tvCount, "scaleY", 1f, 1.4f, 1f);
        sx.setDuration(400); sy.setDuration(400);
        sx.setInterpolator(new OvershootInterpolator(3f));
        sy.setInterpolator(new OvershootInterpolator(3f));
        sx.start(); sy.start();
    }

    private void pulse() {
        ringPulse.setAlpha(0.8f); ringPulse.setScaleX(0.6f); ringPulse.setScaleY(0.6f);
        ringPulse.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(ringPulse, "scaleX", 0.6f, 2f).setDuration(600);
        ObjectAnimator sx    = ObjectAnimator.ofFloat(ringPulse, "scaleX", 0.6f, 2f);
        ObjectAnimator sy    = ObjectAnimator.ofFloat(ringPulse, "scaleY", 0.6f, 2f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ringPulse, "alpha", 0.8f, 0f);
        sx.setDuration(600); sy.setDuration(600); alpha.setDuration(600);
        sx.start(); sy.start(); alpha.start();
    }
}
