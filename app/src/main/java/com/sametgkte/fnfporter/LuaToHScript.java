package com.sametgkte.fnfporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Psych Lua → V-Slice HScript (.hxc). Best-effort, same spirit as GkTe UFT.
 */
public class LuaToHScript {
    public final List<String> warnings = new ArrayList<String>();

    private static final Map<String, String> CALLBACKS = new HashMap<String, String>();
    private static final Map<String, String> PARAMS = new HashMap<String, String>();

    static {
        String[] same = {
                "onCreate", "onCreatePost", "onUpdate", "onUpdatePost", "onBeatHit", "onStepHit",
                "onSectionHit", "onSongStart", "onEndSong", "onCountdownTick", "onEvent",
                "noteMiss", "goodNoteHit", "opponentNoteHit", "onKeyPress", "onKeyRelease",
                "onGhostTap", "onMoveCamera", "onGameOver", "onPause", "onResume", "onDestroy"
        };
        for (String s : same) CALLBACKS.put(s, s);
        CALLBACKS.put("onStartCountdown", "onCountdownStart");
        CALLBACKS.put("onCountdownStarted", "onCountdownStart");
        PARAMS.put("onCountdownStart", "event:CountdownScriptEvent");
        PARAMS.put("onUpdate", "elapsed:Float");
        PARAMS.put("onUpdatePost", "elapsed:Float");
        PARAMS.put("onEvent", "name:String, value1:String, value2:String");
        PARAMS.put("noteMiss", "id:Int, direction:Int, noteType:String, isSustain:Bool");
        PARAMS.put("goodNoteHit", "id:Int, direction:Int, noteType:String, isSustain:Bool");
        PARAMS.put("opponentNoteHit", "id:Int, direction:Int, noteType:String, isSustain:Bool");
        PARAMS.put("onKeyPress", "key:Int");
        PARAMS.put("onKeyRelease", "key:Int");
        PARAMS.put("onGhostTap", "key:Int");
        PARAMS.put("onMoveCamera", "focus:String");
    }

    private static class Func {
        String params;
        List<String> body = new ArrayList<String>();
    }

    public String convert(String lua) {
        warnings.clear();
        Map<String, Func> functions = new LinkedHashMap<String, Func>();
        List<String> globals = new ArrayList<String>();
        parse(lua.split("\n", -1), functions, globals);
        return build(functions, globals);
    }

    private void parse(String[] lines, Map<String, Func> functions, List<String> globals) {
        boolean inFn = false;
        String name = "", params = "";
        List<String> body = new ArrayList<String>();
        int endDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            String stripped = lines[i].trim();
            if (stripped.length() == 0) {
                if (inFn) body.add("");
                continue;
            }
            if (stripped.startsWith("--")) {
                continue;
            }
            Matcher fm = Pattern.compile("^function\\s+(\\w+)\\s*\\((.*?)\\)\\s*$").matcher(stripped);
            if (fm.find()) {
                if (inFn && name.length() > 0) save(functions, name, params, body);
                name = fm.group(1);
                params = fm.group(2).trim();
                body = new ArrayList<String>();
                inFn = true;
                endDepth = 0;
                continue;
            }
            if ("end".equals(stripped)) {
                if (inFn) {
                    if (endDepth == 0) {
                        save(functions, name, params, body);
                        inFn = false;
                        name = "";
                        params = "";
                        body = new ArrayList<String>();
                    } else {
                        endDepth--;
                        body.add("end");
                    }
                }
                continue;
            }
            if (inFn && (stripped.endsWith(" then") || stripped.endsWith(" do"))) endDepth++;
            if (inFn) body.add(stripped);
            else globals.add(stripped);
        }
        if (inFn && name.length() > 0) save(functions, name, params, body);
    }

    private static void save(Map<String, Func> functions, String name, String params, List<String> body) {
        Func f = new Func();
        f.params = params;
        f.body = body;
        functions.put(name, f);
    }

    private String build(Map<String, Func> functions, List<String> globals) {
        List<String> result = new ArrayList<String>();
        result.add("");
        boolean anyG = false;
        for (String line : globals) {
            if (line.trim().length() == 0) continue;
            String c = convertLine(line.trim());
            if (c != null) {
                result.add(c);
                anyG = true;
            }
        }
        if (anyG) result.add("");
        for (Map.Entry<String, Func> e : functions.entrySet()) {
            String hs = CALLBACKS.containsKey(e.getKey()) ? CALLBACKS.get(e.getKey()) : e.getKey();
            String params = PARAMS.containsKey(hs) ? PARAMS.get(hs) : e.getValue().params;
            result.add("function " + hs + "(" + params + ") {");
            int indent = 1;
            for (String raw : e.getValue().body) {
                String stripped = raw.trim();
                if (stripped.length() == 0) {
                    result.add("");
                    continue;
                }
                if (stripped.startsWith("--")) {
                    continue;
                }
                if ("end".equals(stripped)) {
                    indent = Math.max(1, indent - 1);
                    result.add(pad(indent) + "}");
                    continue;
                }
                if ("else".equals(stripped)) {
                    indent = Math.max(1, indent - 1);
                    result.add(pad(indent) + "} else {");
                    indent++;
                    continue;
                }
                Matcher em = Pattern.compile("^elseif\\s+(.+)\\s+then$").matcher(stripped);
                if (em.find()) {
                    indent = Math.max(1, indent - 1);
                    result.add(pad(indent) + "} else if (" + toHsCond(em.group(1)) + ") {");
                    indent++;
                    continue;
                }
                Matcher im = Pattern.compile("^if\\s+(.+)\\s+then$").matcher(stripped);
                if (im.find()) {
                    result.add(pad(indent) + "if (" + toHsCond(im.group(1)) + ") {");
                    indent++;
                    continue;
                }
                Matcher frm = Pattern.compile("^for\\s+(\\w+)\\s*=\\s*(\\d+)\\s*,\\s*(\\d+)\\s*do$").matcher(stripped);
                if (frm.find()) {
                    int end = Integer.parseInt(frm.group(3)) + 1;
                    result.add(pad(indent) + "for (" + frm.group(1) + " in " + frm.group(2) + "..." + end + ") {");
                    indent++;
                    continue;
                }
                String c = convertLine(stripped);
                if (c != null) result.add(pad(indent) + c);
            }
            result.add("}");
            result.add("");
        }
        if (warnings.size() > 0) { }
        return join(result);
    }

    private String convertLine(String line) {
        if (line.startsWith("--")) return null;
        if ("return Function_Stop".equals(line)) return "event.cancel();";
        Matcher m;
        m = Pattern.compile("^startVideo\\(\\s*\"([^\"]+)\"\\s*\\)$").matcher(line);
        if (m.find()) return "VideoCutscene.play(Paths.videos('" + m.group(1) + "'));";
        m = Pattern.compile("^makeLuaSprite\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*([^,]+)\\s*,\\s*([^)]+)\\)$").matcher(line);
        if (m.find()) return "var " + m.group(1) + " = new FlxSprite(" + m.group(3).trim() + ", " + m.group(4).trim() + ").loadGraphic(Paths.image('" + m.group(2) + "'));";
        m = Pattern.compile("^makeAnimatedLuaSprite\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*([^,]+)\\s*,\\s*([^)]+)\\)$").matcher(line);
        if (m.find()) return "var " + m.group(1) + " = new FlxSprite(" + m.group(3).trim() + ", " + m.group(4).trim() + ").loadGraphic(Paths.image('" + m.group(2) + "'));";
        m = Pattern.compile("^addLuaSprite\\(\\s*\"([^\"]+)\"\\s*(?:,\\s*(true|false))?\\s*\\)$").matcher(line);
        if (m.find()) {
            if ("false".equals(m.group(2))) return "game.insert(0, " + m.group(1) + ");";
            return "game.add(" + m.group(1) + ");";
        }
        m = Pattern.compile("^removeLuaSprite\\(\\s*\"([^\"]+)\".*\\)$").matcher(line);
        if (m.find()) return "game.remove(" + m.group(1) + ");";
        m = Pattern.compile("^addAnimationByPrefix\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*(?:,\\s*(\\d+))?\\s*(?:,\\s*(true|false))?\\s*\\)$").matcher(line);
        if (m.find()) {
            String fps = m.group(4) == null ? "24" : m.group(4);
            String loop = m.group(5) == null ? "false" : m.group(5);
            return m.group(1) + ".animation.addByPrefix('" + m.group(2) + "', '" + m.group(3) + "', " + fps + ", " + loop + ");";
        }
        m = Pattern.compile("^objectPlayAnimation\\(\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*(?:,\\s*(true|false))?\\s*\\)$").matcher(line);
        if (m.find()) return m.group(1) + ".animation.play('" + m.group(2) + "', " + (m.group(3) == null ? "false" : m.group(3)) + ");";
        m = Pattern.compile("^characterPlayAnim\\(\\s*([^,]+)\\s*,\\s*(.+)\\s*,\\s*(true|false)\\s*\\)$").matcher(line);
        if (m.find()) {
            String who = m.group(1).trim().replace("\"", "").replace("'", "");
            String getter = "gf".equals(who) ? "getGirlfriend()" : ("dad".equals(who) ? "getDad()" : "getBoyfriend()");
            return "PlayState.instance.currentStage." + getter + ".playAnimation(" + m.group(2).trim() + ");";
        }
        m = Pattern.compile("^scaleObject\\(\\s*\"([^\"]+)\"\\s*,\\s*([^,]+)\\s*,\\s*([^)]+)\\)$").matcher(line);
        if (m.find()) return m.group(1) + ".scale.set(" + m.group(2).trim() + ", " + m.group(3).trim() + ");";
        m = Pattern.compile("^setScrollFactor\\(\\s*\"([^\"]+)\"\\s*,\\s*([^,]+)\\s*,\\s*([^)]+)\\)$").matcher(line);
        if (m.find()) return m.group(1) + ".scrollFactor.set(" + m.group(2).trim() + ", " + m.group(3).trim() + ");";
        m = Pattern.compile("^setProperty\\(\\s*\"([^\"]+)\"\\s*,\\s*(.+)\\)$").matcher(line);
        if (m.find()) return "game." + m.group(1) + " = " + toHsVal(m.group(2).trim()) + ";";
        m = Pattern.compile("^playSound\\(\\s*\"([^\"]+)\"\\s*(?:,\\s*([^)]+))?\\)$").matcher(line);
        if (m.find()) return "FlxG.sound.play(Paths.sound('" + m.group(1) + "')" + (m.group(2) == null ? "" : ", " + m.group(2).trim()) + ");";
        m = Pattern.compile("^playMusic\\(\\s*\"([^\"]+)\"\\s*(?:,\\s*([^)]+))?\\)$").matcher(line);
        if (m.find()) return "FlxG.sound.playMusic(Paths.music('" + m.group(1) + "')" + (m.group(2) == null ? "" : ", " + m.group(2).trim()) + ");";
        m = Pattern.compile("^debugPrint\\((.+)\\)$").matcher(line);
        if (m.find()) return "trace(" + m.group(1) + ");";
        m = Pattern.compile("^local\\s+(\\w+)\\s*=\\s*(.+)$").matcher(line);
        if (m.find()) return "var " + m.group(1) + " = " + toHsVal(m.group(2).trim()) + ";";
        m = Pattern.compile("^(\\w+)\\s*=\\s*(.+)$").matcher(line);
        if (m.find()) return m.group(1) + " = " + toHsVal(m.group(2).trim()) + ";";
        m = Pattern.compile("^return\\s+(.+)$").matcher(line);
        if (m.find()) return "return " + toHsVal(m.group(1).trim()) + ";";
        if ("return".equals(line)) return "return;";
        warnings.add(line);
        return "// TODO: " + line;
    }

    private String toHsVal(String val) {
        if ("nil".equals(val)) return "null";
        val = val.replace(" .. ", " + ");
        val = val.replace("~=", "!=");
        val = val.replaceAll("\\band\\b", "&&");
        val = val.replaceAll("\\bor\\b", "||");
        val = val.replaceAll("\\bnot\\s+", "!");
        val = val.replaceAll("getProperty\\(\"([^\"]+)\"\\)", "game.$1");
        return val;
    }

    private String toHsCond(String cond) {
        return toHsVal(cond);
    }

    private static String pad(int n) {
        String s = "";
        for (int i = 0; i < n; i++) s += "    ";
        return s;
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }
}
