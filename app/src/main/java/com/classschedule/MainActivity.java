package com.classschedule;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.classschedule.data.SettingsStore;
import com.classschedule.databinding.ActivityMainBinding;
import com.classschedule.ui.OverviewFragment;
import com.classschedule.ui.ProfileEditFragment;
import com.classschedule.ui.ScheduleFragment;
import com.classschedule.ui.SettingsFragment;
import com.classschedule.ui.ThemeHelper;

/**
 * 单 Activity：底部导航在「概括 / 课表 / 配置」三个 Fragment 间切换，
 * 课程编辑器以覆盖 Fragment + 返回栈的方式打开。
 */
public class MainActivity extends androidx.appcompat.app.AppCompatActivity
        implements SettingsFragment.Host {

    private static final String KEY_ACTIVE_TAB = "active_bottom_tab";

    /** 主题/深色模式重建时用于交叉淡入的上一帧截图，重建后立即回收。 */
    private static Bitmap crossfadeBitmap;

    private ActivityMainBinding binding;
    private OverviewFragment overviewFragment;
    private ScheduleFragment scheduleFragment;
    private SettingsFragment settingsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsStore settings = SettingsStore.init(getApplicationContext()); // 先恢复夜间模式设置
        // 在 super.onCreate 与 inflate 之前应用用户选择的配色方案
        boolean night = ThemeHelper.isNightEffective(this, settings.themeMode);
        setTheme(ThemeHelper.themeRes(settings.colorScheme, night));
        if (night && settings.pureBlack) {
            // 纯黑模式：深色主题上再叠加黑色表面
            getTheme().applyStyle(R.style.ThemeOverlay_ClassSchedule_PureBlack, true);
        }
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 底部导航栏避开系统手势/导航条
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), bars.bottom + dp(6));
            return insets;
        });

        // 先注册切换监听，重建恢复时通过 setSelectedItemId 回到原来的 Tab
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_overview) {
                switchTo(overviewFragment);
                return true;
            } else if (id == R.id.nav_schedule) {
                switchTo(scheduleFragment);
                return true;
            } else if (id == R.id.nav_settings) {
                switchTo(settingsFragment);
                return true;
            }
            return false;
        });

        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // 横屏：隐藏底部导航与分隔线，全屏显示横屏专属「桌面台」
        if (landscape) {
            binding.bottomNav.setVisibility(View.GONE);
            binding.navDivider.setVisibility(View.GONE);
        }

        if (savedInstanceState == null) {
            overviewFragment = new OverviewFragment();
            scheduleFragment = new ScheduleFragment();
            settingsFragment = new SettingsFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.add(R.id.nav_host_container, settingsFragment, "settings").hide(settingsFragment);
            ft.add(R.id.nav_host_container, scheduleFragment, "schedule").hide(scheduleFragment);
            ft.add(R.id.nav_host_container, overviewFragment, "overview");
            ft.commit();
            activeFragment = overviewFragment;
            if (!landscape) {
                binding.bottomNav.setSelectedItemId(R.id.nav_overview);
            }
        } else {
            // Fragment 由系统恢复
            overviewFragment = (OverviewFragment) getSupportFragmentManager().findFragmentByTag("overview");
            scheduleFragment = (ScheduleFragment) getSupportFragmentManager().findFragmentByTag("schedule");
            settingsFragment = (SettingsFragment) getSupportFragmentManager().findFragmentByTag("settings");
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            // 横屏只保留桌面台；竖屏先以概括页为基线，再选中重建前所在的 Tab
            if (scheduleFragment != null) {
                ft.hide(scheduleFragment);
            }
            if (settingsFragment != null) {
                ft.hide(settingsFragment);
            }
            if (overviewFragment != null) {
                ft.show(overviewFragment);
            }
            ft.commitNowAllowingStateLoss();
            activeFragment = overviewFragment;
            if (!landscape) {
                int savedTab = savedInstanceState.getInt(KEY_ACTIVE_TAB, R.id.nav_overview);
                binding.bottomNav.setSelectedItemId(savedTab);
            }
        }

        // 返回键：编辑器优先出栈；非概括页先回到概括页
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                    return;
                }
                if (activeFragment != overviewFragment) {
                    binding.bottomNav.setSelectedItemId(R.id.nav_overview);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        installCrossfadeOverlay();
    }

    @Override
    protected void onSaveInstanceState(@NonNull android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putInt(KEY_ACTIVE_TAB, binding.bottomNav.getSelectedItemId());
        }
    }

    @Override
    public void openProfileEditor(String profileId) {
        ProfileEditFragment editor = ProfileEditFragment.newInstance(profileId);
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .add(R.id.nav_host_container, editor, "editor")
                .addToBackStack("editor")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    /** 仅截取当前画面（配合系统自动重建，如切换深色模式）。 */
    @Override
    public void captureForRecreate() {
        captureSnapshot();
    }

    /** 截取当前画面后重建（如切换主题配色、纯黑开关），重建时做交叉淡入。 */
    @Override
    public void smoothRecreate() {
        captureSnapshot();
        recreate();
    }

    /** 把当前 DecorView 画成一张静态图，供重建后的新界面从它淡入。 */
    private void captureSnapshot() {
        try {
            View decor = getWindow().getDecorView();
            if (decor.getWidth() <= 0 || decor.getHeight() <= 0) {
                return;
            }
            recycleCrossfade();
            crossfadeBitmap = Bitmap.createBitmap(
                    decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(crossfadeBitmap));
        } catch (Exception ignored) {
            crossfadeBitmap = null;
        }
    }

    /** 重建完成后，在内容层最上方盖上旧画面截图并淡出，形成平滑渐变。 */
    private void installCrossfadeOverlay() {
        if (crossfadeBitmap == null) {
            return;
        }
        final Bitmap snapshot = crossfadeBitmap;
        crossfadeBitmap = null;
        FrameLayout content = findViewById(android.R.id.content);
        if (content == null) {
            snapshot.recycle();
            return;
        }
        final ImageView overlay = new ImageView(this);
        overlay.setImageBitmap(snapshot);
        overlay.setScaleType(ImageView.ScaleType.FIT_XY);
        overlay.setClickable(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        content.addView(overlay, lp);
        overlay.setAlpha(1f);
        overlay.animate().alpha(0f).setDuration(280).withEndAction(() -> {
            content.removeView(overlay);
            snapshot.recycle();
        }).start();
    }

    private void recycleCrossfade() {
        if (crossfadeBitmap != null && !crossfadeBitmap.isRecycled()) {
            crossfadeBitmap.recycle();
        }
        crossfadeBitmap = null;
    }

    private void switchTo(Fragment target) {
        if (target == null || target == activeFragment) {
            return;
        }
        // 若编辑器在栈上，先弹出
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStackImmediate();
        }
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
        activeFragment = target;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
