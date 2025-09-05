package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin handles death and respawn for Bedrock players to prevent them from
 * getting stuck in a dead state that causes loading screen issues.
 * 
 * The core issue is that Bedrock players who die immediately upon spawn get stuck
 * in a death state that doesn't properly trigger the respawn cycle, leaving them
 * on the loading screen while actually being dead server-side.
 */
@Mixin(value = ServerPlayer.class, priority = 1500)
public class BedrockDeathRespawnMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockDeathRespawnMixin");
    
    /**
     * Intercepts player death to handle Bedrock players specially.
     */
    @Inject(
        method = "die",
        at = @At("HEAD")
    )
    private void handleBedrockPlayerDeath(net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfo ci) {
        try {
            ServerPlayer player = (ServerPlayer) (Object) this;
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.warn("BedrockDeathRespawnMixin: Bedrock player {} is dying - damage source: {}", 
                    playerName, damageSource.getMsgId());
                
                // Check if this is a void/fall damage death (common with spawn issues)
                String damageType = damageSource.getMsgId();
                if (damageType.contains("fall") || damageType.contains("void") || damageType.contains("generic")) {
                    LOGGER.warn("BedrockDeathRespawnMixin: Bedrock player {} died from spawn-related damage: {}", 
                        playerName, damageType);
                    
                    // Schedule immediate respawn handling
                    var server = player.getServer();
                    if (server != null) {
                        server.execute(() -> {
                            try {
                                // Small delay to let death processing complete
                                Thread.sleep(100);
                                handleBedrockRespawn(player);
                            } catch (Exception e) {
                                LOGGER.debug("BedrockDeathRespawnMixin: Exception in death handling: {}", e.getMessage());
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockDeathRespawnMixin: Exception in death interception: {}", e.getMessage());
        }
    }
    
    /**
     * Intercepts respawn to ensure Bedrock players respawn at safe positions.
     */
    @Inject(
        method = "restoreFrom",
        at = @At("TAIL")
    )
    private void handleBedrockRespawn(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
        try {
            ServerPlayer newPlayer = (ServerPlayer) (Object) this;
            if (newPlayer != null && BedrockDetectionHelper.isFloodgatePlayer(newPlayer.getGameProfile().getName())) {
                String playerName = newPlayer.getGameProfile().getName();
                LOGGER.info("BedrockDeathRespawnMixin: Handling respawn for Bedrock player: {}", playerName);
                
                // Ensure the player respawns at a safe position
                ensureSafeRespawnPosition(newPlayer);
                
                // Send additional packets to ensure proper client state
                sendRespawnCompletionPackets(newPlayer);
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockDeathRespawnMixin: Exception in respawn handling: {}", e.getMessage());
        }
    }
    
    /**
     * Handles immediate respawn for Bedrock players who die from spawn issues.
     */
    private void handleBedrockRespawn(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.isDeadOrDying()) {
                LOGGER.info("BedrockDeathRespawnMixin: Force respawning Bedrock player: {}", playerName);
                
                // Get a safe respawn position
                ServerLevel respawnLevel = player.getServer().overworld(); // Default to overworld for now
                
                BlockPos respawnPos = respawnLevel.getSharedSpawnPos(); // Use world spawn as default
                // respawnPos is now guaranteed to be non-null
                
                // Find a safe Y position
                double safeY = findSafeRespawnY(respawnLevel, respawnPos.getX() + 0.5, respawnPos.getZ() + 0.5);
                
                LOGGER.info("BedrockDeathRespawnMixin: Respawning {} at safe position ({}, {}, {})", 
                    playerName, respawnPos.getX() + 0.5, safeY, respawnPos.getZ() + 0.5);
                
                // Force respawn by creating a new player instance
                try {
                    // Use the server's respawn mechanism
                    var server = player.getServer();
                    if (server == null) {
                        LOGGER.warn("BedrockDeathRespawnMixin: Server is null for player {}", playerName);
                        return;
                    }
                    var playerList = server.getPlayerList();
                    
                    // Trigger respawn - use reflection to avoid enum issues
                    ServerPlayer respawnedPlayer = null;
                    try {
                        // Try to get the RespawnReason enum
                        Class<?> respawnReasonClass = Class.forName("net.minecraft.world.entity.player.Player$RespawnReason");
                        Object deathReason = respawnReasonClass.getField("DEATH").get(null);
                        
                        java.lang.reflect.Method respawnMethod = playerList.getClass().getDeclaredMethod("respawn", 
                            ServerPlayer.class, boolean.class, respawnReasonClass);
                        respawnedPlayer = (ServerPlayer) respawnMethod.invoke(playerList, player, false, deathReason);
                    } catch (Exception respawnReflectionException) {
                        LOGGER.debug("BedrockDeathRespawnMixin: Could not use respawn method with reason: {}", 
                            respawnReflectionException.getMessage());
                        
                        // Fallback: try without the reason parameter
                        try {
                            java.lang.reflect.Method respawnMethod = playerList.getClass().getDeclaredMethod("respawn", 
                                ServerPlayer.class, boolean.class);
                            respawnedPlayer = (ServerPlayer) respawnMethod.invoke(playerList, player, false);
                        } catch (Exception fallbackException) {
                            LOGGER.debug("BedrockDeathRespawnMixin: Fallback respawn also failed: {}", 
                                fallbackException.getMessage());
                        }
                    }
                    
                    if (respawnedPlayer != null) {
                        // Move to safe position
                        respawnedPlayer.teleportTo(respawnPos.getX() + 0.5, safeY, respawnPos.getZ() + 0.5);
                        
                        // Send completion packets
                        sendRespawnCompletionPackets(respawnedPlayer);
                        
                        LOGGER.info("BedrockDeathRespawnMixin: Successfully respawned Bedrock player: {}", playerName);
                    }
                } catch (Exception respawnException) {
                    LOGGER.error("BedrockDeathRespawnMixin: Failed to force respawn for {}: {}", 
                        playerName, respawnException.getMessage());
                    
                    // Fallback: try to heal and teleport the existing player
                    player.setHealth(player.getMaxHealth());
                    player.teleportTo(respawnPos.getX() + 0.5, safeY, respawnPos.getZ() + 0.5);
                    
                    if (player.connection != null) {
                        player.connection.teleport(respawnPos.getX() + 0.5, safeY, respawnPos.getZ() + 0.5, 0.0F, 0.0F);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("BedrockDeathRespawnMixin: Exception in force respawn for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Ensures the player respawns at a safe position.
     */
    private void ensureSafeRespawnPosition(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            ServerLevel level = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
            if (level == null) return;
            
            // Check if current position is safe
            if (isUnsafeRespawnPosition(player)) {
                LOGGER.warn("BedrockDeathRespawnMixin: Player {} respawned in unsafe position, correcting", playerName);
                
                // Get the world spawn
                BlockPos worldSpawn = level.getSharedSpawnPos();
                double safeX = worldSpawn.getX() + 0.5;
                double safeZ = worldSpawn.getZ() + 0.5;
                double safeY = findSafeRespawnY(level, safeX, safeZ);
                
                // Teleport to safety
                player.teleportTo(safeX, safeY, safeZ);
                
                if (player.connection != null) {
                    player.connection.teleport(safeX, safeY, safeZ, 0.0F, 0.0F);
                }
                
                LOGGER.info("BedrockDeathRespawnMixin: Corrected respawn position for {} to ({}, {}, {})", 
                    playerName, safeX, safeY, safeZ);
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockDeathRespawnMixin: Exception ensuring safe respawn: {}", e.getMessage());
        }
    }
    
    /**
     * Finds a safe Y coordinate for respawning.
     */
    private double findSafeRespawnY(ServerLevel level, double x, double z) {
        BlockPos pos = new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z));
        
        // Start from a reasonable height and work down
        int minY = -64; // Default void level for most worlds
        int maxY = 320; // Default build height for most worlds
        try {
            minY = level.dimensionType().minY();
            maxY = level.dimensionType().minY() + level.dimensionType().height();
        } catch (Exception e) {
            LOGGER.debug("BedrockDeathRespawnMixin: Could not get dimension bounds, using defaults");
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
    
    /**
     * Checks if the respawn position is unsafe.
     */
    private boolean isUnsafeRespawnPosition(ServerPlayer player) {
        try {
            double y = player.getY();
            ServerLevel level = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
            if (level == null) return false;
            
            int minY = -64; // Default void level
            try {
                minY = level.dimensionType().minY();
            } catch (Exception e) {
                // Use default
            }
            return y < minY + 5 || 
                   level.getBlockState(player.blockPosition()).is(net.minecraft.world.level.block.Blocks.LAVA);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Sends packets to ensure proper respawn completion for Bedrock players.
     */
    private void sendRespawnCompletionPackets(ServerPlayer player) {
        try {
            if (player.connection != null) {
                String playerName = player.getGameProfile().getName();
                LOGGER.debug("BedrockDeathRespawnMixin: Sending respawn completion packets for: {}", playerName);
                
                // Send player abilities
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                
                // Send position confirmation
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                
                // Send game mode
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                    player.gameMode.getGameModeForPlayer().getId()));
                
                // Force flush
                player.connection.getConnection().flushChannel();
                
                LOGGER.debug("BedrockDeathRespawnMixin: Sent respawn completion packets for: {}", playerName);
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockDeathRespawnMixin: Exception sending respawn packets: {}", e.getMessage());
        }
    }
}
