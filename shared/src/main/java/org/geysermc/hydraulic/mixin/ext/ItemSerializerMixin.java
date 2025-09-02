package org.geysermc.hydraulic.mixin.ext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "team.unnamed.creative.serialize.minecraft.item.ItemSerializer", remap = false)
public class ItemSerializerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemSerializerMixin");

    @Redirect(
        method = "readSelect",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/IllegalArgumentException;<init>(Ljava/lang/String;)V"
        )
    )
    private IllegalArgumentException redirectSelectException(String message) {
        LOGGER.warn("Skipping item model with unknown select property: {}. Using fallback model instead.", message);
        // Return a special marked exception that our other mixin can catch
        return new IllegalArgumentException("HYDRAULIC_SKIP_SELECT: " + message);
    }

    @Redirect(
        method = "readSpecial",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/IllegalArgumentException;<init>(Ljava/lang/String;)V"
        )
    )
    private IllegalArgumentException redirectSpecialException(String message) {
        LOGGER.warn("Skipping item model with unknown special render type: {}. Using null model instead.", message);
        // Return a special marked exception that our other mixin can catch
        return new IllegalArgumentException("HYDRAULIC_SKIP_SPECIAL: " + message);
    }
}
