package com.aefamily.discordstatus;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class DiscordStatusPlugin extends JavaPlugin {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static class ChannelConfig {
        String channelId;
        long messageId;
    }

    private static class PresenceConfig {
        OnlineStatus status;
        Activity.ActivityType activityType;
        String activityText;
    }

    private static class EmbedFieldConfig {
        String name;
        String value;
        boolean inline;
    }

    private static class EmbedStateConfig {
        String description;
        Color color;
    }

    private static class EmbedConfig {
        String title;
        String footer;
        EmbedStateConfig online;
        EmbedStateConfig offline;
        List<EmbedFieldConfig> fields = new ArrayList<>();
    }

    private static class RetryConfig {
        int maxAttempts;
        long backoffSeconds;
    }

    private static class BotConfig {
        String token;
        JDA jda;
        List<ChannelConfig> channels = new ArrayList<>();
        PresenceConfig presence;
    }

    private final List<BotConfig> bots = new ArrayList<>();
    private List<Map<String, Object>> botConfigSections = Collections.emptyList();
    private EmbedConfig embedConfig;
    private RetryConfig retryConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();
        initBots();
        updateAll(true);
    }

    @Override
    public void onDisable() {
        updateAll(false);
        shutdownBots();
    }

    private void reloadSettings() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        embedConfig = parseEmbedConfig(cfg);
        retryConfig = parseRetryConfig(cfg);
        botConfigSections = readBotConfigSections(cfg);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readBotConfigSections(FileConfiguration cfg) {
        List<?> rawBots = cfg.getList("bots");
        if (rawBots == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Object obj : rawBots) {
            if (obj instanceof Map<?, ?>) {
                sections.add((Map<String, Object>) obj);
            }
        }
        return sections;
    }

    private void initBots() {
        shutdownBots();
        if (botConfigSections.isEmpty()) {
            getLogger().warning("No bots configured in config.yml!");
            return;
        }
        for (int i = 0; i < botConfigSections.size(); i++) {
            Map<String, Object> botMap = botConfigSections.get(i);
            BotConfig bc = new BotConfig();
            bc.token = asString(botMap.get("token"));
            bc.presence = parsePresence(botMap.get("presence"));
            bc.channels.addAll(parseChannels(botMap.get("channels")));
            if (bc.token == null || bc.token.isBlank()) {
                getLogger().warning("Missing token for bot #" + i + ", skipping.");
                continue;
            }
            try {
                bc.jda = JDABuilder.createDefault(bc.token)
                        .enableIntents(GatewayIntent.GUILD_MESSAGES)
                        .build();
                bc.jda.awaitReady();
                bots.add(bc);
            } catch (LoginException e) {
                getLogger().severe("Failed to login bot " + i + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                getLogger().severe("Login interrupted for bot " + i);
            } catch (Exception e) {
                getLogger().severe("Unexpected error while starting bot " + i + ": " + e.getMessage());
            }
        }
    }

    private void shutdownBots() {
        bots.forEach(bc -> {
            if (bc.jda != null) {
                bc.jda.shutdown();
            }
        });
        bots.clear();
    }

    private List<ChannelConfig> parseChannels(Object channelsObj) {
        List<ChannelConfig> channels = new ArrayList<>();
        if (!(channelsObj instanceof List<?>)) {
            return channels;
        }
        for (Object chObj : (List<?>) channelsObj) {
            if (!(chObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> chMap = (Map<?, ?>) chObj;
            ChannelConfig cc = new ChannelConfig();
            cc.channelId = asString(chMap.get("channelId"));
            Object mid = chMap.get("messageId");
            cc.messageId = mid instanceof Number ? ((Number) mid).longValue() : 0L;
            if (cc.channelId != null && !cc.channelId.isBlank()) {
                channels.add(cc);
            } else {
                getLogger().warning("Skipped a channel with no channelId.");
            }
        }
        return channels;
    }

    private PresenceConfig parsePresence(Object presenceObj) {
        if (!(presenceObj instanceof Map<?, ?>)) {
            return null;
        }
        Map<?, ?> presenceMap = (Map<?, ?>) presenceObj;
        PresenceConfig presence = new PresenceConfig();
        presence.status = parseStatus(asString(presenceMap.get("status")));
        presence.activityType = parseActivityType(asString(presenceMap.get("activityType")));
        presence.activityText = asString(presenceMap.get("activityText"));
        return presence;
    }

    private OnlineStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return OnlineStatus.ONLINE;
        }
        try {
            return OnlineStatus.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Unknown presence status '" + raw + "', defaulting to ONLINE.");
            return OnlineStatus.ONLINE;
        }
    }

    private Activity.ActivityType parseActivityType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Activity.ActivityType.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Unknown activity type '" + raw + "', ignoring activity.");
            return null;
        }
    }

    private String asString(Object obj) {
        return obj == null ? null : Objects.toString(obj);
    }

    private void updateAll(boolean active) {
        if (bots.isEmpty()) {
            getLogger().warning("No bots ready to send status messages.");
            return;
        }
        MessageEmbed embed = buildEmbed(active);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < bots.size(); i++) {
            BotConfig bc = bots.get(i);
            if (!isJdaUsable(bc)) {
                getLogger().warning("Bot #" + i + " is not connected, skipping.");
                continue;
            }
            applyPresence(bc, active);
            for (int j = 0; j < bc.channels.size(); j++) {
                ChannelConfig cc = bc.channels.get(j);
                futures.add(sendOrUpdateWithRetry(bc, i, cc, j, embed, 1));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        saveConfig();
    }

    private boolean isJdaUsable(BotConfig bc) {
        return bc != null && bc.jda != null && bc.jda.getStatus() != JDA.Status.SHUTDOWN;
    }

    private void applyPresence(BotConfig bc, boolean active) {
        if (bc.jda == null || bc.presence == null) {
            return;
        }
        bc.jda.getPresence().setStatus(bc.presence.status);
        Activity.ActivityType activityType = bc.presence.activityType;
        String rawText = bc.presence.activityText;
        if (activityType != null && rawText != null && !rawText.isBlank()) {
            String text = formatPlaceholders(rawText, active);
            bc.jda.getPresence().setActivity(Activity.of(activityType, text));
        } else {
            bc.jda.getPresence().setActivity(null);
        }
    }

    private CompletableFuture<Void> sendOrUpdateWithRetry(BotConfig bc, int botIndex, ChannelConfig cc, int chIndex, MessageEmbed embed, int attempt) {
        TextChannel textChannel = bc.jda.getTextChannelById(cc.channelId);
        if (textChannel == null) {
            getLogger().warning("Channel " + cc.channelId + " not found for bot " + botIndex);
            return CompletableFuture.completedFuture(null);
        }
        MessageEmbed safeEmbed = Objects.requireNonNull(embed, "embed");
        CompletableFuture<Message> action = cc.messageId != 0L
                ? textChannel.editMessageEmbedsById(cc.messageId, safeEmbed).submit()
                : textChannel.sendMessageEmbeds(safeEmbed).submit();

        return action.handle((msg, throwable) -> {
            if (throwable == null) {
                if (cc.messageId == 0L && msg != null) {
                    cc.messageId = msg.getIdLong();
                    updateStoredMessageId(botIndex, chIndex, cc.messageId);
                }
                return CompletableFuture.<Void>completedFuture(null);
            }
            return handleSendFailure(bc, botIndex, cc, chIndex, embed, attempt, throwable);
        }).thenCompose(Function.identity());
    }

    private CompletableFuture<Void> handleSendFailure(BotConfig bc, int botIndex, ChannelConfig cc, int chIndex, MessageEmbed embed, int attempt, Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        if (attempt >= retryConfig.maxAttempts) {
            getLogger().severe(String.format("Failed to send/edit message after %d attempts (bot %d, channel %d): %s", attempt, botIndex, chIndex, cause.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
        long delay = retryConfig.backoffSeconds * attempt;
        getLogger().warning(String.format("Could not reach Discord for bot %d channel %d (attempt %d/%d): %s. Retrying in %d seconds.", botIndex, chIndex, attempt, retryConfig.maxAttempts, cause.getMessage(), delay));
        return CompletableFuture.runAsync(() -> { }, CompletableFuture.delayedExecutor(delay, TimeUnit.SECONDS))
                .thenCompose(ignored -> sendOrUpdateWithRetry(bc, botIndex, cc, chIndex, embed, attempt + 1));
    }

    @SuppressWarnings("unchecked")
    private void updateStoredMessageId(int botIndex, int channelIndex, long messageId) {
        if (botIndex >= botConfigSections.size()) {
            return;
        }
        Map<String, Object> botMap = botConfigSections.get(botIndex);
        Object channelsObj = botMap.get("channels");
        if (!(channelsObj instanceof List<?>)) {
            return;
        }
        List<?> channels = (List<?>) channelsObj;
        if (channelIndex >= channels.size()) {
            return;
        }
        Object channelObj = channels.get(channelIndex);
        if (channelObj instanceof Map<?, ?>) {
            ((Map<String, Object>) channelObj).put("messageId", messageId);
        }
    }

    private MessageEmbed buildEmbed(boolean active) {
        EmbedStateConfig state = active ? embedConfig.online : embedConfig.offline;
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(formatPlaceholders(embedConfig.title, active))
                .setDescription(formatPlaceholders(state.description, active))
                .setColor(state.color)
                .setTimestamp(Instant.now());
        if (embedConfig.footer != null && !embedConfig.footer.isBlank()) {
            eb.setFooter(formatPlaceholders(embedConfig.footer, active));
        }
        for (EmbedFieldConfig field : embedConfig.fields) {
            eb.addField(formatPlaceholders(field.name, active), formatPlaceholders(field.value, active), field.inline);
        }
        return eb.build();
    }

    private String formatPlaceholders(String input, boolean active) {
        if (input == null) {
            return "";
        }
        String status = active ? "ONLINE" : "OFFLINE";
        return input
                .replace("{status}", status)
                .replace("{status_lower}", status.toLowerCase(Locale.ENGLISH))
                .replace("{server}", getServer().getName())
                .replace("{time}", TIME_FORMATTER.format(Instant.now()));
    }

    private EmbedConfig parseEmbedConfig(FileConfiguration cfg) {
        EmbedConfig ec = new EmbedConfig();
        ec.title = firstNonNull(cfg.getString("embed.title"), cfg.getString("embedTitle"), "Server Status");
        ec.footer = cfg.getString("embed.footer", "Updated at {time}");

        ec.online = new EmbedStateConfig();
        ec.online.description = firstNonNull(cfg.getString("embed.online.description"), cfg.getString("activeDescription"), "The server is online!");
        ec.online.color = parseColor(cfg.getString("embed.online.color"), Color.GREEN);

        ec.offline = new EmbedStateConfig();
        ec.offline.description = firstNonNull(cfg.getString("embed.offline.description"), cfg.getString("inactiveDescription"), "The server is offline.");
        ec.offline.color = parseColor(cfg.getString("embed.offline.color"), Color.RED);

        List<Map<?, ?>> fieldList = cfg.getMapList("embed.fields");
        if (fieldList != null && !fieldList.isEmpty()) {
            for (Map<?, ?> fieldMap : fieldList) {
                EmbedFieldConfig field = parseField(fieldMap);
                if (field != null) {
                    ec.fields.add(field);
                }
            }
        } else {
            for (String msg : cfg.getStringList("extraMessages")) {
                EmbedFieldConfig field = new EmbedFieldConfig();
                field.name = "Info";
                field.value = msg;
                field.inline = false;
                ec.fields.add(field);
            }
        }
        return ec;
    }

    private RetryConfig parseRetryConfig(FileConfiguration cfg) {
        RetryConfig rc = new RetryConfig();
        rc.maxAttempts = Math.max(1, cfg.getInt("retry.attempts", 3));
        rc.backoffSeconds = Math.max(1, cfg.getLong("retry.backoffSeconds", 5L));
        return rc;
    }

    private EmbedFieldConfig parseField(Map<?, ?> fieldMap) {
        if (fieldMap == null) {
            return null;
        }
        EmbedFieldConfig field = new EmbedFieldConfig();
        Object nameObj = fieldMap.get("name");
        Object valueObj = fieldMap.get("value");
        Object inlineObj = fieldMap.get("inline");

        field.name = nameObj != null ? asString(nameObj) : "Info";
        field.value = valueObj != null ? asString(valueObj) : "";
        field.inline = inlineObj instanceof Boolean ? (Boolean) inlineObj : Boolean.parseBoolean(asString(inlineObj));
        return field;
    }

    private Color parseColor(String raw, Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Color.decode(raw.trim());
        } catch (NumberFormatException ex) {
            getLogger().warning("Invalid color value '" + raw + "', using fallback.");
            return fallback;
        }
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
