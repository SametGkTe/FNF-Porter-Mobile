package com.sametgkte.fnfporter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Utils {

    public static String character(String name) {
        if (name == null) return null;
        if ("pico-player".equals(name)) return "pico-playable";
        return name;
    }

    public static String stage(String name) {
        if (name == null) return null;
        if ("stage".equals(name)) return "mainStage";
        return name;
    }

    public static JSONObject timeChange(double timeStamp, double bpm, int timeSignatureNum,
                                        int timeSignatureDen, int beatTime, JSONArray beatTuplets) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", timeStamp);
        o.put("b", beatTime);
        o.put("bpm", bpm);
        o.put("n", timeSignatureNum);
        o.put("d", timeSignatureDen);
        o.put("bt", beatTuplets);
        return o;
    }

    public static JSONArray defaultTuplets() {
        JSONArray a = new JSONArray();
        for (int i = 0; i < 4; i++) a.put(4);
        return a;
    }

    public static JSONObject note(int data, Object length, Object time) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("d", data);
        o.put("t", time);
        boolean skipLen = false;
        if (length == null) skipLen = true;
        else if (length instanceof String) skipLen = true;
        else if (length instanceof Number && ((Number) length).doubleValue() == 0) skipLen = true;
        if (!skipLen) o.put("l", length);
        return o;
    }

    public static JSONObject event(double time, String event, JSONObject values) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", time);
        o.put("e", event);
        o.put("v", values);
        return o;
    }

    public static JSONObject changeCharacter(double time, String target, String charName) throws JSONException {
        JSONObject v = new JSONObject();
        v.put("target", target);
        v.put("char", charName);
        return event(time, "Change Character", v);
    }

    public static JSONObject focusCamera(double time, boolean charIsPlayer) throws JSONException {
        JSONObject v = new JSONObject();
        v.put("char", charIsPlayer ? "0" : "1");
        return event(time, "FocusCamera", v);
    }

    public static JSONObject playAnimation(double time, String target, String anim, boolean force) throws JSONException {
        JSONObject v = new JSONObject();
        v.put("target", target);
        v.put("anim", anim);
        v.put("force", force);
        return event(time, "PlayAnimation", v);
    }

    public static String formatToSongPath(String name) {
        if (name == null) return "";
        String s = name.replace(" ", "-").toLowerCase();
        s = s.replaceAll("[~&\\\\;:<>#]", "-");
        s = s.replaceAll("[.,'\"%?!]", "");
        return s;
    }

    public static String titleCaseDashed(String raw) {
        if (raw == null) return "";
        String base = raw.replace(".json", "");
        String[] parts = base.split("-");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }

    public static JSONObject optObj(JSONObject o, String key) {
        if (o == null) return new JSONObject();
        JSONObject v = o.optJSONObject(key);
        return v == null ? new JSONObject() : v;
    }

    public static boolean optBool(JSONObject o, String key, boolean def) {
        if (o == null) return def;
        return o.optBoolean(key, def);
    }
}
