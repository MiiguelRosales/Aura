package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.EditText;
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
import androidx.appcompat.app.AlertDialog;

import java.util.regex.Pattern;

public class pantalla_login extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String KEY_GUARDAR_LOGIN = "guardarLogin";
    private static final String KEY_CORREO_LOGIN = "correoLogin";
    private static final String KEY_CONTRASENA_LOGIN = "contrasenaLogin";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    private static final Pattern PATRON_PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[0-9])[a-z0-9]{8,12}$");

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
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return insets;
        });

        ImageView ivFondo = findViewById(R.id.ivFondoGifLogin);
        EditText etCorreo = findViewById(R.id.etCorreoLogin);
        EditText etContrasena = findViewById(R.id.etContrasenaLogin);
        CheckBox cbGuardarInicioSesion = findViewById(R.id.cbGuardarInicioSesion);
        MaterialButton btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        TextView tvDatosDemo = findViewById(R.id.tvDatosDemo);
        TextView tvCrearCuenta = findViewById(R.id.tvCrearCuenta);
        TextView tvRecuperarContrasena = findViewById(R.id.tvRecuperarContrasena);

        tvDatosDemo.setVisibility(TextView.GONE);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Auto-marcar checkbox si está guardado
        boolean guardarLoginGuardado = prefs.getBoolean(KEY_GUARDAR_LOGIN, false);
        if (guardarLoginGuardado) {
            etCorreo.setText(prefs.getString(KEY_CORREO_LOGIN, ""));
            etContrasena.setText(prefs.getString(KEY_CONTRASENA_LOGIN, ""));
            cbGuardarInicioSesion.setChecked(true);
        }

//        Glide.with(this)
//                .load(R.drawable.pantalla_registro)
//                .into(ivFondo);

        cbGuardarInicioSesion.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String correo = etCorreo.getText().toString().trim();
            String contrasena = etContrasena.getText().toString().trim();

            if (isChecked) {
                if (TextUtils.isEmpty(correo) || TextUtils.isEmpty(contrasena)) {
                    cbGuardarInicioSesion.setChecked(false);
                    Toast.makeText(this, "Completa correo y contraseña primero", Toast.LENGTH_LONG).show();
                    return;
                }

                prefs.edit()
                        .putBoolean(KEY_GUARDAR_LOGIN, true)
                        .putString(KEY_CORREO_LOGIN, correo)
                        .putString(KEY_CONTRASENA_LOGIN, contrasena)
                        .apply();

                Toast.makeText(this, "Inicio de sesión guardado", Toast.LENGTH_LONG).show();
            } else {
                prefs.edit()
                        .putBoolean(KEY_GUARDAR_LOGIN, false)
                        .remove(KEY_CORREO_LOGIN)
                        .remove(KEY_CONTRASENA_LOGIN)
                        .apply();

                Toast.makeText(this, "Credenciales olvidadas", Toast.LENGTH_LONG).show();
            }
        });

        btnIniciarSesion.setOnClickListener(v -> {
            String correo = etCorreo.getText().toString().trim();
            String contrasena = etContrasena.getText().toString().trim();

            if (TextUtils.isEmpty(correo) || TextUtils.isEmpty(contrasena)) {
                Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_LONG).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                Toast.makeText(this, "El correo es incorrecto", Toast.LENGTH_LONG).show();
                return;
            }

            btnIniciarSesion.setEnabled(false);
            loginConFirebase(correo, contrasena, btnIniciarSesion);
        });

        tvCrearCuenta.setOnClickListener(v -> {
            startActivity(new Intent(pantalla_login.this, pantalla_registro.class));
        });

        tvRecuperarContrasena.setOnClickListener(v -> mostrarDialogoRecuperarContrasena(etCorreo.getText().toString().trim()));
    }

    private void mostrarDialogoRecuperarContrasena(String correoActual) {
        final EditText inputCorreo = new EditText(this);
        inputCorreo.setHint("correo@ejemplo.com");
        inputCorreo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        inputCorreo.setText(correoActual);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        inputCorreo.setPadding(padding, padding, padding, padding);

        LinearLayout container = new LinearLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(inputCorreo);

        new AlertDialog.Builder(this)
                .setTitle("Recuperar contraseña")
                .setMessage("Ingresa tu correo para enviarte un enlace de recuperación")
                .setView(container)
                .setPositiveButton("Enviar", (dialog, which) -> {
                    String correo = inputCorreo.getText().toString().trim();
                    if (TextUtils.isEmpty(correo) || !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                        Toast.makeText(this, "Ingresa un correo válido", Toast.LENGTH_LONG).show();
                        return;
                    }

                    auth.sendPasswordResetEmail(correo)
                            .addOnSuccessListener(unused -> Toast.makeText(this,
                                    "Te enviamos un enlace de recuperación",
                                    Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e -> Toast.makeText(this,
                                    "No se pudo enviar el correo: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void loginConFirebase(String correo,
                                  String contrasena,
                                  MaterialButton btnIniciarSesion) {
        auth.signInWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        btnIniciarSesion.setEnabled(true);
                        Toast.makeText(this, "No se pudo recuperar usuario", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (!authResult.getUser().isEmailVerified()) {
                        btnIniciarSesion.setEnabled(true);
                        Toast.makeText(this, "Por favor, verifica tu correo electrónico antes de entrar.", Toast.LENGTH_LONG).show();
                        auth.signOut();
                        return;
                    }

                    String uid = authResult.getUser().getUid();
                    firestore.collection("usuarios")
                            .document(uid)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (!documentSnapshot.exists()) {
                                    btnIniciarSesion.setEnabled(true);
                                    Toast.makeText(this, "Perfil no encontrado", Toast.LENGTH_LONG).show();
                                    return;
                                }

                                String tipoReal = documentSnapshot.getString("tipoUsuario");
                                if (tipoReal == null) {
                                    btnIniciarSesion.setEnabled(true);
                                    Toast.makeText(this, "Perfil incompleto", Toast.LENGTH_LONG).show();
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

                                Toast.makeText(this, "Inicio de sesión correcto", Toast.LENGTH_LONG).show();
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
                                showMessage("Error al leer perfil: " + detalle);
                            });
                })
                .addOnFailureListener(e -> {
                    btnIniciarSesion.setEnabled(true);
                    if (e instanceof FirebaseAuthException) {
                        String code = ((FirebaseAuthException) e).getErrorCode();
                        if (code.equals("ERROR_USER_NOT_FOUND") || code.equals("user-not-found")) {
                            Toast.makeText(this, "El correo es incorrecto", Toast.LENGTH_LONG).show();
                            return;
                        } else if (code.equals("ERROR_WRONG_PASSWORD") || code.equals("wrong-password")) {
                            Toast.makeText(this, "La contraseña es incorrecta", Toast.LENGTH_LONG).show();
                            return;
                        } else if (code.equals("ERROR_INVALID_CREDENTIAL") || code.equals("invalid-credential") || code.equals("INVALID_LOGIN_CREDENTIALS")) {
                            Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    showMessage("Error de login: " + e.getMessage());
                });
    }
}