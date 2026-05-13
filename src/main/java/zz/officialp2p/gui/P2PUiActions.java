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
            status(client, statusSink, "Listen needs a single-player world first.");
            return CompletableFuture.completedFuture(null);
        }

        status(client, statusSink, "Listen clicked. Connecting official signaling...");
        ExperimentalP2PSessionManager manager = OfficialP2PBackportClient.experimentalManager(client);
        return manager.connectSignaling()
            .thenCompose(ignored -> manager.publishHostedPresence())
            .orTimeout(35, TimeUnit.SECONDS)
            .whenComplete((presence, throwable) -> client.execute(() -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    status(client, statusSink, "Listen failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    return;
                }
                status(client, statusSink, "Listening OK. Hosted presence posted. Visible entries="
                    + presence.presence().size());
                status(client, statusSink, "HOST " + client.getUser().getName() + " UUID="
                    + client.getUser().getProfileId());
            }))
            .thenApply(ignored -> null);
    }

    public static CompletableFuture<Void> connect(Minecraft client, UUID peerPmid, Consumer<String> statusSink) {
        status(client, statusSink, "Connect clicked. Resolving host id " + peerPmid + "...");
        ExperimentalP2PSessionManager manager = OfficialP2PBackportClient.experimentalManager(client);
        return PeerTargetResolver.resolve(client, peerPmid)
            .thenCompose(resolved -> {
                client.execute(() -> status(client, statusSink, resolved.message()));
                return manager.connectSignaling()
                    .orTimeout(20, TimeUnit.SECONDS)
                    .thenCompose(ignored -> {
                        client.execute(() -> status(client, statusSink, "Signaling connected. Sending WebRTC offer to "
                            + resolved.targetPlayerId() + "..."));
                        return manager.startOffer(resolved.targetPlayerId());
                    });
            })
            .orTimeout(65, TimeUnit.SECONDS)
            .whenComplete((ignored, throwable) -> client.execute(() -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    status(client, statusSink, "Connect failed: " + shortError(cause));
                    return;
                }
                status(client, statusSink, "WebRTC channel open. Joining host world...");
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
            return "player not registered with signaling; use the host pmid, not the host UUID.";
        }
        if (message != null && message.contains("Message to player could not be delivered")) {
            return "message could not be delivered; host must click Listen again and stay in the world.";
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }
}
