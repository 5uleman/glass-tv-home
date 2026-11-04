package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

/**
 * Outfit is a variable font whose default instance is Thin (wght 100). A bare Typeface.BOLD on it
 * does not move the weight axis, so text built in code comes out thin unless the variation setting
 * is applied. Every code-built label goes through here, with the same weights the XML uses.
 */
final class Fonts {

    static final int REGULAR = 400;
    static final int MEDIUM = 500;
    static final int SEMIBOLD = 600;
    static final int BOLD = 700;

    private Fonts() {
    }

    static Typeface outfit(Context context) {
        return context.getResources().getFont(R.font.outfit);
    }

    static void apply(TextView view, int weight) {
        view.setTypeface(outfit(view.getContext()));
        view.setFontVariationSettings("'wght' " + weight);
    }
}
