package com.nicdeveloper.kuida.sensor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.nicdeveloper.kuida.MainActivity;
import com.nicdeveloper.kuida.R;

public class FallDetectionService extends Service implements SensorEventListener {
    private static final String TAG = "FallDetectionService";
    private static final String CHANNEL_ID = "kuida_fall_detection_channel";
    private static final int NOTIFICATION_ID = 1001;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isFreeFalling = false;
    private long freeFallStartTime = 0;
    private static final float GRAVITY_EARTH = SensorManager.GRAVITY_EARTH;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = createForegroundNotification("Kuida te esta protegiendo", "Deteccion inercial de caidas y SOS activa.");
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } catch (Exception e) {
                Log.w(TAG, "FGS special use fallback: " + e.getMessage());
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 0);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Proteccion Inercial Kuida",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitoreo continuo de caidas para enviar SOS");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createForegroundNotification(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.kuida_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Formula: Magnitud A = sqrt(x^2 + y^2 + z^2)
            double magnitude = Math.sqrt(x * x + y * y + z * z);
            double gForce = magnitude / GRAVITY_EARTH;

            long now = System.currentTimeMillis();

            // Deteccion de fase 1: Caida libre (gForce cercana a 0g, < 0.4g)
            if (gForce < 0.4) {
                isFreeFalling = true;
                freeFallStartTime = now;
            } else if (isFreeFalling) {
                // Si despues de caida libre se produce un impacto brusco (> 2.7g) dentro de una ventana de 1 segundo
                if (gForce > 2.7 && (now - freeFallStartTime < 1000)) {
                    isFreeFalling = false;
                    onFallDetected();
                } else if (now - freeFallStartTime >= 1000) {
                    isFreeFalling = false;
                }
            }
        }
    }

    private void onFallDetected() {
        Log.w(TAG, "¡ALERTA DE CAIDA DETECTADA!");

        // Vibracion de emergencia
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
            }
        }

        // Enviar broadcast o preparar llamada/SMS SOS
        Intent sosIntent = new Intent(Intent.ACTION_SENDTO);
        sosIntent.setData(Uri.parse("smsto:"));
        sosIntent.putExtra("sms_body", "¡ALERTA SOS KUIDA! Se ha detectado una caida fuerte de la persona asistida. Por favor contactar inmediatamente.");
        sosIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(sosIntent);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo abrir app de SMS para alerta de caida: " + e.getMessage());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
