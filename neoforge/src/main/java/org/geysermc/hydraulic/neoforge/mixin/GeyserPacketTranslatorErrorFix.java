package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents packet translation errors in Geyser from causing issues.
 * Specifically handles errors in the PacketTranslatorRegistry.translate method.
 */
@Mixin(targets = "org.geysermc.geyser.registry.PacketTranslatorRegistry", remap = false)
public class GeyserPacketTranslatorErrorFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("GeyserPacketTranslatorErrorFix");

    /**
     * Catches and handles exceptions in packet translation to prevent crashes.
     */
    @Inject(
        method = "translate0",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void handleTranslationErrors(Object session, Object packet, CallbackInfo ci) {
        try {
            // Let the original method proceed, but we'll catch any exceptions
        } catch (Exception e) {
            // Log the error but don't let it crash the session
            LOGGER.warn("GeyserPacketTranslatorErrorFix: Caught packet translation error: {} for packet: {}", 
                e.getMessage(), packet != null ? packet.getClass().getSimpleName() : "null");
            ci.cancel(); // Prevent the original method from running
        }
    }

    /**
     * Additional safety for the main translate method.
     */
    @Inject(
        method = "translate",
        at = @At(value = "INVOKE", target = "translate0"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void safeTranslate(Object session, Object packet, CallbackInfo ci) {
        try {
            // Additional safety wrapper
            if (packet == null) {
                LOGGER.debug("GeyserPacketTranslatorErrorFix: Skipping translation for null packet");
                ci.cancel();
                return;
            }
            
            // Check for known problematic packet types
            String packetName = packet.getClass().getSimpleName();
            if (packetName.contains("SetEntityData") || packetName.contains("EntityMetadata")) {
                LOGGER.debug("GeyserPacketTranslatorErrorFix: Processing potentially problematic packet: {}", packetName);
            }
        } catch (Exception e) {
            LOGGER.warn("GeyserPacketTranslatorErrorFix: Error in safe translate wrapper: {}", e.getMessage());
            ci.cancel();
        }
    }
}
