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
 * V-Slice HScript / Haxe → Psych Engine Lua.
 * Port of GkTe Tool src/hscript_to_lua.py
 */
public class HScriptToLua {
    public final List<String> warnings = new ArrayList<String>();
    private final Set<String> variables = new HashSet<String>();
    private String className = "";
    private String extendsName = "";
    private String noteKindId = "";
    private String noteKindLabel = "";

    private static final Set<String> NO_PARAM = new HashSet<String>();
    private static final Map<String, String> CALLBACKS = new HashMap<String, String>();

    static {
        String[] np = {
                "onCreate", "onCreatePost", "onUpdate", "onUpdatePost",
                "onBeatHit", "onStepHit", "onSectionHit", "onSongStart", "onEndSong",
                "onStartCountdown", "onCountdownStarted", "onCountdownTick", "onEvent",
                "noteMiss", "goodNoteHit", "opponentNoteHit", "onKeyPress", "onKeyRelease",
                "onGhostTap", "onMoveCamera", "onGameOver", "onPause", "onResume", "onDestroy"
        };
        for (String s : np) NO_PARAM.add(s);
        CALLBACKS.put("onCountdownStart", "onStartCountdown");
        CALLBACKS.put("onSongRetry", "onGameOver");
        CALLBACKS.put("onNoteHit", "goodNoteHit");
        CALLBACKS.put("onSustainHit", "goodNoteHit");
    }

    public String convert(String hscript) {
        warnings.clear();
        variables.clear();
        className = "";
        extendsName = "";
        noteKindId = "";
        noteKindLabel = "";
        String code = preprocess(hscript);
        String[] lines = code.split("\n", -1);
        Map<String, Func> functions = new LinkedHashMap<String, Func>();
        List<String> globals = new ArrayList<String>();
        parseStructure(lines, functions, globals);
        return buildOutput(functions, globals);
    }

    private static String preprocess(String code) {
        code = Pattern.compile(";\\s*$", Pattern.MULTILINE).matcher(code).replaceAll("");
        code = code.replaceAll("cast\\(\\s*(\\w+)\\s*,\\s*\\w+\\s*\\)", "$1");
        return code;
    }

    private static class Func {
        String params;
        List<String> body = new ArrayList<String>();
    }

    private void parseStructure(String[] lines, Map<String, Func> functions, List<String> globalLines) {
        int i = 0, braceDepth = 0;
        boolean inClass = false, inFunction = false, inBlockComment = false;
        String funcName = "", funcParams = "";
        List<String> funcBody = new ArrayList<String>();
        Pattern classPat = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)(?:\\s+extends\\s+(\\w+))?");
        Pattern funcPat = Pattern.compile("(?:(?:public|private|override|static|inline)\\s+)*function\\s+(\\w+)\\s*\\(([^)]*)\\)(?:\\s*:\\s*[\\w.<>]+)?\\s*");

        while (i < lines.length) {
            String line = lines[i];
            String stripped = line.trim();
            if (stripped.length() == 0) {
                if (inFunction) funcBody.add("");
                i++;
                continue;
            }
            if (inBlockComment) {
                if (stripped.contains("*/")) inBlockComment = false;
                i++;
                continue;
            }
            if (stripped.startsWith("/*") || stripped.startsWith("/**")) {
                if (!stripped.contains("*/")) inBlockComment = true;
                i++;
                continue;
            }
            if (stripped.startsWith("import ") || stripped.startsWith("package ")) {
                i++;
                continue;
            }
            if (stripped.startsWith("//")) {
                i++;
                continue;
            }
            Matcher cm = classPat.matcher(stripped);
            if (cm.find()) {
                className = cm.group(1);
                if (cm.groupCount() >= 2 && cm.group(2) != null) extendsName = cm.group(2);
                inClass = true;
                if (stripped.contains("{")) braceDepth++;
                i++;
                continue;
            }
            Matcher fm = funcPat.matcher(stripped);
            if (fm.find()) {
                if (inFunction && funcName.length() > 0) {
                    Func f = new Func();
                    f.params = funcParams;
                    f.body = funcBody;
                    functions.put(funcName, f);
                }
                String rawName = fm.group(1);
                funcName = "new".equals(rawName) ? "__constructor__" : mapCallback(rawName);
                funcParams = cleanParams(fm.group(2));
                funcBody = new ArrayList<String>();
                inFunction = true;
                if (stripped.contains("{")) braceDepth++;
                i++;
                continue;
            }
            if ("{".equals(stripped)) {
                braceDepth++;
                i++;
                continue;
            }
            if ("}".equals(stripped)) {
                int minDepth = inClass ? 1 : 0;
                braceDepth--;
                if (inFunction) {
                    if (braceDepth <= minDepth) {
                        if (funcName.length() > 0) {
                            Func f = new Func();
                            f.params = funcParams;
                            f.body = funcBody;
                            functions.put(funcName, f);
                        }
                        inFunction = false;
                        funcName = "";
                        funcParams = "";
                        funcBody = new ArrayList<String>();
                    } else {
                        funcBody.add("end");
                    }
                } else if (inClass && braceDepth == 0) {
                    inClass = false;
                }
                i++;
                continue;
            }
            int open = countChar(stripped, '{');
            int close = countChar(stripped, '}');
            String converted = convertLine(stripped);
            if (converted != null) {
                if (inFunction) funcBody.add(converted);
                else globalLines.add(converted);
            }
            if (!"{".equals(stripped)) braceDepth += open;
            if (!"}".equals(stripped)) braceDepth -= close;
            i++;
        }
        if (inFunction && funcName.length() > 0) {
            Func f = new Func();
            f.params = funcParams;
            f.body = funcBody;
            functions.put(funcName, f);
        }
    }

    private String buildOutput(Map<String, Func> functions, List<String> globalLines) {
        List<String> result = new ArrayList<String>();
        boolean noteKind = "ScriptedNoteKind".equals(extendsName) || noteKindId.length() > 0;
        if (noteKind && functions.containsKey("goodNoteHit")) {
            Func hit = functions.get("goodNoteHit");
            hit.params = "id, direction, noteType, isSustainNote";
            List<String> prefixed = new ArrayList<String>();
            if (noteKindId.length() > 0) {
                prefixed.add("if noteType ~= \"" + noteKindId + "\" then return end");
            }
            prefixed.add("local dirs = {\"LEFT\", \"DOWN\", \"UP\", \"RIGHT\"}");
            prefixed.addAll(hit.body);
            hit.body = prefixed;
        }
        Set<String> initialized = new HashSet<String>();
        if (functions.containsKey("__constructor__")) {
            Func c = functions.remove("__constructor__");
            boolean any = false;
            for (String line : c.body) {
                if (line.trim().length() > 0) {
                    result.add(line);
                    any = true;
                    Matcher m = Pattern.compile("^(\\w+)\\s*=").matcher(line.trim());
                    if (m.find()) initialized.add(m.group(1));
                }
            }
            if (any) result.add("");
        }
        boolean hasGlobals = false;
        for (String line : globalLines) {
            if (line.trim().length() == 0) continue;
            Matcher m = Pattern.compile("^local\\s+(\\w+)\\s*=\\s*nil$").matcher(line.trim());
            if (m.find() && initialized.contains(m.group(1))) continue;
            result.add(line);
            hasGlobals = true;
        }
        if (hasGlobals) result.add("");

        for (Map.Entry<String, Func> e : functions.entrySet()) {
            String name = e.getKey();
            String params = e.getValue().params;
            if (NO_PARAM.contains(name) && !"goodNoteHit".equals(name) && !"noteMiss".equals(name) && !"opponentNoteHit".equals(name)) {
                params = "";
            }
            if ("goodNoteHit".equals(name) || "noteMiss".equals(name) || "opponentNoteHit".equals(name)) {
                params = "id, direction, noteType, isSustainNote";
            }
            String outName = "startVideo".equals(name) ? "playIntroVideo" : name;
            if ("__constructor__".equals(name)) continue;
            result.add("function " + outName + "(" + params + ")");
            List<String> body = optimizeBody(e.getValue().body);
            int indent = 1;
            for (String line : body) {
                String sl = line.trim();
                if (sl.length() == 0) {
                    result.add("");
                    continue;
                }
                if ("end".equals(sl) || "else".equals(sl) || sl.startsWith("elseif ")) {
                    indent = Math.max(1, indent - 1);
                }
                String pad = "";
                for (int k = 0; k < indent; k++) pad += "    ";
                result.add(pad + sl);
                if (sl.endsWith(" then") || sl.endsWith(" do") || "else".equals(sl)) indent++;
            }
            result.add("end");
            result.add("");
        }
        return joinClean(result);
    }

    private List<String> optimizeBody(List<String> body) {
        List<String> result = new ArrayList<String>();
        int i = 0;
        Pattern call = Pattern.compile("^\\w+\\(.*\\)$");
        while (i < body.size()) {
            String line = body.get(i).trim();
            if ("startVideo()".equals(line)) {
                result.add("playIntroVideo()");
                i++;
                continue;
            }
            if ("return Function_Stop".equals(line)) {
                int j = i + 1;
                while (j < body.size() && body.get(j).trim().length() == 0) j++;
                if (j < body.size()) {
                    String next = body.get(j).trim();
                    if (call.matcher(next).matches()) {
                        result.add("startVideo()".equals(next) ? "playIntroVideo()" : next);
                        result.add("return Function_Stop");
                        i = j + 1;
                        continue;
                    }
                }
            }
            result.add(body.get(i));
            i++;
        }
        return result;
    }

    private static String mapCallback(String name) {
        return CALLBACKS.containsKey(name) ? CALLBACKS.get(name) : name;
    }

    private static String cleanParams(String raw) {
        if (raw == null || raw.trim().length() == 0) return "";
        List<String> clean = new ArrayList<String>();
        String[] parts = raw.split(",");
        for (String p : parts) {
            p = p.trim();
            if (p.contains(":")) p = p.split(":")[0].trim();
            if (p.length() > 0 && !"Void".equals(p) && !"void".equals(p)) clean.add(p);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(clean.get(i));
        }
        return sb.toString();
    }

    private String convertLine(String line) {
        String original = line;
        line = line.trim();
        if (line.endsWith(";")) line = line.substring(0, line.length() - 1).trim();
        String stripped = line;

        if (line.matches("super\\.\\w+\\(.*")) return null;
        Matcher superM = Pattern.compile("super\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*(?:,\\s*[\"']([^\"']*)[\"'])?").matcher(line);
        if (superM.find()) {
            if (noteKindId.length() == 0) {
                noteKindId = superM.group(1);
                if (superM.group(2) != null) noteKindLabel = superM.group(2);
            }
            return null;
        }
        if (line.matches("super\\s*\\(.*")) return null;
        if (line.contains("event.cancel()")) return "return Function_Stop";

        Matcher m;
        // PlayState / stage character lookups
        if (line.contains("PlayState.instance") && line.contains("== null") && line.contains("return")) {
            return null;
        }
        m = Pattern.compile("(?:var\\s+)?(\\w+)(?:\\s*:\\s*\\w+)?\\s*=\\s*PlayState\\.instance\\.currentStage\\.getGirlfriend\\(\\)").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "local " + m.group(1) + " = \"gf\"";
        }
        m = Pattern.compile("(?:var\\s+)?(\\w+)(?:\\s*:\\s*\\w+)?\\s*=\\s*PlayState\\.instance\\.currentStage\\.getBoyfriend\\(\\)").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "local " + m.group(1) + " = \"boyfriend\"";
        }
        m = Pattern.compile("(?:var\\s+)?(\\w+)(?:\\s*:\\s*\\w+)?\\s*=\\s*PlayState\\.instance\\.currentStage\\.get(?:Dad|Opponent)\\(\\)").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "local " + m.group(1) + " = \"dad\"";
        }
        m = Pattern.compile("(\\w+)\\.playAnimation\\(\\s*(.+)\\s*\\)").matcher(line);
        if (m.find()) {
            return "characterPlayAnim(" + m.group(1) + ", " + convertValue(m.group(2).trim()) + ", true)";
        }
        m = Pattern.compile("(?:var\\s+)?(\\w+)(?:\\s*:\\s*\\w+)?\\s*=\\s*NoteKindsHandler\\.DIRECTION_NAMES\\s*\\[(.+)\\]").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "local " + m.group(1) + " = dirs[(" + convertValue(m.group(2).trim()) + ") + 1]";
        }
        if (line.contains("NoteKindsHandler.HOLD_SUFFIX")) {
            line = line.replace("NoteKindsHandler.HOLD_SUFFIX", "\"-hold\"");
        }
        if (line.contains(".animation.getNameList().contains(")) {
            return null;
        }

        m = Pattern.compile("VideoCutscene\\.play\\(\\s*Paths\\.videos\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\)").matcher(line);
        if (m.find()) return "startVideo(\"" + m.group(1) + "\")";

        m = Pattern.compile("(?:var\\s+)?(\\w+)\\s*=\\s*new\\s+FlxSprite\\(\\s*([^,]*)\\s*,\\s*([^)]*)\\s*\\)\\.loadGraphic\\(\\s*Paths\\.image\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*\\)").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "makeLuaSprite(\"" + m.group(1) + "\", \"" + m.group(4) + "\", " + m.group(2).trim() + ", " + m.group(3).trim() + ")";
        }
        m = Pattern.compile("(?:var\\s+)?(\\w+)\\s*=\\s*new\\s+FlxSprite\\(\\s*([^,]*)\\s*,\\s*([^)]*)\\s*\\)").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "makeLuaSprite(\"" + m.group(1) + "\", \"\", " + m.group(2).trim() + ", " + m.group(3).trim() + ")";
        }
        m = Pattern.compile("game\\.add\\(\\s*(\\w+)\\s*\\)").matcher(line);
        if (m.find()) return "addLuaSprite(\"" + m.group(1) + "\", true)";
        m = Pattern.compile("game\\.insert\\(\\s*.*?,\\s*(\\w+)\\s*\\)").matcher(line);
        if (m.find()) return "addLuaSprite(\"" + m.group(1) + "\", false)";
        m = Pattern.compile("game\\.remove\\(\\s*(\\w+)\\s*\\)").matcher(line);
        if (m.find()) return "removeLuaSprite(\"" + m.group(1) + "\", true)";

        m = Pattern.compile("(\\w+)\\.animation\\.addByPrefix\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*(?:,\\s*(\\d+))?\\s*(?:,\\s*(true|false))?\\s*\\)").matcher(line);
        if (m.find()) {
            String fps = m.group(4) == null ? "24" : m.group(4);
            String loop = m.group(5) == null ? "false" : m.group(5);
            return "addAnimationByPrefix(\"" + m.group(1) + "\", \"" + m.group(2) + "\", \"" + m.group(3) + "\", " + fps + ", " + loop + ")";
        }
        m = Pattern.compile("(\\w+)\\.animation\\.play\\(\\s*['\"]([^'\"]+)['\"]\\s*(?:,\\s*(true|false))?\\s*\\)").matcher(line);
        if (m.find()) {
            String forced = m.group(3) == null ? "false" : m.group(3);
            return "objectPlayAnimation(\"" + m.group(1) + "\", \"" + m.group(2) + "\", " + forced + ")";
        }
        m = Pattern.compile("(\\w+)\\.scale\\.set\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)").matcher(line);
        if (m.find()) return "scaleObject(\"" + m.group(1) + "\", " + m.group(2).trim() + ", " + m.group(3).trim() + ")";
        if (line.matches("\\w+\\.updateHitbox\\(\\)")) return null;
        m = Pattern.compile("(\\w+)\\.scrollFactor\\.set\\(\\s*([^,]+)\\s*,\\s*([^)]+)\\s*\\)").matcher(line);
        if (m.find()) return "setScrollFactor(\"" + m.group(1) + "\", " + m.group(2).trim() + ", " + m.group(3).trim() + ")";

        m = Pattern.compile("game\\.(\\w+(?:\\.\\w+)*)\\s*=\\s*(.+)").matcher(line);
        if (m.find()) return "setProperty(\"" + m.group(1) + "\", " + convertValue(m.group(2).trim()) + ")";
        m = Pattern.compile("(\\w+)\\.cameras\\s*=\\s*\\[\\s*game\\.(\\w+)\\s*\\]").matcher(line);
        if (m.find()) return "setObjectCamera(\"" + m.group(1) + "\", \"" + m.group(2) + "\")";

        m = Pattern.compile("FlxG\\.sound\\.play\\(\\s*Paths\\.sound\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*(?:,\\s*([^)]+))?\\s*\\)").matcher(line);
        if (m.find()) return "playSound(\"" + m.group(1) + "\", " + (m.group(2) == null ? "1" : m.group(2).trim()) + ")";
        m = Pattern.compile("FlxG\\.sound\\.playMusic\\(\\s*Paths\\.music\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\s*(?:,\\s*([^)]+))?\\s*\\)").matcher(line);
        if (m.find()) return "playMusic(\"" + m.group(1) + "\", " + (m.group(2) == null ? "1" : m.group(2).trim()) + ")";

        m = Pattern.compile("trace\\(\\s*(.+)\\s*\\)").matcher(line);
        if (m.find()) {
            String c = m.group(1).trim().replace("'", "\"").replace("`", "'");
            return "debugPrint(" + c + ")";
        }
        m = Pattern.compile("^(\\w+)\\(\\s*\\)$").matcher(line);
        if (m.find()) {
            String fn = m.group(1);
            return "startVideo".equals(fn) ? "playIntroVideo()" : fn + "()";
        }
        m = Pattern.compile("^(\\w+)\\(\\s*(.+)\\s*\\)$").matcher(line);
        if (m.find() && !"super".equals(m.group(1))) {
            String fn = m.group(1);
            String args = convertValue(m.group(2).trim());
            return "startVideo".equals(fn) ? "playIntroVideo(" + args + ")" : fn + "(" + args + ")";
        }

        m = Pattern.compile("(?:var|local)\\s+(\\w+)(?:\\s*:\\s*[\\w<>]+)?\\s*=\\s*(.+)").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "local " + m.group(1) + " = " + convertValue(m.group(2).trim());
        }
        m = Pattern.compile("(?:var|local)\\s+(\\w+)(?:\\s*:\\s*[\\w<>]+)?\\s*$").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            return "local " + m.group(1) + " = nil";
        }

        m = Pattern.compile("(\\w+)\\s*\\+=\\s*(.+)").matcher(line);
        if (m.find()) {
            String vn = m.group(1);
            String rhs = convertValue(m.group(2).trim());
            return vn + " = " + vn + " .. " + rhs;
        }
        m = Pattern.compile("if\\s*\\((.+)\\)\\s+return\\s*;?$").matcher(line);
        if (m.find()) return "if " + convertCondition(m.group(1)) + " then return end";
        m = Pattern.compile("if\\s*\\((.+)\\)\\s+(.+)$").matcher(line);
        if (m.find() && !m.group(2).trim().startsWith("{")) {
            String stmt = convertLine(m.group(2).trim());
            if (stmt == null) return "if " + convertCondition(m.group(1)) + " then return end";
            return "if " + convertCondition(m.group(1)) + " then " + stmt + " end";
        }
        m = Pattern.compile("if\\s*\\(\\s*(.+)\\s*\\)\\s*\\{?$").matcher(line);
        if (m.find()) return "if " + convertCondition(m.group(1)) + " then";
        m = Pattern.compile("\\}\\s*else\\s+if\\s*\\(\\s*(.+)\\s*\\)\\s*\\{?").matcher(line);
        if (m.find()) return "elseif " + convertCondition(m.group(1)) + " then";
        if ("} else {".equals(stripped) || "} else".equals(stripped)) return "else";
        if ("}".equals(stripped)) return "end";

        m = Pattern.compile("(\\w+)\\s*=\\s*(.+)").matcher(line);
        if (m.find()) {
            String vn = m.group(1);
            if (!"end then do else return function if elseif for while repeat".contains(vn)) {
                return vn + " = " + convertValue(m.group(2).trim());
            }
        }
        m = Pattern.compile("return\\s+(.+)").matcher(line);
        if (m.find()) return "return " + convertValue(m.group(1).trim());
        if ("return".equals(stripped)) return "return";

        m = Pattern.compile("for\\s*\\(\\s*(\\w+)\\s+in\\s+(\\d+)\\.\\.\\.(\\d+)\\s*\\)\\s*\\{?").matcher(line);
        if (m.find()) {
            variables.add(m.group(1));
            int end = Integer.parseInt(m.group(3)) - 1;
            return "for " + m.group(1) + " = " + m.group(2) + ", " + end + " do";
        }

        warnings.add(original);
        return null;
    }

    private String convertValue(String val) {
        val = val.trim();
        if (val.endsWith(";")) val = val.substring(0, val.length() - 1).trim();
        if ("null".equals(val)) return "nil";
        if ("true".equals(val) || "false".equals(val)) return val;
        if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
            return "\"" + val.substring(1, val.length() - 1) + "\"";
        }
        if (val.contains("\"") && val.contains("+")) val = val.replace(" + ", " .. ");
        val = val.replace("!=", "~=");
        val = val.replaceAll("&&", "and");
        val = val.replaceAll("\\|\\|", "or");
        val = val.replaceAll("!\\s*(\\w)", "not $1");
        val = Pattern.compile("game\\.(\\w+(?:\\.\\w+)*)").matcher(val).replaceAll("getProperty(\"$1\")");
        val = val.replaceAll("Paths\\.image\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)", "\"$1\"");
        val = val.replaceAll("Paths\\.sound\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)", "\"$1\"");
        val = val.replaceAll("Paths\\.music\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)", "\"$1\"");
        val = val.replaceAll("Paths\\.videos\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)", "\"$1\"");
        return val;
    }

    private String convertCondition(String cond) {
        cond = convertValue(cond);
        return cond.replaceAll("^!\\s*(\\w)", "not $1");
    }

    private static String joinClean(List<String> lines) {
        List<String> out = new ArrayList<String>();
        boolean prevEmpty = false;
        for (String line : lines) {
            boolean empty = line.trim().length() == 0;
            if (empty) {
                if (!prevEmpty) out.add("");
                prevEmpty = true;
            } else {
                out.add(line);
                prevEmpty = false;
            }
        }
        while (out.size() > 0 && out.get(0).trim().length() == 0) out.remove(0);
        while (out.size() > 0 && out.get(out.size() - 1).trim().length() == 0) out.remove(out.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(out.get(i));
        }
        return sb.toString();
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }
}
