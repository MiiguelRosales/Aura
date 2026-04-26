package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class PantallaVincular extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_EXPLORADOR_VINCULADO_ID = "exploradorVinculadoId";
    private static final String KEY_CODIGO_VINCULADO = "codigoVinculado";
    private static final String TIPO_GUARDIAN = "GUARDIAN";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private String exploradorVinculadoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_vincular);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_vincular), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return insets;
        });

        final EditText etCodigoVincular = findViewById(R.id.etCodigoVincular);
        final MaterialButton btnAceptarVinculo = findViewById(R.id.btnAceptarVinculo);
        final MaterialCardView cardVinculoActual = findViewById(R.id.cardVinculoActual);
        final TextView tvCodigoVinculado = findViewById(R.id.tvCodigoVinculado);
        final MaterialButton btnDesvincular = findViewById(R.id.btnDesvincular);

        // Botón Volver
        MaterialButton btnRegresar = findViewById(R.id.btnRegresar);
        btnRegresar.setOnClickListener(v -> {
            startActivity(new Intent(this, pantalla_login.class));
            overridePendingTransition(0, 0);
            finish();
        });

        // Cargar datos del Guardian para ver si ya tiene vínculo
        cargarEstadoVinculo(cardVinculoActual, tvCodigoVinculado, btnDesvincular, btnRegresar);

        btnAceptarVinculo.setOnClickListener(v -> {
            if (exploradorVinculadoId != null && !exploradorVinculadoId.trim().isEmpty()) {
                showMessage("Vínculo activo. Desvincula primero antes de vincular otro explorador.");
                return;
            }
            String codigo = etCodigoVincular.getText().toString().trim().toUpperCase();
            if (codigo.isEmpty()) {
                Toast.makeText(this, "Ingresa el código del explorador", Toast.LENGTH_LONG).show();
                return;
            }
            vincularConCodigo(codigo, cardVinculoActual, tvCodigoVinculado, btnDesvincular);
        });

        btnDesvincular.setOnClickListener(v -> mostrarDialogoDesvincular());
    }

    private void cargarEstadoVinculo(MaterialCardView card, TextView tvCodigo,
                                     MaterialButton btnDesvincular, MaterialButton btnRegresar) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            btnRegresar.setVisibility(View.VISIBLE); // Sin sesión, mostrar botón volver
            return;
        }

        firestore.collection("usuarios")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String vinculadoId = doc.getString("exploradorVinculadoId");
                    String codigo = doc.getString("codigoVinculado");

                    if (vinculadoId != null && !vinculadoId.trim().isEmpty()) {
                        // Hay vínculo activo: mostrar tarjeta, ocultar botón volver
                        exploradorVinculadoId = vinculadoId;
                        card.setVisibility(View.VISIBLE);
                        tvCodigo.setText(codigo != null ? codigo : vinculadoId);
                        btnRegresar.setVisibility(View.GONE);
                    } else {
                        // Sin vínculo: mostrar botón volver al inicio
                        btnRegresar.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void mostrarDialogoDesvincular() {
        new AlertDialog.Builder(this)
                .setTitle("¿Estás seguro?")
                .setMessage("Se eliminará el vínculo entre tú y tu explorador. Ambos necesitarán vincularse de nuevo.")
                .setPositiveButton("Sí, desvincular", (dialog, which) -> ejecutarDesvinculacion())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void ejecutarDesvinculacion() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || exploradorVinculadoId == null) return;

        String guardianUid = user.getUid();

        // 1. Limpiar datos del Guardian
        firestore.collection("usuarios").document(guardianUid)
                .update(
                        "exploradorVinculadoId", null,
                        "codigoVinculado", null,
                        "vinculadoEn", null
                )
                .addOnSuccessListener(unused -> {
                    // 2. Limpiar datos del Explorador
                    firestore.collection("usuarios").document(exploradorVinculadoId)
                            .update(
                                    "guardianVinculadoId", null,
                                    "vinculadoEn", null
                            )
                            .addOnSuccessListener(unused2 -> {
                                // 3. Limpiar SharedPreferences
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                        .edit()
                                        .remove(KEY_EXPLORADOR_VINCULADO_ID)
                                        .remove(KEY_CODIGO_VINCULADO)
                                        .apply();

                                Toast.makeText(this, "Vínculo eliminado correctamente", Toast.LENGTH_LONG).show();
                                // Recargar la pantalla para mostrar estado sin vínculo
                                finish();
                                startActivity(getIntent());
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Error al limpiar datos del explorador", Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error al desvincular", Toast.LENGTH_LONG).show());
    }

    private void vincularConCodigo(String codigo, MaterialCardView card, TextView tvCodigo, MaterialButton btnDesvincular) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Debes iniciar sesión como guardián", Toast.LENGTH_LONG).show();
            return;
        }

        String guardianUid = currentUser.getUid();
        firestore.collection("usuarios")
                .document(guardianUid)
                .get()
                .addOnSuccessListener(guardianDoc -> {
                    String tipoUsuario = guardianDoc.getString("tipoUsuario");
                    if (!TIPO_GUARDIAN.equals(tipoUsuario)) {
                        Toast.makeText(this, "Solo un guardián puede vincular", Toast.LENGTH_LONG).show();
                        return;
                    }
                    validarCodigoYGuardarVinculo(guardianUid, codigo, card, tvCodigo);
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "No se pudo validar el perfil", Toast.LENGTH_LONG).show());
    }

    private void validarCodigoYGuardarVinculo(String guardianUid, String codigo,
                                               MaterialCardView card, TextView tvCodigo) {
        firestore.collection("vinculos")
                .document(codigo)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Código inválido", Toast.LENGTH_LONG).show();
                        return;
                    }

                    String expId = documentSnapshot.getString("exploradorId");
                    if (expId == null || expId.trim().isEmpty()) {
                        Toast.makeText(this, "Código sin explorador asociado", Toast.LENGTH_LONG).show();
                        return;
                    }

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putString(KEY_EXPLORADOR_VINCULADO_ID, expId)
                            .putString(KEY_CODIGO_VINCULADO, codigo)
                            .apply();

                    firestore.collection("usuarios").document(guardianUid)
                            .update(
                                    "exploradorVinculadoId", expId,
                                    "codigoVinculado", codigo,
                                    "vinculadoEn", FieldValue.serverTimestamp()
                            )
                            .addOnSuccessListener(unused -> {
                                firestore.collection("usuarios").document(expId)
                                        .update(
                                                "guardianVinculadoId", guardianUid,
                                                "vinculadoEn", FieldValue.serverTimestamp()
                                        )
                                        .addOnSuccessListener(unused2 -> {
                                            Toast.makeText(this, "Vinculación exitosa", Toast.LENGTH_LONG).show();
                                            exploradorVinculadoId = expId;
                                            card.setVisibility(View.VISIBLE);
                                            tvCodigo.setText(codigo);
                                            startActivity(new Intent(PantallaVincular.this, PantallaGuardian.class));
                                            finish();
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Error al guardar en explorador", Toast.LENGTH_LONG).show());
                            })
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "No se pudo guardar la vinculación", Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "No se pudo validar el código", Toast.LENGTH_LONG).show());
    }
}