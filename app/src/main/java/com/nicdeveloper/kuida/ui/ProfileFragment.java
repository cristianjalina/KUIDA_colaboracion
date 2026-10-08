package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.nicdeveloper.kuida.R;
import com.nicdeveloper.kuida.data.ProfileManager;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();

        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        // ── Top bar ──────────────────────────────────────────────
        LinearLayout topBar = new LinearLayout(c);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));
        topBar.setPadding(dp(c, 4), dp(c, 8), dp(c, 16), dp(c, 8));

        ImageView backBtn = new ImageView(c);
        backBtn.setImageResource(R.drawable.ic_arrow_back);
        backBtn.setColorFilter(res(R.color.kuida_teal, "#008080"));
        backBtn.setPadding(dp(c, 12), dp(c, 8), dp(c, 16), dp(c, 8));
        backBtn.setClickable(true);
        backBtn.setFocusable(true);
        backBtn.setContentDescription("Volver");
        backBtn.setOnClickListener(v -> {
            try {
                if (!NavHostFragment.findNavController(this).popBackStack()) {
                    int dest = com.nicdeveloper.kuida.MainActivity.getHomeDestinationForProfile(requireContext());
                    NavHostFragment.findNavController(this).navigate(dest);
                }
            } catch (Exception e) {
                try {
                    int dest = com.nicdeveloper.kuida.MainActivity.getHomeDestinationForProfile(requireContext());
                    NavHostFragment.findNavController(this).navigate(dest);
                } catch (Exception ignored) {}
            }
        });
        topBar.addView(backBtn,
                new LinearLayout.LayoutParams(dp(c, 48), dp(c, 40)));

        TextView topTitle = new TextView(c);
        topTitle.setText("Mi perfil");
        topTitle.setTextSize(20);
        topTitle.setTextColor(res(R.color.kuida_toptitle, "#008080"));
        topTitle.setTypeface(Typeface.create("sans", Typeface.BOLD));
        topBar.addView(topTitle, new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(topBar,
                new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Header band ──────────────────────────────────────────
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setBackgroundColor(res(R.color.kuida_metric, "#E8F6F6"));
        header.setPadding(dp(c, 16), dp(c, 24), dp(c, 16), dp(c, 28));

        AvatarView avatar = new AvatarView(c,
                res(R.color.kuida_teal, "#008080"),
                res(R.color.kuida_metric, "#E8F6F6"));
        LinearLayout.LayoutParams avatarParams =
                new LinearLayout.LayoutParams(dp(c, 90), dp(c, 90));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        avatar.setContentDescription("Avatar de usuario");
        header.addView(avatar, avatarParams);

        TextView nameView = new TextView(c);
        nameView.setText("Nombre del Usuario");
        nameView.setTextSize(18);
        nameView.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
        nameView.setTypeface(Typeface.create("sans", Typeface.BOLD));
        nameView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(c, 14);
        header.addView(nameView, nameParams);

        root.addView(header,
                new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Thin divider ─────────────────────────────────────────
        View divider0 = new View(c);
        divider0.setBackgroundColor(res(R.color.kuida_border_subtle, "#C8E6E6"));
        root.addView(divider0, new LinearLayout.LayoutParams(-1, dp(c, 1)));

        // ── Menu items ───────────────────────────────────────────
        LinearLayout menu = new LinearLayout(c);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));
        menu.setPadding(0, dp(c, 8), 0, dp(c, 32));

        addMenuItem(c, menu, R.drawable.ic_email, "Correo del Usuario", () ->
                Toast.makeText(c, "Cambiar correo", Toast.LENGTH_SHORT).show());

        addMenuDivider(c, menu);

        addMenuItem(c, menu, R.drawable.ic_lock, "Cambiar contraseña", () ->
                Toast.makeText(c, "Cambiar contraseña", Toast.LENGTH_SHORT).show());

        addMenuDivider(c, menu);

        addMenuItem(c, menu, R.drawable.ic_disability, "Tipo de discapacidad", () ->
                Toast.makeText(c, ProfileManager.label(c), Toast.LENGTH_SHORT).show());

        root.addView(menu,
                new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));

        return scroll;
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void addMenuItem(Context c, LinearLayout parent,
                              int iconRes, String label, Runnable action) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 18));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(label);

        android.util.TypedValue tv = new android.util.TypedValue();
        if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            row.setBackgroundResource(tv.resourceId);
        }
        row.setOnClickListener(v -> action.run());

        ImageView iconView = new ImageView(c);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(res(R.color.kuida_teal, "#008080"));
        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24));
        iconP.setMargins(0, 0, dp(c, 16), 0);
        row.addView(iconView, iconP);

        TextView labelView = new TextView(c);
        labelView.setText(label);
        labelView.setTextSize(15);
        labelView.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
        row.addView(labelView,
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        ImageView chevron = new ImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(res(R.color.kuida_teal, "#008080"));
        row.addView(chevron,
                new LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)));

        parent.addView(row,
                new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addMenuDivider(Context c, LinearLayout parent) {
        View div = new View(c);
        div.setBackgroundColor(res(R.color.kuida_border_subtle, "#E8F0F0"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(c, 1));
        p.setMargins(dp(c, 20), 0, dp(c, 20), 0);
        parent.addView(div, p);
    }

    /** Color por recurso dia/noche (el sistema resuelve; sin ramas manuales). */
    private int res(int resId, String fallbackHex) {
        try {
            Context ctx = getContext();
            if (ctx != null) return androidx.core.content.ContextCompat.getColor(ctx, resId);
        } catch (Exception ignored) {}
        return Color.parseColor(fallbackHex);
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

    private int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ── Avatar drawn with Canvas (circle + person silhouette) ────
    private static class AvatarView extends View {
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

        public AvatarView(Context c, int ringColor, int fillColor) {
            super(c);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setColor(ringColor);
            circlePaint.setStrokeWidth(6f);

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fillColor);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - 6f;

            canvas.drawCircle(cx, cy, r, fillPaint);
            canvas.drawCircle(cx, cy, r, circlePaint);

            float headR = r * 0.28f;
            canvas.drawCircle(cx, cy - r * 0.20f, headR, circlePaint);

            android.graphics.RectF bodyRect = new android.graphics.RectF(
                    cx - r * 0.55f, cy + r * 0.18f,
                    cx + r * 0.55f, cy + r * 0.80f);
            canvas.drawArc(bodyRect, 180, 180, false, circlePaint);
        }
    }
}
