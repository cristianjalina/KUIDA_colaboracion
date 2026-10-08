package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.nicdeveloper.kuida.R;

public class TermsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context c = requireContext();

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(res(R.color.kuida_surface, "#FFFFFF"));

        // Header bar
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor("#008080"));
        header.setPadding(dp(c, 8), dp(c, 12), dp(c, 16), dp(c, 12));

        ImageView backBtn = new ImageView(c);
        backBtn.setImageResource(R.drawable.ic_arrow_back);
        backBtn.setColorFilter(Color.WHITE);
        backBtn.setPadding(dp(c, 8), dp(c, 4), dp(c, 12), dp(c, 4));
        backBtn.setClickable(true);
        backBtn.setFocusable(true);
        backBtn.setOnClickListener(v -> {
            try { NavHostFragment.findNavController(this).popBackStack(); } catch (Exception ignored) {}
        });
        header.addView(backBtn, new LinearLayout.LayoutParams(dp(c, 44), dp(c, 36)));

        TextView headerTitle = new TextView(c);
        headerTitle.setText("Terminos y Condiciones");
        headerTitle.setTextSize(18);
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setTypeface(Typeface.create("sans", Typeface.BOLD));
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Scrollable content
        ScrollView scroll = new ScrollView(c);
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 40));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        addSection(c, content, "1. Aceptacion de los Terminos",
                "Al utilizar KUIDA, usted acepta los presentes terminos y condiciones. Si no esta de acuerdo con alguna parte de estos terminos, por favor no utilice la aplicacion.");

        addSection(c, content, "2. Descripcion del Servicio",
                "KUIDA es una aplicacion de accesibilidad disenada para personas con discapacidades visuales, auditivas y de comunicacion. La app utiliza tecnologias de reconocimiento de imagenes, sintesis de voz y procesamiento de lenguaje natural para asistir a sus usuarios.");

        addSection(c, content, "3. Tecnologias Utilizadas",
                "KUIDA es un MVP en prueba. Incluye herramientas de lectura y voz, transcripción, frases de comunicación, un catálogo de consulta de LSN, alertas experimentales de sonidos y mapa. La descripción de fotografías usa la API de Gemini y requiere internet; la imagen se transmite al servicio para su análisis. Las solicitudes de apoyo y la detección automática de caídas son demostraciones y no conectan con cuidadores ni servicios de emergencia.");

        addSection(c, content, "4. Privacidad y Datos",
                "Las preferencias y el contador del módulo de apoyo se guardan localmente en el teléfono. Cuando se solicita una descripción, la fotografía se envía a la API de Gemini por internet para procesarla. No capture información sensible si no desea transmitirla. El módulo de apoyo no tiene backend ni sincroniza datos con cuidadores. La ubicación se utiliza cuando se abre la función de mapa.");

        addSection(c, content, "5. Permisos Requeridos",
                "La aplicacion requiere los siguientes permisos para funcionar correctamente:\n\n" +
                "- Camara: Para capturar imagenes y videos del entorno.\n" +
                "- Microfono: Para reconocimiento de voz.\n" +
                "- Ubicacion (GPS): Para navegacion y mapa de transporte.\n" +
                "- Almacenamiento: Para guardar configuraciones locales.\n" +
                "- Sensores: algunas herramientas experimentales pueden solicitar acceso a sensores; la detección automática de caídas no está habilitada en este MVP.");

        addSection(c, content, "6. Limitaciones de Responsabilidad",
                "KUIDA es una herramienta de asistencia complementaria. No debe ser utilizada como unico medio de orientacion o seguridad. El usuario asume la responsabilidad de su propio bienestar y seguridad. Las descripciones generadas por la aplicacion pueden contener imprecisiones.");

        addSection(c, content, "7. Actualizaciones",
                "Nos reservamos el derecho de actualizar estos terminos en cualquier momento. Le notificaremos sobre cambios significativos a traves de la aplicacion.");

        addSection(c, content, "8. Contacto",
                "Para preguntas o sugerencias sobre estos terminos, puede contactarnos a traves de la configuracion de la aplicacion.");

        return root;
    }

    private void addSection(Context c, LinearLayout content, String title, String body) {
        TextView titleView = new TextView(c);
        titleView.setText(title);
        titleView.setTextSize(15);
        titleView.setTextColor(res(R.color.kuida_teal, "#008080"));
        titleView.setTypeface(Typeface.create("sans", Typeface.BOLD));
        titleView.setPadding(0, dp(c, 16), 0, dp(c, 6));
        content.addView(titleView);

        TextView bodyView = new TextView(c);
        bodyView.setText(body);
        bodyView.setTextSize(14);
        bodyView.setTextColor(res(R.color.kuida_slate, "#4C7280"));
        bodyView.setLineSpacing(0, 1.4f);
        bodyView.setPadding(0, 0, 0, dp(c, 8));
        content.addView(bodyView);

        View divider = new View(c);
        divider.setBackgroundColor(res(R.color.kuida_border_subtle, "#E0EAEA"));
        content.addView(divider, new LinearLayout.LayoutParams(-1, dp(c, 1)));
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

    private int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
