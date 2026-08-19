package com.sametgkte.fnfporter;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Constants {
    public static final String VERSION = "1.0.0";
    public static final String POLYMOD_API_VERSION = "0.8.5";
    public static final String GENERATED_BY = "FNF Porter For Mobile — original Porter by Gusborg, tposejank, BombasticTom & VocalFan — Android by SametGkTe";

    public static final List<String> DIFFICULTIES = Arrays.asList("easy", "normal", "hard");

    public static final Set<String> EXCLUDE_IMAGE_FOLDERS = new HashSet<>(Arrays.asList(
            "menubackgrounds", "icons", "dialogue", "storymenu",
            "menucharacters", "achievements", "credits", "characters"
    ));

    public static final Map<String, String[]> FILE_LOCS = new HashMap<String, String[]>();

    static {
        FILE_LOCS.put("PACKJSON", new String[]{"/pack.json", "/_polymod_meta.json"});
        FILE_LOCS.put("PACKPNG", new String[]{"/pack.png", "/_polymod_icon.png"});
        FILE_LOCS.put("CREDITSTXT", new String[]{"/data/credits.txt", "/mod-credits.txt"});
        FILE_LOCS.put("CHARACTERASSETS", new String[]{"/images/characters/", "/shared/images/characters/"});
        FILE_LOCS.put("CHARACTERJSONS", new String[]{"/characters/", "/data/characters/"});
        FILE_LOCS.put("CHARACTERICON", new String[]{"/images/icons/", "/images/icons/"});
        FILE_LOCS.put("CHARTFOLDER", new String[]{"/data/", "/data/songs/"});
        FILE_LOCS.put("SONGS", new String[]{"/songs/", "/songs/"});
        FILE_LOCS.put("SOUNDS", new String[]{"/sounds/", "/sounds/"});
        FILE_LOCS.put("MUSIC", new String[]{"/music/", "/music/"});
        FILE_LOCS.put("WEEKS", new String[]{"/weeks/", "/data/levels/"});
        FILE_LOCS.put("WEEKCHARACTERASSET", new String[]{"/images/menucharacters/", "/images/storymenu/props/"});
        FILE_LOCS.put("WEEKCHARACTERJSON", new String[]{"/images/menucharacters/", ""});
        FILE_LOCS.put("WEEKIMAGE", new String[]{"/images/storymenu/", "/images/storymenu/titles/"});
        FILE_LOCS.put("WEEKIMAGE_WEEKJSON", new String[]{"", "storymenu/titles/"});
        FILE_LOCS.put("STAGE", new String[]{"/stages/", "/data/stages/"});
        FILE_LOCS.put("IMAGES", new String[]{"/images/", "/shared/images/"});
        FILE_LOCS.put("FREEPLAYICON", new String[]{"/images/icons/", "/images/freeplay/icons"});
        FILE_LOCS.put("SCRIPTS_DIR", new String[]{"/scripts/", "/scripts/"});
    }

    public static final String MISSING_MOD_IMAGE_B64 =
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABAAQMAAACQp+OdAAAABlBMVEX///8AAABVwtN+AAAACXBIWXMAAAsSAAALEgHS3X78AAAAyUlEQVQoz2NgoBlghlAGDMwz0BnsSAwmdIYF838w40YN8/8DQAYbkCH/4AOQ8cOHmefjDiCjAsgoltzAwFKRw8xjLG3AwCJRw8xgPBvIYLBgBDMg4PcGKKP4A5TB+IEINwMZzZhCMJ0gZ4GNapY9+AHC4Dn8xwbCeNxTbwdi8D3mO2wPZhTzNZeDGGxARnIDiGHc1w5hGN77Xw9mGPyrrwepYWAoswe6nbGJgYGNzQLIAAYKGx/IQKDpEjx8EIb8D6hL+H8wDDAAAEa9OlI0tsK2AAAAAElFTkSuQmCC";

    public static byte[] missingModImage() {
        return Base64.decode(MISSING_MOD_IMAGE_B64, Base64.DEFAULT);
    }

    public static JSONObject baseChartMetadata() throws JSONException {
        JSONObject meta = new JSONObject();
        meta.put("version", "2.2.0");
        meta.put("songName", "");
        meta.put("artist", "");
        meta.put("looped", false);

        JSONObject offsets = new JSONObject();
        offsets.put("instrumental", 0);
        offsets.put("altInstrumentals", new JSONObject());
        offsets.put("vocals", new JSONObject());
        meta.put("offsets", offsets);

        JSONObject play = new JSONObject();
        play.put("album", "volume1");
        play.put("previewStart", 0);
        play.put("previewEnd", 15000);
        play.put("songVariations", new JSONArray());
        play.put("difficulties", new JSONArray());

        JSONObject chars = new JSONObject();
        chars.put("album", "volume1");
        chars.put("player", "bf");
        chars.put("girlfriend", "gf");
        chars.put("opponent", "dad");
        chars.put("instrumental", "");
        chars.put("altInstrumentals", new JSONArray());
        play.put("characters", chars);
        play.put("stage", "mainStage");
        play.put("noteStyle", "funkin");
        play.put("ratings", new JSONObject());
        meta.put("playData", play);

        meta.put("timeFormat", "ms");
        meta.put("timeChanges", new JSONArray());
        meta.put("generatedBy", GENERATED_BY);
        return meta;
    }

    public static JSONObject baseChart() throws JSONException {
        JSONObject c = new JSONObject();
        c.put("version", "2.0.0");
        c.put("scrollSpeed", new JSONObject());
        c.put("events", new JSONArray());
        c.put("notes", new JSONObject());
        c.put("generatedBy", GENERATED_BY);
        return c;
    }

    public static JSONObject characterTemplate() throws JSONException {
        JSONObject c = new JSONObject();
        c.put("version", "1.0.0");
        c.put("name", JSONObject.NULL);
        c.put("assetPath", JSONObject.NULL);
        c.put("singTime", JSONObject.NULL);
        c.put("isPixel", JSONObject.NULL);
        c.put("scale", JSONObject.NULL);
        JSONObject icon = new JSONObject();
        icon.put("id", JSONObject.NULL);
        icon.put("isPixel", JSONObject.NULL);
        icon.put("flipX", false);
        icon.put("scale", 1);
        c.put("healthIcon", icon);
        c.put("animations", new JSONArray());
        return c;
    }

    public static JSONObject animationTemplate() throws JSONException {
        JSONObject a = new JSONObject();
        a.put("name", JSONObject.NULL);
        a.put("prefix", JSONObject.NULL);
        JSONArray off = new JSONArray();
        off.put(0);
        off.put(0);
        a.put("offsets", off);
        a.put("frameRate", 24);
        a.put("frameIndices", new JSONArray());
        return a;
    }

    public static JSONObject levelTemplate() throws JSONException {
        JSONObject l = new JSONObject();
        l.put("version", "1.0.0");
        l.put("name", JSONObject.NULL);
        l.put("titleAsset", JSONObject.NULL);
        l.put("props", new JSONArray());
        l.put("background", JSONObject.NULL);
        l.put("songs", new JSONArray());
        return l;
    }

    public static JSONObject levelProp() throws JSONException {
        JSONObject p = new JSONObject();
        p.put("assetPath", JSONObject.NULL);
        p.put("scale", JSONObject.NULL);
        p.put("offsets", new JSONArray());
        p.put("animations", new JSONArray());
        return p;
    }

    public static JSONObject levelPropAnim() throws JSONException {
        JSONObject a = new JSONObject();
        a.put("name", JSONObject.NULL);
        a.put("prefix", JSONObject.NULL);
        a.put("frameRate", 24);
        return a;
    }

    public static JSONObject stageTemplate() throws JSONException {
        JSONObject s = new JSONObject();
        s.put("props", new JSONArray());
        s.put("cameraZoom", JSONObject.NULL);
        s.put("version", "1.0.0");
        JSONObject chars = new JSONObject();
        chars.put("bf", charPos(300, -100, -100));
        chars.put("dad", charPos(200, 150, -100));
        chars.put("gf", charPos(100, 0, 50));
        s.put("characters", chars);
        s.put("name", JSONObject.NULL);
        return s;
    }

    private static JSONObject charPos(int z, int camX, int camY) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("zIndex", z);
        o.put("position", JSONObject.NULL);
        JSONArray cam = new JSONArray();
        cam.put(camX);
        cam.put(camY);
        o.put("cameraOffsets", cam);
        return o;
    }

    public static JSONObject stagePropImage() throws JSONException {
        JSONObject p = new JSONObject();
        p.put("danceEvery", 0);
        p.put("zIndex", JSONObject.NULL);
        JSONArray pos = new JSONArray(); pos.put(0); pos.put(0);
        p.put("position", pos);
        JSONArray scale = new JSONArray(); scale.put(1); scale.put(1);
        p.put("scale", scale);
        p.put("name", JSONObject.NULL);
        p.put("isPixel", false);
        p.put("assetPath", JSONObject.NULL);
        JSONArray scroll = new JSONArray(); scroll.put(1); scroll.put(1);
        p.put("scroll", scroll);
        return p;
    }

    public static JSONObject stagePropAnimated() throws JSONException {
        JSONObject p = stagePropImage();
        p.put("animType", "sparrow");
        p.put("startingAnimation", "Idle");
        p.put("animations", new JSONArray());
        return p;
    }

    public static JSONObject stagePropAnimation() throws JSONException {
        JSONObject a = new JSONObject();
        JSONArray off = new JSONArray(); off.put(0); off.put(0);
        a.put("offsets", off);
        a.put("flipY", false);
        a.put("frameRate", 24);
        a.put("prefix", JSONObject.NULL);
        a.put("looped", true);
        a.put("flipX", false);
        a.put("name", JSONObject.NULL);
        return a;
    }

    public static JSONObject defaultProp(String name) {
        try {
            if ("bf".equals(name)) {
                return prop("storymenu/props/bf", 1.0, 150, 80,
                        anim("idle", "idle0", 24, null),
                        anim("confirm", "confirm0", 24, null));
            }
            if ("gf".equals(name)) {
                return prop("storymenu/props/gf", 1.0, 200, 80,
                        anim("danceLeft", "idle0", 0, new int[]{30,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14}),
                        anim("danceRight", "idle0", 0, new int[]{15,16,17,18,19,20,21,22,23,24,25,26,27,28,29}));
            }
            if ("dad".equals(name)) {
                return prop("storymenu/props/dad", 1.0, 100, 60, anim("idle", "idle0", 24, null));
            }
            if ("spooky".equals(name)) {
                return prop("storymenu/props/spooky", 1.0, 100, 120,
                        anim("danceLeft", "idle0", 0, new int[]{0,1,2,3,4,5,6,7}),
                        anim("danceRight", "idle0", 0, new int[]{8,9,10,11,12,13,14,15}));
            }
            if ("pico".equals(name)) {
                return prop("storymenu/props/pico", 1.0, 100, 120, anim("idle", "idle0", 24, null));
            }
            if ("mom".equals(name)) {
                return prop("storymenu/props/mom", 0.9, 120, 50, anim("idle", "idle0", 24, null));
            }
            if ("parents-christmas".equals(name)) {
                return prop("storymenu/props/parents-xmas", 0.9, 10, 60, anim("idle", "idle0", 24, null));
            }
            if ("senpai".equals(name)) {
                return prop("storymenu/props/senpai", 1.0, 60, 100, anim("idle", "idle0", 24, null));
            }
            if ("tankman".equals(name)) {
                return prop("storymenu/props/tankman", 1.0, 100, 100, anim("idle", "idle0", 24, null));
            }
            if ("darnell".equals(name)) {
                return prop("storymenu/props/darnell", 1.0, 120, 120, anim("idle", "idle0", 24, null));
            }
        } catch (JSONException ignored) {
        }
        return null;
    }

    private static JSONObject anim(String name, String prefix, int fps, int[] indices) throws JSONException {
        JSONObject a = new JSONObject();
        a.put("name", name);
        a.put("prefix", prefix);
        if (fps > 0) a.put("frameRate", fps);
        if (indices != null) {
            JSONArray arr = new JSONArray();
            for (int i : indices) arr.put(i);
            a.put("frameIndices", arr);
        }
        return a;
    }

    private static JSONObject prop(String asset, double scale, int ox, int oy, JSONObject... anims) throws JSONException {
        JSONObject p = new JSONObject();
        p.put("assetPath", asset);
        p.put("scale", scale);
        JSONArray off = new JSONArray();
        off.put(ox);
        off.put(oy);
        p.put("offsets", off);
        JSONArray as = new JSONArray();
        for (JSONObject a : anims) as.put(a);
        p.put("animations", as);
        return p;
    }
}
