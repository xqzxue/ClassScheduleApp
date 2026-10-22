package com.classschedule.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 一天的课程安排（节数可变，允许用户新增到 13、14 节……）。
 */
public class DayPlan {

    public final List<PeriodItem> periods = new ArrayList<>();

    public DayPlan() {
    }

    public DayPlan(List<PeriodItem> items) {
        for (PeriodItem item : items) {
            periods.add(item.copy());
        }
    }

    public boolean hasClass() {
        for (PeriodItem p : periods) {
            if (!p.isFree()) {
                return true;
            }
        }
        return false;
    }

    public int totalCount() {
        return periods.size();
    }

    public JSONObject toJson() throws JSONException {
        JSONArray arr = new JSONArray();
        for (PeriodItem p : periods) {
            arr.put(p.toJson());
        }
        return new JSONObject().put("items", arr);
    }

    public static DayPlan fromJson(JSONObject o) throws JSONException {
        DayPlan plan = new DayPlan();
        JSONArray arr = o.getJSONArray("items");
        for (int i = 0; i < arr.length(); i++) {
            PeriodItem item = PeriodItem.fromJson(arr.getJSONObject(i));
            // 跳过时间非法的节次（损坏 / 手改的存档），避免后续 LocalTime.parse 崩溃
            if (item.hasValidTime()) {
                plan.periods.add(item);
            }
        }
        return plan;
    }
}
