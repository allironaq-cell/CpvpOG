package com.example.stunslammacro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

/**
 * The stun slam logic.
 *
 * You left-click a player while falling (e.g. after a wind charge):
 *   - shield UP    -> axe hit, then mace hit {@code gapTicks} (2) ticks later
 *   - shield DOWN  -> mace hit straight away from wherever you are
 * Afterwards your original slot is restored.
 *
 * If the needed items are missing, you're on the ground, or the target isn't a player,
 * the macro stays out of the way and your normal attack happens untouched.
 */
public final class StunSlam {

    private enum Phase { IDLE, AXE, MACE }

    private final StunSlamConfig config;

    private Phase phase = Phase.IDLE;
    private boolean internalAttack = false; // true while WE call attackEntity (so we don't intercept ourselves)
    private PlayerEntity target;
    private int axeSlot = -1;
    private int maceSlot = -1;
    private int originalSlot = 0;
    private int ticksLeft = 0;

    public StunSlam(StunSlamConfig config) {
        this.config = config;
    }

    /**
     * Called from the attack callback.
     * @return true if the macro takes over this click (vanilla attack is cancelled), false to let it through.
     */
    public boolean onAttack(MinecraftClient client, Entity entity) {
        if (internalAttack) return false;          // our own attack, let it through
        if (phase != Phase.IDLE) return true;      // sequence running: swallow extra clicks (no double hits)

        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || client.getNetworkHandler() == null) return false;
        if (!(entity instanceof PlayerEntity victim) || victim == player || !victim.isAlive()) return false;

        // Must be falling for the mace smash to work.
        if (config.requireFalling && (player.isOnGround() || player.fallDistance <= config.minFallDistance)) {
            return false;
        }

        PlayerInventory inv = player.getInventory();
        int mace = findMace(inv);
        if (mace < 0) return false;                // no mace -> normal hit

        boolean shieldUp = victim.isBlocking();
        int axe = -1;
        if (shieldUp) {
            axe = findAxe(inv);
            if (axe < 0) return false;             // no axe -> normal hit
        } else if (inv.getSelectedSlot() == mace) {
            return false;                          // already holding the mace -> plain vanilla hit
        }

        this.target = victim;
        this.maceSlot = mace;
        this.axeSlot = axe;
        this.originalSlot = inv.getSelectedSlot();
        this.ticksLeft = 0;                        // first step runs this very tick
        this.phase = shieldUp ? Phase.AXE : Phase.MACE;
        return true;
    }

    /** Runs every client tick. */
    public void tick(MinecraftClient client) {
        if (phase == Phase.IDLE) return;

        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || client.getNetworkHandler() == null
                || target == null || !target.isAlive() || target.isRemoved()) {
            finish(client);
            return;
        }

        if (ticksLeft > 0) ticksLeft--;
        if (ticksLeft > 0) return;

        // Target walked out of reach between the two hits -> stop cleanly.
        if (!player.canInteractWithEntity(target, 0.0)) {
            finish(client);
            return;
        }

        if (phase == Phase.AXE) {
            strike(client, axeSlot);
            phase = Phase.MACE;
            ticksLeft = Math.max(1, config.gapTicks); // mace lands gapTicks later
        } else {
            strike(client, maceSlot);
            finish(client);
        }
    }

    /** Select a hotbar slot and attack the target with it. */
    private void strike(MinecraftClient client, int slot) {
        ClientPlayerEntity player = client.player;
        if (config.silentSwap) {
            // Tell the server about the slot + attack; the local hotbar stays where it is.
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            client.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, player.isSneaking()));
        } else {
            player.getInventory().setSelectedSlot(slot);
            internalAttack = true;
            try {
                client.interactionManager.attackEntity(player, target);
            } finally {
                internalAttack = false;
            }
        }
        player.swingHand(Hand.MAIN_HAND);
    }

    /** End of sequence (or abort): put the server/client back on the slot the player is really on. */
    private void finish(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && client.getNetworkHandler() != null) {
            if (config.silentSwap) {
                client.getNetworkHandler().sendPacket(
                        new UpdateSelectedSlotC2SPacket(player.getInventory().getSelectedSlot()));
            } else {
                player.getInventory().setSelectedSlot(originalSlot);
            }
        }
        phase = Phase.IDLE;
        target = null;
    }

    // ------------------------------------------------------------ hotbar scan

    private static int findMace(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.MACE)) return i;
        }
        return -1;
    }

    private static int findAxe(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isIn(ItemTags.AXES)) return i;
        }
        return -1;
    }
}
