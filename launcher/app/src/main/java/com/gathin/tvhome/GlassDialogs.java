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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * One live-glass panel for every launcher dialog: the blurred wallpaper under a dark smoke layer,
 * Outfit weights, header, body/list content and the footer buttons inside the rounded panel. The
 * system AlertDialog button bar is never used, so no button ever sits outside the glass.
 *
 * <p>Lists scroll inside a fixed maximum height, rows keep their 2dp focus ring inside their own
 * bounds, and every dialog registers with {@link OverlayManager} so Home closes it like Back.
 */
final class GlassDialogs {

    /** 75% black over the glass: white and 85% white both keep 4.5:1 even on a white wallpaper. */
    private static final int SMOKE = 0xBF000000;

    private GlassDialogs() {
    }

    /** A list row: a title, an optional second line, and whether it is the current choice. */
    static final class Row {
        final String title;
        final String subtitle;
        final boolean selected;

        Row(String title, String subtitle, boolean selected) {
            this.title = title;
            this.subtitle = subtitle;
            this.selected = selected;
        }
    }

    interface RowListener {
        void onRow(int index);
    }

    /** Title + body message with footer buttons inside the panel. */
    static AlertDialog show(Activity activity, int iconRes, String title, String message,
                            String primary, Runnable onPrimary,
                            String secondary, Runnable onSecondary) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = panel(activity, density);
        panel.addView(header(activity, density, iconRes, title, null));

        TextView body = new TextView(activity);
        body.setText(message);
        body.setTextSize(15);
        body.setTextColor(0xF0FFFFFF);
        Fonts.apply(body, Fonts.SEMIBOLD);
        body.setLineSpacing(0, 1.35f);
        body.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        body.setPadding(0, (int) (10 * density), 0, 0);
        ScrollView bodyScroll = new ScrollView(activity);
        bodyScroll.setVerticalScrollBarEnabled(false);
        bodyScroll.addView(body);
        panel.addView(bodyScroll, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog)
                .setView(panel)
                .create();

        LinearLayout footer = footer(activity);
        if (secondary != null) {
            footer.addView(button(activity, density, secondary, false, () -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (onSecondary != null) onSecondary.run();
            }));
        }
        if (primary != null) {
            footer.addView(button(activity, density, primary, true, () -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (onPrimary != null) onPrimary.run();
            }));
        }
        LayoutParams footerParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = (int) (16 * density);
        panel.addView(footer, footerParams);

        present(activity, dialog, panel, bodyScroll, null);
        View focus = footer.getChildCount() > 0
                ? footer.getChildAt(footer.getChildCount() - 1) : null;
        if (focus != null) {
            final View target = focus;
            panel.post(target::requestFocus);
        }
        return dialog;
    }

    /**
     * A scrolling list of rows with one footer button. A row click dismisses the panel and then
     * runs its action; {@code initialFocus} is the row that starts focused (-1 for the first).
     */
    static AlertDialog showList(Activity activity, int iconRes, String title, String subtitle,
                                List<Row> rows, int initialFocus,
                                String footerLabel, Runnable onFooter, RowListener onRow) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = panel(activity, density);
        panel.addView(header(activity, density, iconRes, title, subtitle));

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog)
                .setView(panel)
                .create();

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        // clipChildren stays true: rows must scroll INSIDE this viewport, never over the header
        // or footer. clipToPadding=false only lets the focus ring use the 2dp padding band.
        scroll.setClipToPadding(false);
        int ringRoom = (int) (2 * density);
        scroll.setPadding(0, ringRoom, 0, ringRoom);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        final View[] focusRow = new View[1];
        for (int i = 0; i < rows.size(); i++) {
            final int index = i;
            View row = row(activity, density, scroll, list, rows.get(i), ringRoom, () -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (onRow != null) onRow.onRow(index);
            });
            list.addView(row);
            if (i == (initialFocus >= 0 && initialFocus < rows.size() ? initialFocus : 0)) {
                focusRow[0] = row;
            }
        }
        scroll.addView(list);
        LayoutParams scrollParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollParams.topMargin = (int) (14 * density);
        panel.addView(scroll, scrollParams);

        LinearLayout footer = footer(activity);
        if (footerLabel != null) {
            footer.addView(button(activity, density, footerLabel, false, () -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (onFooter != null) onFooter.run();
            }));
        }
        LayoutParams footerParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = (int) (12 * density);
        panel.addView(footer, footerParams);

        present(activity, dialog, panel, scroll, null);
        final View target = focusRow[0] != null ? focusRow[0]
                : (footer.getChildCount() > 0 ? footer.getChildAt(footer.getChildCount() - 1) : null);
        if (target != null) panel.post(target::requestFocus);
        return dialog;
    }

    /** A panel around caller-built content, with footer buttons inside and an optional Back action. */
    static AlertDialog showCustom(Activity activity, int iconRes, String title, String subtitle,
                                  View content, String primary, Runnable onPrimary,
                                  String secondary, Runnable onSecondary,
                                  boolean focusSecondary, Runnable onCancel, Runnable onDismissed) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout panel = panel(activity, density);
        panel.addView(header(activity, density, iconRes, title, subtitle));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        // Same rule as a list: the content is clipped to this viewport, header/footer stay clear.
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LayoutParams contentParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = (int) (12 * density);
        panel.addView(scroll, contentParams);

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.Theme_TvHome_GlassDialog)
                .setView(panel)
                .create();

        LinearLayout footer = footer(activity);
        TextView secondaryButton = null;
        if (secondary != null) {
            secondaryButton = button(activity, density, secondary, false, () -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (onSecondary != null) onSecondary.run();
            });
            footer.addView(secondaryButton);
        }
        TextView primaryButton = null;
        if (primary != null) {
            primaryButton = button(activity, density, primary, true, () -> {
                if (dialog.isShowing()) dialog.dismiss();
                if (onPrimary != null) onPrimary.run();
            });
            footer.addView(primaryButton);
        }
        LayoutParams footerParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = (int) (12 * density);
        panel.addView(footer, footerParams);

        if (onCancel != null) {
            dialog.setOnCancelListener(d -> onCancel.run());
        }
        present(activity, dialog, panel, scroll, onDismissed);
        final View target = focusSecondary && secondaryButton != null ? secondaryButton
                : (primaryButton != null ? primaryButton : secondaryButton);
        if (target != null) panel.post(target::requestFocus);
        return dialog;
    }

    /** A right-aligned row for buttons that belong inside the glass panel. */
    static LinearLayout footer(Activity activity) {
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        return footer;
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
        // No focus scale: the ring is the background, and scaling pushed it outside the footer.
        button.setOnFocusChangeListener((v, has) -> {
            if (has) SoundEffects.playNav();
        });
        return button;
    }

    // ---- building blocks -----------------------------------------------------------------------

    private static LinearLayout panel(Activity activity, float density) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (24 * density), (int) (20 * density),
                (int) (24 * density), (int) (18 * density));
        panel.setBackground(new GlassDrawable(panel, 22 * density, density, null, 0.5f,
                false, SMOKE));
        Backdrop.watch(panel);
        return panel;
    }

    private static LinearLayout header(Activity activity, float density, int iconRes,
                                       String title, String subtitle) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (iconRes != 0) {
            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconRes);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    (int) (24 * density), (int) (24 * density));
            iconParams.rightMargin = (int) (10 * density);
            header.addView(icon, iconParams);
        }
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(titleView(activity, density, title));
        if (subtitle != null) {
            TextView sub = new TextView(activity);
            sub.setText(subtitle);
            sub.setTextSize(13);
            sub.setTextColor(0xE6FFFFFF);
            Fonts.apply(sub, Fonts.SEMIBOLD);
            sub.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
            sub.setPadding(0, (int) (2 * density), 0, 0);
            column.addView(sub);
        }
        header.addView(column, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private static View row(Activity activity, float density, ScrollView scroll, ViewGroup list,
                            Row data, int ringRoom, Runnable onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding((int) (16 * density), (int) (9 * density),
                (int) (16 * density), (int) (9 * density));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x2EFFFFFF);
        focused.setStroke((int) (2 * density), 0xFFFFFFFF);
        focused.setCornerRadius(12 * density);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(data.selected ? 0x2A4A90E2 : 0x1AFFFFFF);
        if (data.selected) normal.setStroke((int) (1.5f * density), 0xFF38BDF8);
        normal.setCornerRadius(12 * density);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, focused);
        background.addState(new int[]{}, normal);
        row.setBackground(background);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(data.title);
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        Fonts.apply(title, Fonts.BOLD);
        title.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        top.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView check = null;
        if (data.selected) {
            check = new TextView(activity);
            check.setText("\u2713");
            check.setTextSize(15);
            check.setTextColor(0xFFE0F2FE);
            Fonts.apply(check, Fonts.BOLD);
            check.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            top.addView(check);
        }
        row.addView(top);

        TextView subtitle = null;
        if (data.subtitle != null) {
            subtitle = new TextView(activity);
            subtitle.setText(data.subtitle);
            subtitle.setTextSize(13);
            subtitle.setTextColor(0xD9FFFFFF);
            Fonts.apply(subtitle, Fonts.SEMIBOLD);
            subtitle.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            subtitle.setPadding(0, (int) (2 * density), 0, 0);
            row.addView(subtitle);
        }

        final TextView sub = subtitle;
        final TextView tag = check;
        row.setOnFocusChangeListener((v, has) -> {
            if (sub != null) sub.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            if (tag != null) tag.setTextColor(has ? Color.WHITE : 0xFFE0F2FE);
            if (!has) return;
            SoundEffects.playNav();
            // The framework may only reveal the row itself; add the ring band on both sides and
            // keep the whole row inside the scroll viewport.
            v.post(() -> {
                // Rows live in the list, which the ScrollView lays out below its top padding;
                // the scroll offset counts from the content origin, so add that padding back.
                int padTop = scroll.getPaddingTop();
                int viewport = scroll.getHeight() - padTop - scroll.getPaddingBottom();
                int contentHeight = list.getHeight() + padTop;
                int target = GlassDialogLayout.scrollTarget(v.getTop() + padTop,
                        v.getBottom() + padTop, scroll.getScrollY(), viewport, ringRoom,
                        contentHeight);
                if (target != scroll.getScrollY()) scroll.smoothScrollTo(0, target);
            });
        });
        row.setOnClickListener(v -> onClick.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (5 * density);
        row.setLayoutParams(params);
        return row;
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

    /**
     * Sizes the panel to its content, capped at 82% of the screen. Above the cap the given scroll
     * area takes the remaining height and scrolls, so the header and footer stay in place.
     */
    private static void present(Activity activity, AlertDialog dialog, LinearLayout panel,
                                ScrollView scroll, Runnable onDismissed) {
        float density = activity.getResources().getDisplayMetrics().density;
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        // Real screen with a 56dp top/bottom margin; never a percentage of an assumed window.
        int width = Math.min((int) (520 * density), screenW - (int) (120 * density));
        int maxHeight = GlassDialogLayout.maxPanelHeight(screenH, density, 56);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED);
        int height = Math.min(panel.getMeasuredHeight() + (int) (2 * density), maxHeight);
        if (scroll != null && panel.getMeasuredHeight() > maxHeight) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) scroll.getLayoutParams();
            params.height = 0;
            params.weight = 1f;
            scroll.setLayoutParams(params);
        }

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.setLayout(width, height);
        }
        final Object overlay = OverlayManager.register(() -> {
            if (dialog.isShowing()) dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> {
            OverlayManager.unregister(overlay);
            if (onDismissed != null) onDismissed.run();
        });
    }

    /** A LayoutParams alias that reads clearly in the builder methods. */
    private static final class LayoutParams extends LinearLayout.LayoutParams {
        LayoutParams(int width, int height) {
            super(width, height);
        }
    }
}
