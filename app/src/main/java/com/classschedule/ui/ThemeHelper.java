package com.classschedule.ui;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.StyleRes;

import com.classschedule.R;
import com.classschedule.data.SettingsStore;

/**
 * 配色方案与主题样式映射。
 * 内部 scheme 取值固定为 1..5（青绿/海洋蓝/紫罗兰/活力橙/樱花粉），
 * 不再提供“动态取色”，旧版本若存过 0 一律按青绿处理。
 */
public final class ThemeHelper {

    public static final int SCHEME_GREEN = 1;
    public static final int SCHEME_BLUE = 2;
    public static final int SCHEME_PURPLE = 3;
    public static final int SCHEME_ORANGE = 4;
    public static final int SCHEME_PINK = 5;

    /** 选择对话框中展示的名称（顺序与 DIALOG_VALUES 一一对应）。 */
    public static final String[] DIALOG_NAMES = {
            "青绿（默认）",
            "海洋蓝",
            "紫罗兰",
            "活力橙",
            "樱花粉"
    };

    /** 对话框下标对应的内部 scheme 值。 */
    public static final int[] DIALOG_VALUES = {
            SCHEME_GREEN, SCHEME_BLUE, SCHEME_PURPLE, SCHEME_ORANGE, SCHEME_PINK
    };

    private ThemeHelper() {
    }

    /** 内部 scheme 值对应的中文名（用于设置页副标题）。 */
    public static String schemeName(int scheme) {
        for (int i = 0; i < DIALOG_VALUES.length; i++) {
            if (DIALOG_VALUES[i] == scheme) {
                return DIALOG_NAMES[i];
            }
        }
        return DIALOG_NAMES[0];
    }

    /** 当前 scheme 在选择对话框中的选中下标。 */
    public static int dialogIndex(int scheme) {
        for (int i = 0; i < DIALOG_VALUES.length; i++) {
            if (DIALOG_VALUES[i] == scheme) {
                return i;
            }
        }
        return 0;
    }

    /** 根据深色模式设置推导当前是否为深色（跟随系统时读系统配置）。 */
    public static boolean isNightEffective(Context context, int themeMode) {
        if (themeMode == SettingsStore.THEME_LIGHT) {
            return false;
        }
        if (themeMode == SettingsStore.THEME_DARK) {
            return true;
        }
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 按配色方案与当前是否深色返回对应主题样式。 */
    @StyleRes
    public static int themeRes(int scheme, boolean night) {
        switch (scheme) {
            case SCHEME_BLUE:
                return night ? R.style.Theme_ClassSchedule_Blue_Dark
                        : R.style.Theme_ClassSchedule_Blue_Light;
            case SCHEME_PURPLE:
                return night ? R.style.Theme_ClassSchedule_Purple_Dark
                        : R.style.Theme_ClassSchedule_Purple_Light;
            case SCHEME_ORANGE:
                return night ? R.style.Theme_ClassSchedule_Orange_Dark
                        : R.style.Theme_ClassSchedule_Orange_Light;
            case SCHEME_PINK:
                return night ? R.style.Theme_ClassSchedule_Pink_Dark
                        : R.style.Theme_ClassSchedule_Pink_Light;
            case SCHEME_GREEN:
            default: // 含已废弃的 0（动态取色），统一回落到青绿
                return night ? R.style.Theme_ClassSchedule_Green_Dark
                        : R.style.Theme_ClassSchedule_Green_Light;
        }
    }
}
