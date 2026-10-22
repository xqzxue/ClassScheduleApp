package com.classschedule.data;

import java.time.DayOfWeek;
import java.util.List;

/**
 * 某一天的完整课程表（节数可变，可能为空，例如周六）。
 */
public class DaySchedule {

    public final DayOfWeek day;
    public final List<CoursePeriod> periods;

    public DaySchedule(DayOfWeek day, List<CoursePeriod> periods) {
        this.day = day;
        this.periods = periods;
    }

    public boolean isEmpty() {
        return periods.isEmpty();
    }

    /** 是否存在真正的课（非“无”）。 */
    public boolean hasClass() {
        for (CoursePeriod p : periods) {
            if (!p.isFree()) {
                return true;
            }
        }
        return false;
    }

    public CoursePeriod first() {
        return periods.isEmpty() ? null : periods.get(0);
    }

    public CoursePeriod last() {
        return periods.isEmpty() ? null : periods.get(periods.size() - 1);
    }

    /** 第一节真正的课。 */
    public CoursePeriod firstClass() {
        for (CoursePeriod p : periods) {
            if (!p.isFree()) {
                return p;
            }
        }
        return null;
    }

    /** 最后一节真正的课。 */
    public CoursePeriod lastClass() {
        for (int i = periods.size() - 1; i >= 0; i--) {
            CoursePeriod p = periods.get(i);
            if (!p.isFree()) {
                return p;
            }
        }
        return null;
    }
}
