package com.sametgkte.fnfporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage JSON conversion + a lightweight Psych Lua sprite parser
 * (regex-based stand-in for luaparser, covers typical stage.lua files).
 */
public class StageConverter {

    public static JSONObject convert(JSONObject stageJSON, String assetName, JSONArray luaProps) throws Exception {
        JSONObject stage = Constants.stageTemplate();
        stage.put("cameraZoom", stageJSON.opt("defaultZoom"));
        stage.getJSONObject("characters").getJSONObject("bf").put("position", stageJSON.opt("boyfriend"));
        stage.getJSONObject("characters").getJSONObject("gf").put("position", stageJSON.opt("girlfriend"));
        stage.getJSONObject("characters").getJSONObject("dad").put("position", stageJSON.opt("opponent"));
        stage.put("props", luaProps == null ? new JSONArray() : luaProps);
        stage.put("name", Utils.titleCaseDashed(assetName));
        return stage;
    }

    public static JSONArray parseStageLua(File luaFile) {
        if (luaFile == null || !luaFile.exists()) return new JSONArray();
        try {
            String src = FileOps.readText(luaFile);
            src = src.replaceAll("--\\[\\[[\\s\\S]*?\\]\\]", " ");
            src = src.replaceAll("--[^\\n]*", " ");
            List<Prop> props = new ArrayList<Prop>();
            Map<String, Prop> byTag = new HashMap<String, Prop>();

            Pattern make = Pattern.compile(
                    "(makeLuaSprite|makeAnimatedLuaSprite)\\s*\\(([^;]*)\\)",
                    Pattern.CASE_INSENSITIVE);
            Matcher m = make.matcher(src);
            while (m.find()) {
                List<String> args = splitArgs(m.group(2));
                if (args.size() < 2) continue;
                Prop p = new Prop();
                p.tag = unquote(args.get(0));
                p.sprite = unquote(args.get(1));
                p.animated = m.group(1).toLowerCase(Locale.US).contains("animated");
                if (args.size() >= 4) {
                    p.x = toDouble(args.get(2), 0);
                    p.y = toDouble(args.get(3), 0);
                }
                props.add(p);
                byTag.put(p.tag, p);
            }

            applyPair(src, "scaleObject", byTag, true);
            applyPair(src, "setScrollFactor", byTag, false);

            Pattern anim = Pattern.compile("addAnimationByPrefix\\s*\\(([^;]*)\\)", Pattern.CASE_INSENSITIVE);
            Matcher am = anim.matcher(src);
            while (am.find()) {
                List<String> args = splitArgs(am.group(1));
                if (args.size() < 3) continue;
                Prop p = byTag.get(unquote(args.get(0)));
                if (p == null) continue;
                Anim a = new Anim();
                a.name = unquote(args.get(1));
                a.prefix = unquote(args.get(2));
                if (args.size() >= 4) a.fps = toInt(args.get(3), 24);
                if (args.size() >= 5) a.loop = toBool(args.get(4), true);
                p.anims.add(a);
            }

            Pattern add = Pattern.compile("addLuaSprite\\s*\\(([^;]*)\\)", Pattern.CASE_INSENSITIVE);
            Matcher ad = add.matcher(src);
            List<String> order = new ArrayList<String>();
            List<Boolean> front = new ArrayList<Boolean>();
            while (ad.find()) {
                List<String> args = splitArgs(ad.group(1));
                if (args.isEmpty()) continue;
                order.add(unquote(args.get(0)));
                boolean inFront = args.size() > 1 && toBool(args.get(1), false);
                front.add(inFront);
            }
            for (int i = 0; i < order.size(); i++) {
                Prop p = byTag.get(order.get(i));
                if (p == null) continue;
                if (front.get(i)) p.z = 300 + i;
                else p.z = i - order.size();
            }

            return toFnfProps(props);
        } catch (Exception e) {
            AppLog.error("Could not complete parsing of " + luaFile.getName(), e);
            return new JSONArray();
        }
    }

    private static void applyPair(String src, String fn, Map<String, Prop> byTag, boolean scale) {
        Pattern ptn = Pattern.compile(fn + "\\s*\\(([^;]*)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = ptn.matcher(src);
        while (m.find()) {
            List<String> args = splitArgs(m.group(1));
            if (args.size() < 3) continue;
            Prop p = byTag.get(unquote(args.get(0)));
            if (p == null) continue;
            double a = toDouble(args.get(1), 1);
            double b = toDouble(args.get(2), 1);
            if (scale) {
                p.sx = a;
                p.sy = b;
            } else {
                p.scx = a;
                p.scy = b;
            }
        }
    }

    private static JSONArray toFnfProps(List<Prop> props) throws Exception {
        JSONArray out = new JSONArray();
        for (Prop prop : props) {
            JSONObject tmpl = prop.animated ? Constants.stagePropAnimated() : Constants.stagePropImage();
            tmpl.put("name", prop.tag);
            tmpl.put("assetPath", prop.sprite);
            JSONArray pos = new JSONArray();
            pos.put(prop.x);
            pos.put(prop.y - 720);
            tmpl.put("position", pos);
            tmpl.put("zIndex", prop.z);
            JSONArray scale = new JSONArray();
            scale.put(prop.sx);
            scale.put(prop.sy);
            tmpl.put("scale", scale);
            JSONArray scroll = new JSONArray();
            scroll.put(prop.scx);
            scroll.put(prop.scy);
            tmpl.put("scroll", scroll);
            if (prop.animated) {
                JSONArray anims = new JSONArray();
                for (Anim a : prop.anims) {
                    JSONObject at = Constants.stagePropAnimation();
                    at.put("frameRate", a.fps);
                    at.put("looped", a.loop);
                    at.put("name", String.valueOf(a.name));
                    at.put("prefix", String.valueOf(a.prefix));
                    anims.put(at);
                }
                tmpl.put("animations", anims);
            }
            out.put(tmpl);
        }
        return out;
    }

    private static List<String> splitArgs(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inS = false, inD = false;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' && !inD) inS = !inS;
            else if (c == '"' && !inS) inD = !inD;
            else if (!inS && !inD) {
                if (c == '(' || c == '{') depth++;
                if (c == ')' || c == '}') depth--;
                if (c == ',' && depth <= 0) {
                    out.add(cur.toString().trim());
                    cur.setLength(0);
                    continue;
                }
            }
            cur.append(c);
        }
        String last = cur.toString().trim();
        if (last.length() > 0) out.add(last);
        return out;
    }

    private static String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            if (s.length() >= 2) return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static double toDouble(String s, double def) {
        try {
            return Double.parseDouble(unquote(s).replace("+", "").trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int toInt(String s, int def) {
        try {
            return (int) Double.parseDouble(unquote(s).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean toBool(String s, boolean def) {
        if (s == null) return def;
        s = unquote(s).trim().toLowerCase(Locale.US);
        if ("true".equals(s) || "1".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s)) return false;
        return def;
    }

    private static class Prop {
        String tag = "";
        String sprite = "";
        boolean animated;
        double x, y;
        double sx = 1, sy = 1;
        double scx = 1, scy = 1;
        int z;
        List<Anim> anims = new ArrayList<Anim>();
    }

    private static class Anim {
        String name;
        String prefix;
        int fps = 24;
        boolean loop = true;
    }
}
