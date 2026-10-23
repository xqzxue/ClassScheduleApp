package com.classschedule.engine;

import com.classschedule.data.CoursePeriod;
import com.classschedule.data.DaySchedule;
import com.classschedule.data.WeekTable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 根据当前时间与指定周课表推导课程状态：当前课、下节课、各类倒计时。
 * 支持周一至周日，无课日（如周六）按休息日处理。
 */
public final class ScheduleEngine {

    private ScheduleEngine() {
    }

    public static ScheduleStatus at(LocalDateTime time, WeekTable table) {
        DayOfWeek dow = time.getDayOfWeek();
        DaySchedule today = table.of(dow);
        LocalDate date = time.toLocalDate();
        LocalTime t = time.toLocalTime();

        // —— 当天没有任何真正的课程：休息日 ——
        if (today == null || !today.hasClass()) {
            DaySchedule nextDay = table.nextSchoolDay(dow);
            CoursePeriod next = nextDay == null ? null : nextDay.firstClass();
            Duration toNext = Duration.ZERO;
            if (nextDay != null && next != null) {
                LocalDate nextDate = date.plusDays(daysUntil(dow, nextDay.day));
                toNext = Duration.between(time, nextDate.atTime(next.start));
            }
            return new ScheduleStatus(
                    ScheduleStatus.State.WEEKEND, null, null,
                    next, nextDay,
                    Duration.ZERO, toNext,
                    Duration.ZERO, weekendCountdown(time, table, dow), toNext);
        }

        // —— 上学日 ——
        CoursePeriod current = null;
        CoursePeriod upcoming = null;
        for (CoursePeriod p : today.periods) {
            if (!t.isBefore(p.start) && t.isBefore(p.end)) {
                current = p;
            }
            // “下一节课”跳过“无”的时间段
            if (t.isBefore(p.start) && upcoming == null && !p.isFree()) {
                upcoming = p;
            }
        }

        ScheduleStatus.State state;
        Duration untilCurrentEnd = Duration.ZERO;
        if (current != null) {
            state = ScheduleStatus.State.IN_CLASS;
            untilCurrentEnd = Duration.between(time, date.atTime(current.end));
        } else if (t.isBefore(today.first().start)) {
            state = ScheduleStatus.State.BEFORE_SCHOOL;
        } else if (upcoming != null) {
            state = ScheduleStatus.State.BREAK;
        } else {
            state = ScheduleStatus.State.AFTER_SCHOOL;
        }

        // 下一节课：今天剩余的，否则取下一个上学日第一节真正的课
        CoursePeriod next;
        DaySchedule nextDay;
        if (upcoming != null) {
            next = upcoming;
            nextDay = today;
        } else {
            nextDay = table.nextSchoolDay(dow);
            next = nextDay == null ? null : nextDay.firstClass();
        }
        Duration untilNextStart = Duration.ZERO;
        if (next != null) {
            LocalDate nextDate = nextDay.day == dow ? date
                    : date.plusDays(daysUntil(dow, nextDay.day));
            untilNextStart = Duration.between(time, nextDate.atTime(next.start));
        }

        // 距今天放学（最后一节真正的课结束）
        CoursePeriod lastClass = today.lastClass();
        Duration untilSchoolEnd = (lastClass != null && t.isBefore(lastClass.end))
                ? Duration.between(time, date.atTime(lastClass.end))
                : Duration.ZERO;

        // 距下一个上学日第一节（返校）
        Duration untilBack = Duration.ZERO;
        DaySchedule backDay = table.nextSchoolDay(dow);
        if (backDay != null && backDay.firstClass() != null) {
            LocalDate backDate = date.plusDays(daysUntil(dow, backDay.day));
            LocalDateTime backTime = backDate.atTime(backDay.firstClass().start);
            if (backTime.isAfter(time)) {
                untilBack = Duration.between(time, backTime);
            }
        }

        return new ScheduleStatus(state, today, current, next, nextDay,
                untilCurrentEnd, untilNextStart, untilSchoolEnd,
                weekendCountdown(time, table, dow), untilBack);
    }

    /** 距本周五最后一节课结束（周末开始）；已过则为 ZERO。 */
    private static Duration weekendCountdown(LocalDateTime time, WeekTable table, DayOfWeek dow) {
        DaySchedule friday = table.of(DayOfWeek.FRIDAY);
        if (friday == null || friday.lastClass() == null) {
            return Duration.ZERO;
        }
        LocalDate fridayDate = time.toLocalDate()
                .plusDays(DayOfWeek.FRIDAY.getValue() - dow.getValue());
        LocalDateTime weekendStart = fridayDate.atTime(friday.lastClass().end);
        return weekendStart.isAfter(time) ? Duration.between(time, weekendStart) : Duration.ZERO;
    }

    /** 从 from 到目标星期的天数（1~7）。 */
    private static long daysUntil(DayOfWeek from, DayOfWeek target) {
        long d = (target.getValue() - from.getValue() + 7) % 7;
        return d == 0 ? 7 : d;
    }
}
