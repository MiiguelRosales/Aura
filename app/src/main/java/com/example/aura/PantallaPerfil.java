package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PantallaPerfil extends BaseActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    
    private EditText etNombre;
    private EditText etCorreo;
    private TextView tvTipoUsuario;
    private EditText etCelular;
    private EditText etFechaNac;
    private EditText etContrasena;
    private Button btnModificar;

    private boolean enModoEdicion = false;

    // Almacenar valores originales para comparar
    private String originalNombre = "";
    private String originalCorreo = "";
    private String originalCelular = "";
    private String originalFechaNac = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_perfil);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_perfil), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        etNombre = findViewById(R.id.etNombreUsuarioPerfil);
        etCorreo = findViewById(R.id.etCorreoPerfil);
        tvTipoUsuario = findViewById(R.id.tvTipoUsuarioPerfil);
        etCelular = findViewById(R.id.etCelularPerfil);
        etFechaNac = findViewById(R.id.etFechaNacPerfil);
        etContrasena = findViewById(R.id.etContrasenaPerfil);
        btnModificar = findViewById(R.id.btnModificar);

        cargarDatosPerfil();

        btnModificar.setOnClickListener(v -> {
            if (!enModoEdicion) {
                habilitarEdicion();
            } else {
                guardarCambios();
            }
        });

        // ── Navegación Inferior ───────────────────────────────────────
        SharedPreferences prefs = getSharedPreferences("AuraPrefs", MODE_PRIVATE);
        String tipoUsuario = prefs.getString("tipoUsuario", "GUARDIAN");

        // Actualizar icono y texto del primer tab según el tipo de usuario
        TextView tvNavIcono = findViewById(R.id.tvNavPrincipalIcono);
        TextView tvNavTexto = findViewById(R.id.tvNavPrincipalTexto);
        if ("EXPLORADOR".equals(tipoUsuario)) {
            tvNavIcono.setText("🎮");
            tvNavTexto.setText("Explorador");
        }

        LinearLayout navPaginaGuardian = findViewById(R.id.navPaginaGuardian);
        navPaginaGuardian.setOnClickListener(v -> {
            Intent intent = "EXPLORADOR".equals(tipoUsuario)
                    ? new Intent(PantallaPerfil.this, PantallaJuegos.class)
                    : new Intent(PantallaPerfil.this, PantallaGuardian.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navConfiguraciones = findViewById(R.id.navConfiguraciones);
        navConfiguraciones.setOnClickListener(v -> {
            startActivity(new Intent(PantallaPerfil.this, PantallaAjustes.class));
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void cargarDatosPerfil() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            originalCorreo = user.getEmail() != null ? user.getEmail() : "";
            etCorreo.setText(originalCorreo.isEmpty() ? "Sin correo" : originalCorreo);

            firestore.collection("usuarios")
                    .document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            originalNombre = documentSnapshot.getString("nombreUsuario");
                            String tipo = documentSnapshot.getString("tipoUsuario");
                            originalCelular = documentSnapshot.getString("celular");
                            originalFechaNac = documentSnapshot.getString("fechaNacimiento");

                            originalNombre = originalNombre != null ? originalNombre : "Guardián";
                            originalCelular = originalCelular != null ? originalCelular : "No registrado";
                            originalFechaNac = originalFechaNac != null ? originalFechaNac : "No registrada";

                            etNombre.setText(originalNombre);
                            
                            if ("GUARDIAN".equals(tipo)) {
                                tvTipoUsuario.setText("🛡️ Guardián");
                            } else if ("EXPLORADOR".equals(tipo)) {
                                tvTipoUsuario.setText("🌟 Explorador");
                            } else {
                                tvTipoUsuario.setText(tipo != null ? tipo : "Desconocido");
                            }

                            etCelular.setText(originalCelular);
                            etFechaNac.setText(originalFechaNac);
                        } else {
                            etNombre.setText("Usuario Desconocido");
                            tvTipoUsuario.setText("Desconocido");
                            etCelular.setText("No registrado");
                            etFechaNac.setText("No registrada");
                        }
                    })
                    .addOnFailureListener(e -> {
                        etNombre.setText("Error al cargar");
                        tvTipoUsuario.setText("Error");
                        etCelular.setText("Error");
                        etFechaNac.setText("Error");
                    });
        } else {
            etNombre.setText("Invitado");
            etCorreo.setText("No has iniciado sesión");
            tvTipoUsuario.setText("---");
            etCelular.setText("---");
            etFechaNac.setText("---");
        }
    }

    private void habilitarEdicion() {
        enModoEdicion = true;
        btnModificar.setText("GUARDAR CAMBIOS");

        // Habilitar campos
        etNombre.setEnabled(true);
        etCorreo.setEnabled(true);
        etCelular.setEnabled(true);
        etFechaNac.setEnabled(true);
        etContrasena.setEnabled(true);

        // Dar un poco de estilo para que parezcan campos de texto editables
        int bg = R.drawable.card_inner_bg; // Reutilizando un fondo oscuro sutil
        etNombre.setBackgroundResource(bg);
        etCorreo.setBackgroundResource(bg);
        etCelular.setBackgroundResource(bg);
        etFechaNac.setBackgroundResource(bg);
        etContrasena.setBackgroundResource(bg);

        // Limpiar y poner hints de opcional para todos menos la fecha de nacimiento
        etNombre.setText("");
        etNombre.setHint("(Opcional) Nuevo nombre");

        etCorreo.setText("");
        etCorreo.setHint("(Opcional) Nuevo correo");
        
        etCelular.setText("");
        etCelular.setHint("(Opcional) Nuevo celular");

        etContrasena.setText("");
        etContrasena.setHint("(Opcional) Nueva contraseña");
    }

    private void deshabilitarEdicion() {
        enModoEdicion = false;
        btnModificar.setText("MODIFICAR DATOS");

        // Deshabilitar campos
        etNombre.setEnabled(false);
        etCorreo.setEnabled(false);
        etCelular.setEnabled(false);
        etFechaNac.setEnabled(false);
        etContrasena.setEnabled(false);

        // Quitar fondos
        etNombre.setBackground(null);
        etCorreo.setBackground(null);
        etCelular.setBackground(null);
        etFechaNac.setBackground(null);
        etContrasena.setBackground(null);

        // Restaurar textos y hints
        etNombre.setHint("Nombre");
        etNombre.setText(originalNombre);

        etCorreo.setHint("Correo");
        etCorreo.setText(originalCorreo.isEmpty() ? "Sin correo" : originalCorreo);
        
        etCelular.setHint("Celular");
        etCelular.setText(originalCelular);

        etFechaNac.setText(originalFechaNac);

        etContrasena.setText("••••••••");
    }

    private void guardarCambios() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String tempNombre = etNombre.getText().toString().trim();
        final String nuevoNombre = tempNombre.isEmpty() ? originalNombre : tempNombre;
        
        final String nuevaFechaNac = etFechaNac.getText().toString().trim();
        
        String tempCorreo = etCorreo.getText().toString().trim();
        final String nuevoCorreo = tempCorreo.isEmpty() ? originalCorreo : tempCorreo;
        
        String tempCelular = etCelular.getText().toString().trim();
        final String nuevoCelular = tempCelular.isEmpty() ? originalCelular : tempCelular;

        String nuevaContrasena = etContrasena.getText().toString().trim();

        if (TextUtils.isEmpty(nuevaFechaNac)) {
            Toast.makeText(this, "Completa la fecha de nacimiento", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean cambioDatos = !nuevoNombre.equals(originalNombre) 
                || !nuevoCorreo.equals(originalCorreo) 
                || !nuevoCelular.equals(originalCelular) 
                || !nuevaFechaNac.equals(originalFechaNac);
        
        boolean cambioContrasena = !TextUtils.isEmpty(nuevaContrasena);

        if (!cambioDatos && !cambioContrasena) {
            // No hubo cambios
            deshabilitarEdicion();
            return;
        }

        btnModificar.setEnabled(false);
        btnModificar.setText("GUARDANDO...");

        if (cambioDatos) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("nombreUsuario", nuevoNombre);
            updates.put("correo", nuevoCorreo);
            updates.put("celular", nuevoCelular);
            updates.put("fechaNacimiento", nuevaFechaNac);

            // Guardar en Firestore
            firestore.collection("usuarios")
                    .document(user.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Actualizar original values locales
                        originalNombre = nuevoNombre;
                        originalCelular = nuevoCelular;
                        originalFechaNac = nuevaFechaNac;

                        if (!nuevoCorreo.equals(originalCorreo)) {
                            // Actualizar correo en Firebase Auth
                            user.updateEmail(nuevoCorreo)
                                    .addOnSuccessListener(aVoid1 -> {
                                        originalCorreo = nuevoCorreo;
                                        procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
                                    })
                                    .addOnFailureListener(e -> {
                                        showMessage("Perfil guardado. Error al actualizar correo de sesión.");
                                        procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
                                    });
                        } else {
                            procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error al guardar perfil", Toast.LENGTH_SHORT).show();
                        btnModificar.setEnabled(true);
                        btnModificar.setText("GUARDAR CAMBIOS");
                    });
        } else {
            // Solo cambió la contraseña
            procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
        }
    }

    private void procesarCambioContrasena(FirebaseUser user, String nuevaContrasena, boolean cambioContrasena) {
        if (cambioContrasena) {
            user.updatePassword(nuevaContrasena)
                    .addOnSuccessListener(aVoid1 -> {
                        Toast.makeText(this, "Perfil y datos actualizados correctamente", Toast.LENGTH_SHORT).show();
                        finalizarGuardado();
                    })
                    .addOnFailureListener(e -> {
                        showMessage("Cambios guardados, pero hubo error al cambiar la contraseña (intenta cerrar sesión e iniciar de nuevo).");
                        finalizarGuardado();
                    });
        } else {
            Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show();
            finalizarGuardado();
        }
    }

    private void finalizarGuardado() {
        btnModificar.setEnabled(true);
        deshabilitarEdicion();
    }
}
