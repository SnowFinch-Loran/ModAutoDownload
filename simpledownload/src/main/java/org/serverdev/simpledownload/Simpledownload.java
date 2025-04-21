package org.serverdev.simpledownload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Type;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.FMLHandshakeHandler;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.network.NetworkRegistry;


import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static org.serverdev.simpledownload.Simpledownload.RegistryEvents.ForgeEvents.loadUrlsFromJson;

@Mod("simpledownload")
public class Simpledownload {

    public static boolean logConnections = true;
    public static boolean logBans = true;

    public static final Map<Integer, String> urlMap = new HashMap<>();
    private static final Logger LOGGER = LogManager.getLogger();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type URL_MAP_TYPE = new TypeToken<Map<Integer, String>>() {}.getType();
    private static final File configDir = new File("mods/SDSetup");
    private static final File configFile = new File(configDir, "Settings.json");

    public Simpledownload() {
        startHttpServer();
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void startHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
            server.createContext("/getDurlData", new GetDurlDataHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("§b[Simpledownload]§a[Event]§l>Server started on port 8000");
            System.out.println("§b[Simpledownload]§a[Event]§l>DDoS protection enabled");
        } catch (IOException e) {
            System.err.println("§b[Simpledownload]§4[Event]§l>Failed to start server: " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            loadUrlsFromJson(); // Call the method to load URL data
        }

        @Mod.EventBusSubscriber
        public static class ForgeEvents {
            @SubscribeEvent
            // Register Commands...Guess what they are used for ?
            // Alright...I have written usage on the readme file...
            public static void registerCommands(RegisterCommandsEvent event) {
                event.getDispatcher().register(Commands.literal("durl")
                        .then(Commands.literal("file")
                                .executes(ctx -> openFile(ctx.getSource()))
                        )
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearUrls(ctx.getSource()))
                        )
                        .then(Commands.literal("add")
                                .then(Commands.argument("number", IntegerArgumentType.integer())
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(ctx -> addUrl(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number"), StringArgumentType.getString(ctx, "url")))
                                        )
                                )
                        )
                        .then(Commands.literal("list")
                                .executes(ctx -> listUrls(ctx.getSource()))
                        )
                        .then(Commands.literal("delete")
                                .then(Commands.argument("number", IntegerArgumentType.integer())
                                        .executes(ctx -> deleteUrl(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number")))
                                )
                        )
                        .then(Commands.literal("clo")
                                .executes(ctx -> showLogConnectionsStatus(ctx.getSource()))
                                .then(Commands.literal("Enable")
                                        .executes(ctx -> setLogConnections(ctx.getSource(), true))
                                )
                                .then(Commands.literal("Disable")
                                        .executes(ctx -> setLogConnections(ctx.getSource(), false))
                                )
                        )
                        .then(Commands.literal("banlog")
                                .executes(ctx -> showLogBansStatus(ctx.getSource()))
                                .then(Commands.literal("Enable")
                                        .executes(ctx -> setLogBans(ctx.getSource(), true))
                                )
                                .then(Commands.literal("Disable")
                                        .executes(ctx -> setLogBans(ctx.getSource(), false))
                                )
                        )
                );
            }

            private static int openFile(CommandSource source) {
                try {
                    // Make sure the DIRECTORY exists
                    if (!configDir.exists()) {
                        boolean dirCreated = configDir.mkdirs();
                        if (!dirCreated) {
                            source.sendFailure(new StringTextComponent("§b[Simpledownload]§4[CMD]§f>Failed to create config directory."));
                            return 0;
                        }
                    }

                    // Make sure the FILE exists
                    if (!configFile.exists()) {
                        try (Writer writer = new FileWriter(configFile)) {
                            JsonObject root = new JsonObject();
                            JsonObject urls = new JsonObject();
                            root.add("urls", urls);
                            GSON.toJson(root, writer);
                        } catch (IOException e) {
                            source.sendFailure(new StringTextComponent("§b[Simpledownload]§4[CMD]§f>Failed to create config file."));
                            return 0;
                        }
                    }

                    // Open file
                    Desktop.getDesktop().open(configFile);
                    source.sendSuccess(new StringTextComponent("§b[Simpledownload]§a[CMD]§f>Opened config file at: " + configFile.getAbsolutePath()), false);
                } catch (IOException e) {
                    LOGGER.error("Error opening config file", e);
                    source.sendFailure(new StringTextComponent("§b[Simpledownload]§4[CMD]§f>Error opening config file: " + e.getMessage()));
                }
                return 1;
            }

            private static int clearUrls(CommandSource source) {
                urlMap.clear();
                saveUrlsToJson(); // Added auto-save,U know what it does, right ? It Makes Sure that we don't have to manually save the config after proceed the commands
                source.sendSuccess(new StringTextComponent("§b[Simpledownload]§a[CMD]§f>All URLs cleared."), false);
                return 1;
            }

            private static int addUrl(CommandSource source, int number, String url) throws CommandSyntaxException {
                urlMap.put(number, url);
                saveUrlsToJson(); // Added auto-save
                source.sendSuccess(new StringTextComponent("§b[Simpledownload]§a[CMD]§f>URL added for table " + number + ": " + url), false);
                return 1;
            }

            private static int listUrls(CommandSource source) {
                if (urlMap.isEmpty()) {
                    source.sendSuccess(new StringTextComponent("§b[Simpledownload]§e[CMD]§f>No URLs added yet."), false);
                } else {
                    urlMap.forEach((key, value) -> source.sendSuccess(new StringTextComponent("Table " + key + ": " + value), false));
                }
                return 1;
            }

            private static int deleteUrl(CommandSource source, int number) {
                if (urlMap.containsKey(number)) {
                    urlMap.remove(number);
                    saveUrlsToJson(); // Added auto-save
                    source.sendSuccess(new StringTextComponent("§b[Simpledownload]§a[CMD]§f>URL for table " + number + " deleted."), false);
                } else {
                    source.sendSuccess(new StringTextComponent("§b[Simpledownload]§4[CMD]§f>Table " + number + " does not exist."), false);
                }
                return 1;
            }

            public static void loadUrlsFromJson() {
                try (Reader reader = new FileReader(configFile)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root != null) {
                        // Load URLs
                        if (root.has("urls")) {
                            urlMap.clear();
                            JsonObject urls = root.getAsJsonObject("urls");
                            urls.entrySet().forEach(entry -> {
                                urlMap.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
                            });
                        }

                        // Load Config -> (Settings)
                        if (root.has("settings")) {
                            JsonObject settings = root.getAsJsonObject("settings");
                            if (settings.has("logConnections")) {
                                logConnections = settings.get("logConnections").getAsBoolean();
                            }
                            if (settings.has("logBans")) {
                                logBans = settings.get("logBans").getAsBoolean();
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("§b[Simpledownload]§4[Event]§f>Cannot Load Settings", e);
                }
            }

            private static void saveUrlsToJson() {
                try (Writer writer = new FileWriter(configFile)) {
                    JsonObject root = new JsonObject();

                    // Save URLs
                    JsonObject urls = new JsonObject();
                    urlMap.forEach((k, v) -> urls.addProperty(k.toString(), v));
                    root.add("urls", urls);

                    // Save Config -> (Settings)
                    JsonObject settings = new JsonObject();
                    settings.addProperty("logConnections", logConnections);
                    settings.addProperty("logBans", logBans);
                    root.add("settings", settings);

                    GSON.toJson(root, writer);
                } catch (IOException e) {
                    LOGGER.error("§b[Simpledownload]§4[Event]§f>Cannot Save Settings", e);
                }
            }

            private static int showLogConnectionsStatus(CommandSource source) {
                source.sendSuccess(new StringTextComponent(
                        "§b[Simpledownload]§a[Query]§f>Connection logging is currently " +
                                (logConnections ? "§aEnabled" : "§cDisabled")), false);
                return 1;
            }

            private static int setLogConnections(CommandSource source, boolean enabled) {
                logConnections = enabled;
                saveUrlsToJson();
                source.sendSuccess(new StringTextComponent(
                        "§b[Simpledownload]§a[CMD]§f>Connection logging has been " +
                                (enabled ? "§aEnabled" : "§cDisabled")), false);
                return 1;
            }

            private static int showLogBansStatus(CommandSource source) {
                source.sendSuccess(new StringTextComponent(
                        "§b[Simpledownload]§a[Query]§f>Ban logging is currently " +
                                (logBans ? "§aEnabled" : "§cDisabled")), false);
                return 1;
            }

            private static int setLogBans(CommandSource source, boolean enabled) {
                logBans = enabled;
                saveUrlsToJson();
                source.sendSuccess(new StringTextComponent(
                        "§b[Simpledownload]§a[CMD]§f>Ban logging has been " +
                                (enabled ? "§aEnabled" : "§cDisabled")), false);
                return 1;
            }
        }
    }
}