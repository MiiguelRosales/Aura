package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;

public class pantalla_login extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String KEY_GUARDAR_LOGIN = "guardarLogin";
    private static final String KEY_CORREO_LOGIN = "correoLogin";
    private static final String KEY_CONTRASENA_LOGIN = "contrasenaLogin";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_login);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_login), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView ivFondo = findViewById(R.id.ivFondoGifLogin);
        ImageButton btnRegresar = findViewById(R.id.imageButtonLoginIzquierda);
        EditText etCorreo = findViewById(R.id.etCorreoLogin);
        EditText etContrasena = findViewById(R.id.etContrasenaLogin);
        AutoCompleteTextView actvTipoUsuarioLogin = findViewById(R.id.actvTipoUsuarioLogin);
        CheckBox cbGuardarInicioSesion = findViewById(R.id.cbGuardarInicioSesion);
        MaterialButton btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        TextView tvDatosDemo = findViewById(R.id.tvDatosDemo);

        tvDatosDemo.setVisibility(TextView.GONE);

        final String[] tiposUsuario = {"🛡️  Guardián", "🌟  Explorador"};
        ArrayAdapter<String> adapterTipo = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, tiposUsuario);
        actvTipoUsuarioLogin.setAdapter(adapterTipo);

        SharedPreferences prefsTipo = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String tipoGuardado = prefsTipo.getString(KEY_TIPO_USUARIO, TIPO_EXPLORADOR);
        if (TIPO_GUARDIAN.equals(tipoGuardado)) {
            actvTipoUsuarioLogin.setText(tiposUsuario[0], false);
        } else {
            actvTipoUsuarioLogin.setText(tiposUsuario[1], false);
        }

        // Auto-marcar checkbox si está guardado
        boolean guardarLoginGuardado = prefs.getBoolean(KEY_GUARDAR_LOGIN, false);
        if (guardarLoginGuardado) {
            etCorreo.setText(prefs.getString(KEY_CORREO_LOGIN, ""));
            etContrasena.setText(prefs.getString(KEY_CONTRASENA_LOGIN, ""));
            cbGuardarInicioSesion.setChecked(true);
        }

        actvTipoUsuarioLogin.setOnItemClickListener((parent, view, position, id) -> {
            String tipo = position == 0 ? TIPO_GUARDIAN : TIPO_EXPLORADOR;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TIPO_USUARIO, tipo)
                    .apply();
        });

        Glide.with(this)
                .load(R.drawable.pantalla_registro)
                .into(ivFondo);

        btnRegresar.setOnClickListener(v -> {
            startActivity(new Intent(pantalla_login.this, pantalla_inicio.class));
            finish();
        });

        cbGuardarInicioSesion.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String correo = etCorreo.getText().toString().trim();
            String contrasena = etContrasena.getText().toString().trim();

            if (isChecked) {
                if (TextUtils.isEmpty(correo) || TextUtils.isEmpty(contrasena)) {
                    cbGuardarInicioSesion.setChecked(false);
                    Toast.makeText(this, "Completa correo y contraseña primero", Toast.LENGTH_SHORT).show();
                    return;
                }

                prefs.edit()
                        .putBoolean(KEY_GUARDAR_LOGIN, true)
                        .putString(KEY_CORREO_LOGIN, correo)
                        .putString(KEY_CONTRASENA_LOGIN, contrasena)
                        .apply();

                Toast.makeText(this, "Inicio de sesión guardado", Toast.LENGTH_SHORT).show();
            } else {
                prefs.edit()
                        .putBoolean(KEY_GUARDAR_LOGIN, false)
                        .remove(KEY_CORREO_LOGIN)
                        .remove(KEY_CONTRASENA_LOGIN)
                        .apply();

                Toast.makeText(this, "Credenciales olvidadas", Toast.LENGTH_SHORT).show();
            }
        });

        btnIniciarSesion.setOnClickListener(v -> {
            String correo = etCorreo.getText().toString().trim();
            String contrasena = etContrasena.getText().toString().trim();
            String tipoSeleccionado = actvTipoUsuarioLogin.getText().toString();
            String tipoEsperado = tipoSeleccionado.contains("Guardián") ? TIPO_GUARDIAN : TIPO_EXPLORADOR;

            if (TextUtils.isEmpty(correo) || TextUtils.isEmpty(contrasena)) {
                Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_SHORT).show();
                return;
            }

            btnIniciarSesion.setEnabled(false);
            loginConFirebase(correo, contrasena, tipoEsperado, btnIniciarSesion);
        });
    }

    private void loginConFirebase(String correo,
                                  String contrasena,
                                  String tipoEsperado,
                                  MaterialButton btnIniciarSesion) {
        auth.signInWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        btnIniciarSesion.setEnabled(true);
                        Toast.makeText(this, "No se pudo recuperar usuario", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = authResult.getUser().getUid();
                    firestore.collection("usuarios")
                            .document(uid)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (!documentSnapshot.exists()) {
                                    btnIniciarSesion.setEnabled(true);
                                    Toast.makeText(this, "Perfil no encontrado", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                String tipoReal = documentSnapshot.getString("tipoUsuario");
                                if (tipoReal == null) {
                                    btnIniciarSesion.setEnabled(true);
                                    Toast.makeText(this, "Perfil incompleto", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                if (!tipoEsperado.equals(tipoReal)) {
                                    auth.signOut();
                                    btnIniciarSesion.setEnabled(true);
                                    Toast.makeText(this,
                                            "Este usuario está registrado como " + tipoReal,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                                prefs.edit().putString(KEY_TIPO_USUARIO, tipoReal).apply();

                                if (TIPO_GUARDIAN.equals(tipoReal)) {
                                    firestore.document("configuracion/registro_general")
                                            .set(new java.util.HashMap<String, Object>() {{
                                                put("guardianRegistrado", true);
                                                put("guardianUid", uid);
                                                put("actualizadoEn", FieldValue.serverTimestamp());
                                            }});
                                }

                                Intent intentDestino;
                                if (TIPO_GUARDIAN.equals(tipoReal)) {
                                    String exploradorVinculadoId = documentSnapshot.getString("exploradorVinculadoId");
                                    boolean tieneVinculo = exploradorVinculadoId != null
                                            && !exploradorVinculadoId.trim().isEmpty();

                                    intentDestino = tieneVinculo
                                            ? new Intent(pantalla_login.this, PantallaGuardian.class)
                                            : new Intent(pantalla_login.this, PantallaVincular.class);
                                } else {
                                    intentDestino = new Intent(pantalla_login.this, PantallaJuegos.class);
                                }

                                Toast.makeText(this, "Inicio de sesión correcto", Toast.LENGTH_SHORT).show();
                                startActivity(intentDestino);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnIniciarSesion.setEnabled(true);
                                String detalle = e.getMessage();
                                if (e instanceof FirebaseFirestoreException) {
                                    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
                                    detalle = "[" + firestoreException.getCode() + "] " + detalle;
                                }
                                Toast.makeText(this, "Error al leer perfil: " + detalle, Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnIniciarSesion.setEnabled(true);
                    String detalle = e.getMessage();
                    if (e instanceof FirebaseAuthException) {
                        String code = ((FirebaseAuthException) e).getErrorCode();
                        detalle = "[" + code + "] " + detalle;
                    }
                    Toast.makeText(this, "Error de login: " + detalle, Toast.LENGTH_LONG).show();
                });
    }
}