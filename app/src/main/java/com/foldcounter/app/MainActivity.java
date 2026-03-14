package com.foldcounter.app;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // 统一缓动曲线（实例变量，避免类加载时初始化崩溃）
    private final PathInterpolator SPRING   = new PathInterpolator(0.34f, 1.56f, 0.64f, 1.0f);
    private final PathInterpolator EASE_OUT = new PathInterpolator(0.16f, 1.0f, 0.30f, 1.0f);
    private final PathInterpolator EASE_IN  = new PathInterpolator(0.4f,  0.0f, 1.0f,  1.0f);

    private TextView      tvCount, tvSession, tvToday, tvHingeAngle;
    private TextView      tvStatus, tvServiceBadge, tvDate, tvWeekTotal;
    private TextView      tvAvgLabel;
    private View          pulse1, pulse2, pulse3;
    private LinearLayout  chartBars, chartLabels;

    private FoldCounterService svc;
    private boolean isBound        = false;
    private int     lastTotal      = -1;
    private int     displayedCount = 0;

    // ── 服务绑定 ──────────────────────────────────────────────────────────
    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            svc = ((FoldCounterService.LocalBinder) b).getService();
            isBound = true;
            displayedCount = svc.getTotalCount();
            lastTotal = displayedCount;
            refresh(false);
            buildChart();
        }
        @Override public void onServiceDisconnected(ComponentName n) { isBound = false; }
    };

    // ── 实时广播接收 ──────────────────────────────────────────────────────
    private final BroadcastReceiver rxUpdate = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            int     session     = i.getIntExtra(FoldCounterService.EXTRA_SESSION, 0);
            int     total       = i.getIntExtra(FoldCounterService.EXTRA_TOTAL,   0);
            int     today       = i.getIntExtra(FoldCounterService.EXTRA_TODAY,   0);
            boolean fold        = i.getBooleanExtra(FoldCounterService.EXTRA_FOLDED,      false);
            float   angle       = i.getFloatExtra(FoldCounterService.EXTRA_ANGLE,         -1f);
            boolean isFoldEvent = i.getBooleanExtra(FoldCounterService.EXTRA_IS_FOLD_EVENT, false);
            boolean avail       = i.getBooleanExtra(FoldCounterService.EXTRA_HINGE_AVAIL,  false);
            updateUI(session, total, today, fold, angle, avail, isFoldEvent);
            if (isFoldEvent) buildChart();
        }
    };

    // ── 里程碑广播接收 ─────────────────────────────────────────────────────
    private final BroadcastReceiver rxMilestone = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            int count = i.getIntExtra(FoldCounterService.EXTRA_MILESTONE_COUNT, 0);
            showMilestone(count);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        tvDate.setText(new SimpleDateFormat("M月d日  EEEE", Locale.CHINESE).format(new Date()));

        enterAnimation();

        Intent svcI = new Intent(this, FoldCounterService.class);
        ContextCompat.startForegroundService(this, svcI);
        bindService(svcI, conn, Context.BIND_AUTO_CREATE);

        setupButtons();
    }

    private void bindViews() {
        tvCount        = findViewById(R.id.tv_count);
        tvSession      = findViewById(R.id.tv_session);
        tvToday        = findViewById(R.id.tv_today);
        tvHingeAngle   = findViewById(R.id.tv_hinge_angle);
        tvStatus       = findViewById(R.id.tv_status);
        tvServiceBadge = findViewById(R.id.tv_service_badge);
        tvDate         = findViewById(R.id.tv_date);
        tvWeekTotal    = findViewById(R.id.tv_week_total);
        tvAvgLabel     = findViewById(R.id.tv_avg_label);
        pulse1         = findViewById(R.id.pulse1);
        pulse2         = findViewById(R.id.pulse2);
        pulse3         = findViewById(R.id.pulse3);
        chartBars      = findViewById(R.id.chart_bars);
        chartLabels    = findViewById(R.id.chart_labels);
    }

    private void setupButtons() {
        // 触摸缩放反馈
        int[] ids = {R.id.btn_reset_session, R.id.btn_clear_all, R.id.btn_stop};
        for (int id : ids) {
            View v = findViewById(id);
            v.setOnTouchListener((view, ev) -> {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.95f).scaleY(0.95f)
                            .setDuration(120).setInterpolator(EASE_IN).start();
                } else if (ev.getAction() == MotionEvent.ACTION_UP
                        || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(250).setInterpolator(SPRING).start();
                }
                return false;
            });
        }

        findViewById(R.id.btn_reset_session).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("重置本次计数")
                .setMessage("将本次会话计数清零，累计和今日数据不受影响。")
                .setPositiveButton("确定", (d, w) -> sendAction(FoldCounterService.ACTION_RESET_SESSION))
                .setNegativeButton("取消", null).show());

        findViewById(R.id.btn_clear_all).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("清空所有数据")
                .setMessage("将清空累计、今日、本次及全部历史记录。\n此操作不可恢复，确定吗？")
                .setPositiveButton("清空", (d, w) -> sendAction(FoldCounterService.ACTION_RESET_ALL))
                .setNegativeButton("取消", null).show());

        findViewById(R.id.btn_stop).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("停止后台服务")
                .setMessage("停止后将不再统计折叠次数，已有数据不会丢失。")
                .setPositiveButton("停止", (d, w) -> {
                    stopService(new Intent(this, FoldCounterService.class));
                    finish();
                })
                .setNegativeButton("取消", null).show());
    }

    private void sendAction(String action) {
        Intent i = new Intent(this, FoldCounterService.class);
        i.setAction(action);
        startService(i);
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f1 = new IntentFilter(FoldCounterService.BROADCAST_UPDATE);
        IntentFilter f2 = new IntentFilter(FoldCounterService.BROADCAST_MILESTONE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(rxUpdate,    f1, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(rxMilestone, f2, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(rxUpdate,    f1);
            registerReceiver(rxMilestone, f2);
        }
        if (isBound) { refresh(false); buildChart(); }
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(rxUpdate); } catch (Exception ignored) {}
        try { unregisterReceiver(rxMilestone); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isBound) { unbindService(conn); isBound = false; }
    }

    private void refresh(boolean animate) {
        if (!isBound || svc == null) return;
        updateUI(svc.getSessionCount(), svc.getTotalCount(), svc.getTodayCount(),
                 svc.isCurrentlyFolded(), svc.getCurrentAngle(), svc.isHingeAvailable(), animate);
    }

    // ── UI 更新 ───────────────────────────────────────────────────────────
    private void updateUI(int session, int total, int today, boolean folded,
                          float angle, boolean avail, boolean isFoldEvent) {

        // 铰链角度
        tvHingeAngle.setText(angle >= 0
                ? String.format(Locale.getDefault(), "%.0f°", angle) : "--°");

        // 今日 / 本次
        tvToday.setText(String.valueOf(today));
        tvSession.setText(String.valueOf(session));

        // 主计数：折叠事件时滚动动画，否则直接设值
        if (isFoldEvent && total != displayedCount) {
            animateCountRoll(displayedCount, total);
            displayedCount = total;
        } else if (!isFoldEvent && total != lastTotal) {
            tvCount.setText(String.valueOf(total));
            displayedCount = total;
        }
        lastTotal = total;

        // 状态文字淡换
        String newStatus = !avail ? "未检测到铰链传感器"
                         : folded ? "屏幕已合上" : "屏幕已展开";
        if (!tvStatus.getText().toString().equals(newStatus)) {
            crossFadeText(tvStatus, newStatus);
        }

        // badge
        if (!avail) {
            tvServiceBadge.setText("● 不支持");
            tvServiceBadge.setTextColor(Color.parseColor("#FF453A"));
            tvServiceBadge.setBackground(
                    ContextCompat.getDrawable(this, R.drawable.bg_badge_gray));
        } else {
            tvServiceBadge.setText("● 运行中");
            tvServiceBadge.setTextColor(Color.parseColor("#34C759"));
            tvServiceBadge.setBackground(
                    ContextCompat.getDrawable(this, R.drawable.bg_badge_green));
        }

        // 折叠事件 → 脉冲
        if (isFoldEvent) firePulses();
    }

    // ── 进场动画 ─────────────────────────────────────────────────────────
    private void enterAnimation() {
        View root = findViewById(R.id.scroll_root);
        if (root == null) return;
        root.setAlpha(0f);
        root.setTranslationY(dp(20));
        root.animate().alpha(1f).translationY(0f)
                .setDuration(360).setInterpolator(EASE_OUT).start();
    }

    // ── 文字淡入切换 ──────────────────────────────────────────────────────
    private void crossFadeText(TextView tv, String newText) {
        tv.animate().alpha(0f).setDuration(140).setInterpolator(EASE_IN)
                .withEndAction(() -> {
                    tv.setText(newText);
                    tv.animate().alpha(1f).setDuration(200).setInterpolator(EASE_OUT).start();
                }).start();
    }

    // ── 数字滚动 ─────────────────────────────────────────────────────────
    private void animateCountRoll(int from, int to) {
        // 数字递增
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(380);
        va.setInterpolator(EASE_OUT);
        va.addUpdateListener(a -> tvCount.setText(String.valueOf((int) a.getAnimatedValue())));

        // 弹跳缩放
        ObjectAnimator sx = ObjectAnimator.ofFloat(tvCount, "scaleX", 1f, 1.15f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(tvCount, "scaleY", 1f, 1.15f, 1f);
        sx.setDuration(380); sy.setDuration(380);
        sx.setInterpolator(SPRING); sy.setInterpolator(SPRING);

        // 颜色闪白
        ObjectAnimator col = ObjectAnimator.ofArgb(tvCount, "textColor",
                Color.parseColor("#34C759"), Color.WHITE, Color.parseColor("#34C759"));
        col.setDuration(480);
        col.setInterpolator(EASE_OUT);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(va, sx, sy, col);
        set.start();
    }

    // ── 三层脉冲 ─────────────────────────────────────────────────────────
    private void firePulses() {
        fireSinglePulse(pulse1,   0);
        fireSinglePulse(pulse2, 110);
        fireSinglePulse(pulse3, 220);
    }

    private void fireSinglePulse(final View v, long delay) {
        v.setScaleX(0.5f); v.setScaleY(0.5f); v.setAlpha(0.85f);
        ObjectAnimator sx    = ObjectAnimator.ofFloat(v, "scaleX", 0.5f, 2.5f);
        ObjectAnimator sy    = ObjectAnimator.ofFloat(v, "scaleY", 0.5f, 2.5f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, "alpha",  0.85f, 0f);
        sx.setDuration(680); sy.setDuration(680); alpha.setDuration(680);
        sx.setInterpolator(EASE_OUT); sy.setInterpolator(EASE_OUT);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, alpha);
        set.setStartDelay(delay);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { v.setAlpha(0f); }
        });
        set.start();
    }

    // ── 里程碑提示 ────────────────────────────────────────────────────────
    private void showMilestone(int count) {
        // Toast 提示
        Toast.makeText(this, "🎉 已折叠 " + count + " 次！", Toast.LENGTH_SHORT).show();
        // 大数字额外闪烁
        ObjectAnimator col = ObjectAnimator.ofArgb(tvCount, "textColor",
                Color.parseColor("#34C759"), Color.parseColor("#FFD60A"),
                Color.parseColor("#34C759"), Color.WHITE, Color.parseColor("#34C759"));
        col.setDuration(900);
        col.setInterpolator(EASE_OUT);
        col.start();
        // 额外一轮脉冲
        firePulses();
    }

    // ── 近7天柱状图 ───────────────────────────────────────────────────────
    private void buildChart() {
        if (!isBound || svc == null) return;
        int[]    data   = svc.getHistory(7);
        String[] labels = svc.getHistoryLabels(7);

        int max = 1, sum = 0, activeDays = 0;
        for (int d : data) {
            if (d > max) max = d;
            sum += d;
            if (d > 0) activeDays++;
        }

        // 周统计 + 日均
        String avg = activeDays > 0
                ? String.valueOf(Math.round((float) sum / activeDays))
                : "0";
        tvWeekTotal.setText("共 " + sum + " 次");
        tvAvgLabel.setText("活跃日均 " + avg + " 次");

        chartBars.removeAllViews();
        chartLabels.removeAllViews();

        String todayStr = new SimpleDateFormat("MM-dd", Locale.getDefault()).format(new Date());

        for (int i = 0; i < 7; i++) {
            boolean isToday = labels[i].equals(todayStr);
            final int fi = i;

            // 列容器
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            cp.setMargins(3, 0, 3, 0);
            col.setLayoutParams(cp);

            // 数值
            TextView vt = new TextView(this);
            vt.setText(data[i] > 0 ? String.valueOf(data[i]) : "");
            vt.setTextColor(isToday ? Color.parseColor("#34C759") : Color.parseColor("#36364A"));
            vt.setTextSize(9f);
            vt.setGravity(Gravity.CENTER);
            col.addView(vt);

            // 柱子
            View bar = new View(this);
            int barH = data[i] > 0
                    ? Math.max((int)((float)data[i] / max * dp(72)), dp(4))
                    : dp(2);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(18), barH);
            bp.topMargin = dp(2);
            bar.setLayoutParams(bp);
            bar.setBackground(ContextCompat.getDrawable(this,
                    isToday ? R.drawable.bar_today : R.drawable.bar_normal));

            // 入场动画
            bar.setScaleY(0f);
            bar.setPivotY(barH);
            bar.animate().scaleY(1f).setDuration(380)
                    .setStartDelay(fi * 30L)
                    .setInterpolator(EASE_OUT).start();

            col.addView(bar);
            chartBars.addView(col);

            // 日期标签
            TextView lt = new TextView(this);
            String[] parts = labels[i].split("-");
            lt.setText(isToday ? "今天" : (parts.length > 1 ? parts[1] + "日" : labels[i]));
            lt.setTextColor(isToday ? Color.parseColor("#34C759") : Color.parseColor("#36364A"));
            lt.setTextSize(9f);
            lt.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lt.setLayoutParams(lp);
            chartLabels.addView(lt);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
