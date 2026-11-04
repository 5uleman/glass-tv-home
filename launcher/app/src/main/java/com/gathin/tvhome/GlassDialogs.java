package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One helper for the live-glass message dialogs: the blurred wallpaper with a smoke layer, the same
 * Outfit weights as the rest of the glass UI, and checkable footer buttons inside the rounded
 * panel. The system AlertDialog button bar is never used, so nothing sits below the glass.
 */
final class GlassDialogs {

    private GlassDialogs() {
    }

    static AlertDialog show(Activity activity, int iconRes, String title, String message,
                            String primary, Runnable onPrimary,
                            String secondary, Runnable onSecondary) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (24 * density), (int) (20 * density),
                (int) (24 * density), (int) (18 * density));
        panel.setBackground(new GlassDrawable(panel, 22 * density, density, null, 0.5f,
                false, 0x99000000));
        Backdrop.watch(panel);

        if (iconRes != 0) {
            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconRes);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    (int) (24 * density), (int) (24 * density));
            iconParams.rightMargin = (int) (10 * density);
            header.addView(icon, iconParams);
            header.addView(titleView(activity, density, title));
            panel.addView(header);
        } else {
            panel.addView(titleView(activity, density, title));
        }

        TextView body = new TextView(activity);
        body.setText(message);
        body.setTextSize(15);
        body.setTextColor(0xF0FFFFFF);
        Fonts.apply(body, Fonts.SEMIBOLD);
        body.setLineSpacing(0, 1.35f);
        body.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        body.setPadding(0, (int) (10 * density), 0, 0);
        panel.addView(body);

        final AlertDialog[] holder = new AlertDialog[1];
        LinearLayout footer = footer(activity);
        if (secondary != null) {
            footer.addView(button(activity, density, secondary, false, () -> {
                if (holder[0] != null) holder[0].dismiss();
                if (onSecondary != null) onSecondary.run();
            }));
        }
        if (primary != null) {
            footer.addView(button(activity, density, primary, true, () -> {
                if (holder[0] != null) holder[0].dismiss();
                if (onPrimary != null) onPrimary.run();
            }));
        }
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = (int) (16 * density);
        panel.addView(footer, footerParams);

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog)
                .setView(panel)
                .create();
        holder[0] = dialog;
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            int width = Math.min((int) (500 * density),
                    activity.getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        View focus = footer.getChildCount() > 0
                ? footer.getChildAt(footer.getChildCount() - 1) : null;
        if (focus != null) {
            final View target = focus;
            panel.post(target::requestFocus);
        }
        return dialog;
    }

    /** A right-aligned row for buttons that belong inside the glass panel. */
    static LinearLayout footer(Activity activity) {
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        return footer;
    }

    private static TextView titleView(Activity activity, float density, String title) {
        TextView view = new TextView(activity);
        view.setText(title);
        view.setTextSize(19);
        view.setTextColor(Color.WHITE);
        Fonts.apply(view, Fonts.BOLD);
        view.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        return view;
    }

    static TextView button(Activity activity, float density, String label, boolean primary, Runnable action) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        Fonts.apply(button, primary ? Fonts.BOLD : Fonts.SEMIBOLD);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setClickable(true);
        button.setMinWidth((int) (120 * density));
        button.setPadding((int) (22 * density), (int) (11 * density),
                (int) (22 * density), (int) (11 * density));
        button.setShadowLayer(3 * density, 0, 1 * density, 0xAA000000);

        GradientDrawable on = new GradientDrawable();
        on.setColor(0x2EFFFFFF);
        on.setStroke((int) (2 * density), 0xFFFFFFFF);
        on.setCornerRadius(14 * density);
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x1AFFFFFF);
        off.setCornerRadius(14 * density);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, on);
        background.addState(new int[]{}, off);
        button.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = (int) (10 * density);
        button.setLayoutParams(params);

        button.setOnClickListener(v -> action.run());
        // No focus scale: the 2dp ring is the background, and scaling a button that is flush with
        // the footer's right edge pushed the ring outside it (clipChildren), cutting the right side.
        button.setOnFocusChangeListener((v, has) -> {
            if (has) SoundEffects.playNav();
        });
        return button;
    }
}
