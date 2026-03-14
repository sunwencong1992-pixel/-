package com.foldcounter.app;

import android.animation.AnimatorSet;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.animation.ObjectAnimator;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvCount, tvSession, tvTotal, tvToday, tvStatus;
    private TextView tvServiceBadge, tvDate, tvWeekTotal, tvHingeAngle;
    private View pulse1, pulse2, pulse3;
    private LinearLayout chartBars, chartLabels;

    private FoldCounterService svc;
    private boolean isBound = false;
    private int lastTotal = -1;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((FoldCounterService.LocalBinder) b).getService();
            isBound = true;
            refresh(false);
            buildChart();
        }
        @Override public void onServiceDisconnected(ComponentName n) { isBound = false; }
    };

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            int   session = i.getIntExtra(FoldCounterService.EXTRA_SESSION, 0);
            int   total   = i.getIntExtra(FoldCounterService.EXTRA_TOTAL, 0);
            int   today   = i.getIntExtra(FoldCounterService.EXTRA_TODAY, 0);
            boolean fold  = i.getBooleanExtra(FoldCounterService.EXTRA_FOLDED, false);
            float angle   = i.getFloatExtra(FoldCounterService.EXTRA_ANGLE, -1f);
            boolean avail = i.getBooleanExtra(FoldCounterService.EXTRA_HINGE_AVAIL, false);
            boolean isNew = total > lastTotal && lastTotal >= 0;
            updateUI(session, total, today, fold, angle, avail, isNew);
            if (isNew) buildChart();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCount       = findViewById(R.id.tv_count);
        tvSession     = findViewById(R.id.tv_session);
        tvToday       = findViewById(R.id.tv_today);
        tvStatus      = findViewById(R.id.tv_status);
        tvServiceBadge= findViewById(R.id.tv_service_badge);
        tvDate        = findViewById(R.id.tv_date);
        tvWeekTotal   = findViewById(R.id.tv_week_total);
        tvHingeAngle  = findViewById(R.id.tv_hinge_angle);
        pulse1        = findViewById(R.id.pulse1);
        pulse2        = findViewById(R.id.pulse2);
        pulse3        = findViewById(R.id.pulse3);
        chartBars     = findViewById(R.id.chart_bars);
        chartLabels   = findViewById(R.id.chart_labels);

        // 显示今日日期
        tvDate.setText(new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE).format(new Date()));

        Intent svcIntent = new Intent(this, FoldCounterService.class);
        ContextCompat.startForegroundService(this, svcIntent);
        bindService(svcIntent, conn, Context.BIND_AUTO_CREATE);

        findViewById(R.id.btn_reset_session).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("重置本次计数")
                .setMessage("将本次会话计数清零，累计和今日数据不受影响。")
                .setPositiveButton("确定", (d, w) -> {
                    Intent r = new Intent(this, FoldCounterService.class);
                    r.setAction(FoldCounterService.ACTION_RESET_SESSION);
                    startService(r);
                }).setNegativeButton("取消", null).show());

        findViewById(R.id.btn_clear_all).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("清空所有数据")
                .setMessage("将清空累计、今日、本次及全部历史记录。\n\n此操作不可恢复，确定吗？")
                .setPositiveButton("清空", (d, w) -> {
                    Intent r = new Intent(this, FoldCounterService.class);
                    r.setAction(FoldCounterService.ACTION_RESET_ALL);
                    startService(r);
                }).setNegativeButton("取消", null).show());

        findViewById(R.id.btn_stop).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("停止后台服务")
                .setMessage("停止后将不再统计折叠次数，已有数据不会丢失。")
                .setPositiveButton("停止", (d, w) -> {
                    stopService(new Intent(this, FoldCounterService.class));
                    finish();
                }).setNegativeButton("取消", null).show());
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(rx, new IntentFilter(FoldCounterService.BROADCAST_UPDATE),
                Context.RECEIVER_NOT_EXPORTED);
        if (isBound) { refresh(false); buildChart(); }
    }

    @Override protected void onPause() { super.onPause(); unregisterReceiver(rx); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isBound) { unbindService(conn); isBound = false; }
    }

    private void refresh(boolean animate) {
        if (!isBound || svc == null) return;
        updateUI(svc.getSessionCount(), svc.getTotalCount(), svc.getTodayCount(),
                svc.isCurrentlyFolded(), svc.getCurrentAngle(), svc.isHingeAvailable(), animate);
    }

    private void updateUI(int session, int total, int today,
                          boolean folded, float angle, boolean avail, boolean isNew) {
        lastTotal = total;
        tvCount.setText(String.valueOf(total));
        tvSession.setText(String.valueOf(session));
        tvToday.setText(String.valueOf(today));

        if (angle >= 0)
            tvHingeAngle.setText(String.format(Locale.getDefault(), "%.0f°", angle));
        else
            tvHingeAngle.setText("—°");

        if (!avail) {
            tvStatus.setText("未检测到铰链传感器");
            tvServiceBadge.setText("● 不支持");
            tvServiceBadge.setTextColor(Color.parseColor("#FF453A"));
            tvServiceBadge.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chip_gray));
        } else {
            tvStatus.setText(folded ? "屏幕已合上" : "屏幕已展开");
            tvServiceBadge.setText("● 运行中");
            tvServiceBadge.setTextColor(Color.parseColor("#30D158"));
            tvServiceBadge.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_chip_green));
        }

        if (isNew) { animateBounce(); animatePulses(); }
    }

    // ── 数字弹跳 ────────────────────────────────────────────────────────
    private void animateBounce() {
        ObjectAnimator sx  = ObjectAnimator.ofFloat(tvCount, "scaleX", 1f, 1.5f, 0.92f, 1f);
        ObjectAnimator sy  = ObjectAnimator.ofFloat(tvCount, "scaleY", 1f, 1.5f, 0.92f, 1f);
        ObjectAnimator col = ObjectAnimator.ofArgb(tvCount, "textColor",
                Color.parseColor("#30D158"), Color.WHITE, Color.parseColor("#30D158"));
        sx.setDuration(550); sy.setDuration(550); col.setDuration(550);
        sx.setInterpolator(new OvershootInterpolator(3f));
        sy.setInterpolator(new OvershootInterpolator(3f));
        AnimatorSet _set = new AnimatorSet(); _set.playTogether(sx, sy, col); _set.start();
    }

    // ── 三层脉冲 ─────────────────────────────────────────────────────────
    private void animatePulses() {
        firePulse(pulse1,   0);
        firePulse(pulse2, 150);
        firePulse(pulse3, 300);
    }

    private void firePulse(View v, long delay) {
        v.setScaleX(0.6f); v.setScaleY(0.6f); v.setAlpha(1f);
        ObjectAnimator sx    = ObjectAnimator.ofFloat(v, "scaleX", 0.6f, 2.4f);
        ObjectAnimator sy    = ObjectAnimator.ofFloat(v, "scaleY", 0.6f, 2.4f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, "alpha",  1f,   0f);
        sx.setDuration(800); sy.setDuration(800); alpha.setDuration(800);
        sx.setInterpolator(new DecelerateInterpolator(1.5f));
        sy.setInterpolator(new DecelerateInterpolator(1.5f));
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, alpha);
        set.setStartDelay(delay);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { v.setAlpha(0f); }
        });
        set.start();
    }

    // ── 近7天柱状图 ───────────────────────────────────────────────────────
    private void buildChart() {
        if (!isBound || svc == null) return;
        int[]    data   = svc.getHistory(7);
        String[] labels = svc.getHistoryLabels(7);

        int max = 1, weekSum = 0;
        for (int v : data) { if (v > max) max = v; weekSum += v; }
        tvWeekTotal.setText("共 " + weekSum + " 次");

        chartBars.removeAllViews();
        chartLabels.removeAllViews();

        String todayLbl = new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date());

        for (int i = 0; i < 7; i++) {
            boolean isToday = labels[i].equals(todayLbl);
            float ratio = (float) data[i] / max;
            final int idx = i;

            // 柱列
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            cp.setMargins(4, 0, 4, 0);
            col.setLayoutParams(cp);

            // 数值标签
            TextView vt = new TextView(this);
            vt.setText(data[i] > 0 ? String.valueOf(data[i]) : "");
            vt.setTextColor(isToday ? Color.parseColor("#30D158") : Color.parseColor("#3C3C48"));
            vt.setTextSize(9);
            vt.setGravity(Gravity.CENTER);
            col.addView(vt);

            // 柱子
            View bar = new View(this);
            int barH = data[i] > 0 ? Math.max((int)(ratio * dp(88)), dp(4)) : dp(2);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(22), barH);
            bp.topMargin = dp(2);
            bar.setLayoutParams(bp);
            bar.setBackground(ContextCompat.getDrawable(this,
                    isToday ? R.drawable.bar_today : R.drawable.bar_normal));

            // 柱子进入动画
            bar.setScaleY(0f);
            bar.setPivotY(barH);
            bar.animate().scaleY(1f).setDuration(400).setStartDelay(idx * 50L)
                .setInterpolator(new DecelerateInterpolator()).start();

            col.addView(bar);
            chartBars.addView(col);

            // 日期标签
            TextView lt = new TextView(this);
            String[] p = labels[i].split("-");
            lt.setText(isToday ? "今天" : p[1] + "日");
            lt.setTextColor(isToday ? Color.parseColor("#30D158") : Color.parseColor("#3C3C48"));
            lt.setTextSize(9);
            lt.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lt.setLayoutParams(lp);
            chartLabels.addView(lt);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

