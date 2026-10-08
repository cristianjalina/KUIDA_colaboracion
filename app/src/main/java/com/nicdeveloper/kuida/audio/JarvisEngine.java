package com.nicdeveloper.kuida.audio;

import android.Manifest;
import android.os.BatteryManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import com.nicdeveloper.kuida.data.LsnStore;
import com.nicdeveloper.kuida.data.ProfileManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Cerebro de Jarvis: intenciones 100% offline primero (hora, batería,
 * ubicación, señas, ayuda). Solo si nada coincide se consulta la nube.
 */
public final class JarvisEngine {

    public enum Action {
        NONE, OPEN_MAP, OPEN_SUPPORT, DIAL,
        OPEN_CONTACTS, OPEN_CAMERA, OPEN_GALLERY, OPEN_SMS, OPEN_WHATSAPP,
        FLASHLIGHT_ON, FLASHLIGHT_OFF, OPEN_APP
    }

    public static class Result {
        public final String reply;
        public final String imageAsset; // asset lsn/... o null
        public final String imageLabel;
        public final Action action;
        public final String param; // ej. nombre de app para OPEN_APP
        Result(String reply, String imageAsset, String imageLabel, Action action) {
            this(reply, imageAsset, imageLabel, action, "");
        }
        Result(String reply, String imageAsset, String imageLabel, Action action, String param) {
            this.reply = reply;
            this.imageAsset = imageAsset;
            this.imageLabel = imageLabel;
            this.action = action;
            this.param = param != null ? param : "";
        }
    }

    private JarvisEngine() {}

    public static Result resolve(Context context, String rawText) {
        String t = " " + LsnStore.norm(rawText) + " ";
        Context app = context.getApplicationContext();

        if (has(t, "hora", "que hora es", "qué hora")) {
            String h = new SimpleDateFormat("h:mm a", new Locale("es")).format(new Date());
            return new Result("Son las " + h, null, null, Action.NONE);
        }
        if (has(t, "bateria", "pila", "carga")) {
            return new Result("Tu batería está al " + battery(app) + " por ciento.", null, null, Action.NONE);
        }
        if (has(t, "donde estoy", "ubicacion", "ubicación", "donde me encuentro", "mi posicion", "mi posición")) {
            return new Result(whereAmI(context), null, null, Action.NONE);
        }
        if (has(t, "llamar", "llama", "telefono", "teléfono", "marcame", "márcame")) {
            return new Result("Abriendo el marcador para que llames a tu contacto.", null, null, Action.DIAL);
        }
        // Acciones del dispositivo (offline, intents seguros del sistema)
        if (has(t, "contacto", "contactos", "agenda", "libreta", "directorio")) {
            return new Result("Abriendo tu libreta de contactos.", null, null, Action.OPEN_CONTACTS);
        }
        if (has(t, "galeria", "galería", "mis fotos", "misfotos", "album", "álbum")) {
            return new Result("Abriendo tu galería de fotos.", null, null, Action.OPEN_GALLERY);
        }
        if (has(t, "foto", "fotografia", "fotografía", "camara", "cámara", "tomame", "tómame", "selfie")) {
            return new Result("Abriendo la cámara. Usa el botón de volumen para disparar.",
                    null, null, Action.OPEN_CAMERA);
        }
        if (has(t, "mensaje", "sms", "escribele", "escríbele", "texto a")) {
            return new Result("Abriendo mensajes para que escribas.", null, null, Action.OPEN_SMS);
        }
        if (has(t, "whatsapp", "wasap", "guasap", "watsap")) {
            return new Result("Abriendo WhatsApp.", null, null, Action.OPEN_WHATSAPP);
        }
        if (has(t, "linterna", "luz", "flash", "lampara", "lámpara", "foco")) {
            boolean off = has(t, "apaga", "apagala", "apágala", "off", "desactiva");
            return new Result(off ? "Apagando la linterna." : "Encendiendo la linterna.",
                    null, null, off ? Action.FLASHLIGHT_OFF : Action.FLASHLIGHT_ON);
        }
        if (has(t, "que aplicaciones", "qué aplicaciones", "que apps", "qué apps", "apps tienes",
                "aplicaciones tienes", "apps hay", "aplicaciones hay")) {
            return new Result(installedAppsAnswer(context), null, null, Action.NONE);
        }
        // Abrir aplicaciones por nombre: ÚLTIMO recurso (no robar "abre el mapa" etc.)
        String openApp = extractAfter(t, new String[]{"abre", "abrir", "inicia", "iniciar", "ejecuta", "ejecutar"});
        if (has(t, "emergencia", "sos", "ayuda", "auxilio", "peligro", "accidente")) {
            return new Result("Tranquilo, te llevo a tu red de apoyo con tu ficha médica y tu botón SOS.",
                    null, null, Action.OPEN_SUPPORT);
        }
        if (has(t, "mapa", "llevame", "llévame", "como llego", "cómo llego", "ruta", "donde queda", "dónde queda")) {
            return new Result("Abriendo tu mapa con lugares cercanos y guía por voz.",
                    null, null, Action.OPEN_MAP);
        }
        if (has(t, "quien eres", "quién eres", "tu nombre", "jarvis", "que eres", "qué eres")) {
            return new Result("Soy el asistente de Kuida. Entiendo órdenes sin internet: "
                    + "pregúntame la hora, tu ubicación, una seña, o pide ayuda.",
                    null, null, Action.NONE);
        }
        if (has(t, "que puedes hacer", "qué puedes hacer", "comandos", "ayuda con", "que sabes hacer")) {
            return new Result("Puedo decirte la hora y tu batería, decirte dónde estás, "
                    + "llamar, abrir tus contactos, la cámara o cualquier aplicación, "
                    + "encender la linterna, mostrarte señas, abrir tu mapa o tu red de apoyo. Sin internet.",
                    null, null, Action.NONE);
        }
        if (has(t, "gracias")) {
            return new Result("De nada. Aquí estoy para lo que necesites.", null, null, Action.NONE);
        }
        if (has(t, "hola", "buenos dias", "buenas tardes", "buenas noches", "buenas")) {
            return new Result("Hola. Soy el asistente de Kuida. ¿En qué te ayudo?", null, null, Action.NONE);
        }
        if (has(t, "chiste")) {
            return new Result("¿Qué le dice un ciego a otro ciego? Nos estamos viendo.",
                    null, null, Action.NONE);
        }
        // Señas: "seña de X", "cómo se dice X", "X en señas"
        String signWord = extractSignWord(t);
        if (signWord != null) {
            if (!ProfileManager.canUseSignLanguageCatalog(context)) {
                return new Result("El catálogo de Lengua de Señas está disponible en los perfiles auditivo, de comunicación del habla y asistencia completa.",
                        null, null, Action.NONE);
            }
            LsnStore.Entry e = LsnStore.find(context, signWord);
            if (e != null) {
                return new Result("Esta es la seña de " + e.label + ". Mírala con calma.",
                        e.asset, e.label, Action.NONE);
            }
            return new Result("Aún no tengo la seña de " + signWord.trim()
                    + ". Tengo 244: prueba con familia, comida o saludos.",
                    null, null, Action.NONE);
        }
        // Abrir aplicaciones por nombre: ÚLTIMO recurso (no robar "abre el mapa" etc.)
        openApp = extractAfter(t, new String[]{"abre", "abrir", "inicia", "iniciar", "ejecuta", "ejecutar"});
        if (openApp != null) {
            return new Result("Abriendo " + openApp + ".", null, null, Action.OPEN_APP, openApp);
        }
        return null; // Nadie entendió: el fragmento consulta la nube
    }

    private static String extractSignWord(String t) {
        String[] markers = {"sena de", "seña de", "como se dice", "cómo se dice",
                "como digo", "en senas", "en señas", "muestrame", "muéstrame"};
        for (String m : markers) {
            int i = t.indexOf(" " + m + " ");
            if (i >= 0) {
                String rest = t.substring(i + m.length() + 2).trim();
                rest = rest.replaceAll("\\b(por favor|porfa)\\b", "").trim();
                if (!rest.isEmpty()) return rest;
            }
        }
        return null;
    }

    private static boolean has(String t, String... words) {
        for (String w : words) if (t.contains(" " + w + " ")) return true;
        return false;
    }

    private static int battery(Context context) {
        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception ignored) {}
        return -1;
    }

    private static String whereAmI(Context context) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                return "Aún no tengo permiso de ubicación. Ábrelo una vez en el Mapa.";
            }
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return "No pude leer el GPS ahora mismo.";
            Location best = null;
            for (String p : lm.getAllProviders()) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignored) {}
            }
            if (best == null) return "Todavía sin señal GPS. Sal al aire libre un momento.";
            return String.format(Locale.US,
                    "Estás cerca de latitud %.4f, longitud %.4f. Míralo en tu Mapa.",
                    best.getLatitude(), best.getLongitude());
        } catch (Exception ignored) {
            return "No pude leer el GPS ahora mismo.";
        }
    }

    /** Respuesta cuando no hay internet ni coincidencia local. */
    public static String offlineFallback() {
        return "Sin internet solo entiendo órdenes directas: dime qué hora es, "
                + "dónde estoy, llama a alguien, abre tus contactos, tómame una foto, "
                + "enciende la linterna, abre una aplicación, muéstrame una seña o pide ayuda.";
    }

    /** Pregunta abierta a la nube con personalidad Jarvis. */
    public static String cloudPrompt(String userText) {
        return "Eres el asistente de voz de Kuida, app nicaragüense de accesibilidad "
                + "para personas ciegas, sordas y mudas. Responde en español claro y breve "
                + "(máximo 2 frases), cálido, sin formato ni asteriscos. Pregunta del usuario: "
                + userText;
    }

    public static int signCount(Context context) {
        List<LsnStore.Entry> all = LsnStore.all(context);
        return all != null ? all.size() : 0;
    }

    /** Extrae lo que sigue a un verbo ("abre whatsapp" -> "whatsapp"). Null si queda vacío. */
    private static String extractAfter(String t, String[] verbs) {
        for (String v : verbs) {
            String mark = " " + v + " ";
            int i = t.indexOf(mark);
            if (i >= 0) {
                String rest = t.substring(i + mark.length()).trim()
                        .replaceAll("\\b(por favor|porfa|la|el|los|las|mi|mis|un|una|app|aplicacion|aplicación)\\b", " ")
                        .replaceAll("\\s+", " ").trim();
                if (!rest.isEmpty()) return rest;
            }
        }
        return null;
    }

    /** Apps instaladas que se pueden abrir (sin permisos especiales). */
    public static List<String[]> launchableApps(Context context) {
        List<String[]> out = new ArrayList<>();
        try {
            android.content.Intent main = new android.content.Intent(
                    android.content.Intent.ACTION_MAIN, null);
            main.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            android.content.pm.PackageManager pm = context.getPackageManager();
            for (android.content.pm.ResolveInfo ri
                    : pm.queryIntentActivities(main, 0)) {
                try {
                    String label = String.valueOf(ri.loadLabel(pm));
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(context.getPackageName())) continue;
                    out.add(new String[]{label, pkg});
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String installedAppsAnswer(Context context) {
        List<String[]> apps = launchableApps(context);
        if (apps.isEmpty()) return "No pude leer tus aplicaciones ahora mismo.";
        StringBuilder names = new StringBuilder();
        int show = Math.min(5, apps.size());
        for (int i = 0; i < show; i++) {
            if (i > 0) names.append(", ");
            names.append(apps.get(i)[0]);
        }
        return "Tienes " + apps.size() + " aplicaciones. Por ejemplo: " + names + ". Dime abre y el nombre.";
    }

    /** Busca el paquete cuya etiqueta contenga la consulta (insensible a tildes). */
    public static String findAppPackage(Context context, String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = LsnStore.norm(query.trim());
        String startsWith = null;
        for (String[] app : launchableApps(context)) {
            String label = LsnStore.norm(app[0]);
            if (label.contains(q)) {
                if (label.startsWith(q) || label.equals(q)) return app[1];
                if (startsWith == null) startsWith = app[1];
            }
        }
        return startsWith;
    }
}
