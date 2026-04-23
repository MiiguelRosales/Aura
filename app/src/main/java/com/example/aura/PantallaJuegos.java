package com.example.aura;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PantallaJuegos extends BaseActivity {

    private static final String CHANNEL_ID = "juego_notificaciones";
    private static final int NOTIFICATION_ID = 1;
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    // Objetivo real: 24 horas. Durante pruebas usamos 2 minutos.
    private static final long TIEMPO_LIMITE_CONFIRMACION_MS = 2 * 60 * 1000L;
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

    private Button btnAccionJuegos;
    private TextView tvContadorListo;
    private CountDownTimer contadorListo;
    private boolean listoConfirmado;
    private boolean alertaTimeoutEnviada;

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

        btnAccionJuegos = findViewById(R.id.btnAccionJuegos);
        tvContadorListo = findViewById(R.id.tvContadorListo);

        btnAccionJuegos.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmarListo();
            }
        });
    }

    private void confirmarListo() {
        if (listoConfirmado) {
            return;
        }

        listoConfirmado = true;
        detenerContadorListo();

        btnAccionJuegos.setEnabled(false);
        btnAccionJuegos.setClickable(false);
        btnAccionJuegos.setAlpha(0.5f);
        tvContadorListo.setText("Confirmacion enviada al Guardian");

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
                .setContentTitle("Confirmacion enviada")
                .setContentText("Le avisamos a tu Guardian que ya terminaste")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        enviarMensajeAlGuardian(
                "Confirmo que ya termine y estoy listo.",
                "listo_confirmado"
        );
    }

    private void iniciarContadorListo() {
        detenerContadorListo();
        listoConfirmado = false;
        alertaTimeoutEnviada = false;

        if (btnAccionJuegos != null) {
            btnAccionJuegos.setEnabled(true);
            btnAccionJuegos.setClickable(true);
            btnAccionJuegos.setAlpha(1f);
        }

        if (tvContadorListo != null) {
            tvContadorListo.setText("Tiempo para confirmar: 02:00");
        }

        contadorListo = new CountDownTimer(TIEMPO_LIMITE_CONFIRMACION_MS, INTERVALO_CONTADOR_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSegundos = millisUntilFinished / 1000;
                long minutos = totalSegundos / 60;
                long segundos = totalSegundos % 60;
                if (tvContadorListo != null) {
                    tvContadorListo.setText(String.format(Locale.getDefault(),
                            "Tiempo para confirmar: %02d:%02d", minutos, segundos));
                }
            }

            @Override
            public void onFinish() {
                if (listoConfirmado || alertaTimeoutEnviada) {
                    return;
                }

                alertaTimeoutEnviada = true;

                if (tvContadorListo != null) {
                    tvContadorListo.setText("Tiempo agotado: contacta a tu Guardian");
                }

                if (btnAccionJuegos != null) {
                    btnAccionJuegos.setEnabled(false);
                    btnAccionJuegos.setClickable(false);
                    btnAccionJuegos.setAlpha(0.5f);
                }

                enviarMensajeAlGuardian(
                        "No se recibio confirmacion de Listo en 2 minutos. Ponte en contacto con tu Explorador.",
                        "alerta_timeout_liberacion"
                );
            }
        };

        contadorListo.start();
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
            Toast.makeText(this, "Inicia sesion para publicar ubicacion", Toast.LENGTH_SHORT).show();
            return;
        }

        exploradorUid = user.getUid();
        firestore.collection("usuarios")
                .document(exploradorUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String tipoUsuario = documentSnapshot.getString("tipoUsuario");
                    if (!TIPO_EXPLORADOR.equals(tipoUsuario)) {
                        exploradorUid = null;
                        Toast.makeText(this,
                                "Esta cuenta no es Explorador", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    codigoExplorador = documentSnapshot.getString("codigoExplorador");
                    nombreExplorador = documentSnapshot.getString("nombreUsuario");
                    guardianVinculadoId = documentSnapshot.getString("guardianVinculadoId");

                    iniciarContadorListo();

                    android.util.Log.d("PantallaJuegos", "Explorador: " + nombreExplorador +
                            ", Guardian vinculado: " + guardianVinculadoId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PantallaJuegos", "Error leyendo perfil", e);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
    }
}
