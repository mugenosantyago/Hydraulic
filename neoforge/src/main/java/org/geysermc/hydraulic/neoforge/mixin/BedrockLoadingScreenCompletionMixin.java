package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin forces the loading screen to complete for Bedrock players
 * even if they're in a problematic state (like being dead).
 * 
 * The core issue is that Bedrock players can get stuck on the loading screen
 * when they die immediately after spawn, because the client never receives
 * the proper "loading complete" signal.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 6000)
public class BedrockLoadingScreenCompletionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockLoadingScreenCompletionMixin");
    
    @Shadow
    public ServerPlayer player;
    
    // Track which players have had their loading screen forced
    private static final java.util.Set<String> loadingScreenCompleted = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    /**
     * Force loading screen completion immediately after the connection is established.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void forceLoadingScreenCompletion(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                if (loadingScreenCompleted.contains(playerName)) {
                    LOGGER.debug("BedrockLoadingScreenCompletionMixin: Loading screen already completed for: {}", playerName);
                    return;
                }
                
                LOGGER.info("BedrockLoadingScreenCompletionMixin: Force completing loading screen for Bedrock player: {}", playerName);
                loadingScreenCompleted.add(playerName);
                
                // Schedule multiple attempts to complete the loading screen
                server.execute(() -> {
                    try {
                        // Multiple attempts with increasing delays
                        for (int attempt = 1; attempt <= 5; attempt++) {
                            final int currentAttempt = attempt;
                            
                            java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                                try {
                                    forceLoadingScreenComplete(player, server, currentAttempt);
                                    
                                    // On the last attempt, try a more aggressive approach
                                    if (currentAttempt == 5) {
                                        forceUltimateLoadingScreenCompletion(player);
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("BedrockLoadingScreenCompletionMixin: Exception in attempt {} for {}: {}", 
                                        currentAttempt, playerName, e.getMessage());
                                }
                            }, attempt * 300L, java.util.concurrent.TimeUnit.MILLISECONDS);
                        }
                    } catch (Exception e) {
                        LOGGER.error("BedrockLoadingScreenCompletionMixin: Exception scheduling completion for {}: {}", 
                            playerName, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenCompletionMixin: Exception in constructor: {}", e.getMessage());
        }
    }
    
    /**
     * Forces the loading screen to complete regardless of player state.
     */
    private void forceLoadingScreenComplete(ServerPlayer player, net.minecraft.server.MinecraftServer server, int attempt) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.debug("BedrockLoadingScreenCompletionMixin: Player {} no longer connected (attempt {})", playerName, attempt);
                return;
            }
            
            LOGGER.info("BedrockLoadingScreenCompletionMixin: Attempt {} - Force completing loading screen for: {}", 
                attempt, playerName);
            
            // If player is dead, revive them first
            if (player.isDeadOrDying()) {
                LOGGER.warn("BedrockLoadingScreenCompletionMixin: Player {} is dead, reviving for loading screen completion", playerName);
                revivePlayerForLoadingScreen(player);
            }
            
            // Ensure player is at a safe position
            ensurePlayerAtSafePosition(player);
            
            // Send comprehensive loading completion packets
            sendLoadingCompletionPackets(player);
            
            // Force Geyser to complete the loading screen
            forceGeyserLoadingCompletion(player);
            
            LOGGER.info("BedrockLoadingScreenCompletionMixin: Completed loading screen force attempt {} for: {}", 
                attempt, playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenCompletionMixin: Exception in loading screen completion attempt {} for {}: {}", 
                attempt, player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Revives a dead player specifically for loading screen completion.
     */
    private void revivePlayerForLoadingScreen(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // Set full health
            player.setHealth(player.getMaxHealth());
            
            // Clear death-related effects
            player.clearFire();
            player.setAirSupply(player.getMaxAirSupply());
            
            // Send health update
            if (player.connection != null) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                    player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
            }
            
            LOGGER.info("BedrockLoadingScreenCompletionMixin: Revived player {} for loading screen completion", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenCompletionMixin: Exception reviving player: {}", e.getMessage());
        }
    }
    
    /**
     * Ensures the player is at a safe position.
     */
    private void ensurePlayerAtSafePosition(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            ServerLevel level = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
            
            if (level == null) return;
            
            // Check if current position is unsafe (void, etc.)
            double currentY = player.getY();
            int minY = -64;
            try {
                minY = level.dimensionType().minY();
            } catch (Exception e) {
                // Use default
            }
            
            if (currentY < minY + 5) {
                LOGGER.warn("BedrockLoadingScreenCompletionMixin: Player {} in unsafe position (Y={}), moving to safety", 
                    playerName, currentY);
                
                // Move to world spawn
                BlockPos worldSpawn = level.getSharedSpawnPos();
                double safeX = worldSpawn.getX() + 0.5;
                double safeZ = worldSpawn.getZ() + 0.5;
                double safeY = worldSpawn.getY() + 1.0; // Just above spawn point
                
                player.teleportTo(safeX, safeY, safeZ);
                
                if (player.connection != null) {
                    player.connection.teleport(safeX, safeY, safeZ, 0.0F, 0.0F);
                }
                
                LOGGER.info("BedrockLoadingScreenCompletionMixin: Moved player {} to safe position ({}, {}, {})", 
                    playerName, safeX, safeY, safeZ);
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenCompletionMixin: Exception ensuring safe position: {}", e.getMessage());
        }
    }
    
    /**
     * Sends packets to force loading screen completion.
     */
    private void sendLoadingCompletionPackets(ServerPlayer player) {
        try {
            if (player.connection == null) return;
            
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockLoadingScreenCompletionMixin: Sending loading completion packets for: {}", playerName);
            
            // Send player abilities
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            
            // Send position
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Send game mode
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                player.gameMode.getGameModeForPlayer().getId()));
            
            // Send time (often triggers loading completion)
            long worldTime = player.level().getGameTime();
            long dayTime = player.level().getDayTime();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(worldTime, dayTime, true));
            
            // Send weather clear (another loading completion trigger)
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            
            // Force immediate delivery
            player.connection.getConnection().flushChannel();
            
            LOGGER.debug("BedrockLoadingScreenCompletionMixin: Sent loading completion packets for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenCompletionMixin: Exception sending completion packets: {}", e.getMessage());
        }
    }
    
    /**
     * Forces Geyser to complete the loading screen on its end.
     */
    private void forceGeyserLoadingCompletion(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            
            // Try to access Geyser session and force completion
            try {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                
                if (geyserApi != null) {
                    Object connection = geyserApiClass.getMethod("connectionByUuid", java.util.UUID.class)
                        .invoke(geyserApi, player.getUUID());
                    
                    if (connection != null) {
                        LOGGER.info("BedrockLoadingScreenCompletionMixin: Found Geyser connection for {}, forcing loading completion", playerName);
                        
                        // Force various spawn/loading related flags to true
                        setGeyserSpawnFlags(connection, playerName);
                    }
                }
            } catch (Exception geyserException) {
                LOGGER.debug("BedrockLoadingScreenCompletionMixin: Could not access Geyser for {}: {}", 
                    playerName, geyserException.getMessage());
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenCompletionMixin: Exception in Geyser loading completion: {}", e.getMessage());
        }
    }
    
    /**
     * Sets Geyser spawn flags to force loading completion.
     */
    private void setGeyserSpawnFlags(Object geyserConnection, String playerName) {
        try {
            // List of flags that might control loading screen state
            String[] flagNames = {
                "sentSpawnPacket", "loggedIn", "spawned", "loggingIn"
            };
            
            for (String flagName : flagNames) {
                try {
                    java.lang.reflect.Field flag = geyserConnection.getClass().getDeclaredField(flagName);
                    flag.setAccessible(true);
                    
                    // Set spawn-related flags to true, loggingIn to false
                    boolean targetValue = !flagName.equals("loggingIn");
                    flag.set(geyserConnection, targetValue);
                    
                    LOGGER.debug("BedrockLoadingScreenCompletionMixin: Set {} to {} for {}", 
                        flagName, targetValue, playerName);
                } catch (Exception flagException) {
                    // Ignore individual flag failures
                }
            }
            
            LOGGER.info("BedrockLoadingScreenCompletionMixin: Forced Geyser loading completion flags for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenCompletionMixin: Exception setting Geyser flags: {}", e.getMessage());
        }
    }
    
    /**
     * Ultimate loading screen completion attempt - forces everything.
     */
    private void forceUltimateLoadingScreenCompletion(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.warn("BedrockLoadingScreenCompletionMixin: ULTIMATE LOADING SCREEN COMPLETION for: {}", playerName);
            
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.warn("BedrockLoadingScreenCompletionMixin: Player {} no longer connected for ultimate fix", playerName);
                return;
            }
            
            // If player is dead, force them to be alive
            if (player.isDeadOrDying()) {
                LOGGER.warn("BedrockLoadingScreenCompletionMixin: FORCING PLAYER {} TO BE ALIVE", playerName);
                
                // Set health to full
                player.setHealth(player.getMaxHealth());
                
                // Clear any death-related effects
                player.clearFire();
                player.setAirSupply(player.getMaxAirSupply());
                
                // Force position to world spawn
                ServerLevel level = player.level() instanceof ServerLevel ? (ServerLevel) player.level() : null;
                if (level != null) {
                    BlockPos worldSpawn = level.getSharedSpawnPos();
                    double spawnX = worldSpawn.getX() + 0.5;
                    double spawnY = worldSpawn.getY() + 2.0; // Well above ground
                    double spawnZ = worldSpawn.getZ() + 0.5;
                    
                    player.teleportTo(spawnX, spawnY, spawnZ);
                    
                    LOGGER.warn("BedrockLoadingScreenCompletionMixin: TELEPORTED {} TO SAFE SPAWN ({}, {}, {})", 
                        playerName, spawnX, spawnY, spawnZ);
                }
            }
            
            // Send comprehensive state reset packets
            if (player.connection != null) {
                // Health packet
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                    player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
                
                // Position packet
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
                
                // Abilities packet
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                
                // Game mode packet
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                    player.gameMode.getGameModeForPlayer().getId()));
                
                // Multiple loading completion signals
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
                
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0.0F));
                
                // Time update
                long worldTime = player.level().getGameTime();
                long dayTime = player.level().getDayTime();
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(worldTime, dayTime, true));
                
                // Force multiple flushes
                for (int i = 0; i < 3; i++) {
                    player.connection.getConnection().flushChannel();
                }
                
                LOGGER.warn("BedrockLoadingScreenCompletionMixin: SENT ULTIMATE COMPLETION PACKETS for: {}", playerName);
            }
            
            // Force Geyser flags one more time
            forceGeyserLoadingCompletion(player);
            
            LOGGER.warn("BedrockLoadingScreenCompletionMixin: ULTIMATE COMPLETION ATTEMPT FINISHED for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenCompletionMixin: Exception in ultimate completion: {}", e.getMessage());
        }
    }
    
    /**
     * Clean up when players disconnect.
     */
    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    private void cleanupLoadingScreenCompleted(net.minecraft.network.DisconnectionDetails details, CallbackInfo ci) {
        try {
            if (this.player != null) {
                String playerName = this.player.getGameProfile().getName();
                if (loadingScreenCompleted.remove(playerName)) {
                    LOGGER.debug("BedrockLoadingScreenCompletionMixin: Cleaned up loading screen tracking for: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenCompletionMixin: Exception during cleanup: {}", e.getMessage());
        }
    }
}
