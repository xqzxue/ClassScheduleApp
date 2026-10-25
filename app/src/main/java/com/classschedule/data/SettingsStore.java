package com.classschedule.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全局设置与课程表配置的持久化中心（单例）。
 * 配置分两类：
 * 1. 内置默认配置（BUILTIN_ID）：始终由 ScheduleRepository 生成，只读、不可删改；
 * 2. 自定义配置：用户新建 / 复制，可编辑、重命名、删除，持久化在本机。
 */
public class SettingsStore {

    private static final String PREFS = "schedule_settings";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "active_id";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_SECONDS = "show_seconds";
    private static final String KEY_SCHEME = "color_scheme";
    private static final String KEY_PURE_BLACK = "pure_black";

    /** 内置默认配置固定 ID（高二一班）。 */
    public static final String BUILTIN_ID = "builtin_default";
    /** 内置默认配置固定 ID（高二二班）。 */
    public static final String BUILTIN_CLASS2_ID = "builtin_class2";
    /** 旧版本默认配置 ID，迁移时丢弃。 */
    private static final String LEGACY_DEFAULT_ID = "p_default";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static SettingsStore instance;

    private final SharedPreferences sp;
    /** 仅保存自定义配置。 */
    private final List<ScheduleProfile> profiles = new ArrayList<>();
    private String activeId;

    public int themeMode = THEME_SYSTEM;
    public boolean showSeconds = true;
    /** 配色方案，见 ui.ThemeHelper。 */
    public int colorScheme = 1; // 默认青绿，避免低版本动态取色发灰
    /** 深色模式下使用纯黑（AMOLED）背景。 */
    public boolean pureBlack = false;

    /** 数据变化观察者。 */
    public interface ChangeObserver {
        default void onProfilesChanged() {
        }

        default void onSettingsChanged() {
        }
    }

    private final List<ChangeObserver> observers = new ArrayList<>();

    private SettingsStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    /** 必须在 Activity onCreate 前初始化（会应用夜间模式）。 */
    public static synchronized SettingsStore init(Context context) {
        if (instance == null) {
            instance = new SettingsStore(context);
        }
        instance.applyNightMode();
        return instance;
    }

    public static synchronized SettingsStore get() {
        if (instance == null) {
            throw new IllegalStateException("SettingsStore 尚未初始化");
        }
        return instance;
    }

    // ---------- 观察者 ----------

    public void register(ChangeObserver o) {
        if (!observers.contains(o)) {
            observers.add(o);
        }
    }

    public void unregister(ChangeObserver o) {
        observers.remove(o);
    }

    private void fireProfilesChanged() {
        for (ChangeObserver o : new ArrayList<>(observers)) {
            o.onProfilesChanged();
        }
    }

    private void fireSettingsChanged() {
        for (ChangeObserver o : new ArrayList<>(observers)) {
            o.onSettingsChanged();
        }
    }

    // ---------- 读写 ----------

    private void load() {
        themeMode = sp.getInt(KEY_THEME, THEME_SYSTEM);
        showSeconds = sp.getBoolean(KEY_SECONDS, true);
        colorScheme = sp.getInt(KEY_SCHEME, 1);
        if (colorScheme <= 0) { // 已废弃的“动态取色(0)”统一回落到青绿
            colorScheme = 1;
        }
        pureBlack = sp.getBoolean(KEY_PURE_BLACK, false);

        profiles.clear();
        String raw = sp.getString(KEY_PROFILES, null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    ScheduleProfile p = ScheduleProfile.fromJson(arr.getJSONObject(i));
                    // 内置课表与旧版本默认表不作为可编辑配置保留
                    if (!LEGACY_DEFAULT_ID.equals(p.id) && !isBuiltinId(p.id)) {
                        profiles.add(p);
                    }
                }
            } catch (Exception ignored) {
                profiles.clear();
            }
        }
        activeId = sp.getString(KEY_ACTIVE, null);
        // 旧版默认表 / 失效 ID 统一回落到内置默认配置
        if (activeId == null || LEGACY_DEFAULT_ID.equals(activeId) || find(activeId) == null) {
            activeId = BUILTIN_ID;
        }
        persistProfiles();
    }

    private void persistProfiles() {
        JSONArray arr = new JSONArray();
        try {
            for (ScheduleProfile p : profiles) {
                arr.put(p.toJson());
            }
        } catch (Exception ignored) {
        }
        sp.edit()
                .putString(KEY_PROFILES, arr.toString())
                .putString(KEY_ACTIVE, activeId)
                .apply();
    }

    // ---------- 配置查询 ----------

    /** 内置默认配置（高二一班，每次返回全新副本，避免外部误改）。 */
    public ScheduleProfile builtinProfile() {
        return ScheduleProfile.defaultProfile(BUILTIN_ID);
    }

    /** 全部内置只读配置（高二一班、高二二班）。 */
    public List<ScheduleProfile> builtinProfiles() {
        List<ScheduleProfile> list = new ArrayList<>();
        list.add(ScheduleProfile.defaultProfile(BUILTIN_ID));
        list.add(ScheduleProfile.defaultProfile(BUILTIN_CLASS2_ID));
        return list;
    }

    public boolean isBuiltin(String id) {
        return isBuiltinId(id);
    }

    /** 静态判定：是否为内置课表 ID。 */
    public static boolean isBuiltinId(String id) {
        return BUILTIN_ID.equals(id) || BUILTIN_CLASS2_ID.equals(id);
    }

    /** 仅自定义配置。 */
    public List<ScheduleProfile> customProfiles() {
        return new ArrayList<>(profiles);
    }

    public ScheduleProfile find(String id) {
        if (BUILTIN_ID.equals(id) || BUILTIN_CLASS2_ID.equals(id)) {
            return ScheduleProfile.defaultProfile(id);
        }
        for (ScheduleProfile p : profiles) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    public ScheduleProfile active() {
        ScheduleProfile p = find(activeId);
        return p != null ? p : builtinProfile();
    }

    public boolean isActive(String id) {
        return active().id.equals(id);
    }

    public WeekTable activeWeekTable() {
        return new WeekTable(active());
    }

    public void setActive(String id) {
        if (find(id) == null || id.equals(activeId)) {
            return;
        }
        activeId = id;
        persistProfiles();
        fireProfilesChanged();
    }

    private String newId() {
        return "p_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 空白自定义配置。 */
    public ScheduleProfile createBlank(String name) {
        ScheduleProfile p = ScheduleProfile.blank(newId(), name);
        addProfile(p);
        return p;
    }

    /** 复制内置默认配置（高二一班）为自定义配置。 */
    public ScheduleProfile createFromDefault(String name) {
        return createFromBuiltin(name, BUILTIN_ID);
    }

    /** 复制指定内置课表为自定义配置。 */
    public ScheduleProfile createFromBuiltin(String name, String builtinId) {
        ScheduleProfile src = ScheduleProfile.defaultProfile(
                isBuiltinId(builtinId) ? builtinId : BUILTIN_ID);
        ScheduleProfile p = src.copy(newId(), name);
        addProfile(p);
        return p;
    }

    /** 复制已有配置（内置或自定义均可）。 */
    public ScheduleProfile createCopy(String name, String copyFromId) {
        ScheduleProfile src = find(copyFromId);
        ScheduleProfile p = src != null ? src.copy(newId(), name)
                : ScheduleProfile.blank(newId(), name);
        addProfile(p);
        return p;
    }

    private void addProfile(ScheduleProfile p) {
        profiles.add(p);
        activeId = p.id;
        persistProfiles();
        fireProfilesChanged();
    }

    public void renameProfile(String id, String newName) {
        if (isBuiltin(id)) {
            return;
        }
        ScheduleProfile p = find(id);
        if (p != null) {
            p.name = newName;
            persistProfiles();
            fireProfilesChanged();
        }
    }

    /** 删除自定义配置；内置配置不可删。删的是当前配置则切回内置默认。 */
    public boolean deleteProfile(String id) {
        if (isBuiltin(id)) {
            return false;
        }
        ScheduleProfile target = find(id);
        if (target == null) {
            return false;
        }
        profiles.remove(target);
        if (id.equals(activeId)) {
            activeId = BUILTIN_ID;
        }
        persistProfiles();
        fireProfilesChanged();
        return true;
    }

    // ---------- 课程编辑（仅自定义配置） ----------

    /** 修改某节课程名（留空记为“无”）。 */
    public void updateCourseName(String id, int dayIndex, int periodIndex, String name) {
        if (isBuiltin(id)) {
            return;
        }
        ScheduleProfile p = find(id);
        if (p == null || dayIndex < 0 || dayIndex >= p.days.length) {
            return;
        }
        DayPlan day = p.days[dayIndex];
        if (periodIndex < 0 || periodIndex >= day.periods.size()) {
            return;
        }
        String value = (name == null || name.trim().isEmpty()) ? "无" : name.trim();
        day.periods.get(periodIndex).name = value;
        persistProfiles();
        fireProfilesChanged();
    }

    /** 新增一节课（第 13、14 节……），返回新节序号；时间非法返回 -1。 */
    public int addPeriod(String id, int dayIndex, String name, String start, String end) {
        if (isBuiltin(id)) {
            return -1;
        }
        ScheduleProfile p = find(id);
        if (p == null || dayIndex < 0 || dayIndex >= p.days.length || !validTime(start, end)) {
            return -1;
        }
        String value = (name == null || name.trim().isEmpty()) ? "无" : name.trim();
        DayPlan day = p.days[dayIndex];
        day.periods.add(new PeriodItem(value, start, end, true));
        persistProfiles();
        fireProfilesChanged();
        return day.periods.size() - 1;
    }

    /** 修改一整节（用户自定义节，允许改时间）。 */
    public boolean updatePeriod(String id, int dayIndex, int periodIndex,
                                String name, String start, String end) {
        if (isBuiltin(id)) {
            return false;
        }
        ScheduleProfile p = find(id);
        if (p == null || dayIndex < 0 || dayIndex >= p.days.length || !validTime(start, end)) {
            return false;
        }
        DayPlan day = p.days[dayIndex];
        if (periodIndex < 0 || periodIndex >= day.periods.size()) {
            return false;
        }
        PeriodItem item = day.periods.get(periodIndex);
        item.name = (name == null || name.trim().isEmpty()) ? "无" : name.trim();
        item.start = start;
        item.end = end;
        persistProfiles();
        fireProfilesChanged();
        return true;
    }

    /** 删除用户新增的某节。 */
    public boolean removePeriod(String id, int dayIndex, int periodIndex) {
        if (isBuiltin(id)) {
            return false;
        }
        ScheduleProfile p = find(id);
        if (p == null || dayIndex < 0 || dayIndex >= p.days.length) {
            return false;
        }
        DayPlan day = p.days[dayIndex];
        if (periodIndex < 0 || periodIndex >= day.periods.size()) {
            return false;
        }
        if (!day.periods.get(periodIndex).custom) {
            return false;
        }
        day.periods.remove(periodIndex);
        persistProfiles();
        fireProfilesChanged();
        return true;
    }

    private boolean validTime(String start, String end) {
        if (start == null || end == null) {
            return false;
        }
        try {
            return !java.time.LocalTime.parse(start).isAfter(java.time.LocalTime.parse(end))
                    && !start.equals(end);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 外观设置 ----------

    public void setThemeMode(int mode) {
        themeMode = mode;
        sp.edit().putInt(KEY_THEME, mode).apply();
        applyNightMode();
        fireSettingsChanged();
    }

    public void applyNightMode() {
        int nightMode;
        switch (themeMode) {
            case THEME_LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case THEME_DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    public void setShowSeconds(boolean show) {
        showSeconds = show;
        sp.edit().putBoolean(KEY_SECONDS, show).apply();
        fireSettingsChanged();
    }

    /** 配色方案（1 青绿，2 蓝，3 紫，4 橙，5 粉）。 */
    public void setColorScheme(int scheme) {
        colorScheme = scheme;
        sp.edit().putInt(KEY_SCHEME, scheme).apply();
        fireSettingsChanged();
    }

    /** 深色模式下是否启用纯黑背景。 */
    public void setPureBlack(boolean black) {
        pureBlack = black;
        sp.edit().putBoolean(KEY_PURE_BLACK, black).apply();
        fireSettingsChanged();
    }
}
