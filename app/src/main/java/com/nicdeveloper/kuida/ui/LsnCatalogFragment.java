package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nicdeveloper.kuida.MainActivity;
import com.nicdeveloper.kuida.R;
import com.nicdeveloper.kuida.data.ProfileManager;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Catálogo de Lengua de Señas Nicaragüense: 244 señas reales en 16 categorías. */
public class LsnCatalogFragment extends Fragment {

    private static class Sign {
        final String category;
        final String raw;
        final String label;
        final String asset;
        Sign(String category, String raw, String label) {
            this.category = category;
            this.raw = raw;
            this.label = label;
            this.asset = "lsn/" + category + "_" + raw + ".png";
        }
    }

    private static final String[] CATEGORY_ORDER = {
            "saludos", "familia", "letras", "numeros", "comida", "animales",
            "colores", "tiempo", "verbos", "trabajos", "transporte", "geografia",
            "direcciones", "ropa", "anatomia", "emergencias",
    };

    private static final Map<String, String> CATEGORY_NAMES = new HashMap<>();
    static {
        CATEGORY_NAMES.put("saludos", "Saludos");
        CATEGORY_NAMES.put("familia", "Familia");
        CATEGORY_NAMES.put("letras", "Letras");
        CATEGORY_NAMES.put("numeros", "Números");
        CATEGORY_NAMES.put("comida", "Comida");
        CATEGORY_NAMES.put("animales", "Animales");
        CATEGORY_NAMES.put("colores", "Colores");
        CATEGORY_NAMES.put("tiempo", "Tiempo");
        CATEGORY_NAMES.put("verbos", "Verbos");
        CATEGORY_NAMES.put("trabajos", "Trabajos");
        CATEGORY_NAMES.put("transporte", "Transporte");
        CATEGORY_NAMES.put("geografia", "Geografía");
        CATEGORY_NAMES.put("direcciones", "Direcciones");
        CATEGORY_NAMES.put("ropa", "Ropa");
        CATEGORY_NAMES.put("anatomia", "Anatomía");
        CATEGORY_NAMES.put("emergencias", "Emergencias");
    }

    private static final Map<String, String> PRETTY = new HashMap<>();
    static {
        PRETTY.put("buenosdias", "Buenos días");
        PRETTY.put("buenasnoches", "Buenas noches");
        PRETTY.put("buenastardes", "Buenas tardes");
        PRETTY.put("comoestas", "¿Cómo estás?");
        PRETTY.put("quehoraes", "¿Qué hora es?");
        PRETTY.put("soysordo", "Soy sordo");
        PRETTY.put("soyhipoacusico", "Soy hipoacúsico");
        PRETTY.put("salsadetomate", "Salsa de tomate");
        PRETTY.put("papasfritas", "Papas fritas");
        PRETTY.put("trajedebanio", "Traje de baño");
        PRETTY.put("dolordecabeza", "Dolor de cabeza");
        PRETTY.put("maniana", "Mañana");
        PRETTY.put("enie", "Ñ");
        PRETTY.put("eeuu", "EE. UU.");
        PRETTY.put("elsalvador", "El Salvador");
        PRETTY.put("costarica", "Costa Rica");
        PRETTY.put("guatemala", "Guatemala");
        PRETTY.put("honduras", "Honduras");
        PRETTY.put("nicaragua", "Nicaragua");
        PRETTY.put("panama", "Panamá");
        PRETTY.put("repdom", "Rep. Dominicana");
        PRETTY.put("centroamerica", "Centroamérica");
        PRETTY.put("cuba", "Cuba");
        PRETTY.put("adios", "Adiós");
        PRETTY.put("cafe", "Café");
        PRETTY.put("calzoncillo", "Calzoncillo");
    }

    private final List<Sign> allSigns = new ArrayList<>();
    private final List<Sign> visibleSigns = new ArrayList<>();
    private final List<String> categoriesPresent = new ArrayList<>();
    private String selectedCategory = null; // null = todas
    private String query = "";

    private LruCache<String, Bitmap> thumbCache;
    private SignAdapter adapter;
    private TextView countView;
    private LinearLayout chipsRow;
    private EditText searchBox;
    private TextToSpeech speech;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int memKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        thumbCache = new LruCache<String, Bitmap>(memKb / 8) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        speech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                try { speech.setLanguage(new Locale("es", "NI")); } catch (Exception ignored) {}
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        // Defensa en profundidad: impedir entrada directa desde cualquier ruta.
        if (!ProfileManager.canUseSignLanguageCatalog(c)) {
            LinearLayout blank = new LinearLayout(c);
            Toast.makeText(c, "El catálogo LSN está disponible para los perfiles auditivo, de comunicación del habla y asistencia completa.", Toast.LENGTH_LONG).show();
            blank.post(() -> {
                try {
                    NavHostFragment.findNavController(this).navigate(R.id.visionFragment);
                } catch (Exception ignored) {}
            });
            return blank;
        }
        loadSigns();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));

        root.addView(topBar(c), new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(c);
        hint.setText(allSigns.size() + " señas · " + categoriesPresent.size()
                + " categorías. Toca una seña para verla grande y escucharla.");
        hint.setTextSize(13);
        hint.setTextColor(res(R.color.kuida_slate, "#4C7280"));
        hint.setPadding(dp(20), dp(2), dp(20), dp(8));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        // Buscador sin contorno
        EditText search = new EditText(c);
        searchBox = search;
        search.setHint("Buscar seña...");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
        search.setHintTextColor(res(R.color.kuida_slate, "#4C7280"));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setShape(GradientDrawable.RECTANGLE);
        searchBg.setCornerRadius(dp(12));
        searchBg.setColor(res(R.color.kuida_card_bg, "#EDF3F3"));
        search.setBackground(searchBg);
        search.setPadding(dp(16), dp(12), dp(16), dp(12));
        search.setContentDescription("Buscar seña por palabra");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                applyFilter();
            }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
        searchParams.setMargins(dp(20), dp(2), dp(20), dp(10));
        root.addView(search, searchParams);

        // Chips de categoría
        HorizontalScrollView chipsScroll = new HorizontalScrollView(c);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsRow = new LinearLayout(c);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(dp(20), 0, dp(20), dp(10));
        chipsScroll.addView(chipsRow);
        root.addView(chipsScroll, new LinearLayout.LayoutParams(-1, -2));
        buildChips();

        countView = new TextView(c);
        countView.setTextSize(13);
        countView.setTypeface(Typeface.create("sans", Typeface.BOLD));
        countView.setTextColor(res(R.color.kuida_slate, "#4C7280"));
        countView.setPadding(dp(20), 0, dp(20), dp(6));
        root.addView(countView, new LinearLayout.LayoutParams(-1, -2));

        RecyclerView grid = new RecyclerView(c);
        grid.setLayoutManager(new GridLayoutManager(c, 3));
        grid.setClipToPadding(false);
        grid.setPadding(dp(12), 0, dp(12), dp(24));
        adapter = new SignAdapter();
        grid.setAdapter(adapter);
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));

        // Llegada desde Jarvis ("muéstrame la seña de X"): filtrar directo
        Bundle args = getArguments();
        if (args != null && args.containsKey("query")) {
            String q = args.getString("query");
            if (q != null && !q.trim().isEmpty() && searchBox != null) {
                searchBox.setText(q.trim());
                query = q.trim();
            }
        }

        applyFilter();
        return root;
    }

    // ---------- Barra superior ----------

    private LinearLayout topBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(10));

        ImageView menu = new ImageView(c);
        menu.setImageResource(R.drawable.ic_menu_hamburger);
        menu.setColorFilter(res(R.color.kuida_topicon, "#008080"));
        menu.setContentDescription("Menú lateral");
        menu.setClickable(true);
        menu.setFocusable(true);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setOnClickListener(v -> MainActivity.openDrawer(getActivity()));
        bar.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView name = new TextView(c);
        name.setText("Catálogo LSN");
        name.setTextSize(22);
        name.setTextColor(res(R.color.kuida_toptitle, "#008080"));
        name.setGravity(Gravity.CENTER);
        name.setTypeface(Typeface.create("sans", Typeface.BOLD));
        bar.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView userIcon = new ImageView(c);
        userIcon.setImageResource(R.drawable.ic_user_profile);
        userIcon.setColorFilter(res(R.color.kuida_topicon, "#008080"));
        userIcon.setContentDescription("Mi perfil");
        userIcon.setClickable(true);
        userIcon.setFocusable(true);
        userIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        userIcon.setOnClickListener(v -> {
            try {
                NavHostFragment.findNavController(this).navigate(R.id.profileFragment);
            } catch (Exception ignored) {}
        });
        bar.addView(userIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return bar;
    }

    // ---------- Datos ----------

    private void loadSigns() {
        if (!allSigns.isEmpty()) return;
        try {
            String[] files = requireContext().getAssets().list("lsn");
            if (files == null) return;
            Map<String, List<Sign>> byCat = new HashMap<>();
            for (String f : files) {
                if (!f.endsWith(".png")) continue;
                String base = f.substring(0, f.length() - 4);
                int cut = base.indexOf('_');
                if (cut < 0) continue;
                String cat = base.substring(0, cut);
                String raw = base.substring(cut + 1);
                if (!CATEGORY_NAMES.containsKey(cat)) continue;
                Sign s = new Sign(cat, raw, pretty(raw));
                if (!byCat.containsKey(cat)) byCat.put(cat, new ArrayList<>());
                byCat.get(cat).add(s);
            }
            for (String cat : CATEGORY_ORDER) {
                List<Sign> list = byCat.get(cat);
                if (list == null || list.isEmpty()) continue;
                if ("numeros".equals(cat)) {
                    Collections.sort(list, (a, b) -> Integer.compare(parseNum(a.raw), parseNum(b.raw)));
                } else {
                    Collections.sort(list, (a, b) -> a.raw.compareTo(b.raw));
                }
                categoriesPresent.add(cat);
                allSigns.addAll(list);
            }
        } catch (Exception ignored) {}
    }

    private int parseNum(String raw) {
        try {
            return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private String pretty(String raw) {
        String hit = PRETTY.get(raw.toLowerCase(Locale.US));
        if (hit != null) return hit;
        String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) return raw;
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    private static String norm(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.US), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    private void buildChips() {
        Context c = requireContext();
        chipsRow.removeAllViews();
        chipsRow.addView(chip(c, null, "Todas · " + allSigns.size()));
        for (String cat : categoriesPresent) {
            int count = 0;
            for (Sign s : allSigns) if (s.category.equals(cat)) count++;
            chipsRow.addView(chip(c, cat, CATEGORY_NAMES.get(cat) + " · " + count));
        }
    }

    private TextView chip(Context c, final String cat, String label) {
        boolean selected = (cat == null && selectedCategory == null)
                || (cat != null && cat.equals(selectedCategory));
        TextView chip = new TextView(c);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setTypeface(Typeface.create("sans", Typeface.BOLD));
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription("Categoría " + label + (selected ? ", seleccionada" : ""));
        chip.setTextColor(selected
                ? 0xFFFFFFFF
                : res(R.color.kuida_on_surface, "#0B2228"));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        bg.setColor(selected
                ? res(R.color.kuida_button, "#008080")
                : res(R.color.kuida_card_bg, "#EDF3F3"));
        chip.setBackground(bg);
        chip.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(p);
        chip.setOnClickListener(v -> {
            selectedCategory = cat;
            buildChips();
            applyFilter();
        });
        return chip;
    }

    private void applyFilter() {
        visibleSigns.clear();
        String q = norm(query.trim());
        for (Sign s : allSigns) {
            if (selectedCategory != null && !s.category.equals(selectedCategory)) continue;
            if (!q.isEmpty() && !norm(s.label).contains(q)) continue;
            visibleSigns.add(s);
        }
        if (countView != null) {
            countView.setText(visibleSigns.size() == 1 ? "1 seña" : visibleSigns.size() + " señas");
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ---------- Detalle ----------

    private void openDetail(Sign s) {
        Context c = requireContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView title = new TextView(c);
        title.setText(s.label);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        title.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView cat = new TextView(c);
        cat.setText(CATEGORY_NAMES.get(s.category));
        cat.setTextSize(14);
        cat.setTextColor(res(R.color.kuida_slate, "#4C7280"));
        cat.setGravity(Gravity.CENTER);
        cat.setPadding(0, 0, 0, dp(12));
        box.addView(cat);

        ImageView img = new ImageView(c);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setContentDescription("Seña de " + s.label);
        Bitmap full = loadBitmap(s.asset, 1080);
        if (full != null) img.setImageBitmap(full);
        box.addView(img, new LinearLayout.LayoutParams(-1, -2));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c, R.style.Theme_Kuida_Dialog)
                .setView(box)
                .setPositiveButton("Escuchar", (d, w) -> speak(s.label))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void speak(String text) {
        try {
            if (speech != null) speech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kuida-lsn");
        } catch (Exception ignored) {}
    }

    // ---------- Imágenes ----------

    private Bitmap loadBitmap(String asset, int maxDim) {
        Bitmap cached = maxDim <= 320 ? thumbCache.get(asset) : null;
        if (cached != null) return cached;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = requireContext().getAssets().open(asset)) {
                BitmapFactory.decodeStream(is, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxDim
                    && bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream is = requireContext().getAssets().open(asset)) {
                Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                if (bmp != null && maxDim <= 320) thumbCache.put(asset, bmp);
                return bmp;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---------- Adaptador ----------

    private class SignAdapter extends RecyclerView.Adapter<SignAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(4), dp(4), dp(4), dp(10));
            cell.setClickable(true);
            cell.setFocusable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2);
            cell.setLayoutParams(lp);

            FrameLayout frame = new FrameLayout(c);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(12));
            bg.setColor(res(R.color.kuida_card_bg, "#EDF3F3"));
            frame.setBackground(bg);
            frame.setClipToOutline(true);
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(dp(10), dp(10), dp(10), dp(10));
            frame.addView(img, new FrameLayout.LayoutParams(-1, dp(96)));
            cell.addView(frame, new LinearLayout.LayoutParams(-1, -2));

            TextView label = new TextView(c);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setPadding(0, dp(6), 0, 0);
            label.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
            cell.addView(label, new LinearLayout.LayoutParams(-1, -2));
            return new Holder(cell, img, label);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Sign s = visibleSigns.get(position);
            h.label.setText(s.label);
            h.cell.setContentDescription("Seña " + s.label + ". Toca para ampliar.");
            Bitmap bmp = loadBitmap(s.asset, 300);
            if (bmp != null) h.image.setImageBitmap(bmp);
            else h.image.setImageDrawable(null);
            h.cell.setOnClickListener(v -> openDetail(s));
        }

        @Override
        public int getItemCount() {
            return visibleSigns.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final LinearLayout cell;
            final ImageView image;
            final TextView label;
            Holder(LinearLayout cell, ImageView image, TextView label) {
                super(cell);
                this.cell = cell;
                this.image = image;
                this.label = label;
            }
        }
    }

    // ---------- Utilidades ----------

    private int res(int resId, String fallbackHex) {
        try {
            Context ctx = getContext();
            if (ctx != null) return androidx.core.content.ContextCompat.getColor(ctx, resId);
        } catch (Exception ignored) {}
        return android.graphics.Color.parseColor(fallbackHex);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    @Override
    public void onDestroy() {
        try {
            if (speech != null) {
                speech.stop();
                speech.shutdown();
            }
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
