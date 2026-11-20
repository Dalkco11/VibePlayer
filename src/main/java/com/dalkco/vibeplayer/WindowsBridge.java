package com.dalkco.vibeplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Windows: runs a small PowerShell script (bundled in the jar) that talks to the System Media Transport Controls,
 * the same thing the volume flyout uses. One JSON line per update in, one command per line out.
 */
final class WindowsBridge extends MediaBridge {
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("vibeplayer");

    private volatile boolean stopping;
    private volatile Process process;
    private volatile BufferedWriter out;
    private Thread thread;

    @Override
    public synchronized void start() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        stopping = false;
        status = Status.STARTING;
        thread = new Thread(this::run, "Vibe Player media bridge");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void stop() {
        stopping = true;
        Process p = process;
        if (p != null) {
            p.destroy();
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        state = MediaState.EMPTY;
    }

    private void run() {
        int failures = 0;
        while (!stopping) {
            try {
                boolean ok = runOnce();
                failures = ok ? 0 : failures + 1;
            } catch (Exception e) {
                failures++;
                VibePlayerClient.LOGGER.warn("Media bridge failed", e);
            }
            if (stopping) {
                break;
            }
            status = Status.UNAVAILABLE;
            state = MediaState.EMPTY;
            try {
                Thread.sleep(Math.min(60_000L, 2_000L * Math.max(1, failures)));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Runs the script until it exits. True if it connected at least once. */
    private boolean runOnce() throws IOException {
        Path script = DIR.resolve("smtc.ps1");
        Files.createDirectories(DIR);
        try (InputStream in = WindowsBridge.class.getResourceAsStream("/vibeplayer/smtc.ps1")) {
            if (in == null) {
                throw new IOException("bridge script missing from the jar");
            }
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", script.toString());
        pb.redirectError(ProcessBuilder.Redirect.to(DIR.resolve("bridge-errors.log").toFile()));
        Process p = pb.start();
        process = p;
        out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8));
        String pinned = VibeConfig.get().pinned;
        if (pinned != null && !pinned.isEmpty()) {
            send("pin " + pinned);
        }
        boolean connected = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!stopping && (line = reader.readLine()) != null) {
                if (line.isBlank() || line.charAt(0) != '{') {
                    continue;
                }
                try {
                    JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                    switch (str(o, "type")) {
                        case "hello" -> {
                            connected = true;
                            status = Status.CONNECTED;
                        }
                        case "state" -> state = parse(o);
                        case "thumb" -> {
                            String png = str(o, "png");
                            CoverArt.offer(str(o, "key"), png.isEmpty() ? new byte[0] : Base64.getDecoder().decode(png));
                        }
                        case "error" -> VibePlayerClient.LOGGER.debug("Media bridge: {}", str(o, "msg"));
                        default -> {
                        }
                    }
                } catch (Exception e) {
                    VibePlayerClient.LOGGER.debug("Bad line from media bridge: {}", line, e);
                }
            }
        } finally {
            out = null;
            p.destroy();
            process = null;
        }
        return connected;
    }

    private static MediaState parse(JsonObject o) {
        List<String> sessions = new ArrayList<>();
        JsonElement s = o.get("sessions");
        if (s != null && s.isJsonArray()) {
            JsonArray arr = s.getAsJsonArray();
            for (JsonElement e : arr) {
                sessions.add(e.getAsString());
            }
        } else if (s != null && s.isJsonPrimitive()) {
            sessions.add(s.getAsString());
        }
        return new MediaState(str(o, "app"), str(o, "title"), str(o, "artist"), str(o, "album"), str(o, "status"),
                num(o, "pos"), num(o, "dur"), (long) num(o, "upd"),
                bool(o, "next"), bool(o, "prev"), bool(o, "seek"), bool(o, "toggle"),
                List.copyOf(sessions), str(o, "pinned"), str(o, "key"));
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        try {
            return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
        } catch (Exception ex) {
            return 0;
        }
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    @Override
    protected synchronized void send(String command) {
        BufferedWriter w = out;
        if (w == null) {
            return;
        }
        try {
            w.write(command);
            w.newLine();
            w.flush();
        } catch (IOException e) {
            VibePlayerClient.LOGGER.debug("Could not send '{}' to the media bridge", command, e);
        }
    }
}
