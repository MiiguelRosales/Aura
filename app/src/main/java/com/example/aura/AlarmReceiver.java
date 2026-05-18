package com.example.aura;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";
    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_CURRENT_UID = "currentUid";
    private static final String PREFS_JUEGO = "JuegoPrefs";
    private static final String KEY_LISTO_CONFIRMADO = "listo_confirmado_hoy";

    private String obtenerPrefsJuegosNombre(String uid) {
        return uid == null ? PREFS_JUEGO : PREFS_JUEGO + "_" + uid;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarma de medianoche recibida");

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String candidateUid = prefs.getString(KEY_CURRENT_UID, null);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            candidateUid = user.getUid();
        }

        if (candidateUid == null || candidateUid.trim().isEmpty()) {
            Log.w(TAG, "No hay UID disponible para enviar alerta");
            return;
        }

        final String uid = candidateUid;
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        // 1. Verificar si ya se confirmó hoy
        SharedPreferences juegoPrefs = context.getSharedPreferences(obtenerPrefsJuegosNombre(uid), Context.MODE_PRIVATE);
        String fechaConfirmado = juegoPrefs.getString(KEY_LISTO_CONFIRMADO, "");
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (hoy.equals(fechaConfirmado)) {
            Log.d(TAG, "Misión ya completada hoy. No se envía alerta.");
            return;
        }

        // 2. Obtener datos del guardián y enviar alerta
        firestore.collection("usuarios").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String guardianVinculadoId = documentSnapshot.getString("guardianVinculadoId");
                String nombreExplorador = documentSnapshot.getString("nombreUsuario");

                if (guardianVinculadoId != null && !guardianVinculadoId.trim().isEmpty()) {
                    enviarMensajeAlGuardian(firestore, guardianVinculadoId, nombreExplorador, uid);
                } else {
                    Log.w(TAG, "No hay guardián vinculado para enviar alerta");
                }
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Error al obtener perfil en alarma", e));
    }

    private void enviarMensajeAlGuardian(FirebaseFirestore firestore, String guardianId, String nombre, String exploradorId) {
        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("remitente", nombre != null ? nombre : "Explorador");
        mensaje.put("contenido", "ES IMPORTANTE QUE TE COMUNIQUES CON TU EXPLORADOR");
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("tipo", "alerta_timeout_critico");

        firestore.collection("mensajes")
                .document(guardianId)
                .collection("historial")
                .add(mensaje)
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Alerta enviada al Guardián: " + guardianId))
                .addOnFailureListener(e -> Log.e(TAG, "Error al enviar alerta al Guardián", e));

        // También enviar al chat si es posible
        enviarMensajeEnChat(firestore, guardianId, exploradorId, nombre);
    }

    private void enviarMensajeEnChat(FirebaseFirestore firestore, String guardianId, String exploradorId, String nombre) {
        String chatId = guardianId + "_" + exploradorId;
        Map<String, Object> mensajeChat = new HashMap<>();
        mensajeChat.put("remitenteId", exploradorId);
        mensajeChat.put("remitenteNombre", nombre != null ? nombre : "Explorador");
        mensajeChat.put("contenido", "ES IMPORTANTE QUE TE COMUNIQUES CON TU EXPLORADOR");
        mensajeChat.put("tipo", "alerta_timeout_critico");
        mensajeChat.put("timestamp", FieldValue.serverTimestamp());

        firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .add(mensajeChat);
    }
}
