package com.farmlist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Saved state for one checklist, in .minecraft/farmlist/progress/<name>.properties.
 * Keys are item IDs (value "true"/"false" for checked), "choice.<row>" (chosen block ID), or "have.<row>" (count found by the last Scan).
 * Saved on this computer only.
 */
public final class Progress {

    static final Path PROGRESS_DIR =
            FarmListClient.TRANSCRIPTION_DIR.getParent().resolve("progress");

    private final Path file;
    private final Map<String, String> values = new LinkedHashMap<>();

    public Progress(String checklistName) {
        this.file = PROGRESS_DIR.resolve(checklistName + ".properties");
        load();
    }

    public boolean isChecked(String rowId) {
        return Boolean.parseBoolean(values.getOrDefault(rowId, "false"));
    }

    public boolean isComplete() {
        return isChecked("_complete");
    }

    public void setComplete(boolean value) {
        set("_complete", value);
    }

    public void set(String rowId, boolean value) {
        values.put(rowId, Boolean.toString(value));
        save();
    }

    /** The block chosen for a building_block row, or null if none chosen yet. */
    public String getChoice(String rowId) {
        return values.get("choice." + rowId);
    }

    public void setChoice(String rowId, String blockId) {
        values.put("choice." + rowId, blockId);
        values.remove("have." + rowId);   // the old block's count no longer applies
        save();
    }

    /** How many the last Scan found in the inventory for this row, or null if never scanned. */
    public Integer getHave(String rowId) {
        String v = values.get("have." + rowId);
        if (v == null) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void setHave(String rowId, int have) {
        values.put("have." + rowId, Integer.toString(have));
        save();
    }

    /** Id shared by everyone collaborating on this list, or null if it is not a shared project. */
    public String projectId() {
        return values.get("_project");
    }

    public void setProjectId(String id) {
        values.put("_project", id);
        save();
    }

    /** True if the player typed the count by hand (so Scan must not lower it). */
    public boolean isManual(String rowId) {
        return Boolean.parseBoolean(values.getOrDefault("manual." + rowId, "false"));
    }

    /** Sets how many the player has, by hand. */
    public void setManualHave(String rowId, int have) {
        values.put("have." + rowId, Integer.toString(have));
        values.put("manual." + rowId, "true");
        save();
    }

    /** Forgets everything saved for a row (used when it is removed). */
    public void clearRow(String rowId) {
        dropRow(rowId);
        save();
    }

    /** The row was changed to a different item: keep its check mark, drop counts that belonged to the old item. */
    public void rename(String oldRow, String newRow) {
        if (oldRow.equals(newRow)) return;
        boolean checked = isChecked(oldRow) || isChecked(newRow);
        dropRow(oldRow);
        if (checked) values.put(newRow, "true");
        save();
    }

    private void dropRow(String rowId) {
        values.remove(rowId);
        values.remove("have." + rowId);
        values.remove("manual." + rowId);
        values.remove("choice." + rowId);
    }

    private void load() {
        if (!Files.exists(file)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            values.put(key, props.getProperty(key));
        }
    }

    private void save() {
        Properties props = new Properties();
        values.forEach(props::setProperty);
        try {
            Files.createDirectories(PROGRESS_DIR);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Farm Checklist progress");
            }
        } catch (IOException e) {
            FarmListClient.message("Could not save progress: " + e.getMessage());
        }
    }
}
