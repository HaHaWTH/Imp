package io.wdsj.imp.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.status.client.WrapperStatusClientPing;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerPong;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.JsonObject;
import io.wdsj.imp.Imp;
import io.wdsj.imp.config.Config;
import io.wdsj.imp.impl.player.Player;

public class ServerListPingListener implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = event.getUser();
        Player player = (Player) event.getPlayer();
        if (event.getPacketType() == PacketType.Status.Client.REQUEST) {
            //This is invoked when the client opens up or refreshes the multiplayer server list menu.
            JsonObject responseComponent = new JsonObject();

            JsonObject versionComponent = new JsonObject();
            versionComponent.addProperty("name", Imp.SERVER_VERSION_NAME);
            versionComponent.addProperty("protocol", Imp.SERVER_PROTOCOL_VERSION);
            //Add sub component
            responseComponent.add("version", versionComponent);

            JsonObject playersComponent = new JsonObject();
            playersComponent.addProperty("max", Imp.config.getProperty(Config.MAX_PLAYERS));
            playersComponent.addProperty("online", Imp.PLAYERS.size());
            //Add sub component
            responseComponent.add("players", playersComponent);

            JsonObject descriptionComponent = new JsonObject();
            descriptionComponent.addProperty("text", Imp.config.getProperty(Config.SERVER_DESCRIPTION));
            //Add sub component
            responseComponent.add("description", descriptionComponent);
            //We respond by sending them information about the server.
            WrapperStatusServerResponse response = new WrapperStatusServerResponse(responseComponent.toString());
            player.sendPacket(response);
        } else if (event.getPacketType() == PacketType.Status.Client.PING) {
            //The client sends us this to inform us they successfully received our response with data about the server.
            //We just respond by sending them the same packet.
            WrapperStatusClientPing ping = new WrapperStatusClientPing(event);
            long time = ping.getTime();
            WrapperStatusServerPong pong = new WrapperStatusServerPong(time);
            player.sendPacket(pong);
            user.closeConnection();
        }
    }
}
