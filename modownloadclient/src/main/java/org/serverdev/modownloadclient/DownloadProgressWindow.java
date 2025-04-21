package org.serverdev.modownloadclient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Modality;

import com.google.gson.JsonElement;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadProgressWindow extends Application {

    private static Label skippedFilesLabel;

    private static Label speedLabel;
    private static ProgressBar progressBar;
    private static Label progressLabel;
    private static Label totalFilesLabel;
    private static Label downloadedFilesLabel;
    private static TextField serverUrlField;
    private static Stage primaryStage;
    private static String serverUrl;
    private static Button pauseResumeButton;
    private static Button cancelButton;

    private static final AtomicBoolean isPaused = new AtomicBoolean(false);
    private static final AtomicBoolean isCancelled = new AtomicBoolean(false);

    private static List<String> downloadedFiles = new ArrayList<>();
    private static int totalFiles = 0;

    @Override
    public void start(Stage primaryStage) {
        DownloadProgressWindow.primaryStage = primaryStage;
        createUrlInputStage();
    }

    private static void createUrlInputStage() {
        serverUrlField = new TextField();
        serverUrlField.setPromptText("Enter Server Url");
        serverUrlField.setText("http://example.com/getDurlData");

        Button fetchButton = new Button("Fetch");
        Button cancelButton = new Button("Cancel");

        Button ignoreButton = new Button("Ignore-mods");
        ignoreButton.setOnAction(e -> showIgnoreSetWindow());

        fetchButton.setOnAction(e -> {
            serverUrl = serverUrlField.getText();
            if (validateServerUrl(serverUrl)) {
                showCleanupWindow(() -> {
                    createDownloadProgressStage();
                    Modownloadclient.startDownload(serverUrl);
                });
            } else {
                showErrorDialog("Oops", "Can Not Reach Server");
            }
        });


        cancelButton.setOnAction(e -> {
            Platform.exit();
        });

        HBox buttonBox = new HBox(10, fetchButton, ignoreButton, cancelButton); // 添加ignoreButton
        buttonBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(10);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(
                new Label("Enter Server URL + /getDurlData:"),
                serverUrlField,
                buttonBox
        );

        Scene scene = new Scene(root, 400, 200);
        primaryStage.setTitle("MDC Ver 1.0");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private static boolean validateServerUrl(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            return false;
        }
    }

    static void showErrorDialog(String title, String message) {
        Platform.runLater(() -> {
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(title);

            Button okButton = new Button("OK");
            okButton.setOnAction(e -> dialog.close());

            VBox dialogVBox = new VBox(20,
                    new Label(message),
                    okButton
            );
            dialogVBox.setAlignment(Pos.CENTER);
            dialogVBox.setPadding(new Insets(20));

            Scene dialogScene = new Scene(dialogVBox, 300, 150);
            dialog.setScene(dialogScene);
            dialog.showAndWait();
        });
    }

    private static void createDownloadProgressStage() {
        Platform.runLater(() -> {
            progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(400);
            speedLabel = new Label("Speed: 0 KB/s");

            skippedFilesLabel = new Label("Skipped Files: 0");

            progressLabel = new Label("CurrentFile: 0%");
            totalFilesLabel = new Label("TotalFiles: " + totalFiles);
            downloadedFilesLabel = new Label("DownloadedFiles: " + downloadedFiles.size());

            pauseResumeButton = new Button("Pause");
            cancelButton = new Button("Cancel");

            pauseResumeButton.setOnAction(e -> {
                if (isPaused.get()) {
                    isPaused.set(false);
                    pauseResumeButton.setText("Pause");
                    synchronized (isPaused) {
                        isPaused.notifyAll();
                    }
                } else {
                    isPaused.set(true);
                    pauseResumeButton.setText("Resume");
                }
            });

            cancelButton.setOnAction(e -> {
                isCancelled.set(true);
                synchronized (isPaused) {
                    isPaused.notifyAll(); // Wake up any download threads that may be waiting
                }
                Platform.exit();
            });

            HBox buttonBox = new HBox(10, pauseResumeButton, cancelButton);
            buttonBox.setAlignment(Pos.CENTER);

            VBox root = new VBox(10);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));
            root.getChildren().addAll(
                    progressLabel,
                    speedLabel,
                    progressBar,
                    totalFilesLabel,
                    downloadedFilesLabel,
                    skippedFilesLabel,  // 新增
                    buttonBox
            );

            Scene scene = new Scene(root, 500, 250);
            primaryStage.setScene(scene);
            primaryStage.setTitle("MDC Ver 1.0");
        });
    }

    private static void showIgnoreSetWindow() {
        Platform.runLater(() -> {
            Stage ignoreStage = new Stage();
            ignoreStage.setTitle("IgnoreSet");
            ignoreStage.initModality(Modality.APPLICATION_MODAL);
            ignoreStage.setResizable(false);

            // Disable the close button
            ignoreStage.setOnCloseRequest(event -> event.consume());

            // Get the mods folder list
            File modsDir = new File(Minecraft.getInstance().gameDirectory, "mods");
            File[] modFiles = modsDir.listFiles();

            Set<String> ignoredMods = new HashSet<>();
            File ignoreFile = new File(Minecraft.getInstance().gameDirectory, "mods/Data/Ignore.json");
            if (ignoreFile.exists()) {
                try {
                    String jsonStr = new String(Files.readAllBytes(ignoreFile.toPath()));
                    JsonObject json = new Gson().fromJson(jsonStr, JsonObject.class);
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        ignoredMods.add(entry.getValue().getAsString());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Create a list with checkboxes (add check logic)
            VBox checkBoxContainer = new VBox(5);
            if (modFiles != null) {
                for (File file : modFiles) {
                    if (file.isFile()) {
                        CheckBox checkBox = new CheckBox(file.getName());
                        // Check if it is in the ignore list
                        if (ignoredMods.contains(file.getName())) {
                            checkBox.setSelected(true);
                        }
                        checkBoxContainer.getChildren().add(checkBox);
                    }
                }
            }

            // Save Button
            Button saveButton = new Button("Save & Close");
            saveButton.setOnAction(e -> {
                saveIgnoredMods(checkBoxContainer);
                ignoreStage.close();
            });

            ScrollPane scrollPane = new ScrollPane(checkBoxContainer);
            scrollPane.setFitToWidth(true);

            VBox root = new VBox(10);
            root.setPadding(new Insets(10));
            root.getChildren().addAll(
                    new Label("Select Mods to Ignore"),
                    scrollPane,
                    saveButton
            );

            Scene scene = new Scene(root, 400, 500);
            ignoreStage.setScene(scene);
            ignoreStage.show();
        });
    }

    private static void saveIgnoredMods(VBox container) {
        try {
            // Create a data directory
            File dataDir = new File(Minecraft.getInstance().gameDirectory, "mods/Data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            // Generate JSON
            JsonObject json = new JsonObject();
            int index = 1;
            for (javafx.scene.Node node : container.getChildren()) {
                if (node instanceof CheckBox) {
                    CheckBox cb = (CheckBox) node;
                    if (cb.isSelected()) {
                        json.addProperty(String.valueOf(index++), cb.getText());
                    }
                }
            }

            // Write to file (Ignore.Json)
            File output = new File(dataDir, "Ignore.json");
            try (FileWriter writer = new FileWriter(output)) {
                new Gson().toJson(json, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("Save ERROR", "Cannot Save Ignore Mod Data: " + e.getMessage());
        }
    }

    private static void showCleanupWindow(Runnable onCleanupComplete) {
        Platform.runLater(() -> {
            Stage cleanupStage = new Stage();
            cleanupStage.initModality(Modality.APPLICATION_MODAL);
            cleanupStage.setTitle("Cleanup Sequence");

            ProgressBar cleanupProgress = new ProgressBar(0);
            cleanupProgress.setPrefWidth(400);
            Label cleanupLabel = new Label("Cleaning up non-Ignore mod files...");

            VBox root = new VBox(20, cleanupLabel, cleanupProgress);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));

            Scene scene = new Scene(root, 400, 150);
            cleanupStage.setScene(scene);
            cleanupStage.show();

            new Thread(() -> {
                try {
                    // Get Ignore List
                    Set<String> ignoreSet = getIgnoreSet();

                    // Get a list of server files
                    Set<String> serverFiles = getServerFiles(serverUrl);

                    // Clean up the mods folder
                    File modsDir = new File(Minecraft.getInstance().gameDirectory, "mods");
                    File[] modFiles = modsDir.listFiles();
                    int totalFiles = modFiles != null ? modFiles.length : 0;
                    int processedFiles = 0;

                    if (modFiles != null) {
                        for (File file : modFiles) {
                            if (file.isFile()) {
                                String fileName = file.getName();
                                // If the file is not in the server list and is not in the ignore list, then delete
                                if (!serverFiles.contains(fileName) && !ignoreSet.contains(fileName)) {
                                    file.delete();
                                }
                            }
                            processedFiles++;
                            final int progress = (int) ((double) processedFiles / totalFiles * 100);
                            Platform.runLater(() -> cleanupProgress.setProgress(progress / 100.0));
                        }
                    }

                    // Check file integrity
                    boolean allFilesExist = checkAllFilesExist(serverUrl, ignoreSet);
                    if (allFilesExist) {
                        Platform.runLater(() -> {
                            cleanupStage.close();
                            showUpToDateMessage();
                        });
                        return;
                    }

                    Platform.runLater(() -> {
                        cleanupStage.close();
                        onCleanupComplete.run();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        cleanupStage.close();
                        showErrorDialog("Cleanup ERROR", "ERROR: " + e.getMessage());
                    });
                }
            }).start();
        });
    }

    // Check if all files exist
    private static boolean checkAllFilesExist(String serverUrl, Set<String> ignoreSet) throws Exception {
        Set<String> serverFiles = getServerFiles(serverUrl);
        File modsDir = new File(Minecraft.getInstance().gameDirectory, "mods");

        for (String fileName : serverFiles) {
            if (ignoreSet.contains(fileName)) {
                continue; // Skip ignored files
            }

            File file = new File(modsDir, fileName);
            if (!file.exists()) {
                return false; // Missing files found
            }
        }
        return true; // All files exist
    }

    // Display the "File is up to date" message
    private static void showUpToDateMessage() {
        Platform.runLater(() -> {
            Stage messageStage = new Stage();
            messageStage.initModality(Modality.APPLICATION_MODAL);
            messageStage.setTitle("Ready");
            messageStage.setResizable(false);

            Label messageLabel = new Label("All Files Are Ready To Go");
            VBox root = new VBox(20, messageLabel);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));

            Scene scene = new Scene(root, 300, 100);
            messageStage.setScene(scene);
            messageStage.show();

            // Automatically END after 3 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(messageStage::close);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    private static Set<String> getIgnoreSet() throws Exception {
        Set<String> ignoreSet = new HashSet<>();
        File ignoreFile = new File(Minecraft.getInstance().gameDirectory, "mods/Data/Ignore.json");
        if (ignoreFile.exists()) {
            String jsonStr = new String(Files.readAllBytes(ignoreFile.toPath()));
            JsonObject json = new Gson().fromJson(jsonStr, JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                ignoreSet.add(entry.getValue().getAsString());
            }
        }
        return ignoreSet;
    }

    private static Set<String> getServerFiles(String serverUrl) throws Exception {
        Set<String> serverFiles = new HashSet<>();
        URL url = new URL(serverUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (InputStream inputStream = connection.getInputStream();
             Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A")) {
            String jsonString = scanner.hasNext() ? scanner.next() : "";
            JsonElement element = new Gson().fromJson(jsonString, JsonElement.class);
            JsonObject rootJson = element.getAsJsonObject();
            JsonObject urlsJson = rootJson.getAsJsonObject("urls");

            for (Map.Entry<String, JsonElement> entry : urlsJson.entrySet()) {
                String fileUrl = entry.getValue().getAsString();
                serverFiles.add(getFileNameFromUrl(fileUrl));
            }
        }
        return serverFiles;
    }

    public static void setSkippedFilesCount(int count) {
        Platform.runLater(() -> {
            skippedFilesLabel.setText("SkippedFiles: " + count);
        });
    }

    public static void showRestartDialog() {
        Platform.runLater(() -> {
            Stage restartStage = new Stage();
            restartStage.initModality(Modality.APPLICATION_MODAL);
            restartStage.setTitle("Ready");

            // Disabled the close button
            restartStage.setOnCloseRequest(event -> event.consume());

            Button restartButton = new Button("Restart");
            restartButton.setOnAction(e -> {
                // End the game process
                Minecraft.getInstance().stop();
                Platform.exit();
            });

            Button cancelButton = new Button("Cancel");
            cancelButton.setOnAction(e -> {
                // Close the FX window only
                Platform.exit();
            });

            HBox buttonBox = new HBox(10, restartButton, cancelButton);
            buttonBox.setAlignment(Pos.CENTER);

            VBox root = new VBox(20,
                    new Label("All Files Are Ready To Go"),
                    new Label("Restart Game to Complete , Restart Now ?"),
                    buttonBox
            );
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(20));

            Scene scene = new Scene(root, 300, 200);
            restartStage.setScene(scene);
            restartStage.showAndWait();
        });
    }


    private static String getFileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public static void updateProgress(int percentComplete) {
        Platform.runLater(() -> {
            progressBar.setProgress((double) percentComplete / 100);
            progressLabel.setText("CurrentFile: " + percentComplete + "%");
        });
    }

    public static void updateSpeed(double speed) {
        Platform.runLater(() -> {
            speedLabel.setText(String.format("CurrentSpeed: %.2f KB/s", speed));
        });
    }

    public static void setTotalFiles(int total) {
        Platform.runLater(() -> {
            totalFiles = total;
            totalFilesLabel.setText("TotalFiles: " + totalFiles);
        });
    }

    public static void setDownloadedFilesCount(int count) {
        Platform.runLater(() -> {
            downloadedFilesLabel.setText("DownloadedFiles: " + count);
        });
    }

    public static void closeWindow() {
        Platform.runLater(() -> {
            Platform.exit();
        });
    }

    public static boolean isPaused() {
        return isPaused.get();
    }

    public static boolean isCancelled() {
        return isCancelled.get();
    }

    public static void waitIfPaused() {
        synchronized (isPaused) {
            while (isPaused.get() && !isCancelled.get()) {
                try {
                    isPaused.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}