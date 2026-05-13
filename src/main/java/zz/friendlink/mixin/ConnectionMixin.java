package zz.friendlink.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zz.friendlink.webrtc.RtcChannel;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow
    private Channel channel;

    @Inject(method = "isMemoryConnection", at = @At("HEAD"), cancellable = true)
    private void officialP2P$isMemoryConnection(CallbackInfoReturnable<Boolean> cir) {
        if (channel instanceof RtcChannel) {
            cir.setReturnValue(true);
        }
    }
}
