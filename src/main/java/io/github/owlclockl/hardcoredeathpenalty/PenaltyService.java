package io.github.owlclockl.hardcoredeathpenalty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;

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
     * The player stays dead until they press "Respawn", then {@link #handleRespawn}
     * puts them into spectator mode.
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
        long duration = Config.SPECTATOR_ENABLED.get() ? Config.spectatorDurationMillis() : 0L;

        int heartsLost = old.heartsLost();
        if (Config.LOSE_HEART_ON_DEATH.get() && Config.HEARTS_PER_DEATH.get() > 0) {
            heartsLost = Math.min(maxLostHearts(), heartsLost + Config.HEARTS_PER_DEATH.get());
        }

        PlayerPenalty updated = new PlayerPenalty(
                duration > 0L ? now + duration : 0L,
                heartsLost,
                old.streak(),
                old.lastDay(),
                old.deaths() + 1,
                now,
                duration > 0L,
                realGameMode(player));
        storage.set(playerId, updated);

        if (duration > 0L && Config.ANNOUNCE_DEATH.get()) {
            Map<String, String> placeholders = punishmentPlaceholders(updated, now);
            placeholders.put("player", player.getName().getString());
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(Messages.format(Config.DEATH_ANNOUNCEMENT.get(), placeholders)), false);
        }

        if (duration > 0L) {
            // Make sure the daily checks run as long as somebody is online.
            startOnlineWatcher(server);
        }
    }

    // ---------------------------------------------------------------- respawn

    /**
     * Called after the player pressed "Respawn" and the new player entity exists.
     * Runs one tick later, so vanilla has already finished its own respawn logic
     * (in a hardcore world vanilla switches the player to spectator there).
     */
    public static void scheduleRespawnFix(MinecraftServer server, UUID playerId) {
        server.schedule(new TickTask(0, () -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.connection == null) {
                return;
            }

            PenaltySavedData storage = PenaltySavedData.get(server);
            PlayerPenalty penalty = storage.get(playerId);
            applyHearts(player, penalty);

            if (penalty.isPunished(System.currentTimeMillis())) {
                if (!player.isSpectator()) {
                    player.setGameMode(GameType.SPECTATOR);
                }
                send(player, Config.SPECTATOR_JOIN_MESSAGE.get(), punishmentPlaceholders(penalty, System.currentTimeMillis()));
            } else if (penalty.modeRestorePending() && player.isSpectator()) {
                player.setGameMode(penalty.restoreGameMode());
            }
        }));
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

        startOnlineWatcher(server);

        if (penalty.isPunished(now)) {
            // The spectator period is still running.
            if (!player.isDeadOrDying() && !player.isSpectator()) {
                player.setGameMode(GameType.SPECTATOR);
            }
            applyHearts(player, penalty);
            if (!player.isDeadOrDying()) {
                send(player, Config.SPECTATOR_JOIN_MESSAGE.get(), punishmentPlaceholders(penalty, now));
            }
            // A dead player sees the vanilla death screen and becomes a spectator
            // as soon as they press "Respawn" (see scheduleRespawnFix).
            return;
        }

        if (penalty.hasPunishment()) {
            // The spectator period ended while the player was away.
            server.schedule(new TickTask(0, () -> finishPunishment(server, playerId)));
            return;
        }

        applyHearts(player, penalty);
        handleDailyVisit(player, penalty, storage);
    }

    /**
     * Counts the visit of the day and sends the matching message.
     */
    private static void handleDailyVisit(ServerPlayer player, PlayerPenalty penalty, PenaltySavedData storage) {
        if (penalty.heartsLost() <= 0) {
            return;
        }

        Attendance attendance = updateAttendance(player, penalty);
        if (!attendance.changed()) {
            return;
        }

        storage.set(player.getUUID(), attendance.penalty());
        if (attendance.restored()) {
            send(player, Config.HEART_RESTORED_MESSAGE.get(), heartPlaceholders(attendance.penalty()));
        } else {
            send(player, Config.STREAK_PROGRESS_MESSAGE.get(), heartPlaceholders(attendance.penalty()));
        }
    }

    /**
     * Ends the spectator period: restores the game mode, brings the player back
     * to their spawn point and applies the heart penalty for returning.
     */
    private static void finishPunishment(MinecraftServer server, UUID playerId) {
        PenaltySavedData storage = PenaltySavedData.get(server);
        PlayerPenalty penalty = storage.get(playerId);
        if (!penalty.hasPunishment()) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);

        if (player != null) {
            if (!player.isDeadOrDying()) {
                if (player.isSpectator()) {
                    player.setGameMode(penalty.restoreGameMode());
                }
                if (Config.RETURN_TO_SPAWN.get() && penalty.modeRestorePending()) {
                    player = teleportToSpawn(player);
                }
                applyHearts(player, penalty);
            } else {
                applyHearts(player, penalty);
            }
        }

        penalty = penalty.withSpectatorUntil(0L).withModeRestorePending(false);
        storage.set(playerId, penalty);

        if (player == null || penalty.heartsLost() <= 0) {
            if (player != null) {
                send(player, Config.PUNISHMENT_ENDED_MESSAGE.get(), heartPlaceholders(penalty));
            }
            return;
        }

        send(player, Config.HEART_LOST_MESSAGE.get(), heartPlaceholders(penalty));
        handleDailyVisit(player, penalty, storage);
    }

    /**
     * @return the player entity after the teleport (a new one if another dimension was entered).
     */
    private static ServerPlayer teleportToSpawn(ServerPlayer player) {
        TeleportTransition transition;
        try {
            transition = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
        } catch (RuntimeException e) {
            HardcoreDeathPenalty.LOGGER.warn("Could not find a respawn position for {}", player.getName().getString(), e);
            return player;
        }
        if (transition == null) {
            return player;
        }
        try {
            ServerPlayer result = player.teleport(transition);
            return result != null ? result : player;
        } catch (RuntimeException e) {
            HardcoreDeathPenalty.LOGGER.warn("Could not teleport {} to their spawn", player.getName().getString(), e);
            return player;
        }
    }

    // --------------------------------------------------------------- watching

    private static final Set<MinecraftServer> WATCHED_SERVERS = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Starts the tick task that ends spectator periods and counts daily visits
     * of online players. Does nothing if the task is already running.
     */
    public static void startOnlineWatcher(MinecraftServer server) {
        if (WATCHED_SERVERS.add(server)) {
            server.schedule(new TickTask(0, new OnlineWatcher(server)));
        }
    }

    public static void stopOnlineWatcher(MinecraftServer server) {
        WATCHED_SERVERS.remove(server);
    }

    /**
     * Runs once per second while at least one player is online.
     */
    private static final class OnlineWatcher implements Runnable {
        private final MinecraftServer server;

        private OnlineWatcher(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public void run() {
            boolean keepRunning;
            try {
                keepRunning = tick();
            } catch (RuntimeException e) {
                HardcoreDeathPenalty.LOGGER.error("Hardcore Death Penalty tick failed", e);
                keepRunning = true;
            }

            if (keepRunning && WATCHED_SERVERS.contains(this.server)) {
                this.server.schedule(new TickTask(this.server.getTickCount() + 20, this));
            } else {
                WATCHED_SERVERS.remove(this.server);
            }
        }

        private boolean tick() {
            long now = System.currentTimeMillis();
            List<ServerPlayer> players = new ArrayList<>(this.server.getPlayerList().getPlayers());
            if (players.isEmpty()) {
                return false;
            }

            PenaltySavedData storage = PenaltySavedData.get(this.server);
            for (ServerPlayer player : players) {
                UUID playerId = player.getUUID();
                PlayerPenalty penalty = storage.get(playerId);

                if (penalty.isPunished(now)) {
                    if (!player.isSpectator() && !player.isDeadOrDying()) {
                        player.setGameMode(GameType.SPECTATOR);
                    }
                    continue;
                }

                if (penalty.hasPunishment()) {
                    finishPunishment(this.server, playerId);
                    continue;
                }

                handleDailyVisit(player, penalty, storage);
            }
            return true;
        }
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
     * @return the game mode that has to be restored after the spectator period.
     */
    public static GameType realGameMode(ServerPlayer player) {
        GameType mode = player.gameMode();
        if (mode == null || mode == GameType.SPECTATOR) {
            return PlayerPenalty.DEFAULT_MODE;
        }
        return mode;
    }

    /**
     * @return how many hearts a player can possibly lose.
     */
    public static int maxLostHearts() {
        int minHearts = Math.min(VANILLA_HEARTS, Math.max(1, Config.MIN_HEARTS.get()));
        return VANILLA_HEARTS - minHearts;
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

    /**
     * Placeholders for the spectator messages: time left and the moment the
     * player can play again.
     */
    public static Map<String, String> punishmentPlaceholders(PlayerPenalty penalty, long now) {
        long[] parts = TimeUtil.splitDuration(penalty.spectatorUntil() - now);
        Map<String, String> map = heartPlaceholders(penalty);
        map.put("days", String.valueOf(parts[0]));
        map.put("hours", String.valueOf(parts[1]));
        map.put("minutes", String.valueOf(parts[2]));
        map.put("until", TimeUtil.formatDateTime(penalty.spectatorUntil()));
        return map;
    }

    public record Attendance(PlayerPenalty penalty, boolean changed, boolean restored) {
    }
}
