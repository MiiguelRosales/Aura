package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

public class pantalla_login extends BaseActivity {

    private static final String DEMO_CORREO = "aura@demo.com";
    private static final String DEMO_CONTRASENA = "123456";
    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_login);

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
        MaterialButton btnIniciarSesion = findViewById(R.id.btnIniciarSesion);

        final String[] tiposUsuario = {"🛡️  Guardián", "🌟  Explorador"};
        ArrayAdapter<String> adapterTipo = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, tiposUsuario);
        actvTipoUsuarioLogin.setAdapter(adapterTipo);

        SharedPreferences prefsTipo = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String tipoGuardado = prefsTipo.getString(KEY_TIPO_USUARIO, TIPO_EXPLORADOR);
        if (TIPO_GUARDIAN.equals(tipoGuardado)) {
            actvTipoUsuarioLogin.setText(tiposUsuario[0], false);
        } else {
            actvTipoUsuarioLogin.setText(tiposUsuario[1], false);
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

        btnIniciarSesion.setOnClickListener(v -> {
            String correo = etCorreo.getText().toString().trim();
            String contrasena = etContrasena.getText().toString().trim();

            if (TextUtils.isEmpty(correo) || TextUtils.isEmpty(contrasena)) {
                Toast.makeText(this, "Completa correo y contraseña", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!DEMO_CORREO.equalsIgnoreCase(correo) || !DEMO_CONTRASENA.equals(contrasena)) {
                Toast.makeText(this, "Datos incorrectos", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Inicio de sesión correcto", Toast.LENGTH_SHORT).show();

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String tipoUsuario = prefs.getString(KEY_TIPO_USUARIO, TIPO_EXPLORADOR);

            Intent intentDestino;
            if (TIPO_GUARDIAN.equals(tipoUsuario)) {
                intentDestino = new Intent(pantalla_login.this, PantallaGuardian.class);
            } else {
                intentDestino = new Intent(pantalla_login.this, PantallaJuegos.class);
            }

            startActivity(intentDestino);
            finish();
        });
    }
}