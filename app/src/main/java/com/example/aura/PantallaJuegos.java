package com.example.aura;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import android.util.TypedValue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PantallaJuegos extends BaseActivity {

    private static final String CHANNEL_ID = "juego_notificaciones";
    private static final int NOTIFICATION_ID = 1;
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    private static final long INTERVALO_CONTADOR_MS = 1_000L;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> requestLocationPermissionLauncher;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private String exploradorUid;
    private String codigoExplorador;
    private String nombreExplorador;
    private String guardianVinculadoId;
    private boolean accesoJuegosPermitido;

    private Button btnAccionJuegos;
    private TextView tvContadorListo;
    private CountDownTimer contadorListo;
    private boolean listoConfirmado;

    // Referencias para el simulador
    private ScrollView scrollViewJuegos;
    private LinearLayout layoutSimuladorJuego;
    private View cardJuego1;
    private Button btnSimularGanar;
    private Button btnSimularSalir;

    private static final String PREFS_JUEGO = "JuegoPrefs";
    private static final String KEY_FECHA_GANADO = "fecha_juego_ganado";
    private static final String KEY_LISTO_CONFIRMADO = "listo_confirmado_hoy";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_juegos);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_juegos), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Registrar token FCM cada vez que se entra por seguridad
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (auth.getCurrentUser() != null) {
                firestore.collection("usuarios").document(auth.getCurrentUser().getUid())
                        .update("fcmToken", token);
            }
        });

        configurarSolicitudUbicacion();
        configurarCallbackUbicacion();

        createNotificationChannel();

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        mostrarNotificacionJuego();
                    } else {
                        Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        iniciarPublicacionUbicacion();
                    }
                }
        );

        solicitarPermisoUbicacionSiHaceFalta();
        prepararPerfilExplorador();


        LinearLayout navPerfil = findViewById(R.id.navPerfil);
        navPerfil.setOnClickListener(v -> {
            startActivity(new Intent(PantallaJuegos.this, PantallaPerfil.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navConfiguraciones = findViewById(R.id.navConfiguraciones);
        navConfiguraciones.setOnClickListener(v -> {
            startActivity(new Intent(PantallaJuegos.this, PantallaAjustes.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navChat = findViewById(R.id.navChat);
        navChat.setOnClickListener(v -> {
            startActivity(new Intent(PantallaJuegos.this, PantallaChat.class));
            overridePendingTransition(0, 0);
            finish();
        });

        btnAccionJuegos = findViewById(R.id.btnAccionJuegos);
        tvContadorListo = findViewById(R.id.tvContadorListo);

        scrollViewJuegos = findViewById(R.id.scrollViewJuegos);
        layoutSimuladorJuego = findViewById(R.id.layoutSimuladorJuego);
        cardJuego1 = findViewById(R.id.cardJuego1);
        btnSimularGanar = findViewById(R.id.btnSimularGanar);
        btnSimularSalir = findViewById(R.id.btnSimularSalir);

        btnAccionJuegos.setOnClickListener(v -> confirmarListo());

        cardJuego1.setOnClickListener(v -> {
            scrollViewJuegos.setVisibility(View.GONE);
            layoutSimuladorJuego.setVisibility(View.VISIBLE);
        });

        btnSimularGanar.setOnClickListener(v -> {
            marcarJuegoGanadoHoy();
            habilitarBotonListo();
            scrollViewJuegos.setVisibility(View.VISIBLE);
            layoutSimuladorJuego.setVisibility(View.GONE);
            Toast.makeText(this, "¡Nivel superado! Botón Listo desbloqueado", Toast.LENGTH_SHORT).show();
        });

        btnSimularSalir.setOnClickListener(v -> {
            scrollViewJuegos.setVisibility(View.VISIBLE);
            layoutSimuladorJuego.setVisibility(View.GONE);
        });
    }

    private String obtenerFechaActualStr() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());
    }

    private String obtenerPrefsJuegosNombre() {
        FirebaseUser user = auth.getCurrentUser();
        String uid = user != null ? user.getUid() : exploradorUid;
        return uid == null ? PREFS_JUEGO : PREFS_JUEGO + "_" + uid;
    }

    private SharedPreferences obtenerPrefsJuegos() {
        return getSharedPreferences(obtenerPrefsJuegosNombre(), MODE_PRIVATE);
    }

    private void marcarJuegoGanadoHoy() {
        obtenerPrefsJuegos().edit()
                .putString(KEY_FECHA_GANADO, obtenerFechaActualStr())
                .apply();
    }

    private boolean fueJuegoGanadoHoy() {
        String fechaGanado = obtenerPrefsJuegos()
                .getString(KEY_FECHA_GANADO, "");
        return obtenerFechaActualStr().equals(fechaGanado);
    }

    private void habilitarBotonListo() {
        if (!listoConfirmado) {
            btnAccionJuegos.setEnabled(true);
            btnAccionJuegos.setClickable(true);
            btnAccionJuegos.setAlpha(1.0f);
        }
    }

    private void confirmarListo() {
        if (listoConfirmado) return;

        listoConfirmado = true;
        // Guardar que ya se confirmó hoy
        obtenerPrefsJuegos().edit()
                .putString(KEY_LISTO_CONFIRMADO, obtenerFechaActualStr())
                .apply();

        // IMPORTANTE: NO detenemos el contador, sigue hasta medianoche
        btnAccionJuegos.setEnabled(false);
        btnAccionJuegos.setClickable(false);
        btnAccionJuegos.setAlpha(0.5f);

        if (tvContadorListo != null) {
            tvContadorListo.setText("Ya hiciste la misión del día");
            tvContadorListo.setTextColor(ContextCompat.getColor(this, R.color.color_mision_completada));
            tvContadorListo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        }

        cancelarAlarmaMedianoche();
        enviarMensajeAlGuardian("TODO BIEN 👌🏻", "listo_confirmado");

        verificarYMostrarNotificacion();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Notificaciones de Juego";
            String description = "Notificaciones para el inicio de juegos";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void verificarYMostrarNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                mostrarNotificacionJuego();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            mostrarNotificacionJuego();
        }
    }

    private void mostrarNotificacionJuego() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Confirmación enviada")
                .setContentText("Todo bien 👌🏻")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

    }

    private void iniciarContadorListo() {
        detenerContadorListo();

        // Recuperar estado del día actual
        String fechaConfirmado = obtenerPrefsJuegos()
                .getString(KEY_LISTO_CONFIRMADO, "");
        listoConfirmado = obtenerFechaActualStr().equals(fechaConfirmado);

        // Configurar botón según el estado
        if (btnAccionJuegos != null) {
            if (fueJuegoGanadoHoy() && !listoConfirmado) {
                habilitarBotonListo();
            } else {
                btnAccionJuegos.setEnabled(false);
                btnAccionJuegos.setClickable(false);
                btnAccionJuegos.setAlpha(0.5f);
            }
        }

        // Calcular tiempo hasta la próxima medianoche
        java.util.Calendar c = java.util.Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        long midnight = c.getTimeInMillis();
        long diff = midnight - now;

        contadorListo = new CountDownTimer(diff, INTERVALO_CONTADOR_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvContadorListo != null) {
                    if (listoConfirmado) {
                        tvContadorListo.setText("Ya hiciste la misión del día");
                        tvContadorListo.setTextColor(ContextCompat.getColor(PantallaJuegos.this, R.color.color_mision_completada));
                        tvContadorListo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    } else {
                        long totalSegundos = millisUntilFinished / 1000;
                        long horas = totalSegundos / 3600;
                        long minutos = (totalSegundos % 3600) / 60;
                        long segundos = totalSegundos % 60;

                        tvContadorListo.setText(String.format(Locale.getDefault(),
                                "%02d:%02d:%02d", horas, minutos, segundos));
                        tvContadorListo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);

                        if (horas >= 10) {
                            tvContadorListo.setTextColor(android.graphics.Color.parseColor("#00E676"));
                        } else if (horas >= 5) {
                            tvContadorListo.setTextColor(android.graphics.Color.parseColor("#FF9800"));
                        } else {
                            tvContadorListo.setTextColor(android.graphics.Color.parseColor("#F44336"));
                        }
                    }
                }
            }

            @Override
            public void onFinish() {
                if (tvContadorListo != null) {
                    tvContadorListo.setText("00:00:00");
                    tvContadorListo.setTextColor(android.graphics.Color.parseColor("#F44336"));
                }

                // Lógica principal: Solo enviar alerta si NO ganaron el juego hoy
                if (!fueJuegoGanadoHoy()) {
                    enviarMensajeAlGuardian(
                            "ES IMPORTANTE QUE TE COMUNIQUES CON TU EXPLORADOR",
                            "alerta_timeout_critico"
                    );
                }

                // Limpiar variables para el nuevo día
                obtenerPrefsJuegos().edit()
                        .remove(KEY_FECHA_GANADO)
                        .remove(KEY_LISTO_CONFIRMADO)
                        .apply();
                listoConfirmado = false;

                // Bloquear botón nuevamente
                if (btnAccionJuegos != null) {
                    btnAccionJuegos.setEnabled(false);
                    btnAccionJuegos.setClickable(false);
                    btnAccionJuegos.setAlpha(0.5f);
                }

                // Reiniciar el contador para el siguiente día (24h)
                iniciarContadorListo();
            }
        };

        contadorListo.start();

        // Programar la alarma física por si cierran la app
        if (!listoConfirmado) {
            programarAlarmaMedianoche(diff);
        }
    }

    private void programarAlarmaMedianoche(long diffMs) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerTime = System.currentTimeMillis() + diffMs;

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        }
    }

    private void cancelarAlarmaMedianoche() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void detenerContadorListo() {
        if (contadorListo != null) {
            contadorListo.cancel();
            contadorListo = null;
        }
    }

    private void configurarSolicitudUbicacion() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
                .setMinUpdateIntervalMillis(5_000)
                .build();
    }

    private void configurarCallbackUbicacion() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    publicarUbicacionExplorador(location);
                }
            }
        };
    }

    private void solicitarPermisoUbicacionSiHaceFalta() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarPublicacionUbicacion();
            return;
        }

        requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void iniciarPublicacionUbicacion() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                publicarUbicacionExplorador(location);
            }
        });
    }

    private void detenerPublicacionUbicacion() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void publicarUbicacionExplorador(Location location) {
        if (exploradorUid == null) {
            return;
        }

        Map<String, Object> ubicacion = new HashMap<>();
        ubicacion.put("exploradorId", exploradorUid);
        if (codigoExplorador != null) {
            ubicacion.put("codigo", codigoExplorador);
        }
        ubicacion.put("lat", location.getLatitude());
        ubicacion.put("lng", location.getLongitude());
        ubicacion.put("actualizadoEn", FieldValue.serverTimestamp());

        firestore.collection("ubicaciones_explorador")
                .document(exploradorUid)
                .set(ubicacion);
    }

    private void prepararPerfilExplorador() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Inicia sesión para continuar", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
            finish();
            return;
        }

        accesoJuegosPermitido = false;
        exploradorUid = user.getUid();
        firestore.collection("usuarios")
                .document(exploradorUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot == null || !documentSnapshot.exists()) {
                        android.util.Log.e("PantallaJuegos", "El documento del usuario no existe");
                        Toast.makeText(this, "Error: No se encontró el perfil", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
                        finish();
                        return;
                    }

                    String tipoUsuario = documentSnapshot.getString("tipoUsuario");
                    if (!TIPO_EXPLORADOR.equals(tipoUsuario)) {
                        exploradorUid = null;
                        Toast.makeText(this,
                                "Esta cuenta no es Explorador", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
                        finish();
                        return;
                    }

                    codigoExplorador = documentSnapshot.getString("codigoExplorador");
                    nombreExplorador = documentSnapshot.getString("nombreUsuario");
                    guardianVinculadoId = documentSnapshot.getString("guardianVinculadoId");

                    if (guardianVinculadoId == null || guardianVinculadoId.trim().isEmpty()) {
                        android.util.Log.d("PantallaJuegos", "Redirigiendo a CompartirCodigo (sin vinculo)");
                        Toast.makeText(PantallaJuegos.this, "Primero debes vincularte con un Guardián", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(PantallaJuegos.this, PantallaCompartirCodigo.class));
                        finish();
                        return;
                    }

                    accesoJuegosPermitido = true;
                    iniciarContadorListo();

                    android.util.Log.d("PantallaJuegos", "Perfil cargado: " + nombreExplorador +
                            ", Guardian: " + guardianVinculadoId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PantallaJuegos", "Error leyendo perfil", e);
                    Toast.makeText(this, "No se pudo validar tu acceso", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
                    finish();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!accesoJuegosPermitido) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarPublicacionUbicacion();
        }
    }

    @Override
    protected void onPause() {
        detenerPublicacionUbicacion();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        detenerContadorListo();
        super.onDestroy();
    }

    private void enviarMensajeAlGuardian(@NonNull String contenido, @NonNull String tipo) {
        if (guardianVinculadoId == null || guardianVinculadoId.trim().isEmpty()) {
            android.util.Log.w("PantallaJuegos", "No hay guardian vinculado");
            return;
        }

        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("remitente", nombreExplorador != null ? nombreExplorador : "Explorador");
        mensaje.put("contenido", contenido);
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("tipo", tipo);

        String messageId = firestore.collection("mensajes")
                .document(guardianVinculadoId)
                .collection("historial")
                .document().getId();

        firestore.collection("mensajes")
                .document(guardianVinculadoId)
                .collection("historial")
                .document(messageId)
                .set(mensaje)
                .addOnSuccessListener(aVoid -> android.util.Log.d("PantallaJuegos",
                        "Mensaje guardado exitosamente: " + messageId))
                .addOnFailureListener(e -> {
                    android.util.Log.e("PantallaJuegos", "Error al guardar mensaje", e);
                    Toast.makeText(PantallaJuegos.this,
                            "Error al enviar mensaje: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });

            if (exploradorUid != null) {
                enviarMensajeEnChatVinculado(contenido, tipo);
            }
            }

            private void enviarMensajeEnChatVinculado(@NonNull String contenido, @NonNull String tipo) {
            if (guardianVinculadoId == null || guardianVinculadoId.trim().isEmpty() || exploradorUid == null) {
                return;
            }

            String chatId = construirChatId(guardianVinculadoId, exploradorUid);
            Map<String, Object> mensaje = new HashMap<>();
            mensaje.put("remitenteId", exploradorUid);
            mensaje.put("remitenteNombre", nombreExplorador != null ? nombreExplorador : "Explorador");
            mensaje.put("contenido", contenido);
            mensaje.put("tipo", tipo);
            mensaje.put("timestamp", FieldValue.serverTimestamp());

            Map<String, Object> conversacion = new HashMap<>();
            conversacion.put("guardianId", guardianVinculadoId);
            conversacion.put("exploradorId", exploradorUid);
            conversacion.put("actualizadoEn", FieldValue.serverTimestamp());

            firestore.collection("chats")
                .document(chatId)
                .set(conversacion, SetOptions.merge());

            String messageId = firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .document().getId();

            firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .document(messageId)
                .set(mensaje)
                .addOnSuccessListener(aVoid -> android.util.Log.d("PantallaJuegos",
                    "Mensaje de chat guardado: " + messageId))
                .addOnFailureListener(e -> android.util.Log.e("PantallaJuegos",
                    "Error al guardar mensaje de chat", e));
            }

            private String construirChatId(@NonNull String guardianId, @NonNull String exploradorId) {
            return guardianId + "_" + exploradorId;
    }
}
