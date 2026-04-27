package com.example.aura;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class AuraMessagingService extends FirebaseMessagingService {

    private static final String TAG = "AuraMessagingService";
    private static final String CHANNEL_ID = "aura_notifications";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Mensaje recibido de: " + remoteMessage.getFrom());

        // Manejar datos del mensaje
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();
            String titulo = data.get("title");
            String body = data.get("body");
            String tipo = data.get("type"); // Por ejemplo "chat"

            mostrarNotificacion(titulo != null ? titulo : "Aura", body != null ? body : "Nuevo mensaje", tipo);
        }

        // Manejar notificación del mensaje (si viene como notificación de Firebase)
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            mostrarNotificacion(
                    title != null ? title : "Aura",
                    body != null ? body : "Nuevo aviso",
                    "general"
            );
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nuevo token FCM: " + token);
        actualizarTokenEnFirestore(token);
    }

    private void actualizarTokenEnFirestore(String token) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            FirebaseFirestore.getInstance().collection("usuarios")
                    .document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Token actualizado en Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error al actualizar token", e));
        }
    }

    private void mostrarNotificacion(String titulo, String mensaje, String tipo) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Notificaciones de Aura",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent;
        if ("chat".equals(tipo)) {
            intent = new Intent(this, PantallaChat.class);
        } else if ("guardian".equals(tipo)) {
            intent = new Intent(this, PantallaGuardian.class);
        } else {
            intent = new Intent(this, pantalla_inicio.class);
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher) // Asegúrate de tener un icono adecuado
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }
}
