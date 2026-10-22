package com.classschedule.data;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 由一份 ScheduleProfile 构建出的一周可查询课表（周一至周日，含无课日）。
 */
public class WeekTable {

    private final Map<DayOfWeek, DaySchedule> table = new EnumMap<>(DayOfWeek.class);
    private static final DayOfWeek[] ALL_DAYS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    };

    public WeekTable(ScheduleProfile profile) {
        for (int d = 0; d < ALL_DAYS.length; d++) {
            List<CoursePeriod> list = new ArrayList<>();
            DayPlan plan = profile.days[d];
            if (plan != null) {
                for (PeriodItem item : plan.periods) {
                    // 双保险：非法时间的节次直接跳过，避免运行时 LocalTime.parse 崩溃
                    if (!item.hasValidTime()) {
                        continue;
                    }
                    list.add(new CoursePeriod(list.size() + 1, item.name,
                            LocalTime.parse(item.start), LocalTime.parse(item.end)));
                }
            }
            table.put(ALL_DAYS[d], new DaySchedule(ALL_DAYS[d], list));
        }
    }

    /** 取指定星期的课表（无课日返回空的 DaySchedule，不为 null）。 */
    public DaySchedule of(DayOfWeek day) {
        return table.get(day);
    }

    /** 周一到周日顺序返回。 */
    public List<DaySchedule> week() {
        return Arrays.asList(
                table.get(DayOfWeek.MONDAY),
                table.get(DayOfWeek.TUESDAY),
                table.get(DayOfWeek.WEDNESDAY),
                table.get(DayOfWeek.THURSDAY),
                table.get(DayOfWeek.FRIDAY),
                table.get(DayOfWeek.SATURDAY),
                table.get(DayOfWeek.SUNDAY));
    }

    /** 下一个有真正课程的上学日（跳过无课日）。 */
    public DaySchedule nextSchoolDay(DayOfWeek day) {
        DayOfWeek next = day.plus(1);
        int guard = 0;
        while (guard++ < 7) {
            DaySchedule candidate = table.get(next);
            if (candidate != null && candidate.hasClass()) {
                return candidate;
            }
            next = next.plus(1);
        }
        return null;
    }
}
