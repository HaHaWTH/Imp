package io.wdsj.imp.listener;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgePlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange;
import io.wdsj.imp.Imp;
import io.wdsj.imp.impl.entity.ItemEntity;
import io.wdsj.imp.impl.player.Player;
import io.wdsj.imp.impl.util.ServerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InputListener implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        Player player = (Player) event.getPlayer();
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            Vector3d position = player.getEntityInformation().getLocation().getPosition();
            double shortestDistance = ItemEntity.PICKUP_DISTANCE;
            ItemEntity shortestItemEntity = null;
            for (ItemEntity itemEntity : Imp.ITEM_ENTITIES) {
                double dist = itemEntity.getPosition().distance(position);
                if (dist <= shortestDistance) {
                    shortestDistance = dist;
                    shortestItemEntity = itemEntity;
                    if (dist == ItemEntity.PICKUP_DISTANCE) {
                        break;
                    }
                }
            }
            if (shortestItemEntity != null) {
                //Pick it up for them(also destroy)
                shortestItemEntity.pickup(player, Imp.PLAYERS);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {
            WrapperPlayClientChatMessage chatMessage = new WrapperPlayClientChatMessage(event);
            String msg = chatMessage.getMessage();
            Imp.LOGGER.info(player.getUsername() + ": " + msg);
            //Prefix the display message with the player's name in green, and then a colon and their message in white
            Component displayComponent = Component.text(player.getUsername()).color(NamedTextColor.GREEN)
                    .append(Component.text(": " + msg).color(NamedTextColor.WHITE).asComponent()).asComponent();
            //Send it to everyone(including the sender)
            ServerUtil.broadcastMessage(displayComponent);
        } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            WrapperPlayClientHeldItemChange heldItemChange = new WrapperPlayClientHeldItemChange(event);
            int slot = heldItemChange.getSlot();
            player.currentSlot = slot;
            @Nullable ItemStack item = player.getHotbarIndex(slot);

            for (Player p : Imp.PLAYERS) {
                if (player.getEntityId() != p.getEntityId()) {
                    List<Equipment> equipment = new ArrayList<>();
                    if (item == null) {
                        item = ItemStack.builder().type(ItemTypes.AIR).amount(64).build();
                    }
                    equipment.add(new Equipment(EquipmentSlot.MAIN_HAND, item));
                    WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(player.getEntityId(), equipment);
                    p.sendPacket(equipmentPacket);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = digging.getAction();
            if (action == DiggingAction.DROP_ITEM ||
                    action == DiggingAction.DROP_ITEM_STACK) {
                ItemStack item = player.getCurrentItem();
                //Remove debug
                if (item != null) {
                    int newAmount = action == DiggingAction.DROP_ITEM ? 1 : item.getAmount();
                    ItemStack entity = item.copy();
                    entity.setAmount(newAmount);
                    if (item.isEmpty()) {
                        player.setCurrentItem(null);
                    } else {
                        player.getCurrentItem().shrink(newAmount);
                    }
                    player.updateHotbar();
                    ItemEntity itemEntity = new ItemEntity(player.getEntityInformation().getLocation().getPosition(),
                            entity);
                    itemEntity.spawn(player, Imp.PLAYERS);
                }
            } else if (action == DiggingAction.FINISHED_DIGGING) {
                //Send this always if in creative
                int blockID = 0;
                WrapperPlayServerAcknowledgePlayerDigging diggingResponse =
                        new WrapperPlayServerAcknowledgePlayerDigging(action, true, digging.getBlockPosition(), blockID);
                player.sendPacket(diggingResponse);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interactEntity = new WrapperPlayClientInteractEntity(event);
            WrapperPlayClientInteractEntity.InteractAction action = interactEntity.getAction();
            if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                int targetEntityId = interactEntity.getEntityId();
                Player targetPlayer = null;
                for (Player p : Imp.PLAYERS) {
                    if (p.getEntityId() == targetEntityId) {
                        targetPlayer = p;
                        break;
                    }
                }
                if (targetPlayer != null) {
                    WrapperPlayServerEntityAnimation animation = new WrapperPlayServerEntityAnimation(targetEntityId,
                            WrapperPlayServerEntityAnimation.EntityAnimationType.HURT);
                    //暴击!!!!!
                    WrapperPlayServerEntityAnimation animation2 = new WrapperPlayServerEntityAnimation(targetEntityId,
                            WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_HIT);
                    //TODO Velocity calc
                    //TODO Health process
                    player.sendPacket(animation);
                    player.sendPacket(animation2);
                }
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player player = (Player) event.getPlayer();
        if (event.getPacketType() == PacketType.Play.Server.HELD_ITEM_CHANGE) {
            WrapperPlayServerHeldItemChange heldItemChange = new WrapperPlayServerHeldItemChange(event);
            int slot = heldItemChange.getSlot();
            player.currentSlot = slot;
            @Nullable ItemStack item = player.getHotbarIndex(slot);
            for (Player p : Imp.PLAYERS) {
                if (p.getEntityId() != player.getEntityId()) {
                    List<Equipment> equipment = new ArrayList<>();
                    if (item == null) {
                        item = ItemStack.builder().type(ItemTypes.AIR).amount(64).build();
                    }
                    equipment.add(new Equipment(EquipmentSlot.MAIN_HAND, item));
                    WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(player.getEntityId(), equipment);
                    p.sendPacket(equipmentPacket);
                }
            }
        }
    }
}
