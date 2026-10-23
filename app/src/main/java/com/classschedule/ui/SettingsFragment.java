package com.classschedule.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.classschedule.data.ScheduleProfile;
import com.classschedule.data.ScheduleRepository;
import com.classschedule.data.SettingsStore;
import com.classschedule.databinding.DialogCreateProfileBinding;
import com.classschedule.databinding.DialogEditTextBinding;
import com.classschedule.databinding.DialogProfileMenuBinding;
import com.classschedule.databinding.FragmentSettingsBinding;
import com.classschedule.databinding.ItemProfileBinding;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * 配置页：外观设置（深色模式 / 主题配色 / 纯黑 / 精确到秒）+ 两个配置板块。
 * 上：内置默认配置（只读，单击仅选用）；下：自定义配置（单击仅选用，三点菜单编辑/重命名/复制/删除）。
 */
public class SettingsFragment extends Fragment implements SettingsStore.ChangeObserver {

    /** 由宿主 Activity 实现，用于打开课程编辑器与带渐变地重建界面。 */
    public interface Host {
        void openProfileEditor(String profileId);

        /** 截取当前画面（随后由系统自动重建，如切换深色模式）。 */
        void captureForRecreate();

        /** 截取当前画面并重建，形成交叉淡入（如切换配色、纯黑）。 */
        void smoothRecreate();
    }

    private FragmentSettingsBinding b;
    private SettingsStore store;
    /** 程序化刷新开关时屏蔽监听，避免误触发重建。 */
    private boolean syncing;

    private static final String[] THEME_NAMES = {"跟随系统", "浅色模式", "深色模式"};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        store = SettingsStore.get();

        ViewCompat.setOnApplyWindowInsetsListener(b.settingsRoot, (v, insets) -> {
            androidx.core.graphics.Insets i = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, v.getPaddingBottom());
            return insets;
        });

        // —— 深色模式 ——
        b.rowTheme.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("深色模式")
                .setSingleChoiceItems(THEME_NAMES, store.themeMode, (dialog, which) -> {
                    if (store.themeMode != which) {
                        if (getActivity() instanceof Host) {
                            // setDefaultNightMode 会自动重建 Activity，先截一帧用于渐变
                            ((Host) getActivity()).captureForRecreate();
                        }
                        store.setThemeMode(which);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show());

        // —— 主题配色（固定 5 套，不再提供动态取色） ——
        b.rowScheme.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("主题配色")
                .setSingleChoiceItems(ThemeHelper.DIALOG_NAMES,
                        ThemeHelper.dialogIndex(store.colorScheme),
                        (dialog, which) -> {
                            int newScheme = ThemeHelper.DIALOG_VALUES[which];
                            if (store.colorScheme != newScheme) {
                                store.setColorScheme(newScheme);
                                if (getActivity() instanceof Host) {
                                    ((Host) getActivity()).smoothRecreate();
                                }
                            }
                            dialog.dismiss();
                        })
                .setNegativeButton("取消", null)
                .show());

        // —— 纯黑模式（仅深色下可用） ——
        b.swPureBlack.setChecked(store.pureBlack);
        b.swPureBlack.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) {
                return;
            }
            if (store.pureBlack != checked) {
                store.setPureBlack(checked);
                if (getActivity() instanceof Host) {
                    ((Host) getActivity()).smoothRecreate();
                }
            }
        });

        // —— 精确到秒 ——
        b.swSeconds.setChecked(store.showSeconds);
        b.swSeconds.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) {
                return;
            }
            if (store.showSeconds != checked) {
                store.setShowSeconds(checked);
            }
        });

        // —— 新建自定义配置 ——
        b.btnNewProfile.setOnClickListener(v -> showCreateDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        store.register(this);
        syncSettingsViews();
        rebuildLists();
    }

    @Override
    public void onPause() {
        super.onPause();
        store.unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        store.unregister(this);
        b = null;
    }

    @Override
    public void onProfilesChanged() {
        if (b != null) {
            rebuildLists();
        }
    }

    @Override
    public void onSettingsChanged() {
        syncSettingsViews();
    }

    private void syncSettingsViews() {
        if (b == null) {
            return;
        }
        syncing = true;
        b.tvThemeValue.setText(THEME_NAMES[store.themeMode]);
        b.tvSchemeValue.setText(ThemeHelper.schemeName(store.colorScheme));
        b.swSeconds.setChecked(store.showSeconds);

        // 纯黑模式只在深色下可用：浅色时整行置灰、开关强制显示为关且不可点
        boolean night = ThemeHelper.isNightEffective(requireContext(), store.themeMode);
        b.rowPureBlack.setEnabled(night);
        b.swPureBlack.setEnabled(night);
        float alpha = night ? 1f : 0.4f;
        b.rowPureBlack.setAlpha(alpha);
        b.swPureBlack.setChecked(night && store.pureBlack);
        syncing = false;
    }

    // ---------- 两个板块 ----------

    private void rebuildLists() {
        if (b == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        // 板块一：内置默认配置（只读，单击仅选用）
        b.defaultContainer.removeAllViews();
        for (ScheduleProfile builtin : store.builtinProfiles()) {
            ItemProfileBinding defRow = ItemProfileBinding.inflate(inflater, b.defaultContainer, false);
            defRow.tvProfileName.setText(builtin.name);
            defRow.tvProfileMeta.setText(builtin.filledCount() + " 节有课 · 共 " + builtin.totalCount()
                    + " 节 · 内置默认" + (store.isActive(builtin.id) ? " · 当前使用" : ""));
            defRow.ivActive.setVisibility(store.isActive(builtin.id) ? View.VISIBLE : View.INVISIBLE);
            defRow.btnProfileMore.setVisibility(View.GONE); // 默认配置没有操作菜单
            defRow.cardProfile.setStrokeWidth(store.isActive(builtin.id) ? dp(1.6f) : dp(0.8f));
            final String builtinId = builtin.id;
            final String builtinName = builtin.name;
            defRow.getRoot().setOnClickListener(v -> {
                store.setActive(builtinId);
                snack("已选用默认配置「" + builtinName + "」");
            });
            b.defaultContainer.addView(defRow.getRoot());
        }

        // 板块二：自定义配置（单击仅选用，编辑走右侧三点菜单）
        b.profileContainer.removeAllViews();
        List<ScheduleProfile> customs = store.customProfiles();
        b.tvNoCustom.setVisibility(customs.isEmpty() ? View.VISIBLE : View.GONE);
        for (ScheduleProfile p : customs) {
            ItemProfileBinding row = ItemProfileBinding.inflate(inflater, b.profileContainer, false);
            boolean active = store.isActive(p.id);
            row.tvProfileName.setText(p.name);
            row.tvProfileMeta.setText(p.filledCount() + " 节有课 · 共 " + p.totalCount() + " 节"
                    + (active ? " · 当前使用" : ""));
            row.ivActive.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            row.cardProfile.setStrokeWidth(active ? dp(1.6f) : dp(0.8f));

            // 单击：只切换为当前配置，不进入编辑
            row.getRoot().setOnClickListener(v -> {
                store.setActive(p.id);
                snack("已切换到「" + p.name + "」");
            });
            row.btnProfileMore.setOnClickListener(v -> showProfileMenu(v, p));
            b.profileContainer.addView(row.getRoot());
        }
    }

    private void showProfileMenu(View anchor, ScheduleProfile p) {
        DialogProfileMenuBinding menu = DialogProfileMenuBinding.inflate(getLayoutInflater());
        BottomSheetDialog dlg = new BottomSheetDialog(requireContext());
        dlg.setContentView(menu.getRoot());
        Dialogs.apply(dlg);

        menu.menuEdit.setOnClickListener(v -> {
            dlg.dismiss();
            store.setActive(p.id);
            if (getActivity() instanceof Host) {
                ((Host) getActivity()).openProfileEditor(p.id);
            }
        });
        menu.menuRename.setOnClickListener(v -> {
            dlg.dismiss();
            showRenameDialog(p);
        });
        menu.menuCopy.setOnClickListener(v -> {
            dlg.dismiss();
            ScheduleProfile copy = store.createCopy(p.name + " 副本", p.id);
            snack("已复制为「" + copy.name + "」并切换");
        });
        menu.menuDelete.setOnClickListener(v -> {
            dlg.dismiss();
            confirmDelete(p);
        });
        dlg.show();
    }

    /** 0 空白 / 1 复制一班 / 2 复制二班。 */
    private int createMode;

    private void showCreateDialog() {
        DialogCreateProfileBinding dlg = DialogCreateProfileBinding.inflate(getLayoutInflater());
        createMode = 0;

        View.OnClickListener select = v -> {
            int mode;
            if (v == dlg.rowBlank) {
                mode = 0;
            } else if (v == dlg.rowClass1) {
                mode = 1;
            } else {
                mode = 2;
            }
            createMode = mode;
            dlg.radioBlank.setChecked(mode == 0);
            dlg.radioClass1.setChecked(mode == 1);
            dlg.radioClass2.setChecked(mode == 2);
            styleCreateRow(dlg.rowBlank, mode == 0);
            styleCreateRow(dlg.rowClass1, mode == 1);
            styleCreateRow(dlg.rowClass2, mode == 2);
        };
        dlg.rowBlank.setOnClickListener(select);
        dlg.rowClass1.setOnClickListener(select);
        dlg.rowClass2.setOnClickListener(select);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dlg.getRoot())
                .setPositiveButton("创建", (d, which) -> {
                    String text = dlg.editName.getText() == null
                            ? "" : dlg.editName.getText().toString().trim();
                    if (createMode == 0) {
                        String name = text.isEmpty() ? "空白课程表" : text;
                        ScheduleProfile p = store.createBlank(name);
                        snack("已创建空白配置「" + p.name + "」，可在三点菜单中编辑课程");
                    } else {
                        String sourceName = createMode == 1
                                ? ScheduleRepository.DEFAULT_PROFILE_NAME
                                : ScheduleRepository.CLASS2_PROFILE_NAME;
                        String sourceId = createMode == 1
                                ? SettingsStore.BUILTIN_ID
                                : SettingsStore.BUILTIN_CLASS2_ID;
                        String name = text.isEmpty() ? sourceName + " 副本" : text;
                        ScheduleProfile p = store.createFromBuiltin(name, sourceId);
                        snack("已复制「" + sourceName + "」为「" + p.name + "」");
                        // 复制出来的自定义配置直接进入编辑
                        if (getActivity() instanceof Host) {
                            ((Host) getActivity()).openProfileEditor(p.id);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        Dialogs.apply(dialog);
    }

    private void styleCreateRow(MaterialCardView row, boolean selected) {
        row.setStrokeWidth(dp(selected ? 1.6f : 1f));
        row.setStrokeColor(themeColor(selected
                ? com.google.android.material.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOutline));
    }

    private int themeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private void showRenameDialog(ScheduleProfile p) {
        DialogEditTextBinding dlg = DialogEditTextBinding.inflate(getLayoutInflater());
        dlg.editInput.setText(p.name);
        if (dlg.editInput.getText() != null) {
            dlg.editInput.setSelection(dlg.editInput.getText().length());
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("重命名配置")
                .setView(dlg.getRoot())
                .setPositiveButton("保存", (d, which) -> {
                    String name = nameOr(dlg, p.name);
                    store.renameProfile(p.id, name);
                    snack("已重命名为「" + name + "」");
                })
                .setNegativeButton("取消", null)
                .show();
        Dialogs.apply(dialog);
    }

    private void confirmDelete(ScheduleProfile p) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除配置")
                .setMessage("确定删除「" + p.name + "」吗？此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (store.deleteProfile(p.id)) {
                        snack("已删除「" + p.name + "」");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String nameOr(DialogEditTextBinding dlg, String fallback) {
        String s = dlg.editInput.getText() == null ? "" : dlg.editInput.getText().toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private void snack(String msg) {
        if (b != null) {
            Snackbar.make(b.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
