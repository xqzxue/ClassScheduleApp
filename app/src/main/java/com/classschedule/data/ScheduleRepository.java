package com.classschedule.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认时间段与内置课程表数据（首次启动时生成默认配置）。
 */
public final class ScheduleRepository {

    /** 内置默认配置名称。 */
    public static final String DEFAULT_PROFILE_NAME = "新侨中学高二一班";
    public static final String CLASS2_PROFILE_NAME = "新侨中学高二二班";

    /** 标准 12 个时间段的开始时间。 */
    public static final String[] STARTS = {
            "08:00", "09:00", "10:15", "11:10", "14:10", "15:05",
            "16:00", "16:45", "18:50", "20:00", "21:00", "21:45"
    };

    /** 标准 12 个时间段的结束时间。 */
    public static final String[] ENDS = {
            "08:45", "09:45", "11:00", "11:55", "14:55", "15:50",
            "16:45", "17:45", "19:50", "20:50", "21:45", "22:20"
    };

    public static final int STANDARD_PERIOD_COUNT = 12;
    public static final int DAY_COUNT = 7; // 周一至周日

    /** 周一至周五的标准课程；行序 0 周一 … 4 周五，每行 12 节。 */
    public static final String[][] DEFAULT_WEEK = {
            {
                    "语文", "历史", "数学", "英语", "生物", "政治",
                    "化学", "无", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "语文", "英语", "体育", "化学", "生物", "物理",
                    "数学", "数学/政治", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "物理", "数学", "英语", "通用技术", "信息技术", "化学",
                    "语文", "语文/地理", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "英语", "数学", "信息技术", "语文", "生物", "化学",
                    "历史", "英语/历史/生物", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "物理", "语文", "英语", "数学", "政治", "体育",
                    "班会", "物理/化学", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            }
    };

    /** 高二二班：周一至周五标准课程，每行 12 节。 */
    public static final String[][] CLASS2_WEEK = {
            {
                    "历史", "语文", "数学", "英语", "生物", "化学",
                    "政治", "无", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "英语", "生物", "化学", "体育", "物理", "语文",
                    "数学", "数学/政治", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "数学", "物理", "通用技术", "英语", "语文", "信息技术",
                    "化学", "语文/地理", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "数学", "英语", "语文", "信息技术", "化学", "历史",
                    "生物", "英语/历史/生物", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            },
            {
                    "语文", "物理", "数学", "英语", "体育", "政治",
                    "班会", "物理/化学", "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
            }
    };

    /** 周日晚自习：与周一至周五第 9~12 节时间一致。 */
    public static final String[] SUNDAY_EVENING = {
            "晚自修（一）", "晚自修（二）", "晚自修（三）", "晚自修（四）"
    };

    private ScheduleRepository() {
    }

    /** 由 12 个课程名构建一个标准教学日（使用统一时间段）。 */
    public static DayPlan standardDay(String[] names) {
        DayPlan plan = new DayPlan();
        for (int i = 0; i < STANDARD_PERIOD_COUNT; i++) {
            String name = i < names.length ? names[i] : "无";
            plan.periods.add(new PeriodItem(name, STARTS[i], ENDS[i]));
        }
        return plan;
    }

    /** 周日：仅 4 节晚自习，时间与工作日第 9~12 节一致。 */
    public static DayPlan sundayDay() {
        DayPlan plan = new DayPlan();
        for (int i = 0; i < SUNDAY_EVENING.length; i++) {
            int slot = 8 + i; // 第 9 节起
            plan.periods.add(new PeriodItem(SUNDAY_EVENING[i], STARTS[slot], ENDS[slot]));
        }
        return plan;
    }

    /** 空白的一天。 */
    public static DayPlan emptyDay() {
        return new DayPlan();
    }

    /** 生成一份全新的空白一周（7 天都没有课）。 */
    public static DayPlan[] blankWeek() {
        DayPlan[] days = new DayPlan[DAY_COUNT];
        for (int d = 0; d < DAY_COUNT; d++) {
            days[d] = emptyDay();
        }
        return days;
    }

    /** 高二一班默认一周：周一至周五标准课程，周六空，周日晚自习 4 节。 */
    public static DayPlan[] defaultWeek() {
        DayPlan[] days = new DayPlan[DAY_COUNT];
        for (int d = 0; d < 5; d++) {
            days[d] = ScheduleRepository.standardDay(DEFAULT_WEEK[d]);
        }
        days[5] = emptyDay();   // 周六
        days[6] = sundayDay();  // 周日
        return days;
    }

    /** 高二二班一周：周一至周五标准课程，周六空，周日晚自习 4 节。 */
    public static DayPlan[] class2Week() {
        DayPlan[] days = new DayPlan[DAY_COUNT];
        for (int d = 0; d < 5; d++) {
            days[d] = ScheduleRepository.standardDay(CLASS2_WEEK[d]);
        }
        days[5] = emptyDay();   // 周六
        days[6] = sundayDay();  // 周日
        return days;
    }

    /** 按内置 ID 生成对应内置课表（只读，每次返回全新副本）。 */
    public static ScheduleProfile builtinProfile(String id) {
        if (SettingsStore.BUILTIN_CLASS2_ID.equals(id)) {
            return new ScheduleProfile(id, CLASS2_PROFILE_NAME, class2Week());
        }
        return new ScheduleProfile(id, DEFAULT_PROFILE_NAME, defaultWeek());
    }

    /** 深拷贝一周。 */
    public static DayPlan[] copyWeek(DayPlan[] src) {
        DayPlan[] dst = new DayPlan[DAY_COUNT];
        for (int d = 0; d < DAY_COUNT; d++) {
            List<PeriodItem> items = new ArrayList<>();
            for (PeriodItem p : src[d].periods) {
                items.add(p.copy());
            }
            dst[d] = new DayPlan(items);
        }
        return dst;
    }
}
