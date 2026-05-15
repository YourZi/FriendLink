package zz.friendlink;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zz.friendlink.diagnostics.P2PDiagnostics;
import zz.friendlink.diagnostics.P2PStatus;
import zz.friendlink.friends.OfficialFriendsClient;
import zz.friendlink.friends.OfficialFriendsException;
import zz.friendlink.friends.model.FriendData;
import zz.friendlink.friends.model.JoinInfoUpdate;
import zz.friendlink.friends.model.PresenceResponse;
import zz.friendlink.p2p.ExperimentalP2PSessionManager;
import zz.friendlink.p2p.HostPresencePublisher;
import zz.friendlink.p2p.PeerTargetResolver;
import zz.friendlink.signaling.SignalingServiceClient;
import zz.friendlink.util.Uuids;

import java.net.ProxySelector;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Mod(FriendLinkClient.MOD_ID)
public final class FriendLinkClient {
    public static final String MOD_ID = "friendlink";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ExperimentalP2PSessionManager experimentalP2P;

    public FriendLinkClient() {
        LOGGER.info("FriendLink loaded");
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent
        static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            registerCommands(event);
        }
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("friendlink")
                .then(Commands.literal("id").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    context.getSource().sendSuccess(() -> Component.literal("[FriendLink] player="
                        + minecraft.getUser().getName() + " uuid=" + minecraft.getUser().getProfileId()), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    P2PStatus status = P2PDiagnostics.collect(minecraft);
                    status.toChatLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    status.log(LOGGER);
                    return 1;
                }))
                .then(Commands.literal("friends").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    User user = minecraft.getUser();
                    context.getSource().sendSuccess(() -> Component.literal("[FriendLink] fetching official friends..."), false);

                    CompletableFuture
                        .supplyAsync(() -> new OfficialFriendsClient(user.getAccessToken(), ProxySelector.getDefault()).getFriendData())
                        .whenComplete((friendData, throwable) -> minecraft.execute(() -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                                String message = cause instanceof OfficialFriendsException friendsException
                                    ? friendsException.userMessage()
                                    : cause.getClass().getSimpleName() + ": " + cause.getMessage();
                                context.getSource().sendSuccess(() -> Component.literal("[FriendLink] friends failed: " + message), false);
                                LOGGER.warn("FriendLink friends request failed", cause);
                                return;
                            }

                            sendFriendData(context.getSource(), friendData);
                        }));
                    return 1;
                }))
                .then(Commands.literal("presence").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    String status = HostPresencePublisher.currentStatus(minecraft);
                    context.getSource().sendSuccess(() -> Component.literal("[FriendLink] posting " + status + " presence..."), false);

                    CompletableFuture
                        .supplyAsync(() -> new OfficialFriendsClient(minecraft.getUser().getAccessToken(), ProxySelector.getDefault())
                            .presence(status, JoinInfoUpdate.emptyInvites()))
                        .whenComplete((presence, throwable) -> minecraft.execute(() -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                                String message = cause instanceof OfficialFriendsException friendsException
                                    ? friendsException.userMessage()
                                    : cause.getClass().getSimpleName() + ": " + cause.getMessage();
                                context.getSource().sendSuccess(() -> Component.literal("[FriendLink] presence failed: " + message), false);
                                LOGGER.warn("Official presence request failed", cause);
                                return;
                            }

                            sendPresenceData(context.getSource(), presence);
                        }));
                    return 1;
                }))
                .then(Commands.literal("signaling").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    User user = minecraft.getUser();
                    context.getSource().sendSuccess(() -> Component.literal("[FriendLink] connecting official signaling..."), false);

                    SignalingServiceClient signaling = new SignalingServiceClient(user);
                    signaling.connect()
                        .thenCompose(ignored -> signaling.requestTurnAuth())
                        .whenComplete((iceServer, throwable) -> {
                            signaling.close();
                            minecraft.execute(() -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                                    context.getSource().sendSuccess(() -> Component.literal("[FriendLink] signaling failed: "
                                        + cause.getClass().getSimpleName() + ": " + cause.getMessage()), false);
                                    LOGGER.warn("Official signaling request failed", cause);
                                    return;
                                }

                                context.getSource().sendSuccess(() -> Component.literal("[FriendLink] signaling OK, TURN urls="
                                    + iceServer.urls.size()), false);
                                iceServer.urls.stream()
                                    .limit(4)
                                    .forEach(url -> context.getSource().sendSuccess(() -> Component.literal(" - " + url), false));
                            });
                        });
                    return 1;
                }))
                .then(Commands.literal("listen").executes(context -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.getSingleplayerServer() == null) {
                        context.getSource().sendSuccess(() -> Component.literal("[FriendLink] listen needs a single-player world first"), false);
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("[FriendLink] connecting signaling listener..."), false);
                    ExperimentalP2PSessionManager manager = experimentalManager(minecraft);
                    manager.connectSignaling()
                        .thenCompose(ignored -> manager.publishHostedPresence())
                        .orTimeout(35, TimeUnit.SECONDS)
                        .whenComplete((presence, throwable) -> minecraft.execute(() -> {
                            if (throwable != null) {
                                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                                context.getSource().sendSuccess(() -> Component.literal("[FriendLink] listen failed: "
                                    + cause.getClass().getSimpleName() + ": " + cause.getMessage()), false);
                                LOGGER.warn("P2P listener failed", cause);
                                return;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("[FriendLink] listening for P2P offers; hosted presence posted; visible entries="
                                + presence.presence().size()), false);
                            context.getSource().sendSuccess(() -> Component.literal("[FriendLink] HOST "
                                + minecraft.getUser().getName() + " UUID=" + minecraft.getUser().getProfileId()), false);
                        }));
                    return 1;
                }))
                .then(Commands.literal("connect")
                    .then(Commands.argument("peerPmid", StringArgumentType.word()).executes(context -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        UUID peerPmid = Uuids.parseFlexible(StringArgumentType.getString(context, "peerPmid"));
                        context.getSource().sendSuccess(() -> Component.literal("[FriendLink] resolving host id " + peerPmid), false);
                        ExperimentalP2PSessionManager manager = experimentalManager(minecraft);
                        PeerTargetResolver.resolve(minecraft, peerPmid)
                            .thenCompose(resolved -> {
                                minecraft.execute(() -> context.getSource().sendSuccess(() -> Component.literal("[FriendLink] "
                                    + resolved.message()), false));
                                return manager.connectSignaling()
                                    .thenCompose(ignored -> manager.startOffer(resolved.targetPlayerId()));
                            })
                            .orTimeout(65, TimeUnit.SECONDS)
                            .whenComplete((ignored, throwable) -> minecraft.execute(() -> {
                                if (throwable != null) {
                                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                                    context.getSource().sendSuccess(() -> Component.literal("[FriendLink] connect failed: "
                                        + shortError(cause)), false);
                                    LOGGER.warn("P2P connect failed", cause);
                                    return;
                                }
                                context.getSource().sendSuccess(() -> Component.literal("[FriendLink] WebRTC channel open; joining host world"), false);
                            }));
                        return 1;
                    })))
        );
    }

    public static ExperimentalP2PSessionManager experimentalManager(Minecraft minecraft) {
        if (experimentalP2P == null) {
            experimentalP2P = new ExperimentalP2PSessionManager(minecraft);
        }
        return experimentalP2P;
    }

    public static ExperimentalP2PSessionManager experimentalManagerIfPresent() {
        return experimentalP2P;
    }

    private static void sendFriendData(CommandSourceStack source, FriendData friendData) {
        source.sendSuccess(() -> Component.literal("[FriendLink] friends=" + friendData.friends().size()
            + " incoming=" + friendData.incomingRequests().size()
            + " outgoing=" + friendData.outgoingRequests().size()), false);
        friendData.friends().stream()
            .limit(10)
            .forEach(friend -> source.sendSuccess(() -> Component.literal(" - " + friend.name() + " " + friend.profileId()), false));
    }

    private static void sendPresenceData(CommandSourceStack source, PresenceResponse presence) {
        source.sendSuccess(() -> Component.literal("[FriendLink] presence entries=" + presence.presence().size()), false);
        presence.presence().stream()
            .limit(10)
            .forEach(entry -> source.sendSuccess(() -> Component.literal(" - " + entry.profileId()
                + " status=" + entry.status()
                + " pmid=" + entry.pmid()
                + " invited=" + (entry.joinInfo() != null && entry.joinInfo().invited())), false));
    }

    private static String shortError(Throwable cause) {
        String message = cause.getMessage();
        if (message != null && message.contains("Player not registered with the service")) {
            return "target player is not registered with signaling. Keep host on Listen, update both jars, and use the pmid from /friendlink presence.";
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }
}
