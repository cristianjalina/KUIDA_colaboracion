package com.nicdeveloper.kuida.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.TextView;
import android.widget.Toast;

import com.nicdeveloper.kuida.R;

import java.util.Locale;

public class SupportFragment extends BaseKuidaFragment {
    private static final String PREFS = "kuida_support";
    private static final String KEY_TASKS = "task_count";
    private TextView taskMetric;

    @Override
    protected void buildContent(Context context) {
        screenTopBar(context, "Apoyo (en prueba)");
        body(context, "Este módulo es experimental dentro del MVP. Algunas opciones son demostraciones: no existe una cuenta ni un servidor que conecte las solicitudes con familiares o cuidadores. Los registros se quedan en este teléfono.");
        taskMetric = metric(context, "Registros de prueba", taskCount(context) + " en este teléfono");
        card(context, "Registrar solicitud de prueba", "Solo aumenta un contador local; no guarda el detalle ni envía una tarea a otra persona.", v -> createTask(context));
        card(context, "Detección de caídas (demostración)", "La detección automática no está habilitada. Esta opción no vigila el teléfono ni puede pedir ayuda.", v -> Toast.makeText(context, "Demostración: no hay detección activa", Toast.LENGTH_SHORT).show());
        card(context, "Ficha médica de demostración", "Muestra cómo podría organizarse información de emergencia; no guarda datos médicos.", v -> showMedicalCard(context));
        card(context, "Preparar SMS de ayuda", "Abre mensajería con un borrador. La persona revisa y confirma antes de enviarlo.", v -> openSms());
        card(context, "Llamar a una persona de confianza", "Abre el marcador para que la persona elija el número y realice la llamada.", v -> openDialer());
        card(context, "Privacidad local", "El contador de solicitudes de prueba se guarda solo en este teléfono.", v -> Toast.makeText(context, "El contador está guardado en este teléfono", Toast.LENGTH_SHORT).show());
    }

    private int taskCount(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TASKS, 0);
    }

    private void createTask(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(KEY_TASKS, 0) + 1;
        prefs.edit().putInt(KEY_TASKS, count).apply();
        taskMetric.setText(String.format(Locale.US, "Registros de prueba\n%d en este teléfono", count));
        taskMetric.setContentDescription(count + " registros de prueba guardados en este teléfono");
        Toast.makeText(context, "Contador local actualizado; no se notificó a nadie", Toast.LENGTH_SHORT).show();
    }

    private void openSms() {
        String text = "Necesito apoyo. Por favor revisa mi ubicacion o llamame cuando puedas. Mensaje preparado desde KUIDA.";
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:"));
        intent.putExtra("sms_body", text);
        startIfAvailable(intent, "No hay aplicacion de mensajes disponible en este dispositivo.");
    }

    private void openDialer() {
        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:"));
        startIfAvailable(intent, "No hay marcador telefonico disponible en este dispositivo.");
    }

    private void startIfAvailable(Intent intent, String fallbackMessage) {
        if (!isAdded()) return;
        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(), fallbackMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void showMedicalCard(Context context) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context, R.style.Theme_Kuida_Dialog)
                .setTitle("Ejemplo de ficha médica")
                .setMessage("Esta pantalla es solo una demostración. No contiene datos médicos reales ni comparte información con contactos o servicios de emergencia.")
                .setPositiveButton("Cerrar", null)
                .show();
    }
}
