package com.farmlist;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Client side: opens the screens, reads and writes checklist files, and scans the inventory. */
public class FarmListClient implements ClientModInitializer {

    static final Path TRANSCRIPTION_DIR =
            FabricLoader.getInstance().getGameDir().resolve("farmlist").resolve("transcriptions");

    static final String BUILDING_BLOCK = "building_block";

    /** Latest status line, shown on the screen. */
    static String status = "";

    /** Press this key anywhere in game to open your checklists. Rebind it in Controls. */
    static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.farmlist.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("farmlist", "main"))));

    @Override
    public void onInitializeClient() {
        ChatShare.init();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.consumeClick()) {
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new PickerScreen());
                }
            }
        });
    }

    /** A valid checklist line: the row key (item id, "building_block", or a bare family like "wall") and its count. */
    record Line(String key, int count) {
    }

    /** A line that was not understood, with a guess at what was meant. */
    record Rejected(String text, String suggestion, int count) {
    }

    record Parsed(Map<String, Integer> rows, List<Rejected> rejected) {
    }

    private static final Pattern LEAD = Pattern.compile("^(\\d+)\\s*x?\\s+(.+)$");
    private static final Pattern TRAIL = Pattern.compile("^(.+?)[\\s:=]*x?(\\d+)$");

    /** Splits "oak planks x64", "64x oak_planks" or "wall:cobblestone 64" into {name, count}, or null. */
    private static String[] splitNameCount(String line) {
        String s = line.trim().replaceAll("^[-*\u2022]+\\s*", "").replace("`", "").replace(",", "").trim();
        Matcher m = LEAD.matcher(s);
        if (m.matches()) return new String[] { m.group(2).trim(), m.group(1) };
        m = TRAIL.matcher(s);
        if (m.matches()) return new String[] { m.group(1).trim(), m.group(2) };
        return null;
    }

    /**
     * Turns text into a row key, or null if it is not a real item. Accepts "minecraft:oak_planks",
     * "oak planks", "fence:spruce" (family:variant) and a bare family such as "wall" (material chosen later).
     */
    static String canonical(String raw) {
        String t = raw.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_');
        if (t.isEmpty()) return null;
        if (t.equals(BUILDING_BLOCK)) return t;
        int colon = t.indexOf(':');
        if (colon >= 0) {
            Families.Family f = Families.byKey(t.substring(0, colon));
            if (f == null) return null;
            String v = t.substring(colon + 1);
            if (v.isEmpty() || v.equals("?")) return f.key();
            return Families.resolve(f, v);
        }
        if (entryFor(t) != null) return t;
        if (Families.byKey(t) != null) return t;
        return null;
    }

    /** Parses one line, or returns null if it is not a valid "item count" row. */
    static Line parseLine(String line) {
        String[] nc = splitNameCount(line);
        if (nc == null) return null;
        try {
            int count = Integer.parseInt(nc[1]);
            if (count < 1) return null;
            String key = canonical(nc[0]);
            return key == null ? null : new Line(key, count);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True for a row that has no real item yet (a bare family like "wall", or an unchosen building_block). */
    static boolean isPlaceholder(String key) {
        return BUILDING_BLOCK.equals(key) || (Families.byKey(key) != null && entryFor(key) == null);
    }

    /** Reads a checklist file. Lines that are not real items are returned in rejected, never in rows. */
    static Parsed parseFile(Path file) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        List<Rejected> rejected = new ArrayList<>();
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Line l = parseLine(line);
                if (l != null) {
                    rows.merge(l.key(), l.count(), Integer::sum);
                } else if (line.matches(".*[A-Za-z].*")) {
                    rejected.add(rejectedFor(line));
                }
            }
        } catch (IOException ignored) {
        }
        return new Parsed(rows, rejected);
    }

    /** Parses lines like "wheat_seeds 64", skipping titles, comments, blank lines and anything that is not a real item. */
    static Map<String, Integer> parseRequired(Path file) {
        return parseFile(file).rows();
    }

    private static Rejected rejectedFor(String line) {
        String[] nc = splitNameCount(line);
        String name = nc != null ? nc[0] : line;
        int count = 1;
        if (nc != null) {
            try {
                count = Math.max(1, Integer.parseInt(nc[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        return new Rejected(line, suggest(name), count);
    }

    /** The closest real item id to some unrecognized text, or null if nothing is close. */
    static String suggest(String text) {
        String t = text.toLowerCase(Locale.ROOT).replace("minecraft:", "").replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (t.length() < 3) return null;
        int limit = t.length() >= 9 ? 2 : 1;
        String best = null;
        int bestDist = limit + 1;
        for (CatalogEntry e : catalog()) {
            if (Math.abs(e.id().length() - t.length()) > limit) continue;
            int d = distance(t, e.id(), bestDist);
            if (d < bestDist) {
                bestDist = d;
                best = e.id();
            }
        }
        return best;
    }

    /** Edit distance between a and b (stops early once it is certain to be at least cap). */
    private static int distance(String a, String b, int cap) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            int rowMin = cur[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, cur[j]);
            }
            if (rowMin >= cap) return cap;
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    /** Counts item IDs in the player's inventory. */
    static Map<String, Integer> countInventory() {
        Map<String, Integer> counts = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return counts;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    /** One catalog item with everything the screens need, cached so drawing stays cheap. */
    record CatalogEntry(Item item, String id, String name, String nameLower, ItemStack stack, boolean block) {
    }

    private static List<CatalogEntry> catalog = null;
    private static Map<String, CatalogEntry> catalogById = null;

    /** Every item in the game (minus air), in registry order, built once on first use. */
    static List<CatalogEntry> catalog() {
        if (catalog == null) {
            List<CatalogEntry> list = new ArrayList<>();
            Map<String, CatalogEntry> byId = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String id = itemId(item);
                ItemStack stack = new ItemStack(item);
                String name = stack.getHoverName().getString();
                CatalogEntry entry = new CatalogEntry(item, id, name, name.toLowerCase(Locale.ROOT),
                        stack, item instanceof BlockItem);
                list.add(entry);
                byId.put(id, entry);
            }
            catalog = list;
            catalogById = byId;
        }
        return catalog;
    }

    /** Looks up a catalog entry by item ID (e.g. "wheat_seeds"), or null if there is no such item. */
    static CatalogEntry entryFor(String id) {
        catalog();
        return catalogById.get(id);
    }

    static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    static String itemName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    /** Item ID that actually gets counted for a row: the chosen block, or the row ID itself. */
    static String countedId(String rowId, Progress progress) {
        if (BUILDING_BLOCK.equals(rowId)) {
            String choice = progress.getChoice(rowId);
            return choice != null ? choice : rowId;
        }
        return rowId;
    }

    /** "Wheat Farm.txt" -> "Wheat Farm" */
    static String baseName(String fileName) {
        return fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    static String normalize(String id) {
        return id.replace("minecraft:", "").toLowerCase();
    }

    static List<String> listTranscriptions() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(TRANSCRIPTION_DIR)) return names;
        try (Stream<Path> s = Files.list(TRANSCRIPTION_DIR)) {
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".txt"))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException ignored) {
        }
        return names;
    }

    /** Copies a checklist's text to the clipboard so it can be pasted elsewhere. */
    static void copyToClipboard(String fileName) {
        try {
            String text = Files.readString(TRANSCRIPTION_DIR.resolve(fileName), StandardCharsets.UTF_8);
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            message("Copied " + fileName + " to the clipboard.");
        } catch (IOException e) {
            message("Could not read " + fileName);
        }
    }

    /** Lines dropped by the last import, waiting for the review screen. */
    static List<Rejected> pendingReview = new ArrayList<>();

    /**
     * Reads a transcription from the clipboard, checks every line against the game's item list,
     * keeps only real items, names the file after its "# Title" line, saves it into the transcriptions folder,
     * and returns the saved file name (or null). Lines that are not real items go to pendingReview.
     */
    static String importFromClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isBlank()) {
            message("Clipboard is empty. Copy the checklist text first.");
            return null;
        }
        if (Share.isCode(text)) {
            return Share.importCode(text);   // a share / collaborate code: list + progress
        }
        pendingReview = new ArrayList<>();
        String title = null;
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("#") && !t.toUpperCase().contains("NEEDS") && !t.toUpperCase().contains("GUESS")) {
                title = t.replaceFirst("^#+", "").trim();
                break;
            }
        }
        if (title == null || title.isEmpty()) {
            title = "Checklist " + System.currentTimeMillis();
        }

        StringBuilder clean = new StringBuilder("# ").append(title).append("\n");
        List<Rejected> rejected = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Line l = parseLine(line);
            if (l != null) {
                clean.append(l.key()).append(' ').append(l.count()).append('\n');
            } else if (line.matches(".*[A-Za-z].*")) {
                rejected.add(rejectedFor(line));
            }
        }
        pendingReview = rejected;
        String saved = saveNamedChecklist(title, clean.toString());
        if (saved != null && !rejected.isEmpty()) {
            message("Skipped " + rejected.size() + " line" + (rejected.size() == 1 ? "" : "s")
                    + " that are not real items.");
        }
        return saved;
    }

    /** Saves a checklist file with the given title as its name and first line. */
    static String saveNamedChecklist(String title, String text) {
        String name = title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        try {
            Files.createDirectories(TRANSCRIPTION_DIR);
            Files.writeString(TRANSCRIPTION_DIR.resolve(name), text, StandardCharsets.UTF_8);
            message("Saved " + name + ".");
            return name;
        } catch (IOException e) {
            message("Could not save " + name + ": " + e.getMessage());
            return null;
        }
    }

    /** Appends one "item count" row to a checklist file. */
    static void appendRow(String fileName, String itemId, int count) {
        try {
            Files.writeString(TRANSCRIPTION_DIR.resolve(fileName),
                    "\n" + itemId + " " + count + "\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            message("Added " + count + " " + itemId + ".");
        } catch (IOException e) {
            message("Could not add row: " + e.getMessage());
        }
    }

    /**
     * Changes a row: oldKey becomes newKey with the given count. If newKey is already on the list the two
     * rows are merged (counts added). Other lines in the file are left alone.
     */
    static void replaceRow(String fileName, String oldKey, String newKey, int count) {
        try {
            Path file = TRANSCRIPTION_DIR.resolve(fileName);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int existing = 0;
            if (!oldKey.equals(newKey)) {
                for (String line : lines) {
                    Line l = parseLine(line);
                    if (l != null && l.key().equals(newKey)) existing += l.count();
                }
            }
            List<String> out = new ArrayList<>();
            boolean written = false;
            for (String line : lines) {
                Line l = parseLine(line);
                if (l != null && !oldKey.equals(newKey) && l.key().equals(newKey)) continue;
                if (l != null && l.key().equals(oldKey)) {
                    if (!written) {
                        out.add(newKey + " " + (count + existing));
                        written = true;
                    }
                    continue;
                }
                out.add(line);
            }
            if (!written) out.add(newKey + " " + (count + existing));
            Files.write(file, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            message("Could not edit row: " + e.getMessage());
        }
    }

    /** Removes a row (every line for that item) from a checklist file. */
    static void removeRow(String fileName, String key) {
        try {
            Path file = TRANSCRIPTION_DIR.resolve(fileName);
            List<String> out = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Line l = parseLine(line);
                if (l != null && l.key().equals(key)) continue;
                out.add(line);
            }
            Files.write(file, out, StandardCharsets.UTF_8);
            message("Removed row.");
        } catch (IOException e) {
            message("Could not remove row: " + e.getMessage());
        }
    }

    /** Deletes a checklist file and its saved checkboxes. */
    static void deleteChecklist(String fileName) {
        try {
            Files.deleteIfExists(TRANSCRIPTION_DIR.resolve(fileName));
            String base = baseName(fileName);
            Files.deleteIfExists(Progress.PROGRESS_DIR.resolve(base + ".properties"));
            message("Deleted " + fileName + ".");
        } catch (IOException e) {
            message("Could not delete " + fileName + ": " + e.getMessage());
        }
    }

    static void message(String text) {
        status = text;
    }
}
