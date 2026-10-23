package com.classschedule.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 一份课程表配置：名称 + 周一至周日共 7 天的 DayPlan，每天节数可变。
 */
public class ScheduleProfile {

    public String id;
    public String name;
    /** days[0]=周一 … days[6]=周日。 */
    public final DayPlan[] days;

    public ScheduleProfile(String id, String name, DayPlan[] days) {
        this.id = id;
        this.name = name;
        this.days = days;
    }

    /** 空白配置（7 天均无课）。 */
    public static ScheduleProfile blank(String id, String name) {
        return new ScheduleProfile(id, name, ScheduleRepository.blankWeek());
    }

    /** 内置配置（按 id 返回一班或二班内置课程表，含周日晚自习）。 */
    public static ScheduleProfile defaultProfile(String id) {
        return ScheduleRepository.builtinProfile(id);
    }

    public ScheduleProfile copy(String newId, String newName) {
        return new ScheduleProfile(newId, newName, ScheduleRepository.copyWeek(days));
    }

    /** 全部节数（含“无”）。 */
    public int totalCount() {
        int total = 0;
        for (DayPlan d : days) {
            total += d.totalCount();
        }
        return total;
    }

    /** 非“无”的课程节数，用于列表摘要。 */
    public int filledCount() {
        int count = 0;
        for (DayPlan d : days) {
            for (PeriodItem p : d.periods) {
                if (!p.isFree()) {
                    count++;
                }
            }
        }
        return count;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("version", 2);
        JSONArray week = new JSONArray();
        for (DayPlan d : days) {
            week.put(d.toJson());
        }
        obj.put("days", week);
        return obj;
    }

    public static ScheduleProfile fromJson(JSONObject obj) throws JSONException {
        String id = obj.getString("id");
        String name = obj.getString("name");

        if (obj.has("days")) {
            // 新版：7 天 DayPlan
            JSONArray week = obj.getJSONArray("days");
            DayPlan[] days = ScheduleRepository.blankWeek();
            for (int d = 0; d < Math.min(week.length(), ScheduleRepository.DAY_COUNT); d++) {
                days[d] = DayPlan.fromJson(week.getJSONObject(d));
            }
            return new ScheduleProfile(id, name, days);
        } else {
            // 旧版（v1.x）：5 天 × 12 节纯名称数组，迁移并补上周六空、周日晚自习
            JSONArray week = obj.getJSONArray("courses");
            DayPlan[] days = ScheduleRepository.defaultWeek();
            for (int d = 0; d < Math.min(week.length(), 5); d++) {
                JSONArray row = week.getJSONArray(d);
                String[] names = new String[ScheduleRepository.STANDARD_PERIOD_COUNT];
                for (int p = 0; p < ScheduleRepository.STANDARD_PERIOD_COUNT; p++) {
                    names[p] = p < row.length() ? row.getString(p) : "无";
                }
                days[d] = ScheduleRepository.standardDay(names);
            }
            return new ScheduleProfile(id, name, days);
        }
    }
}
