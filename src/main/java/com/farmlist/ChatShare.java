package com.farmlist;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends a checklist to another player through a private chat message (/msg), in small pieces, and puts the pieces
 * back together on the other side. A player who has this mod does not see the pieces: they see
 * "Michael shared 'Wheat Farm'  [Click to add]", and clicking it adds the list to their checklists.
 * Players without the mod just see some text in their private messages.
 */
final class ChatShare {

    private ChatShare() {
    }

    private static final String TAG = "FLS1|";
    private static final int CHUNK = 160;          // characters of code per message
    private static final int SEND_EVERY_TICKS = 25; // about 1 message per 1.25 s, to stay under server spam limits
    private static final int MAX_PARTS = 60;
    private static final int MAX_PENDING = 8;

    private record Pending(String to, String text) {
    }

    private static final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private static int cooldown = 0;

    /** id -> pieces received so far. */
    private static final Map<String, String[]> buffers = new LinkedHashMap<>();
    /** id -> finished lists waiting for a click. */
    private static final Map<String, Share.Decoded> inbox = new LinkedHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    static void init() {
        // Never carry unsent pieces into another server or session.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            queue.clear();
            cooldown = 0;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            Pending p = queue.poll();
            if (p == null) return;
            if (mc.player == null || mc.getConnection() == null) {
                queue.clear();   // not connected anymore: drop the rest instead of sending later
                return;
            }
            mc.player.connection.sendCommand("msg " + p.to() + " " + p.text());
            cooldown = SEND_EVERY_TICKS;
            if (queue.isEmpty()) FarmListClient.message("Sent.");
        });

        // Whispers (/msg) are signed player chat in modern Minecraft, so they arrive here, not in ALLOW_GAME.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !receive(message.getString(), null));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) ->
                !receive(message.getString(), sender == null ? null : sender.name()));

        // Clicking [Click to add] "runs" the command below. We catch it here, so it never reaches the server.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String prefix = "farmlist accept ";
            if (!command.startsWith(prefix)) return true;
            accept(command.substring(prefix.length()).trim());
            return false;
        });
    }

    // ---------- sending ----------

    /** Names of the players currently online (not you), for the "Everyone online" button. */
    static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return names;
        String me = mc.player.getGameProfile().name();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String n = info.getProfile().name();
            if (!n.equals(me) && n.matches("[A-Za-z0-9_]{1,16}")) names.add(n);
        }
        return names;
    }

    /** Queues a whisper to each player with the list in pieces. Returns false (and sets a message) if it cannot. */
    static boolean send(String fileName, boolean withProgress, List<String> players) {
        if (players.isEmpty()) {
            FarmListClient.message("Type at least one player name (separate with commas).");
            return false;
        }
        for (String player : players) {
            if (!player.matches("[A-Za-z0-9_]{1,16}")) {
                FarmListClient.message("\"" + player + "\" is not a valid player name.");
                return false;
            }
        }
        if (Minecraft.getInstance().getConnection() == null) {
            FarmListClient.message("You need to be on a server to send this.");
            return false;
        }
        String code = Share.buildCode(fileName, false, withProgress);
        if (code == null) return false;
        // Refuse anything that would not decode on the other side, so nothing broken goes out.
        if (Share.decode(code) == null || !code.startsWith(Share.PREFIX)) {
            FarmListClient.message("Could not prepare this list for sending.");
            return false;
        }
        String body = code.substring(Share.PREFIX.length());
        if (!body.matches("[A-Za-z0-9_-]+")) {
            FarmListClient.message("Could not prepare this list for sending.");
            return false;
        }
        int parts = (body.length() + CHUNK - 1) / CHUNK;
        if (parts > MAX_PARTS) {
            FarmListClient.message("This list is too big to send in chat. Use Copy code instead.");
            return false;
        }
        if (queue.size() + parts * players.size() > 300) {
            FarmListClient.message("Still sending a previous list. Wait for it to finish first.");
            return false;
        }
        String id = randomId();
        // one player at a time, so each person gets all their pieces together
        for (String player : players) {
            for (int i = 0; i < parts; i++) {
                String data = body.substring(i * CHUNK, Math.min(body.length(), (i + 1) * CHUNK));
                String piece = TAG + id + "|" + i + "|" + parts + "|" + data;
                if (piece.length() > 220 || piece.contains(" ")) {   // must fit a /msg and stay one word
                    FarmListClient.message("Could not prepare this list for sending.");
                    queue.clear();
                    return false;
                }
                queue.add(new Pending(player, piece));
            }
        }
        FarmListClient.message("Sending to " + players.size() + " player" + (players.size() == 1 ? "" : "s")
                + " (" + parts + " message" + (parts == 1 ? "" : "s") + " each)...");
        return true;
    }

    private static String randomId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    // ---------- receiving ----------

    /** Handles an incoming message. Returns true if it was one of our pieces (so it should be hidden). */
    private static boolean receive(String text, String knownSender) {
        int idx = text.indexOf(TAG);
        if (idx < 0) return false;
        // Our own outgoing pieces echo back as "You whisper to X: ..."; hide them, and never assemble them.
        if (text.trim().startsWith("You whisper to")) return true;
        String[] f = text.substring(idx + TAG.length()).trim().split("\\|", 4);
        if (f.length < 4 || !f[0].matches("[a-z0-9]{8}") || !f[3].matches("[A-Za-z0-9_-]+")) return false;
        int i;
        int n;
        try {
            i = Integer.parseInt(f[1]);
            n = Integer.parseInt(f[2]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (n < 1 || n > MAX_PARTS || i < 0 || i >= n) return false;

        String id = f[0];
        String[] parts = buffers.computeIfAbsent(id, k -> new String[n]);
        if (parts.length != n) return true;
        parts[i] = f[3];
        while (buffers.size() > MAX_PENDING) {
            buffers.remove(buffers.keySet().iterator().next());
        }
        for (String part : parts) {
            if (part == null) return true;   // still waiting for more pieces
        }
        buffers.remove(id);

        String sender = knownSender;
        if (sender == null || sender.isEmpty()) {
            String before = text.substring(0, idx).trim().replaceAll("^[<\\[\\s]+", "");
            sender = before.isEmpty() ? "Someone" : before.split("[\\s>\\]:]")[0];
            if (sender.isEmpty()) sender = "Someone";
        }

        Share.Decoded d = Share.decode(Share.PREFIX + String.join("", parts));
        if (d == null) {
            say(Component.literal("[Farm Checklist] A shared list from " + sender + " arrived damaged.")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        inbox.put(id, d);
        while (inbox.size() > MAX_PENDING) {
            inbox.remove(inbox.keySet().iterator().next());
        }
        boolean hasProgress = d.done() || d.rows().stream().anyMatch(r -> r.checked() || r.have() != null);
        say(Component.literal("[Farm Checklist] " + sender + " shared \"" + d.title() + "\" (" + d.rows().size()
                        + " items" + (hasProgress ? ", with progress" : "") + ") ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("[Click to add]").withStyle(st -> st
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("farmlist accept " + id)))));
        return true;
    }

    private static void accept(String id) {
        Share.Decoded d = inbox.remove(id);
        if (d == null) {
            say(Component.literal("[Farm Checklist] That shared list is no longer available. Ask them to send it again.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        String file = Share.importDecoded(d);
        if (file != null) {
            say(Component.literal("[Farm Checklist] Added \"" + FarmListClient.baseName(file)
                    + "\" to your checklists. Press K to open it.").withStyle(ChatFormatting.GREEN));
        }
    }

    private static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(c);
    }
}
