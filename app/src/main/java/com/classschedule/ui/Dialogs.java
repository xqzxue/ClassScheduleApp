package com.classschedule.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Window;
import android.view.WindowManager;

/**
 * 弹窗统一尺寸约束：竖屏宽度不超屏宽减边距、横屏收窄并限制最大高度，
 * 保证自定义 setView 的弹窗（重命名 / 新建 / 切换课程表 / 菜单）
 * 不会出现输入框溢出、按钮被裁、横屏显示不全的问题。
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** 竖屏：屏宽 - 48dp（最大 520dp）；横屏：屏宽 56%（夹在 320dp ~ 520dp）。 */
    public static int dialogWidthPx(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density;
        int screenW = dm.widthPixels;
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int w;
        if (landscape) {
            w = Math.round(screenW * 0.56f);
            w = Math.max(w, dp(density, 320));
            w = Math.min(w, dp(density, 520));
        } else {
            w = Math.min(screenW - dp(density, 48), dp(density, 520));
        }
        return Math.max(w, dp(density, 280));
    }

    /**
     * 约束弹窗窗口：宽度按 {@link #dialogWidthPx}；横屏时高度限为屏高 86%（内容内部滚动），
     * 竖屏高度自适应。
     */
    public static void apply(Dialog dialog) {
        if (dialog == null) {
            return;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        Context context = dialog.getContext();
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int w = dialogWidthPx(context);
        int h = landscape ? Math.round(dm.heightPixels * 0.86f)
                : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setLayout(w, h);
    }

    private static int dp(float density, int value) {
        return Math.round(value * density);
    }
}
