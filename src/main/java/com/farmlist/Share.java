package com.farmlist;

import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Share and collaborate without a server, using a text code (copied to the clipboard).
 *
 * Share: copies a code with the whole list and your progress. The other player pastes it into Import and gets their own copy.
 * Collaborate: the same, but the list is marked as a shared project. When someone imports a code for a project they
 * already have, the lists are merged instead of copied: rows are added, check marks combine, and the higher Have wins.
 * Pass the code around whenever you want to sync (the Sync button in a checklist merges a code from your clipboard).
 */
final class Share {

    private Share() {
    }

    static final String PREFIX = "FARMLIST1:";
    private static final int MAX_BYTES = 1 << 20;

    record RowData(String id, int count, boolean checked, Integer have, boolean manual) {
    }

    record Decoded(String title, String project, boolean done, List<RowData> rows) {
    }

    static boolean isCode(String text) {
        return text != null && text.trim().startsWith(PREFIX);
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // ---------- making a code ----------

    /** Copies a code for this checklist to the clipboard. collab = also mark it as a shared project. */
    static void copyCode(String fileName, boolean collab, boolean withProgress) {
        String code = buildCode(fileName, collab, withProgress);
        if (code == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        FarmListClient.message((withProgress ? "Code with progress" : "Code (list only)") + " copied. Send it to your friend.");
    }

    /** Makes a share code for a checklist, or null on error. withProgress = include check marks and Have counts. */
    static String buildCode(String fileName, boolean collab, boolean withProgress) {
        try {
            String base = FarmListClient.baseName(fileName);
            Progress p = new Progress(base);
            if (collab && p.projectId() == null) p.setProjectId(UUID.randomUUID().toString());
            Map<String, Integer> rows = FarmListClient.parseRequired(FarmListClient.TRANSCRIPTION_DIR.resolve(fileName));

            StringBuilder sb = new StringBuilder();
            sb.append("title\t").append(clean(base)).append('\n');
            if (collab) sb.append("project\t").append(p.projectId()).append('\n');
            sb.append("done\t").append(withProgress && p.isComplete() ? 1 : 0).append('\n');
            for (Map.Entry<String, Integer> e : rows.entrySet()) {
                String id = FarmListClient.countedId(e.getKey(), p);   // an old building_block row is sent as the real block
                Integer have = withProgress ? p.getHave(e.getKey()) : null;
                sb.append("row\t").append(id).append('\t').append(e.getValue()).append('\t')
                        .append(withProgress && p.isChecked(e.getKey()) ? 1 : 0).append('\t')
                        .append(have == null ? "-" : have.toString()).append('\t')
                        .append(withProgress && p.isManual(e.getKey()) ? 1 : 0).append('\n');
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
                gz.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            FarmListClient.message("Could not make a code: " + e.getMessage());
            return null;
        }
    }

    // ---------- reading a code ----------

    static Decoded decode(String text) {
        try {
            String body = text.trim().substring(PREFIX.length()).replaceAll("\\s+", "");
            byte[] raw = Base64.getUrlDecoder().decode(body);
            byte[] plain;
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                plain = gz.readNBytes(MAX_BYTES + 1);
            }
            if (plain.length > MAX_BYTES) return null;
            String title = "Shared checklist";
            String project = null;
            boolean done = false;
            List<RowData> rows = new ArrayList<>();
            for (String line : new String(plain, StandardCharsets.UTF_8).split("\n")) {
                String[] f = line.split("\t");
                if (f.length < 2) continue;
                switch (f[0]) {
                    case "title" -> title = f[1].trim();
                    case "project" -> project = f[1].trim();
                    case "done" -> done = f[1].equals("1");
                    case "row" -> {
                        if (f.length < 6) break;
                        int count = Integer.parseInt(f[2]);
                        Integer have = f[4].equals("-") ? null : Integer.valueOf(f[4]);
                        rows.add(new RowData(f[1], count, f[3].equals("1"), have, f[5].equals("1")));
                    }
                    default -> {
                    }
                }
            }
            if (title.isEmpty()) title = "Shared checklist";
            return new Decoded(title, project, done, rows);
        } catch (IllegalArgumentException | IOException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Imports a code. If it is a project you already have, merges into that list; otherwise creates a new list
     * (never overwriting one). Returns the file name to open, or null.
     */
    static String importCode(String text) {
        Decoded d = decode(text);
        if (d == null) {
            FarmListClient.message("That code is not valid. Copy the whole code and try again.");
            return null;
        }
        return importDecoded(d);
    }

    /** Adds a decoded list (from a code or from chat). Merges into a project you already have, else makes a new list. */
    static String importDecoded(Decoded d) {
        List<FarmListClient.Rejected> rejected = new ArrayList<>();
        List<RowData> good = validRows(d, rejected);
        FarmListClient.pendingReview = rejected;

        if (d.project() != null) {
            for (String file : FarmListClient.listTranscriptions()) {
                if (d.project().equals(new Progress(FarmListClient.baseName(file)).projectId())) {
                    int changes = merge(file, good, d.done());
                    FarmListClient.message("Merged into " + FarmListClient.baseName(file) + ": " + changes
                            + (changes == 1 ? " change." : " changes."));
                    return file;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String title = uniqueTitle(d.title());
        sb.append("# ").append(title).append('\n');
        for (RowData r : good) sb.append(r.id()).append(' ').append(r.count()).append('\n');
        String file = FarmListClient.saveNamedChecklist(title, sb.toString());
        if (file == null) return null;
        Progress p = new Progress(FarmListClient.baseName(file));
        if (d.project() != null) p.setProjectId(d.project());
        for (RowData r : good) applyProgress(p, r);
        if (d.done()) p.setComplete(true);
        FarmListClient.message("Imported " + title + " with its progress.");
        return file;
    }

    /** Merges the code on the clipboard into this checklist (same project only). */
    static boolean mergeFromClipboard(String fileName) {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (!isCode(text)) {
            FarmListClient.message("Copy a collaborate code first.");
            return false;
        }
        Decoded d = decode(text);
        if (d == null) {
            FarmListClient.message("That code is not valid.");
            return false;
        }
        String mine = new Progress(FarmListClient.baseName(fileName)).projectId();
        if (d.project() == null || !d.project().equals(mine)) {
            FarmListClient.message("That code is for a different project. Use Import to add it as a new list.");
            return false;
        }
        List<FarmListClient.Rejected> rejected = new ArrayList<>();
        int changes = merge(fileName, validRows(d, rejected), d.done());
        FarmListClient.message("Synced: " + changes + (changes == 1 ? " change." : " changes."));
        return true;
    }

    // ---------- helpers ----------

    /** Only rows that are real items survive; others go to the review list. */
    private static List<RowData> validRows(Decoded d, List<FarmListClient.Rejected> rejected) {
        List<RowData> good = new ArrayList<>();
        for (RowData r : d.rows()) {
            String key = r.count() >= 1 ? FarmListClient.canonical(r.id()) : null;
            if (key == null) {
                rejected.add(new FarmListClient.Rejected(r.id() + " " + r.count(), FarmListClient.suggest(r.id()),
                        Math.max(1, r.count())));
            } else {
                good.add(new RowData(key, r.count(), r.checked(), r.have(), r.manual()));
            }
        }
        return good;
    }

    private static void applyProgress(Progress p, RowData r) {
        if (r.checked()) p.set(r.id(), true);
        if (r.have() != null) {
            if (r.manual()) p.setManualHave(r.id(), r.have());
            else p.setHave(r.id(), r.have());
        }
    }

    /** Rows are added, a bigger Need wins, check marks combine, a bigger Have wins. Returns how many things changed. */
    private static int merge(String fileName, List<RowData> incoming, boolean done) {
        Progress p = new Progress(FarmListClient.baseName(fileName));
        Map<String, Integer> mine = FarmListClient.parseRequired(FarmListClient.TRANSCRIPTION_DIR.resolve(fileName));
        int changes = 0;
        for (RowData r : incoming) {
            Integer have = mine.get(r.id());
            if (have == null) {
                FarmListClient.appendRow(fileName, r.id(), r.count());
                changes++;
            } else if (r.count() > have) {
                FarmListClient.replaceRow(fileName, r.id(), r.id(), r.count());
                changes++;
            }
            if (r.checked() && !p.isChecked(r.id())) {
                p.set(r.id(), true);
                changes++;
            }
            Integer cur = p.getHave(r.id());
            if (r.have() != null && (cur == null || r.have() > cur)) {
                if (r.manual() || p.isManual(r.id())) p.setManualHave(r.id(), r.have());
                else p.setHave(r.id(), r.have());
                changes++;
            }
        }
        if (done && !p.isComplete()) {
            p.setComplete(true);
            changes++;
        }
        return changes;
    }

    private static String uniqueTitle(String title) {
        String base = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        String name = base;
        int n = 2;
        while (Files.exists(FarmListClient.TRANSCRIPTION_DIR.resolve(name + ".txt"))) {
            name = base + " (" + n++ + ")";
        }
        return name;
    }
}
