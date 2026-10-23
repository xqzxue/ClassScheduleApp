package com.classschedule.data;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 时间与时长的中文格式化工具。
 */
public final class TimeFmt {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private TimeFmt() {
    }

    public static String hm(LocalTime t) {
        return t.format(HM);
    }

    public static String weekdayName(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "星期一";
            case TUESDAY: return "星期二";
            case WEDNESDAY: return "星期三";
            case THURSDAY: return "星期四";
            case FRIDAY: return "星期五";
            case SATURDAY: return "星期六";
            default: return "星期日";
        }
    }

    public static String weekdayShort(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "周一";
            case TUESDAY: return "周二";
            case WEDNESDAY: return "周三";
            case THURSDAY: return "周四";
            case FRIDAY: return "周五";
            case SATURDAY: return "周六";
            default: return "周日";
        }
    }

    /** 1 小时以上：X小时Y分钟；否则 X分钟。 */
    public static String chineseDuration(Duration d) {
        if (d == null || d.isNegative() || d.isZero()) {
            return "0 分钟";
        }
        long totalMinutes = d.toMinutes();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return String.format(Locale.CHINA, "%d 小时 %d 分钟", hours, minutes);
        } else if (hours > 0) {
            return String.format(Locale.CHINA, "%d 小时", hours);
        } else {
            return String.format(Locale.CHINA, "%d 分钟", (int) Math.max(totalMinutes, 1));
        }
    }

    /**
     * 紧凑中文时长（无空格、无“钟”字）：X小时Y分 / X小时 / X分，
     * 用于横屏桌面台窄列统计。
     */
    public static String compactRounded(Duration d) {
        if (d == null || d.isNegative() || d.isZero()) {
            return "0分";
        }
        long totalMinutes = (d.getSeconds() + 59) / 60; // 向上取整
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return hours + "小时" + minutes + "分";
        } else if (hours > 0) {
            return hours + "小时";
        }
        return totalMinutes + "分";
    }

    /** 紧凑时间段：第2节·09:00-09:45（无多余空格）。 */
    public static String compactPeriod(int period, String timeText) {
        return "第" + period + "节·" + timeText.replace(" ", "");
    }

    /** 紧凑倒计时：HH:MM:SS（用于秒级刷新）。 */
    public static String hmsCountdown(Duration d) {
        if (d == null || d.isNegative()) {
            d = Duration.ZERO;
        }
        long total = d.getSeconds();
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return String.format(Locale.CHINA, "%02d:%02d:%02d", h, m, s);
    }

    /** 分秒倒计时：X分Y秒（不足 1 分钟也展示秒）。 */
    public static String minuteSecondCountdown(Duration d) {
        if (d == null || d.isNegative()) {
            d = Duration.ZERO;
        }
        long total = d.getSeconds();
        long m = total / 60;
        long s = total % 60;
        return String.format(Locale.CHINA, "%d 分 %02d 秒", m, s);
    }

    /**
     * 不显示秒时的倒计时：向上取整到分钟，避免出现“0 分钟”。
     * 超过 1 小时显示 X小时Y分钟。
     */
    public static String roundedMinuteCountdown(Duration d) {
        if (d == null || d.isNegative() || d.isZero()) {
            return "0 分钟";
        }
        long totalSeconds = d.getSeconds();
        long totalMinutes = (totalSeconds + 59) / 60; // 向上取整
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return String.format(Locale.CHINA, "%d 小时 %d 分钟", hours, minutes);
        } else if (hours > 0) {
            return String.format(Locale.CHINA, "%d 小时", hours);
        } else {
            return String.format(Locale.CHINA, "%d 分钟", totalMinutes);
        }
    }
}
