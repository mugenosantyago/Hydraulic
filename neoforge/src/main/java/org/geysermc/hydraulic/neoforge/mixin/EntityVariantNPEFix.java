package org.geysermc.hydraulic.neoforge.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin prevents NPEs in Geyser's TemperatureVariantAnimal.setBedrockVariant method.
 * The NPE occurs when the variant is null, causing packet translation failures.
 */
@Mixin(targets = "org.geysermc.geyser.entity.type.living.animal.farm.TemperatureVariantAnimal", remap = false)
public class EntityVariantNPEFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityVariantNPEFix");

    /**
     * Prevents NPE when setting Bedrock variant with null variant.
     */
    @Inject(
        method = "setBedrockVariant(Lorg/geysermc/geyser/entity/type/living/animal/farm/TemperatureVariantAnimal$BuiltInVariant;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void preventNullVariantNPE(Object variant, CallbackInfo ci) {
        if (variant == null) {
            LOGGER.debug("EntityVariantNPEFix: Preventing NPE - variant is null, skipping setBedrockVariant");
            ci.cancel(); // Skip the method execution to prevent NPE
        }
    }

    /**
     * Additional safety for the variant setting method that takes no parameters.
     */
    @Inject(
        method = "setBedrockVariant()V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void preventNullVariantNPENoArgs(CallbackInfo ci) {
        try {
            // Try to access the variant field through reflection to check if it's null
            java.lang.reflect.Field variantField = this.getClass().getDeclaredField("variant");
            variantField.setAccessible(true);
            Object currentVariant = variantField.get(this);
            
            if (currentVariant == null) {
                LOGGER.debug("EntityVariantNPEFix: Preventing NPE - current variant is null, skipping setBedrockVariant");
                ci.cancel();
            }
        } catch (Exception e) {
            // If we can't access the field, let the method proceed but log the attempt
            LOGGER.debug("EntityVariantNPEFix: Could not check variant field: {}", e.getMessage());
        }
    }
}
