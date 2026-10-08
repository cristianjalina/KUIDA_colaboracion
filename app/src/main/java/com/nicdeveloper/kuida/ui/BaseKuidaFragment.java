package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.nicdeveloper.kuida.MainActivity;
import com.nicdeveloper.kuida.R;

public abstract class BaseKuidaFragment extends Fragment {
    protected LinearLayout content;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle state) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(dp(20), dp(16), dp(20), bottom + dp(32));
            return insets;
        });
        buildContent(context);
        UiMotion.entranceStagger(content);
        return scroll;
    }

    @Nullable
    @Override
    public android.view.animation.Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        try {
            return UiMotion.screenAnim(enter);
        } catch (Exception ignored) {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    protected abstract void buildContent(Context context);

    protected TextView title(Context c, String text) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextColor(night());
        view.setTextSize(26);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        view.setContentDescription(text);
        view.setPadding(0, 0, 0, dp(8));
        content.addView(view, params(-1, -2));
        return view;
    }

    protected TextView body(Context c, String text) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextColor(slate());
        view.setTextSize(14);
        view.setLineSpacing(0, 1.2f);
        view.setPadding(0, 0, 0, dp(14));
        content.addView(view, params(-1, -2));
        return view;
    }

    /**
     * Clean Line Card: Sharp 0dp corners, 1dp subtle clean border, pure white background.
     */
    protected MaterialCardView card(Context c, String heading, String description, View.OnClickListener listener) {
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(12)); // Plano limpio, sin contornos
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(res(R.color.kuida_card_bg, "#EDF3F3"));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(heading + ". " + description);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView h = new TextView(c);
        h.setText(heading);
        h.setTextSize(16);
        h.setTextColor(night());
        h.setTypeface(Typeface.create("sans", Typeface.BOLD));

        TextView d = new TextView(c);
        d.setText(description);
        d.setTextSize(13);
        d.setTextColor(slate());
        d.setPadding(0, dp(4), 0, 0);

        box.addView(h, params(-1, -2));
        box.addView(d, params(-1, -2));
        card.addView(box);
        card.setOnClickListener(listener);
        UiMotion.pressBounce(card);

        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(12));
        content.addView(card, cardParams);
        return card;
    }

    /**
     * Clean Line Action Tile: Exactly like 01 ui.jpeg square grid tiles.
     * Sharp square border 1.5dp Teal #008080, White background, centered icon and text.
     */
    protected MaterialCardView actionTile(Context c, int iconRes, String heading, String description, View.OnClickListener listener) {
        MaterialCardView tile = new MaterialCardView(c);
        tile.setRadius(dp(12)); // Plano limpio, sin contornos
        tile.setCardElevation(0);
        tile.setStrokeWidth(0); // Sin contornos en ningun modo
        tile.setCardBackgroundColor(res(R.color.kuida_card_bg, "#EDF3F3"));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(heading + ". " + description);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(dp(8), dp(12), dp(8), dp(12));

        ImageView iconView = new ImageView(c);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(teal());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        iconParams.gravity = android.view.Gravity.CENTER;
        box.addView(iconView, iconParams);

        TextView title = new TextView(c);
        title.setText(heading);
        title.setTextSize(13);
        title.setTextColor(night());
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, dp(6), 0, 0);

        TextView desc = new TextView(c);
        desc.setText(description);
        desc.setTextSize(11);
        desc.setTextColor(slate());
        desc.setGravity(android.view.Gravity.CENTER);

        box.addView(title, params(-1, -2));
        box.addView(desc, params(-1, -2));
        tile.addView(box);
        tile.setOnClickListener(listener);
        UiMotion.pressBounce(tile);
        return tile;
    }

    protected MaterialCardView actionTile(Context c, String icon, String heading, String description, View.OnClickListener listener) {
        MaterialCardView tile = new MaterialCardView(c);
        tile.setRadius(dp(12)); // Plano limpio, sin contornos
        tile.setCardElevation(0);
        tile.setStrokeWidth(0); // Sin contornos en ningun modo
        tile.setCardBackgroundColor(res(R.color.kuida_card_bg, "#EDF3F3"));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(heading + ". " + description);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setPadding(dp(8), dp(12), dp(8), dp(12));

        TextView iconView = new TextView(c);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setTextColor(teal());
        iconView.setGravity(android.view.Gravity.CENTER);

        TextView title = new TextView(c);
        title.setText(heading);
        title.setTextSize(13);
        title.setTextColor(night());
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, dp(4), 0, 0);

        TextView desc = new TextView(c);
        desc.setText(description);
        desc.setTextSize(11);
        desc.setTextColor(slate());
        desc.setGravity(android.view.Gravity.CENTER);

        box.addView(iconView, params(-1, -2));
        box.addView(title, params(-1, -2));
        box.addView(desc, params(-1, -2));
        tile.addView(box);
        tile.setOnClickListener(listener);
        UiMotion.pressBounce(tile);
        return tile;
    }

    protected void twoColumnTiles(View left, View right) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(0, 0, dp(8), 0);
        row.addView(left, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -1, 1);
        rp.setMargins(dp(8), 0, 0, 0);
        row.addView(right, rp);
        LinearLayout.LayoutParams rowParams = params(-1, dp(115));
        rowParams.setMargins(0, 0, 0, dp(14));
        content.addView(row, rowParams);
    }

    /**
     * Solid Clean Line Button: Sharp corners, solid Teal background, white text.
     */
    protected MaterialButton button(Context c, String label, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(c);
        button.setText(label);
        button.setTextSize(16);
        button.setMinHeight(dp(54));
        button.setCornerRadius(dp(16));
        button.setBackgroundColor(buttonBg());
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setOnClickListener(listener);
        UiMotion.pressBounce(button);
        LinearLayout.LayoutParams p = params(-1, -2);
        p.setMargins(0, dp(8), 0, dp(14));
        content.addView(button, p);
        return button;
    }

    /**
     * Banner Button (like "CAMARA" in 01 ui.jpeg): Full-width solid teal block.
     */
    protected MaterialButton bannerButton(Context c, String label, View.OnClickListener listener) {
        MaterialButton button = new MaterialButton(c);
        button.setText(label);
        button.setTextSize(20);
        button.setMinHeight(dp(68));
        button.setCornerRadius(dp(16)); // Boton solido limpio
        button.setBackgroundColor(buttonBg());
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setLetterSpacing(0.08f);
        button.setOnClickListener(listener);
        UiMotion.pressBounce(button);
        LinearLayout.LayoutParams p = params(-1, -2);
        p.setMargins(0, dp(8), 0, dp(14));
        content.addView(button, p);
        return button;
    }

    /**
     * Clean Top Bar matching 01 ui.jpeg and KUIDA brand:
     * Left hamburger icon (Teal), Center "KUIDA" title (Teal, bold), Right User/Profile icon (Teal).
     */
    protected void screenTopBar(Context c, String title) {
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(0, 0, 0, dp(16));

        ImageView menu = new ImageView(c);
        menu.setImageResource(R.drawable.ic_menu_hamburger);
        menu.setColorFilter(topIcon());
        menu.setContentDescription("Menu lateral");
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setOnClickListener(v -> showNavigationDrawer(c));
        bar.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView name = new TextView(c);
        name.setText(title != null && !title.isEmpty() ? title : "KUIDA");
        name.setTextSize(22);
        name.setTextColor(topTitle());
        name.setGravity(android.view.Gravity.CENTER);
        name.setTypeface(Typeface.create("sans", Typeface.BOLD));
        name.setLetterSpacing(0.06f);
        bar.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView userIcon = new ImageView(c);
        userIcon.setImageResource(R.drawable.ic_user_profile);
        userIcon.setColorFilter(topIcon());
        userIcon.setContentDescription("Perfil de usuario");
        userIcon.setClickable(true);
        userIcon.setFocusable(true);
        userIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        userIcon.setOnClickListener(v -> {
            try {
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(R.id.profileFragment);
            } catch (Exception ignored) {}
        });
        bar.addView(userIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));

        content.addView(bar, params(-1, -2));
    }

    protected void showNavigationDrawer(Context c) {
        MainActivity.openDrawer(getActivity());
    }

    /**
     * Clean Line Metric: Clean white card with thin teal outline or clean slate background.
     */
    protected TextView metric(Context c, String label, String value) {
        TextView view = new TextView(c);
        view.setText(label + "\n" + value);
        view.setTextColor(night());
        view.setTextSize(16);
        view.setTypeface(Typeface.create("sans", Typeface.BOLD));
        view.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(res(R.color.kuida_metric, "#F0F7F7"));
        view.setBackground(bg);
        view.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams p = params(-1, -2);
        p.setMargins(0, dp(4), 0, dp(14));
        content.addView(view, p);
        return view;
    }

    protected void addCustomView(View view, int heightDp) {
        LinearLayout.LayoutParams p = params(-1, heightDp > 0 ? dp(heightDp) : -2);
        p.setMargins(0, dp(8), 0, dp(14));
        content.addView(view, p);
    }

    protected int teal() { return res(R.color.kuida_teal, "#008080"); }
    protected int night() { return res(R.color.kuida_on_surface, "#0B2228"); }
    protected int onSurface() { return res(R.color.kuida_on_surface, "#102A33"); }
    protected int slate() { return res(R.color.kuida_slate, "#4C7280"); }
    protected int mint() { return Color.parseColor("#00C9BE"); }
    protected int surfaceBg() { return res(R.color.kuida_surface, "#FFFFFF"); }
    protected int cardBg() { return res(R.color.kuida_card_bg, "#FFFFFF"); }
    protected int metricBg() { return res(R.color.kuida_metric, "#F0F7F7"); }
    protected int borderSubtle() { return res(R.color.kuida_border_subtle, "#E0EAEA"); }
    protected int buttonBg() { return res(R.color.kuida_button, "#008080"); }
    protected int buttonText() { return Color.WHITE; }
    protected int topIcon() { return res(R.color.kuida_topicon, "#008080"); }
    protected int topTitle() { return res(R.color.kuida_toptitle, "#008080"); }

    /** Color por recurso dia/noche (el sistema resuelve; sin ramas manuales). */
    protected int res(int resId, String fallbackHex) {
        try {
            android.app.Activity act = getActivity();
            if (act != null) return ContextCompat.getColor(act, resId);
            Context ctx = getContext();
            if (ctx != null) return ContextCompat.getColor(ctx, resId);
        } catch (Exception ignored) {}
        return Color.parseColor(fallbackHex);
    }

    protected boolean isNight() {
        try {
            int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception ignored) {
            return false;
        }
    }

    protected LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    protected int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
