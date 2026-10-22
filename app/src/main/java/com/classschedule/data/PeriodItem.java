package com.classschedule.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalTime;

/**
 * 一节课的原始数据：名称 + 起止时间（HH:mm）。
 * custom=true 表示用户手动新增，允许删除与修改时间。
 */
public class PeriodItem {

    public String name;
    public String start;
    public String end;
    public boolean custom;

    public PeriodItem(String name, String start, String end) {
        this(name, start, end, false);
    }

    public PeriodItem(String name, String start, String end, boolean custom) {
        this.name = name;
        this.start = start;
        this.end = end;
        this.custom = custom;
    }

    public boolean isFree() {
        return name == null || name.trim().equals("无") || name.trim().isEmpty();
    }

    /**
     * 起止时间是否为可解析的 HH:mm 且结束严格晚于开始。
     * 用于过滤导入 / 旧版本中损坏的节次数据，避免运行时解析崩溃。
     */
    public boolean hasValidTime() {
        if (start == null || end == null) {
            return false;
        }
        try {
            return LocalTime.parse(start).isBefore(LocalTime.parse(end));
        } catch (Exception e) {
            return false;
        }
    }

    public PeriodItem copy() {
        return new PeriodItem(name, start, end, custom);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("n", name);
        o.put("s", start);
        o.put("e", end);
        o.put("c", custom);
        return o;
    }

    public static PeriodItem fromJson(JSONObject o) throws JSONException {
        return new PeriodItem(
                o.getString("n"),
                o.getString("s"),
                o.getString("e"),
                o.optBoolean("c", false));
    }
}
