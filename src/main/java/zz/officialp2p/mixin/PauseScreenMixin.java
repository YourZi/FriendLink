package zz.officialp2p.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zz.officialp2p.gui.P2PConnectScreen;
import zz.officialp2p.gui.P2PUiActions;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void officialP2P$addButton(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.literal("P2P开房"), button -> {
                button.setMessage(Component.literal("开房中..."));
                button.active = false;
                P2PUiActions.listen(this.minecraft, message -> {
                    if (!message.startsWith("正在连接")) {
                        button.active = true;
                    }
                    button.setMessage(Component.literal(message.startsWith("开房成功") ? "开房成功" : "P2P开房"));
                });
            })
            .bounds(this.width - 224, 8, 104, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("好友联机"), button ->
                this.minecraft.setScreen(new P2PConnectScreen((Screen) (Object) this)))
            .bounds(this.width - 112, 8, 104, 20)
            .build());
    }
}
