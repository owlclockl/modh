package io.github.owlclockl.hardcoredeathpenalty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/**
 * All gameplay logic of the mod.
 * <p>
 * Everything here is called from the server thread only.
 */
public final class PenaltyService {
    /** 10 hearts, 2 health points each. */
    public static final int VANILLA_HEARTS = 10;
    public static final double HEALTH_PER_HEART = 2.0D;

    private static final Identifier HEARTS_MODIFIER_ID = Identifier.fromNamespaceAndPath(
            HardcoreDeathPenalty.MODID, "hearts_lost");

    private PenaltyService() {
    }

    // ------------------------------------------------------------------ death

    /**
     * Called when a player dies (before the vanilla death handling is finished).
     */
    public static void handleDeath(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerId = player.getUUID();
        PenaltySavedData storage = PenaltySavedData.get(server);
        PlayerPenalty old = storage.get(playerId);

        int heartsLost = old.heartsLost();
        if (Config.LOSE_HEART_ON_DEATH.get() && Config.HEARTS_PER_DEATH.get() > 0) {
            heartsLost = Math.min(maxLostHearts(), heartsLost + Config.HEARTS_PER_DEATH.get());
        }

        boolean ban = Config.BAN_ENABLED.get() && Config.BAN_DAYS.get() > 0;
        long banUntil = ban ? now + Config.banDurationMillis() : 0L;

        PlayerPenalty updated = new PlayerPenalty(
                banUntil,
                heartsLost,
                old.streak(),
                old.lastDay(),
                old.deaths() + 1,
                now,
                ban);
        storage.set(playerId, updated);

        if (Config.ANNOUNCE_DEATH.get()) {
            long[] parts = TimeUtil.splitDuration(banUntil - now);
            String text = Messages.format(Config.DEATH_ANNOUNCEMENT.get(), Messages.map(
                    "player", player.getName().getString(),
                    "days", String.valueOf(parts[0]),
                    "until", TimeUtil.formatDateTime(banUntil)));
            server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
        }

        if (ban) {
            // Respawn the dead player and kick them on the next tick, so the vanilla
            // death handling (drops, statistics, death message) has already finished.
            server.schedule(new TickTask(0, () -> finishDeath(server, playerId)));
        }
    }

    private static void finishDeath(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || player.connection == null) {
            // The player is already gone. restorePending stays true, so the next
            // login will fix the state (see restoreAfterFailedRespawn).
            return;
        }

        PenaltySavedData storage = PenaltySavedData.get(server);
        PlayerPenalty penalty = storage.get(playerId);
        if (!penalty.restorePending()) {
            return;
        }

        if (player.isDeadOrDying()) {
            ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            if (respawned != null) {
                player = respawned;
            }
        }

        penalty = penalty.withRestorePending(false);
        storage.set(playerId, penalty);
        applyHearts(player, penalty);

        if (player.connection != null) {
            player.connection.disconnect(Component.literal(banMessage(penalty.banUntil(), System.currentTimeMillis())));
        }
    }

    // ------------------------------------------------------------------ login

    public static void handleLogin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }

        UUID playerId = player.getUUID();
        PenaltySavedData storage = PenaltySavedData.get(server);
        PlayerPenalty penalty = storage.get(playerId);
        long now = System.currentTimeMillis();

        if (penalty.isBanned(now)) {
            if (player.connection != null) {
                player.connection.disconnect(Component.literal(banMessage(penalty.banUntil(), now)));
            }
            return;
        }

        boolean returnedFromBan = penalty.banUntil() > 0L;
        if (returnedFromBan) {
            penalty = penalty.withBanUntil(0L);
            storage.set(playerId, penalty);
        }

        if (penalty.restorePending()) {
            // The server crashed (or was stopped) between the death and the forced respawn.
            server.schedule(new TickTask(0, () -> restoreAfterFailedRespawn(server, playerId)));
        }

        applyHearts(player, penalty);

        if (penalty.heartsLost() <= 0) {
            return;
        }

        Attendance attendance = updateAttendance(player, penalty);
        penalty = attendance.penalty();
        if (attendance.changed()) {
            storage.set(playerId, penalty);
        }

        if (attendance.restored()) {
            send(player, Config.HEART_RESTORED_MESSAGE.get(), heartPlaceholders(penalty));
        } else if (returnedFromBan) {
            send(player, Config.HEART_LOST_MESSAGE.get(), heartPlaceholders(penalty));
        } else if (attendance.changed()) {
            send(player, Config.STREAK_PROGRESS_MESSAGE.get(), heartPlaceholders(penalty));
        }
    }

    /**
     * Fixes the player state if the forced respawn did not happen (crash, restart, ...).
     */
    private static void restoreAfterFailedRespawn(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }

        PenaltySavedData storage = PenaltySavedData.get(server);
        PlayerPenalty penalty = storage.get(playerId);
        if (!penalty.restorePending()) {
            return;
        }

        boolean needsRespawn = player.isDeadOrDying();
        if (needsRespawn) {
            ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            if (respawned != null) {
                player = respawned;
            }
        }

        if (player.isSpectator()) {
            player.setGameMode(GameType.SURVIVAL);
        }

        penalty = penalty.withRestorePending(false);
        storage.set(playerId, penalty);
        applyHearts(player, penalty);
    }

    // ------------------------------------------------------------- attendance

    /**
     * Counts today as a visit if the day has changed since the last counted visit.
     * May restore one heart.
     */
    private static Attendance updateAttendance(ServerPlayer player, PlayerPenalty penalty) {
        if (penalty.heartsLost() <= 0) {
            return new Attendance(penalty, false, false);
        }

        long today = TimeUtil.currentDayIndex();
        long last = penalty.lastDay();

        if (last >= today) {
            // Already counted today (or the system clock went backwards).
            return new Attendance(penalty, false, false);
        }

        int streak;
        if (last == today - 1L) {
            streak = penalty.streak() + 1;
        } else if (!Config.RESET_STREAK_ON_MISSED_DAY.get() && last != Long.MIN_VALUE) {
            streak = penalty.streak() + 1;
        } else {
            streak = 1;
        }

        int heartsLost = penalty.heartsLost();
        boolean restored = false;
        int required = Math.max(1, Config.RECOVERY_DAYS.get());
        if (streak >= required) {
            heartsLost = Math.max(0, heartsLost - 1);
            streak = 0;
            restored = true;
        }

        PlayerPenalty updated = penalty
                .withStreak(streak)
                .withHeartsLost(heartsLost)
                .withLastDay(today);

        if (restored) {
            applyHearts(player, updated);
        }

        return new Attendance(updated, true, restored);
    }

    /**
     * Called on every server tick. Handles players who stay online across midnight.
     */
    public static void handleServerTick(MinecraftServer server) {
        // Once every 10 seconds is more than enough.
        if (server.getTickCount() % 200 != 0) {
            return;
        }

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.isEmpty()) {
            return;
        }

        PenaltySavedData storage = PenaltySavedData.get(server);
        for (ServerPlayer player : players) {
            PlayerPenalty penalty = storage.get(player.getUUID());
            if (penalty.heartsLost() <= 0) {
                continue;
            }

            Attendance attendance = updateAttendance(player, penalty);
            if (!attendance.changed()) {
                continue;
            }

            storage.set(player.getUUID(), attendance.penalty());
            if (attendance.restored()) {
                send(player, Config.HEART_RESTORED_MESSAGE.get(), heartPlaceholders(attendance.penalty()));
            } else {
                send(player, Config.STREAK_PROGRESS_MESSAGE.get(), heartPlaceholders(attendance.penalty()));
            }
        }
    }

    // ----------------------------------------------------------------- hearts

    /**
     * Applies the lost hearts to the player's max health attribute.
     * The modifier is transient, so this method is also called after respawn.
     */
    public static void applyHearts(ServerPlayer player, PlayerPenalty penalty) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        int lost = Math.min(Math.max(0, penalty.heartsLost()), maxLostHearts());
        if (lost <= 0) {
            attribute.removeModifier(HEARTS_MODIFIER_ID);
        } else {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(
                    HEARTS_MODIFIER_ID,
                    -HEALTH_PER_HEART * lost,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * @return how many hearts a player can possibly lose.
     */
    public static int maxLostHearts() {
        int minHearts = Math.min(VANILLA_HEARTS, Math.max(1, Config.MIN_HEARTS.get()));
        return VANILLA_HEARTS - minHearts;
    }

    /**
     * @return the current amount of hearts (max health / 2).
     */
    public static int currentHearts(ServerPlayer player) {
        return Math.max(0, (int) Math.ceil(player.getMaxHealth() / HEALTH_PER_HEART));
    }

    // ------------------------------------------------------------------ utils

    private static void send(ServerPlayer player, String template, Map<String, String> placeholders) {
        player.sendSystemMessage(Component.literal(Messages.format(template, placeholders)));
    }

    private static Map<String, String> heartPlaceholders(PlayerPenalty penalty) {
        int lost = Math.min(Math.max(0, penalty.heartsLost()), maxLostHearts());
        return Messages.map(
                "hearts", String.valueOf(VANILLA_HEARTS - lost),
                "max_hearts", String.valueOf(VANILLA_HEARTS),
                "lost", String.valueOf(lost),
                "required", String.valueOf(Math.max(1, Config.RECOVERY_DAYS.get())),
                "streak", String.valueOf(penalty.streak()),
                "deaths", String.valueOf(penalty.deaths()),
                "hearts_per_death", String.valueOf(Config.HEARTS_PER_DEATH.get()));
    }

    public static String banMessage(long banUntil, long now) {
        long[] parts = TimeUtil.splitDuration(banUntil - now);
        return Messages.format(Config.BAN_MESSAGE.get(), Messages.map(
                "days", String.valueOf(parts[0]),
                "hours", String.valueOf(parts[1]),
                "minutes", String.valueOf(parts[2]),
                "until", TimeUtil.formatDateTime(banUntil),
                "required", String.valueOf(Math.max(1, Config.RECOVERY_DAYS.get())),
                "hearts_per_death", String.valueOf(Config.HEARTS_PER_DEATH.get())));
    }

    public record Attendance(PlayerPenalty penalty, boolean changed, boolean restored) {
    }
}
