package io.wdsj.imp.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import io.wdsj.imp.impl.player.Player;
import io.wdsj.imp.impl.util.entity.UpdateType;

public class KeepAliveListener implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            if (player.getSendKeepAliveTime() != 0L) {
                long now = System.currentTimeMillis();
                long ping = now - player.getSendKeepAliveTime();
                player.setLatency(ping);
                player.getEntityInformation().queueUpdate(UpdateType.LATENCY);
                player.setLastKeepAliveTime(now);
            }
        }

    }
}
