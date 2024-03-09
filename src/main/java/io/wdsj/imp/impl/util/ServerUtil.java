package io.wdsj.imp.impl.util;

import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.chat.ChatTypes;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage_v1_16;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import io.wdsj.imp.Imp;
import io.wdsj.imp.impl.player.Player;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.UUID;

public class ServerUtil {
    public static void broadcastMessage(Component component) {
        for (Player player : Imp.PLAYERS) {
            player.sendMessage(component);
        }
    }

    public static void handlePlayerQuit(User user, Player player) {
        if (user.getConnectionState() == ConnectionState.PLAY) {
            ServerUtil.handlePlayerLeave(player);
        }
        ProtocolManager.USERS.remove(user.getChannel());
        Imp.PLAYERS.remove(player);
    }

    public static void handlePlayerLeave(Player player) {
        Component translatableComponent = Component.translatable("multiplayer.player.left").color(NamedTextColor.YELLOW)
                .args(Component
                        .text(player.getUsername())
                        .asComponent())
                .asComponent();
        for (Player p : Imp.PLAYERS) {
            if (p.getEntityId() != player.getEntityId()) {
                WrapperPlayServerPlayerInfo.PlayerData data = new WrapperPlayServerPlayerInfo.PlayerData(null, player.getUserProfile(), null, -1);
                WrapperPlayServerPlayerInfo removePlayerInfo = new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER, data);

                WrapperPlayServerDestroyEntities destroyEntities = new WrapperPlayServerDestroyEntities(player.getEntityId());

                ChatMessage leftChatMsg = new ChatMessage_v1_16(translatableComponent, ChatTypes.CHAT, new UUID(0L, 0L));
                WrapperPlayServerChatMessage leftMessage =
                        new WrapperPlayServerChatMessage(leftChatMsg);
                //Remove this user from everyone's tab list
                p.sendPacket(removePlayerInfo);
                //Destroy this user's entity
                p.sendPacket(destroyEntities);
                //Send a message to everyone that this user has left
                p.sendPacket(leftMessage);
            }
        }
        Imp.LOGGER.info(player.getUsername() + " left the server.");
        ProtocolManager.CHANNELS.remove(player.getUsername());
    }

    public static void handlePlayerJoin(User user, Player player) {
        Imp.PLAYERS.add(player);
        HoverEvent<HoverEvent.ShowEntity> hoverEvent = HoverEvent.hoverEvent(HoverEvent.Action.SHOW_ENTITY,
                HoverEvent.ShowEntity.showEntity(Key.key("minecraft:player"),
                        player.getUserProfile().getUUID(),
                        Component.text(player.getUsername())));
        ClickEvent clickEvent = ClickEvent.suggestCommand("/tell " + player.getUsername() + " Welcome!");
        Component translatableComponent = Component.translatable("multiplayer.player.joined")
                .color(NamedTextColor.YELLOW)
                .args(Component
                        .text(player.getUsername())
                        .hoverEvent(hoverEvent)
                        .clickEvent(clickEvent).asComponent())
                .asComponent();
        for (Player p : Imp.PLAYERS) {
            ChatMessage loginChatMsg = new ChatMessage_v1_16(translatableComponent, ChatTypes.CHAT, new UUID(0L, 0L));
            WrapperPlayServerChatMessage loginMessage = new WrapperPlayServerChatMessage(loginChatMsg);

            Component otherDisplayName = Component.text(player.getUsername()).color(NamedTextColor.DARK_GREEN).asComponent();
            WrapperPlayServerPlayerInfo.PlayerData nextData =
                    new WrapperPlayServerPlayerInfo.PlayerData(otherDisplayName, player.getUserProfile(), player.getGameMode(), 100);
            WrapperPlayServerPlayerInfo nextPlayerInfo =
                    new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, nextData);
            //Send every player the login message
            p.sendPacket(loginMessage);

            //Add this joining user to everyone's tab list
            Component displayName = Component.text(p.getUsername()).color(NamedTextColor.DARK_GREEN).asComponent();
            WrapperPlayServerPlayerInfo.PlayerData data = new WrapperPlayServerPlayerInfo.PlayerData(displayName,
                    p.getUserProfile(), p.getGameMode(), 100);
            WrapperPlayServerPlayerInfo playerInfo = new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, data);
            player.sendPacket(playerInfo);
            p.sendPacket(nextPlayerInfo);
        }

        Imp.LOGGER.info(player.getUsername() + " has joined the server.");

        //NPC
        /*Imp.WORKER_THREADS.execute(() -> {
            UUID md5UUID = MojangAPIUtil.requestPlayerUUID("md_5");
            UserProfile profile = new UserProfile(md5UUID, "md_5", MojangAPIUtil.requestPlayerTextureProperties(md5UUID));
            NPC npc = new NPC(profile, Imp.ENTITIES++, Component.text("md_5_npc").asComponent(),
                    NamedTextColor.BLACK, Component.text("Owner: ").color(NamedTextColor.RED),
                    null);
            npc.setLocation(player.getEntityInformation().getLocation());
            npc.spawn(user.getChannel());
        });*/
    }
}

