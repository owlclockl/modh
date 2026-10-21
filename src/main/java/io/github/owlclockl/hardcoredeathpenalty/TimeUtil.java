package io.github.owlclockl.hardcoredeathpenalty;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Real-time helpers. The mod uses real life time everywhere, never in-game ticks.
 */
public final class TimeUtil {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static String cachedZoneName;
    private static ZoneId cachedZone = ZoneId.systemDefault();

    private TimeUtil() {
    }

    /**
     * @return the configured time zone, or the JVM time zone when {@code timeZone} is "system".
     */
    public static ZoneId zone() {
        String configured = Config.TIME_ZONE.get();
        String name = (configured == null || configured.isBlank()) ? "system" : configured.trim();

        if (!name.equals(cachedZoneName)) {
            ZoneId zone = ZoneId.systemDefault();
            if (!name.equalsIgnoreCase("system") && !name.equalsIgnoreCase("default")) {
                try {
                    zone = ZoneId.of(name);
                } catch (DateTimeException e) {
                    HardcoreDeathPenalty.LOGGER.warn("Unknown time zone '{}', using the system time zone", name);
                }
            }
            cachedZone = zone;
            cachedZoneName = name;
        }
        return cachedZone;
    }

    /**
     * @return an ever increasing index of the current "server day".
     */
    public static long currentDayIndex() {
        return dayIndex(System.currentTimeMillis());
    }

    /**
     * Converts epoch millis into the index of the day, taking {@code dayStartHour} into account.
     */
    public static long dayIndex(long epochMillis) {
        ZonedDateTime time = Instant.ofEpochMilli(epochMillis).atZone(zone());
        return time.minusHours(Config.DAY_START_HOUR.get()).toLocalDate().toEpochDay();
    }

    public static String formatDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone()).format(DATE_TIME);
    }

    public static String formatDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone()).format(DATE_ONLY);
    }

    /**
     * Formats a duration as "days", "hours" and "minutes" (rounded up).
     */
    public static long[] splitDuration(long millis) {
        long totalMinutes = Math.max(0L, (millis + 59_999L) / 60_000L);
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;
        return new long[] { days, hours, minutes };
    }
}
