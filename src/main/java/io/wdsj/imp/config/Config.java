package io.wdsj.imp.config;

import ch.jalu.configme.SettingsHolder;
import ch.jalu.configme.properties.Property;

import static ch.jalu.configme.properties.PropertyInitializer.newProperty;

public class Config implements SettingsHolder {
    private Config() {}

    public static final Property<Boolean> ONLINE_MODE = newProperty("online-mode", false);
    public static final Property<Boolean> SHOULD_RUN_KEEP_ALIVE_LOOP = newProperty("keep-alive-loop", true);
    public static final Property<Integer> MAX_PLAYERS = newProperty("max-players", 1000);
    public static final Property<Integer> PORT = newProperty("port", 23336);
    public static final Property<String> ADDRESS = newProperty("address", "0.0.0.0");
    public static final Property<String> SERVER_DESCRIPTION = newProperty("server-description", "Imp Server");
}
