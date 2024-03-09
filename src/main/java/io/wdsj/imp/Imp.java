package io.wdsj.imp;

import ch.jalu.configme.SettingsManager;
import ch.jalu.configme.SettingsManagerBuilder;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import io.github.retrooper.packetevents.impl.netty.BuildData;
import io.github.retrooper.packetevents.impl.netty.factory.NettyPacketEventsBuilder;
import io.github.retrooper.packetevents.impl.netty.manager.player.PlayerManagerAbstract;
import io.github.retrooper.packetevents.impl.netty.manager.protocol.ProtocolManagerAbstract;
import io.github.retrooper.packetevents.impl.netty.manager.server.ServerManagerAbstract;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.wdsj.imp.config.Config;
import io.wdsj.imp.handler.PacketDecoder;
import io.wdsj.imp.handler.PacketEncoder;
import io.wdsj.imp.handler.PacketFormatter;
import io.wdsj.imp.handler.PacketSplitter;
import io.wdsj.imp.impl.entity.ItemEntity;
import io.wdsj.imp.impl.player.Player;
import io.wdsj.imp.impl.world.World;
import io.wdsj.imp.injector.ChannelInjectorImpl;
import io.wdsj.imp.listener.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class Imp extends JavaPlugin {
    private static TaskScheduler scheduler;
    private static Imp instance;
    public static MyScheduledTask TASK;
    public static SettingsManager config;
    private final File CONFIG_FILE = new File(getDataFolder(), "server.yml");
    public static Imp getInstance() {
        return instance;
    }

    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    public static boolean shouldTick = true;
    public static volatile boolean shouldRunKeepAliveLoop = true;
    public static String SERVER_VERSION_NAME;
    public static int SERVER_PROTOCOL_VERSION;
    public static final Logger LOGGER = Bukkit.getLogger();
    public static final KeyPair KEY_PAIR = generateKeyPair();
    public static final ExecutorService WORKER_THREADS = Executors.newFixedThreadPool(2);
    public static final Queue<Player> PLAYERS = new ConcurrentLinkedQueue<>();
    public static long totalTicks = 0L;
    public static int ENTITIES = 0;
    public static final Queue<ItemEntity> ITEM_ENTITIES = new ConcurrentLinkedQueue<>();
    public static final World MAIN_WORLD = new io.wdsj.imp.impl.world.World();
    public static final Set<Channel> SERVER_CHANNELS = new HashSet<>();

    @Override
    public void onLoad() {
        instance = this;
        scheduler = UniversalScheduler.getScheduler(this);
        config = SettingsManagerBuilder
                .withYamlFile(CONFIG_FILE)
                .configurationData(Config.class)
                .useDefaultMigrationService()
                .create();
        BuildData data = new BuildData("sis");
        ChannelInjector injector = new ChannelInjectorImpl();

        ServerManagerAbstract serverManager = new ServerManagerAbstract() {
            @Override
            public ServerVersion getVersion() {
                return ServerVersion.V_1_12_2;
            }
        };

        PlayerManagerAbstract playerManager = new PlayerManagerAbstract() {
            @Override
            public int getPing(@NotNull Object player) {
                return (int) ((Player) player).getLatency();
            }

            @Override
            public Object getChannel(@NotNull Object player) {
                return ((Player) player).getChannel();
            }
        };

        //TODO protocol manager
        ProtocolManagerAbstract protocolManager = new ProtocolManagerAbstract() {
            @Override
            public ProtocolVersion getPlatformVersion() {
                return ProtocolVersion.UNKNOWN;
            }
        };
        PacketEvents.setAPI(NettyPacketEventsBuilder.build(data, injector,
                protocolManager,
                serverManager, playerManager));
        PacketEvents.getAPI().getSettings().debug(true).checkForUpdates(false).bStats(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().getEventManager()
                .registerListener(new ServerListPingListener(), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().getEventManager()
                .registerListener(new LoginListener(config.getProperty(Config.ONLINE_MODE)), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().getEventManager()
                .registerListener(new KeepAliveListener(), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().getEventManager()
                .registerListener(new EntityHandler(), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().getEventManager()
                .registerListener(new InputListener(), PacketListenerPriority.LOWEST);
        PacketEvents.getAPI().init();
        SERVER_VERSION_NAME = PacketEvents.getAPI().getServerManager().getVersion().getReleaseName();
        SERVER_PROTOCOL_VERSION = PacketEvents.getAPI().getServerManager().getVersion().getProtocolVersion();
        this.LOGGER.info("Starting Server. Version: " + SERVER_VERSION_NAME + ". Online mode: " + config.getProperty(Config.ONLINE_MODE));

        this.LOGGER.info("Preparing chunks...");
        MAIN_WORLD.generateChunkRectangle(1, 1);
        this.LOGGER.info("Binding to port... " + config.getProperty(Config.PORT));
        EventLoopGroup bossGroup;
        EventLoopGroup workerGroup;
        int workerThreads = Runtime.getRuntime().availableProcessors();
        if (Epoll.isAvailable()) {
            bossGroup = new EpollEventLoopGroup(1);
            workerGroup = new EpollEventLoopGroup(workerThreads);
        } else {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup(workerThreads);
        }
        TASK = getScheduler().runTaskAsynchronously(() -> {
            try {
                ServerBootstrap b = new ServerBootstrap();
                b.group(bossGroup, workerGroup)
                        .channel(Epoll.isAvailable()
                                ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @SuppressWarnings("RedundantThrows")
                            @Override
                            public void initChannel(@NotNull SocketChannel channel) throws Exception {
                                User user = new User(channel, ConnectionState.HANDSHAKING, null, new UserProfile(null, null));
                                ProtocolManager.USERS.put(channel, user);
                                Player player = new Player(user);
                                PacketDecoder decoder = new PacketDecoder(user, player);
                                PacketEncoder encoder = new PacketEncoder(user, player);
                                channel.pipeline()
                                        .addLast("decryption_handler", new ChannelHandlerAdapter() {
                                        })
                                        .addLast("packet_splitter", new PacketSplitter())
                                        .addLast(PacketEvents.DECODER_NAME, decoder)
                                        .addLast("encryption_handler", new ChannelHandlerAdapter() {
                                        })
                                        .addLast("packet_formatter", new PacketFormatter())
                                        .addLast(PacketEvents.ENCODER_NAME, encoder);
                            }
                        })
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);

                WORKER_THREADS.execute(this::runKeepAliveLoop);

                // Bind and start to accept incoming connections.
                ChannelFutureListener listener = future -> SERVER_CHANNELS.add(future.channel());
                ChannelFuture f = b.bind(config.getProperty(Config.ADDRESS),config.getProperty(Config.PORT)).addListener(listener);

                getScheduler().runTask( () -> this.LOGGER.info("(" + (Runtime.getRuntime().availableProcessors()) + " worker threads)"));
                this.runTickLoop();
            } finally {
                workerGroup.shutdownGracefully();
                bossGroup.shutdownGracefully();
            }
        });
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        TASK.cancel();
    }

    public static void closeServer() {
        for (Channel serverChannel : SERVER_CHANNELS) {
            try {
                serverChannel.closeFuture().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void processInput(String input) {
        if (input.equalsIgnoreCase("close")) {
            closeServer();
        }
        System.out.println("Got input: " + input);
    }

    public static void runTickLoop() {
        //1000ms / 50ms = 20 ticks
        //Scanner scanner = new Scanner(System.in);
        //String input = scanner.nextLine();
        //processInput(input);
        while (shouldTick) {
            totalTicks += 1;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            EntityHandler.onTick();
        }
    }

    public void runKeepAliveLoop() {
        while (shouldRunKeepAliveLoop) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (Player player : PLAYERS) {
                if ((System.currentTimeMillis() - player.getKeepAliveTimer()) > 3000L) {
                    long elapsedTime = System.currentTimeMillis() - player.getLastKeepAliveTime();

                    if (elapsedTime > 30000L) {
                        player.kick("Timed out.");
                        LOGGER.info(player.getUsername() + " was kicked for not responding to keep alives!");
                        break;
                    }

                    WrapperPlayServerKeepAlive keepAlive = new WrapperPlayServerKeepAlive((long) Math.floor(Math.random() * Integer.MAX_VALUE));
                    player.sendPacket(keepAlive);

                    player.setKeepAliveTimer(System.currentTimeMillis());
                    player.setSendKeepAliveTime(System.currentTimeMillis());
                }
            }
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(1024);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
            // If an error is thrown then shutdown because we
            // literally can't start the server without it, also
            // stops IntelliJ from asking to assert not null on keys.
            LOGGER.severe("Failed to generate RSA-1024 key, cannot start server!");
            return null;
        }
    }

}
