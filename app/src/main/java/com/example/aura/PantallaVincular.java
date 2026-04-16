package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) { //llamado cuando se crea por primera vez la actividad
        super.onCreate(savedInstanceState); //llamada a su implementacion
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_vincular); //indica a android que debe establecer
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_vincular), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //IMAGEN PARA EL FONDO ANIMADO
        final ImageView ivFondoGifVincular = (ImageView) findViewById(R.id.ivFondoGifVincular);

        //BOTON PARA REGRESAR AL INICIO
        final ImageButton imageButtonRegresar = (ImageButton) findViewById(R.id.imageButtonRegresar);
        final EditText etCodigoVincular = findViewById(R.id.etCodigoVincular);
        final MaterialButton btnAceptarVinculo = findViewById(R.id.btnAceptarVinculo);

        //AQUI SE CARGA EL FONDO ANIMADO CON GLIDE
        Glide.with(this)
                .load(R.drawable.pantalla_vincular)
                .into(ivFondoGifVincular);

        btnAceptarVinculo.setOnClickListener(v -> {
            String codigo = etCodigoVincular.getText().toString().trim().toUpperCase();
            if (codigo.isEmpty()) {
                Toast.makeText(this, "Ingresa el código del Explorador", Toast.LENGTH_SHORT).show();
                return;
            }

            vincularConCodigo(codigo);
        });

        //EVENTO REGRESAR AL INICIO
        imageButtonRegresar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PantallaVincular.this, pantalla_inicio.class);
                startActivity(intent);
            }
        });
    }

    private void vincularConCodigo(String codigo) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Debes iniciar sesión como Guardián", Toast.LENGTH_SHORT).show();
            return;
        }

        String guardianUid = currentUser.getUid();
        firestore.collection("usuarios")
                .document(guardianUid)
                .get()
                .addOnSuccessListener(guardianDoc -> {
                    String tipoUsuario = guardianDoc.getString("tipoUsuario");
                    if (!TIPO_GUARDIAN.equals(tipoUsuario)) {
                        Toast.makeText(this, "Solo un Guardián puede vincular", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    validarCodigoYGuardarVinculo(guardianUid, codigo);
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "No se pudo validar el perfil", Toast.LENGTH_SHORT).show());
    }

    private void validarCodigoYGuardarVinculo(String guardianUid, String codigo) {
        firestore.collection("vinculos")
                .document(codigo)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "Código inválido", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String exploradorId = documentSnapshot.getString("exploradorId");
                    if (exploradorId == null || exploradorId.trim().isEmpty()) {
                        Toast.makeText(this, "Código sin Explorador asociado", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putString(KEY_EXPLORADOR_VINCULADO_ID, exploradorId)
                            .putString(KEY_CODIGO_VINCULADO, codigo)
                            .apply();

                    firestore.collection("usuarios")
                            .document(guardianUid)
                            .update(
                                    "exploradorVinculadoId", exploradorId,
                                    "codigoVinculado", codigo,
                                    "vinculadoEn", FieldValue.serverTimestamp()
                            )
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Vinculación exitosa", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(PantallaVincular.this, PantallaGuardian.class));
                                finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "No se pudo guardar la vinculación", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "No se pudo validar el código", Toast.LENGTH_SHORT).show());
    }
}