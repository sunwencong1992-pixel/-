package com.foldcounter.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView tvCount, tvSession, tvTotal, tvToday, tvStatus, tvServiceStatus;
    private View pulse1, pulse2;
    private LinearLayout chartContainer, chartLabels;

    private FoldCounterService boundService;
    private boolean isBound   = false;
    private int     lastCount = 0;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            boundService = ((FoldCounterService.LocalBinder) b).getService();
            isBound = true;
            refresh(false);
            buildChart();
        }
        @Override public void onServiceDisconnected(ComponentName n) { isBound = false; }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            int  session  = i.getIntExtra(FoldCounterService.EXTRA_SESSION, 0);
            int  total    = i.getIntExtra(FoldCounterService.EXTRA_TOTAL, 0);
            int  today    = i.getIntExtra(FoldCounterService.EXTRA_TODAY, 0);
            boolean fold  = i.getBooleanExtra(FoldCounterService.EXTRA_FOLDED, false);
            boolean avail = i.getBooleanExtra(FoldCounterService.EXTRA_HINGE_AVAIL, false);
            boolean isNew = total > lastCount;
            updateUI(session, total, today, fold, avail, isNew);
            if (isNew) buildChart();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCount         = findViewById(R.id.tv_count);
        tvSession       = findViewById(R.id.tv_session);
        tvTotal         = findViewById(R.id.tv_total);
        tvToday         = findViewById(R.id.tv_today);
        tvStatus        = findViewById(R.id.tv_status);
        tvServiceStatus = findViewById(R.id.tv_service_status);
        pulse1          = findViewById(R.id.pulse1);
        pulse2          = findViewById(R.id.pulse2);
        chartContainer  = findViewById(R.id.chart_container);
        chartLabels     = findViewById(R.id.chart_labels);

        Intent svc = new Intent(this, FoldCounterService.class);
        ContextCompat.startForegroundService(this, svc);
        bindService(svc, conn, Context.BIND_AUTO_CREATE);

        // 重置本次会话
        findViewById(R.id.btn_reset).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("重置本次计数")
                .setMessage("将本次会话的折叠次数清零（不影响总计和今日数据）")
                .setPositiveButton("确定", (d, w) -> {
                    Intent r = new Intent(this, FoldCounterService.class);
                    r.setAction(FoldCounterService.ACTION_RESET_SESSION);
                    startService(r);
                }).setNegativeButton("取消", null).show());

        // 手动清空全部
        findViewById(R.id.btn_reset_total).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("清空所有数据")
                .setMessage("将清空总计、今日、本次及全部历史记录，此操作不可恢复，确定吗？")
                .setPositiveButton("清空", (d, w) -> {
                    Intent r = new Intent(this, FoldCounterService.class);
                    r.setAction(FoldCounterService.ACTION_RESET_ALL);
                    startService(r);
                }).setNegativeButton("取消", null).show());

        // 停止服务
        findViewById(R.id.btn_stop).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("停止后台服务")
                .setMessage("停止后将不再统计折叠次数，已有数据不会丢失，确定吗？")
                .setPositiveButton("停止", (d, w) -> {
                    stopService(new Intent(this, FoldCounterService.class));
                    finish();
                }).setNegativeButton("取消", null).show());
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(receiver,
                new IntentFilter(FoldCounterService.BROADCAST_UPDATE),
                Context.RECEIVER_NOT_EXPORTED);
        if (isBound) { refresh(false); buildChart(); }
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
                 boundService.getTodayCount(), boundService.isCurrentlyFolded(),
                 boundService.isHingeAvailable(), animate);
    }

    private void updateUI(int session, int total, int today,
                          boolean folded, boolean avail, boolean animate) {
        lastCount = total;
        // 主数字 = 累计总数
        tvCount.setText(String.valueOf(total));
        tvSession.setText(String.valueOf(session));
        tvTotal.setText(String.valueOf(total));
        tvToday.setText(String.valueOf(today));

        if (!avail) {
            tvStatus.setText("未检测到铰链传感器，请在折叠屏上运行");
            tvServiceStatus.setText("● 不支持");
            tvServiceStatus.setTextColor(Color.parseColor("#F87171"));
        } else {
            tvStatus.setText(folded ? "📕  屏幕已合上" : "📖  屏幕已展开");
            tvServiceStatus.setText("● 运行中");
            tvServiceStatus.setTextColor(Color.parseColor("#A8E063"));
        }
        if (animate) { animateBounce(); animatePulse(); }
    }

    private void animateBounce() {
        ObjectAnimator sx = ObjectAnimator.ofFloat(tvCount, "scaleX", 1f, 1.45f, 0.95f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(tvCount, "scaleY", 1f, 1.45f, 0.95f, 1f);
        ObjectAnimator col = ObjectAnimator.ofArgb(tvCount, "textColor",
                Color.parseColor("#A8E063"), Color.WHITE, Color.parseColor("#A8E063"));
        sx.setDuration(500); sy.setDuration(500); col.setDuration(500);
        sx.setInterpolator(new OvershootInterpolator(2.5f));
        sy.setInterpolator(new OvershootInterpolator(2.5f));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, col);
        set.start();
    }

    private void animatePulse() { firePulse(pulse1, 0); firePulse(pulse2, 160); }

    private void firePulse(View v, long delay) {
        v.setScaleX(0.7f); v.setScaleY(0.7f); v.setAlpha(0.9f);
        ObjectAnimator sx    = ObjectAnimator.ofFloat(v, "scaleX", 0.7f, 2.2f);
        ObjectAnimator sy    = ObjectAnimator.ofFloat(v, "scaleY", 0.7f, 2.2f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, "alpha", 0.9f, 0f);
        sx.setDuration(700); sy.setDuration(700); alpha.setDuration(700);
        sx.setInterpolator(new DecelerateInterpolator());
        sy.setInterpolator(new DecelerateInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, alpha);
        set.setStartDelay(delay);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { v.setAlpha(0f); }
        });
        set.start();
    }

    private void buildChart() {
        if (!isBound || boundService == null) return;
        int[]    data   = boundService.getHistory(7);
        String[] labels = boundService.getHistoryLabels(7);

        int max = 1;
        for (int v : data) if (v > max) max = v;

        chartContainer.removeAllViews();
        chartLabels.removeAllViews();

        String todayLabel = new java.text.SimpleDateFormat("MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());

        for (int i = 0; i < 7; i++) {
            boolean isToday = labels[i].equals(todayLabel);
            float ratio = (float) data[i] / max;

            // 柱形列
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colP = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            colP.setMargins(3, 0, 3, 0);
            col.setLayoutParams(colP);

            // 数值
            TextView valTv = new TextView(this);
            valTv.setText(data[i] > 0 ? String.valueOf(data[i]) : "");
            valTv.setTextColor(isToday ? Color.parseColor("#A8E063") : Color.parseColor("#44445A"));
            valTv.setTextSize(9);
            valTv.setGravity(Gravity.CENTER);
            col.addView(valTv);

            // 柱子
            View bar = new View(this);
            int barH = data[i] > 0 ? Math.max((int)(ratio * dp(96)), dp(4)) : 0;
            LinearLayout.LayoutParams barP = new LinearLayout.LayoutParams(dp(18), barH);
            barP.topMargin = dp(2);
            bar.setLayoutParams(barP);
            bar.setBackground(ContextCompat.getDrawable(this,
                    isToday ? R.drawable.bg_today_bar : R.drawable.bg_history_bar));
            col.addView(bar);
            chartContainer.addView(col);

            // 日期标签
            TextView lbl = new TextView(this);
            String[] parts = labels[i].split("-");
            lbl.setText(isToday ? "今天" : parts[1] + "日");
            lbl.setTextColor(isToday ? Color.parseColor("#A8E063") : Color.parseColor("#44445A"));
            lbl.setTextSize(9);
            lbl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lblP = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lbl.setLayoutParams(lblP);
            chartLabels.addView(lbl);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
