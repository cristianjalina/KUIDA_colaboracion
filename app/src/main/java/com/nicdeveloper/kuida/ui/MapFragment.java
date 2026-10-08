package com.nicdeveloper.kuida.ui;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.nicdeveloper.kuida.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2002;
    private static final String HIST_PREFS = "kuida_map_history";
    private static final String HIST_KEY = "places_json";

    private enum State { EXPLORE, LIST, DETAIL }

    private static class Category {
        final String key, label, filter;
        final int icon;
        Category(String key, String label, int icon, String filter) {
            this.key = key; this.label = label; this.icon = icon; this.filter = filter;
        }
    }

    private static final Category[] CATEGORIES = {
            new Category("recent", "Anunciados recientemente", R.drawable.ic_cat_history, ""),
            new Category("transport", "Transporte", R.drawable.ic_cat_bus, "amenity=bus_station|highway=bus_stop"),
            new Category("banks", "Bancos y cajeros", R.drawable.ic_cat_bank, "amenity=bank|amenity=atm"),
            new Category("health", "Salud", R.drawable.ic_cat_health, "amenity=hospital|amenity=clinic|amenity=pharmacy|amenity=doctors"),
            new Category("food", "Comida", R.drawable.ic_cat_food, "amenity=restaurant|amenity=fast_food|amenity=cafe"),
            new Category("shops", "Tiendas", R.drawable.ic_cat_shop, "shop=supermarket|shop=convenience|shop=clothes|shop=bakery"),
            new Category("arts", "Arte y entretenimiento", R.drawable.ic_cat_arts, "tourism=museum|tourism=gallery|tourism=artwork|amenity=theatre|amenity=cinema"),
            new Category("public", "Edificios publicos", R.drawable.ic_cat_public, "amenity=townhall|amenity=police|amenity=post_office|amenity=library"),
            new Category("education", "Centros educacionales", R.drawable.ic_cat_edu, "amenity=school|amenity=university|amenity=college|amenity=kindergarten"),
            new Category("night", "Pubs y discoteques", R.drawable.ic_cat_night, "amenity=pub|amenity=bar|amenity=nightclub"),
            new Category("hotels", "Alojamientos", R.drawable.ic_cat_hotel, "tourism=hotel|tourism=hostel|tourism=guest_house"),
    };

    private static class Place {
        final String name, type;
        final double lat, lng, dist;
        final String cardinal;
        Place(String name, String type, double lat, double lng, double dist, String cardinal) {
            this.name = name; this.type = type; this.lat = lat; this.lng = lng;
            this.dist = dist; this.cardinal = cardinal;
        }
    }

    private LinearLayout root;
    private LinearLayout headerBox;
    private WebView mapWebView;
    private android.widget.FrameLayout mapCard;
    private LinearLayout bottomBox;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private ExecutorService executor;
    private TextToSpeech tts;

    private double lastLat = 12.1364; // Managua por defecto hasta primer fix real
    private double lastLng = -86.2514;
    private boolean isMapReady = false;
    private boolean hasGpsFix = false;
    private boolean paused = false;
    // Freno anti-cierres: el GPS empuja 1-3 veces por segundo; la UI no necesita tanto
    private long lastMapPushMs = 0;
    private double lastPushLat = 0;
    private double lastPushLng = 0;
    private boolean firstPushDone = false;
    private long lastPlacesMs = 0;
    private double lastPlacesLat = 0;
    private double lastPlacesLng = 0;

    private State state = State.EXPLORE;
    private Category currentCategory;
    private int radiusMeters = 2000;
    private List<Place> places = new ArrayList<>();
    private Place followTarget;
    private Place detailPlace;
    private String travelMode = "walking";
    private long lastAnnounceTime = 0;
    private double lastAnnounceDist = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();
        executor = Executors.newSingleThreadExecutor();
        tts = new TextToSpeech(c, status -> {
            if (status == TextToSpeech.SUCCESS) {
                try { tts.setLanguage(new Locale("es", "NI")); }
                catch (Exception ignored) { try { tts.setLanguage(new Locale("es", "ES")); } catch (Exception ignored2) {} }
            }
        });

        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));

        headerBox = new LinearLayout(c);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(headerBox, new LinearLayout.LayoutParams(-1, -2));

        // Mapa persistente (no se recarga al cambiar de estado)
        mapWebView = new WebView(c);
        WebSettings ws = mapWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setGeolocationEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        mapWebView.setBackgroundColor(Color.parseColor("#0B2228"));
        mapWebView.setContentDescription("Mapa de navegacion");
        mapWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isMapReady = true;
                if (hasGpsFix) updateMapPosition(lastLat, lastLng);
            }
        });
        mapWebView.loadUrl("file:///android_asset/kuida_map.html");

        // Tarjeta redondeada para el mapa (estilo referencia): recorta el WebView
        mapCard = new android.widget.FrameLayout(c);
        android.graphics.drawable.GradientDrawable mapBg = new android.graphics.drawable.GradientDrawable();
        mapBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        mapBg.setCornerRadius(dp(c, 24));
        mapBg.setColor(Color.parseColor("#0B2228"));
        mapCard.setBackground(mapBg);
        mapCard.setClipToOutline(true);
        mapCard.setContentDescription("Mapa de navegacion");
        mapCard.addView(mapWebView, new android.widget.FrameLayout.LayoutParams(-1, -1));

        bottomBox = new LinearLayout(c);
        bottomBox.setOrientation(LinearLayout.VERTICAL);

        // Argumentos desde VisionFragment (categoria directa)
        Bundle args = getArguments();
        if (args != null && args.containsKey("category_key")) {
            Category cat = findCategory(args.getString("category_key"));
            if (cat != null) {
                currentCategory = cat;
                state = State.LIST;
            }
        }

        rebuildScreen();
        return root;
    }

    // ================= ESTADOS =================

    private void rebuildScreen() {
        if (!isAdded()) return;
        Context c = requireContext();
        headerBox.removeAllViews();
        // Reanclar mapa y panel inferior segun estado
        if (mapCard.getParent() != null) ((ViewGroup) mapCard.getParent()).removeView(mapCard);
        if (bottomBox.getParent() != null) ((ViewGroup) bottomBox.getParent()).removeView(bottomBox);
        bottomBox.removeAllViews();

        if (state == State.EXPLORE) {
            buildExploreHeader(c);
            root.addView(bottomBox, new LinearLayout.LayoutParams(-1, 0, 1));
            buildExploreGrid(c);
        } else if (state == State.LIST) {
            buildListHeader(c);
            root.addView(mapCard, mapCardParams(c));
            root.addView(bottomBox, new LinearLayout.LayoutParams(-1, 0, 1));
            buildListBody(c);
            loadCurrentCategory();
        } else {
            buildDetailHeader(c);
            root.addView(mapCard, mapCardParams(c));
            root.addView(bottomBox, new LinearLayout.LayoutParams(-1, 0, 1));
            buildDetailBody(c);
        }
    }

    private LinearLayout.LayoutParams mapCardParams(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 0, 1);
        p.setMargins(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 8));
        return p;
    }

    // ============ ESTADO A: EXPLORACION ============

    private void buildExploreHeader(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 16), dp(c, 14), dp(c, 12), dp(c, 10));

        TextView title = new TextView(c);
        title.setText("Exploración");
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        title.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        bar.addView(headerIcon(c, R.drawable.ic_pause, "Pausar GPS", v -> togglePause()));
        bar.addView(headerIcon(c, R.drawable.ic_radar, "Centrar en mi ubicacion", v -> recenter()));
        bar.addView(headerIcon(c, R.drawable.ic_search, "Buscar lugar", v -> showSearchDialog()));
        bar.addView(headerIcon(c, R.drawable.ic_filter, "Radio de busqueda", v -> showRadiusDialog()));
        headerBox.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        // Pildora ubicacion actual
        LinearLayout pillRow = new LinearLayout(c);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);
        pillRow.setPadding(dp(c, 16), dp(c, 2), dp(c, 16), dp(c, 10));
        LinearLayout pill = new LinearLayout(c);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(c, 24));
        bg.setColor(Color.parseColor("#00C9BE"));
        pill.setBackground(bg);
        pill.setPadding(dp(c, 6), dp(c, 6), dp(c, 18), dp(c, 6));
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setContentDescription("Ubicacion actual del usuario. Toque para escuchar coordenadas.");
        pill.setOnClickListener(v -> announceMyLocation());

        ImageView pin = new ImageView(c);
        pin.setImageResource(R.drawable.ic_map);
        pin.setColorFilter(Color.parseColor("#0B2228"));
        pill.addView(pin, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));

        TextView pillText = new TextView(c);
        pillText.setText("Ubicación actual del usuario");
        pillText.setTextSize(16);
        pillText.setTypeface(Typeface.create("sans", Typeface.BOLD));
        pillText.setTextColor(Color.parseColor("#0B2228"));
        pill.addView(pillText);
        pillRow.addView(pill, new LinearLayout.LayoutParams(-2, -2));
        headerBox.addView(pillRow, new LinearLayout.LayoutParams(-1, -2));
    }

    private ImageView headerIcon(Context c, int res, String desc, View.OnClickListener l) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(res(R.color.kuida_on_surface, "#0B2228"));
        iv.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 10));
        iv.setClickable(true);
        iv.setFocusable(true);
        iv.setContentDescription(desc);
        iv.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44));
        iv.setLayoutParams(p);
        return iv;
    }

    private void buildExploreGrid(Context c) {
        ScrollView scroll = new ScrollView(c);
        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(3);
        grid.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 20));
        for (Category cat : CATEGORIES) {
            grid.addView(categoryCell(c, cat));
        }
        scroll.addView(grid);
        bottomBox.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private LinearLayout categoryCell(Context c, Category cat) {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setContentDescription(cat.label);
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0;
        gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cell.setLayoutParams(gp);

        LinearLayout circle = new LinearLayout(c);
        circle.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#00C9BE"));
        circle.setBackground(bg);
        ImageView icon = new ImageView(c);
        icon.setImageResource(cat.icon);
        icon.setColorFilter(Color.parseColor("#0B2228"));
        circle.addView(icon, new LinearLayout.LayoutParams(dp(c, 36), dp(c, 36)));
        cell.addView(circle, new LinearLayout.LayoutParams(dp(c, 72), dp(c, 72)));

        TextView label = new TextView(c);
        label.setText(cat.label);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(res(R.color.kuida_on_surface, "#0B2228"));
        label.setPadding(0, dp(c, 6), 0, 0);
        cell.addView(label, new LinearLayout.LayoutParams(-1, -2));

        cell.setOnClickListener(v -> {
            currentCategory = cat;
            state = State.LIST;
            rebuildScreen();
        });
        return cell;
    }

    // ============ ESTADO B: LISTA POR CATEGORIA ============

    private void buildListHeader(Context c) {
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor("#008080"));
        header.setPadding(dp(c, 4), dp(c, 12), dp(c, 16), dp(c, 12));

        ImageView back = new ImageView(c);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(Color.WHITE);
        back.setPadding(dp(c, 10), dp(c, 6), dp(c, 14), dp(c, 6));
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("Volver a Exploracion");
        back.setOnClickListener(v -> { state = State.EXPLORE; stopFollow(); rebuildScreen(); });
        header.addView(back, new LinearLayout.LayoutParams(dp(c, 48), dp(c, 40)));

        TextView title = new TextView(c);
        title.setText(currentCategory != null ? currentCategory.label : "Mapa");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        headerBox.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildListBody(Context c) {
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Color.parseColor("#004D4D"));
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setTag("places_list");
        TextView loading = new TextView(c);
        loading.setText("Buscando lugares cercanos con GPS real...");
        loading.setTextColor(Color.WHITE);
        loading.setTextSize(14);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(c, 16), dp(c, 24), dp(c, 16), dp(c, 24));
        list.addView(loading);
        scroll.addView(list);
        bottomBox.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void renderPlaces() {
        if (!isAdded() || bottomBox == null) return;
        requireActivity().runOnUiThread(() -> {
            View scroll = bottomBox.getChildAt(0);
            if (!(scroll instanceof ScrollView)) return;
            LinearLayout list = (LinearLayout) ((ScrollView) scroll).getChildAt(0);
            list.removeAllViews();
            Context c = requireContext();
            if (places.isEmpty()) {
                TextView empty = new TextView(c);
                empty.setText(hasGpsFix
                        ? "No se encontraron lugares de esta categoria a " + radiusMeters + " m. Pruebe ampliar el radio con el filtro."
                        : "Esperando señal GPS real para buscar...");
                empty.setTextColor(Color.WHITE);
                empty.setTextSize(14);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(c, 20), dp(c, 28), dp(c, 20), dp(c, 28));
                list.addView(empty);
                return;
            }
            for (Place p : places) {
                list.addView(placeRow(c, p));
                View div = new View(c);
                div.setBackgroundColor(Color.parseColor("#80CBC4"));
                list.addView(div, new LinearLayout.LayoutParams(-1, dp(c, 1)));
            }
        });
    }

    private LinearLayout placeRow(Context c, Place p) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.parseColor("#004D4D"));
        row.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(p.name + ", " + p.type + ", a " + Math.round(p.dist) + " metros al " + p.cardinal);

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(c);
        name.setText(p.name);
        name.setTextSize(15);
        name.setTextColor(Color.WHITE);
        name.setTypeface(Typeface.create("sans", Typeface.BOLD));
        TextView type = new TextView(c);
        type.setText(p.type);
        type.setTextSize(13);
        type.setTextColor(Color.parseColor("#B2DFDB"));
        left.addView(name);
        left.addView(type);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView dist = new TextView(c);
        dist.setText(Math.round(p.dist) + " m");
        dist.setTextSize(14);
        dist.setTextColor(Color.WHITE);
        dist.setTypeface(Typeface.create("sans", Typeface.BOLD));
        TextView dir = new TextView(c);
        dir.setText(p.cardinal);
        dir.setTextSize(13);
        dir.setTextColor(Color.parseColor("#B2DFDB"));
        dir.setGravity(Gravity.END);
        right.addView(dist);
        right.addView(dir);
        row.addView(right);

        row.setOnClickListener(v -> openDetail(p));
        return row;
    }

    // ============ ESTADO C: DETALLE ============

    private void openDetail(Place p) {
        detailPlace = p;
        followTarget = null;
        saveHistory(p);
        state = State.DETAIL;
        rebuildScreen();
        if (mapWebView != null) {
            try {
                mapWebView.evaluateJavascript(String.format(Locale.US,
                        "mostrarDetalle(%f, %f, '%s');", p.lat, p.lng, escapeJs(p.name)), null);
            } catch (Exception ignored) {}
        }
        speak(p.name + ". " + p.type + ". A " + Math.round(p.dist) + " metros al " + p.cardinal + ".");
    }

    private void buildDetailHeader(Context c) {
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor("#008080"));
        header.setPadding(dp(c, 4), dp(c, 12), dp(c, 16), dp(c, 12));
        ImageView back = new ImageView(c);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(Color.WHITE);
        back.setPadding(dp(c, 10), dp(c, 6), dp(c, 14), dp(c, 6));
        back.setClickable(true);
        back.setFocusable(true);
        back.setContentDescription("Volver a la lista");
        back.setOnClickListener(v -> { state = State.LIST; stopFollow(); rebuildScreen(); });
        header.addView(back, new LinearLayout.LayoutParams(dp(c, 48), dp(c, 40)));
        TextView title = new TextView(c);
        title.setText(currentCategory != null ? currentCategory.label : "Lugar");
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans", Typeface.BOLD));
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        headerBox.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildDetailBody(Context c) {
        ScrollView scroll = new ScrollView(c);
        scroll.setBackgroundColor(Color.parseColor("#00796B"));
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#00796B"));

        TextView name = new TextView(c);
        name.setTag("detail_name");
        name.setText(detailPlace.name);
        name.setTextSize(24);
        name.setTextColor(Color.WHITE);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(c, 16), dp(c, 20), dp(c, 16), 0);
        panel.addView(name);

        TextView type = new TextView(c);
        type.setTag("detail_type");
        type.setText(detailPlace.type);
        type.setTextSize(16);
        type.setTextColor(Color.parseColor("#E0F2F1"));
        type.setGravity(Gravity.CENTER);
        panel.addView(type);

        TextView dist = new TextView(c);
        dist.setTag("detail_dist");
        dist.setText("Distancia: " + Math.round(detailPlace.dist) + " metros\n" + detailPlace.cardinal);
        dist.setTextSize(16);
        dist.setTextColor(Color.parseColor("#E0F2F1"));
        dist.setGravity(Gravity.CENTER);
        dist.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 12));
        panel.addView(dist);

        View div = new View(c);
        div.setBackgroundColor(Color.parseColor("#4DB6AC"));
        LinearLayout.LayoutParams divP = new LinearLayout.LayoutParams(-1, dp(c, 1));
        divP.setMargins(dp(c, 16), dp(c, 4), dp(c, 16), 0);
        panel.addView(div, divP);

        // Seguir este lugar + switch
        LinearLayout followRow = new LinearLayout(c);
        followRow.setOrientation(LinearLayout.HORIZONTAL);
        followRow.setGravity(Gravity.CENTER_VERTICAL);
        followRow.setPadding(dp(c, 24), dp(c, 8), dp(c, 24), dp(c, 8));
        ImageView arrow = new ImageView(c);
        arrow.setImageResource(R.drawable.ic_arrow_back);
        arrow.setRotation(180);
        arrow.setColorFilter(Color.WHITE);
        followRow.addView(arrow, new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)));
        TextView followLabel = new TextView(c);
        followLabel.setText("Seguir este lugar");
        followLabel.setTextSize(16);
        followLabel.setTextColor(Color.WHITE);
        followLabel.setPadding(dp(c, 16), 0, 0, 0);
        followRow.addView(followLabel, new LinearLayout.LayoutParams(0, -2, 1));
        SwitchCompat followSwitch = new SwitchCompat(c);
        followSwitch.setContentDescription("Seguir este lugar con guia por voz");
        followSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) startFollow();
            else stopFollow();
        });
        followRow.addView(followSwitch);
        panel.addView(followRow);

        View div2 = new View(c);
        div2.setBackgroundColor(Color.parseColor("#4DB6AC"));
        LinearLayout.LayoutParams div2P = new LinearLayout.LayoutParams(-1, dp(c, 1));
        div2P.setMargins(dp(c, 16), dp(c, 4), dp(c, 16), 0);
        panel.addView(div2, div2P);

        // Como llegar (navegacion real externa)
        LinearLayout navRow = new LinearLayout(c);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        navRow.setPadding(dp(c, 24), dp(c, 12), dp(c, 24), dp(c, 12));
        navRow.setClickable(true);
        navRow.setFocusable(true);
        navRow.setContentDescription("Como llegar a este lugar con navegacion real");
        ImageView navIcon = new ImageView(c);
        navIcon.setImageResource(R.drawable.ic_map);
        navIcon.setColorFilter(Color.WHITE);
        navRow.addView(navIcon, new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)));
        TextView navLabel = new TextView(c);
        navLabel.setText("Cómo llegar a este lugar");
        navLabel.setTextSize(16);
        navLabel.setTextColor(Color.WHITE);
        navLabel.setPadding(dp(c, 16), 0, 0, 0);
        navRow.addView(navLabel, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chev = new ImageView(c);
        chev.setImageResource(R.drawable.ic_chevron_right);
        chev.setColorFilter(Color.WHITE);
        navRow.addView(chev, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
        navRow.setOnClickListener(v -> openExternalNavigation());
        panel.addView(navRow);

        View div3 = new View(c);
        div3.setBackgroundColor(Color.parseColor("#4DB6AC"));
        panel.addView(div3, new LinearLayout.LayoutParams(-1, dp(c, 1)));

        // Barra modos de transporte
        LinearLayout modes = new LinearLayout(c);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Caminando", "Bus", "Automóvil", "Uber"};
        String[] keys = {"walking", "transit", "driving", "uber"};
        int[] modeIcons = {R.drawable.ic_mode_walk, R.drawable.ic_mode_bus, R.drawable.ic_mode_car, R.drawable.ic_mode_uber};
        for (int i = 0; i < labels.length; i++) {
            final String key = keys[i];
            LinearLayout m = new LinearLayout(c);
            m.setOrientation(LinearLayout.VERTICAL);
            m.setGravity(Gravity.CENTER);
            m.setPadding(0, dp(c, 12), 0, dp(c, 12));
            m.setClickable(true);
            m.setFocusable(true);
            m.setContentDescription("Modo " + labels[i]);
            boolean selected = travelMode.equals(key);
            m.setBackgroundColor(selected ? Color.parseColor("#004D4D") : Color.TRANSPARENT);
            ImageView modeIcon = new ImageView(c);
            modeIcon.setImageResource(modeIcons[i]);
            LinearLayout.LayoutParams mip = new LinearLayout.LayoutParams(dp(c, 28), dp(c, 28));
            mip.gravity = Gravity.CENTER;
            m.addView(modeIcon, mip);
            TextView ml = new TextView(c);
            ml.setText(labels[i]);
            ml.setTextSize(12);
            ml.setTextColor(Color.WHITE);
            ml.setGravity(Gravity.CENTER);
            m.addView(ml);
            m.setOnClickListener(v -> {
                travelMode = key;
                speak("Modo " + labels[keyIndex(key)] + " seleccionado.");
                rebuildScreen();
            });
            modes.addView(m, new LinearLayout.LayoutParams(0, -2, 1));
        }
        panel.addView(modes);

        scroll.addView(panel);
        bottomBox.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private int keyIndex(String key) {
        String[] keys = {"walking", "transit", "driving", "uber"};
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return i;
        return 0;
    }

    // ================= GPS REAL =================

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
    public void onStart() {
        super.onStart();
        startGps();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!paused) startGps();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopGps();
    }

    @Override
    public void onDestroyView() {
        stopGps();
        stopFollow();
        if (executor != null) executor.shutdownNow();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {} }
        if (mapWebView != null) { try { mapWebView.destroy(); } catch (Exception ignored) {} mapWebView = null; }
        super.onDestroyView();
    }

    private void startGps() {
        if (!isAdded() || paused) return;
        Context context = getContext();
        if (context == null) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        try {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return;
            if (locationListener == null) {
                locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        lastLat = location.getLatitude();
                        lastLng = location.getLongitude();
                        hasGpsFix = true;
                        // Mapa: máximo cada 3s y solo si caminó +8m (el seguimiento usa el dato crudo)
                        long now = SystemClock.uptimeMillis();
                        boolean moved = !firstPushDone
                                || metersBetween(lastLat, lastLng, lastPushLat, lastPushLng) > 8;
                        if (!firstPushDone || (now - lastMapPushMs > 3000 && moved)) {
                            lastMapPushMs = now;
                            lastPushLat = lastLat;
                            lastPushLng = lastLng;
                            firstPushDone = true;
                            updateMapPosition(lastLat, lastLng);
                        }
                        onGpsUpdate();
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(@NonNull String provider) { startGps(); }
                    @Override public void onProviderDisabled(@NonNull String provider) {}
                };
            }
            Location best = null;
            for (String p : locationManager.getAllProviders()) {
                try {
                    Location l = locationManager.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignored) {}
            }
            if (best != null) {
                lastLat = best.getLatitude();
                lastLng = best.getLongitude();
                hasGpsFix = true;
                updateMapPosition(lastLat, lastLng);
                onGpsUpdate();
            }
            for (String provider : locationManager.getProviders(true)) {
                try {
                    locationManager.requestLocationUpdates(provider, 1000L, 0.0f, locationListener, Looper.getMainLooper());
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            android.util.Log.e("MapFragment", "Error iniciando GPS: " + e.getMessage());
        }
    }

    private void stopGps() {
        if (locationManager != null && locationListener != null) {
            try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE
                && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGps();
        }
    }

    private static double metersBetween(double a1, double o1, double a2, double o2) {
        try {
            float[] r = new float[1];
            Location.distanceBetween(a1, o1, a2, o2, r);
            return r[0];
        } catch (Exception ignored) {
            return Double.MAX_VALUE;
        }
    }

    private void onGpsUpdate() {
        if (!isAdded()) return;
        if (state == State.LIST && !places.isEmpty()) {
            // Lista: recalcular cada 15s o si caminó +25m (no a cada fix)
            long now = SystemClock.uptimeMillis();
            if (now - lastPlacesMs > 15000
                    || metersBetween(lastLat, lastLng, lastPlacesLat, lastPlacesLng) > 25) {
                lastPlacesMs = now;
                lastPlacesLat = lastLat;
                lastPlacesLng = lastLng;
                refreshDistances();
            }
        }
        if (followTarget != null && detailPlace != null) {
            updateFollow();
        }
    }

    private void togglePause() {
        paused = !paused;
        if (paused) {
            stopGps();
            Toast.makeText(requireContext(), "GPS pausado", Toast.LENGTH_SHORT).show();
        } else {
            startGps();
            Toast.makeText(requireContext(), "GPS reanudado", Toast.LENGTH_SHORT).show();
        }
    }

    private void recenter() {
        if (mapWebView != null && hasGpsFix) {
            try {
                mapWebView.evaluateJavascript(String.format(Locale.US,
                        "recentrar(%f, %f);", lastLat, lastLng), null);
            } catch (Exception ignored) {}
        }
        announceMyLocation();
    }

    private void announceMyLocation() {
        String msg = hasGpsFix
                ? String.format(Locale.US, "Su ubicación actual es latitud %.5f, longitud %.5f.", lastLat, lastLng)
                : "Aún sin señal GPS. Espere unos segundos al aire libre.";
        speak(msg);
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    private void updateMapPosition(double lat, double lng) {
        if (mapWebView == null || !isMapReady || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            try {
                if (mapWebView != null) {
                    mapWebView.evaluateJavascript(String.format(Locale.US,
                            "setUbicacionUsuario(%f, %f, 'Mi Ubicación Real');", lat, lng), null);
                }
            } catch (Exception ignored) {}
        });
    }

    // ================= DATOS REALES: OVERPASS =================

    private void loadCurrentCategory() {
        if (currentCategory == null) return;
        if ("recent".equals(currentCategory.key)) {
            loadHistory();
            return;
        }
        if (!hasGpsFix) {
            // Sin fix aun: se cargara al primer onGpsUpdate
            return;
        }
        queryOverpass(currentCategory);
    }

    private void queryOverpass(Category cat) {
        executor.execute(() -> {
            try {
                StringBuilder union = new StringBuilder();
                String[] filters = cat.filter.split("\\|");
                for (String f : filters) {
                    union.append("node(around:").append(radiusMeters).append(",")
                            .append(String.format(Locale.US, "%.5f,%.5f", lastLat, lastLng))
                            .append(")[").append(f).append("];");
                }
                String data = "[out:json][timeout:20];(" + union + ");out center 20;";
                String url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(data, StandardCharsets.UTF_8.name());
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent", "KUIDA-Android/1.0 (accesibilidad)");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder raw = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
                reader.close();
                List<Place> result = parsePlaces(raw.toString());
                if (!isAdded()) return;
                places = result;
                pushPinsToMap();
                renderPlaces();
                if (state == State.LIST && !result.isEmpty()) {
                    Place first = result.get(0);
                    speak(result.size() + " lugares de " + cat.label + ". El más cercano: "
                            + first.name + ", a " + Math.round(first.dist) + " metros al " + first.cardinal + ".");
                } else if (state == State.LIST) {
                    renderPlaces();
                }
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Sin conexión para buscar lugares. Verifique internet.", Toast.LENGTH_LONG).show());
                renderPlaces();
            }
        });
    }

    private List<Place> parsePlaces(String rawJson) throws Exception {
        List<Place> out = new ArrayList<>();
        JSONArray elements = new JSONObject(rawJson).optJSONArray("elements");
        if (elements == null) return out;
        for (int i = 0; i < elements.length(); i++) {
            JSONObject item = elements.getJSONObject(i);
            double plat = item.optDouble("lat", Double.NaN);
            double plon = item.optDouble("lon", Double.NaN);
            if (Double.isNaN(plat) || Double.isNaN(plon)) continue;
            JSONObject center = item.optJSONObject("center");
            if (center != null) { plat = center.optDouble("lat", plat); plon = center.optDouble("lon", plon); }
            JSONObject tags = item.optJSONObject("tags");
            String name = tags != null ? tags.optString("name", "") : "";
            if (name.isEmpty()) name = "Punto de interés";
            String type = tags != null ? tags.optString("amenity",
                    tags.optString("shop", tags.optString("tourism",
                            tags.optString("leisure", "lugar")))) : "lugar";
            float[] r = new float[1];
            Location.distanceBetween(lastLat, lastLng, plat, plon, r);
            out.add(new Place(name, type, plat, plon, r[0], cardinalTo(plat, plon)));
        }
        Collections.sort(out, (a, b) -> Double.compare(a.dist, b.dist));
        if (out.size() > 20) return out.subList(0, 20);
        return out;
    }

    private String cardinalTo(double plat, double plon) {
        float[] r = new float[1];
        Location.distanceBetween(lastLat, lastLng, plat, plon, r);
        // bearing manual
        double dLon = Math.toRadians(plon - lastLng);
        double lat1 = Math.toRadians(lastLat);
        double lat2 = Math.toRadians(plat);
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        double bearing = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
        String[] winds = {"Norte", "Noreste", "Este", "Sureste", "Sur", "Suroeste", "Oeste", "Noroeste"};
        return winds[(int) Math.round(bearing / 45.0) % 8];
    }

    private void refreshDistances() {
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            float[] r = new float[1];
            Location.distanceBetween(lastLat, lastLng, p.lat, p.lng, r);
            places.set(i, new Place(p.name, p.type, p.lat, p.lng, r[0], cardinalTo(p.lat, p.lng)));
        }
        Collections.sort(places, (a, b) -> Double.compare(a.dist, b.dist));
        pushPinsToMap();
        renderPlaces();
    }

    private void pushPinsToMap() {
        if (mapWebView == null || !isMapReady) return;
        try {
            JSONArray arr = new JSONArray();
            for (Place p : places) {
                JSONObject o = new JSONObject();
                o.put("nombre", p.name);
                o.put("tipo", p.type);
                o.put("lat", p.lat);
                o.put("lon", p.lng);
                arr.put(o);
            }
            final String json = arr.toString().replace("\\", "\\\\").replace("'", "\\'");
            requireActivity().runOnUiThread(() -> {
                try {
                    if (mapWebView != null) mapWebView.evaluateJavascript("cargarPines('" + json + "');", null);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    // ================= HISTORIAL REAL LOCAL =================

    private void saveHistory(Place p) {
        try {
            SharedPreferences prefs = requireContext().getSharedPreferences(HIST_PREFS, Context.MODE_PRIVATE);
            JSONArray arr;
            try { arr = new JSONArray(prefs.getString(HIST_KEY, "[]")); }
            catch (Exception e) { arr = new JSONArray(); }
            JSONArray next = new JSONArray();
            JSONObject me = new JSONObject();
            me.put("nombre", p.name); me.put("tipo", p.type);
            me.put("lat", p.lat); me.put("lon", p.lng);
            next.put(me);
            for (int i = 0; i < arr.length() && next.length() < 10; i++) {
                JSONObject o = arr.getJSONObject(i);
                if (Math.abs(o.optDouble("lat") - p.lat) > 1e-6 || Math.abs(o.optDouble("lon") - p.lng) > 1e-6) {
                    next.put(o);
                }
            }
            prefs.edit().putString(HIST_KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadHistory() {
        executor.execute(() -> {
            List<Place> out = new ArrayList<>();
            try {
                SharedPreferences prefs = requireContext().getSharedPreferences(HIST_PREFS, Context.MODE_PRIVATE);
                JSONArray arr = new JSONArray(prefs.getString(HIST_KEY, "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    double plat = o.optDouble("lat"), plon = o.optDouble("lon");
                    float[] r = new float[1];
                    if (hasGpsFix) Location.distanceBetween(lastLat, lastLng, plat, plon, r);
                    else r[0] = 0;
                    out.add(new Place(o.optString("nombre", "Lugar"), o.optString("tipo", "lugar"),
                            plat, plon, r[0], hasGpsFix ? cardinalTo(plat, plon) : ""));
                }
            } catch (Exception ignored) {}
            if (!isAdded()) return;
            places = out;
            pushPinsToMap();
            renderPlaces();
        });
    }

    // ================= BUSQUEDA REAL: NOMINATIM =================

    private void showSearchDialog() {
        Context c = requireContext();
        EditText input = new EditText(c);
        input.setHint("Ej: hospital, farmacia, parada...");
        input.setSingleLine(true);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(c, R.style.Theme_Kuida_Dialog)
                .setTitle("Buscar lugar real")
                .setView(input)
                .setPositiveButton("Buscar", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) searchNominatim(q);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void searchNominatim(String q) {
        Toast.makeText(requireContext(), "Buscando \"" + q + "\"...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                String url = "https://nominatim.openstreetmap.org/search?format=json&limit=8&q="
                        + URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                        + "&viewbox=" + String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f",
                        lastLng - 0.2, lastLat + 0.2, lastLng + 0.2, lastLat - 0.2)
                        + "&bounded=1";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "KUIDA-Android/1.0 (accesibilidad)");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder raw = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
                reader.close();
                JSONArray arr = new JSONArray(raw.toString());
                List<Place> out = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    double plat = o.optDouble("lat", Double.NaN);
                    double plon = o.optDouble("lon", Double.NaN);
                    if (Double.isNaN(plat)) continue;
                    String name = o.optString("display_name", "Lugar").split(",")[0];
                    float[] r = new float[1];
                    if (hasGpsFix) Location.distanceBetween(lastLat, lastLng, plat, plon, r);
                    out.add(new Place(name, o.optString("type", "lugar"), plat, plon, r[0],
                            hasGpsFix ? cardinalTo(plat, plon) : ""));
                }
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (out.isEmpty()) {
                        Toast.makeText(requireContext(), "Sin resultados reales para \"" + q + "\"", Toast.LENGTH_LONG).show();
                        return;
                    }
                    currentCategory = new Category("search", "Resultados: " + q, R.drawable.ic_search, "");
                    places = out;
                    state = State.LIST;
                    rebuildScreen();
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Sin conexión para buscar.", Toast.LENGTH_LONG).show());
            }
        });
    }

    // ================= RADIO DE BUSQUEDA =================

    private void showRadiusDialog() {
        String[] labels = {"500 metros", "1 kilómetro", "2 kilómetros", "5 kilómetros"};
        int[] values = {500, 1000, 2000, 5000};
        int checked = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == radiusMeters) checked = i;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Kuida_Dialog)
                .setTitle("Radio de búsqueda real")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    radiusMeters = values[which];
                    d.dismiss();
                    if (state == State.LIST) rebuildScreen();
                    Toast.makeText(requireContext(), "Radio: " + labels[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // ================= SEGUIMIENTO REAL =================

    private void startFollow() {
        if (detailPlace == null) return;
        followTarget = detailPlace;
        lastAnnounceTime = 0;
        lastAnnounceDist = -1;
        try { root.setKeepScreenOn(true); } catch (Exception ignored) {}
        speak("Siguiendo " + followTarget.name + ". Le avisaré la distancia mientras camina.");
        Toast.makeText(requireContext(), "Siguiendo lugar con guía por voz", Toast.LENGTH_SHORT).show();
    }

    private void stopFollow() {
        followTarget = null;
        try { if (root != null) root.setKeepScreenOn(false); } catch (Exception ignored) {}
    }

    private void updateFollow() {
        float[] r = new float[1];
        Location.distanceBetween(lastLat, lastLng, followTarget.lat, followTarget.lng, r);
        double dist = r[0];
        String card = cardinalTo(followTarget.lat, followTarget.lng);
        long now = System.currentTimeMillis();

        if (isAdded()) {
            requireActivity().runOnUiThread(() -> {
                View distView = bottomBox.findViewWithTag("detail_dist");
                if (distView instanceof TextView) {
                    ((TextView) distView).setText("Distancia: " + Math.round(dist) + " metros\n" + card);
                }
            });
        }
        updateMapPosition(lastLat, lastLng);

        if (dist < 50) {
            vibrate();
            if (now - lastAnnounceTime > 20000) {
                lastAnnounceTime = now;
                speak("Ha llegado a " + followTarget.name + ". Está a " + Math.round(dist) + " metros.");
            }
            return;
        }
        if (now - lastAnnounceTime > 12000 || (lastAnnounceDist > 0 && Math.abs(dist - lastAnnounceDist) > 60)) {
            lastAnnounceTime = now;
            lastAnnounceDist = dist;
            speak("Faltan " + Math.round(dist) + " metros al " + card + " para " + followTarget.name + ".");
        }
    }

    // ================= NAVEGACION EXTERNA REAL =================

    private void openExternalNavigation() {
        if (detailPlace == null) return;
        Context c = requireContext();
        String dest = detailPlace.lat + "," + detailPlace.lng;
        try {
            android.content.Intent intent;
            switch (travelMode) {
                case "driving":
                    intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("google.navigation:q=" + dest + "&mode=d"));
                    intent.setPackage("com.google.android.apps.maps");
                    break;
                case "transit":
                    intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + dest + "&travelmode=transit"));
                    break;
                case "uber":
                    try {
                        intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[latitude]=" + detailPlace.lat + "&dropoff[longitude]=" + detailPlace.lng));
                        startActivity(intent);
                        return;
                    } catch (Exception e) {
                        intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=com.ubercab"));
                    }
                    break;
                case "walking":
                default:
                    intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("google.navigation:q=" + dest + "&mode=w"));
                    intent.setPackage("com.google.android.apps.maps");
                    break;
            }
            startActivity(intent);
        } catch (Exception e) {
            try {
                String mode = travelMode.equals("driving") ? "driving" : travelMode.equals("transit") ? "transit" : "walking";
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.openstreetmap.org/directions?from=" + lastLat + "%2C" + lastLng + "&to=" + dest + "&route=" + mode)));
            } catch (Exception ignored) {
                Toast.makeText(c, "No hay aplicación de navegación disponible.", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ================= UTILIDADES =================

    private Category findCategory(String key) {
        if (key == null) return null;
        for (Category cat : CATEGORIES) if (cat.key.equals(key)) return cat;
        return null;
    }

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void speak(String text) {
        if (tts == null || !isAdded()) return;
        try {
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null && am.isTouchExplorationEnabled()) return; // TalkBack ya lo lee
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kuida-map");
        } catch (Exception ignored) {}
    }

    private void vibrate() {
        try {
            Vibrator vib = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vib == null || !vib.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(120);
            }
        } catch (Exception ignored) {}
    }

    /** Color por recurso dia/noche (el sistema resuelve; sin ramas manuales). */
    private int res(int resId, String fallbackHex) {
        try {
            Context ctx = getContext();
            if (ctx != null) return ContextCompat.getColor(ctx, resId);
        } catch (Exception ignored) {}
        return Color.parseColor(fallbackHex);
    }

    private int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}