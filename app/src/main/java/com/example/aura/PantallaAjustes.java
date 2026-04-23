package com.example.aura;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class PantallaAjustes extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_DARK_MODE = "darkMode";
    private boolean isDarkMode;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aplicar tema guardado ANTES de setContentView
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true);
        
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_ajustes);

        auth = FirebaseAuth.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_ajustes), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // TOGGLE DE TEMA (sol / luna)
        final FrameLayout layoutToggle = findViewById(R.id.layoutThemeToggle);
        final ImageView ivThumb = findViewById(R.id.ivThumbToggle);
        actualizarToggleUI(layoutToggle, ivThumb, isDarkMode);

        layoutToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isDarkMode = !isDarkMode;
                // Guardar preferencia
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, isDarkMode).apply();
                // Aplicar tema globalmente y recrear
                AppCompatDelegate.setDefaultNightMode(
                        isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                recreate();
            }
        });

        // Botón Cerrar Sesión
        Button btnCerrarSesion = findViewById(R.id.btnCerrarSesion);
        btnCerrarSesion.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean("guardarLogin", false)
                    .apply();
            auth.signOut();
            startActivity(new Intent(PantallaAjustes.this, pantalla_inicio.class));
            finishAffinity(); // Limpia el historial para que no pueda volver atrás
        });

        // ── Navegación Inferior ───────────────────────────────────────
        String tipoUsuario = prefs.getString("tipoUsuario", "GUARDIAN");

        // Actualizar icono y texto del primer tab según el tipo de usuario
        TextView tvNavIcono = findViewById(R.id.tvNavPrincipalIcono);
        TextView tvNavTexto = findViewById(R.id.tvNavPrincipalTexto);
        if ("EXPLORADOR".equals(tipoUsuario)) {
            tvNavIcono.setText("🎮");
            tvNavTexto.setText("Explorador");
        }

        // Botón Vincular: solo visible para Guardian
        Button btnVincular = findViewById(R.id.btnVincular);
        if ("GUARDIAN".equals(tipoUsuario)) {
            btnVincular.setVisibility(android.view.View.VISIBLE);
            btnVincular.setOnClickListener(v -> {
                startActivity(new Intent(PantallaAjustes.this, PantallaVincular.class));
            });
        }

        // Botón Mi Token: solo visible para Explorador
        Button btnMiToken = findViewById(R.id.btnMiToken);
        if ("EXPLORADOR".equals(tipoUsuario)) {
            btnMiToken.setVisibility(android.view.View.VISIBLE);
            btnMiToken.setOnClickListener(v -> {
                startActivity(new Intent(PantallaAjustes.this, PantallaCompartirCodigo.class));
            });
        }

        LinearLayout navPaginaGuardian = findViewById(R.id.navPaginaGuardian);
        navPaginaGuardian.setOnClickListener(v -> {
            Intent intent = "EXPLORADOR".equals(tipoUsuario)
                    ? new Intent(PantallaAjustes.this, PantallaJuegos.class)
                    : new Intent(PantallaAjustes.this, PantallaGuardian.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navPerfil = findViewById(R.id.navPerfil);
        navPerfil.setOnClickListener(v -> {
            startActivity(new Intent(PantallaAjustes.this, PantallaPerfil.class));
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void actualizarToggleUI(FrameLayout track, ImageView thumb, boolean dark) {
        float density = getResources().getDisplayMetrics().density;
        if (dark) {
            track.setBackground(getDrawable(R.drawable.toggle_track_night));
            thumb.setImageResource(R.drawable.ic_moon);
            // Thumb a la derecha: 64dp track - 26dp thumb - 4dp margin = 34dp
            ObjectAnimator.ofFloat(thumb, "translationX", 34 * density).setDuration(300).start();
        } else {
            track.setBackground(getDrawable(R.drawable.toggle_track_day));
            thumb.setImageResource(R.drawable.ic_sun);
            ObjectAnimator.ofFloat(thumb, "translationX", 0f).setDuration(300).start();
        }
    }
}
