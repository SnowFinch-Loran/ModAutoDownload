package org.serverdev.simpledownload;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;

import static org.serverdev.simpledownload.Simpledownload.GSON;
import static org.serverdev.simpledownload.Simpledownload.urlMap;

public class GetDurlDataHandler implements HttpHandler {
    // IP access frequency tracking
    private static final ConcurrentHashMap<String, AtomicInteger> ipAccessCount = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Instant> ipBlockList = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 100; // Max Request Per Minute
    private static final long BLOCK_DURATION_MINUTES = 30; // Ban Duration(Minute)

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();

        // Check if it is on the banned list
        if (isIpBlocked(clientIP)) {
            sendErrorResponse(exchange, 403, "IP blocked due to excessive requests");
            return;
        }

        // Record access frequency
        trackIpAccess(clientIP);

        // Get IP location information
        String location = getIpLocation(clientIP);

        // Print connection information only once
        if (Simpledownload.logConnections) {
            System.out.println(String.format(
                    "§b[Simpledownload]§a[Event]§f> IP: %s | Location: %s | Time: %s",
                    clientIP, location, Instant.now().toString()
            ));
        }

        // Handling Requests
        byte[] response = readDurlJsonData();
        exchange.getResponseHeaders().set("Content-Type", "application/json");

        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    private boolean isIpBlocked(String ip) {
        Instant blockTime = ipBlockList.get(ip);
        if (blockTime != null) {
            if (Instant.now().isBefore(blockTime.plusSeconds(BLOCK_DURATION_MINUTES * 60))) {
                return true;
            } else {
                ipBlockList.remove(ip); // The ban period has expired
            }
        }
        return false;
    }

    private void trackIpAccess(String ip) {
        ipAccessCount.compute(ip, (key, count) -> {
            if (count == null) {
                return new AtomicInteger(1);
            }
            int newCount = count.incrementAndGet();
            if (newCount > MAX_REQUESTS_PER_MINUTE) {
                ipBlockList.put(ip, Instant.now());
                // Modified ban log output
                if (Simpledownload.logBans) {
                    System.out.println("§b[Simpledownload]§6[Security]§f> IP blocked: " + ip);
                }
            }
            return count;
        });

        // Counter will be reset per minute
        new Thread(() -> {
            try {
                Thread.sleep(60000);
                ipAccessCount.remove(ip);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private String getIpLocation(String ip) {
        // You can use a third-party API to get more accurate location information
        // Simple implementation: return only the country code
        // I'm Too Lazy...Maybe Next Time Update ?
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            String hostName = inetAddress.getHostName();
            if (hostName.contains(".com") || hostName.contains(".net")) {
                return "US"; // Example: Assuming the domain name(.com/.net) is from the United States
            }
            return inetAddress.getHostAddress().startsWith("192.168.") ? "LAN" : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        byte[] response = error.toString().getBytes();
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static byte[] readDurlJsonData() {
        JsonObject root = new JsonObject();
        JsonObject urls = new JsonObject();
        urlMap.forEach((k, v) -> urls.addProperty(k.toString(), v));
        root.add("urls", urls);
        return GSON.toJson(root).getBytes();
    }
}