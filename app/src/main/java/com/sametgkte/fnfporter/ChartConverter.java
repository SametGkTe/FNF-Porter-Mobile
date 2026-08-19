package com.sametgkte.fnfporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ChartConverter {
    public final String songFile;
    public String songName;
    public double startingBpm;
    public final List<JSONObject> sections = new ArrayList<JSONObject>();
    public JSONObject metadata;
    public JSONObject chart;

    private final File songPath;
    private final File savePath;
    private final boolean shouldConvertEvents;
    private final Map<String, JSONObject> charts = new LinkedHashMap<String, JSONObject>();
    private final List<String> difficulties = new ArrayList<String>();
    private JSONObject sampleChart;

    public ChartConverter(File path, File output, boolean events) throws Exception {
        this.songPath = path;
        this.savePath = output;
        this.shouldConvertEvents = events;
        this.songFile = path.getName();
        this.songName = songFile.replace("-", " ");
        this.metadata = Constants.baseChartMetadata();
        this.chart = Constants.baseChart();
        this.chart.put("events", new JSONArray());
        initCharts();
        try {
            setMetadata();
        } catch (Exception e) {
            AppLog.error("Failed to set metadata", e);
        }
        AppLog.info("Chart for " + metadata.optString("songName", songName) + " was created!");
    }

    private void initCharts() throws Exception {
        AppLog.info("Initialising charts for " + songName + "...");
        Set<String> unordered = new HashSet<String>();
        File[] files = songPath.listFiles();
        if (files == null) throw new java.io.FileNotFoundException("Chart not found!");

        for (File file : files) {
            if (!file.isFile() || !file.getName().toLowerCase(Locale.US).endsWith(".json")) continue;
            String stem = stripExt(file.getName());
            if ("events".equals(stem) && shouldConvertEvents) {
                convertEvents(file);
                continue;
            }

            String[] nameSplit = stem.split("-");
            String difficulty = "normal";
            if (nameSplit.length > 2) {
                difficulty = nameSplit[nameSplit.length - 1];
            } else if (nameSplit.length > 1 && !stem.equals(songFile)) {
                difficulty = nameSplit[1];
            }

            JSONObject root = new JSONObject(FileOps.readText(file));
            JSONObject songJson = root.optJSONObject("song");
            if (songJson != null) {
                unordered.add(difficulty);
                charts.put(difficulty, songJson);
            }
        }

        for (String d : Constants.DIFFICULTIES) {
            if (unordered.contains(d)) {
                difficulties.add(d);
                unordered.remove(d);
            }
        }
        difficulties.addAll(unordered);

        if (difficulties.isEmpty()) throw new java.io.FileNotFoundException("Chart not found!");
        sampleChart = charts.get(difficulties.get(0));
    }

    private void setMetadata() throws Exception {
        startingBpm = sampleChart.optDouble("bpm", 100);
        JSONObject playData = metadata.getJSONObject("playData");
        JSONObject characters = playData.getJSONObject("characters");

        String rawSong = sampleChart.optString("song", songName);
        songName = rawSong.replace("-", " ");
        // title-ish
        String[] words = songName.split(" ");
        StringBuilder titled = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (titled.length() > 0) titled.append(' ');
            titled.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) titled.append(w.substring(1));
        }
        songName = titled.toString();
        metadata.put("songName", songName);
        metadata.put("artist", "Unknown Artist");
        AppLog.info("Initialising metadata for " + songName + "...");

        characters.put("player", Utils.character(sampleChart.optString("player1", "bf")));
        String gf = sampleChart.has("gfVersion") ? sampleChart.optString("gfVersion") : sampleChart.optString("player3", "gf");
        characters.put("girlfriend", Utils.character(gf));
        characters.put("opponent", Utils.character(sampleChart.optString("player2", "dad")));

        JSONArray diffs = new JSONArray();
        for (String d : difficulties) diffs.put(d);
        playData.put("difficulties", diffs);
        playData.put("stage", Utils.stage(sampleChart.optString("stage", "mainStage")));

        JSONObject ratings = new JSONObject();
        for (String d : difficulties) ratings.put(d, 0);
        metadata.put("ratings", ratings);

        JSONArray timeChanges = new JSONArray();
        timeChanges.put(Utils.timeChange(0, startingBpm, 4, 4, 0, Utils.defaultTuplets()));
        metadata.put("timeChanges", timeChanges);
    }

    private void convertEvents(File file) {
        AppLog.info("Events conversion for " + songName + " started!");
        try {
            JSONObject fileJson = new JSONObject(FileOps.readText(file));
            JSONObject song = fileJson.optJSONObject("song");
            JSONArray eventsData = song == null ? new JSONArray() : song.optJSONArray("events");
            if (eventsData == null) eventsData = new JSONArray();
            JSONArray dest = chart.getJSONArray("events");
            for (int i = 0; i < eventsData.length(); i++) {
                JSONArray event = eventsData.optJSONArray(i);
                if (event == null) continue;
                double time = event.optDouble(0, 0);
                JSONArray inner = event.optJSONArray(1);
                if (inner == null || inner.length() == 0) continue;
                JSONArray first = inner.optJSONArray(0);
                if (first == null) continue;
                String eventType = first.optString(0, "");
                appendTypedEvent(dest, time, eventType, first.optString(1, ""), first.optString(2, ""), new HashSet<String>());
            }
            AppLog.info("Events conversion for " + songName + " complete!");
        } catch (Exception e) {
            AppLog.error("Failed converting events.json", e);
        }
    }

    public void convert() throws Exception {
        AppLog.info("Chart conversion for " + metadata.optString("songName") + " started!");
        JSONArray notes0 = sampleChart.optJSONArray("notes");
        boolean prevMustHit = true;
        if (notes0 != null && notes0.length() > 0) {
            JSONObject firstSec = notes0.optJSONObject(0);
            if (firstSec != null) prevMustHit = firstSec.optBoolean("mustHitSection", true);
        }
        double prevTime = 0;
        JSONArray events = chart.getJSONArray("events");
        events.put(Utils.focusCamera(0, prevMustHit));
        Set<String> existingEvents = new HashSet<String>();
        double stepCrochet = 15000.0 / (startingBpm == 0 ? 100 : startingBpm);

        int chartIndex = 0;
        for (Map.Entry<String, JSONObject> entry : charts.entrySet()) {
            String diff = entry.getKey();
            JSONObject cChart = entry.getValue();
            chart.getJSONObject("scrollSpeed").put(diff, cChart.optDouble("speed", 1));
            JSONArray notesOut = new JSONArray();
            chart.getJSONObject("notes").put(diff, notesOut);

            int steps = 0;
            Set<String> prevNotes = new HashSet<String>();
            int totalDuplicates = 0;
            JSONArray sectionsArr = cChart.optJSONArray("notes");
            if (sectionsArr == null) sectionsArr = new JSONArray();

            for (int s = 0; s < sectionsArr.length(); s++) {
                JSONObject section = sectionsArr.optJSONObject(s);
                if (section == null) continue;
                boolean mustHit = section.optBoolean("mustHitSection", true);
                boolean isDuet = false;
                JSONArray sectionNotes = section.optJSONArray("sectionNotes");
                if (sectionNotes == null) sectionNotes = new JSONArray();

                for (int n = 0; n < sectionNotes.length(); n++) {
                    JSONArray note = sectionNotes.optJSONArray(n);
                    if (note == null) continue;
                    double strumTime = note.optDouble(0, 0);
                    int noteData = note.optInt(1, 0);
                    Object length = note.length() > 2 ? note.opt(2) : 0;

                    if (noteData < 0 && shouldConvertEvents) {
                        AppLog.warn("Tried converting legacy event. Legacy events are currently not supported. Sorry!");
                        continue;
                    }
                    if (!mustHit) {
                        noteData = (noteData + 4) % 8;
                        if (!isDuet && noteData < 4) isDuet = true;
                    }

                    boolean dup = false;
                    for (String existing : prevNotes) {
                        String[] p = existing.split("\\|");
                        double et = Double.parseDouble(p[0]);
                        int ed = Integer.parseInt(p[1]);
                        if (Math.abs(et - strumTime) < 1 && ed == noteData) {
                            dup = true;
                            break;
                        }
                    }
                    if (dup) {
                        totalDuplicates++;
                        continue;
                    }
                    prevNotes.add(strumTime + "|" + noteData);

                    if (note.length() > 3 && "Alt Animation".equals(String.valueOf(note.opt(3)))) {
                        String target = (noteData >= 0 && noteData <= 3) ? "player" : "opponent";
                        String anim = altAnim(noteData);
                        String key = strumTime + "|" + target + "|" + anim;
                        if (!existingEvents.contains(key)) {
                            events.put(Utils.playAnimation(strumTime, target, anim, true));
                            existingEvents.add(key);
                        }
                    }
                    notesOut.put(Utils.note(noteData, length, strumTime));
                }

                if (chartIndex == 0) {
                    int lengthInSteps = section.has("lengthInSteps")
                            ? section.optInt("lengthInSteps", 16)
                            : (int) (section.optDouble("sectionBeats", 4) * 4);
                    double sectionBeats = section.has("sectionBeats")
                            ? section.optDouble("sectionBeats", 4)
                            : lengthInSteps / 4.0;
                    double bpm = section.optDouble("bpm", startingBpm);
                    boolean changeBPM = section.optBoolean("changeBPM", false);

                    JSONObject sec = new JSONObject();
                    sec.put("mustHitSection", mustHit);
                    sec.put("isDuet", isDuet);
                    sec.put("lengthInSteps", lengthInSteps);
                    sec.put("bpm", bpm);
                    sec.put("changeBPM", changeBPM);
                    sections.add(sec);

                    if (prevMustHit != mustHit) {
                        events.put(Utils.focusCamera(prevTime + steps * stepCrochet, mustHit));
                        prevMustHit = mustHit;
                    }
                    steps += lengthInSteps;
                    if (changeBPM) {
                        prevTime += steps * stepCrochet;
                        JSONArray bt = new JSONArray();
                        for (int i = 0; i < 4; i++) bt.put(sectionBeats);
                        metadata.getJSONArray("timeChanges").put(
                                Utils.timeChange(prevTime, bpm, (int) sectionBeats, (int) sectionBeats, 0, bt));
                        stepCrochet = 15000.0 / (bpm == 0 ? 100 : bpm);
                        steps = 0;
                    }
                }
            }

            if (totalDuplicates > 0) {
                AppLog.warn("We found " + totalDuplicates + " duplicate notes in '" + diff + "' difficulty data! Notes were successfully removed.");
            }

            if (shouldConvertEvents && cChart.has("events")) {
                JSONArray evs = cChart.optJSONArray("events");
                if (evs != null) {
                    for (int i = 0; i < evs.length(); i++) {
                        JSONArray event = evs.optJSONArray(i);
                        if (event == null) continue;
                        double time = event.optDouble(0, 0);
                        JSONArray all = event.optJSONArray(1);
                        if (all == null) continue;
                        for (int j = 0; j < all.length(); j++) {
                            JSONArray stacked = all.optJSONArray(j);
                            if (stacked == null) continue;
                            appendTypedEvent(events, time, stacked.optString(0, ""),
                                    stacked.optString(1, ""), stacked.optString(2, ""), existingEvents);
                        }
                    }
                }
            }
            chartIndex++;
        }
        AppLog.info("Chart conversion for " + metadata.optString("songName") + " was completed!");
    }

    private void appendTypedEvent(JSONArray dest, double time, String eventType, String a, String b, Set<String> existing) throws Exception {
        String target;
        if ("Play Animation".equals(eventType)) {
            String anim = a;
            target = normalizeTarget(b);
            String key = time + "|pa|" + target + "|" + anim;
            if (existing != null && existing.contains(key)) return;
            dest.put(Utils.playAnimation(time, target, anim, true));
            if (existing != null) existing.add(key);
        } else if ("Change Character".equals(eventType)) {
            target = normalizeTarget(a);
            String charName = b;
            String key = time + "|cc|" + target + "|" + charName;
            if (existing != null && existing.contains(key)) return;
            dest.put(Utils.changeCharacter(time, target, charName));
            if (existing != null) existing.add(key);
        } else if (eventType != null && !eventType.isEmpty()) {
            AppLog.warn("Conversion for event " + eventType + " is not implemented!");
        }
    }

    private static String normalizeTarget(String target) {
        if (target == null) return "bf";
        target = target.toLowerCase(Locale.US);
        if ("0".equals(target)) return "bf";
        if ("1".equals(target)) return "dad";
        if ("2".equals(target)) return "gf";
        return target;
    }

    private static String altAnim(int noteData) {
        switch (noteData) {
            case 0:
            case 4:
                return "singLEFT-alt";
            case 1:
            case 5:
                return "singDOWN-alt";
            case 2:
            case 6:
                return "singUP-alt";
            default:
                return "singRIGHT-alt";
        }
    }

    public void save() throws Exception {
        String newSongFile = Utils.formatToSongPath(songName);
        File folder = new File(savePath, Constants.FILE_LOCS.get("CHARTFOLDER")[1] + newSongFile);
        FileOps.folderMake(folder.getAbsolutePath());
        FileOps.writeText(new File(folder, songFile + "-metadata.json"), metadata.toString(2));
        FileOps.writeText(new File(folder, newSongFile + "-chart.json"), chart.toString(2));
        AppLog.info("[" + newSongFile + "] Saving " + songName + " to " + folder.getAbsolutePath());
    }

    public JSONObject summary() {
        try {
            JSONObject o = new JSONObject();
            o.put("songKey", songFile);
            JSONArray secs = new JSONArray();
            for (JSONObject s : sections) secs.put(s);
            o.put("sections", secs);
            o.put("bpm", startingBpm);
            JSONObject chars = metadata.getJSONObject("playData").getJSONObject("characters");
            o.put("player", chars.optString("player"));
            o.put("opponent", chars.optString("opponent"));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
