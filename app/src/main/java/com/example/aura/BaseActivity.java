package com.example.aura;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.Timestamp;
import java.util.List;

import java.util.Date;

public class BaseActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_DARK_MODE = "darkMode";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String KEY_GUARDIAN_VINCULADO_ID = "guardianVinculadoId";
    private static final String KEY_EXPLORADOR_VINCULADO_ID = "exploradorVinculadoId";
    private static final String KEY_ULTIMO_MENSAJE_LEIDO_PREFIX = "ultimoMensajeLeido_";
    private static final String KEY_ULTIMO_MENSAJE_ALERTADO_PREFIX = "ultimoMensajeAlertado_";
    private static final String CHANNEL_ID = "aura_notifications";

    private ListenerRegistration listenerChatNotificaciones;
    private String chatNotificacionesUid;
    private String pendingEliminarCuentaPassword;

    @Override
    public void setContentView(int layoutResID) {
        // Aplicar tema guardado antes de inflar el layout
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, true);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.setContentView(layoutResID);
    }

    /**
     * Muestra un mensaje al usuario.
     * - Mensajes cortos (≤60 chars): Toast que se desvanece solo.
     * - Mensajes largos (>60 chars): AlertDialog que el usuario cierra cuando termina de leer.
     */
    protected void showMessage(String mensaje) {
        if (mensaje == null) return;
        if (mensaje.length() <= 60) {
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(mensaje)
                    .setPositiveButton("Entendido", null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        iniciarServicioNotificacionesChat();
        if (!(this instanceof PantallaChat)) {
            iniciarObservadorNotificacionesChat();
            actualizarBadgeChat();
        } else {
            actualizarBadgeChat(0);
        }
    }

    @Override
    protected void onPause() {
        detenerObservadorNotificacionesChat();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        detenerObservadorNotificacionesChat();
        super.onDestroy();
    }

    private void iniciarServicioNotificacionesChat() {
        Intent intent = new Intent(this, ChatNotificationForegroundService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    protected void marcarChatComoLeido(@NonNull String chatId, long ultimoTimestampMillis) {
        if (ultimoTimestampMillis <= 0) {
            return;
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(KEY_ULTIMO_MENSAJE_LEIDO_PREFIX + chatId, ultimoTimestampMillis)
                .apply();
    }

    private void iniciarObservadorNotificacionesChat() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
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

                    String chatId = construirChatId(guardianId, exploradorId);
                    escucharUltimoMensaje(chatId, uidActual);
                    actualizarBadgeChat(chatId, uidActual);
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

                    String titulo = defaultString(ultimoMensaje.getString("remitenteNombre"), "Aura");
                    String cuerpo = defaultString(ultimoMensaje.getString("contenido"), "Nuevo mensaje");
                        mostrarNotificacionChat(chatId, titulo, cuerpo);
                    prefs.edit()
                            .putLong(KEY_ULTIMO_MENSAJE_ALERTADO_PREFIX + chatId, ultimoTimestampMillis)
                            .apply();
                    actualizarBadgeChat(chatId, uidActual);
                });
    }

    private long obtenerTimestampMillis(@NonNull DocumentSnapshot snapshot) {
        if (snapshot.getTimestamp("timestamp") != null) {
            return snapshot.getTimestamp("timestamp").toDate().getTime();
        }
        if (snapshot.getDate("timestamp") != null) {
            return snapshot.getDate("timestamp").getTime();
        }
        return 0L;
    }

    protected void cancelarNotificacionChat(@NonNull String chatId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(obtenerIdNotificacionChat(chatId));
        }
    }

    private void mostrarNotificacionChat(@NonNull String chatId, @NonNull String titulo, @NonNull String mensaje) {
        if (!tienePermisoNotificaciones()) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Notificaciones de Aura",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, PantallaChat.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
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

    private int obtenerIdNotificacionChat(@NonNull String chatId) {
        return Math.abs(chatId.hashCode());
    }

    private boolean tienePermisoNotificaciones() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void detenerObservadorNotificacionesChat() {
        if (listenerChatNotificaciones != null) {
            listenerChatNotificaciones.remove();
            listenerChatNotificaciones = null;
        }
    }

    protected void actualizarBadgeChat() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            actualizarBadgeChat(0);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        actualizarBadgeChat(0);
                        return;
                    }

                    String tipoUsuario = defaultString(
                            documentSnapshot.getString(KEY_TIPO_USUARIO),
                            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_TIPO_USUARIO, "GUARDIAN")
                    );

                    String guardianId;
                    String exploradorId;

                    if ("GUARDIAN".equals(tipoUsuario)) {
                        guardianId = auth.getCurrentUser().getUid();
                        exploradorId = documentSnapshot.getString(KEY_EXPLORADOR_VINCULADO_ID);
                    } else if ("EXPLORADOR".equals(tipoUsuario)) {
                        guardianId = documentSnapshot.getString(KEY_GUARDIAN_VINCULADO_ID);
                        exploradorId = auth.getCurrentUser().getUid();
                    } else {
                        actualizarBadgeChat(0);
                        return;
                    }

                    if (TextUtils.isEmpty(guardianId) || TextUtils.isEmpty(exploradorId)) {
                        actualizarBadgeChat(0);
                        return;
                    }

                    String chatId = construirChatId(guardianId, exploradorId);
                    actualizarBadgeChat(chatId, auth.getCurrentUser().getUid());
                })
                .addOnFailureListener(e -> actualizarBadgeChat(0));
    }

    private void actualizarBadgeChat(@NonNull String chatId, @NonNull String uidActual) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long ultimoLeidoMillis = prefs.getLong(KEY_ULTIMO_MENSAJE_LEIDO_PREFIX + chatId, 0L);

        CollectionReference mensajesRef = FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("mensajes");

        mensajesRef
                .whereGreaterThan("timestamp", new Date(ultimoLeidoMillis))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int noLeidos = 0;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String remitenteId = doc.getString("remitenteId");
                        if (!uidActual.equals(remitenteId)) {
                            noLeidos++;
                        }
                    }
                    actualizarBadgeChat(noLeidos);
                })
                .addOnFailureListener(e -> actualizarBadgeChat(0));
    }

    protected void actualizarBadgeChat(int cantidad) {
        android.widget.TextView badge = findViewById(R.id.tvChatBadge);
        if (badge == null) {
            return;
        }

        if (cantidad <= 0) {
            badge.setVisibility(android.view.View.GONE);
            badge.setText("");
            return;
        }

        badge.setVisibility(android.view.View.VISIBLE);
        badge.setText(cantidad > 99 ? "99+" : String.valueOf(cantidad));
    }

    private String construirChatId(@NonNull String guardianId, @NonNull String exploradorId) {
        return guardianId + "_" + exploradorId;
    }

    private String defaultString(String valor, String porDefecto) {
        return valor != null && !valor.trim().isEmpty() ? valor : porDefecto;
    }

    protected void mostrarDialogoEliminarCuenta() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return;
        }

        firestore.collection("usuarios").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        return;
                    }

                    String nombreUsuario = documentSnapshot.getString("nombreUsuario");
                    String correo = user.getEmail();
                    String tipoUsuario = documentSnapshot.getString("tipoUsuario");

                    android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
                    android.view.View dialogView = inflater.inflate(R.layout.dialog_eliminar_cuenta, null);

                    android.widget.TextView tvNombreUsuario = dialogView.findViewById(R.id.tvNombreUsuario);
                    android.widget.TextView tvCorreo = dialogView.findViewById(R.id.tvCorreo);
                    android.widget.TextView tvContrasena = dialogView.findViewById(R.id.tvContrasena);

                    android.widget.CheckBox cbNoUso = dialogView.findViewById(R.id.cbNoUso);
                    android.widget.CheckBox cbNoVinculo = dialogView.findViewById(R.id.cbNoVinculo);
                    android.widget.CheckBox cbOtro = dialogView.findViewById(R.id.cbOtro);
                    com.google.android.material.textfield.TextInputLayout tilMotivoPrincipal = dialogView.findViewById(R.id.tilMotivoPrincipal);
                    com.google.android.material.textfield.TextInputEditText etMotivoPrincipal = dialogView.findViewById(R.id.etMotivoPrincipal);

                    android.widget.Button btnCancelar = dialogView.findViewById(R.id.btnCancelarEliminar);
                    android.widget.Button btnEnviar = dialogView.findViewById(R.id.btnEnviarSolicitud);

                    tvNombreUsuario.setText(nombreUsuario != null ? nombreUsuario : "Usuario");
                    tvCorreo.setText(correo != null ? correo : "");
                    tvContrasena.setText("••••••••");
                    com.google.android.material.textfield.TextInputEditText etConfirmarPassword = dialogView.findViewById(R.id.etConfirmarPassword);

                    com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
                    builder.setView(dialogView);

                    androidx.appcompat.app.AlertDialog dialog = builder.create();

                    cbOtro.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        tilMotivoPrincipal.setVisibility(isChecked ? android.view.View.VISIBLE : android.view.View.GONE);
                        if (!isChecked) {
                            etMotivoPrincipal.setText("");
                        }
                    });

                    btnCancelar.setOnClickListener(v -> dialog.dismiss());
                    btnEnviar.setOnClickListener(v -> {
                        boolean noUso = cbNoUso.isChecked();
                        boolean noVinculo = cbNoVinculo.isChecked();
                        boolean otro = cbOtro.isChecked();

                        if (!noUso && !noVinculo && !otro) {
                            showMessage("Por favor selecciona un motivo");
                            return;
                        }

                        if (otro && etMotivoPrincipal.getText().toString().trim().isEmpty()) {
                            showMessage("Por favor describe tu motivo");
                            return;
                        }

                        StringBuilder motivoBuilder = new StringBuilder();
                        if (noUso) motivoBuilder.append("No uso la aplicación; ");
                        if (noVinculo) motivoBuilder.append("No vinculé a mi guardián; ");
                        if (otro) {
                            motivoBuilder.append("Otros: ").append(etMotivoPrincipal.getText().toString());
                        }

                        String motivoFinal = motivoBuilder.toString();
                        String password = etConfirmarPassword.getText() != null
                                ? etConfirmarPassword.getText().toString().trim()
                                : "";
                        if (password.isEmpty()) {
                            showMessage("Ingresa tu contraseña para confirmar la eliminación");
                            return;
                        }
                        guardarSolicitudEliminacion(user.getUid(), nombreUsuario, correo, tipoUsuario, motivoFinal, dialog, password);
                    });

                    dialog.show();
                })
                .addOnFailureListener(e -> showMessage("Error al obtener datos del usuario: " + e.getMessage()));
    }

    private void guardarSolicitudEliminacion(String usuarioId,
                                             String nombreUsuario,
                                             String correo,
                                             String tipoUsuario,
                                             String motivo,
                                             androidx.appcompat.app.AlertDialog dialog,
                                             String password) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        dialog.dismiss();
        firestore.collection("usuarios").document(usuarioId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String guardianVinculadoId = documentSnapshot.getString("guardianVinculadoId");
                    String exploradorVinculadoId = documentSnapshot.getString("exploradorVinculadoId");

                    if ((guardianVinculadoId != null && !guardianVinculadoId.trim().isEmpty()) ||
                            (exploradorVinculadoId != null && !exploradorVinculadoId.trim().isEmpty())) {
                        showMessage("No puedes eliminar tu cuenta mientras tengas un vínculo activo. Primero desvincula la cuenta.");
                        return;
                    }

                    pendingEliminarCuentaPassword = password;
                    firestore.collection("solicitudes_eliminacion_cuenta")
                            .document(usuarioId)
                            .delete()
                            .addOnCompleteListener(ignore -> eliminarCuentaCompletamente(usuarioId, auth, firestore));
                })
                .addOnFailureListener(e -> showMessage("Error al verificar vínculo antes de eliminar: " + e.getMessage()));
    }

    private void eliminarCuentaCompletamente(String usuarioId, FirebaseAuth auth, FirebaseFirestore firestore) {
        List<String> chatIds = new java.util.ArrayList<>();
        java.util.Set<String> vinculoIds = new java.util.HashSet<>();
        java.util.concurrent.atomic.AtomicInteger queryCounter = new java.util.concurrent.atomic.AtomicInteger(2);

        firestore.collection("usuarios").document(usuarioId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String guardianVinculadoId = documentSnapshot.getString("guardianVinculadoId");
                    String exploradorVinculadoId = documentSnapshot.getString("exploradorVinculadoId");
                    if (guardianVinculadoId != null && !guardianVinculadoId.trim().isEmpty()) {
                        limpiarDatosVinculado(guardianVinculadoId, true, firestore);
                        eliminarHistorialVinculado(guardianVinculadoId, usuarioId, firestore);
                    }
                    if (exploradorVinculadoId != null && !exploradorVinculadoId.trim().isEmpty()) {
                        limpiarDatosVinculado(exploradorVinculadoId, false, firestore);
                    }
                })
                .addOnFailureListener(e -> showMessage("Error al leer usuario para limpieza de vínculo: " + e.getMessage()));

        firestore.collection("vinculos")
                .whereEqualTo("guardianId", usuarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    procesarVinculos(querySnapshot, usuarioId, chatIds, vinculoIds);
                    if (queryCounter.decrementAndGet() == 0) {
                        eliminarVinculosYChats(usuarioId, chatIds, vinculoIds, firestore, auth);
                    }
                })
                .addOnFailureListener(e -> {
                    showMessage("Error al leer vinculos: " + e.getMessage());
                    if (queryCounter.decrementAndGet() == 0) {
                        eliminarVinculosYChats(usuarioId, chatIds, vinculoIds, firestore, auth);
                    }
                });

        firestore.collection("vinculos")
                .whereEqualTo("exploradorId", usuarioId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    procesarVinculos(querySnapshot, usuarioId, chatIds, vinculoIds);
                    if (queryCounter.decrementAndGet() == 0) {
                        eliminarVinculosYChats(usuarioId, chatIds, vinculoIds, firestore, auth);
                    }
                })
                .addOnFailureListener(e -> {
                    showMessage("Error al leer vinculos: " + e.getMessage());
                    if (queryCounter.decrementAndGet() == 0) {
                        eliminarVinculosYChats(usuarioId, chatIds, vinculoIds, firestore, auth);
                    }
                });
    }

    private void procesarVinculos(com.google.firebase.firestore.QuerySnapshot querySnapshot,
                                  String usuarioId,
                                  List<String> chatIds,
                                  java.util.Set<String> vinculoIds) {
        for (com.google.firebase.firestore.QueryDocumentSnapshot documento : querySnapshot) {
            String guardianId = documento.getString("guardianId");
            String exploradorId = documento.getString("exploradorId");
            if (guardianId != null && exploradorId != null) {
                String chatId = guardianId + "_" + exploradorId;
                if (!chatIds.contains(chatId)) {
                    chatIds.add(chatId);
                }
            }
            if (usuarioId.equals(guardianId) && exploradorId != null && !exploradorId.trim().isEmpty()) {
                limpiarDatosVinculado(exploradorId, false, FirebaseFirestore.getInstance());
            } else if (usuarioId.equals(exploradorId) && guardianId != null && !guardianId.trim().isEmpty()) {
                limpiarDatosVinculado(guardianId, true, FirebaseFirestore.getInstance());
            }
            vinculoIds.add(documento.getId());
        }
    }

    private void limpiarDatosVinculado(String usuarioId, boolean esGuardian, FirebaseFirestore firestore) {
        if (usuarioId == null || usuarioId.trim().isEmpty()) {
            return;
        }

        java.util.Map<String, Object> actualizaciones = new java.util.HashMap<>();
        if (esGuardian) {
            actualizaciones.put("exploradorVinculadoId", com.google.firebase.firestore.FieldValue.delete());
            actualizaciones.put("codigoVinculado", com.google.firebase.firestore.FieldValue.delete());
            actualizaciones.put("vinculadoEn", com.google.firebase.firestore.FieldValue.delete());
        } else {
            actualizaciones.put("guardianVinculadoId", com.google.firebase.firestore.FieldValue.delete());
            actualizaciones.put("vinculadoEn", com.google.firebase.firestore.FieldValue.delete());
        }

        firestore.collection("usuarios").document(usuarioId)
                .update(actualizaciones)
                .addOnSuccessListener(unused -> {
                    // Datos limpiados.
                })
                .addOnFailureListener(e -> showMessage("Error al limpiar vínculo del usuario vinculado: " + e.getMessage()));
    }

    private void eliminarHistorialVinculado(String usuarioId, String remitenteId, FirebaseFirestore firestore) {
        if (usuarioId == null || usuarioId.trim().isEmpty() || remitenteId == null || remitenteId.trim().isEmpty()) {
            return;
        }

        firestore.collection("mensajes")
                .document(usuarioId)
                .collection("historial")
                .whereEqualTo("remitenteId", remitenteId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (com.google.firebase.firestore.QueryDocumentSnapshot mensajeDoc : querySnapshot) {
                        mensajeDoc.getReference().delete();
                    }
                })
                .addOnFailureListener(e -> showMessage("Error al limpiar historial del vinculado: " + e.getMessage()));
    }

    private void eliminarVinculosYChats(String usuarioId,
                                        List<String> chatIds,
                                        java.util.Set<String> vinculoIds,
                                        FirebaseFirestore firestore,
                                        FirebaseAuth auth) {
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(vinculoIds.size() + chatIds.size());

        if (pending.get() == 0) {
            eliminarMensajesGuardian(usuarioId, firestore, auth);
            return;
        }

        if (!vinculoIds.isEmpty()) {
            for (String vinculoId : vinculoIds) {
                firestore.collection("vinculos")
                        .document(vinculoId)
                        .delete()
                        .addOnCompleteListener(task -> {
                            if (pending.decrementAndGet() == 0) {
                                eliminarMensajesGuardian(usuarioId, firestore, auth);
                            }
                        });
            }
        }

        if (!chatIds.isEmpty()) {
            for (String chatId : chatIds) {
                borrarChatCompleto(chatId, () -> {
                    if (pending.decrementAndGet() == 0) {
                        eliminarMensajesGuardian(usuarioId, firestore, auth);
                    }
                }, firestore);
            }
        }
    }

    private void borrarChatCompleto(String chatId, Runnable onComplete, FirebaseFirestore firestore) {
        firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.concurrent.atomic.AtomicInteger deleteCounter = new java.util.concurrent.atomic.AtomicInteger(querySnapshot.size());
                    if (deleteCounter.get() == 0) {
                        firestore.collection("chats").document(chatId).delete().addOnCompleteListener(ignore -> onComplete.run());
                        return;
                    }
                    for (com.google.firebase.firestore.QueryDocumentSnapshot mensajeDoc : querySnapshot) {
                        mensajeDoc.getReference().delete().addOnCompleteListener(task -> {
                            if (deleteCounter.decrementAndGet() == 0) {
                                firestore.collection("chats").document(chatId).delete().addOnCompleteListener(ignore -> onComplete.run());
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    showMessage("Error al borrar chat " + chatId + ": " + e.getMessage());
                    onComplete.run();
                });
    }

    private void eliminarMensajesGuardian(String usuarioId, FirebaseFirestore firestore, FirebaseAuth auth) {
        firestore.collection("mensajes")
                .document(usuarioId)
                .collection("historial")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.concurrent.atomic.AtomicInteger deleteCounter = new java.util.concurrent.atomic.AtomicInteger(querySnapshot.size());
                    if (deleteCounter.get() == 0) {
                        firestore.collection("mensajes").document(usuarioId).delete().addOnCompleteListener(ignore -> eliminarUsuarioFirestore(usuarioId, firestore, auth));
                        return;
                    }
                    for (com.google.firebase.firestore.QueryDocumentSnapshot mensajeDoc : querySnapshot) {
                        mensajeDoc.getReference().delete().addOnCompleteListener(task -> {
                            if (deleteCounter.decrementAndGet() == 0) {
                                firestore.collection("mensajes").document(usuarioId).delete().addOnCompleteListener(ignore -> eliminarUsuarioFirestore(usuarioId, firestore, auth));
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    showMessage("Error al borrar historial de mensajes: " + e.getMessage());
                    eliminarUsuarioFirestore(usuarioId, firestore, auth);
                });
    }

    private void eliminarUsuarioFirestore(String usuarioId, FirebaseFirestore firestore, FirebaseAuth auth) {
        firestore.collection("usuarios")
                .document(usuarioId)
                .delete()
                .addOnSuccessListener(aVoid -> eliminarAuthUsuario(usuarioId, auth, pendingEliminarCuentaPassword))
                .addOnFailureListener(e -> {
                    showMessage("Error al eliminar usuario: " + e.getMessage());
                    eliminarAuthUsuario(usuarioId, auth, pendingEliminarCuentaPassword);
                });
    }

    private void eliminarAuthUsuario(String usuarioId, FirebaseAuth auth, String password) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && usuarioId.equals(currentUser.getUid())) {
            String email = currentUser.getEmail();
            if (email != null && !password.isEmpty()) {
                AuthCredential credential = EmailAuthProvider.getCredential(email, password);
                currentUser.reauthenticate(credential)
                        .addOnSuccessListener(ignore -> currentUser.delete()
                                .addOnSuccessListener(aVoid -> mostrarMensajeConfirmacion())
                                .addOnFailureListener(e -> {
                                    showMessage("No se pudo eliminar la cuenta: " + e.getMessage());
                                    cerrarSesionTrasEliminacion();
                                }))
                        .addOnFailureListener(e -> {
                            showMessage("Reautenticación fallida: " + e.getMessage());
                            cerrarSesionTrasEliminacion();
                        });
            } else {
                currentUser.delete()
                        .addOnSuccessListener(aVoid -> mostrarMensajeConfirmacion())
                        .addOnFailureListener(e -> {
                            showMessage("Los datos se eliminaron, pero la cuenta de autenticación requiere reautenticación: " + e.getMessage());
                            cerrarSesionTrasEliminacion();
                        });
            }
        } else {
            cerrarSesionTrasEliminacion();
        }
    }

    private void cerrarSesionTrasEliminacion() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean("guardarLogin", false)
                .apply();
        stopService(new Intent(this, ChatNotificationForegroundService.class));
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, pantalla_inicio.class));
        finishAffinity();
    }

    private void mostrarMensajeConfirmacion() {
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(24, 24, 24, 24);

        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        card.setCardBackgroundColor(getResources().getColor(R.color.login_card_bg));
        card.setCardElevation(8);
        card.setRadius(16);

        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(16, 16, 16, 16);

        android.widget.TextView tvTitulo = new android.widget.TextView(this);
        tvTitulo.setText("Eliminación completa");
        tvTitulo.setTextSize(18);
        tvTitulo.setTextColor(getResources().getColor(R.color.aura_text_primary));
        tvTitulo.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        tvTitulo.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.TextView tvMensaje = new android.widget.TextView(this);
        tvMensaje.setText("Tu cuenta y todos los datos vinculados se eliminaron de inmediato.\n\n" +
                "✓ Chats eliminados\n" +
                "✓ Vinculaciones eliminadas\n" +
                "✓ Usuario eliminado\n" +
                "✓ Para volver a usar Aura deberás registrarte nuevamente\n\n" +
                "Se cerrará la sesión ahora.");
        tvMensaje.setTextSize(13);
        tvMensaje.setTextColor(getResources().getColor(R.color.aura_text_primary));
        tvMensaje.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL));
        tvMensaje.setLineSpacing(6f, 1.0f);
        android.widget.LinearLayout.LayoutParams msgParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.setMargins(0, 16, 0, 0);
        tvMensaje.setLayoutParams(msgParams);

        content.addView(tvTitulo);
        content.addView(tvMensaje);
        card.addView(content);
        container.addView(card);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(container)
                .setPositiveButton("Entendido", (dialog, which) -> {
                    dialog.dismiss();
                    cerrarSesionTrasEliminacion();
                })
                .setCancelable(false)
                .show();
    }
}
