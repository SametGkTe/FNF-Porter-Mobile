package com.sametgkte.fnfporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class CharacterConverter {
    public final String characterName;
    public final String iconID;
    public final String characterJson;
    private final JSONObject character;

    public CharacterConverter(File path) throws Exception {
        this.characterJson = stripExt(path.getName());
        String[] parts = characterJson.split("-");
        StringBuilder name = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) name.append(p.substring(1));
        }
        this.characterName = name.toString();

        JSONObject psych = new JSONObject(FileOps.readText(path));
        character = Constants.characterTemplate();
        AppLog.info("Converting character " + characterName);

        character.put("name", characterName);
        character.put("assetPath", psych.opt("image"));
        character.put("singTime", psych.opt("sing_duration"));
        double scale = psych.optDouble("scale", 1);
        character.put("scale", scale);
        boolean pixel = scale >= 6;
        character.put("isPixel", pixel);
        character.getJSONObject("healthIcon").put("id", psych.opt("healthicon"));
        character.getJSONObject("healthIcon").put("isPixel", pixel);
        character.put("flipX", psych.optBoolean("flip_x", false));
        this.iconID = psych.optString("healthicon", "");

        JSONArray anims = psych.optJSONArray("animations");
        if (anims == null) anims = new JSONArray();
        JSONArray out = character.getJSONArray("animations");
        for (int i = 0; i < anims.length(); i++) {
            JSONObject animation = anims.optJSONObject(i);
            if (animation == null) continue;
            JSONObject tmpl = Constants.animationTemplate();
            tmpl.put("name", animation.opt("anim"));
            tmpl.put("prefix", animation.opt("name"));
            tmpl.put("offsets", animation.optJSONArray("offsets") == null ? tmpl.getJSONArray("offsets") : animation.getJSONArray("offsets"));
            tmpl.put("frameRate", animation.opt("fps"));
            tmpl.put("frameIndices", animation.optJSONArray("indices") == null ? new JSONArray() : animation.getJSONArray("indices"));
            AppLog.info("[" + characterName + "] Converting animation " + animation.optString("anim"));
            out.put(tmpl);
        }
        AppLog.info("Character " + characterName + " successfully converted");
    }

    public void save(File resultDir) throws Exception {
        File out = new File(resultDir, characterJson + ".json");
        FileOps.writeText(out, character.toString(4));
        AppLog.info("Character " + characterName + " saved to " + out.getAbsolutePath());
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
