package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The recent-apps deck: a scrim over Home with a row of glass cards, one per recently used app.
 * Home twice, or the top-bar button, opens it; Left/Right moves, OK opens the app, Back closes.
 * Built in code so MainActivity only has to add it to its root and forward Back.
 */
final class RecentsDeck {

    private final Activity activity;
    private final FrameLayout root;
    private final float density;
    private final boolean reducedMotion;
    private FrameLayout scrim;
    private boolean open;
    private boolean clearing;

    RecentsDeck(Activity activity, FrameLayout root, float density, boolean reducedMotion) {
        this.activity = activity;
        this.root = root;
        this.density = density;
        this.reducedMotion = reducedMotion;
    }

    boolean isShowing() {
        return open;
    }

    /** Builds the deck fresh, so it reflects the last launches, and fades it in. */
    void show() {
        if (open) return;
        clearing = false;
        List<RecentAppItem> items = RecentsDeckItems.load(activity);

        scrim = new FrameLayout(activity);
        scrim.setBackgroundColor(0xD9000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnParams.gravity = Gravity.CENTER;
        columnParams.leftMargin = (int) (36 * density);
        columnParams.rightMargin = (int) (36 * density);
        scrim.addView(column, columnParams);

        TextView title = text(activity.getString(R.string.recents_title), 26, true);
        title.setPadding(0, 0, 0, (int) (18 * density));
        column.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int) (8 * density), (int) (8 * density), (int) (8 * density), (int) (8 * density));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View first = null;
        if (items.isEmpty()) {
            TextView empty = text(activity.getString(R.string.recents_empty), 16, false);
            empty.setGravity(Gravity.CENTER);
            empty.setAlpha(0.85f);
            empty.setPadding((int) (12 * density), (int) (30 * density), (int) (12 * density), (int) (30 * density));
            column.addView(empty);
        } else {
            for (int i = 0; i < items.size(); i++) {
                View card = card(items.get(i), scroll, i == 0);
                row.addView(card);
                if (first == null) first = card;
            }
            row.addView(clearButton(items, scroll));
            column.addView(scroll);
        }

        TextView hint = text(activity.getString(R.string.recents_hint), 13, false);
        hint.setAlpha(0.7f);
        hint.setPadding(0, (int) (16 * density), 0, 0);
        column.addView(hint);

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(reducedMotion ? 0 : 160).start();
        open = true;

        if (first != null) {
            final View target = first;
            scrim.post(() -> target.requestFocus());
        }
    }

    void close() {
        if (!open) return;
        open = false;
        FrameLayout panel = scrim;
        scrim = null;
        if (panel == null) return;
        panel.animate().alpha(0f).setDuration(reducedMotion ? 0 : 140).withEndAction(() -> {
            panel.clearAnimation();
            root.removeView(panel);
        }).start();
    }

    private View card(RecentAppItem item, HorizontalScrollView scroll, boolean firstCard) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(0, (int) (22 * density), 0, (int) (18 * density));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) (170 * density), (int) (210 * density));
        params.setMarginEnd((int) (12 * density));
        card.setLayoutParams(params);

        card.setBackground(new GlassDrawable(card, 22 * density, density, null, 0.6f));
        Backdrop.watch(card);
        attachFocus(card, scroll);

        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(item.icon);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                (int) (64 * density), (int) (64 * density));
        card.addView(icon, iconParams);

        TextView label = text(item.label, 14, true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setMaxWidth((int) (146 * density));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (14 * density);
        card.addView(label, labelParams);

        card.setOnClickListener(v -> openApp(item));
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return firstCard;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    close();
                    return true;
                default:
                    return false;
            }
        });
        return card;
    }

    private View clearButton(final List<RecentAppItem> items, HorizontalScrollView scroll) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(0, (int) (22 * density), 0, (int) (18 * density));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) (150 * density), (int) (210 * density));
        params.setMarginEnd((int) (12 * density));
        card.setLayoutParams(params);

        card.setBackground(new GlassDrawable(card, 22 * density, density, null, 0.6f));
        Backdrop.watch(card);
        attachFocus(card, scroll);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_delete);
        card.addView(icon, new LinearLayout.LayoutParams(
                (int) (48 * density), (int) (48 * density)));

        TextView label = text(activity.getString(R.string.recents_clear_all), 15, true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setMaxWidth((int) (126 * density));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (14 * density);
        card.addView(label, labelParams);

        card.setOnClickListener(v -> clearAll(items));
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    clearAll(items);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    close();
                    return true;
                default:
                    return false;
            }
        });
        return card;
    }

    private void attachFocus(final View view, final HorizontalScrollView scroll) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(22 * density);
        ring.setStroke((int) (2 * density), 0xFFFFFFFF);
        ring.setColor(Color.TRANSPARENT);
        view.setForeground(ring);
        view.getForeground().setAlpha(0);

        view.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                    .setDuration(reducedMotion ? 0 : 140).start();
            v.getForeground().setAlpha(has ? 255 : 0);
            if (has) {
                SoundEffects.playNav();
                scroll.smoothScrollTo(Math.max(0, v.getLeft() - (int) (40 * density)), 0);
            }
        });
    }

    private void clearAll(List<RecentAppItem> items) {
        if (clearing) return;
        clearing = true;
        SoundEffects.playSelect();
        RecentsCleaner.clear(activity, items, (apps, freedBytes) -> {
            clearing = false;
            long mb = freedBytes / (1024L * 1024L);
            String message;
            if (apps == 0) {
                message = activity.getString(R.string.recents_nothing_cleared);
            } else if (mb <= 0) {
                message = activity.getString(R.string.recents_freed_none);
            } else if (mb < 5) {
                message = activity.getString(R.string.recents_freed_little, mb);
            } else {
                message = activity.getString(R.string.recents_freed, mb);
            }
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            close();
        });
    }

    private void openApp(RecentAppItem item) {
        if (item.launchIntent == null) return;
        SoundEffects.playSelect();
        try {
            RecentAppsManager.recordAppLaunch(activity, item.packageName);
            activity.startActivity(new Intent(item.launchIntent)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            close();
        } catch (Exception e) {
            Toast.makeText(activity, activity.getString(R.string.cant_open, item.label), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value, float sizeSp, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(Color.WHITE);
        view.setTypeface(activity.getResources().getFont(R.font.outfit),
                bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        return view;
    }
}
