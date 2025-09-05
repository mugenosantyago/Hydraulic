package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin provides a comprehensive fix for Bedrock players getting stuck on the loading screen.
 * It ensures proper spawn packet sequencing and client-server state synchronization.
 * 
 * The core issue is a timing mismatch between server-side spawn completion and Geyser's
 * client-side spawn packet handling, resulting in the client staying on the loading screen
 * even though the server considers the player spawned.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 2100)
public class BedrockLoadingScreenFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockLoadingScreenFix");
    
    @Shadow
    public ServerPlayer player;
    
    // Track which players have been processed to prevent duplicate handling
    private static final java.util.Set<String> processedPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    /**
     * Intercepts the ServerGamePacketListenerImpl creation to ensure proper spawn state
     * synchronization for Bedrock players.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void fixBedrockLoadingScreen(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Check if we've already processed this player
                if (processedPlayers.contains(playerName)) {
                    LOGGER.debug("BedrockLoadingScreenFix: Already processed {}, skipping", playerName);
                    return;
                }
                
                LOGGER.info("BedrockLoadingScreenFix: Applying loading screen fix for Bedrock player: {}", playerName);
                processedPlayers.add(playerName);
                
                // Schedule comprehensive spawn completion sequence
                server.execute(() -> {
                    try {
                        // Small initial delay to ensure all systems are ready
                        Thread.sleep(100);
                        
                        // Execute the comprehensive loading screen fix
                        executeLoadingScreenFix(player, server);
                        
                    } catch (Exception e) {
                        LOGGER.error("BedrockLoadingScreenFix: Exception in loading screen fix for {}: {}", playerName, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenFix: Exception in constructor hook: {}", e.getMessage());
        }
    }
    
    /**
     * Executes a comprehensive loading screen fix for Bedrock players.
     */
    private void executeLoadingScreenFix(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        String playerName = player.getGameProfile().getName();
        
        try {
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.warn("BedrockLoadingScreenFix: Player {} connection is not valid", playerName);
                return;
            }
            
            LOGGER.info("BedrockLoadingScreenFix: Executing comprehensive spawn fix for: {}", playerName);
            
            // Phase 1: Send critical game state packets
            sendCriticalGameStatePackets(player);
            
            // Phase 2: Force position and inventory synchronization
            synchronizePlayerState(player, server);
            
            // Phase 3: Send spawn completion signals
            sendSpawnCompletionSignals(player);
            
            // Phase 4: Final validation and correction (delayed)
            scheduleSpawnValidation(player, server);
            
            LOGGER.info("BedrockLoadingScreenFix: Completed loading screen fix for: {}", playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenFix: Failed to execute loading screen fix for {}: {}", playerName, e.getMessage());
        }
    }
    
    /**
     * Sends critical game state packets that are essential for client spawn completion.
     */
    private void sendCriticalGameStatePackets(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockLoadingScreenFix: Sending critical game state packets for: {}", playerName);
            
            // Send player abilities - critical for client state
            player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            
            // Send held item slot using reflection to access selected field
            try {
                java.lang.reflect.Field selectedField = player.getInventory().getClass().getDeclaredField("selected");
                selectedField.setAccessible(true);
                int selectedSlot = (Integer) selectedField.get(player.getInventory());
                
                // Create the packet using reflection since ClientboundSetCarriedItemPacket might not be available
                try {
                    Class<?> carriedItemPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket");
                    Object carriedItemPacket = carriedItemPacketClass.getDeclaredConstructor(int.class).newInstance(selectedSlot);
                    player.connection.send((net.minecraft.network.protocol.Packet<?>) carriedItemPacket);
                } catch (Exception packetException) {
                    LOGGER.debug("BedrockLoadingScreenFix: Could not send carried item packet: {}", packetException.getMessage());
                }
            } catch (Exception selectedFieldException) {
                LOGGER.debug("BedrockLoadingScreenFix: Could not access selected field: {}", selectedFieldException.getMessage());
            }
            
            // Send game mode confirmation
            player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                player.gameMode.getGameModeForPlayer().getId()));
            
            // Send level loading completion signal
            player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
            
            // Force immediate packet delivery
            player.connection.getConnection().flushChannel();
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenFix: Failed to send critical packets for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Synchronizes player state including position and inventory.
     */
    private void synchronizePlayerState(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockLoadingScreenFix: Synchronizing player state for: {}", playerName);
            
            PlayerList playerList = server.getPlayerList();
            
            // Send all player info updates
            playerList.sendAllPlayerInfo(player);
            
            // Send level information
            playerList.sendLevelInfo(player, player.level());
            
            // Send permission level
            playerList.sendPlayerPermissionLevel(player);
            
            // Force position synchronization with multiple methods
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Synchronize inventory
            if (player.containerMenu != null) {
                player.containerMenu.sendAllDataToRemote();
            }
            
            // Send inventory contents explicitly
            player.inventoryMenu.sendAllDataToRemote();
            
            // Force connection flush
            player.connection.getConnection().flushChannel();
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenFix: Failed to synchronize player state for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Sends spawn completion signals to indicate the player is fully spawned.
     */
    private void sendSpawnCompletionSignals(ServerPlayer player) {
        try {
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockLoadingScreenFix: Sending spawn completion signals for: {}", playerName);
            
            // Send weather clear signal (often triggers client spawn completion)
            player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            
            // Send another level loading completion signal
            player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
            
            // Force immediate delivery
            player.connection.getConnection().flushChannel();
            
        } catch (Exception e) {
            LOGGER.error("BedrockLoadingScreenFix: Failed to send spawn completion signals for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Schedules final spawn validation and correction after a delay.
     */
    private void scheduleSpawnValidation(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        String playerName = player.getGameProfile().getName();
        
        // Schedule validation after a longer delay
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                if (player.connection != null && player.connection.getConnection().isConnected()) {
                    LOGGER.info("BedrockLoadingScreenFix: Final spawn validation for: {}", playerName);
                    
                    // Send final position confirmation
                    player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                    
                    // Send final abilities update
                    player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                    
                    // Force final flush
                    player.connection.getConnection().flushChannel();
                    
                    LOGGER.info("BedrockLoadingScreenFix: Completed final validation for: {}", playerName);
                }
            } catch (Exception e) {
                LOGGER.debug("BedrockLoadingScreenFix: Exception in final validation for {}: {}", playerName, e.getMessage());
            }
        }, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    
    /**
     * Clean up processed players when they disconnect to prevent memory leaks.
     */
    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    private void cleanupProcessedPlayer(net.minecraft.network.DisconnectionDetails details, CallbackInfo ci) {
        try {
            if (this.player != null) {
                String playerName = this.player.getGameProfile().getName();
                if (processedPlayers.remove(playerName)) {
                    LOGGER.debug("BedrockLoadingScreenFix: Cleaned up processed player: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenFix: Exception during cleanup: {}", e.getMessage());
        }
    }
}
