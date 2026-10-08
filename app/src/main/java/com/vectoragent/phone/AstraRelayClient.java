package com.vectoragent.phone;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class AstraRelayClient {
    public interface Listener {
        void onStatus(String status);
        void onCommand(JSONObject command);
        void onError(String error);
    }

    private final Listener listener;
    private volatile boolean running;
    private Thread pollThread;
    private String baseUrl = "";
    private String deviceId = "";
    private String token = "";

    public AstraRelayClient(Listener listener) {
        this.listener = listener;
    }

    public synchronized void configure(String baseUrl, String deviceId, String token) {
        this.baseUrl = normalize(baseUrl);
        this.deviceId = deviceId == null ? "" : deviceId.trim();
        this.token = token == null ? "" : token.trim();
    }

    public synchronized boolean isConfigured() {
        return !baseUrl.isEmpty() && !deviceId.isEmpty() && !token.isEmpty();
    }

    public synchronized void start() {
        if (running) return;
        if (!isConfigured()) {
            listener.onError("Relay не настроен: URL + device ID + token обязательны.");
            return;
        }
        running = true;
        listener.onStatus("CONNECTING");
        pollThread = new Thread(this::loop, "AstraRelayPoll");
        pollThread.start();
    }

    public synchronized void stop() {
        running = false;
        Thread t = pollThread;
        pollThread = null;
        if (t != null) t.interrupt();
        listener.onStatus("DISCONNECTED");
    }

    public boolean isRunning() { return running; }

    private void loop() {
        while (running) {
            try {
                JSONObject command = next();
                if (command != null) {
                    listener.onCommand(command);
                }
                if (running) Thread.sleep(1800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                listener.onError("Relay: " + t.getClass().getSimpleName() +
                        (t.getMessage() == null ? "" : " — " + t.getMessage()));
                try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
            }
        }
    }

    private JSONObject next() throws Exception {
        String url = baseUrl + "/v1/next?device_id=" + java.net.URLEncoder.encode(deviceId, "UTF-8");
        HttpURLConnection c = open(url, "GET");
        try {
            int code = c.getResponseCode();
            String body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code == 204 || body.trim().isEmpty()) return null;
            if (code != 200) throw new Exception("HTTP " + code + " " + body);
            return new JSONObject(body);
        } finally {
            c.disconnect();
        }
    }

    public String sendResult(String commandId, boolean ok, String result) {
        try {
            JSONObject o = new JSONObject();
            o.put("device_id", deviceId);
            o.put("command_id", commandId == null ? "" : commandId);
            o.put("ok", ok);
            o.put("result", result == null ? "" : result);
            return post(baseUrl + "/v1/result", o);
        } catch (Throwable t) {
            return "Relay result error: " + t.getMessage();
        }
    }

    private String post(String url, JSONObject body) throws Exception {
        HttpURLConnection c = open(url, "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = c.getOutputStream()) {
            out.write(bytes);
        }
        int code = c.getResponseCode();
        String response = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " " + response);
        return response;
    }

    private HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);\n        c.setConnectTimeout(7000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) s.append(line);
        }
        return s.toString();
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String x = s.trim();
        while (x.endsWith("/")) x = x.substring(0, x.length() - 1);
        return x;
    }
}
