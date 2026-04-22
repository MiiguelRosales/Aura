package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class pantalla_registro extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) { //llamado cuando se crea por primera vez la actividad
        super.onCreate(savedInstanceState); //llamada a su implementacion
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_registro); //indica a android que debe establecer
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_registro), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //IMAGEN PARA EL FONDO ANIMADO
        final ImageView ivFondoGif = (ImageView) findViewById(R.id.ivFondoGif);

        //BOTON PARA REGRESAR A LA PAGINA PRINCIPAL
        final ImageButton btnRegresar = (ImageButton) findViewById(R.id.imageButtonRegistroIzquierda);
        final MaterialButton btnRegistrar = findViewById(R.id.btnRegistrar);

        final EditText etNombreUsuario = findViewById(R.id.etNombreUsuario);
        final EditText etDia = findViewById(R.id.etDia);
        final EditText etMes = findViewById(R.id.etMes);
        final EditText etAnio = findViewById(R.id.etAnio);
        final EditText etCorreoLocal = findViewById(R.id.etCorreoLocal);
        final EditText etCelular = findViewById(R.id.etCelular);
        final EditText etContrasena = findViewById(R.id.etContrasena);
        final EditText etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena);

        //AQUI SE CARGA EL FONDO ANIMADO CON GLIDE
//        Glide.with(this)
//                .load(R.drawable.pantalla_registro) // Tu archivo webp animado
//                .into(ivFondoGif);

        //DROPDOWN DOMINIO DE CORREO
        final String[] dominios = {"gmail.com", "hotmail.com", "outlook.com", "yahoo.com", "icloud.com"};
        final AutoCompleteTextView actvDominio = findViewById(R.id.actvDominio);
        ArrayAdapter<String> adapterDominio = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, dominios);
        actvDominio.setAdapter(adapterDominio);
        actvDominio.setText(dominios[0], false);

        //DROPDOWN TIPO DE USUARIO
        final String[] tiposUsuario = {"🛡️  Guardián", "🌟  Explorador"};
        final AutoCompleteTextView actvTipoUsuario = findViewById(R.id.actvTipoUsuario);
        final TextView tvInfoTipoUsuario = findViewById(R.id.tvInfoTipoUsuario);
        ArrayAdapter<String> adapterTipo = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, tiposUsuario);
        actvTipoUsuario.setAdapter(adapterTipo);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String tipoGuardado = prefs.getString(KEY_TIPO_USUARIO, TIPO_EXPLORADOR);
        if (TIPO_GUARDIAN.equals(tipoGuardado)) {
            actvTipoUsuario.setText(tiposUsuario[0], false);
            actualizarInfoTipoUsuario(tvInfoTipoUsuario, TIPO_GUARDIAN);
        } else {
            actvTipoUsuario.setText(tiposUsuario[1], false);
            actualizarInfoTipoUsuario(tvInfoTipoUsuario, TIPO_EXPLORADOR);
        }

        actvTipoUsuario.setOnItemClickListener((parent, view, position, id) -> {
            String tipo = position == 0 ? TIPO_GUARDIAN : TIPO_EXPLORADOR;
            actualizarInfoTipoUsuario(tvInfoTipoUsuario, tipo);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TIPO_USUARIO, tipo)
                    .apply();
        });

        btnRegistrar.setOnClickListener(v -> {
            String tipoSeleccionado = actvTipoUsuario.getText().toString();
            String tipoUsuario = tipoSeleccionado.contains("Guardián") ? TIPO_GUARDIAN : TIPO_EXPLORADOR;

            String nombre = etNombreUsuario.getText().toString().trim();
            String dia = etDia.getText().toString().trim();
            String mes = etMes.getText().toString().trim();
            String anio = etAnio.getText().toString().trim();
            String correoLocal = etCorreoLocal.getText().toString().trim();
            String dominio = actvDominio.getText().toString().trim();
            String celular = etCelular.getText().toString().trim();
            String contrasena = etContrasena.getText().toString().trim();
            String confirmarContrasena = etConfirmarContrasena.getText().toString().trim();

            if (TextUtils.isEmpty(nombre) || TextUtils.isEmpty(dia) || TextUtils.isEmpty(mes)
                    || TextUtils.isEmpty(anio) || TextUtils.isEmpty(correoLocal)
                    || TextUtils.isEmpty(dominio) || TextUtils.isEmpty(celular)
                    || TextUtils.isEmpty(contrasena) || TextUtils.isEmpty(confirmarContrasena)) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            if (contrasena.length() < 8) {
                Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!contrasena.equals(confirmarContrasena)) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
                return;
            }

            String correo = correoLocal + "@" + dominio;
            String fechaNacimiento = dia + "/" + mes + "/" + anio;

            btnRegistrar.setEnabled(false);
            registrarUsuarioFirebase(correo, contrasena, tipoUsuario, nombre, celular, fechaNacimiento, btnRegistrar);
        });

        //ESTE ES EL EVENTO DEL BOTON PARA REGRESAR A LA PANTALLA DE INICIO
        btnRegresar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Creamos el intent para ir a la pantalla de inicio
                Intent intent = new Intent(pantalla_registro.this, pantalla_inicio.class);
                startActivity(intent);
                // Cerramos la pantalla actual para que no se acumulen en el fondo
                finish();
            }
        });
    }

    private void actualizarInfoTipoUsuario(TextView tvInfoTipoUsuario, String tipo) {
        if (TIPO_GUARDIAN.equals(tipo)) {
            tvInfoTipoUsuario.setText("Modo Guardián: cuidas y monitoreas a tu Explorador.");
            tvInfoTipoUsuario.setVisibility(View.VISIBLE);
        } else if (TIPO_EXPLORADOR.equals(tipo)) {
            tvInfoTipoUsuario.setText("Modo Explorador: recibes acompañamiento y protección de tu Guardián.");
            tvInfoTipoUsuario.setVisibility(View.VISIBLE);
        } else {
            tvInfoTipoUsuario.setVisibility(View.GONE);
        }
    }

    private void registrarUsuarioFirebase(String correo,
                                          String contrasena,
                                          String tipoUsuario,
                                          String nombre,
                                          String celular,
                                          String fechaNacimiento,
                                          MaterialButton btnRegistrar) {
        auth.createUserWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        btnRegistrar.setEnabled(true);
                        Toast.makeText(this, "No se pudo obtener usuario", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = authResult.getUser().getUid();
                    Map<String, Object> perfil = new HashMap<>();
                    perfil.put("uid", uid);
                    perfil.put("nombreUsuario", nombre);
                    perfil.put("correo", correo);
                    perfil.put("celular", celular);
                    perfil.put("fechaNacimiento", fechaNacimiento);
                    perfil.put("tipoUsuario", tipoUsuario);
                    perfil.put("creadoEn", FieldValue.serverTimestamp());

                    firestore.collection("usuarios")
                            .document(uid)
                            .set(perfil)
                            .addOnSuccessListener(unused -> {
                            if (TIPO_GUARDIAN.equals(tipoUsuario)) {
                                Map<String, Object> configuracion = new HashMap<>();
                                configuracion.put("guardianRegistrado", true);
                                configuracion.put("guardianUid", uid);
                                configuracion.put("actualizadoEn", FieldValue.serverTimestamp());

                                firestore.document("configuracion/registro_general")
                                    .set(configuracion);
                            }

                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                        .edit()
                                        .putString(KEY_TIPO_USUARIO, tipoUsuario)
                                        .apply();

                                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show();

                                Intent intentDestino = TIPO_GUARDIAN.equals(tipoUsuario)
                                    ? new Intent(this, PantallaVincular.class)
                                        : new Intent(this, PantallaJuegos.class);
                                startActivity(intentDestino);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnRegistrar.setEnabled(true);
                                String detalle = e.getMessage();
                                if (e instanceof FirebaseFirestoreException) {
                                    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
                                    detalle = "[" + firestoreException.getCode() + "] " + detalle;
                                }
                                Toast.makeText(this, "No se pudo guardar perfil: " + detalle, Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnRegistrar.setEnabled(true);
                    String detalle = e.getMessage();
                    if (e instanceof FirebaseAuthException) {
                        String code = ((FirebaseAuthException) e).getErrorCode();
                        detalle = "[" + code + "] " + detalle;
                    }
                    Toast.makeText(this, "Error al registrar: " + detalle, Toast.LENGTH_LONG).show();
                });
    }
}