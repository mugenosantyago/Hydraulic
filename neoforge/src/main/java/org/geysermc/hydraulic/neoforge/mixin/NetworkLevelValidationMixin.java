package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin intercepts network-level validation and prevents all forms of
 * "invalid move player packet received" errors for Bedrock players.
 */
@Mixin(value = {ServerGamePacketListenerImpl.class, Connection.class}, priority = 2000)
public class NetworkLevelValidationMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("NetworkLevelValidationMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Intercepts packet handling at the network level to prevent validation errors.
     */
    @Inject(
        method = "handlePacket",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void interceptPacketHandling(Packet<?> packet, CallbackInfo ci) {
        try {
            if (packet instanceof ServerboundMovePlayerPacket && player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("NetworkLevelValidationMixin: Intercepting move player packet at network level for Bedrock player: {}", playerName);
                    
                    ServerboundMovePlayerPacket movePacket = (ServerboundMovePlayerPacket) packet;
                    
                    // Handle the packet completely at this level
                    try {
                        double x = movePacket.getX(player.getX());
                        double y = movePacket.getY(player.getY());
                        double z = movePacket.getZ(player.getZ());
                        float yRot = movePacket.getYRot(player.getYRot());
                        float xRot = movePacket.getXRot(player.getXRot());
                        
                        // Apply basic sanity checks
                        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) &&
                            Float.isFinite(yRot) && Float.isFinite(xRot) &&
                            Math.abs(x) < 30000000 && Math.abs(z) < 30000000 && 
                            y > -2048 && y < 2048) {
                            
                            // Apply the movement directly
                            player.setPos(x, y, z);
                            player.setYRot(yRot);
                            player.setXRot(xRot);
                            
                            LOGGER.debug("NetworkLevelValidationMixin: Applied network-level movement for Bedrock player: {}", playerName);
                        } else {
                            LOGGER.warn("NetworkLevelValidationMixin: Rejected invalid movement data for Bedrock player: {}", playerName);
                        }
                        
                        // Always cancel to prevent further processing that could cause validation errors
                        ci.cancel();
                        return;
                        
                    } catch (Exception e) {
                        LOGGER.error("NetworkLevelValidationMixin: Exception handling network-level move packet: {}", e.getMessage(), e);
                        ci.cancel();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NetworkLevelValidationMixin: Exception in network-level packet interception: {}", e.getMessage());
        }
    }

    /**
     * Redirects all disconnect calls that might contain "invalid move player packet received".
     */
    @Redirect(
        method = "*",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/chat/Component;)V"),
        require = 0
    )
    private void interceptAllDisconnects(Connection connection, Component reason) {
        try {
            String reasonText = reason != null ? reason.getString().toLowerCase() : "";
            
            // Check if this might be a Bedrock player disconnect
            if (reasonText.contains("invalid") && 
                (reasonText.contains("move") || reasonText.contains("player") || reasonText.contains("packet"))) {
                
                LOGGER.info("NetworkLevelValidationMixin: INTERCEPTED potential Bedrock disconnect: {}", reasonText);
                
                // Try to determine if this is a Bedrock player
                if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                    LOGGER.info("NetworkLevelValidationMixin: PREVENTED disconnect for confirmed Bedrock player: {} - {}", 
                        player.getGameProfile().getName(), reasonText);
                    return; // Don't disconnect Bedrock players for movement validation
                }
                
                // If we can't confirm it's NOT a Bedrock player, be cautious and log
                LOGGER.warn("NetworkLevelValidationMixin: Potential Bedrock disconnect blocked: {}", reasonText);
                return;
            }
        } catch (Exception e) {
            LOGGER.debug("NetworkLevelValidationMixin: Exception in disconnect interception: {}", e.getMessage());
        }
        
        // Allow normal disconnects
        connection.disconnect(reason);
    }

    /**
     * Catches any component creation that might contain the invalid move player message.
     */
    @Redirect(
        method = "*",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
        require = 0
    )
    private net.minecraft.network.chat.MutableComponent interceptTranslatableComponents(String key) {
        try {
            if (key != null && key.contains("multiplayer.disconnect.invalid_player_movement")) {
                LOGGER.info("NetworkLevelValidationMixin: INTERCEPTED invalid player movement message creation: {}", key);
                
                // Check if this is for a Bedrock player
                if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                    LOGGER.info("NetworkLevelValidationMixin: SUPPRESSED invalid player movement message for Bedrock player: {}", 
                        player.getGameProfile().getName());
                    // Return a benign message instead
                    return Component.literal("Connection issue resolved");
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NetworkLevelValidationMixin: Exception in component interception: {}", e.getMessage());
        }
        
        // Return normal component
        return Component.translatable(key);
    }
}
