package zz.friendlink.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zz.friendlink.gui.P2PConnectScreen;
import zz.friendlink.gui.P2PUiActions;
import zz.friendlink.i18n.P2PTexts;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void friendLink$addButton(CallbackInfo ci) {
        int bottomButtonY = this.height / 4 + 120;
        int buttonGap = 4;
        for (var child : this.children()) {
            if (child instanceof Button button) {
                int buttonBottom = button.getY() + button.getHeight();
                if (buttonBottom > bottomButtonY) {
                    bottomButtonY = buttonBottom;
                    buttonGap = button.getHeight() + 4;
                }
            }
        }

        int buttonWidth = 100;
        int buttonHeight = 20;
        int gap = 4;
        int y = bottomButtonY + 4;
        int leftX = this.width / 2 - 102;

        this.addRenderableWidget(Button.builder(P2PTexts.c("button.p2p_host"), button -> {
                button.setMessage(P2PTexts.c("button.listening"));
                button.active = false;
                P2PUiActions.listen(this.minecraft, message -> {
                    boolean success = message.startsWith(P2PTexts.s("status.listen_success"));
                    if (!message.startsWith(P2PTexts.s("status.signaling_connecting")) && !success) {
                        button.active = true;
                    }
                    if (success) {
                        button.setMessage(P2PTexts.c("status.listen_success"));
                    } else {
                        button.setMessage(P2PTexts.c("button.p2p_host"));
                    }
                });
            })
            .bounds(leftX, y, buttonWidth, buttonHeight)
            .build());

        this.addRenderableWidget(Button.builder(P2PTexts.c("button.friend_multiplayer"), button ->
                this.minecraft.setScreen(new P2PConnectScreen((Screen) (Object) this)))
            .bounds(leftX + buttonWidth + gap, y, buttonWidth, buttonHeight)
            .build());
    }
}
