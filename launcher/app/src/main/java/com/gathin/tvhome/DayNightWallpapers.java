package com.gathin.tvhome;

import java.io.File;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses which pictures of one online collection may rotate at a given local time. Pure: no
 * Android calls and no image analysis, so the rules can be tested on the JVM.
 */
final class DayNightWallpapers {
    static final int DAY_START_HOUR = 6;
    static final int NIGHT_START_HOUR = 18;
    static final double DAY_MIN_LUMA = 115.0;
    static final double NIGHT_MAX_LUMA = 85.0;
    static final int MIN_PHOTOS = 5;

    enum Tone { DAY, NIGHT, BOTH }

    enum Period { DAY, NIGHT }

    /** One cached picture with the tone data from its catalog entry. */
    static final class Photo {
        final File file;
        final double luma;
        final Tone tone;

        Photo(File file, double luma, Tone tone) {
            this.file = file;
            this.luma = luma;
            this.tone = tone;
        }
    }

    private DayNightWallpapers() {
    }

    static Period periodAt(LocalTime time) {
        int hour = time.getHour();
        return hour >= DAY_START_HOUR && hour < NIGHT_START_HOUR ? Period.DAY : Period.NIGHT;
    }

    static Tone toneFor(double luma) {
        if (luma >= DAY_MIN_LUMA) return Tone.DAY;
        if (luma <= NIGHT_MAX_LUMA) return Tone.NIGHT;
        return Tone.BOTH;
    }

    static boolean matches(Tone tone, Period period) {
        if (tone == Tone.BOTH) return true;
        return period == Period.DAY ? tone == Tone.DAY : tone == Tone.NIGHT;
    }

    /**
     * The pictures allowed in this period. With the switch off every picture is allowed. When
     * fewer than {@link #MIN_PHOTOS} match, the closest others by luma to the period's threshold
     * are added, so a collection can never run dry; a collection smaller than that uses all of it.
     */
    static List<Photo> allowed(boolean enabled, LocalTime time, List<Photo> photos) {
        List<Photo> all = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
        if (!enabled || all.isEmpty()) return all;

        Period period = periodAt(time);
        List<Photo> matching = new ArrayList<>();
        for (Photo photo : all) {
            if (matches(photo.tone, period)) matching.add(photo);
        }
        if (all.size() < MIN_PHOTOS) return all;
        if (matching.size() >= MIN_PHOTOS) return matching;

        double target = period == Period.DAY ? DAY_MIN_LUMA : NIGHT_MAX_LUMA;
        List<Photo> closest = new ArrayList<>();
        for (Photo photo : all) {
            if (!matching.contains(photo)) closest.add(photo);
        }
        closest.sort((a, b) -> {
            int byDistance = Double.compare(Math.abs(a.luma - target), Math.abs(b.luma - target));
            return byDistance != 0 ? byDistance : Double.compare(a.luma, b.luma);
        });
        for (Photo photo : closest) {
            if (matching.size() >= MIN_PHOTOS) break;
            matching.add(photo);
        }
        return matching;
    }
}
