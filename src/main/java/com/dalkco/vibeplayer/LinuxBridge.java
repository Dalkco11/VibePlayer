package com.dalkco.vibeplayer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Linux: MPRIS through the {@code playerctl} command line tool (if it is installed). */
final class LinuxBridge extends MediaBridge {
    private static final String FORMAT = "{{playerName}}\t{{status}}\t{{title}}\t{{artist}}\t{{album}}\t"
            + "{{position}}\t{{mpris:length}}\t{{mpris:artUrl}}";

    private volatile boolean stopping;
    private volatile String pinned = "";
    private Thread thread;
    private String lastArt = "";

    static boolean available() {
        try {
            Process p = new ProcessBuilder("playerctl", "--version").redirectErrorStream(true).start();
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public synchronized void start() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        stopping = false;
        pinned = VibeConfig.get().pinned == null ? "" : VibeConfig.get().pinned;
        thread = new Thread(this::loop, "Vibe Player playerctl");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void stop() {
        stopping = true;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        state = MediaState.EMPTY;
    }

    private List<String> base() {
        List<String> cmd = new ArrayList<>(List.of("playerctl"));
        if (!pinned.isEmpty()) {
            cmd.add("-p");
            cmd.add(pinned);
        }
        return cmd;
    }

    private static String run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        p.waitFor(3, TimeUnit.SECONDS);
        return sb.toString().trim();
    }

    private void loop() {
        while (!stopping) {
            try {
                List<String> cmd = base();
                cmd.addAll(List.of("metadata", "--format", FORMAT));
                String line = run(cmd);
                List<String> players = new ArrayList<>();
                for (String pl : run(List.of("playerctl", "-l")).split("\n")) {
                    if (!pl.isBlank()) {
                        players.add(pl.trim());
                    }
                }
                String[] f = line.split("\t", -1);
                if (f.length >= 8) {
                    double pos = parse(f[5]) / 1_000_000.0;
                    double dur = parse(f[6]) / 1_000_000.0;
                    String key = f[0] + "|" + f[2] + "|" + f[3];
                    state = new MediaState(f[0], f[2], f[3], f[4], f[1], pos, dur, System.currentTimeMillis(),
                            true, true, true, true, List.copyOf(players), pinned, key);
                    if (!f[7].equals(lastArt)) {
                        lastArt = f[7];
                        loadArt(key, f[7]);
                    }
                } else {
                    state = new MediaState("", "", "", "", "", 0, 0, 0, false, false, false, false,
                            List.copyOf(players), pinned, "");
                }
                status = Status.CONNECTED;
            } catch (Exception e) {
                status = Status.UNAVAILABLE;
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static void loadArt(String key, String url) {
        try {
            if (url.startsWith("file://")) {
                CoverArt.offer(key, Files.readAllBytes(Path.of(URI.create(url))));
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                try (var in = URI.create(url).toURL().openStream()) {
                    CoverArt.offer(key, in.readAllBytes());
                }
            } else {
                CoverArt.offer(key, new byte[0]);
            }
        } catch (Exception e) {
            CoverArt.offer(key, new byte[0]);
        }
    }

    @Override
    protected void send(String command) {
        String[] parts = command.split(" ", 2);
        List<String> cmd = base();
        switch (parts[0]) {
            case "toggle" -> cmd.add("play-pause");
            case "play" -> cmd.add("play");
            case "pause" -> cmd.add("pause");
            case "next" -> cmd.add("next");
            case "prev" -> cmd.add("previous");
            case "seek" -> {
                cmd.add("position");
                cmd.add(parts.length > 1 ? parts[1] : "0");
            }
            case "pin" -> {
                pinned = parts.length > 1 && !"auto".equals(parts[1]) ? parts[1] : "";
                return;
            }
            default -> {
                return;
            }
        }
        Thread t = new Thread(() -> {
            try {
                run(cmd);
            } catch (Exception ignored) {
                // nothing to do: the next poll shows the real state
            }
        }, "Vibe Player playerctl command " + command.toLowerCase(Locale.ROOT));
        t.setDaemon(true);
        t.start();
    }
}
