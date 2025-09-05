package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.network.chat.Component;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Debug mixin to trace the exact flow of move player packet processing
 * and identify the root cause of the "invalid move player packet received" error.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MovePlayerDebugMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("MovePlayerDebugMixin");
    
    @Shadow
    public ServerPlayer player;

    /**
     * Trace the entry point of handleMovePlayer to see what packets are coming in.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At("HEAD")
    )
    private void traceHandleMovePlayerEntry(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("MovePlayerDebugMixin: ENTRY - handleMovePlayer for Bedrock player: {} (packet type: {})", 
                        playerName, packet.getClass().getSimpleName());
                    
                    // Log packet details
                    try {
                        double x = packet.getX(player.getX());
                        double y = packet.getY(player.getY());
                        double z = packet.getZ(player.getZ());
                        float yRot = packet.getYRot(player.getYRot());
                        float xRot = packet.getXRot(player.getXRot());
                        
                        LOGGER.info("MovePlayerDebugMixin: Packet data - pos: ({}, {}, {}) rot: ({}, {}) current: ({}, {}, {})", 
                            x, y, z, yRot, xRot, player.getX(), player.getY(), player.getZ());
                        
                        // Check for invalid values
                        boolean hasInvalidPos = Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
                                              Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z);
                        boolean hasInvalidRot = Float.isNaN(yRot) || Float.isNaN(xRot) ||
                                              Float.isInfinite(yRot) || Float.isInfinite(xRot);
                        
                        if (hasInvalidPos || hasInvalidRot) {
                            LOGGER.warn("MovePlayerDebugMixin: INVALID VALUES DETECTED - pos invalid: {}, rot invalid: {}", 
                                hasInvalidPos, hasInvalidRot);
                        }
                        
                        // Check for large movement
                        double deltaX = Math.abs(x - player.getX());
                        double deltaY = Math.abs(y - player.getY());
                        double deltaZ = Math.abs(z - player.getZ());
                        
                        if (deltaX > 10 || deltaY > 10 || deltaZ > 10) {
                            LOGGER.warn("MovePlayerDebugMixin: LARGE MOVEMENT DETECTED - dx: {}, dy: {}, dz: {}", 
                                deltaX, deltaY, deltaZ);
                        }
                        
                    } catch (Exception e) {
                        LOGGER.error("MovePlayerDebugMixin: Exception reading packet data: {}", e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("MovePlayerDebugMixin: Exception in entry trace: {}", e.getMessage(), e);
        }
    }

    /**
     * Trace the exit point of handleMovePlayer to see if it completes successfully.
     */
    @Inject(
        method = "handleMovePlayer",
        at = @At("RETURN")
    )
    private void traceHandleMovePlayerExit(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("MovePlayerDebugMixin: EXIT - handleMovePlayer completed successfully for Bedrock player: {}", 
                        playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.error("MovePlayerDebugMixin: Exception in exit trace: {}", e.getMessage(), e);
        }
    }

    /**
     * Trace any disconnect attempts with full stack trace to see where they originate.
     */
    @Inject(
        method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD")
    )
    private void traceDisconnectAttempt(Component reason, CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    String reasonText = reason != null ? reason.getString() : "null";
                    LOGGER.error("MovePlayerDebugMixin: DISCONNECT ATTEMPT for Bedrock player: {} - reason: '{}' - STACK TRACE:", 
                        playerName, reasonText);
                    
                    // Log stack trace to see where disconnect is coming from
                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                    for (int i = 0; i < Math.min(stackTrace.length, 15); i++) {
                        LOGGER.error("  at {}", stackTrace[i]);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("MovePlayerDebugMixin: Exception in disconnect trace: {}", e.getMessage(), e);
        }
    }

    /**
     * Trace any Component.translatable calls that might be creating the error message.
     */
    @Inject(
        method = "*",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
        require = 0
    )
    private void traceComponentCreation(CallbackInfo ci) {
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                boolean isBedrockPlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                if (isBedrockPlayer) {
                    LOGGER.info("MovePlayerDebugMixin: Component.translatable called for Bedrock player: {} - STACK TRACE:", playerName);
                    
                    // Log partial stack trace
                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                    for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                        LOGGER.info("  at {}", stackTrace[i]);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("MovePlayerDebugMixin: Exception in component trace: {}", e.getMessage());
        }
    }
}
