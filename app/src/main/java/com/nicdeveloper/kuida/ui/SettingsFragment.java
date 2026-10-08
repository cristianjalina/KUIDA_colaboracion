package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import com.nicdeveloper.kuida.data.ProfileManager;
import com.nicdeveloper.kuida.MainActivity;

public class SettingsFragment extends BaseKuidaFragment {
    @Override
    protected void buildContent(Context context) {
        screenTopBar(context, "Ajustes");

        metric(context, "Condición y Necesidad", ProfileManager.label(context));

        card(context, "Modo Asistencia Completa", "Activa todas las herramientas combinadas: lectura visual, transcripción y apoyo.", v -> {
            selectProfile(context, ProfileManager.PROFILE_ALL);
            Toast.makeText(context, "Perfil completo activado", Toast.LENGTH_SHORT).show();
        });

        card(context, "Perfil visual (ceguera o baja visión)", "Prioriza la lectura por voz, la consulta de imágenes y el mapa accesible.", v -> {
            selectProfile(context, ProfileManager.PROFILE_BLIND);
            Toast.makeText(context, "Perfil visual activado", Toast.LENGTH_SHORT).show();
        });

        card(context, "Perfil auditivo (sordera o hipoacusia)", "Prioriza transcripción en tiempo real y avisos visuales o por vibración.", v -> {
            selectProfile(context, ProfileManager.PROFILE_DEAF);
            Toast.makeText(context, "Perfil sordo activado", Toast.LENGTH_SHORT).show();
        });

        card(context, "Perfil de comunicación del habla", "Ofrece frases, escritura y reproducción de texto en voz alta.", v -> {
            selectProfile(context, ProfileManager.PROFILE_MUTE);
            Toast.makeText(context, "Perfil mudo activado", Toast.LENGTH_SHORT).show();
        });


        card(context, "Ajustes del Sistema Android", "Configurar lectores de pantalla TalkBack y opciones del teléfono.", v -> {
            try {
                context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {}
        });

        card(context, "Protección Inercial contra Caídas", "Servicio en primer plano activo con sensores del dispositivo.", v -> {
            Toast.makeText(context, "Protección inercial activa", Toast.LENGTH_SHORT).show();
        });

        card(context, "Privacidad y Seguridad", "Tus datos se procesan en tu dispositivo de forma segura y privada.", v -> {
            Toast.makeText(context, "Modo local seguro activo", Toast.LENGTH_SHORT).show();
        });
    }

    private void selectProfile(Context context, String profile) {
        ProfileManager.setProfile(context, profile);
        if (getActivity() instanceof MainActivity) {
            // Reconstruye el Inicio y la navegación con las reglas del perfil nuevo.
            getActivity().recreate();
        }
    }
}
