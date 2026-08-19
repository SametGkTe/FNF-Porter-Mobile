package com.sametgkte.fnfporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class WeekConverter {

    public static JSONObject convert(JSONObject weekJSON, File modFolder, String weekFilename) throws Exception {
        JSONObject level = Constants.levelTemplate();
        level.put("name", weekJSON.opt("storyName"));

        JSONArray songsOut = new JSONArray();
        JSONArray songsIn = weekJSON.optJSONArray("songs");
        if (songsIn != null) {
            for (int i = 0; i < songsIn.length(); i++) {
                JSONArray song = songsIn.optJSONArray(i);
                String name = song != null ? song.optString(0, "") : String.valueOf(songsIn.opt(i));
                songsOut.put(name.replace(" ", "-").toLowerCase());
            }
        }
        level.put("songs", songsOut);

        JSONArray props = level.getJSONArray("props");
        JSONArray weekChars = weekJSON.optJSONArray("weekCharacters");
        if (weekChars != null) {
            for (int i = 0; i < weekChars.length(); i++) {
                String charName = weekChars.optString(i, "");
                JSONObject def = Constants.defaultProp(charName);
                if (def != null) {
                    props.put(def);
                    continue;
                }
                AppLog.info("Opening " + charName + ".json");
                File jsonFile = new File(modFolder, Constants.FILE_LOCS.get("WEEKCHARACTERJSON")[0] + charName + ".json");
                if (!jsonFile.exists()) {
                    AppLog.error("Could not open " + charName + ".json");
                    continue;
                }
                try {
                    JSONObject wc = new JSONObject(FileOps.readText(jsonFile));
                    JSONObject prop = Constants.levelProp();
                    prop.put("assetPath", Constants.FILE_LOCS.get("WEEKCHARACTERASSET")[1] + wc.optString("image"));
                    prop.put("scale", wc.opt("scale"));
                    prop.put("offsets", wc.optJSONArray("position") == null ? new JSONArray() : wc.getJSONArray("position"));
                    JSONArray anims = new JSONArray();
                    JSONObject idle = Constants.levelPropAnim();
                    idle.put("name", "idle");
                    idle.put("prefix", wc.opt("idle_anim"));
                    anims.put(idle);
                    String confirm = wc.optString("confirm_anim", "");
                    if (confirm != null && confirm.length() > 0) {
                        JSONObject c = Constants.levelPropAnim();
                        c.put("name", "confirm");
                        c.put("prefix", confirm);
                        anims.put(c);
                    }
                    prop.put("animations", anims);
                    props.put(prop);
                } catch (Exception e) {
                    AppLog.error("Could not open " + charName + ".json", e);
                }
            }
        }

        if (weekJSON.has("freeplayColor")) {
            JSONArray col = weekJSON.optJSONArray("freeplayColor");
            int r = col == null ? 255 : col.optInt(0, 255);
            int g = col == null ? 255 : col.optInt(1, 255);
            int b = col == null ? 255 : col.optInt(2, 255);
            level.put("background", String.format("#%02X%02X%02X", r, g, b));
        } else {
            level.put("background", "#FFFFFF");
        }
        level.put("titleAsset", Constants.FILE_LOCS.get("WEEKIMAGE_WEEKJSON")[1] + weekFilename.replace(".json", ""));
        return level;
    }
}
