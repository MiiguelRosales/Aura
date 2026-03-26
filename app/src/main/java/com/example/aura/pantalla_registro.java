package com.example.aura;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

public class pantalla_registro extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";

    @Override
    protected void onCreate(Bundle savedInstanceState) { //llamado cuando se crea por primera vez la actividad
        super.onCreate(savedInstanceState); //llamada a su implementacion
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_registro); //indica a android que debe establecer

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_registro), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //IMAGEN PARA EL FONDO ANIMADO
        final ImageView ivFondoGif = (ImageView) findViewById(R.id.ivFondoGif);

        //BOTON PARA REGRESAR A LA PAGINA PRINCIPAL
        final ImageButton btnRegresar = (ImageButton) findViewById(R.id.imageButtonRegistroIzquierda);

        //AQUI SE CARGA EL FONDO ANIMADO CON GLIDE
        Glide.with(this)
                .load(R.drawable.pantalla_registro) // Tu archivo webp animado
                .into(ivFondoGif);

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
}