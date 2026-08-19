package com.sametgkte.fnfporter;

import org.json.JSONArray;
import org.json.JSONObject;

public class PackConverter {

    public static JSONObject convertPack(JSONObject packJson) throws Exception {
        String title = packJson.optString("name", "Untitled Mod");
        String description = packJson.optString("description", generateDescription(title));
        return meta(title, description);
    }

    public static JSONObject defaultPolymodMeta() throws Exception {
        return meta("Untitled Mod", generateDescription("Untitled Mod"));
    }

    private static JSONObject meta(String title, String description) throws Exception {
        JSONObject o = new JSONObject();
        o.put("title", title);
        o.put("description", description);
        o.put("contributors", new JSONArray());
        o.put("dependencies", new JSONObject());
        o.put("optionalDependencies", new JSONObject());
        o.put("api_version", Constants.POLYMOD_API_VERSION);
        o.put("mod_version", "1.0.0");
        o.put("license", "Apache-2.0");
        return o;
    }

    public static String generateDescription(String name) {
        return name + " by Unknown creator. Converted by FNF Porter v" + Constants.VERSION;
    }

    public static String convertCredits(String text) {
        StringBuilder result = new StringBuilder("Mod credits\n");
        if (text == null) return result.toString();
        String[] lines = text.split("\n");
        for (String line : lines) {
            String[] data = line.split("::");
            if (data.length > 1) {
                String person = data[0];
                String roleDesc = data.length > 2 ? data[2] : "";
                String social = data.length > 3 ? data[3] : "";
                result.append(roleDesc).append(" - ").append(person).append(" (").append(social).append(")\n");
            }
        }
        return result.toString();
    }
}
