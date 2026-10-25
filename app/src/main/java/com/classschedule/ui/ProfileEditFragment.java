package com.classschedule.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.classschedule.R;
import com.classschedule.data.CoursePeriod;
import com.classschedule.data.DayPlan;
import com.classschedule.data.DaySchedule;
import com.classschedule.data.PeriodItem;
import com.classschedule.data.ScheduleProfile;
import com.classschedule.data.ScheduleRepository;
import com.classschedule.data.SettingsStore;
import com.classschedule.data.TimeFmt;
import com.classschedule.data.WeekTable;
import com.classschedule.databinding.DialogEditPeriodBinding;
import com.classschedule.databinding.FragmentProfileEditBinding;
import com.classschedule.databinding.ItemEditPeriodBinding;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 课程表编辑器：周一至周日切换，逐节改名；支持新增（13、14 节……）、
 * 修改自定义课程的时间、删除自定义课程。
 */
public class ProfileEditFragment extends Fragment implements SettingsStore.ChangeObserver {

    private static final String ARG_ID = "profile_id";

    public static ProfileEditFragment newInstance(String profileId) {
        Bundle args = new Bundle();
        args.putString(ARG_ID, profileId);
        ProfileEditFragment f = new ProfileEditFragment();
        f.setArguments(args);
        return f;
    }

    private FragmentProfileEditBinding b;
    private String profileId;
    private int dayIndex = 0;
    private EditAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentProfileEditBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        profileId = getArguments() != null ? getArguments().getString(ARG_ID) : null;
        // 内置默认配置只读，不允许进入编辑器
        if (SettingsStore.BUILTIN_ID.equals(profileId)) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(b.editRoot, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        b.btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        adapter = new EditAdapter();
        b.recyclerEdit.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recyclerEdit.setAdapter(adapter);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        int initialDay = today.getValue() - 1; // 1 周一 … 7 周日 → 0..6
        for (int d = 0; d < ScheduleRepository.DAY_COUNT; d++) {
            Chip chip = (Chip) inflater.inflate(R.layout.view_day_chip, b.chipGroupEdit, false);
            chip.setText(TimeFmt.weekdayShort(DayOfWeek.of(d + 1)));
            chip.setCheckable(true);
            final int idx = d;
            chip.setOnClickListener(v -> selectDay(idx));
            b.chipGroupEdit.addView(chip);
            if (d == initialDay) {
                chip.setChecked(true);
            }
        }
        dayIndex = initialDay;

        b.btnAddPeriod.setOnClickListener(v -> showAddOrEditDialog(-1));
    }

    @Override
    public void onResume() {
        super.onResume();
        SettingsStore.get().register(this);
        selectDay(dayIndex);
    }

    @Override
    public void onPause() {
        super.onPause();
        SettingsStore.get().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SettingsStore.get().unregister(this);
        b = null;
    }

    @Override
    public void onProfilesChanged() {
        selectDay(dayIndex);
    }

    private ScheduleProfile profile() {
        ScheduleProfile p = SettingsStore.get().find(profileId);
        if (p == null) {
            p = SettingsStore.get().active();
            profileId = p.id;
        }
        return p;
    }

    private void selectDay(int idx) {
        if (b == null) {
            return;
        }
        dayIndex = idx;
        ScheduleProfile p = profile();
        b.tvEditTitle.setText(p.name);
        for (int i = 0; i < b.chipGroupEdit.getChildCount(); i++) {
            View child = b.chipGroupEdit.getChildAt(i);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(i == idx);
            }
        }
        WeekTable table = new WeekTable(p);
        DaySchedule day = table.week().get(idx);
        adapter.submit(day.periods);
        boolean empty = day.periods.isEmpty();
        b.tvEditEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.btnAddPeriod.setText(empty ? "添加第一节课"
                : "添加一节课（当前 " + day.periods.size() + " 节）");
    }

    /**
     * 新增 / 编辑对话框。periodIndex < 0 为新增。
     */
    private void showAddOrEditDialog(int periodIndex) {
        ScheduleProfile p = profile();
        DayPlan day = p.days[dayIndex];
        boolean isAdd = periodIndex < 0;
        boolean custom = !isAdd && day.periods.get(periodIndex).custom;

        // 默认/初始值
        String initName = isAdd ? "" : day.periods.get(periodIndex).name;
        String initStart;
        String initEnd;
        if (isAdd) {
            if (day.periods.isEmpty()) {
                initStart = "08:00";
                initEnd = "08:45";
            } else {
                LocalTime lastEnd = LocalTime.parse(day.periods.get(day.periods.size() - 1).end);
                initStart = day.periods.get(day.periods.size() - 1).end;
                initEnd = lastEnd.plusMinutes(45).toString();
            }
        } else {
            PeriodItem item = day.periods.get(periodIndex);
            initStart = item.start;
            initEnd = item.end;
        }

        DialogEditPeriodBinding dlg = DialogEditPeriodBinding.inflate(getLayoutInflater());
        final String[] start = {initStart};
        final String[] end = {initEnd};
        dlg.peName.setText("无".equals(initName) ? "" : initName);
        dlg.btnPickStart.setText("开始 " + initStart);
        dlg.btnPickEnd.setText("结束 " + initEnd);
        dlg.btnPickStart.setOnClickListener(v -> pickTime(start[0], chosen -> {
            start[0] = chosen;
            dlg.btnPickStart.setText("开始 " + chosen);
        }));
        dlg.btnPickEnd.setOnClickListener(v -> pickTime(end[0], chosen -> {
            end[0] = chosen;
            dlg.btnPickEnd.setText("结束 " + chosen);
        }));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isAdd ? "新增一节课" : (custom ? "编辑本节课" : "修改课程名"))
                .setView(dlg.getRoot())
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = dlg.peName.getText() == null ? ""
                            : dlg.peName.getText().toString().trim();
                    SettingsStore store = SettingsStore.get();
                    if (isAdd) {
                        int added = store.addPeriod(profileId, dayIndex, name, start[0], end[0]);
                        if (added < 0) {
                            snack("时间无效，请确认结束晚于开始");
                        }
                    } else if (custom) {
                        if (!store.updatePeriod(profileId, dayIndex, periodIndex,
                                name, start[0], end[0])) {
                            snack("时间无效，请确认结束晚于开始");
                        }
                    } else {
                        store.updateCourseName(profileId, dayIndex, periodIndex, name);
                    }
                });

        // 仅自定义课程允许删除
        if (custom) {
            builder.setNeutralButton("删除本节", (dialog, which) ->
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("删除本节课")
                            .setMessage("确定删除这节自定义课程吗？")
                            .setPositiveButton("删除", (d, w) -> {
                                if (!SettingsStore.get().removePeriod(profileId, dayIndex, periodIndex)) {
                                    snack("内置课程不可删除");
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show());
        }
        builder.show();
    }

    private interface TimeCallback {
        void onPicked(String hhmm);
    }

    private void pickTime(String current, TimeCallback callback) {
        LocalTime t = LocalTime.parse(current);
        new TimePickerDialog(requireContext(),
                (view, hourOfDay, minute) -> callback.onPicked(
                        String.format(Locale.CHINA, "%02d:%02d", hourOfDay, minute)),
                t.getHour(), t.getMinute(), true).show();
    }

    private void snack(String msg) {
        if (b != null) {
            com.google.android.material.snackbar.Snackbar.make(b.getRoot(), msg,
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
        }
    }

    /** 编辑器列表适配器。 */
    private class EditAdapter extends RecyclerView.Adapter<EditAdapter.VH> {
        private List<CoursePeriod> data = Collections.emptyList();

        void submit(List<CoursePeriod> list) {
            data = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemEditPeriodBinding binding = ItemEditPeriodBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CoursePeriod p = data.get(position);
            boolean custom = profile().days[dayIndex].periods.get(position).custom;
            holder.b.tvEditNum.setText(String.valueOf(p.period));
            holder.b.tvEditName.setText(p.displayName() + (custom ? " · 自定义" : ""));
            holder.b.tvEditTime.setText(p.getTimeText());
            holder.b.getRoot().setOnClickListener(v -> showAddOrEditDialog(p.period - 1));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemEditPeriodBinding b;

            VH(ItemEditPeriodBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
