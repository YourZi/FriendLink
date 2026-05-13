package zz.officialp2p.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import zz.officialp2p.OfficialP2PBackportClient;
import zz.officialp2p.p2p.ExperimentalP2PSessionManager;
import zz.officialp2p.p2p.HostPresencePublisher;
import zz.officialp2p.p2p.PeerTargetResolver;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class P2PUiActions {
    private P2PUiActions() {
    }

    public static CompletableFuture<Void> listen(Minecraft client, Consumer<String> statusSink) {
        if (client.getSingleplayerServer() == null) {
            status(client, statusSink, "开房需要先进入单人世界。");
            return CompletableFuture.completedFuture(null);
        }

        status(client, statusSink, "正在连接官方信令...");
        ExperimentalP2PSessionManager manager = OfficialP2PBackportClient.experimentalManager(client);
        return manager.connectSignaling()
            .thenCompose(ignored -> manager.publishHostedPresence())
            .orTimeout(35, TimeUnit.SECONDS)
            .whenComplete((presence, throwable) -> client.execute(() -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    status(client, statusSink, "开房失败：" + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    return;
                }
                status(client, statusSink, "开房成功，已发布在线状态。可见条目="
                    + presence.presence().size());
                status(client, statusSink, "房主 " + client.getUser().getName() + " ID="
                    + client.getUser().getProfileId());
            }))
            .thenApply(ignored -> null);
    }

    public static CompletableFuture<Void> connect(Minecraft client, UUID peerPmid, Consumer<String> statusSink) {
        status(client, statusSink, "正在解析房主ID：" + peerPmid);
        ExperimentalP2PSessionManager manager = OfficialP2PBackportClient.experimentalManager(client);
        return PeerTargetResolver.resolve(client, peerPmid)
            .thenCompose(resolved -> {
                client.execute(() -> status(client, statusSink, "目标已解析，正在准备连接。"));
                return manager.connectSignaling()
                    .orTimeout(20, TimeUnit.SECONDS)
                    .thenCompose(ignored -> {
                        client.execute(() -> status(client, statusSink, "信令已连接，正在发送P2P连接请求。"));
                        return manager.startOffer(resolved.targetPlayerId());
                    });
            })
            .orTimeout(65, TimeUnit.SECONDS)
            .whenComplete((ignored, throwable) -> client.execute(() -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    status(client, statusSink, "加入失败：" + shortError(cause));
                    return;
                }
                status(client, statusSink, "P2P通道已打开，正在进入房主世界...");
            }));
    }

    public static void status(Minecraft client, Consumer<String> statusSink, String message) {
        OfficialP2PBackportClient.LOGGER.info("[P2P UI] {}", message);
        if (statusSink != null) {
            statusSink.accept(message);
        }
        Component component = Component.literal("[OfficialP2P " + OfficialP2PBackportClient.BUILD_MARKER + "] " + message);
        client.gui.setOverlayMessage(component, false);
        client.gui.getChat().addClientSystemMessage(component);
    }

    private static String shortError(Throwable cause) {
        String message = cause.getMessage();
        if (message != null && message.contains("Player not registered with the service")) {
            return "对方没有注册到官方信令；让房主重新点开房，并使用好友列表里的房主ID。";
        }
        if (message != null && message.contains("Message to player could not be delivered")) {
            return "消息发不到房主；房主需要重新点开房并留在世界里。";
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }
}
