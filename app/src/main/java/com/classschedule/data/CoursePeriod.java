package com.classschedule.data;

import java.time.LocalTime;

/**
 * 一节课（或一个固定时间段，如晚自修、自习）。
 */
public class CoursePeriod {

    public final int period;          // 第几节，从 1 开始
    public final String name;         // 课程名称
    public final LocalTime start;     // 开始时间
    public final LocalTime end;       // 结束时间

    public CoursePeriod(int period, String name, LocalTime start, LocalTime end) {
        this.period = period;
        this.name = name;
        this.start = start;
        this.end = end;
    }

    /** 是否为空白/无课时间段（名称为“无”）。 */
    public boolean isFree() {
        return name == null || name.trim().equals("无");
    }

    /** 展示给用户的名称。 */
    public String displayName() {
        return isFree() ? "无课（自习）" : name;
    }

    public String getTimeText() {
        return TimeFmt.hm(start) + " - " + TimeFmt.hm(end);
    }
}
