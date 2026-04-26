package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class pantalla_registro extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    private static final Pattern PATRON_USUARIO = Pattern.compile("^[A-Za-z]{1,12}$");
    private static final Pattern PATRON_TELEFONO = Pattern.compile("^[0-9]{10}$");
    private static final Pattern PATRON_PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[0-9])[a-z0-9]{8,12}$");
    private static final Pattern PATRON_LOCAL_CORREO = Pattern.compile("^[A-Za-z0-9._%+-]{1,25}$");

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
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return insets;
        });

        //IMAGEN PARA EL FONDO ANIMADO
        final ImageView ivFondoGif = (ImageView) findViewById(R.id.ivFondoGif);

        final MaterialButton btnRegistrar = findViewById(R.id.btnRegistrar);

        final EditText etNombreUsuario = findViewById(R.id.etNombreUsuario);
        final EditText etDia = findViewById(R.id.etDia);
        final EditText etMes = findViewById(R.id.etMes);
        final EditText etAnio = findViewById(R.id.etAnio);
        final EditText etCorreoLocal = findViewById(R.id.etCorreoLocal);
        final EditText etCelular = findViewById(R.id.etCelular);
        final EditText etContrasena = findViewById(R.id.etContrasena);
        final EditText etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena);

        configurarAutoAvanceFecha(etDia, etMes, etAnio);

        //AQUI SE CARGA EL FONDO ANIMADO CON GLIDE
//        Glide.with(this)
//                .load(R.drawable.pantalla_registro) // Tu archivo webp animado
//                .into(ivFondoGif);

        //DROPDOWN DOMINIO DE CORREO
        final String[] dominios = {"gmail.com", "hotmail.com", "outlook.com", "yahoo.com", "icloud.com"};
        final AutoCompleteTextView actvDominio = findViewById(R.id.actvDominio);
        ArrayAdapter<String> adapterDominio = new ArrayAdapter<>(this,
            R.layout.item_dropdown_blue, dominios);
        adapterDominio.setDropDownViewResource(R.layout.item_dropdown_blue);
        actvDominio.setAdapter(adapterDominio);
        actvDominio.setText(dominios[0], false);

        //DROPDOWN TIPO DE USUARIO
        final String[] tiposUsuario = {"🛡️  Guardián", "🌟  Explorador"};
        final AutoCompleteTextView actvTipoUsuario = findViewById(R.id.actvTipoUsuario);
        final TextView tvInfoTipoUsuario = findViewById(R.id.tvInfoTipoUsuario);
        ArrayAdapter<String> adapterTipo = new ArrayAdapter<>(this,
            R.layout.item_dropdown_blue, tiposUsuario);
        adapterTipo.setDropDownViewResource(R.layout.item_dropdown_blue);
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

            if (TextUtils.isEmpty(nombre)) {
                Toast.makeText(this, "Ingresa un nombre de usuario", Toast.LENGTH_LONG).show();
                return;
            }
            if (!PATRON_USUARIO.matcher(nombre).matches()) {
                Toast.makeText(this, "Nombre de usuario: solo letras (máx 12)", Toast.LENGTH_LONG).show();
                return;
            }

            if (TextUtils.isEmpty(dia) || TextUtils.isEmpty(mes) || TextUtils.isEmpty(anio)) {
                Toast.makeText(this, "Completa tu fecha de nacimiento", Toast.LENGTH_LONG).show();
                return;
            }
            if (!esFechaValida(dia, mes, anio)) {
                Toast.makeText(this, "Fecha de nacimiento inválida", Toast.LENGTH_LONG).show();
                return;
            }

            if (TextUtils.isEmpty(correoLocal)) {
                Toast.makeText(this, "Ingresa tu correo electrónico", Toast.LENGTH_LONG).show();
                return;
            }
            if (!PATRON_LOCAL_CORREO.matcher(correoLocal).matches()) {
                Toast.makeText(this, "El formato del correo es inválido", Toast.LENGTH_LONG).show();
                return;
            }

            String correo = correoLocal + "@" + dominio;
            if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                Toast.makeText(this, "Correo electrónico inválido", Toast.LENGTH_LONG).show();
                return;
            }

            if (TextUtils.isEmpty(celular)) {
                Toast.makeText(this, "Ingresa tu número de celular", Toast.LENGTH_LONG).show();
                return;
            }
            if (!PATRON_TELEFONO.matcher(celular).matches()) {
                Toast.makeText(this, "El celular debe tener 10 dígitos", Toast.LENGTH_LONG).show();
                return;
            }

            if (TextUtils.isEmpty(contrasena)) {
                Toast.makeText(this, "Ingresa una contraseña", Toast.LENGTH_LONG).show();
                return;
            }
            if (!PATRON_PASSWORD.matcher(contrasena).matches()) {
                Toast.makeText(this, "Contraseña: 8-12 caracteres, solo minúsculas y números", Toast.LENGTH_LONG).show();
                return;
            }

            if (TextUtils.isEmpty(confirmarContrasena)) {
                Toast.makeText(this, "Confirma tu contraseña", Toast.LENGTH_LONG).show();
                return;
            }
            if (!contrasena.equals(confirmarContrasena)) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_LONG).show();
                return;
            }

            String fechaNacimiento = dia + "/" + mes + "/" + anio;

            btnRegistrar.setEnabled(false);
            registrarUsuarioFirebase(correo, contrasena, tipoUsuario, nombre, celular, fechaNacimiento, btnRegistrar);
        });

    }

    private void actualizarInfoTipoUsuario(TextView tvInfoTipoUsuario, String tipo) {
        if (TIPO_GUARDIAN.equals(tipo)) {
            tvInfoTipoUsuario.setText("Modo guardián: cuidas y monitoreas a tu explorador.");
            tvInfoTipoUsuario.setVisibility(View.VISIBLE);
        } else if (TIPO_EXPLORADOR.equals(tipo)) {
            tvInfoTipoUsuario.setText("Modo explorador: recibes acompañamiento y protección de tu guardián.");
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
                        Toast.makeText(this, "No se pudo obtener usuario", Toast.LENGTH_LONG).show();
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

                                if (authResult.getUser() != null) {
                                    authResult.getUser().sendEmailVerification();
                                }

                                Toast.makeText(this, "Registro exitoso. Revisa tu correo para verificar tu cuenta.", Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(this, pantalla_login.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnRegistrar.setEnabled(true);
                                String detalle = e.getMessage();
                                if (e instanceof FirebaseFirestoreException) {
                                    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
                                    detalle = "[" + firestoreException.getCode() + "] " + detalle;
                                }
                                showMessage("No se pudo guardar perfil: " + detalle);
                            });
                })
                .addOnFailureListener(e -> {
                    btnRegistrar.setEnabled(true);
                    String detalle = e.getMessage();
                    if (e instanceof FirebaseAuthException) {
                        String code = ((FirebaseAuthException) e).getErrorCode();
                        detalle = "[" + code + "] " + detalle;
                    }
                    showMessage("Error al registrar: " + detalle);
                });
    }

    private void configurarAutoAvanceFecha(EditText etDia, EditText etMes, EditText etAnio) {
        // Validación en tiempo real para el Día
        etDia.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val > 31) s.replace(0, s.length(), "31");
                        else if (val == 0 && s.length() == 2) s.replace(0, s.length(), "01");
                        else if (s.length() == 1 && val > 3 && val <= 9) {
                            s.replace(0, s.length(), "0" + val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (s.length() == 2) {
                    etMes.requestFocus();
                }
            }
        });

        // Validación en tiempo real para el Mes
        etMes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    try {
                        int val = Integer.parseInt(s.toString());
                        if (val > 12) s.replace(0, s.length(), "12");
                        else if (val == 0 && s.length() == 2) s.replace(0, s.length(), "01");
                        else if (s.length() == 1 && val > 1 && val <= 9) {
                            s.replace(0, s.length(), "0" + val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (s.length() == 2) {
                    validarYAjustarDia(etDia, etMes, etAnio);
                    etAnio.requestFocus();
                }
            }
        });

        // Validación en tiempo real para el Año
        etAnio.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 4) {
                    try {
                        int anio = Integer.parseInt(s.toString());
                        int anioActual = Calendar.getInstance().get(Calendar.YEAR);
                        if (anio > anioActual) {
                            s.replace(0, s.length(), String.valueOf(anioActual));
                        } else if (anio < 1900) {
                            etAnio.setError("Mínimo 1900");
                        }
                        validarYAjustarDia(etDia, etMes, etAnio);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
    }

    private void validarYAjustarDia(EditText etDia, EditText etMes, EditText etAnio) {
        String dStr = etDia.getText().toString();
        String mStr = etMes.getText().toString();
        String aStr = etAnio.getText().toString();

        if (dStr.isEmpty() || mStr.isEmpty()) return;

        try {
            int d = Integer.parseInt(dStr);
            int m = Integer.parseInt(mStr);
            int a = aStr.length() == 4 ? Integer.parseInt(aStr) : 2000; // Año default para bisiestos si no hay año

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, a);
            cal.set(Calendar.MONTH, m - 1);
            int maxDia = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            if (d > maxDia) {
                etDia.setText(String.format("%02d", maxDia));
                Toast.makeText(this, "Ajustado a " + maxDia + " días para este mes", Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException ignored) {}
    }

    private void agregarWatcherAutoAvance(EditText actual, int longitudObjetivo, EditText siguiente) {
        // Este método ya no se usa, se reemplazó por la lógica individual de arriba
    }

    private boolean esFechaValida(String diaText, String mesText, String anioText) {
        try {
            int d = Integer.parseInt(diaText);
            int m = Integer.parseInt(mesText);
            int a = Integer.parseInt(anioText);

            Calendar cal = Calendar.getInstance();
            int anioActual = cal.get(Calendar.YEAR);

            if (a < 1900 || a > anioActual) return false;
            if (m < 1 || m > 12) return false;

            // Validar días según el mes
            cal.setLenient(false);
            cal.set(Calendar.YEAR, a);
            cal.set(Calendar.MONTH, m - 1);
            cal.set(Calendar.DAY_OF_MONTH, d);
            cal.getTime(); // Esto lanzará excepción si la fecha es inválida (ej. 30 de febrero)

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}