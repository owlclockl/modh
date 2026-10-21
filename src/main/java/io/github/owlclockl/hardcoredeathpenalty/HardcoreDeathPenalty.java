package io.github.owlclockl.hardcoredeathpenalty;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Fully server-side mod.
 * <p>
 * Death = temporary real-time ban + lost hearts. Lost hearts come back after a
 * daily login streak.
 */
@Mod(HardcoreDeathPenalty.MODID)
public final class HardcoreDeathPenalty {
    public static final String MODID = "hardcore_death_penalty";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HardcoreDeathPenalty(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SYNCED, Config.SPEC);
        LOGGER.info("Hardcore Death Penalty initialized");
    }
}
