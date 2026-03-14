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
    private View ringPulse, btnReset, btnResetTotal;

    private FoldCounterService boundService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            boundService = ((FoldCounterService.LocalBinder) service).getService();
            isBound = true;
            refreshFromService(false);
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { isBound = false; }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int session   = intent.getIntExtra(FoldCounterService.EXTRA_SESSION, 0);
            int total     = intent.getIntExtra(FoldCounterService.EXTRA_TOTAL, 0);
            boolean folded    = intent.getBooleanExtra(FoldCounterService.EXTRA_FOLDED, false);
            boolean available = intent.getBooleanExtra(FoldCounterService.EXTRA_HINGE_AVAIL, false);
            boolean isNewFold = false;
            try { isNewFold = session > Integer.parseInt(tvCount.getText().toString()); }
            catch (NumberFormatException ignored) {}
            updateUI(session, total, folded, available, isNewFold);
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
        btnReset        = findViewById(R.id.btn_reset);
        btnResetTotal   = findViewById(R.id.btn_reset_total);

        setupButtons();

        Intent serviceIntent = new Intent(this, FoldCounterService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(FoldCounterService.BROADCAST_UPDATE);
        registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        if (isBound) refreshFromService(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) { unbindService(serviceConnection); isBound = false; }
        // 不调用 stopService — 服务继续后台运行
    }

    private void setupButtons() {
        btnReset.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("重置本次计数")
                .setMessage("确定要将本次会话的翻折次数清零吗？")
                .setPositiveButton("确定", (d, w) -> {
                    Intent i = new Intent(this, FoldCounterService.class);
                    i.setAction(FoldCounterService.ACTION_RESET_SESSION);
                    startService(i);
                })
                .setNegativeButton("取消", null).show()
        );

        btnResetTotal.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("重置累计计数")
                .setMessage("确定要清除所有历史翻折记录吗？此操作不可恢复。")
                .setPositiveButton("确定", (d, w) -> {
                    Intent i = new Intent(this, FoldCounterService.class);
                    i.setAction(FoldCounterService.ACTION_RESET_TOTAL);
                    startService(i);
                })
                .setNegativeButton("取消", null).show()
        );

        findViewById(R.id.btn_stop).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("停止后台服务")
                .setMessage("停止后将不再统计翻折次数，确定吗？")
                .setPositiveButton("停止", (d, w) -> {
                    stopService(new Intent(this, FoldCounterService.class));
                    finish();
                })
                .setNegativeButton("取消", null).show()
        );
    }

    private void refreshFromService(boolean animate) {
        if (!isBound || boundService == null) return;
        updateUI(boundService.getSessionCount(), boundService.getTotalCount(),
                 boundService.isCurrentlyFolded(), boundService.isHingeAvailable(), animate);
    }

    private void updateUI(int session, int total, boolean folded,
                          boolean hingeAvailable, boolean animate) {
        tvCount.setText(String.valueOf(session));
        tvSession.setText("本次会话：" + session + " 次");
        tvTotal.setText("累计翻折：" + total + " 次");
        tvStatus.setText(hingeAvailable
                ? (folded ? "📕 屏幕已合上" : "📖 屏幕已展开")
                : "⚠️ 未检测到铰链传感器");
        tvServiceStatus.setText(hingeAvailable ? "🟢 后台服务运行中" : "请在折叠屏设备上运行");
        if (animate) { animateCountBounce(); pulseRing(); }
    }

    private void animateCountBounce() {
        ObjectAnimator sx = ObjectAnimator.ofFloat(tvCount, "scaleX", 1f, 1.4f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(tvCount, "scaleY", 1f, 1.4f, 1f);
        sx.setDuration(420); sy.setDuration(420);
        sx.setInterpolator(new OvershootInterpolator(3f));
        sy.setInterpolator(new OvershootInterpolator(3f));
        sx.start(); sy.start();
    }

    private void pulseRing() {
        ringPulse.setAlpha(0.8f); ringPulse.setScaleX(0.6f); ringPulse.setScaleY(0.6f);
        ringPulse.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(ringPulse, "scaleX", 0.6f, 2.0f).setDuration(600);
        ObjectAnimator sx    = ObjectAnimator.ofFloat(ringPulse, "scaleX", 0.6f, 2.0f);
        ObjectAnimator sy    = ObjectAnimator.ofFloat(ringPulse, "scaleY", 0.6f, 2.0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ringPulse, "alpha", 0.8f, 0f);
        sx.setDuration(600); sy.setDuration(600); alpha.setDuration(600);
        sx.start(); sy.start(); alpha.start();
    }
}
