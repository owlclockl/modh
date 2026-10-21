package io.github.owlclockl.hardcoredeathpenalty.command;

import java.util.Collection;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.GameType;

import io.github.owlclockl.hardcoredeathpenalty.Config;
import io.github.owlclockl.hardcoredeathpenalty.PenaltySavedData;
import io.github.owlclockl.hardcoredeathpenalty.PenaltyService;
import io.github.owlclockl.hardcoredeathpenalty.PlayerPenalty;
import io.github.owlclockl.hardcoredeathpenalty.TimeUtil;

/**
 * Admin commands:
 * <ul>
 *     <li>{@code /hdp status [player]}</li>
 *     <li>{@code /hdp end <player>} - ends the spectator period now</li>
 *     <li>{@code /hdp sethearts <player> <lost>}</li>
 *     <li>{@code /hdp reset <player>}</li>
 * </ul>
 */
public final class PenaltyCommands {
    private PenaltyCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hdp")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("status")
                        .executes(context -> statusSelf(context.getSource()))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> status(context, GameProfileArgument.getGameProfiles(context, "player")))))
                .then(Commands.literal("end")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> endPunishment(context.getSource(), GameProfileArgument.getGameProfiles(context, "player")))))
                .then(Commands.literal("sethearts")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("lost", IntegerArgumentType.integer(0, 10))
                                        .executes(context -> setHearts(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "player"),
                                                IntegerArgumentType.getInteger(context, "lost"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> reset(context.getSource(), GameProfileArgument.getGameProfiles(context, "player"))))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("""
                \u00a78===== \u00a7cHardcore Death Penalty \u00a78=====
                \u00a7e/hdp status \u00a77- \u0441\u0432\u043e\u0439 \u0441\u0442\u0430\u0442\u0443\u0441
                \u00a7e/hdp status <\u0438\u0433\u0440\u043e\u043a> \u00a77- \u0441\u0442\u0430\u0442\u0443\u0441 \u0438\u0433\u0440\u043e\u043a\u0430
                \u00a7e/hdp end <\u0438\u0433\u0440\u043e\u043a> \u00a77- \u0434\u043e\u0441\u0440\u043e\u0447\u043d\u043e \u0432\u0435\u0440\u043d\u0443\u0442\u044c \u0432 \u0438\u0433\u0440\u0443
                \u00a7e/hdp sethearts <\u0438\u0433\u0440\u043e\u043a> <0-10> \u00a77- \u0437\u0430\u0434\u0430\u0442\u044c \u043f\u043e\u0442\u0435\u0440\u044f\u043d\u043d\u044b\u0435 \u0441\u0435\u0440\u0434\u0446\u0430
                \u00a7e/hdp reset <\u0438\u0433\u0440\u043e\u043a> \u00a77- \u0441\u0431\u0440\u043e\u0441\u0438\u0442\u044c \u0432\u0441\u0435 \u0448\u0442\u0440\u0430\u0444\u044b
                """), false);
        return 1;
    }

    private static int statusSelf(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        PlayerPenalty penalty = PenaltySavedData.get(server).get(player.getUUID());
        source.sendSuccess(() -> Component.literal(statusText(player.getName().getString(), player.getUUID(), penalty)), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context, Collection<NameAndId> targets) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        PenaltySavedData storage = PenaltySavedData.get(server);
        for (NameAndId target : targets) {
            PlayerPenalty penalty = storage.get(target.id());
            source.sendSuccess(() -> Component.literal(statusText(target.name(), target.id(), penalty)), false);
        }
        return targets.size();
    }

    /**
     * Removes the spectator punishment right now, a watchdog (or the next
     * login/respawn) then restores the real game mode.
     */
    private static int endPunishment(CommandSourceStack source, Collection<NameAndId> targets) {
        MinecraftServer server = source.getServer();
        PenaltySavedData storage = PenaltySavedData.get(server);

        for (NameAndId target : targets) {
            PlayerPenalty penalty = storage.get(target.id());
            if (!penalty.hasPunishment() && !penalty.welcomePending()) {
                source.sendSuccess(() -> Component.literal("\u00a77" + target.name()
                        + " \u00a7f\u043d\u0435 \u043d\u0430\u0445\u043e\u0434\u0438\u0442\u0441\u044f \u0432 \u0440\u0435\u0436\u0438\u043c\u0435 \u043d\u0430\u0431\u043b\u044e\u0434\u0430\u0442\u0435\u043b\u044f."), false);
                continue;
            }
            storage.set(target.id(), penalty.withSpectatorUntil(0L).withWelcomePending(false));
            source.sendSuccess(() -> Component.literal("\u00a7a" + target.name()
                    + " \u00a7f\u0432\u043e\u0437\u0432\u0440\u0430\u0449\u0435\u043d \u0432 \u0438\u0433\u0440\u0443."), true);

            ServerPlayer online = server.getPlayerList().getPlayer(target.id());
            if (online != null && !online.isDeadOrDying()) {
                online.sendSystemMessage(Component.literal("\u00a7a\u0410\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440 \u0432\u0435\u0440\u043d\u0443\u043b \u0432\u0430\u0441 \u0432 \u0438\u0433\u0440\u0443."));
                if (online.isSpectator()) {
                    online.setGameMode(penalty.restoreGameMode());
                }
                storage.set(target.id(), storage.get(target.id()).withModeRestorePending(false));
            }
            PenaltyService.startOnlineWatcher(server);
        }
        return targets.size();
    }

    private static int setHearts(CommandSourceStack source, Collection<NameAndId> targets, int lost) {
        MinecraftServer server = source.getServer();
        PenaltySavedData storage = PenaltySavedData.get(server);
        int clamped = Math.min(Math.max(0, lost), PenaltyService.maxLostHearts());

        for (NameAndId target : targets) {
            PlayerPenalty penalty = storage.get(target.id()).withHeartsLost(clamped);
            storage.set(target.id(), penalty);
            source.sendSuccess(() -> Component.literal("\u00a7a" + target.name() + ": \u00a7f\u043f\u043e\u0442\u0435\u0440\u044f\u043d\u043e \u00a7c"
                    + clamped + " \u00a7f\u0441\u0435\u0440\u0434\u0435\u0446(\u0430)."), true);

            ServerPlayer online = server.getPlayerList().getPlayer(target.id());
            if (online != null) {
                PenaltyService.applyHearts(online, penalty);
            }
        }
        return targets.size();
    }

    private static int reset(CommandSourceStack source, Collection<NameAndId> targets) {
        MinecraftServer server = source.getServer();
        PenaltySavedData storage = PenaltySavedData.get(server);

        for (NameAndId target : targets) {
            PlayerPenalty penalty = storage.get(target.id()).cleared();
            storage.set(target.id(), penalty);
            source.sendSuccess(() -> Component.literal("\u00a7a" + target.name()
                    + ": \u00a7f\u0432\u0441\u0435 \u0448\u0442\u0440\u0430\u0444\u044b \u0441\u0431\u0440\u043e\u0448\u0435\u043d\u044b."), true);

            ServerPlayer online = server.getPlayerList().getPlayer(target.id());
            if (online != null) {
                PenaltyService.applyHearts(online, penalty);
                if (online.isSpectator() && !online.isDeadOrDying()) {
                    online.setGameMode(penalty.restoreGameMode());
                }
            }
            PenaltyService.startOnlineWatcher(server);
        }
        return targets.size();
    }

    private static String statusText(String name, UUID playerId, PlayerPenalty penalty) {
        long now = System.currentTimeMillis();
        int lost = Math.min(Math.max(0, penalty.heartsLost()), PenaltyService.maxLostHearts());
        int hearts = PenaltyService.VANILLA_HEARTS - lost;

        StringBuilder text = new StringBuilder();
        text.append("\u00a78===== \u00a7cHardcore Death Penalty \u00a78=====\n");
        text.append("\u00a77\u0418\u0433\u0440\u043e\u043a: \u00a7f").append(name).append('\n');
        text.append("\u00a77UUID: \u00a78").append(playerId).append('\n');
        text.append("\u00a77\u0421\u0435\u0440\u0434\u0446\u0430: \u00a7c").append(hearts).append("\u00a77/\u00a7c")
                .append(PenaltyService.VANILLA_HEARTS).append("\u00a77 (\u043f\u043e\u0442\u0435\u0440\u044f\u043d\u043e: \u00a7c")
                .append(lost).append("\u00a77)\n");
        text.append("\u00a77\u0421\u043c\u0435\u0440\u0442\u0435\u0439: \u00a7f").append(penalty.deaths()).append('\n');

        if (penalty.isPunished(now)) {
            text.append("\u00a77\u0420\u0435\u0436\u0438\u043c \u043d\u0430\u0431\u043b\u044e\u0434\u0430\u0442\u0435\u043b\u044f \u0434\u043e: \u00a7c")
                    .append(TimeUtil.formatDateTime(penalty.spectatorUntil())).append('\n');
        } else {
            text.append("\u00a77\u0420\u0435\u0436\u0438\u043c \u043d\u0430\u0431\u043b\u044e\u0434\u0430\u0442\u0435\u043b\u044f: \u00a7a\u043d\u0435\u0442\n");
        }

        if (penalty.modeRestorePending()) {
            text.append("\u00a77\u0412\u043e\u0437\u0432\u0440\u0430\u0442 \u0440\u0435\u0436\u0438\u043c\u0430 \u0438\u0433\u0440\u044b: \u00a7f")
                    .append(penalty.restoreGameMode() == GameType.SPECTATOR ? GameType.SURVIVAL.getName() : penalty.restoreGameMode().getName())
                    .append('\n');
        }

        if (lost > 0) {
            text.append("\u00a77\u0421\u0435\u0440\u0438\u044f \u0432\u0445\u043e\u0434\u043e\u0432: \u00a7a").append(penalty.streak())
                    .append("\u00a77/\u00a7a").append(Math.max(1, Config.RECOVERY_DAYS.get())).append('\n');
        }

        return text.toString();
    }
}
