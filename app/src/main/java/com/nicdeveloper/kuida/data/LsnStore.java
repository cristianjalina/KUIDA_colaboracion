package com.nicdeveloper.kuida.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Acceso compartido a las señas LSN en assets (categoría, nombre bonito, imagen). */
public final class LsnStore {

    public static class Entry {
        public final String category;
        public final String raw;
        public final String label;
        public final String asset;
        Entry(String category, String raw, String label) {
            this.category = category;
            this.raw = raw;
            this.label = label;
            this.asset = "lsn/" + category + "_" + raw + ".png";
        }
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
        PRETTY.put("panama", "Panamá");
        PRETTY.put("repdom", "Rep. Dominicana");
        PRETTY.put("centroamerica", "Centroamérica");
        PRETTY.put("adios", "Adiós");
        PRETTY.put("cafe", "Café");
    }

    private static List<Entry> cache;
    private static LruCache<String, Bitmap> images;

    private LsnStore() {}

    public static synchronized List<Entry> all(Context context) {
        if (cache != null) return cache;
        List<Entry> out = new ArrayList<>();
        try {
            String[] files = context.getAssets().list("lsn");
            if (files != null) {
                for (String f : files) {
                    if (!f.endsWith(".png")) continue;
                    String base = f.substring(0, f.length() - 4);
                    int cut = base.indexOf('_');
                    if (cut < 0) continue;
                    out.add(new Entry(base.substring(0, cut),
                            base.substring(cut + 1),
                            pretty(base.substring(cut + 1))));
                }
            }
        } catch (Exception ignored) {}
        cache = out;
        return out;
    }

    /** Busca una seña por palabra (tolerante a tildes y mayúsculas). */
    public static Entry find(Context context, String word) {
        String q = norm(word);
        if (q.isEmpty()) return null;
        Entry partial = null;
        for (Entry e : all(context)) {
            String label = norm(e.label);
            String raw = norm(e.raw);
            if (label.equals(q) || raw.equals(q)) return e;
            if (partial == null && (label.contains(q) || q.contains(label)) && q.length() >= 3) {
                partial = e;
            }
        }
        return partial;
    }

    public static Bitmap image(Context context, Entry e, int maxDim) {
        if (images == null) {
            int memKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
            images = new LruCache<String, Bitmap>(memKb / 16) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }
        Bitmap hit = images.get(e.asset);
        if (hit != null) return hit;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream is = context.getAssets().open(e.asset)) {
                BitmapFactory.decodeStream(is, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= maxDim
                    && bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream is = context.getAssets().open(e.asset)) {
                Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
                if (bmp != null) images.put(e.asset, bmp);
                return bmp;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String pretty(String raw) {
        String hit = PRETTY.get(raw.toLowerCase(Locale.US));
        if (hit != null) return hit;
        String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) return raw;
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.US), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9 ]", " ").trim();
    }
}
