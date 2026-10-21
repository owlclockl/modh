package io.github.owlclockl.hardcoredeathpenalty;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;

/**
 * World-level storage of all player penalties. It is saved into
 * {@code world/data/hardcore_death_penalty.dat}.
 */
public class PenaltySavedData extends SavedData {
    // Declared before TYPE, because TYPE's codec factory needs it.
    private static final Codec<Map<UUID, PlayerPenalty>> MAP_CODEC = NeoForgeExtraCodecs.unboundedMapAsList(
            "player", UUIDUtil.CODEC, "penalty", PlayerPenalty.CODEC);

    private static final Codec<PenaltySavedData> CODEC = MAP_CODEC.xmap(
            PenaltySavedData::new, PenaltySavedData::copyOfMap);

    public static final SavedDataType<PenaltySavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(HardcoreDeathPenalty.MODID, "penalties"),
            PenaltySavedData::new,
            CODEC,
            null);

    private final Map<UUID, PlayerPenalty> penalties = new HashMap<>();

    public PenaltySavedData() {
    }

    private PenaltySavedData(Map<UUID, PlayerPenalty> penalties) {
        this.penalties.putAll(penalties);
    }

    public static PenaltySavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("The overworld is not loaded yet");
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerPenalty get(UUID playerId) {
        return this.penalties.getOrDefault(playerId, PlayerPenalty.EMPTY);
    }

    public void set(UUID playerId, PlayerPenalty penalty) {
        if (penalty.equals(PlayerPenalty.EMPTY)) {
            this.penalties.remove(playerId);
        } else {
            this.penalties.put(playerId, penalty);
        }
        this.setDirty();
    }

    public Map<UUID, PlayerPenalty> all() {
        return Collections.unmodifiableMap(this.penalties);
    }

    private Map<UUID, PlayerPenalty> copyOfMap() {
        return new HashMap<>(this.penalties);
    }
}
