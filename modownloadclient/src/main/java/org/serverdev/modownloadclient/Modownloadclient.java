package org.serverdev.modownloadclient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraftforge.fml.network.FMLHandshakeHandler;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraft.util.ResourceLocation;


import java.io.FileWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Map;
import java.util.Scanner;

@Mod("modownloadclient")
public class Modownloadclient {

    private static int downloadedFileCount = 0;
    private static int totalFilesCount = 0;
    private static long lastUpdateTime;
    private static long lastBytesRead;
    private static double currentSpeed; // KB/s
    private static volatile boolean isPaused = false;
    private static volatile boolean isCancelled = false;

    private static int skippedFileCount = 0;

    public Modownloadclient() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        new Thread(() -> Application.launch(DownloadProgressWindow.class)).start();
    }

    public static void startDownload(String serverUrl) {
        new Thread(() -> downloadFiles(serverUrl)).start();
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
    }

    private void processIMC(final InterModProcessEvent event) {
    }

    private static void downloadFiles(String serverUrl) {
        try {
            URL url = new URL(serverUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            InputStream inputStream = connection.getInputStream();
            String jsonString = convertInputStreamToString(inputStream);

            Gson gson = new Gson();
            JsonElement element = gson.fromJson(jsonString, JsonElement.class);
            JsonObject rootJson = element.getAsJsonObject();

            JsonObject urlsJson = rootJson.getAsJsonObject("urls");
            totalFilesCount = urlsJson.size();
            DownloadProgressWindow.setTotalFiles(totalFilesCount);

            for (int i = 1; i <= totalFilesCount; i++) {
                String key = String.valueOf(i);
                if (urlsJson.has(key)) {
                    String fileUrl = urlsJson.get(key).getAsString();
                    if (checkIfFileExists(fileUrl)) {
                        skippedFileCount++;
                        DownloadProgressWindow.setSkippedFilesCount(skippedFileCount);
                    } else {
                        downloadFile(fileUrl);
                        downloadedFileCount++;
                        DownloadProgressWindow.setDownloadedFilesCount(downloadedFileCount);
                    }

                    // Check if Finished
                    if ((downloadedFileCount + skippedFileCount) == totalFilesCount) {
                        DownloadProgressWindow.showRestartDialog();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            DownloadProgressWindow.showErrorDialog("DownloadERROR", "ERROR: " + e.getMessage());
        }
    }

    private static boolean checkIfFileExists(String fileUrl) {
        String fileName = getFileNameFromUrl(fileUrl);
        File ignoreFile = new File(Minecraft.getInstance().gameDirectory, "mods/Data/Ignore.json");

        // Check Ignore List
        if (ignoreFile.exists()) {
            try {
                JsonObject ignoreJson = new Gson().fromJson(
                        new String(Files.readAllBytes(ignoreFile.toPath())),
                        JsonObject.class
                );
                for (Map.Entry<String, JsonElement> entry : ignoreJson.entrySet()) {
                    if (entry.getValue().getAsString().equals(fileName)) {
                        return true; // File is in Ignore list
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Check If File Exists
        File file = new File(Minecraft.getInstance().gameDirectory, "mods/" + fileName);
        return file.exists();
    }

    private static void downloadFile(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int fileSize = connection.getContentLength();

            InputStream inputStream = connection.getInputStream();
            File modDirectory = new File(Minecraft.getInstance().gameDirectory, "mods");
            if (!modDirectory.exists()) {
                modDirectory.mkdirs();
            }

            File file = new File(modDirectory, getFileNameFromUrl(fileUrl));
            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalBytesRead = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Check If it was cancelled
                if (DownloadProgressWindow.isCancelled()) {
                    outputStream.close();
                    inputStream.close();
                    file.delete(); // Delete the incomplete downloaded files
                    return;
                }

                // Check If it was paused
                DownloadProgressWindow.waitIfPaused();

                totalBytesRead += bytesRead;
                outputStream.write(buffer, 0, bytesRead);

                // Calculate Download Speed
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= 200) { // Update Per Second
                    currentSpeed = (totalBytesRead - lastBytesRead) / 1024.0; // KB/s
                    lastBytesRead = totalBytesRead;
                    lastUpdateTime = currentTime;
                    DownloadProgressWindow.updateSpeed(currentSpeed);
                }

                int percentComplete = (int) (((double) totalBytesRead / fileSize) * 100);
                DownloadProgressWindow.updateProgress(percentComplete);
            }

            outputStream.close();
            inputStream.close();

        } catch (Exception e) {
            e.printStackTrace();
            DownloadProgressWindow.showErrorDialog("DownloadERROR", "ERROR: " + e.getMessage());
        }
    }

    public static void setPaused(boolean paused) {
        isPaused = paused;
    }

    public static void setCancelled(boolean cancelled) {
        isCancelled = cancelled;
    }

    public static String getFileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static String convertInputStreamToString(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}
