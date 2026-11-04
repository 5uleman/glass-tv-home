package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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
    private static final int CARD_W_DP = 340;
    private static final int CARD_H_DP = 190;
    private static final int OVERLAP_DP = 34;
    private static final float IDLE_SCALE = 0.82f;

    private final Activity activity;
    private final FrameLayout root;
    private final float density;
    private final boolean reducedMotion;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private DeckLayer layer;
    private LinearLayout column;
    /** Fixed card area: the card row and the empty message take turns inside it, so no reflow. */
    private FrameLayout stage;
    private HorizontalScrollView scroll;
    private LinearLayout row;
    private LinearLayout clearPill;
    private TextView ramText, hintText, emptyText;
    private ProgressBar ramBar;
    private String hintDefault;
    private View lastFocused;
    private boolean open;
    private boolean clearing;
    private boolean emptyScheduled;
    private boolean emptyShown;
    private Object overlayToken;
    private final Runnable emptyTransition = this::runEmptyTransition;
    private final List<View> cards = new ArrayList<>();
    private final Runnable ramTick = new Runnable() {
        @Override
        public void run() {
            if (!open) return;
            updateRam();
            handler.postDelayed(this, RAM_REFRESH_MS);
        }
    };
    /** Puts the line under the cards back to the navigation hint, or hides it in the empty state. */
    private final Runnable hideFreed = () -> {
        if (hintText == null) return;
        if (cards.isEmpty()) {
            // INVISIBLE keeps the hint's slot, so the risen empty message does not move.
            hintText.setVisibility(View.INVISIBLE);
            return;
        }
        hintText.setText(hintDefault);
        hintText.setTextColor(Color.WHITE);
        hintText.setAlpha(0.75f);
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
        emptyScheduled = false;
        emptyShown = false;
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
        column.setPadding(side, (int) (26 * density), side, (int) (24 * density));

        // Header: title on the left, glass RAM pill with the inline message underneath it.
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        column.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(activity.getString(R.string.recents_title), 26, Fonts.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.topMargin = (int) (6 * density);
        header.addView(title, titleParams);

        LinearLayout ramPill = new LinearLayout(activity);
        ramPill.setOrientation(LinearLayout.VERTICAL);
        ramPill.setPadding((int) (16 * density), (int) (9 * density),
                (int) (16 * density), (int) (10 * density));
        // Quieter than the cards: more dark smoke and less of the white veil, so the pill recedes
        // against the deck scrim while the white RAM text still keeps 4.5:1.
        GlassDrawable ramGlass = new GlassDrawable(ramPill, 16 * density, density, null, 0.5f,
                false, 0x8C000000);
        ramGlass.setVeilColor(0x08FFFFFF);
        ramPill.setBackground(ramGlass);
        Backdrop.watch(ramPill);
        header.addView(ramPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ramText = text("", 14, Fonts.SEMIBOLD);
        ramPill.addView(ramText);

        ramBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        ramBar.setMax(100);
        ramBar.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        ramBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x3DFFFFFF));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                (int) (210 * density), (int) (3 * density));
        barParams.topMargin = (int) (6 * density);
        ramPill.addView(ramBar, barParams);

        // One fixed slot for whichever of the card row and the empty message is showing.
        stage = new FrameLayout(activity);
        column.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (items.isEmpty()) {
            addEmptyText(false);
        } else {
            scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.setClipChildren(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setFillViewport(true);
            stage.addView(scroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            int focusWidth = (int) (CARD_W_DP * density);
            int rowSide = Math.max(0, (getScreenWidth() - focusWidth) / 2);
            row.setPadding(rowSide, 0, rowSide, 0);
            HorizontalScrollView.LayoutParams rowParams = new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.gravity = Gravity.CENTER_VERTICAL;
            scroll.addView(row, rowParams);

            for (int i = 0; i < items.size(); i++) {
                View card = card(items.get(i), i == 0);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        (int) (CARD_W_DP * density), (int) (CARD_H_DP * density));
                if (i > 0) params.leftMargin = -(int) (OVERLAP_DP * density);
                row.addView(card, params);
                cards.add(card);
            }

            hintText = text(activity.getString(R.string.recents_hint), 13, Fonts.SEMIBOLD);
            hintText.setAlpha(0.75f);
            hintText.setGravity(Gravity.CENTER);
            hintText.setMaxLines(2);
            hintText.setEllipsize(TextUtils.TruncateAt.END);
            hintDefault = hintText.getText().toString();
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
            clearPill.setBackground(new GlassDrawable(clearPill, 24 * density, density, null, 0.5f,
                    false, 0x4D000000));
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
            clearPill.addView(text(activity.getString(R.string.recents_clear_all), 16, Fonts.SEMIBOLD));

            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pillParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(clearPill, pillParams);

            clearPill.setOnFocusChangeListener((v, has) -> {
                // The ring is drawn at the pill's bounds; scaling it here let the column clip it.
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
        overlayToken = OverlayManager.register(this::close);
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
        OverlayManager.unregister(overlayToken);
        overlayToken = null;
        handler.removeCallbacks(ramTick);
        handler.removeCallbacks(hideFreed);
        handler.removeCallbacks(emptyTransition);
        DeckLayer panel = layer;
        layer = null;
        column = null;
        stage = null;
        scroll = null;
        row = null;
        clearPill = null;
        ramText = null;
        ramBar = null;
        hintText = null;
        hintDefault = null;
        emptyText = null;
        emptyScheduled = false;
        emptyShown = false;
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
        card.setClipToOutline(true);

        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(20 * density);
        base.setColor(0xF0141821);
        base.setStroke((int) density, 0x2EFFFFFF);
        card.setBackground(base);

        final ImageView face = new ImageView(activity);
        face.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(face, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ImageView icon = new ImageView(activity);
        icon.setImageDrawable(item.icon);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                (int) (76 * density), (int) (76 * density));
        iconParams.gravity = Gravity.CENTER;
        iconParams.bottomMargin = (int) (18 * density);
        card.addView(icon, iconParams);

        // Dims a side card without touching its name, which sits above this layer.
        final View dim = new View(activity);
        dim.setBackgroundColor(0x66000000);
        card.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = new View(activity);
        scrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6000000, 0x00000000}));
        FrameLayout.LayoutParams scrimParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (78 * density));
        scrimParams.gravity = Gravity.BOTTOM;
        card.addView(scrim, scrimParams);

        final TextView label = text(item.label, 16, Fonts.SEMIBOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setMaxWidth((int) ((CARD_W_DP - 32) * density));
        label.setGravity(Gravity.BOTTOM);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.gravity = Gravity.BOTTOM | Gravity.START;
        labelParams.leftMargin = (int) (16 * density);
        labelParams.rightMargin = (int) (16 * density);
        labelParams.bottomMargin = (int) (13 * density);
        card.addView(label, labelParams);

        if (item.banner != null) {
            face.setImageDrawable(item.banner);
            icon.setVisibility(View.GONE);
        }

        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(20 * density);
        ring.setStroke((int) (3 * density), 0xFFFFFFFF);
        ring.setColor(Color.TRANSPARENT);
        card.setForeground(ring);
        card.getForeground().setAlpha(0);
        card.setElevation(3 * density);

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
            card.getForeground().setAlpha(has ? 255 : 0);
            dim.setAlpha(has ? 0f : 1f);
            card.setElevation((has ? 16 : 3) * density);
            animateScale(card, has ? 1f : IDLE_SCALE);
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
            for (int i = 0; i < cards.size(); i++) {
                animateAwayOnly(cards.get(i));
            }
            cards.clear();
            lastFocused = null;
            showEmptyAfterClear();
            showInline(message);
            updateRam();
        });
    }

    private void animateAwayOnly(View card) {
        card.animate().translationY(-(int) (420 * density)).alpha(0f)
                .setDuration(reducedMotion ? 0 : 220).start();
    }

    /** Cards have flown away: fade the leftover chrome, then let the empty message rise in. */
    private void showEmptyAfterClear() {
        if (layer == null || column == null || emptyScheduled || emptyShown) return;
        emptyScheduled = true;
        handler.postDelayed(emptyTransition, reducedMotion ? 0 : 240);
    }

    private void runEmptyTransition() {
        if (!open || layer == null || column == null || emptyShown) return;
        emptyShown = true;
        // The layer's key listener owns Back, so it keeps working through the whole animation.
        layer.requestFocus();
        int fade = reducedMotion ? 0 : 160;
        if (scroll != null) scroll.animate().alpha(0f).setDuration(fade).start();
        if (clearPill != null) {
            clearPill.setFocusable(false);
            clearPill.animate().alpha(0f).setDuration(fade)
                    .withEndAction(() -> clearPill.setVisibility(View.INVISIBLE)).start();
        }
        if (hintText != null) {
            hintText.animate().alpha(0f).setDuration(fade)
                    .withEndAction(() -> hintText.setVisibility(View.INVISIBLE)).start();
        }
        addEmptyText(true);
        if (emptyText == null) return;
        emptyText.animate().alpha(0.85f).translationY(0f)
                .setStartDelay(fade)
                .setDuration(reducedMotion ? 0 : 300)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void addEmptyText(boolean animateIn) {
        if (emptyText != null || stage == null) return;
        emptyText = text(activity.getString(R.string.recents_empty), 16, Fonts.MEDIUM);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setAlpha(animateIn ? 0f : 0.85f);
        emptyText.setTranslationY(animateIn ? 12 * density : 0f);
        stage.addView(emptyText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Shows the freed-memory or failure message in the line under the cards, so the deck never resizes. */
    private void showInline(String message) {
        // In the empty state the "nothing here" line is the message; the hint row has faded out.
        if (hintText == null || cards.isEmpty()) return;
        handler.removeCallbacks(hideFreed);
        hintText.setText(message);
        hintText.setTextColor(0xFF7DD3FC);
        hintText.setAlpha(1f);
        hintText.setVisibility(View.VISIBLE);
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

    private TextView text(String value, float sizeSp, int weight) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(Color.WHITE);
        Fonts.apply(view, weight);
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
