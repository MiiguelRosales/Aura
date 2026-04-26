package com.example.aura;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PantallaCompartirCodigo extends BaseActivity {

    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String DOC_CONFIGURACION = "configuracion/registro_general";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private String codigoActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_compartir_codigo);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_compartir), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return insets;
        });

        final EditText etCodigoVincular = findViewById(R.id.etCodigoVincular);
        final MaterialButton btnCompartir = findViewById(R.id.btnAceptarVinculo);

        etCodigoVincular.setText("Verificando usuario...");
        etCodigoVincular.setEnabled(false);
        etCodigoVincular.setFocusable(false);
        btnCompartir.setEnabled(false);

        prepararCodigoExplorador(etCodigoVincular, btnCompartir);

        btnCompartir.setOnClickListener(v -> {
            if (codigoActual == null) {
                Toast.makeText(this, "Código no disponible", Toast.LENGTH_SHORT).show();
                return;
            }

            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clipData = ClipData.newPlainText("codigo_explorador", codigoActual);
                clipboard.setPrimaryClip(clipData);
            }
            Toast.makeText(this, "Código copiado y listo para compartir", Toast.LENGTH_SHORT).show();
        });

    }

    private void prepararCodigoExplorador(EditText etCodigoVincular, MaterialButton btnCompartir) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            etCodigoVincular.setText("Inicia sesión como Explorador");
            Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = currentUser.getUid();
        firestore.collection("usuarios")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String tipoUsuario = userDoc.getString("tipoUsuario");
                    if (!TIPO_EXPLORADOR.equals(tipoUsuario)) {
                        etCodigoVincular.setText("Solo Explorador puede compartir código");
                        Toast.makeText(this, "Esta cuenta no es Explorador", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    verificarGuardianRegistrado(uid, etCodigoVincular, btnCompartir);
                })
                .addOnFailureListener(e -> {
                    etCodigoVincular.setText("Error al validar perfil");
                    Toast.makeText(this, "No se pudo leer tu perfil", Toast.LENGTH_SHORT).show();
                });
    }

    private void verificarGuardianRegistrado(String exploradorId,
                                             EditText etCodigoVincular,
                                             MaterialButton btnCompartir) {
        firestore.document(DOC_CONFIGURACION)
                .get()
                .addOnSuccessListener(configDoc -> {
                    Boolean guardianRegistrado = configDoc.getBoolean("guardianRegistrado");
                    if (guardianRegistrado == null || !guardianRegistrado) {
                        etCodigoVincular.setText("Registra un Guardián primero");
                        Toast.makeText(this,
                                "No hay Guardián registrado aún", Toast.LENGTH_LONG).show();
                        return;
                    }

                    codigoActual = generarCodigoVinculacion(exploradorId);
                    etCodigoVincular.setText(codigoActual);
                    btnCompartir.setEnabled(true);
                    publicarCodigoVinculacion(codigoActual, exploradorId);
                })
                .addOnFailureListener(e -> {
                    etCodigoVincular.setText("No se pudo validar Guardián");
                    Toast.makeText(this,
                            "Error al consultar configuración", Toast.LENGTH_SHORT).show();
                });
    }

    private String generarCodigoVinculacion(String exploradorId) {
        String limpio = exploradorId.replace("-", "").toUpperCase(Locale.ROOT);
        String base = limpio.length() >= 6 ? limpio.substring(limpio.length() - 6) : limpio;
        // Eliminar caracteres ambiguos: 0 (cero) y O (letra) → Z
        base = base.replace('0', 'Z').replace('O', 'Z');
        return "AURA-" + base;
    }

    private void publicarCodigoVinculacion(String codigo, String exploradorId) {
        Map<String, Object> vinculo = new HashMap<>();
        vinculo.put("exploradorId", exploradorId);
        vinculo.put("codigo", codigo);
        vinculo.put("actualizadoEn", FieldValue.serverTimestamp());

        firestore.collection("vinculos")
                .document(codigo)
                .set(vinculo)
            .addOnSuccessListener(unused -> firestore.collection("usuarios")
                .document(exploradorId)
                .update("codigoExplorador", codigo))
            .addOnFailureListener(e -> Toast.makeText(this,
                "No se pudo publicar el código", Toast.LENGTH_SHORT).show());
    }
}