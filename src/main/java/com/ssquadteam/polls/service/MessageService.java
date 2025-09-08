package com.ssquadteam.polls.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class MessageService {

    private final Plugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration messages;
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)[§&][0-9A-FK-OR]");

    public MessageService(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public Component parse(String raw) { return mm.deserialize(raw); }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String prefix = messages.getString("prefix", "");
        String value = messages.getString(path, path);
        value = apply(placeholders, value);
        sender.sendMessage(mm.deserialize(prefix + value));
    }

    public void sendWithSound(CommandSender sender, String path, Map<String, String> placeholders, String soundKey) {
        send(sender, path, placeholders);
        if (sender instanceof Player player) playSound(player, soundKey);
    }

    public String apply(Map<String, String> placeholders, String value) {
        if (placeholders == null || placeholders.isEmpty()) return value;
        String result = value;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String safe = sanitizeForMiniMessage(e.getValue());
            result = result.replace("{" + e.getKey() + "}", safe);
        }
        return result;
    }

    public void broadcast(String path, Map<String, String> placeholders) {
        String value = messages.getString(path, path);
        String rendered = apply(placeholders, value);
        plugin.getServer().broadcast(mm.deserialize(messages.getString("prefix", "") + rendered));
        String soundKey = plugin.getConfig().getString("sounds.ui.publish");
        if (plugin.getConfig().getBoolean("sounds.enabled", true) && soundKey != null) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                playSound(p, "ui.publish");
            }
        }
    }

    public String formatRelativeTime(long closesAtEpochSeconds) {
        long now = Instant.now().getEpochSecond();
        long diff = closesAtEpochSeconds - now;
        boolean past = diff < 0;
        long abs = Math.abs(diff);
        long days = abs / 86400; abs %= 86400;
        long hours = abs / 3600; abs %= 3600;
        long minutes = abs / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d");
        if (hours > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(hours).append("h");
        }
        if (minutes > 0 || sb.length() == 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(minutes).append("m");
        }
        return past ? sb + " ago" : "in " + sb;
    }

    public String formatAbsoluteTime(long epochSeconds) {
        Date date = Date.from(Instant.ofEpochSecond(epochSeconds));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.ENGLISH);
        fmt.setTimeZone(java.util.TimeZone.getTimeZone(ZoneId.systemDefault()));
        return fmt.format(date);
    }

    public void playSound(Player player, String keyPath) {
        if (player == null) return;
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) return;
        String soundKey = plugin.getConfig().getString("sounds." + keyPath);
        if (soundKey == null || soundKey.isBlank()) return;
        if (plugin instanceof com.ssquadteam.polls.PollsPlugin polls) {
            polls.getFolia().getScheduler().runAtEntity(player, task -> {
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundKey.toUpperCase(Locale.ROOT)), 1f, 1f);
                } catch (IllegalArgumentException ex) {
                    player.playSound(player.getLocation(), org.bukkit.NamespacedKey.minecraft(soundKey).toString(), 1f, 1f);
                }
            });
        } else {
            try {
                player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundKey.toUpperCase(Locale.ROOT)), 1f, 1f);
            } catch (IllegalArgumentException ex) {
                player.playSound(player.getLocation(), org.bukkit.NamespacedKey.minecraft(soundKey).toString(), 1f, 1f);
            }
        }
    }

    public String sanitizeForMiniMessage(String input) {
        if (input == null) return "";
        // Strip legacy color codes like §a and &a
        return LEGACY_COLOR.matcher(input).replaceAll("");
    }
}
