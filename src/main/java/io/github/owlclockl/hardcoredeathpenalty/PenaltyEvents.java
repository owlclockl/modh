package io.github.owlclockl.hardcoredeathpenalty;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import io.github.owlclockl.hardcoredeathpenalty.command.PenaltyCommands;

/**
 * Server-side event handlers. Every hook is registered on the NeoForge game event bus.
 */
@EventBusSubscriber(modid = HardcoreDeathPenalty.MODID)
public final class PenaltyEvents {
    private PenaltyEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            PenaltyService.handleDeath(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PenaltyService.handleLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getServer() == null) {
            return;
        }
        PenaltySavedData storage = PenaltySavedData.get(player.level().getServer());
        PenaltyService.applyHearts(player, storage.get(player.getUUID()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PenaltyService.handleServerTick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PenaltyCommands.register(event.getDispatcher());
    }
}
