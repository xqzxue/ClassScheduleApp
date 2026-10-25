package com.classschedule.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.classschedule.R;
import com.classschedule.data.CoursePeriod;
import com.classschedule.data.DaySchedule;
import com.classschedule.data.SettingsStore;
import com.classschedule.data.TimeFmt;
import com.classschedule.data.WeekTable;
import com.classschedule.databinding.FragmentScheduleBinding;
import com.google.android.material.chip.Chip;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 课表页：周一至周五切换，高亮当前正在进行的课并自动滚动定位。
 * 数据来自当前启用的课程表配置，配置切换或编辑后自动刷新。
 */
public class ScheduleFragment extends Fragment implements SettingsStore.ChangeObserver {

    private FragmentScheduleBinding b;
    private PeriodAdapter adapter;
    private WeekTable table;
    private DaySchedule selected;
    private boolean didAutoScroll = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            if (b != null) {
                showDay(selected);
                handler.postDelayed(this, 30_000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentScheduleBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewCompat.setOnApplyWindowInsetsListener(b.scheduleRoot, (v, insets) -> {
            androidx.core.graphics.Insets i = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, v.getPaddingBottom());
            return insets;
        });

        adapter = new PeriodAdapter();
        b.recyclerPeriods.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recyclerPeriods.setAdapter(adapter);
        buildChips();
    }

    private void buildChips() {
        table = SettingsStore.get().activeWeekTable();
        b.chipGroupDays.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (DaySchedule day : table.week()) {
            Chip chip = (Chip) inflater.inflate(R.layout.view_day_chip, b.chipGroupDays, false);
            chip.setText(TimeFmt.weekdayShort(day.day));
            chip.setCheckable(true);
            chip.setTag(day);
            chip.setOnClickListener(v -> showDay((DaySchedule) chip.getTag()));
            b.chipGroupDays.addView(chip);
        }

        // 默认选中当天（含周六周日）
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        selected = table.of(today);
    }

    @Override
    public void onResume() {
        super.onResume();
        SettingsStore.get().register(this);
        didAutoScroll = false;
        showDay(selected);
        handler.post(refresher);
    }

    @Override
    public void onPause() {
        super.onPause();
        SettingsStore.get().unregister(this);
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        b = null;
    }

    @Override
    public void onProfilesChanged() {
        if (b == null) {
            return;
        }
        // 配置切换/编辑：重建周课表与 Chip，尽量保持当前星期选择
        DayOfWeek keep = selected != null ? selected.day : null;
        buildChips();
        DaySchedule target = keep == null ? null : table.of(keep);
        selected = target != null ? target : table.week().get(0);
        didAutoScroll = false;
        showDay(selected);
    }

    private void showDay(DaySchedule day) {
        if (day == null || b == null) {
            return;
        }
        selected = day;
        LocalDate nowDate = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        boolean isToday = day.day == nowDate.getDayOfWeek();

        // 同步 Chip 选中态
        for (int i = 0; i < b.chipGroupDays.getChildCount(); i++) {
            View child = b.chipGroupDays.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                DaySchedule tagged = (DaySchedule) chip.getTag();
                chip.setChecked(tagged.day == day.day);
            }
        }

        // 头部：配置名 + 星期 + 日期 + 今天标记
        LocalDate targetDate = nowDate.plusDays(
                day.day.getValue() - nowDate.getDayOfWeek().getValue());
        String header = String.format(Locale.CHINA, "%s  ·  %d月%d日",
                TimeFmt.weekdayName(day.day), targetDate.getMonthValue(), targetDate.getDayOfMonth());
        if (isToday) {
            header += "（今天）";
        }
        b.tvDayHeader.setText(SettingsStore.get().active().name + "  ·  " + header);

        List<Integer> statuses = new ArrayList<>();
        int scrollTo = -1;
        for (int i = 0; i < day.periods.size(); i++) {
            CoursePeriod p = day.periods.get(i);
            int status = PeriodAdapter.NORMAL;
            if (isToday) {
                if (!nowTime.isBefore(p.start) && nowTime.isBefore(p.end)) {
                    status = PeriodAdapter.ONGOING;
                    scrollTo = i;
                } else if (!nowTime.isBefore(p.end)) {
                    status = PeriodAdapter.DONE;
                } else {
                    status = PeriodAdapter.UPCOMING;
                    if (scrollTo == -1) {
                        scrollTo = i;
                    }
                }
            }
            statuses.add(status);
        }
        adapter.submit(day.periods, statuses);

        // 无课日（如周六）显示空提示
        boolean empty = day.periods.isEmpty();
        b.tvEmptyDay.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.recyclerPeriods.setVisibility(empty ? View.GONE : View.VISIBLE);

        // 每次回到本页只自动滚动一次，避免定时刷新打断用户浏览
        if (isToday && scrollTo >= 0 && !didAutoScroll) {
            didAutoScroll = true;
            final int target = Math.max(0, scrollTo - 1);
            b.recyclerPeriods.post(() -> {
                if (b != null) {
                    ((LinearLayoutManager) b.recyclerPeriods.getLayoutManager())
                            .scrollToPositionWithOffset(target, 0);
                }
            });
        }
    }
}
