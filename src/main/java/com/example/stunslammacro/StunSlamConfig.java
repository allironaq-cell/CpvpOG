package com.example.stunslammacro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Config file: .minecraft/config/stunslammacro.json (edit while the game is closed). */
public final class StunSlamConfig {

    /** Ticks between the axe hit and the mace hit. */
    public int gapTicks = 2;

    /**
     * true  = hotbar slot changes are sent to the server only; your own screen keeps showing
     *         the slot you are on (no visible flicker).
     * false = the hotbar visibly switches axe -> mace -> back (normal vanilla behaviour).
     */
    public boolean silentSwap = true;

    /** Only trigger while actually falling (mace smash needs fall distance). */
    public boolean requireFalling = true;

    /** Minimum fall distance (blocks) before the macro triggers. Mace smash needs more than 1.5. */
    public double minFallDistance = 1.5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("stunslammacro.json");
    }

    public static StunSlamConfig load() {
        Path file = path();
        StunSlamConfig config = new StunSlamConfig();
        try {
            if (Files.exists(file)) {
                StunSlamConfig read = GSON.fromJson(Files.readString(file), StunSlamConfig.class);
                if (read != null) config = read;
            }
            config.gapTicks = Math.max(1, config.gapTicks);
            Files.writeString(file, GSON.toJson(config));
        } catch (IOException | RuntimeException e) {
            System.err.println("[StunSlam] Could not read/write config, using defaults: " + e);
        }
        return config;
    }
}
