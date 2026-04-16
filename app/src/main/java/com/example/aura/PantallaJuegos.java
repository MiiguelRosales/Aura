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
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
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

import com.google.firebase.Timestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class PantallaJuegos extends BaseActivity {

    private static final String CHANNEL_ID = "juego_notificaciones";
    private static final int NOTIFICATION_ID = 1;
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    private static final String PREFS_AURA = "AuraPrefs";
    private static final String KEY_EXPLORADOR_VINCULADO_ID = "exploradorVinculadoId";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) { //llamado cuando se crea por primera vez la actividad
        super.onCreate(savedInstanceState); //llamada a su implementacion
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_juegos); //indica a android que debe establecer

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
        prepararPerfilExplorador();

        //CREAR CANAL DE NOTIFICACIONES
        createNotificationChannel();

        //REGISTRAR LAUNCHER PARA SOLICITAR PERMISOS
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

        //BOTON DE FLECHA REGRESAR
        final ImageButton imageButtonRegresar = findViewById(R.id.imageButtonRegresar);

        //EVENTO PARA REGRESAR A PANTALLA INICIO
        imageButtonRegresar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PantallaJuegos.this, pantalla_inicio.class);
                startActivity(intent);
            }
        });

        //BOTON DE CONFIGURACION
        final Button btnConfiguracionJuegos = (Button) findViewById(R.id.btnConfiguracionJuegos);

        //EVENTO PARA IR A COMPARTIR CODIGO
        btnConfiguracionJuegos.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PantallaJuegos.this, PantallaCompartirCodigo.class);
                startActivity(intent);
            }
        });

        //BOTON DE JUGAR
        final Button btnAccionJuegos = (Button) findViewById(R.id.btnAccionJuegos);

        //EVENTO PARA COMENZAR A JUGAR
        btnAccionJuegos.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                verificarYMostrarNotificacion();
            }
        });
    }

    private void createNotificationChannel() {
        // Crear el canal de notificaciones solo para Android 8.0+
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
            // Android 13+ requiere permiso en runtime
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED) {
                mostrarNotificacionJuego();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Para versiones anteriores no se requiere permiso en runtime
            mostrarNotificacionJuego();
        }
    }

    private void mostrarNotificacionJuego() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("¡Has empezado a jugar!")
                .setContentText("Tienes tiempo limitado para terminar")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Toast.makeText(this, "¡Juego Completado!", Toast.LENGTH_SHORT).show();
        }

        // Enviar mensaje al Guardián vinculado
        enviarMensajeAlGuardian();
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
            Toast.makeText(this, "Inicia sesión para publicar ubicación", Toast.LENGTH_SHORT).show();
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

    private void enviarMensajeAlGuardian() {
        // Si no hay guardián vinculado, no enviar mensaje
        if (guardianVinculadoId == null || guardianVinculadoId.trim().isEmpty()) {
            android.util.Log.w("PantallaJuegos", "No hay guardián vinculado");
            return;
        }

        android.util.Log.d("PantallaJuegos", "Enviando mensaje al guardián: " + guardianVinculadoId);

        // Obtener el nombre del guardián para personalizar el mensaje
        firestore.collection("usuarios")
                .document(guardianVinculadoId)
                .get()
                .addOnSuccessListener(guardianDoc -> {
                    String nombreGuardian = guardianDoc.getString("nombreUsuario");
                    if (nombreGuardian == null) {
                        nombreGuardian = "Guardián";
                    }

                    // Crear mensaje
                    Map<String, Object> mensaje = new HashMap<>();
                    mensaje.put("remitente", nombreExplorador != null ? nombreExplorador : "Explorador");
                    mensaje.put("contenido", "Hola " + nombreGuardian);
                    mensaje.put("timestamp", FieldValue.serverTimestamp());
                    mensaje.put("tipo", "juego_iniciado");

                    // Guardar en historial del guardián
                    String messageId = firestore.collection("mensajes")
                            .document(guardianVinculadoId)
                            .collection("historial")
                            .document().getId();

                    firestore.collection("mensajes")
                            .document(guardianVinculadoId)
                            .collection("historial")
                            .document(messageId)
                            .set(mensaje)
                            .addOnSuccessListener(aVoid -> {
                                android.util.Log.d("PantallaJuegos", 
                                        "Mensaje guardado exitosamente: " + messageId);
                            })
                            .addOnFailureListener(e -> {
                                android.util.Log.e("PantallaJuegos", 
                                        "Error al guardar mensaje", e);
                                Toast.makeText(PantallaJuegos.this, 
                                        "Error al enviar mensaje: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PantallaJuegos", 
                            "Error leyendo perfil del guardián", e);
                    Toast.makeText(PantallaJuegos.this, 
                            "No se pudo leer perfil del Guardián", 
                            Toast.LENGTH_SHORT).show();
                });
    }
}
