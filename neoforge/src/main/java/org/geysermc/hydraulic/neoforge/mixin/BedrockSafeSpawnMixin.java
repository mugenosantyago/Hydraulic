package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures Bedrock players spawn at a safe position to prevent them
 * from falling into the void or spawning in unsafe locations.
 * 
 * This addresses the core issue where players can see the world but are stuck
 * on the loading screen because they're actually falling/dying immediately.
 */
@Mixin(value = PlayerList.class, priority = 2000)
public class BedrockSafeSpawnMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockSafeSpawnMixin");
    
    /**
     * Intercepts player placement to ensure Bedrock players spawn at safe positions.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("HEAD")
    )
    private void ensureSafeSpawnPosition(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("BedrockSafeSpawnMixin: Ensuring safe spawn position for Bedrock player: {}", playerName);
                
                // Get the spawn level
                ServerLevel spawnLevel = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
                if (spawnLevel == null) {
                    spawnLevel = player.getServer().overworld();
                }
                
                // Get the world spawn position
                BlockPos worldSpawn = spawnLevel.getSharedSpawnPos();
                double spawnX = worldSpawn.getX() + 0.5;
                double spawnZ = worldSpawn.getZ() + 0.5;
                
                // Find a safe spawn Y position
                double safeY = findSafeSpawnY(spawnLevel, spawnX, spawnZ);
                
                LOGGER.info("BedrockSafeSpawnMixin: Setting spawn position for {} to ({}, {}, {})", 
                    playerName, spawnX, safeY, spawnZ);
                
                // Set the player's position to the safe spawn location
                player.setPos(spawnX, safeY, spawnZ);
                player.setYRot(0.0F);
                player.setXRot(0.0F);
                
                // Skip setting respawn position for now due to API differences
                
                LOGGER.info("BedrockSafeSpawnMixin: Successfully set safe spawn position for: {}", playerName);
            }
        } catch (Exception e) {
            LOGGER.error("BedrockSafeSpawnMixin: Exception ensuring safe spawn for {}: {}", 
                player != null ? player.getGameProfile().getName() : "unknown", e.getMessage(), e);
        }
    }
    
    /**
     * Additional check after placement to validate the spawn position.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void validateSpawnPosition(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Schedule a delayed validation to ensure the position is still safe
                var server = player.getServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            // Small delay to let other systems process
                            Thread.sleep(100);
                            
                            // Check if the player is in a safe position
                            if (isUnsafePosition(player)) {
                                LOGGER.warn("BedrockSafeSpawnMixin: Player {} is in unsafe position, correcting", playerName);
                                correctUnsafeSpawn(player);
                            } else {
                                LOGGER.info("BedrockSafeSpawnMixin: Player {} is in safe position: ({}, {}, {})", 
                                    playerName, player.getX(), player.getY(), player.getZ());
                            }
                        } catch (Exception e) {
                            LOGGER.debug("BedrockSafeSpawnMixin: Exception in spawn validation: {}", e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockSafeSpawnMixin: Exception in spawn validation setup: {}", e.getMessage());
        }
    }
    
    /**
     * Finds a safe Y coordinate for spawning at the given X and Z coordinates.
     */
    private double findSafeSpawnY(ServerLevel level, double x, double z) {
        BlockPos pos = new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z));
        
        // Start from a reasonable height and work our way down
        int minY = -64; // Default void level for most worlds
        int maxY = 320; // Default build height for most worlds
        try {
            minY = level.dimensionType().minY();
            maxY = level.dimensionType().minY() + level.dimensionType().height();
        } catch (Exception e) {
            LOGGER.debug("BedrockSafeSpawnMixin: Could not get dimension bounds, using defaults");
        }
        
        for (int y = Math.min(maxY - 10, 120); y >= minY + 2; y--) {
            BlockPos checkPos = pos.atY(y);
            BlockPos belowPos = checkPos.below();
            BlockPos abovePos = checkPos.above();
            
            // Check if this is a safe spawn position:
            // 1. Block below is solid (not air, water, lava)
            // 2. Current position is air or passable
            // 3. Position above is air or passable
            if (level.getBlockState(belowPos).canOcclude() && 
                !level.getBlockState(belowPos).is(Blocks.LAVA) &&
                (level.getBlockState(checkPos).isAir() || level.getBlockState(checkPos).canBeReplaced()) &&
                (level.getBlockState(abovePos).isAir() || level.getBlockState(abovePos).canBeReplaced())) {
                
                LOGGER.debug("BedrockSafeSpawnMixin: Found safe spawn Y at {} (solid block: {})", 
                    y, level.getBlockState(belowPos).getBlock());
                return y + 0.1; // Slightly above the ground
            }
        }
        
        // If we can't find a safe position, use the world's sea level + 10
        int fallbackY = level.getSeaLevel() + 10;
        LOGGER.warn("BedrockSafeSpawnMixin: Could not find safe spawn, using fallback Y: {}", fallbackY);
        return fallbackY;
    }
    
    /**
     * Checks if the player is in an unsafe position (void, lava, etc.).
     */
    private boolean isUnsafePosition(ServerPlayer player) {
        try {
            double y = player.getY();
            ServerLevel level = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
            if (level == null) return false;
            
            // Check if player is below minimum build height (in the void)
            int minY = -64; // Default void level
            try {
                minY = level.dimensionType().minY();
            } catch (Exception e) {
                // Use default
            }
            if (y < minY) {
                LOGGER.debug("BedrockSafeSpawnMixin: Player {} is below minimum build height: {}", 
                    player.getGameProfile().getName(), y);
                return true;
            }
            
            // Check if player is in lava
            BlockPos playerPos = player.blockPosition();
            if (level.getBlockState(playerPos).is(Blocks.LAVA) || 
                level.getBlockState(playerPos.below()).is(Blocks.LAVA)) {
                LOGGER.debug("BedrockSafeSpawnMixin: Player {} is in lava", player.getGameProfile().getName());
                return true;
            }
            
            // Check if player is falling rapidly (might indicate void fall)
            if (player.getDeltaMovement().y < -2.0) {
                LOGGER.debug("BedrockSafeSpawnMixin: Player {} is falling rapidly: {}", 
                    player.getGameProfile().getName(), player.getDeltaMovement().y);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            LOGGER.debug("BedrockSafeSpawnMixin: Exception checking unsafe position: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Corrects an unsafe spawn by teleporting the player to a safe location.
     */
    private void correctUnsafeSpawn(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            ServerLevel level = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
            if (level == null) return;
            
            // Get the world spawn and find a safe position there
            BlockPos worldSpawn = level.getSharedSpawnPos();
            double safeX = worldSpawn.getX() + 0.5;
            double safeZ = worldSpawn.getZ() + 0.5;
            double safeY = findSafeSpawnY(level, safeX, safeZ);
            
            LOGGER.info("BedrockSafeSpawnMixin: Correcting unsafe spawn for {} to ({}, {}, {})", 
                playerName, safeX, safeY, safeZ);
            
            // Teleport the player to safety
            player.teleportTo(safeX, safeY, safeZ);
            
            // Send the teleport packet to the client
            if (player.connection != null) {
                player.connection.teleport(safeX, safeY, safeZ, player.getYRot(), player.getXRot());
                player.connection.getConnection().flushChannel();
            }
            
            LOGGER.info("BedrockSafeSpawnMixin: Successfully corrected spawn position for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockSafeSpawnMixin: Failed to correct unsafe spawn for {}: {}", 
                player.getGameProfile().getName(), e.getMessage(), e);
        }
    }
}
