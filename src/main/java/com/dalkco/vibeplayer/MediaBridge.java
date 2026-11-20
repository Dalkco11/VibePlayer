package com.dalkco.vibeplayer;

import net.minecraft.util.Util;

import java.util.Locale;

/** Connection to the operating system's media controls. */
public abstract class MediaBridge {
    public enum Status { STARTING, CONNECTED, UNAVAILABLE, UNSUPPORTED }

    protected volatile MediaState state = MediaState.EMPTY;
    protected volatile Status status = Status.STARTING;

    private static MediaBridge instance;

    public static MediaBridge get() {
        if (instance == null) {
            instance = create();
        }
        return instance;
    }

    private static MediaBridge create() {
        if (Util.getPlatform() == Util.OS.WINDOWS) {
            return new WindowsBridge();
        }
        if (Util.getPlatform() == Util.OS.LINUX && LinuxBridge.available()) {
            return new LinuxBridge();
        }
        return new MediaBridge() {
            {
                status = Status.UNSUPPORTED;
            }

            @Override
            public void start() {
            }

            @Override
            public void stop() {
            }

            @Override
            protected void send(String command) {
            }
        };
    }

    public MediaState state() {
        return state;
    }

    public Status status() {
        return status;
    }

    public abstract void start();

    public abstract void stop();

    /** Restart the connection (settings button). */
    public void restart() {
        stop();
        start();
    }

    protected abstract void send(String command);

    public void togglePlay() {
        MediaState s = state;
        send("toggle");
        if (s.active()) {
            state = s.withStatus(s.playing() ? "Paused" : "Playing", System.currentTimeMillis());
        }
    }

    public void play() {
        send("play");
    }

    public void pause() {
        send("pause");
    }

    public void next() {
        send("next");
    }

    public void previous() {
        send("prev");
    }

    public void seek(double seconds) {
        MediaState s = state;
        if (!s.canSeek()) {
            return;
        }
        double clamped = Math.max(0, s.dur() > 0 ? Math.min(seconds, s.dur() - 0.5) : seconds);
        send(String.format(Locale.ROOT, "seek %.2f", clamped));
        state = s.withPosition(clamped, System.currentTimeMillis());
    }

    /** Follow one app ("" = whatever Windows considers current). */
    public void pin(String app) {
        send("pin " + (app == null || app.isEmpty() ? "auto" : app));
    }
}
