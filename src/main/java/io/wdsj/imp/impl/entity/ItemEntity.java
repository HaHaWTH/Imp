package io.wdsj.imp.impl.entity;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.wdsj.imp.Imp;
import io.wdsj.imp.impl.player.Player;
import io.wdsj.imp.impl.util.AdvancedList;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

public class ItemEntity {
    public static double PICKUP_DISTANCE = 2;
    private final int entityId;
    private final UUID uuid;
    private Vector3d position;
    private ItemStack item;
    private long lastSpawnTime;

    public ItemEntity(Vector3d position, ItemStack item) {
        this.entityId = Imp.ENTITIES++;
        this.uuid = UUID.randomUUID();
        this.position = position;
        this.item = item;
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getUUID() {
        return uuid;
    }

    public Vector3d getPosition() {
        return position;
    }

    public void setPosition(Vector3d position) {
        this.position = position;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public void pickup(Player player, Queue<Player> target) {
        if ((System.currentTimeMillis() - lastSpawnTime) >= 1000L) {
            for (Player p : target) {
                WrapperPlayServerCollectItem collectItem = new WrapperPlayServerCollectItem(entityId, p.getEntityId(), getItem().getAmount());
                WrapperPlayServerDestroyEntities destroyEntities = new WrapperPlayServerDestroyEntities(getEntityId());
                p.sendPacket(destroyEntities);
                p.sendPacket(collectItem);
            }
            Imp.ITEM_ENTITIES.remove(this);
            ItemStack currentItem = player.getCurrentItem();
            if (currentItem == null) {
                player.setCurrentItem(getItem());
            }
            else {
                currentItem.grow(getItem().getAmount());
            }
            player.updateHotbar();
        }
    }

    public void spawn(Player spawner, Queue<Player> target) {
        Vector3d pos = spawner.getEntityInformation().getLocation().getPosition();
        pos.add(new Vector3d(0, 4, 0));
        for (Player player : target) {
            WrapperPlayServerSpawnEntity spawnEntity = new WrapperPlayServerSpawnEntity(entityId,
                    Optional.of(UUID.randomUUID()),
                    EntityTypes.ITEM,
                    pos,
                    0, 0,
                    1, 0,
                    Optional.of(new Vector3d(0, -4, 0)));
            /*List<EntityData> data = EntityDataProvider.builderEntity()
                    .customName(Component.text("nice item").color(NamedTextColor.GOLD).asComponent())
                    .customNameVisible(true).build().encode();
            data.add(new EntityData(8, EntityDataTypes.ITEMSTACK, item));*/
            List<EntityData> data = new AdvancedList<>();
            WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata(entityId, data);
            WrapperPlayServerAttachEntity attachEntity = new WrapperPlayServerAttachEntity(spawner.getEntityId(), entityId, false);
            player.writePacket(spawnEntity);
            player.writePacket(metadata);
            player.sendPacket(attachEntity);
        }
        Imp.ITEM_ENTITIES.add(this);
        lastSpawnTime = System.currentTimeMillis();
    }
}
