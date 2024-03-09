package io.wdsj.imp.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.wdsj.imp.Imp;
import io.wdsj.imp.impl.player.Player;
import io.wdsj.imp.impl.util.entity.ClientSettings;
import io.wdsj.imp.impl.util.entity.EntityInformation;
import io.wdsj.imp.impl.util.entity.UpdateType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class EntityHandler implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = event.getUser();
        Player player = (Player) event.getPlayer();
        assert player != null;
        EntityInformation entityInformation = player.getEntityInformation();
        if (user.getConnectionState() == ConnectionState.PLAY) {
            if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
                System.out.println("Detected!");
                WrapperPlayClientPlayerBlockPlacement blockPlacement = new WrapperPlayClientPlayerBlockPlacement(event);
                entityInformation.setLastBlockActionPosition(blockPlacement.getBlockPosition());
                entityInformation.setLastBlockActionData(StateTypes.GOLD_BLOCK.createBlockState(PacketEvents.getAPI()
                        .getServerManager().getVersion().toClientVersion()));
                entityInformation.queueUpdate(UpdateType.BLOCK_PLACE);
            } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
                WrapperPlayClientPlayerDigging playerDigging = new WrapperPlayClientPlayerDigging(event);
                if (playerDigging.getAction() == DiggingAction.FINISHED_DIGGING) {
                    entityInformation.setLastBlockActionPosition(playerDigging.getBlockPosition());
                    entityInformation.queueUpdate(UpdateType.BLOCK_DIG);
                }
            } else if (event.getPacketType() == PacketType.Play.Client.CLIENT_SETTINGS) {
                player.setClientSettings(new ClientSettings(new WrapperPlayClientSettings(event)));
                entityInformation.queueUpdate(UpdateType.METADATA);
            } else if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
                WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
                Location location = flying.getLocation();
                if (flying.hasPositionChanged() && flying.hasRotationChanged()) {
                    entityInformation.setLocation(location);
                    entityInformation.queueUpdate(UpdateType.POSITION_ANGLE);
                } else if (flying.hasPositionChanged()) {
                    entityInformation.setLocation(new Location(location.getPosition(), entityInformation.getLocation().getYaw(), entityInformation.getLocation().getPitch()));
                    //entityInformation.getLocation().setPosition(location.getPosition());
                    entityInformation.queueUpdate(UpdateType.POSITION);
                } else if (flying.hasRotationChanged()) {
                    entityInformation.getLocation().setYaw(location.getYaw());
                    entityInformation.getLocation().setPitch(location.getPitch());
                    entityInformation.queueUpdate(UpdateType.ANGLE);
                }
                entityInformation.setOnGround(flying.isOnGround());
                entityInformation.queueUpdate(UpdateType.GROUND);
            } else if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
                WrapperPlayClientEntityAction entityActionWrapper = new WrapperPlayClientEntityAction(event);
                if (entityActionWrapper.getEntityId() == player.getEntityId()) {
                    switch (entityActionWrapper.getAction()) {
                        case START_SNEAKING:
                            entityInformation.setSneaking(true);
                            break;
                        case STOP_SNEAKING:
                            entityInformation.setSneaking(false);
                            break;
                        case START_SPRINTING:
                            entityInformation.setSprinting(true);
                            break;
                        case STOP_SPRINTING:
                            entityInformation.setSprinting(false);
                            break;
                    }
                    entityInformation.queueUpdate(UpdateType.METADATA);
                }
            } else if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
                for (Player lPlayer : Imp.PLAYERS) {
                    if (lPlayer.getEntityId() != player.getEntityId()) {
                        WrapperPlayClientAnimation animationWrapper = new WrapperPlayClientAnimation(event);
                        WrapperPlayServerEntityAnimation.EntityAnimationType animation = animationWrapper.getHand() == InteractionHand.MAIN_HAND ? WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM : WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_OFF_HAND;
                        WrapperPlayServerEntityAnimation entityAnimationWrapper = new WrapperPlayServerEntityAnimation(player.getEntityId(), animation);
                        lPlayer.sendPacket(entityAnimationWrapper);
                    }
                }
            }
        }
    }

    public static void onTick() {
        for (Player player : Imp.PLAYERS) {
            EntityInformation entityInformation = player.getEntityInformation();
            Queue<UpdateType> queuedUpdates = entityInformation.getQueuedUpdates();
            if (!queuedUpdates.isEmpty()) {
                List<PacketWrapper<?>> packetQueue = new ArrayList<>();
                Location currentLocation = entityInformation.getLocation();
                for (UpdateType updateType : queuedUpdates) {
                    switch (updateType) {
                        case POSITION: {
                            Vector3d deltaPosition = currentLocation.getPosition().subtract(entityInformation.getTickLocation().getPosition());
                            if (!deltaPosition.equals(Vector3d.zero())) {
                                if (shouldSendEntityTeleport(deltaPosition)) {
                                    WrapperPlayServerEntityTeleport teleport =
                                            new WrapperPlayServerEntityTeleport(player.getEntityId(),
                                                    currentLocation, player.getEntityInformation().isOnGround());
                                    packetQueue.add(teleport);
                                } else {
                                    double deltaX = deltaPosition.getX();
                                    double deltaY = deltaPosition.getY();
                                    double deltaZ = deltaPosition.getZ();
                                    System.out.println("dx: " + deltaX);

                                    packetQueue.add(new WrapperPlayServerEntityRelativeMove(player.getEntityId(),
                                            deltaX, deltaY, deltaZ, entityInformation.isOnGround()));

                                }

                            }
                            break;
                        }
                        case POSITION_ANGLE: {
                            Vector3d deltaPosition = currentLocation.getPosition().subtract(entityInformation.getTickLocation().getPosition());
                            //System.out.println("dmA: " + deltaPosition.toString());
                            if (shouldSendEntityTeleport(deltaPosition)) {
                                WrapperPlayServerEntityTeleport teleport =
                                        new WrapperPlayServerEntityTeleport(player.getEntityId(),
                                                currentLocation, player.getEntityInformation().isOnGround());
                                packetQueue.add(teleport);
                            } else {
                                double deltaX = deltaPosition.getX();
                                double deltaY = deltaPosition.getY();
                                double deltaZ = deltaPosition.getZ();

                                packetQueue.add(new WrapperPlayServerEntityRelativeMoveAndRotation(player.getEntityId(),
                                        deltaX, deltaY, deltaZ, (byte) currentLocation.getYaw(),
                                        (byte) currentLocation.getPitch(), entityInformation.isOnGround()));


                                byte headYaw = (byte) ((int) (currentLocation.getYaw() * 256.0F / 360.0F));
                                packetQueue.add(new WrapperPlayServerEntityHeadLook(player.getEntityId(), headYaw));
                            }

                            break;
                        }
                        case ANGLE:
                            packetQueue.add(new WrapperPlayServerEntityRotation(player.getEntityId(),
                                    (byte) currentLocation.getYaw(), (byte) currentLocation.getPitch(), entityInformation.isOnGround()));
                            byte headYaw = (byte) ((int) (currentLocation.getYaw() * 256.0F / 360.0F));
                            packetQueue.add(new WrapperPlayServerEntityHeadLook(player.getEntityId(), headYaw));

                            break;
                        case METADATA:
                            packetQueue.add(getEntityMetadata(player.getEntityId(), player));
                            break;
                        case LATENCY:
                            List<WrapperPlayServerPlayerInfo.PlayerData> playerDataList = new ArrayList<>();

                            playerDataList.add(new WrapperPlayServerPlayerInfo.PlayerData(null, player.getUserProfile(), null, (int) player.getLatency()));

                            packetQueue.add(new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.UPDATE_LATENCY, playerDataList));

                            break;

                        case BLOCK_DIG:
                            //Set to air
                            if (entityInformation.getLastBlockActionPosition() != null) {
                                WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange(entityInformation.getLastBlockActionPosition(), 0);
                                packetQueue.add(blockChange);
                                //Update the chunk cache(set to air)
                                Imp.MAIN_WORLD.setBlockStateAt(blockChange.getBlockPosition(),
                                        WrappedBlockState.getByGlobalId(PacketEvents.getAPI().getServerManager()
                                                .getVersion().toClientVersion(), 0));
                            }
                            break;

                        case BLOCK_PLACE:
                            if (entityInformation.getLastBlockActionPosition() != null) {
                                WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange(
                                        entityInformation.getLastBlockActionPosition(),
                                        entityInformation.getLastBlockActionData().getGlobalId());

                                //Update the chunk cache(set to cobble stone always for now) TODO get block in hand
                                Imp.MAIN_WORLD.setBlockStateAt(blockChange.getBlockPosition(),
                                        blockChange.getBlockState());
                                System.out.println("Block: " + Imp.MAIN_WORLD.getBlockStateAt(blockChange.getBlockPosition()) + ", above: " + Imp.MAIN_WORLD.getBlockStateAt(blockChange.getBlockPosition().add(0, 1, 0)));

                                packetQueue.add(blockChange);
                            }
                    }

                    for (Player lPlayer : Imp.PLAYERS) {
                        if (player.getEntityId() != lPlayer.getEntityId()) {
                            for (PacketWrapper<?> wrapper : packetQueue) {
                                lPlayer.sendPacket(wrapper);
                            }
                        }
                    }
                }

                if (queuedUpdates.contains(UpdateType.POSITION) || queuedUpdates.contains(UpdateType.POSITION_ANGLE)) {
                    //entityInformation.setTickLocation(new Location(0, 0, 0, 0, 0));
                    entityInformation.setTickLocation(entityInformation.getLocation());
                }
                entityInformation.resetQueuedUpdates();
            }
        }
    }

    public static void onLogin(Player player) {
        for (Player p : Imp.PLAYERS) {
            if (player.getEntityId() == p.getEntityId()) {
                //Spawn us for others.
                spawnPlayer(player, Imp.PLAYERS, 2);
            } else {
                //Spawn the others for us.
                //As an optimization, we set the users to have one player.
                Queue<Player> players = new LinkedList<>();
                players.add(player);
                spawnPlayer(p, players, 1);
            }
        }
    }

    public static void spawnPlayer(Player player, Queue<Player> onlinePlayers, int minToOperate) {
        if (onlinePlayers.size() >= minToOperate) {
            for (Player onlinePlayer : onlinePlayers) {
                if (onlinePlayer.getEntityId() != player.getEntityId()) {
                    Location location = onlinePlayer.getEntityInformation().getLocation();
                    Location newLocation = new Location(location.getPosition(), location.getYaw(), location.getPitch());

                    WrapperPlayServerSpawnPlayer spawnPlayer = new WrapperPlayServerSpawnPlayer(onlinePlayer.getEntityId(), onlinePlayer.getUserProfile().getUUID(), newLocation);
                    player.writePacket(spawnPlayer);

                    WrapperPlayServerEntityMetadata metadata = getEntityMetadata(player.getEntityId(), player);
                    player.writePacket(metadata);
                    onlinePlayer.writePacket(metadata);

                    List<Equipment> equipment = new ArrayList<>();
                    ItemStack item = player.inventory[0];
                    if (item == null) {
                        item = ItemStack.builder().type(ItemTypes.AIR).amount(1).build();
                    }
                    //Allow single item constructor
                    equipment.add(new Equipment(EquipmentSlot.MAIN_HAND, item));
                    //Show them what we have
                    WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(player.getEntityId(), equipment);
                    onlinePlayer.writePacket(equipmentPacket);
                    //Show us what they have
                    WrapperPlayServerEntityEquipment equipmentPacket2 = new WrapperPlayServerEntityEquipment(onlinePlayer.getEntityId(), equipment);
                    player.sendPacket(equipmentPacket2);
                }
            }
        }
    }

    public static float wrapAngleTo180(float num) {
        float val = num % 360;

        if (val >= 180) {
            val -= 360;
        } else if (val < -180) {
            val += 360;
        }

        return val;
    }

    public static boolean shouldSendEntityTeleport(Vector3d deltaMovement) {
        //return Math.abs(deltaMovement.getX()) > 8 || Math.abs(deltaMovement.getY()) > 8 || Math.abs(deltaMovement.getZ()) > 8;
        return true;
    }

    public static WrapperPlayServerEntityTeleport getEntityTeleport(Player player) {
        EntityInformation entityInformation = player.getEntityInformation();
        Location location = entityInformation.getLocation();
        boolean onGround = entityInformation.isOnGround();

        return new WrapperPlayServerEntityTeleport(player.getEntityId(), location, onGround);
    }

    public static WrapperPlayServerEntityMetadata getEntityMetadata(int targetEntityId, Player player) {
        EntityInformation entityInformation = player.getEntityInformation();
        ClientSettings clientSettings = player.getClientSettings();

        EntityPose entityPose = entityInformation.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING;

        /*EntityDataProvider playerDataProvider =
                PlayerDataProvider.builderPlayer()
                        .mainArm(HumanoidArm.RIGHT)
                        .skinParts(clientSettings.getVisibleSkinSections())
                        .handActive(true)
                        .hand(InteractionHand.MAIN_HAND)
                        .crouching(entityInformation.isSneaking())
                        .sprinting(entityInformation.isSprinting())
                        .onFire(entityInformation.isOnFire())
                        .hasGravity(true)
                        .invisible(entityInformation.isInvisible())
                        .pose(entityPose).build();
        return new WrapperPlayServerEntityMetadata(targetEntityId, playerDataProvider.encode());*/
        //TODO 处理元数据
        return new WrapperPlayServerEntityMetadata(targetEntityId, new ArrayList<>());
    }

}