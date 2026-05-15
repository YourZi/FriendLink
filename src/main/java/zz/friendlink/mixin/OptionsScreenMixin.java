package zz.friendlink.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zz.friendlink.gui.FriendLinkOnlineSettingsScreen;
import zz.friendlink.i18n.P2PTexts;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void friendLink$addOnlineSettings(CallbackInfo ci) {
        int doneY = this.height - 28;
        for (var child : this.children()) {
            if (child instanceof Button button
                && button.getMessage().getContents() instanceof TranslatableContents tc
                && "gui.done".equals(tc.getKey())) {
                doneY = button.getY();
                break;
            }
        }

        this.addRenderableWidget(Button.builder(P2PTexts.c("settings.open_button"), button ->
                this.minecraft.setScreen(new FriendLinkOnlineSettingsScreen((Screen) (Object) this)))
            .bounds(this.width / 2 - 100, doneY - 28, 200, 20)
            .build());
    }
}
