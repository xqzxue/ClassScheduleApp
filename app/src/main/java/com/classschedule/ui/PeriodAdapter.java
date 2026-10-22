package com.classschedule.ui;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.classschedule.data.CoursePeriod;
import com.classschedule.databinding.ItemPeriodBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 课表列表适配器。itemStatus：0 普通 / 1 已结束 / 2 进行中 / 3 未开始。
 */
public class PeriodAdapter extends RecyclerView.Adapter<PeriodAdapter.VH> {

    public static final int NORMAL = 0;
    public static final int DONE = 1;
    public static final int ONGOING = 2;
    public static final int UPCOMING = 3;

    private final List<CoursePeriod> periods = new ArrayList<>();
    private final List<Integer> statuses = new ArrayList<>();

    @SuppressLint("NotifyDataSetChanged")
    public void submit(List<CoursePeriod> list, List<Integer> status) {
        periods.clear();
        statuses.clear();
        periods.addAll(list);
        statuses.addAll(status);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPeriodBinding binding = ItemPeriodBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CoursePeriod p = periods.get(position);
        int status = statuses.get(position);
        holder.bind(p, status);
    }

    @Override
    public int getItemCount() {
        return periods.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemPeriodBinding b;

        VH(ItemPeriodBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(CoursePeriod p, int status) {
            b.tvPeriodNum.setText(String.valueOf(p.period));
            b.tvPeriodName.setText(p.displayName());
            b.tvPeriodTime.setText(p.getTimeText());

            int colorPrimary = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorPrimary);
            int colorOnPrimary = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorOnPrimary);
            int colorPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorPrimaryContainer);
            int colorOnPrimaryContainer = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorOnPrimaryContainer);
            int colorSurfaceContainer = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorSurfaceContainer);
            int colorOutlineVariant = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorOutlineVariant);
            int colorOnSurface = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorOnSurface);
            int colorOnSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorOnSurfaceVariant);
            int colorOutline = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorOutline);
            int colorSecondaryContainer = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorSecondaryContainer);
            int colorOnSecondaryContainer = com.google.android.material.color.MaterialColors.getColor(
                    b.getRoot(), com.google.android.material.R.attr.colorOnSecondaryContainer);

            // 恢复默认样式
            b.cardPeriod.setCardBackgroundColor(colorSurfaceContainer);
            b.cardPeriod.setStrokeColor(colorOutlineVariant);
            b.cardPeriod.setStrokeWidth(dp(0.6f));
            b.tvPeriodNum.setBackgroundTintList(ColorStateList.valueOf(colorSecondaryContainer));
            b.tvPeriodNum.setTextColor(colorOnSecondaryContainer);
            b.tvPeriodName.setTextColor(colorOnSurface);
            b.getRoot().setAlpha(1f);

            switch (status) {
                case ONGOING:
                    b.cardPeriod.setCardBackgroundColor(colorPrimaryContainer);
                    b.cardPeriod.setStrokeColor(colorPrimary);
                    b.cardPeriod.setStrokeWidth(dp(2));
                    b.tvPeriodNum.setBackgroundTintList(ColorStateList.valueOf(colorPrimary));
                    b.tvPeriodNum.setTextColor(colorOnPrimary);
                    b.tvPeriodName.setTextColor(colorOnPrimaryContainer);
                    b.tvPeriodStatus.setText("进行中");
                    b.tvPeriodStatus.setTextColor(colorPrimary);
                    b.tvPeriodStatus.setVisibility(android.view.View.VISIBLE);
                    break;
                case DONE:
                    b.tvPeriodStatus.setText("已结束");
                    b.tvPeriodStatus.setTextColor(colorOutline);
                    b.tvPeriodStatus.setVisibility(android.view.View.VISIBLE);
                    b.getRoot().setAlpha(0.55f);
                    break;
                case UPCOMING:
                    b.tvPeriodStatus.setText("未开始");
                    b.tvPeriodStatus.setTextColor(colorOnSurfaceVariant);
                    b.tvPeriodStatus.setVisibility(android.view.View.VISIBLE);
                    break;
                default:
                    b.tvPeriodStatus.setVisibility(android.view.View.GONE);
            }
        }

        private int dp(float value) {
            return Math.round(value * b.getRoot().getResources().getDisplayMetrics().density);
        }
    }
}
