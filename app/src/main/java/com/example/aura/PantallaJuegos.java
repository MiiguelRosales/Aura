package com.example.aura;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

public class PantallaJuegos extends AppCompatActivity {

    private static final String CHANNEL_ID = "juego_notificaciones";
    private static final int NOTIFICATION_ID = 1;
    private ActivityResultLauncher<String> requestPermissionLauncher;

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
    }
}
