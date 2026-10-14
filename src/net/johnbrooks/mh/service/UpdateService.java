package net.johnbrooks.mh.service;

import net.johnbrooks.mh.Main;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UpdateService {
    private static final String urlToMobCapture = "https://johnbrooks.net/spigot_plugins/downloads/1.13.1/MobCapture.jar";

    public static boolean isUpdateAvailable() {
        int remoteSize = UpdateService.getFileSize(urlToMobCapture);
        int localSize = UpdateService.getFileSize();

        return remoteSize != localSize;
    }

    private static int getFileSize(String fileUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(fileUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static int getFileSize() {
        File file = Main.plugin.getDataFolder();
        Path path = Paths.get(file.toPath().toString() + ".jar");
        Main.logger.info(path.toString());
        try {
            return (int) Files.size(path);
        }
        catch (Exception ex) {
            return -1;
        }
    }
}
