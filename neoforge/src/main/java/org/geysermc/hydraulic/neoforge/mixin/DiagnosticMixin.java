package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Simple diagnostic mixin to verify that mixins are being loaded.
 */
@Mixin(net.minecraft.server.MinecraftServer.class)
public class DiagnosticMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("DiagnosticMixin");
    private static boolean hasLogged = false;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void onServerTick(CallbackInfo ci) {
        if (!hasLogged) {
            LOGGER.info("DiagnosticMixin: Hydraulic NeoForge mixins are successfully loaded and working!");
            hasLogged = true;
        }
    }
}
