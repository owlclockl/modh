package io.github.owlclockl.hardcoredeathpenalty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.GameType;

/**
 * Persistent per-player penalty state.
 *
 * @param spectatorUntil     epoch millis until which the player is forced into spectator mode, 0 if none
 * @param heartsLost         how many hearts the player has lost
 * @param streak             how many days in a row the player has visited the server
 * @param lastDay            local day index (see {@link TimeUtil#dayIndex(long)}) of the last counted visit
 * @param deaths             total amount of deaths recorded by the mod
 * @param lastDeath          epoch millis of the last death
 * @param modeRestorePending {@code true} if the player's real game mode still has to be restored
 * @param restoreGameMode    the game mode to restore after the spectator time is over
 */
public record PlayerPenalty(
        long spectatorUntil,
        int heartsLost,
        int streak,
        long lastDay,
        int deaths,
        long lastDeath,
        boolean modeRestorePending,
        GameType restoreGameMode) {

    public static final GameType DEFAULT_MODE = GameType.SURVIVAL;

    public static final PlayerPenalty EMPTY = new PlayerPenalty(
            0L, 0, 0, Long.MIN_VALUE, 0, 0L, false, DEFAULT_MODE);

    private static final Codec<GameType> GAME_MODE_CODEC = Codec.STRING.xmap(
            PlayerPenalty::parseGameMode, GameType::getName);

    public static final Codec<PlayerPenalty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("spectator_until", 0L).forGetter(PlayerPenalty::spectatorUntil),
            Codec.INT.optionalFieldOf("hearts_lost", 0).forGetter(PlayerPenalty::heartsLost),
            Codec.INT.optionalFieldOf("streak", 0).forGetter(PlayerPenalty::streak),
            Codec.LONG.optionalFieldOf("last_day", Long.MIN_VALUE).forGetter(PlayerPenalty::lastDay),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(PlayerPenalty::deaths),
            Codec.LONG.optionalFieldOf("last_death", 0L).forGetter(PlayerPenalty::lastDeath),
            Codec.BOOL.optionalFieldOf("mode_restore_pending", false).forGetter(PlayerPenalty::modeRestorePending),
            GAME_MODE_CODEC.optionalFieldOf("restore_game_mode", DEFAULT_MODE).forGetter(PlayerPenalty::restoreGameMode)
    ).apply(instance, PlayerPenalty::new));

    private static GameType parseGameMode(String name) {
        if (name != null) {
            for (GameType type : GameType.values()) {
                if (type != GameType.SPECTATOR && type.getName().equalsIgnoreCase(name)) {
                    return type;
                }
            }
        }
        return DEFAULT_MODE;
    }

    /**
     * @return {@code true} while the player must stay a spectator.
     */
    public boolean isPunished(long now) {
        return this.spectatorUntil > now;
    }

    /**
     * @return {@code true} if the player still has punishment data to process.
     */
    public boolean hasPunishment() {
        return this.spectatorUntil > 0L || this.modeRestorePending;
    }

    public PlayerPenalty withSpectatorUntil(long value) {
        return new PlayerPenalty(value, this.heartsLost, this.streak, this.lastDay, this.deaths, this.lastDeath,
                this.modeRestorePending, this.restoreGameMode);
    }

    public PlayerPenalty withHeartsLost(int value) {
        return new PlayerPenalty(this.spectatorUntil, value, this.streak, this.lastDay, this.deaths, this.lastDeath,
                this.modeRestorePending, this.restoreGameMode);
    }

    public PlayerPenalty withStreak(int value) {
        return new PlayerPenalty(this.spectatorUntil, this.heartsLost, value, this.lastDay, this.deaths, this.lastDeath,
                this.modeRestorePending, this.restoreGameMode);
    }

    public PlayerPenalty withLastDay(long value) {
        return new PlayerPenalty(this.spectatorUntil, this.heartsLost, this.streak, value, this.deaths, this.lastDeath,
                this.modeRestorePending, this.restoreGameMode);
    }

    public PlayerPenalty withModeRestorePending(boolean value) {
        return new PlayerPenalty(this.spectatorUntil, this.heartsLost, this.streak, this.lastDay, this.deaths, this.lastDeath,
                value, this.restoreGameMode);
    }

    /**
     * Removes all punishment data (used by {@code /hdp reset}). The game mode
     * restore info is kept, so a player who was a spectator gets their mode back.
     */
    public PlayerPenalty cleared() {
        return new PlayerPenalty(0L, 0, 0, this.lastDay, this.deaths, this.lastDeath,
                this.modeRestorePending, this.restoreGameMode);
    }
}
