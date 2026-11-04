package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Recent apps as a cover-flow overlay on Home: a blurred, darkened wallpaper behind a big focused
 * near-white card, smaller dark-glass cards either side, a live RAM pill and an always-visible
 * Clear all. Up closes the focused app, OK opens it, Down moves to Clear all, Back closes the deck.
 * Only {@link RecentsCleaner} is used to close apps: no force-stop, no shell, no root.
 */
final class RecentsDeck {

    private static final long RAM_REFRESH_MS = 1000;
    private static final int CARD_W_DP = 300;
    private static final int CARD_H_DP = 340;
    private static final int OVERLAP_DP = 34;
    private static final float IDLE_SCALE = 0.82f;

    private final Activity activity;
    private final FrameLayout root;
    private final float density;
    private final boolean reducedMotion;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private DeckLayer layer;
    private LinearLayout column;
    private HorizontalScrollView scroll;
    private LinearLayout row;
    private LinearLayout clearPill;
    private TextView ramText, freedText, hintText, emptyText;
    private ProgressBar ramBar;
    private View lastFocused;
    private boolean open;
    private boolean clearing;
    private final List<View> cards = new ArrayList<>();
    private final Runnable ramTick = new Runnable() {
        @Override
        public void run() {
            if (!open) return;
            updateRam();
            handler.postDelayed(this, RAM_REFRESH_MS);
        }
    };
    private final Runnable hideFreed = () -> {
        if (freedText != null) freedText.setVisibility(View.GONE);
    };

    RecentsDeck(Activity activity, FrameLayout root, float density, boolean reducedMotion) {
        this.activity = activity;
        this.root = root;
        this.density = density;
        this.reducedMotion = reducedMotion;
    }

    boolean isShowing() {
        return open;
    }

    /** Builds the deck fresh so it reflects the last launches, then fades it in. */
    void show() {
        if (open) return;
        clearing = false;
        cards.clear();
        lastFocused = null;
        List<RecentAppItem> items = RecentsDeckItems.load(activity);

        layer = new DeckLayer(activity);
        layer.setFocusable(true);
        layer.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                close();
                return true;
            }
            return false;
        });
        root.addView(layer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        layer.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int side = (int) (42 * density);
        column.setPadding(side, (int) (28 * density), side, (int) (26 * density));

        // Header: title on the left, glass RAM pill with the inline message underneath it.
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(activity.getString(R.string.recents_title), 26, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.topMargin = (int) (6 * density);
        header.addView(title, titleParams);

        LinearLayout ramPill = new LinearLayout(activity);
        ramPill.setOrientation(LinearLayout.VERTICAL);
        ramPill.setPadding((int) (16 * density), (int) (9 * density),
                (int) (16 * density), (int) (10 * density));
        ramPill.setBackground(new GlassDrawable(ramPill, 16 * density, density, null, 0.5f));
        Backdrop.watch(ramPill);
        header.addView(ramPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ramText = text("", 14, true);
        ramPill.addView(ramText);

        ramBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        ramBar.setMax(100);
        ramBar.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        ramBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x3DFFFFFF));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                (int) (210 * density), (int) (3 * density));
        barParams.topMargin = (int) (6 * density);
        ramPill.addView(ramBar, barParams);

        freedText = text("", 12, false);
        freedText.setTextColor(0xFF7DD3FC);
        freedText.setVisibility(View.GONE);
        freedText.setGravity(Gravity.END);
        LinearLayout.LayoutParams freedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        freedParams.topMargin = (int) (6 * density);
        freedParams.bottomMargin = (int) (2 * density);
        ramPill.addView(freedText, freedParams);

        if (items.isEmpty()) {
            addEmptyText();
        } else {
            scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setClipChildren(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setFillViewport(true);
            column.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            int focusWidth = (int) (CARD_W_DP * density);
            int rowSide = Math.max(0, (getScreenWidth() - focusWidth) / 2);
            row.setPadding(rowSide, 0, rowSide, 0);
            scroll.addView(row, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int i = 0; i < items.size(); i++) {
                View card = card(items.get(i), i == 0);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        (int) (CARD_W_DP * density), (int) (CARD_H_DP * density));
                if (i > 0) params.leftMargin = -(int) (OVERLAP_DP * density);
                row.addView(card, params);
                cards.add(card);
            }

            hintText = text(activity.getString(R.string.recents_hint), 13, false);
            hintText.setAlpha(0.75f);
            hintText.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = (int) (6 * density);
            hintParams.bottomMargin = (int) (10 * density);
            column.addView(hintText, hintParams);

            clearPill = new LinearLayout(activity);
            clearPill.setOrientation(LinearLayout.HORIZONTAL);
            clearPill.setGravity(Gravity.CENTER);
            clearPill.setFocusable(true);
            clearPill.setClickable(true);
            clearPill.setPadding((int) (26 * density), (int) (12 * density),
                    (int) (26 * density), (int) (12 * density));
            clearPill.setBackground(new GlassDrawable(clearPill, 24 * density, density, null, 0.5f));
            Backdrop.watch(clearPill);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(24 * density);
            ring.setStroke((int) (2 * density), 0xFFFFFFFF);
            ring.setColor(Color.TRANSPARENT);
            clearPill.setForeground(ring);
            clearPill.getForeground().setAlpha(0);

            ImageView dust = new ImageView(activity);
            dust.setImageResource(R.drawable.ic_delete);
            LinearLayout.LayoutParams dustParams = new LinearLayout.LayoutParams(
                    (int) (20 * density), (int) (20 * density));
            dustParams.rightMargin = (int) (9 * density);
            clearPill.addView(dust, dustParams);
            clearPill.addView(text(activity.getString(R.string.recents_clear_all), 16, true));

            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pillParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(clearPill, pillParams);

            clearPill.setOnFocusChangeListener((v, has) -> {
                animateScale(v, has ? 1.05f : 1f);
                v.getForeground().setAlpha(has ? 255 : 0);
                if (has) SoundEffects.playNav();
            });
            clearPill.setOnClickListener(v -> clearAll(items));
            clearPill.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    View target = lastFocused != null && cards.contains(lastFocused)
                            ? lastFocused : (cards.isEmpty() ? null : cards.get(0));
                    if (target != null) {
                        target.requestFocus();
                        return true;
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    close();
                    return true;
                }
                return false;
            });
        }

        updateRam();
        layer.setAlpha(0f);
        layer.animate().alpha(1f).setDuration(reducedMotion ? 0 : 180).start();
        open = true;
        handler.post(ramTick);

        if (!cards.isEmpty()) {
            final View first = cards.get(0);
            layer.post(() -> first.requestFocus());
        } else {
            layer.post(layer::requestFocus);
        }
    }

    void close() {
        if (!open) return;
        open = false;
        handler.removeCallbacks(ramTick);
        handler.removeCallbacks(hideFreed);
        DeckLayer panel = layer;
        layer = null;
        column = null;
        scroll = null;
        row = null;
        clearPill = null;
        ramText = null;
        ramBar = null;
        freedText = null;
        hintText = null;
        emptyText = null;
        cards.clear();
        if (panel == null) return;
        panel.animate().alpha(0f).setDuration(reducedMotion ? 0 : 150).withEndAction(() -> {
            panel.clearAnimation();
            root.removeView(panel);
        }).start();
    }

    private View card(final RecentAppItem item, boolean firstCard) {
        final FrameLayout card = new FrameLayout(activity);
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(item);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding((int) (18 * density), 0, (int) (18 * density), 0);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ImageView icon = new ImageView(activity);
        icon.setImageDrawable(item.icon);
        content.addView(icon, new LinearLayout.LayoutParams(
                (int) (92 * density), (int) (92 * density)));

        final TextView label = text(item.label, 17, true);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setMaxWidth((int) ((CARD_W_DP - 44) * density));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (18 * density);
        content.addView(label, labelParams);

        paintCard(card, label, false);

        card.setOnClickListener(v -> openApp(item));
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int index = cards.indexOf(v);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (index > 0) cards.get(index - 1).requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (index >= 0 && index + 1 < cards.size()) cards.get(index + 1).requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                closeCard(card, item);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (clearPill != null) clearPill.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                close();
                return true;
            }
            return false;
        });

        card.setOnFocusChangeListener((v, has) -> {
            FrameLayout c = (FrameLayout) v;
            TextView name = (TextView) ((LinearLayout) c.getChildAt(0)).getChildAt(1);
            paintCard(c, name, has);
            animateScale(c, has ? 1f : IDLE_SCALE);
            if (has) {
                lastFocused = v;
                SoundEffects.playNav();
                if (scroll != null) {
                    int center = v.getLeft() + v.getWidth() / 2 - scroll.getWidth() / 2;
                    scroll.smoothScrollTo(Math.max(0, center), 0);
                }
            }
        });
        card.setScaleX(firstCard ? 1f : IDLE_SCALE);
        card.setScaleY(firstCard ? 1f : IDLE_SCALE);
        return card;
    }

    /** The focused card is near-white with dark text; the others are dark glass with white text. */
    private void paintCard(FrameLayout card, TextView label, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius((focused ? 28 : 24) * density);
        if (focused) {
            bg.setColor(0xFFF4F5F7);
        } else {
            bg.setColor(0xF01A1E26);
            bg.setStroke((int) (1 * density), 0x2EFFFFFF);
        }
        card.setBackground(bg);
        card.setElevation((focused ? 16 : 3) * density);
        label.setTextColor(focused ? 0xFF111827 : 0xFFFFFFFF);
        label.setShadowLayer(focused ? 0 : 3 * density, 0, 1 * density, 0xCC000000);
    }

    private void animateScale(View view, float scale) {
        view.animate().scaleX(scale).scaleY(scale)
                .setDuration(reducedMotion ? 0 : 160).start();
    }

    private void closeCard(final View card, final RecentAppItem item) {
        if (clearing) return;
        SoundEffects.playSelect();
        RecentsCleaner.closeOne(activity, item, (closed, keptForVpn, freedBytes) -> {
            if (keptForVpn) {
                showInline(activity.getString(R.string.recents_vpn_kept, item.label));
            } else if (!closed) {
                showInline(activity.getString(R.string.recents_nothing_cleared));
            } else {
                long mb = freedBytes / (1024L * 1024L);
                if (mb >= 1) showInline(activity.getString(R.string.recents_freed, mb));
                removeCard(card);
            }
            updateRam();
        });
    }

    private void removeCard(final View card) {
        int index = cards.indexOf(card);
        if (index < 0) return;
        final ViewGroup parent = (ViewGroup) card.getParent();
        card.animate().translationY(-(int) (420 * density)).alpha(0f)
                .setDuration(reducedMotion ? 0 : 220).withEndAction(() -> {
                    if (parent != null) parent.removeView(card);
                }).start();
        cards.remove(card);
        if (lastFocused == card) lastFocused = null;
        if (cards.isEmpty()) {
            showEmptyAfterClear();
            return;
        }
        final View next = cards.get(Math.min(index, cards.size() - 1));
        handler.postDelayed(next::requestFocus, reducedMotion ? 0 : 240);
    }

    private void clearAll(final List<RecentAppItem> items) {
        if (clearing || cards.isEmpty()) return;
        clearing = true;
        SoundEffects.playSelect();
        final String vpnKept = RecentsCleaner.vpnKeptLabel(activity, items);
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
            if (vpnKept != null) {
                message = message + " - " + activity.getString(R.string.recents_vpn_kept, vpnKept);
            }
            showInline(message);
            for (int i = 0; i < cards.size(); i++) {
                animateAwayOnly(cards.get(i));
            }
            cards.clear();
            lastFocused = null;
            showEmptyAfterClear();
            updateRam();
        });
    }

    private void animateAwayOnly(View card) {
        card.animate().translationY(-(int) (420 * density)).alpha(0f)
                .setDuration(reducedMotion ? 0 : 220).start();
    }

    private void showEmptyAfterClear() {
        if (layer == null || column == null) return;
        if (scroll != null) scroll.setVisibility(View.GONE);
        if (clearPill != null) clearPill.setVisibility(View.GONE);
        if (hintText != null) hintText.setVisibility(View.GONE);
        addEmptyText();
        layer.requestFocus();
    }

    private void addEmptyText() {
        emptyText = text(activity.getString(R.string.recents_empty), 16, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setAlpha(0.85f);
        column.addView(emptyText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f));
    }

    private void showInline(String message) {
        if (freedText == null) return;
        handler.removeCallbacks(hideFreed);
        freedText.setText(message);
        freedText.setVisibility(View.VISIBLE);
        handler.postDelayed(hideFreed, 4500);
    }

    private void updateRam() {
        if (!open || ramText == null) return;
        RecentAppsManager.MemoryStats stats = RecentAppsManager.getMemoryStats(activity);
        ramText.setText(stats.formattedText);
        if (ramBar != null) ramBar.setProgress(stats.percentUsed);
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
            showInline(activity.getString(R.string.cant_open, item.label));
        }
    }

    private int getScreenWidth() {
        return activity.getResources().getDisplayMetrics().widthPixels;
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

    /** The wallpaper blurred underneath, under a strong scrim so Home's clock, pills and dock vanish. */
    private static final class DeckLayer extends FrameLayout {
        private final Backdrop.Fill fill = new Backdrop.Fill();
        private final RectF bounds = new RectF();

        DeckLayer(Activity activity) {
            super(activity);
            setWillNotDraw(false);
            setClipChildren(false);
            Backdrop.watch(this);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            bounds.set(0, 0, getWidth(), getHeight());
            fill.draw(canvas, this, bounds, 0f, 1f);
            canvas.drawColor(0xD6000000);
        }
    }
}
