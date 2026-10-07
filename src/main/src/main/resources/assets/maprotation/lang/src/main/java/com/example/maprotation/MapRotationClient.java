package com.example.maprotation;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MapRotationClient implements ClientModInitializer {

    private KeyMapping toggleKey;

    private boolean running = false;
    private final List<double[]> waypoints = new ArrayList<>();
    private int index = 0;
    private boolean loop = true;

    private double stuckX, stuckZ;
    private int stuckTicks = 0;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.maprotation.toggle", InputConstants.KEY_O, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            if (running) {
                stop(client, "Stopped");
            } else {
                start(client);
            }
        }
        if (!running) {
            return;
        }

        LocalPlayer p = client.player;
        if (p == null || client.level == null) {
            running = false;
            return;
        }
        if (client.screen != null) {
            stop(client, "Stopped (screen opened)");
            return;
        }
        if (p.hurtTime > 0) {
            stop(client, "Stopped (took damage)");
            return;
        }

        double[] target = waypoints.get(index);
        double dx = target[0] - p.getX();
        double dz = target[1] - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 2.0) {
            index++;
            if (index >= waypoints.size()) {
                if (loop) {
                    index = 0;
                } else {
                    stop(client, "Finished");
                    return;
                }
            }
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.setYRot(yaw);
        p.setXRot(0.0f);

        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);
        client.options.keyJump.setDown(p.horizontalCollision && p.onGround());

        client.player.displayClientMessage(Component.literal(
                "Map Rotation: point " + (index + 1) + "/" + waypoints.size()
                        + "  (" + (int) dist + " blocks left)"), true);

        if (++stuckTicks >= 100) {
            double moved = Math.hypot(p.getX() - stuckX, p.getZ() - stuckZ);
            if (moved < 1.0) {
                stop(client, "Stopped (stuck)");
                return;
            }
            stuckX = p.getX();
            stuckZ = p.getZ();
            stuckTicks = 0;
        }
    }

    private void start(Minecraft client) {
        LocalPlayer p = client.player;
        if (p == null) {
            return;
        }
        Settings s = Settings.load();
        waypoints.clear();
        waypoints.addAll(s.buildWaypoints());
        loop = s.loop;
        if (waypoints.isEmpty()) {
            return;
        }

        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < waypoints.size(); i++) {
            double[] w = waypoints.get(i);
            double d = Math.hypot(w[0] - p.getX(), w[1] - p.getZ());
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        index = best;
        stuckX = p.getX();
        stuckZ = p.getZ();
        stuckTicks = 0;
        running = true;
        p.displayClientMessage(Component.literal("Map Rotation: started (" + s.mode + ")"), true);
    }

    private void stop(Minecraft client, String message) {
        running = false;
        client.options.keyUp.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyJump.setDown(false);
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("Map Rotation: " + message), true);
        }
    }

    private static final class Settings {
        int minX = -1000, maxX = 1000, minZ = -1000, maxZ = 1000;
        int step = 100;
        int inset = 20;
        String mode = "sweep";
        boolean loop = true;

        static Settings load() {
            Settings s = new Settings();
            Path file = FabricLoader.getInstance().getConfigDir().resolve("maprotation.properties");
            Properties props = new Properties();
            try {
                if (Files.exists(file)) {
                    try (Reader r = Files.newBufferedReader(file)) {
                        props.load(r);
                    }
                } else {
                    props.setProperty("minX", String.valueOf(s.minX));
                    props.setProperty("maxX", String.valueOf(s.maxX));
                    props.setProperty("minZ", String.valueOf(s.minZ));
                    props.setProperty("maxZ", String.valueOf(s.maxZ));
                    props.setProperty("step", String.valueOf(s.step));
                    props.setProperty("inset", String.valueOf(s.inset));
                    props.setProperty("mode", s.mode);
                    props.setProperty("loop", String.valueOf(s.loop));
                    try (Writer w = Files.newBufferedWriter(file)) {
                        props.store(w, "Map Rotation: mode = sweep or perimeter");
                    }
                }
            } catch (IOException ignored) {
            }
            s.minX = readInt(props, "minX", s.minX);
            s.maxX = readInt(props, "maxX", s.maxX);
            s.minZ = readInt(props, "minZ", s.minZ);
            s.maxZ = readInt(props, "maxZ", s.maxZ);
            s.step = Math.max(16, readInt(props, "step", s.step));
            s.inset = Math.max(0, readInt(props, "inset", s.inset));
            s.mode = props.getProperty("mode", s.mode).trim().toLowerCase();
            s.loop = Boolean.parseBoolean(props.getProperty("loop", String.valueOf(s.loop)).trim());
            return s;
        }

        private static int readInt(Properties p, String key, int def) {
            try {
                return Integer.parseInt(p.getProperty(key, String.valueOf(def)).trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        List<double[]> buildWaypoints() {
            List<double[]> pts = new ArrayList<>();
            if (mode.equals("sweep")) {
                boolean forward = true;
                for (int z = minZ; z <= maxZ; z += step) {
                    double a = forward ? minX : maxX;
                    double b = forward ? maxX : minX;
                    pts.add(new double[]{a, z});
                    pts.add(new double[]{b, z});
                    forward = !forward;
                }
            } else {
                double x1 = minX + inset, x2 = maxX - inset;
                double z1 = minZ + inset, z2 = maxZ - inset;
                pts.add(new double[]{x1, z1});
                pts.add(new double[]{x2, z1});
                pts.add(new double[]{x2, z2});
                pts.add(new double[]{x1, z2});
            }
            return pts;
        }
    }
          }
