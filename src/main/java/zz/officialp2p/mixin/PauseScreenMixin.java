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
        this.addRenderableWidget(Button.builder(Component.literal("P2P Listen"), button -> {
                button.setMessage(Component.literal("Listening..."));
                button.active = false;
                P2PUiActions.listen(this.minecraft, message -> {
                    if (!message.startsWith("Listen clicked")) {
                        button.active = true;
                    }
                    button.setMessage(Component.literal(message.startsWith("Listening OK") ? "Listening OK" : "P2P Listen"));
                });
            })
            .bounds(this.width - 224, 8, 104, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Official P2P"), button ->
                this.minecraft.setScreen(new P2PConnectScreen((Screen) (Object) this)))
            .bounds(this.width - 112, 8, 104, 20)
            .build());
    }
}
