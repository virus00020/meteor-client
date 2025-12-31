/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTab;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private static Comparator<PlayerListEntry> ENTRY_ORDERING;

    protected abstract List<PlayerListEntry> collectPlayerEntries();

    @Inject(method = "collectPlayerEntries", at = @At("HEAD"), cancellable = true)
    private void modifyCount(CallbackInfoReturnable<List<PlayerListEntry>> cir) {
        BetterTab module = Modules.get().get(BetterTab.class);

        if (client.player == null) return;
        if (module.isActive()) {
            cir.setReturnValue(client.player.networkHandler
                .getListedPlayerListEntries()
                .stream()
                .sorted(ENTRY_ORDERING)
                .limit(module.tabSize.get())
                .toList()
            );
        }
    }

    @Inject(method = "getPlayerName", at = @At("HEAD"), cancellable = true)
    private void getPlayerName(PlayerListEntry playerListEntry, CallbackInfoReturnable<Text> cir) {
        BetterTab module = Modules.get().get(BetterTab.class);
        if (module.isActive()) {
            cir.setReturnValue(module.getPlayerName(playerListEntry));
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"), index = 0)
    private int modifyWidth(int width) {
        BetterTab module = Modules.get().get(BetterTab.class);
        return module.isActive() && module.accurateLatency.get() ? width + 30 : width;
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", shift = At.Shift.BEFORE))
    private void modifyHeight(CallbackInfo ci, @Local(ordinal = 5) LocalIntRef o, @Local(ordinal = 6) LocalIntRef p) {
        BetterTab module = Modules.get().get(BetterTab.class);
        if (!module.isActive()) return;

        int totalPlayers = collectPlayerEntries().size();
        int rows = totalPlayers;
        int columns = 1;

        while (rows > module.tabHeight.get()) {
            columns++;
            rows = (totalPlayers + columns - 1) / columns;
        }

        o.set(rows);
        p.set(columns);
    }

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void onRenderLatencyIcon(DrawContext context, int width, int x, int y, PlayerListEntry entry, CallbackInfo ci) {
        BetterTab module = Modules.get().get(BetterTab.class);
        if (!module.isActive() || !module.accurateLatency.get()) return;

        TextRenderer textRenderer = client.textRenderer;
        int latency = MathHelper.clamp(entry.getLatency(), 0, 9999);
        int color = latency < 150 ? 0x00E970 : latency < 300 ? 0xE7D020 : 0xD74238;
        String text = latency + "ms";
        context.drawTextWithShadow(textRenderer, text, x + width - textRenderer.getWidth(text), y, color);
        ci.cancel();
    }
}
