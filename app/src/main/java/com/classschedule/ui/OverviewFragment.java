package com.classschedule.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.classschedule.MainActivity;
import com.classschedule.R;
import com.classschedule.data.CoursePeriod;
import com.classschedule.data.DaySchedule;
import com.classschedule.data.ScheduleProfile;
import com.classschedule.data.SettingsStore;
import com.classschedule.data.TimeFmt;
import com.classschedule.data.WeekTable;
import com.classschedule.databinding.DialogSwitchProfileBinding;
import com.classschedule.databinding.FragmentOverviewBinding;
import com.classschedule.databinding.ItemProfileBinding;
import com.classschedule.engine.ScheduleEngine;
import com.classschedule.engine.ScheduleStatus;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 概括页：当前课程、下节课、各类倒计时，每秒刷新。
 * 横屏为「桌面台」：全屏渐变铺满状态栏、超大时钟与倒计时、光斑漂移、入场动画。
 */
public class OverviewFragment extends Fragment {

    private FragmentOverviewBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 1000);
        }
    };

    private static final DateTimeFormatter CLOCK_SEC = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter CLOCK_MIN = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter BIG_HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter BIG_S = DateTimeFormatter.ofPattern("ss");

    private boolean landscape;
    private final List<Animator> deskAnims = new ArrayList<>();
    /** 用于检测内容变化触发动画。 */
    private String lastState = "", lastCurrent = "", lastNext = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentOverviewBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        // 点击顶部课表名胶囊即可快速切换配置
        if (b.tvProfileName != null) {
            b.tvProfileName.setOnClickListener(v -> showSwitchSheet());
        }
        if (landscape && b.deskContent != null) {
            setupDesk();
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(b.overviewRoot, (v, insets) -> {
                androidx.core.graphics.Insets i = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, v.getPaddingBottom());
                return insets;
            });
        }
    }

    // ================= 横屏「桌面台」 =================

    private void setupDesk() {
        // 视口避开状态栏/挖孔，背景保持铺满（不黑屏）；内容在视口内等比缩放
        ViewCompat.setOnApplyWindowInsetsListener(b.deskViewport, (v, insets) -> {
            androidx.core.graphics.Insets i = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            int base = Math.round(18 * getResources().getDisplayMetrics().density);
            v.setPadding(base + i.left, base + i.top, base + i.right, base + i.bottom);
            return insets;
        });
        // 视口尺寸/内边距变化后，整体等比缩放并居中（任何分辨率与密度都完整显示）
        b.deskViewport.addOnLayoutChangeListener(
                (v, l, t, r, rb, ol, ot, or, ob) -> applyDeskScale());

        // 全屏柔和渐变（运行时按当前主题取色，配色切换也能自适应）
        int surface = themeColor(com.google.android.material.R.attr.colorSurface);
        int pc = themeColor(com.google.android.material.R.attr.colorPrimaryContainer);
        int sc = themeColor(com.google.android.material.R.attr.colorSecondaryContainer);
        int tc = themeColor(com.google.android.material.R.attr.colorTertiaryContainer);
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        surface,
                        ColorUtils.setAlphaComponent(pc, 72),
                        ColorUtils.setAlphaComponent(sc, 58),
                        ColorUtils.setAlphaComponent(tc, 42),
                        surface
                });
        b.overviewRoot.setBackground(gd);

        // 光斑着色（取当前主题的主/次/三级色）
        tintGlow(b.bgGlow1, com.google.android.material.R.attr.colorPrimary, 70);
        tintGlow(b.bgGlow2, com.google.android.material.R.attr.colorSecondary, 66);
        tintGlow(b.bgGlow3, com.google.android.material.R.attr.colorTertiary, 72);

        // 光斑缓慢漂移（无限循环）
        drift(b.bgGlow1, 55, 42, 13000);
        drift(b.bgGlow2, -60, -48, 16000);
        drift(b.bgGlow3, -48, 52, 11000);

        // 入场动画：各元素依次上浮淡入
        View[] order = {
                b.deskHeader, b.deskClock, b.tvStatePill, b.tvCurrentName, b.tvCurrentTime,
                b.tvMainCountdown, b.progressCurrent, b.deskNext, b.deskStats
        };
        for (int i = 0; i < order.length; i++) {
            View v = order[i];
            v.setAlpha(0f);
            v.setTranslationY(34f);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(70L * i + 120)
                    .setDuration(520)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    /**
     * 横屏桌面台通用自适应：测量内容自然尺寸，把容器固定为该尺寸（父级不会因空间不足
     * 而压缩或裁剪，视口 clipChildren=false 允许溢出绘制），再按视口可用宽/高取较小
     * 比例整体等比缩放并居中。任何分辨率、宽高比与密度下都不会裁切。
     */
    private void applyDeskScale() {
        if (b == null || b.deskViewport == null || b.deskContent == null) {
            return;
        }
        int availW = b.deskViewport.getWidth() - b.deskViewport.getPaddingLeft()
                - b.deskViewport.getPaddingRight();
        int availH = b.deskViewport.getHeight() - b.deskViewport.getPaddingTop()
                - b.deskViewport.getPaddingBottom();
        if (availW <= 0 || availH <= 0) {
            return;
        }
        int unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        b.deskContent.measure(unspec, unspec);
        int naturalW = b.deskContent.getMeasuredWidth();
        int naturalH = b.deskContent.getMeasuredHeight();
        if (naturalW <= 0 || naturalH <= 0) {
            return;
        }
        // 固定容器为自然尺寸（可能大于视口），触发一次居中布局，避免子项被裁剪
        ViewGroup.LayoutParams lp = b.deskContent.getLayoutParams();
        if (lp.width != naturalW || lp.height != naturalH) {
            lp.width = naturalW;
            lp.height = naturalH;
            b.deskContent.setLayoutParams(lp);
            return; // 布局完成后 onLayoutChange 会再次进入，此时再设置缩放
        }
        float scale = Math.min((float) availW / naturalW, (float) availH / naturalH);
        scale = Math.min(scale, 1.3f); // 放大上限，避免超大屏幕上字号过大
        b.deskContent.setPivotX(naturalW / 2f);
        b.deskContent.setPivotY(naturalH / 2f);
        b.deskContent.setScaleX(scale);
        b.deskContent.setScaleY(scale);
    }

    private void tintGlow(View glow, int attr, int alpha) {
        glow.getBackground().mutate()
                .setTint(ColorUtils.setAlphaComponent(themeColor(attr), alpha));
    }

    private void drift(View v, float dx, float dy, long duration) {
        ObjectAnimator x = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, dx);
        ObjectAnimator y = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, dy);
        for (ObjectAnimator a : new ObjectAnimator[]{x, y}) {
            a.setDuration(duration);
            a.setRepeatCount(ObjectAnimator.INFINITE);
            a.setRepeatMode(ObjectAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.LinearInterpolator());
            a.start();
            deskAnims.add(a);
        }
    }

    /** 内容变化时做一次轻微放大淡入。 */
    private void popIn(View v) {
        if (v == null) {
            return;
        }
        v.animate().cancel();
        v.setAlpha(0.35f);
        v.setTranslationY(0f);
        v.setScaleX(0.96f);
        v.setScaleY(0.96f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (Animator a : deskAnims) {
            a.cancel();
        }
        deskAnims.clear();
        handler.removeCallbacks(ticker);
        b = null;
    }

    // ================= 快速切换配置 =================

    private void showSwitchSheet() {
        if (b == null) {
            return;
        }
        SettingsStore store = SettingsStore.get();
        DialogSwitchProfileBinding sheet =
                DialogSwitchProfileBinding.inflate(getLayoutInflater());

        sheet.containerBuiltin.removeAllViews();
        sheet.containerCustom.removeAllViews();
        for (ScheduleProfile p : store.builtinProfiles()) {
            sheet.containerBuiltin.addView(
                    buildSwitchRow(p, store.isActive(p.id), true));
        }
        List<ScheduleProfile> customs = store.customProfiles();
        sheet.tvNoCustom.setVisibility(customs.isEmpty() ? View.VISIBLE : View.GONE);
        for (ScheduleProfile p : customs) {
            sheet.containerCustom.addView(
                    buildSwitchRow(p, store.isActive(p.id), false));
        }

        // 横屏没有底部导航，隐藏「前往配置页」入口
        if (landscape) {
            sheet.btnGotoSettings.setVisibility(View.GONE);
        } else {
            sheet.btnGotoSettings.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).selectTab(R.id.nav_settings);
                }
                if (currentSwitchSheet != null) {
                    currentSwitchSheet.dismiss();
                }
            });
        }

        BottomSheetDialog dlg = new BottomSheetDialog(requireContext());
        dlg.setContentView(sheet.getRoot());
        // 直接展开，不停留在半折叠的 peek 状态
        dlg.getBehavior().setSkipCollapsed(true);
        if (landscape) {
            // 横屏：收窄宽度并限制最大高度，列表内部滚动，避免挤压与文字显示不全
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int w = Math.round(dm.widthPixels * 0.56f);
            w = Math.max(w, dp(320));
            w = Math.min(w, dp(520));
            final int sheetWidth = w;
            dlg.getBehavior().setMaxHeight(Math.round(dm.heightPixels * 0.86f));
            dlg.setOnShowListener(d -> {
                View bottomSheet = dlg.findViewById(
                        com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null) {
                    ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
                    lp.width = sheetWidth;
                    bottomSheet.setLayoutParams(lp);
                }
            });
        }
        dlg.show();
        currentSwitchSheet = dlg;
    }

    /** 当前打开的「切换课程表」底部面板，点击卡片后关闭。 */
    private BottomSheetDialog currentSwitchSheet;

    private View buildSwitchRow(ScheduleProfile p, boolean active, boolean builtin) {
        ItemProfileBinding row = ItemProfileBinding.inflate(getLayoutInflater());
        row.cardProfile.setCardBackgroundColor(
                themeColor(com.google.android.material.R.attr.colorSurfaceContainer));
        row.tvProfileName.setText(p.name);
        row.tvProfileMeta.setText(p.filledCount() + " 节有课 · 共 " + p.totalCount()
                + " 节" + (builtin ? " · 内置" : ""));
        row.ivActive.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
        row.btnProfileMore.setVisibility(View.GONE);
        row.cardProfile.setStrokeWidth(active ? dp(1.6f) : dp(0.8f));
        row.getRoot().setOnClickListener(v -> {
            SettingsStore.get().setActive(p.id);
            if (b != null) {
                Snackbar.make(b.getRoot(), "已切换到「" + p.name + "」",
                        Snackbar.LENGTH_SHORT).show();
            }
            if (currentSwitchSheet != null) {
                currentSwitchSheet.dismiss();
            }
        });
        return row.getRoot();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 根据“精确到秒”设置选择倒计时文案。 */
    private String cd(Duration d) {
        return SettingsStore.get().showSeconds
                ? TimeFmt.minuteSecondCountdown(d)
                : TimeFmt.roundedMinuteCountdown(d);
    }

    private void render() {
        if (b == null) {
            return;
        }
        SettingsStore store = SettingsStore.get();
        WeekTable table = store.activeWeekTable();
        LocalDateTime now = LocalDateTime.now();
        ScheduleStatus s = ScheduleEngine.at(now, table);
        LocalTime nowTime = now.toLocalTime();

        // —— 横屏超大时钟 ——
        if (b.tvBigClock != null) {
            b.tvBigClock.setText(nowTime.format(BIG_HM));
            if (b.tvBigSeconds != null) {
                if (store.showSeconds) {
                    b.tvBigSeconds.setVisibility(View.VISIBLE);
                    b.tvBigSeconds.setText(nowTime.format(BIG_S));
                    View sec = b.tvBigSeconds;
                    sec.animate().cancel();
                    sec.setScaleX(0.82f);
                    sec.setScaleY(0.82f);
                    sec.setAlpha(0.55f);
                    sec.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(220)
                            .setInterpolator(new OvershootInterpolator(2f))
                            .start();
                } else {
                    b.tvBigSeconds.setVisibility(View.GONE);
                }
            }
        }

        // —— 顶部日期 ——
        b.tvDate.setText(String.format(Locale.CHINA, "%d月%d日",
                now.getMonthValue(), now.getDayOfMonth()));
        b.tvWeekdayPill.setText(TimeFmt.weekdayName(now.getDayOfWeek()));
        // —— 当前课表名称 ——
        if (b.tvProfileName != null) {
            b.tvProfileName.setText(store.active().name);
        }
        b.tvLiveClock.setText("现在 " + nowTime.format(
                store.showSeconds ? CLOCK_SEC : CLOCK_MIN));

        // —— 主卡片 ——
        b.progressCurrent.setVisibility(View.GONE);
        switch (s.state) {
            case IN_CLASS:
                CoursePeriod cur = s.current;
                b.tvStatePill.setText(cur.isFree() ? "自习时段" : "上课中");
                b.tvCurrentName.setText(cur.displayName());
                b.tvCurrentTime.setText("第 " + cur.period + " 节 · " + cur.getTimeText());
                b.progressCurrent.setVisibility(View.VISIBLE);
                b.progressCurrent.setProgressCompat(
                        (int) Math.round(s.currentProgress() * 100), true);
                b.tvMainCountdown.setText("还有 " + cd(s.untilCurrentEnd) + " 下课");
                break;
            case BREAK:
                b.tvStatePill.setText("课间休息");
                b.tvCurrentName.setText("课间休息");
                b.tvCurrentTime.setText("下一节：" + s.next.displayName() + "（第 " + s.next.period + " 节）");
                b.tvMainCountdown.setText(cd(s.untilNextStart)
                        + " 后上「" + s.next.displayName() + "」");
                break;
            case BEFORE_SCHOOL:
                b.tvStatePill.setText("课前等待");
                b.tvCurrentName.setText(s.next.displayName());
                b.tvCurrentTime.setText("第 " + s.next.period + " 节 · " + s.next.getTimeText());
                b.tvMainCountdown.setText("还有 " + TimeFmt.roundedMinuteCountdown(s.untilNextStart) + " 上第一节课");
                break;
            case AFTER_SCHOOL:
                b.tvStatePill.setText("已放学");
                b.tvCurrentName.setText("今日课程已结束");
                b.tvCurrentTime.setText("今天辛苦了，注意休息");
                b.tvMainCountdown.setText(s.next == null ? "近期没有课程安排"
                        : "距 " + TimeFmt.weekdayShort(s.nextDay.day)
                        + " 第一节课还有 " + TimeFmt.roundedMinuteCountdown(s.untilNextStart));
                break;
            case WEEKEND:
                boolean satSun = now.getDayOfWeek().getValue() >= 6;
                b.tvStatePill.setText(satSun ? "周末休息" : "休息日");
                b.tvCurrentName.setText(satSun ? "周末愉快" : "今天休息");
                b.tvCurrentTime.setText(s.next == null ? "近期没有课程安排"
                        : TimeFmt.weekdayShort(s.nextDay.day) + "第一节："
                        + s.next.displayName() + " · " + s.next.getTimeText());
                b.tvMainCountdown.setText(s.next == null ? "好好休息"
                        : "距返校还有 " + TimeFmt.roundedMinuteCountdown(s.untilBackToSchool));
                break;
        }

        // —— 下一节课卡片 ——
        if (s.next == null) {
            b.tvNextName.setText("暂无安排");
            b.tvNextTime.setText("可以在「配置」中添加课程");
            b.tvNextCountdown.setText("--:--");
        } else {
            boolean sameDay = s.today != null && s.nextDay.day == s.today.day;
            String dayPrefix = sameDay ? "" : TimeFmt.weekdayShort(s.nextDay.day) + " · ";
            b.tvNextName.setText(s.next.displayName());
            b.tvNextTime.setText(landscape
                    ? (sameDay ? "" : TimeFmt.weekdayShort(s.nextDay.day) + " ")
                        + TimeFmt.compactPeriod(s.next.period, s.next.getTimeText())
                    : dayPrefix + "第 " + s.next.period + " 节 · " + s.next.getTimeText());
            String nextCd = store.showSeconds
                    ? TimeFmt.hmsCountdown(s.untilNextStart)
                    : TimeFmt.roundedMinuteCountdown(s.untilNextStart);
            // 横屏有独立「后上课」静态标签，只填倒计时值
            b.tvNextCountdown.setText(b.deskContent != null ? nextCd : nextCd + " 后上课");
        }

        // —— 放学倒计时 ——
        if (b.tvDismissLabel != null && b.tvDismissLabel.length() == 0) {
            b.tvDismissLabel.setText("距离放学");
        }
        if (s.state == ScheduleStatus.State.WEEKEND) {
            b.tvDismissValue.setText("周末休息中");
        } else if (!s.untilSchoolEnd.isZero() && !s.untilSchoolEnd.isNegative()) {
            b.tvDismissValue.setText(landscape
                    ? TimeFmt.compactRounded(s.untilSchoolEnd)
                    : TimeFmt.roundedMinuteCountdown(s.untilSchoolEnd));
        } else {
            b.tvDismissValue.setText("已放学");
        }

        // —— 周末 / 返校倒计时 ——
        if (s.state == ScheduleStatus.State.WEEKEND) {
            b.tvWeekendLabel.setText("返校倒计时");
            b.tvWeekendValue.setText(landscape
                    ? TimeFmt.compactRounded(s.untilBackToSchool)
                    : TimeFmt.roundedMinuteCountdown(s.untilBackToSchool));
        } else {
            b.tvWeekendLabel.setText("周末倒计时");
            if (!s.untilWeekend.isZero() && !s.untilWeekend.isNegative()) {
                b.tvWeekendValue.setText(landscape
                        ? TimeFmt.compactRounded(s.untilWeekend)
                        : TimeFmt.roundedMinuteCountdown(s.untilWeekend));
            } else {
                b.tvWeekendValue.setText(landscape ? "已到" : "已到周末");
            }
        }

        // —— 今日进度 ——
        DaySchedule today = s.today;
        b.tvProgressLabel.setText("今日进度");
        if (today == null) {
            b.tvTodayProgress.setText("今天没课");
        } else {
            int finished = 0;
            for (CoursePeriod p : today.periods) {
                if (!nowTime.isBefore(p.end)) {
                    finished++;
                }
            }
            b.tvTodayProgress.setText(finished + " / " + today.periods.size() + " 节");
        }

        // —— 横屏：内容变化时触发交叉淡入 ——
        if (landscape) {
            String state = b.tvStatePill.getText().toString();
            String current = b.tvCurrentName.getText().toString();
            String next = b.tvNextName.getText().toString();
            if (!state.equals(lastState)) {
                popIn(b.tvStatePill);
            }
            if (!current.equals(lastCurrent)) {
                popIn(b.tvCurrentName);
            }
            if (!next.equals(lastNext)) {
                popIn(b.tvNextName);
            }
            lastState = state;
            lastCurrent = current;
            lastNext = next;
        }
    }
}
