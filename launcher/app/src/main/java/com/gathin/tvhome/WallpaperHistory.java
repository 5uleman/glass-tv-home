package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * The last pictures shown, in memory only, so Previous can walk back over them and Next can return
 * over the ones it passed before asking the collection for a fresh one. Going back and then showing
 * a new picture drops the forward entries, like a browser history.
 */
final class WallpaperHistory<T> {

    static final int MAX = 10;

    private final ArrayList<T> entries = new ArrayList<>();
    private int cursor = -1;

    void clear() {
        entries.clear();
        cursor = -1;
    }

    int size() {
        return entries.size();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The picture at the cursor, or null before anything was recorded. */
    T current() {
        return cursor >= 0 && cursor < entries.size() ? entries.get(cursor) : null;
    }

    /** Records a picture that is now on screen, dropping anything that was ahead of the cursor. */
    void record(T item) {
        if (item == null) return;
        if (cursor >= 0 && cursor < entries.size() && entries.get(cursor).equals(item)) return;
        while (entries.size() > cursor + 1) entries.remove(entries.size() - 1);
        entries.add(item);
        cursor = entries.size() - 1;
        while (entries.size() > MAX) {
            entries.remove(0);
            cursor--;
        }
    }

    /** The previous picture the filter allows, moving onto it; null when there is none. */
    T previous(Predicate<T> allowed) {
        for (int i = cursor - 1; i >= 0; i--) {
            if (allowed == null || allowed.test(entries.get(i))) {
                cursor = i;
                return entries.get(i);
            }
        }
        return null;
    }

    /** The next picture the filter allows, moving onto it; null when the caller should pick a new one. */
    T next(Predicate<T> allowed) {
        if (cursor < 0) return null;
        for (int i = cursor + 1; i < entries.size(); i++) {
            if (allowed == null || allowed.test(entries.get(i))) {
                cursor = i;
                return entries.get(i);
            }
        }
        return null;
    }
}
