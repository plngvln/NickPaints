package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import net.p4pingvin4ik.NickPaints.client.WebSocketManager;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import net.p4pingvin4ik.NickPaints.interfaces.IEntityProvider;
import net.p4pingvin4ik.NickPaints.util.GradientData;
import net.p4pingvin4ik.NickPaints.util.NametagNicknameLocator;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(value = EntityRenderer.class, priority = 990)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("HEAD")
    )
    private void nickpaints$queueNametagGradient(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, int yOffset, CallbackInfo ci) {
        Component nameTag = state.nameTag;
        if (nameTag == null || state.nameTagAttachment == null) {
            return;
        }

        Entity entity = ((IEntityProvider) state).getEntity();
        String paintToShow = resolvePaint(entity, state);
        if (paintToShow == null) {
            return;
        }

        int paintGlyphStart = -1;
        int paintGlyphEnd = -1;
        int totalLength;
        if (entity instanceof Player player) {
            Optional<NametagNicknameLocator.CharRange> nickname = NametagNicknameLocator.findNicknameRange(nameTag, player);
            if (nickname.isPresent() && nickname.get().length() > 0) {
                paintGlyphStart = nickname.get().start();
                paintGlyphEnd = nickname.get().end();
                totalLength = nickname.get().length();
            } else {
                totalLength = calculatePaintableLength(nameTag);
            }
        } else {
            totalLength = calculatePaintableLength(nameTag);
        }

        if (totalLength > 0) {
            GradientData.PENDING_NAMETAG_GRADIENTS.put(nameTag, new GradientData(paintToShow, totalLength, paintGlyphStart, paintGlyphEnd));
        }
    }

    @Unique
    private static String resolvePaint(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Player player) || state.nameTagAttachment == null) {
            return null;
        }
        if (!ConfigManager.CONFIG.isRenderingEnabledFor(player.getUUID())) {
            return null;
        }
        String paint;
        if (player.equals(Minecraft.getInstance().player)) {
            paint = ConfigManager.CONFIG.currentGradient;
        } else {
            String cachedPaint = WebSocketManager.paintCache.get(player.getUUID());
            if (cachedPaint == null) {
                WebSocketManager.queuePaintForPlayer(player.getUUID());
                return null;
            }
            paint = cachedPaint;
        }
        // "" / blank from server or config → do not touch the nametag (vanilla render).
        if (paint == null || paint.trim().isEmpty()) {
            return null;
        }
        return paint;
    }

    @Unique
    private int calculatePaintableLength(Component component) {
        if (NickPaintsMod.PROTECTED_TAG_INSERTION_KEY.equals(component.getStyle().getInsertion())) {
            return 0;
        }
        int length = 0;
        if (component.getContents() instanceof PlainTextContents literalContent) {
            String s = literalContent.text();
            for (int i = 0; i < s.length(); i++) {
                if (!Character.isWhitespace(s.charAt(i))) {
                    length++;
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            length += calculatePaintableLength(sibling);
        }
        return length;
    }
}
