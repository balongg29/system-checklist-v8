package com.farmlist;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Block "types" that come in many variants: fence (pick the wood), wool (pick the color), wall (pick the material), and so on.
 * Variants are discovered from the game's item registry by name ("oak_fence" is the "oak" variant of "fence"),
 * so new blocks added in newer versions show up automatically.
 * In checklist files the text form is "fence:oak 64" or "wall:cobblestone 64". A bare "wall 64" means
 * "a wall, material not chosen yet". The app always saves the concrete item id ("oak_fence 64") once you choose.
 */
final class Families {

    private Families() {
    }

    /** suffix is the part of the item id after the variant, e.g. "fence" for "oak_fence". */
    record Family(String key, String label, String suffix, boolean colorsOnly, String excludes) {
    }

    static final Set<String> COLORS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** In the order they appear on the Types tab. */
    static final List<Family> ALL = List.of(
            new Family("fence", "Fence", "fence", false, ""),
            new Family("wool", "Wool", "wool", true, ""),
            new Family("concrete", "Concrete", "concrete", true, ""),
            new Family("slab", "Slab", "slab", false, ""),
            new Family("stairs", "Stairs", "stairs", false, ""),
            new Family("wall", "Wall", "wall", false, ""),
            new Family("trapdoor", "Trapdoor", "trapdoor", false, ""),
            new Family("door", "Door", "door", false, ""),
            new Family("fence_gate", "Fence Gate", "fence_gate", false, ""),
            new Family("planks", "Planks", "planks", false, ""),
            new Family("log", "Log", "log", false, ""),
            new Family("wood", "Wood", "wood", false, ""),
            new Family("button", "Button", "button", false, ""),
            new Family("pressure_plate", "Pressure Plate", "pressure_plate", false, ""),
            new Family("sign", "Sign", "sign", false, "hanging"),
            new Family("hanging_sign", "Hanging Sign", "hanging_sign", false, ""),
            new Family("carpet", "Carpet", "carpet", true, ""),
            new Family("concrete_powder", "Concrete Powder", "concrete_powder", true, ""),
            new Family("terracotta", "Terracotta", "terracotta", true, ""),
            new Family("glazed_terracotta", "Glazed Terracotta", "glazed_terracotta", true, ""),
            new Family("stained_glass", "Stained Glass", "stained_glass", true, ""),
            new Family("stained_glass_pane", "Stained Glass Pane", "stained_glass_pane", true, ""),
            new Family("bed", "Bed", "bed", true, ""),
            new Family("banner", "Banner", "banner", true, ""),
            new Family("shulker_box", "Shulker Box", "shulker_box", true, ""),
            new Family("candle", "Candle", "candle", true, ""));

    private static final Map<String, Family> BY_KEY = new HashMap<>();
    private static final List<Family> LONGEST_FIRST = new ArrayList<>(ALL);
    private static Map<String, List<FarmListClient.CatalogEntry>> variants = null;

    static {
        for (Family f : ALL) BY_KEY.put(f.key(), f);
        LONGEST_FIRST.sort(Comparator.comparingInt((Family f) -> f.suffix().length()).reversed());
    }

    static Family byKey(String key) {
        return BY_KEY.get(key);
    }

    /** The variant part of an item id for this family ("oak" for "oak_fence"), or null if it is not in the family. */
    private static String variantOf(Family f, String id) {
        String tail = "_" + f.suffix();
        if (!id.endsWith(tail) || id.length() == tail.length()) return null;
        String v = id.substring(0, id.length() - tail.length());
        if (f.colorsOnly() && !COLORS.contains(v)) return null;
        if (!f.excludes().isEmpty() && v.contains(f.excludes())) return null;
        return v;
    }

    /** The family an item id belongs to, or null. */
    static Family ofItem(String id) {
        for (Family f : LONGEST_FIRST) {
            if (variantOf(f, id) != null) return f;
        }
        return null;
    }

    /** All items of a family, in registry order. */
    static List<FarmListClient.CatalogEntry> variants(Family f) {
        if (variants == null) {
            Map<String, List<FarmListClient.CatalogEntry>> map = new HashMap<>();
            for (FarmListClient.CatalogEntry e : FarmListClient.catalog()) {
                Family owner = ofItem(e.id());
                if (owner != null) map.computeIfAbsent(owner.key(), k -> new ArrayList<>()).add(e);
            }
            variants = map;
        }
        return variants.getOrDefault(f.key(), List.of());
    }

    /** An icon to represent the family on the Types tab (its first variant), or null. */
    static ItemStack iconFor(Family f) {
        List<FarmListClient.CatalogEntry> v = variants(f);
        return v.isEmpty() ? null : v.get(0).stack();
    }

    /** "fence" + "spruce" -> "spruce_fence"; null if there is no such item. */
    static String resolve(Family f, String variant) {
        String v = variant.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        String tail = "_" + f.suffix();
        String id = v.endsWith(tail) ? v : v + tail;
        FarmListClient.CatalogEntry e = FarmListClient.entryFor(id);
        return e != null && ofItem(id) == f ? id : null;
    }
}
