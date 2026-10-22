package com.classschedule.engine;

import com.classschedule.data.CoursePeriod;
import com.classschedule.data.DaySchedule;

import java.time.Duration;

/**
 * 某一时刻的课程状态快照，由 ScheduleEngine 计算。
 */
public class ScheduleStatus {

    public enum State {
        /** 周末，无课。 */
        WEEKEND,
        /** 今天第一节课还没开始。 */
        BEFORE_SCHOOL,
        /** 正在上课（或处于晚自修等时间段内）。 */
        IN_CLASS,
        /** 两节课之间的课间。 */
        BREAK,
        /** 今天所有时间段均已结束。 */
        AFTER_SCHOOL
    }

    public final State state;
    public final DaySchedule today;          // 周末为 null
    public final CoursePeriod current;       // 仅 IN_CLASS 非空
    public final CoursePeriod next;          // 下一节课（可能是次日）
    public final DaySchedule nextDay;        // next 所属那天
    public final Duration untilCurrentEnd;   // IN_CLASS：距当前时间段结束
    public final Duration untilNextStart;    // 距 next 开始
    public final Duration untilSchoolEnd;    // 距今天最后一节课结束（已过为 ZERO）
    public final Duration untilWeekend;      // 距本周五最后一节课结束（周末中为 ZERO）
    public final Duration untilBackToSchool; // 距下周一第一节课开始

    public ScheduleStatus(State state, DaySchedule today, CoursePeriod current,
                          CoursePeriod next, DaySchedule nextDay,
                          Duration untilCurrentEnd, Duration untilNextStart,
                          Duration untilSchoolEnd, Duration untilWeekend,
                          Duration untilBackToSchool) {
        this.state = state;
        this.today = today;
        this.current = current;
        this.next = next;
        this.nextDay = nextDay;
        this.untilCurrentEnd = untilCurrentEnd;
        this.untilNextStart = untilNextStart;
        this.untilSchoolEnd = untilSchoolEnd;
        this.untilWeekend = untilWeekend;
        this.untilBackToSchool = untilBackToSchool;
    }

    /** 当前课程已进行的比例 0~1，仅 IN_CLASS 有效。 */
    public double currentProgress() {
        if (current == null || untilCurrentEnd == null) {
            return 0;
        }
        long total = java.time.Duration.between(current.start, current.end).getSeconds();
        long left = untilCurrentEnd.getSeconds();
        if (total <= 0) {
            return 0;
        }
        double p = 1.0 - (double) left / total;
        return Math.max(0, Math.min(1, p));
    }
}
