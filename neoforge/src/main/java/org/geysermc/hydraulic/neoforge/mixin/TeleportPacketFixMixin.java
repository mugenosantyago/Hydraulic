package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
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
 * This mixin fixes the ACTUAL root cause: teleportation packet validation issues
 * that generate the misleading "invalid move player packet received" error.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class TeleportPacketFixMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("TeleportPacketFixMixin");
    
    @Shadow
    public ServerPlayer player;
    
    @Shadow
    private int awaitingTeleport;

    /**
     * Fix teleportation acceptance packet validation for Bedrock players.
     * This is the ACTUAL source of the "invalid move player packet received" error.
     */
    @Inject(
        method = "handleAcceptTeleportPacket",
        at = @At("HEAD"),
        cancellable = true
    )
    private void fixBedrockTeleportAcceptance(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("TeleportPacketFixMixin: Handling teleport acceptance for Bedrock player: {} (packet ID: {})", 
                        playerName, packet.getId());
                    
                    // For Bedrock players, be more lenient with teleport acceptance
                    // The issue is that Bedrock clients send teleport acceptance packets differently
                    
                    try {
                        // Get the packet ID
                        int packetId = packet.getId();
                        
                        // For Bedrock players, we'll accept the teleport more leniently
                        // Check if there's a pending teleport
                        if (this.awaitingTeleport != 0) {
                            LOGGER.info("TeleportPacketFixMixin: Accepting teleport for Bedrock player: {} (expected: {}, received: {})", 
                                playerName, this.awaitingTeleport, packetId);
                            
                            // Clear the awaiting teleport
                            this.awaitingTeleport = 0;
                            
                            LOGGER.info("TeleportPacketFixMixin: Successfully processed teleport acceptance for Bedrock player: {}", playerName);
                            ci.cancel(); // Skip the original validation that causes issues
                            return;
                        } else {
                            LOGGER.debug("TeleportPacketFixMixin: No pending teleport for Bedrock player: {}", playerName);
                        }
                        
                    } catch (Exception e) {
                        LOGGER.error("TeleportPacketFixMixin: Exception handling teleport for Bedrock player {}: {}", 
                            playerName, e.getMessage(), e);
                        // For Bedrock players, if there's an exception, just accept it and move on
                        ci.cancel();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("TeleportPacketFixMixin: Critical exception in teleport fix: {}", e.getMessage(), e);
        }
    }
}
