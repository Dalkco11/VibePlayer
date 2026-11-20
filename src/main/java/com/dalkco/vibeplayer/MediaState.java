package com.dalkco.vibeplayer;

import java.util.List;

/** What is playing on the PC right now (one media session). */
public record MediaState(String app, String title, String artist, String album, String status,
                         double pos, double dur, long updated,
                         boolean canNext, boolean canPrev, boolean canSeek, boolean canToggle,
                         List<String> sessions, String pinned, String key) {

    public static final MediaState EMPTY = new MediaState("", "", "", "", "", 0, 0, 0,
            false, false, false, false, List.of(), "", "");

    /** Something is loaded in a player. */
    public boolean active() {
        return !app.isEmpty() && !(title.isEmpty() && artist.isEmpty());
    }

    public boolean playing() {
        return "Playing".equals(status);
    }

    /** Position in seconds, moved forward by the time since the player last reported it. */
    public double position(long now) {
        double p = playing() && updated > 0 ? pos + (now - updated) / 1000.0 : pos;
        p = Math.max(0, p);
        return dur > 0 ? Math.min(p, dur) : p;
    }

    /** Optimistic copy after a play/pause click, so the UI reacts before the player confirms. */
    public MediaState withStatus(String newStatus, long now) {
        return new MediaState(app, title, artist, album, newStatus, position(now), dur, now,
                canNext, canPrev, canSeek, canToggle, sessions, pinned, key);
    }

    public MediaState withPosition(double seconds, long now) {
        return new MediaState(app, title, artist, album, status, seconds, dur, now,
                canNext, canPrev, canSeek, canToggle, sessions, pinned, key);
    }
}
