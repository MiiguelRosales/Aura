package com.example.aura;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.Date;

public class ChatNotificationForegroundService extends Service {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String KEY_GUARDIAN_VINCULADO_ID = "guardianVinculadoId";
    private static final String KEY_EXPLORADOR_VINCULADO_ID = "exploradorVinculadoId";
    private static final String KEY_ULTIMO_MENSAJE_LEIDO_PREFIX = "ultimoMensajeLeido_";
    private static final String KEY_ULTIMO_MENSAJE_ALERTADO_PREFIX = "ultimoMensajeAlertado_";
    private static final String CHANNEL_ID = "aura_background_notifications";
    private static final int FOREGROUND_NOTIFICATION_ID = 8100;

    private ListenerRegistration listenerChatNotificaciones;
    private String chatNotificacionesUid;

    @Override
    public void onCreate() {
        super.onCreate();
        crearCanalServicio();
        startForeground(FOREGROUND_NOTIFICATION_ID, crearNotificacionPersistente());
        iniciarObservadorNotificacionesChat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        iniciarObservadorNotificacionesChat();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        detenerObservadorNotificacionesChat();
        // Intentar reprogramar reinicio rápido del servicio
        Intent restartIntent = new Intent(this, ServiceRestartReceiver.class);
        restartIntent.setAction(ServiceRestartReceiver.ACTION_RESTART_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                2,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pendingIntent);
        }

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Programar reinicio del servicio si el proceso es eliminado
        Intent restartIntent = new Intent(this, ServiceRestartReceiver.class);
        restartIntent.setAction(ServiceRestartReceiver.ACTION_RESTART_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                3,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pendingIntent);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void iniciarObservadorNotificacionesChat() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            stopSelf();
            return;
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String uidActual = auth.getCurrentUser().getUid();

        if (uidActual.equals(chatNotificacionesUid) && listenerChatNotificaciones != null) {
            return;
        }

        detenerObservadorNotificacionesChat();
        chatNotificacionesUid = uidActual;

        firestore.collection("usuarios")
                .document(uidActual)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        return;
                    }

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    String tipoUsuario = defaultString(
                            documentSnapshot.getString(KEY_TIPO_USUARIO),
                            prefs.getString(KEY_TIPO_USUARIO, "GUARDIAN")
                    );

                    String guardianId;
                    String exploradorId;

                    if ("GUARDIAN".equals(tipoUsuario)) {
                        guardianId = uidActual;
                        exploradorId = documentSnapshot.getString(KEY_EXPLORADOR_VINCULADO_ID);
                    } else if ("EXPLORADOR".equals(tipoUsuario)) {
                        guardianId = documentSnapshot.getString(KEY_GUARDIAN_VINCULADO_ID);
                        exploradorId = uidActual;
                    } else {
                        return;
                    }

                    if (TextUtils.isEmpty(guardianId) || TextUtils.isEmpty(exploradorId)) {
                        return;
                    }

                    escucharUltimoMensaje(construirChatId(guardianId, exploradorId), uidActual);
                });
    }

    private void escucharUltimoMensaje(@NonNull String chatId, @NonNull String uidActual) {
        if (listenerChatNotificaciones != null) {
            listenerChatNotificaciones.remove();
            listenerChatNotificaciones = null;
        }

        listenerChatNotificaciones = FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("mensajes")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || snapshot.isEmpty()) {
                        return;
                    }

                    DocumentSnapshot ultimoMensaje = snapshot.getDocuments().get(0);
                    String remitenteId = ultimoMensaje.getString("remitenteId");
                    if (uidActual.equals(remitenteId)) {
                        return;
                    }

                    long ultimoTimestampMillis = obtenerTimestampMillis(ultimoMensaje);
                    if (ultimoTimestampMillis <= 0) {
                        return;
                    }

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    long ultimoLeidoMillis = prefs.getLong(KEY_ULTIMO_MENSAJE_LEIDO_PREFIX + chatId, 0L);
                    long ultimoAlertadoMillis = prefs.getLong(KEY_ULTIMO_MENSAJE_ALERTADO_PREFIX + chatId, 0L);
                    if (ultimoTimestampMillis <= ultimoLeidoMillis || ultimoTimestampMillis <= ultimoAlertadoMillis) {
                        return;
                    }

                    if (AuraApplication.isAppInForeground()) {
                        return;
                    }

                    String titulo = defaultString(ultimoMensaje.getString("remitenteNombre"), "Aura");
                    String cuerpo = defaultString(ultimoMensaje.getString("contenido"), "Nuevo mensaje");
                    mostrarNotificacionChat(chatId, titulo, cuerpo);
                    prefs.edit()
                            .putLong(KEY_ULTIMO_MENSAJE_ALERTADO_PREFIX + chatId, ultimoTimestampMillis)
                            .apply();
                });
    }

    private long obtenerTimestampMillis(@NonNull DocumentSnapshot snapshot) {
        Timestamp timestamp = snapshot.getTimestamp("timestamp");
        if (timestamp != null) {
            return timestamp.toDate().getTime();
        }
        Date date = snapshot.getDate("timestamp");
        if (date != null) {
            return date.getTime();
        }
        return 0L;
    }

    private void mostrarNotificacionChat(@NonNull String chatId, @NonNull String titulo, @NonNull String mensaje) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Chat en segundo plano",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, PantallaChat.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                obtenerIdNotificacionChat(chatId),
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(obtenerIdNotificacionChat(chatId), builder.build());
    }

    private Notification crearNotificacionPersistente() {
        Intent intent = new Intent(this, pantalla_inicio.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Aura activa")
                .setContentText("Escuchando mensajes en segundo plano")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void crearCanalServicio() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Servicio en segundo plano de Aura",
                NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);
    }

    private void detenerObservadorNotificacionesChat() {
        if (listenerChatNotificaciones != null) {
            listenerChatNotificaciones.remove();
            listenerChatNotificaciones = null;
        }
    }

    private int obtenerIdNotificacionChat(@NonNull String chatId) {
        return Math.abs(chatId.hashCode());
    }

    private String construirChatId(@NonNull String guardianId, @NonNull String exploradorId) {
        return guardianId + "_" + exploradorId;
    }

    private String defaultString(String valor, String porDefecto) {
        return valor != null && !valor.trim().isEmpty() ? valor : porDefecto;
    }
}