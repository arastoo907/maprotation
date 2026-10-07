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
    private KeyMapping recordKey;

    private boolean running = false;
    private boolean recording = false;

    private final List<double[]> waypoints = new ArrayList<>();
    private final List<double[]> recorded = new ArrayList<>();
    private int index = 0;
    private boolean loop = true;
    private boolean pathMode = false;

    private double lastRecX, lastRecZ;
    private double stu
