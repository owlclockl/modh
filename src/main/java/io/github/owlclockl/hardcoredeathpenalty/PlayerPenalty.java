package io.github.owlclockl.hardcoredeathpenalty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent per-player penalty state.
 *
 * @param banUntil       epoch millis until which the player cannot join, 0 if not banned
 * @param heartsLost     how many hearts the player has lost
 * @param streak         how many days in a row the player has visited the server
 * @param lastDay        local day index (see {@link TimeUtil#dayIndex(long)}) of the last counted visit
 * @param deaths         total amount of deaths recorded by the mod
 * @param lastDeath      epoch millis of the last death
 * @param restorePending {@code true} if the player died and the server still has to respawn them
 */
public record PlayerPenalty(
        long banUntil,
        int heartsLost,
        int streak,
        long lastDay,
        int deaths,
        long lastDeath,
        boolean restorePending) {

    public static final PlayerPenalty EMPTY = new PlayerPenalty(0L, 0, 0, Long.MIN_VALUE, 0, 0L, false);

    public static final Codec<PlayerPenalty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("ban_until", 0L).forGetter(PlayerPenalty::banUntil),
            Codec.INT.optionalFieldOf("hearts_lost", 0).forGetter(PlayerPenalty::heartsLost),
            Codec.INT.optionalFieldOf("streak", 0).forGetter(PlayerPenalty::streak),
            Codec.LONG.optionalFieldOf("last_day", Long.MIN_VALUE).forGetter(PlayerPenalty::lastDay),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(PlayerPenalty::deaths),
            Codec.LONG.optionalFieldOf("last_death", 0L).forGetter(PlayerPenalty::lastDeath),
            Codec.BOOL.optionalFieldOf("restore_pending", false).forGetter(PlayerPenalty::restorePending)
    ).apply(instance, PlayerPenalty::new));

    public boolean isBanned(long now) {
        return this.banUntil > now;
    }

    public PlayerPenalty withBanUntil(long value) {
        return new PlayerPenalty(value, this.heartsLost, this.streak, this.lastDay, this.deaths, this.lastDeath, this.restorePending);
    }

    public PlayerPenalty withHeartsLost(int value) {
        return new PlayerPenalty(this.banUntil, value, this.streak, this.lastDay, this.deaths, this.lastDeath, this.restorePending);
    }

    public PlayerPenalty withStreak(int value) {
        return new PlayerPenalty(this.banUntil, this.heartsLost, value, this.lastDay, this.deaths, this.lastDeath, this.restorePending);
    }

    public PlayerPenalty withLastDay(long value) {
        return new PlayerPenalty(this.banUntil, this.heartsLost, this.streak, value, this.deaths, this.lastDeath, this.restorePending);
    }

    public PlayerPenalty withRestorePending(boolean value) {
        return new PlayerPenalty(this.banUntil, this.heartsLost, this.streak, this.lastDay, this.deaths, this.lastDeath, value);
    }

    /**
     * Resets everything, including the ban and the lost hearts.
     */
    public PlayerPenalty cleared() {
        return new PlayerPenalty(0L, 0, 0, this.lastDay, this.deaths, this.lastDeath, false);
    }
}
