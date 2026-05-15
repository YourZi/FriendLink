package zz.friendlink.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zz.friendlink.assets.FriendLinkAssets;
import zz.friendlink.gui.P2PConnectScreen;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final ResourceLocation FRIENDS_ICON =
        ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/friends.png");

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void friendLink$addFriendButton(CallbackInfo ci) {
        FriendLinkAssets.ensureInitialized();
        int x = this.width - 42;
        int y = 6;
        for (var child : this.children()) {
            if (child instanceof Button button
                && button.getMessage().getContents() instanceof TranslatableContents tc
                && "menu.multiplayer".equals(tc.getKey())) {
                x = button.getX() + button.getWidth() + 4;
                y = button.getY();
                break;
            }
        }
        this.addRenderableWidget(new FriendIconButton(
            x, y,
            button -> this.minecraft.setScreen(new P2PConnectScreen((Screen) (Object) this))
        ));
    }

    private static final class FriendIconButton extends Button {
        FriendIconButton(int x, int y, OnPress onPress) {
            super(x, y, 20, 20, Component.empty(), onPress, Button.DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            int iconX = this.getX() + 2;
            int iconY = this.getY() + 2;
            guiGraphics.blit(FRIENDS_ICON, iconX, iconY, 0, 0, 16, 16, 16, 16);
        }
    }
}
