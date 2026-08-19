package com.sametgkte.fnfporter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ports a Psych Engine mod folder to official Base Game / Polymod layout.
 * Logic mirrors FNF-Porter/Porter.py (psychtobase/main.py).
 */
public class Converter {

    public static void convert(String psychModFolder, String resultFolder, JSONObject options) throws Exception {
        long runtime = System.currentTimeMillis();
        AppLog.banner("NEW CONVERSION STARTED");
        AppLog.info(options.toString());

        File modRoot = new File(psychModFolder);
        String modFoldername = modRoot.getName();
        File outRoot = new File(resultFolder, modFoldername);

        AppLog.info("Converting from " + psychModFolder + " to " + resultFolder);

        List<JSONObject> charts = new ArrayList<JSONObject>();
        Map<String, List<String>> characterMap = new HashMap<String, List<String>>();

        if (options.optBoolean("modpack_meta", false)) {
            convertMeta(modRoot, outRoot);
        }

        JSONObject chartOptions = Utils.optObj(options, "charts");
        if (chartOptions.optBoolean("songs", false)) {
            convertCharts(modRoot, outRoot, chartOptions.optBoolean("events", false), charts);
        }
        if (chartOptions.optBoolean("events", false)) {
            writeChangeCharacterScript(outRoot);
        }

        JSONObject charOpts = Utils.optObj(options, "characters");
        if (charOpts.optBoolean("assets", false)) {
            copyCharacterAssets(modRoot, outRoot);
        }
        if (charOpts.optBoolean("json", false)) {
            convertCharacters(modRoot, outRoot, characterMap);
        }
        if (charOpts.optBoolean("icons", false)) {
            copyIcons(modRoot, outRoot, characterMap);
        }

        JSONObject songOpts = Utils.optObj(options, "songs");
        if (songOpts.optBoolean("inst", false) || songOpts.optBoolean("voices", false)
                || songOpts.optBoolean("music", false) || songOpts.optBoolean("sounds", false)
                || songOpts.optBoolean("split", false)) {
            copyAudio(modRoot, outRoot, songOpts, charts);
        }

        JSONObject weekOpts = Utils.optObj(options, "weeks");
        if (weekOpts.optBoolean("levels", false)) convertWeeks(modRoot, outRoot);
        if (weekOpts.optBoolean("props", false)) copyWeekProps(modRoot, outRoot);
        if (weekOpts.optBoolean("titles", false)) copyWeekTitles(modRoot, outRoot);

        if (options.optBoolean("stages", false)) convertStages(modRoot, outRoot);
        if (options.optBoolean("images", false)) copyImages(modRoot, outRoot);

        AppLog.banner("CONVERSION COMPLETED");
        AppLog.info("Conversion done: Took " + ((System.currentTimeMillis() - runtime) / 1000.0) + "s");
    }

    private static void convertMeta(File modRoot, File outRoot) {
        AppLog.info("Converting pack.json");
        File pack = new File(modRoot, "pack.json");
        FileOps.folderMake(outRoot.getAbsolutePath());
        try {
            JSONObject meta;
            if (pack.exists()) {
                meta = PackConverter.convertPack(new JSONObject(FileOps.readText(pack)));
                AppLog.info("pack.json converted and saved");
            } else {
                meta = PackConverter.defaultPolymodMeta();
                AppLog.warn("pack.json not found. Replaced it with default");
            }
            FileOps.writeText(new File(outRoot, "_polymod_meta.json"), meta.toString(4));
        } catch (Exception e) {
            AppLog.error("Couldn't convert pack.json file", e);
        }

        AppLog.info("Copying pack.png");
        File packPng = new File(modRoot, "pack.png");
        File destIcon = new File(outRoot, "_polymod_icon.png");
        if (packPng.exists()) {
            FileOps.fileCopy(packPng.getAbsolutePath(), destIcon.getAbsolutePath());
        } else {
            AppLog.warn("pack.png not found. Replacing it with default");
            try {
                FileOps.writeBytes(destIcon, Constants.missingModImage());
            } catch (Exception e) {
                AppLog.error("Could not write default file", e);
            }
        }

        AppLog.info("Parsing and converting credits.txt");
        File credits = new File(modRoot, "data/credits.txt");
        if (credits.exists()) {
            try {
                String result = PackConverter.convertCredits(FileOps.readText(credits));
                FileOps.writeText(new File(outRoot, "mod-credits.txt"), result);
            } catch (Exception e) {
                AppLog.error("Could not convert credits.txt", e);
            }
        } else {
            AppLog.warn("Could not find " + credits.getAbsolutePath());
        }
    }

    private static void convertCharts(File modRoot, File outRoot, boolean events, List<JSONObject> charts) {
        File psychChartFolder = new File(modRoot, "data");
        FileOps.folderMake(new File(outRoot, "data/songs").getAbsolutePath());
        for (File song : FileOps.listAll(psychChartFolder)) {
            if (!song.isDirectory()) continue;
            AppLog.info("Loading charts in " + song.getAbsolutePath());
            try {
                ChartConverter songChart = new ChartConverter(song, outRoot, events);
                AppLog.info(song.getName() + " successfully initialized! Converting");
                songChart.convert();
                charts.add(songChart.summary());
                songChart.save();
            } catch (java.io.FileNotFoundException e) {
                AppLog.warn(song.getName() + " data not found! Skipping...");
            } catch (Exception e) {
                AppLog.error("Error converting chart " + song.getName(), e);
            }
        }
    }

    private static void writeChangeCharacterScript(File outRoot) {
        try {
            File scripts = new File(outRoot, "scripts");
            FileOps.folderMake(scripts.getAbsolutePath());
            File dest = new File(scripts, "ChangeCharacterEvent.hxc");
            // Content is shipped as an asset; MainActivity copies it into the converter via static holder.
            String contents = ChangeCharacterScript.CONTENTS;
            FileOps.writeText(dest, contents);
        } catch (Exception e) {
            AppLog.error("Failed creating the scripts folder", e);
        }
    }

    private static void copyCharacterAssets(File modRoot, File outRoot) {
        AppLog.info("Copying character assets...");
        File src = new File(modRoot, "images/characters");
        File dst = new File(outRoot, "shared/images/characters");
        FileOps.folderMake(dst.getAbsolutePath());
        for (File character : FileOps.listAll(src)) {
            if (character.isFile()) {
                AppLog.info("Copying asset " + character.getName());
                FileOps.fileCopy(character.getAbsolutePath(), new File(dst, character.getName()).getAbsolutePath());
            } else {
                AppLog.warn(character.getName() + " is a directory, not a file! Skipped");
            }
        }
    }

    private static void convertCharacters(File modRoot, File outRoot, Map<String, List<String>> characterMap) {
        AppLog.info("Converting character jsons...");
        File src = new File(modRoot, "characters");
        File dst = new File(outRoot, "data/characters");
        FileOps.folderMake(dst.getAbsolutePath());
        for (File character : FileOps.listAll(src)) {
            AppLog.info("Checking if " + character.getName() + " is a file...");
            if (character.isFile() && character.getName().toLowerCase(Locale.US).endsWith(".json")) {
                try {
                    CharacterConverter converted = new CharacterConverter(character);
                    converted.save(dst);
                    String fileBasename = converted.iconID.replace("icon-", "");
                    if (!characterMap.containsKey(fileBasename)) {
                        characterMap.put(fileBasename, new ArrayList<String>());
                    }
                    characterMap.get(fileBasename).add(converted.characterName);
                    AppLog.info("Saved " + converted.characterName + " to character map using their icon id: " + fileBasename + ".");
                } catch (Exception e) {
                    AppLog.error("Failed to convert character " + character.getName(), e);
                }
            } else {
                AppLog.warn(character.getName() + " is a directory, or not a json! Skipped");
            }
        }
    }

    private static void copyIcons(File modRoot, File outRoot, Map<String, List<String>> characterMap) {
        AppLog.info("Copying character icons...");
        File src = new File(modRoot, "images/icons");
        File dst = new File(outRoot, "images/icons");
        File freeplay = new File(outRoot, "images/freeplay/icons");
        FileOps.folderMake(dst.getAbsolutePath());
        FileOps.folderMake(freeplay.getAbsolutePath());
        for (File character : FileOps.listFiles(src, ".png")) {
            AppLog.info("Copying asset " + character.getName());
            try {
                String filename = character.getName();
                if (!filename.startsWith("icon-")) {
                    AppLog.warn("Invalid icon name being renamed from '" + filename + "' to 'icon-" + filename + "'!");
                    filename = "icon-" + filename;
                }
                File dest = new File(dst, filename);
                FileOps.fileCopy(character.getAbsolutePath(), dest.getAbsolutePath());

                String key = filename.replace("icon-", "").replace(".png", "");
                AppLog.info("Checking if " + key + " is in the characterMap");
                if (characterMap.containsKey(key)) {
                    try {
                        Bitmap img = BitmapFactory.decodeFile(character.getAbsolutePath());
                        if (img != null) {
                            int w = Math.min(150, img.getWidth());
                            int h = Math.min(150, img.getHeight());
                            Bitmap half = Bitmap.createBitmap(img, 0, 0, w, h);
                            Bitmap pixel = Bitmap.createScaledBitmap(half, 50, 50, false);
                            for (String characterName : characterMap.get(key)) {
                                File fp = new File(freeplay, characterName + "pixel.png");
                                FileOutputStream fos = new FileOutputStream(fp);
                                pixel.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.close();
                                AppLog.info("Saving converted freeplay icon to " + fp.getAbsolutePath());
                            }
                            if (pixel != half) pixel.recycle();
                            half.recycle();
                            img.recycle();
                        }
                    } catch (Exception e) {
                        AppLog.error("Failed to create character " + key + "'s freeplay icon", e);
                    }
                }
            } catch (Exception e) {
                AppLog.error("Could not copy asset " + character.getName(), e);
            }
        }
    }

    private static void copyAudio(File modRoot, File outRoot, JSONObject songOptions, List<JSONObject> charts) {
        File psychSongs = new File(modRoot, "songs");
        File bgSongs = new File(outRoot, "songs");
        boolean splitRequested = songOptions.optBoolean("split", false);
        if (splitRequested) {
            AppLog.warn("Vocal Split requires FFmpeg and is not available in the Android build. Voices.ogg will be copied instead.");
        }

        for (File song : FileOps.listAll(psychSongs)) {
            String unformatted = song.getName();
            String formatted = unformatted.replace(" ", "-").toLowerCase(Locale.US);
            AppLog.info("Checking if " + song.getName() + " is a valid song directory...");
            if (!song.isDirectory()) continue;
            AppLog.info("Copying files in " + song.getName());

            List<File> audios = FileOps.listAll(song);
            Set<String> names = new HashSet<String>();
            for (File a : audios) names.add(a.getName());
            boolean isPsych073 = names.contains("Voices-Opponent.ogg") && names.contains("Voices-Player.ogg");

            JSONObject chart = findChart(charts, unformatted);

            for (File songFile : audios) {
                String name = songFile.getName();
                File destDir = new File(bgSongs, formatted);

                if ("Inst.ogg".equals(name) && songOptions.optBoolean("inst", false)) {
                    AppLog.info("Copying asset " + name);
                    FileOps.folderMake(destDir.getAbsolutePath());
                    FileOps.fileCopy(songFile.getAbsolutePath(), new File(destDir, name).getAbsolutePath());
                } else if (isPsych073 && chart != null && ("Voices-Player.ogg".equals(name) || "Voices-Opponent.ogg".equals(name))) {
                    FileOps.folderMake(destDir.getAbsolutePath());
                    try {
                        String who = "Voices-Player.ogg".equals(name)
                                ? chart.optString("player", "bf")
                                : chart.optString("opponent", "dad");
                        FileOps.fileCopy(songFile.getAbsolutePath(), new File(destDir, "Voices-" + who + ".ogg").getAbsolutePath());
                    } catch (Exception e) {
                        AppLog.error("Could not copy asset " + name, e);
                    }
                } else if (isPsych073) {
                    AppLog.warn(formatted + " is a Psych Engine 0.7.3 song with separated vocals. Chart was not found; copying original names.");
                    FileOps.folderMake(destDir.getAbsolutePath());
                    FileOps.fileCopy(songFile.getAbsolutePath(), new File(destDir, name).getAbsolutePath());
                } else if (songOptions.optBoolean("voices", false) || ("Voices.ogg".equals(name) && splitRequested)) {
                    AppLog.info("Copying asset " + name);
                    FileOps.folderMake(destDir.getAbsolutePath());
                    FileOps.fileCopy(songFile.getAbsolutePath(), new File(destDir, name).getAbsolutePath());
                }
            }
        }

        if (songOptions.optBoolean("sounds", false)) {
            copyTreeOrFiles(new File(modRoot, "sounds"), new File(outRoot, "sounds"));
        }
        if (songOptions.optBoolean("music", false)) {
            File dst = new File(outRoot, "music");
            FileOps.folderMake(dst.getAbsolutePath());
            for (File asset : FileOps.listAll(new File(modRoot, "music"))) {
                AppLog.info("Copying asset " + asset.getName());
                if (asset.isFile()) {
                    FileOps.fileCopy(asset.getAbsolutePath(), new File(dst, asset.getName()).getAbsolutePath());
                }
            }
        }
    }

    private static JSONObject findChart(List<JSONObject> charts, String songKey) {
        for (JSONObject c : charts) {
            if (songKey.equals(c.optString("songKey"))) return c;
        }
        return null;
    }

    private static void copyTreeOrFiles(File src, File dst) {
        for (File asset : FileOps.listAll(src)) {
            AppLog.info("Checking on " + asset.getName());
            if (asset.isDirectory()) {
                AppLog.info(asset.getName() + " is a tree, attempting to copy it");
                FileOps.treeCopy(asset.getAbsolutePath(), new File(dst, asset.getName()).getAbsolutePath());
            } else {
                AppLog.info(asset.getName() + " is file, copying");
                FileOps.folderMake(dst.getAbsolutePath());
                FileOps.fileCopy(asset.getAbsolutePath(), new File(dst, asset.getName()).getAbsolutePath());
            }
        }
    }

    private static void convertWeeks(File modRoot, File outRoot) {
        AppLog.info("Converting weeks (levels)...");
        File src = new File(modRoot, "weeks");
        File dst = new File(outRoot, "data/levels");
        FileOps.folderMake(dst.getAbsolutePath());
        for (File week : FileOps.listFiles(src, ".json")) {
            try {
                AppLog.info("Loading " + week.getName() + " into the converter...");
                JSONObject weekJSON = new JSONObject(FileOps.readText(week));
                JSONObject converted = WeekConverter.convert(weekJSON, modRoot, week.getName());
                FileOps.writeText(new File(dst, week.getName()), converted.toString(4));
            } catch (Exception e) {
                AppLog.error("Error converting week " + week.getName(), e);
            }
        }
    }

    private static void copyWeekProps(File modRoot, File outRoot) {
        AppLog.info("Copying prop assets...");
        File src = new File(modRoot, "images/menucharacters");
        File dst = new File(outRoot, "images/storymenu/props");
        FileOps.folderMake(dst.getAbsolutePath());
        List<File> assets = new ArrayList<File>();
        assets.addAll(FileOps.listFiles(src, ".xml"));
        assets.addAll(FileOps.listFiles(src, ".png"));
        for (File asset : assets) {
            AppLog.info("Copying " + asset.getName());
            FileOps.fileCopy(asset.getAbsolutePath(), new File(dst, asset.getName()).getAbsolutePath());
        }
    }

    private static void copyWeekTitles(File modRoot, File outRoot) {
        AppLog.info("Copying level titles...");
        File src = new File(modRoot, "images/storymenu");
        File dst = new File(outRoot, "images/storymenu/titles");
        FileOps.folderMake(dst.getAbsolutePath());
        for (File asset : FileOps.listFiles(src, ".png")) {
            AppLog.info("Copying week title asset: " + asset.getName());
            FileOps.fileCopy(asset.getAbsolutePath(), new File(dst, asset.getName()).getAbsolutePath());
        }
    }

    private static void convertStages(File modRoot, File outRoot) {
        AppLog.info("Converting stages...");
        File src = new File(modRoot, "stages");
        File dst = new File(outRoot, "data/stages");
        FileOps.folderMake(dst.getAbsolutePath());
        for (File asset : FileOps.listFiles(src, ".json")) {
            AppLog.info("Converting " + asset.getName());
            try {
                JSONObject stageJSON = new JSONObject(FileOps.readText(asset));
                File lua = new File(asset.getAbsolutePath().replace(".json", ".lua"));
                JSONArray luaProps = new JSONArray();
                if (lua.exists()) {
                    AppLog.info("Parsing .lua with matching .json name: " + lua.getName());
                    luaProps = StageConverter.parseStageLua(lua);
                }
                JSONObject converted = StageConverter.convert(stageJSON, asset.getName(), luaProps);
                FileOps.writeText(new File(dst, asset.getName()), converted.toString(4));
            } catch (Exception e) {
                AppLog.error("Could not convert stage " + asset.getName(), e);
            }
        }
    }

    private static void convertScripts(File modRoot, File outRoot) {
        AppLog.info("Converting scripts (Lua -> HScript)...");
        int[] n = new int[]{0};
        luaToHxcTree(new File(modRoot, "scripts"), new File(outRoot, "scripts"), n);
        luaToHxcTree(new File(modRoot, "custom_notetypes"), new File(outRoot, "scripts"), n);
        luaToHxcTree(new File(modRoot, "custom_events"), new File(outRoot, "scripts"), n);
        File data = new File(modRoot, "data");
        if (data.isDirectory()) {
            for (File song : FileOps.listAll(data)) {
                if (!song.isDirectory()) continue;
                luaToHxcTree(song, new File(outRoot, "data/songs/" + song.getName()), n);
            }
        }
        if (n[0] == 0) AppLog.warn("No Lua scripts found");
        else AppLog.info("  " + n[0] + " Lua script(s) converted to .hxc");
    }

    private static void luaToHxcTree(File src, File dst, int[] n) {
        if (src == null || !src.exists()) return;
        File[] kids = src.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                luaToHxcTree(f, new File(dst, f.getName()), n);
                continue;
            }
            if (!f.getName().toLowerCase(Locale.US).endsWith(".lua")) continue;
            try {
                FileOps.folderMake(dst.getAbsolutePath());
                String lua = FileOps.readText(f);
                LuaToHScript conv = new LuaToHScript();
                String hx = conv.convert(lua);
                String outName = f.getName().replaceAll("(?i)\\.lua$", ".hxc");
                FileOps.writeText(new File(dst, outName), hx);
                AppLog.info("  " + f.getName() + " -> " + outName);
                if (conv.warnings.size() > 0) {
                    AppLog.warn("    " + conv.warnings.size() + " line(s) need manual check");
                }
                n[0]++;
            } catch (Exception e) {
                AppLog.error("  Failed " + f.getName(), e);
            }
        }
    }

    private static void copyImages(File modRoot, File outRoot) {
        AppLog.info("Copying images");
        File src = new File(modRoot, "images");
        File dst = new File(outRoot, "shared/images");
        for (File asset : FileOps.listAll(src)) {
            AppLog.info("Checking on " + asset.getName());
            if (asset.isDirectory()) {
                AppLog.info(asset.getName() + " is directory, checking if it should be excluded...");
                if (!Constants.EXCLUDE_IMAGE_FOLDERS.contains(asset.getName())) {
                    AppLog.info(asset.getName() + " is not excluded... attempting to copy.");
                    FileOps.treeCopy(asset.getAbsolutePath(), new File(dst, asset.getName()).getAbsolutePath());
                } else {
                    AppLog.warn(asset.getName() + " is excluded. Skipped");
                }
            } else {
                AppLog.info(asset.getName() + " is file, copying");
                FileOps.folderMake(dst.getAbsolutePath());
                FileOps.fileCopy(asset.getAbsolutePath(), new File(dst, asset.getName()).getAbsolutePath());
            }
        }
    }
}
