package io.github.owlclockl.hardcoredeathpenalty;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration of the mod.
 * <p>
 * The file is created at {@code config/hardcore_death_penalty-synced.toml}.
 * A world can override it with {@code <world>/syncedconfig/hardcore_death_penalty-synced.toml}.
 * <p>
 * Any message supports placeholders in curly braces, for example {@code {days}}.
 * Unknown placeholders are left untouched.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // -------------------------------------------------------------- spectator
    public static final ModConfigSpec.BooleanValue SPECTATOR_ENABLED;
    public static final ModConfigSpec.IntValue SPECTATOR_DAYS;
    public static final ModConfigSpec.IntValue SPECTATOR_MINUTES;
    public static final ModConfigSpec.BooleanValue RETURN_TO_SPAWN;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_DEATH;
    public static final ModConfigSpec.ConfigValue<String> DEATH_ANNOUNCEMENT;

    // --------------------------------------------------------------- hearts
    public static final ModConfigSpec.BooleanValue LOSE_HEART_ON_DEATH;
    public static final ModConfigSpec.IntValue HEARTS_PER_DEATH;
    public static final ModConfigSpec.IntValue MIN_HEARTS;
    public static final ModConfigSpec.IntValue RECOVERY_DAYS;
    public static final ModConfigSpec.BooleanValue RESET_STREAK_ON_MISSED_DAY;

    // ------------------------------------------------------------------ day
    public static final ModConfigSpec.IntValue DAY_START_HOUR;
    public static final ModConfigSpec.ConfigValue<String> TIME_ZONE;

    // ------------------------------------------------------------- messages
    public static final ModConfigSpec.ConfigValue<String> SPECTATOR_START_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> SPECTATOR_JOIN_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> PUNISHMENT_ENDED_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> HEART_LOST_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> STREAK_PROGRESS_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> HEART_RESTORED_MESSAGE;

    static {
        BUILDER.comment(
                "Death punishment.",
                "After death the player is respawned as a spectator for the configured time.",
                "When the time is over the player is returned to the game and loses a heart.")
                .push("spectator");
        SPECTATOR_ENABLED = BUILDER
                .comment("Put dead players into spectator mode instead of letting them play.")
                .define("enabled", true);
        SPECTATOR_DAYS = BUILDER
                .comment("How many real (not in-game) days a dead player stays a spectator.")
                .defineInRange("durationDays", 3, 0, 3650);
        SPECTATOR_MINUTES = BUILDER
                .comment(
                        "Optional spectator time in minutes. If greater than 0, it overrides durationDays.",
                        "Useful for testing. 0 = use durationDays.")
                .defineInRange("durationMinutes", 0, 0, 5_256_000);
        RETURN_TO_SPAWN = BUILDER
                .comment(
                        "Teleport the player to their respawn point (bed / anchor / world spawn)",
                        "when the spectator time is over. Recommended: it prevents returning to the game",
                        "inside a block or high in the air.")
                .define("returnToSpawn", true);
        ANNOUNCE_DEATH = BUILDER
                .comment("Broadcast a message to all online players when somebody dies.")
                .define("announceDeath", true);
        DEATH_ANNOUNCEMENT = BUILDER
                .comment("Placeholders: {player}, {days}, {hours}, {minutes}, {until}")
                .define("deathAnnouncement",
                        "\u00a78[\u00a7c\u2620\u00a78] \u00a7c{player} \u00a7f\u043f\u043e\u0433\u0438\u0431 \u0438 \u0441\u0442\u0430\u043b \u043d\u0430\u0431\u043b\u044e\u0434\u0430\u0442\u0435\u043b\u0435\u043c \u043d\u0430 \u00a7e{days}\u00a7f \u0434. \u00a7e{hours}\u00a7f \u0447.");
        BUILDER.pop();

        BUILDER.comment(
                "Heart penalty.",
                "Every death can take hearts away. The player can win them back by joining the server every day.")
                .push("hearts");
        LOSE_HEART_ON_DEATH = BUILDER
                .comment("Take hearts away when a player dies.")
                .define("loseHeartOnDeath", true);
        HEARTS_PER_DEATH = BUILDER
                .comment("How many hearts are taken away per death.")
                .defineInRange("heartsPerDeath", 1, 0, 10);
        MIN_HEARTS = BUILDER
                .comment("The player can never go below this amount of hearts.")
                .defineInRange("minHearts", 1, 1, 10);
        RECOVERY_DAYS = BUILDER
                .comment(
                        "How many days in a row a player has to join the server to win back ONE heart.",
                        "Set to 1 to give a heart back for every day.")
                .defineInRange("recoveryDays", 7, 1, 3650);
        RESET_STREAK_ON_MISSED_DAY = BUILDER
                .comment(
                        "true  - a missed day resets the streak and the player starts from day 1;",
                        "false - missed days are ignored and the streak keeps growing.")
                .define("resetStreakOnMissedDay", true);
        BUILDER.pop();

        BUILDER.comment(
                "What counts as a 'day'.",
                "A player counts as 'visited today' if they are online at least once during the day,",
                "including staying online across midnight.")
                .push("day");
        DAY_START_HOUR = BUILDER
                .comment("Hour (0-23) at which a new day starts, in the configured time zone.")
                .defineInRange("dayStartHour", 0, 0, 23);
        TIME_ZONE = BUILDER
                .comment(
                        "Time zone used to calculate days, for example \"Europe/Moscow\".",
                        "\"system\" uses the time zone of the server JVM.")
                .define("timeZone", "system");
        BUILDER.pop();

        BUILDER.comment(
                "Messages sent to players. Legacy color codes like \u00a7c are supported.",
                "Placeholders: {player}, {days}, {hours}, {minutes}, {until},",
                "{hearts}, {max_hearts}, {lost}, {required}, {streak}, {deaths}, {hearts_per_death}")
                .push("messages");
        SPECTATOR_START_MESSAGE = BUILDER
                .comment("Sent right after death, when the player becomes a spectator.")
                .define("spectatorStart",
                        "\u00a7c\u00a7l\u0412\u044b \u043f\u043e\u0433\u0438\u0431\u043b\u0438!\n"
                                + "\u00a7r\u00a7f\u0422\u0435\u043f\u0435\u0440\u044c \u0432\u044b \u043d\u0430\u0431\u043b\u044e\u0434\u0430\u0442\u0435\u043b\u044c \u043d\u0430 \u00a7e{days}\u00a7f \u0434. \u00a7e{hours}\u00a7f \u0447.\n"
                                + "\u00a77\u0412\u0435\u0440\u043d\u0443\u0442\u044c\u0441\u044f \u0432 \u0438\u0433\u0440\u0443 \u043c\u043e\u0436\u043d\u043e \u00a7e{until}\u00a77.\n"
                                + "\u00a77\u041f\u043e\u0441\u043b\u0435 \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0435\u043d\u0438\u044f \u0432\u044b \u043f\u043e\u0442\u0435\u0440\u044f\u0435\u0442\u0435 \u0441\u0435\u0440\u0434\u0446\u0435.");
        SPECTATOR_JOIN_MESSAGE = BUILDER
                .comment("Sent when a punished player joins the server while still a spectator.")
                .define("spectatorJoin",
                        "\u00a7c\u00a7l\u0412\u044b \u043f\u043e\u0433\u0438\u0431\u043b\u0438!\n"
                                + "\u00a7r\u00a7f\u0414\u043e \u043a\u043e\u043d\u0446\u0430 \u0440\u0435\u0436\u0438\u043c\u0430 \u043d\u0430\u0431\u043b\u044e\u0434\u0430\u0442\u0435\u043b\u044f: \u00a7e{days}\u00a7f \u0434. \u00a7e{hours}\u00a7f \u0447. \u00a7e{minutes}\u00a7f \u043c\u0438\u043d.\n"
                                + "\u00a77\u0418\u0433\u0440\u0430 \u0441\u043d\u043e\u0432\u0430 \u0431\u0443\u0434\u0435\u0442 \u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0430 \u00a7e{until}\u00a77.");
        PUNISHMENT_ENDED_MESSAGE = BUILDER
                .comment("Sent when the spectator time is over and the player has no lost hearts.")
                .define("punishmentEnded", "\u00a7a\u0412\u044b \u0441\u043d\u043e\u0432\u0430 \u043c\u043e\u0436\u0435\u0442\u0435 \u0438\u0433\u0440\u0430\u0442\u044c!");
        HEART_LOST_MESSAGE = BUILDER
                .comment("Sent when a player returns to the game after the spectator time.")
                .define("heartLost",
                        "\u00a7a\u00a7l\u0412\u044b \u0441\u043d\u043e\u0432\u0430 \u043c\u043e\u0436\u0435\u0442\u0435 \u0438\u0433\u0440\u0430\u0442\u044c!\n"
                                + "\u00a7r\u00a7c\u0412\u044b \u043f\u043e\u0442\u0435\u0440\u044f\u043b\u0438 \u0441\u0435\u0440\u0434\u0446\u0435.\n"
                                + "\u00a7f\u0421\u0435\u0439\u0447\u0430\u0441 \u0443 \u0432\u0430\u0441 \u00a7e{hearts}\u00a7f/\u00a7e{max_hearts}\u00a7f \u0441\u0435\u0440\u0434\u0435\u0446.\n"
                                + "\u00a77\u0417\u0430\u0445\u043e\u0434\u0438\u0442\u0435 \u043a\u0430\u0436\u0434\u044b\u0439 \u0434\u0435\u043d\u044c \u00a7e{required}\u00a77 \u0434\u043d. \u043f\u043e\u0434\u0440\u044f\u0434, \u0447\u0442\u043e\u0431\u044b \u0432\u0435\u0440\u043d\u0443\u0442\u044c \u0435\u0433\u043e.\n"
                                + "\u00a77\u041f\u0440\u043e\u0433\u0440\u0435\u0441\u0441: \u00a7e{streak}\u00a77/\u00a7e{required}\u00a77");
        STREAK_PROGRESS_MESSAGE = BUILDER
                .comment("Sent when a player visits the server on a new day.")
                .define("streakProgress",
                        "\u00a7a\u041f\u0440\u043e\u0433\u0440\u0435\u0441\u0441 \u0432\u043e\u0437\u0432\u0440\u0430\u0442\u0430 \u0441\u0435\u0440\u0434\u0446\u0430: \u00a7e{streak}\u00a7a/\u00a7e{required}\u00a7a \u0434\u043d.");
        HEART_RESTORED_MESSAGE = BUILDER
                .comment("Sent when the player wins a heart back.")
                .define("heartRestored",
                        "\u00a7a\u00a7l\u0421\u0435\u0440\u0434\u0446\u0435 \u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0435\u043d\u043e!\n"
                                + "\u00a7r\u00a7f\u0422\u0435\u043f\u0435\u0440\u044c \u0443 \u0432\u0430\u0441 \u00a7e{hearts}\u00a7f/\u00a7e{max_hearts}\u00a7f \u0441\u0435\u0440\u0434\u0435\u0446.");
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /**
     * @return the spectator duration in milliseconds.
     */
    public static long spectatorDurationMillis() {
        long minutes = SPECTATOR_MINUTES.get();
        if (minutes > 0L) {
            return minutes * 60_000L;
        }
        return SPECTATOR_DAYS.get() * 86_400_000L;
    }
}
