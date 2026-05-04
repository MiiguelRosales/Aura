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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PantallaAjustes extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_DARK_MODE = "darkMode";
    private boolean isDarkMode;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private String nombreUsuario = "Usuario";

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

        firestore = FirebaseFirestore.getInstance();
        obtenerDatosUsuario();

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
            stopService(new Intent(PantallaAjustes.this, ChatNotificationForegroundService.class));
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

        LinearLayout navChat = findViewById(R.id.navChat);
        navChat.setOnClickListener(v -> {
            startActivity(new Intent(PantallaAjustes.this, PantallaChat.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navPerfil = findViewById(R.id.navPerfil);
        navPerfil.setOnClickListener(v -> {
            startActivity(new Intent(PantallaAjustes.this, PantallaPerfil.class));
            overridePendingTransition(0, 0);
            finish();
        });

        // --- BOTÓN SOPORTE TÉCNICO ---
        Button btnSoporte = findViewById(R.id.btnSoporte);
        btnSoporte.setOnClickListener(v -> mostrarDialogoSoporte());

        // --- BOTÓN ELIMINAR CUENTA ---
        Button btnEliminarCuenta = findViewById(R.id.btnEliminarCuenta);
        btnEliminarCuenta.setOnClickListener(v -> mostrarDialogoEliminarCuenta());
    }

    private void obtenerDatosUsuario() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            firestore.collection("usuarios").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            nombreUsuario = documentSnapshot.getString("nombreUsuario");
                            if (nombreUsuario == null) nombreUsuario = "Usuario";
                        }
                    });
        }
    }

    private void mostrarDialogoSoporte() {
        // Crear el contenedor para el EditText
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(48, 20, 48, 0);

        final com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
        til.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxStrokeColor(getResources().getColor(R.color.login_input_stroke));
        til.setHint("Describe tu problema...");
        til.setBoxCornerRadii(12, 12, 12, 12);

        final com.google.android.material.textfield.TextInputEditText etMensaje = new com.google.android.material.textfield.TextInputEditText(til.getContext());
        etMensaje.setHint(null);
        etMensaje.setMinLines(3);
        etMensaje.setGravity(android.view.Gravity.TOP);
        etMensaje.setTextCursorDrawable(R.drawable.cursor_rojo);
        etMensaje.setTextColor(getResources().getColor(R.color.aura_text_primary));
        etMensaje.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL));

        til.addView(etMensaje);
        container.addView(til, params);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Soporte Técnico")
                .setMessage("¿En qué podemos ayudarte?")
                .setView(container)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String mensaje = etMensaje.getText().toString().trim();
                    if (!mensaje.isEmpty()) {
                        enviarMensajeSoporte(mensaje);
                    } else {
                        showMessage("Por favor escribe un mensaje");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarMensajeSoporte(String mensaje) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String fecha = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(new Date());

        Map<String, Object> soporte = new HashMap<>();
        soporte.put("usuarioId", user.getUid());
        soporte.put("nombre", nombreUsuario);
        soporte.put("mensaje", mensaje);
        soporte.put("fechaRegistro", fecha); // Fecha del reporte

        firestore.collection("soporte_tecnico")
                .add(soporte)
                .addOnSuccessListener(documentReference -> {
                    showMessage("Mensaje enviado correctamente. Nos comunicaremos contigo pronto.");
                })
                .addOnFailureListener(e -> {
                    showMessage("Error al enviar el mensaje: " + e.getMessage());
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
    private void mostrarDialogoEliminarCuenta() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        firestore.collection("usuarios").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String nombreUsuario = documentSnapshot.getString("nombreUsuario");
                        String correo = user.getEmail();
                        String tipoUsuario = documentSnapshot.getString("tipoUsuario");

                        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(PantallaAjustes.this);
                        android.view.View dialogView = inflater.inflate(R.layout.dialog_eliminar_cuenta, null);

                        TextView tvNombreUsuario = dialogView.findViewById(R.id.tvNombreUsuario);
                        TextView tvCorreo = dialogView.findViewById(R.id.tvCorreo);
                        TextView tvContrasena = dialogView.findViewById(R.id.tvContrasena);

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

                        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                                new com.google.android.material.dialog.MaterialAlertDialogBuilder(PantallaAjustes.this);
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
                            guardarSolicitudEliminacion(user.getUid(), nombreUsuario, correo, tipoUsuario, motivoFinal, dialog);
                        });

                        dialog.show();
                    }
                })
                .addOnFailureListener(e -> {
                    showMessage("Error al obtener datos del usuario: " + e.getMessage());
                });

    }

    private void guardarSolicitudEliminacion(String usuarioId, String nombreUsuario, String correo, String tipoUsuario, String motivo, androidx.appcompat.app.AlertDialog dialog) {
                    String fecha = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(new Date());

                    Map<String, Object> solicitud = new HashMap<>();
                    solicitud.put("usuarioId", usuarioId);
                    solicitud.put("nombreUsuario", nombreUsuario);
                    solicitud.put("correo", correo);
                    solicitud.put("tipoUsuario", tipoUsuario);
                    solicitud.put("motivo", motivo);
                    solicitud.put("fechaRegistro", fecha);
                    solicitud.put("estado", "pendiente");

                    firestore.collection("solicitudes_eliminacion_cuenta")
                        .document(usuarioId)
                        .set(solicitud, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener(aVoid -> {
                            dialog.dismiss();
                            mostrarMensajeConfirmacion();
                        })
                        .addOnFailureListener(e -> {
                            showMessage("Error al enviar la solicitud: " + e.getMessage());
                        });
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
                    tvTitulo.setText("Solicitud Registrada");
                    tvTitulo.setTextSize(18);
                    tvTitulo.setTextColor(getResources().getColor(R.color.aura_text_primary));
                    tvTitulo.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
                    tvTitulo.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

                    android.widget.TextView tvMensaje = new android.widget.TextView(this);
                    tvMensaje.setText("Tu solicitud de eliminación ha sido recibida.\n\n" +
                        "✓ Tu cuenta será eliminada en 10 días\n" +
                        "✓ Se borrarán todos tus chats\n" +
                        "✓ Se desvincularán tus guardianes/exploradores\n" +
                        "✓ Para volver a usar Aura, deberás registrarte nuevamente\n\n" +
                        "Si tienes preguntas, contáctanos en soporte técnico.");
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
                        .setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss())
                        .setCancelable(false)
                        .show();
                    }
    }
