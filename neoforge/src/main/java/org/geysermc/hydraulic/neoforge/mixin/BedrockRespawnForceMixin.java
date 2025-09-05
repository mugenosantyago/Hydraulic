package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This mixin forces immediate respawn for Bedrock players who die,
 * ensuring they don't get stuck in a death state on the loading screen.
 * 
 * The issue is that Bedrock players die from spawn position issues but
 * the respawn mechanism doesn't work properly, leaving them stuck.
 */
@Mixin(value = PlayerList.class, priority = 5000)
public class BedrockRespawnForceMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockRespawnForceMixin");
    
    /**
     * Intercepts the respawn method to ensure Bedrock players respawn immediately
     * and at safe positions.
     */
    @Inject(
        method = "respawn",
        at = @At("HEAD")
    )
    private void forceBedrockRespawn(ServerPlayer player, boolean keepEverything, Object reason, CallbackInfoReturnable<ServerPlayer> cir) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("BedrockRespawnForceMixin: Forcing respawn for Bedrock player: {} (reason: {})", 
                    playerName, reason);
                
                // Ensure the player is marked as dead so respawn can proceed
                if (!player.isDeadOrDying()) {
                    LOGGER.warn("BedrockRespawnForceMixin: Player {} not marked as dead, forcing death state", playerName);
                    player.setHealth(0.0f);
                }
            }
        } catch (Exception e) {
            LOGGER.error("BedrockRespawnForceMixin: Exception in respawn interception: {}", e.getMessage());
        }
    }
    
    /**
     * After respawn, ensure the player is placed at a safe position.
     */
    @Inject(
        method = "respawn",
        at = @At("RETURN")
    )
    private void ensureSafeRespawnPosition(ServerPlayer player, boolean keepEverything, Object reason, CallbackInfoReturnable<ServerPlayer> cir) {
        try {
            ServerPlayer respawnedPlayer = cir.getReturnValue();
            if (respawnedPlayer != null && BedrockDetectionHelper.isFloodgatePlayer(respawnedPlayer.getGameProfile().getName())) {
                String playerName = respawnedPlayer.getGameProfile().getName();
                LOGGER.info("BedrockRespawnForceMixin: Post-respawn processing for Bedrock player: {}", playerName);
                
                // Schedule safe positioning after respawn
                var server = respawnedPlayer.getServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            // Small delay to let respawn complete
                            Thread.sleep(100);
                            
                            ensurePlayerAtSafePosition(respawnedPlayer);
                            
                        } catch (Exception e) {
                            LOGGER.error("BedrockRespawnForceMixin: Exception in post-respawn processing: {}", e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.error("BedrockRespawnForceMixin: Exception in respawn return processing: {}", e.getMessage());
        }
    }
    
    /**
     * Ensures the respawned player is at a safe position with full health.
     */
    private void ensurePlayerAtSafePosition(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            ServerLevel level = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
            
            if (level == null) {
                LOGGER.warn("BedrockRespawnForceMixin: No valid level for respawned player: {}", playerName);
                return;
            }
            
            // Get world spawn position
            BlockPos worldSpawn = level.getSharedSpawnPos();
            double safeX = worldSpawn.getX() + 0.5;
            double safeZ = worldSpawn.getZ() + 0.5;
            double safeY = findSafeRespawnY(level, safeX, safeZ);
            
            LOGGER.info("BedrockRespawnForceMixin: Moving respawned player {} to safe position ({}, {}, {})", 
                playerName, safeX, safeY, safeZ);
            
            // Teleport to safe position
            player.teleportTo(safeX, safeY, safeZ);
            
            // Ensure full health
            player.setHealth(player.getMaxHealth());
            
            // Clear any death-related effects
            player.clearFire();
            player.setAirSupply(player.getMaxAirSupply());
            
            // Send position update to client
            if (player.connection != null) {
                player.connection.teleport(safeX, safeY, safeZ, 0.0F, 0.0F);
                
                // Send health update
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                    player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
                
                // Force connection flush
                player.connection.getConnection().flushChannel();
            }
            
            LOGGER.info("BedrockRespawnForceMixin: Successfully positioned respawned Bedrock player: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockRespawnForceMixin: Exception ensuring safe respawn position: {}", e.getMessage());
        }
    }
    
    /**
     * Finds a safe Y coordinate for respawning.
     */
    private double findSafeRespawnY(ServerLevel level, double x, double z) {
        BlockPos pos = new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z));
        
        // Start from a reasonable height and work down
        int minY = -64; // Default void level
        int maxY = 320; // Default build height
        try {
            minY = level.dimensionType().minY();
            maxY = level.dimensionType().minY() + level.dimensionType().height();
        } catch (Exception e) {
            LOGGER.debug("BedrockRespawnForceMixin: Could not get dimension bounds, using defaults");
        }
        
        for (int y = Math.min(maxY - 10, 120); y >= minY + 2; y--) {
            BlockPos checkPos = pos.atY(y);
            BlockPos belowPos = checkPos.below();
            BlockPos abovePos = checkPos.above();
            
            if (level.getBlockState(belowPos).canOcclude() && 
                level.getBlockState(checkPos).isAir() &&
                level.getBlockState(abovePos).isAir()) {
                return y + 0.1;
            }
        }
        
        // Fallback to sea level + 10
        return level.getSeaLevel() + 10;
    }
}
