package com.example.aura;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

public class pantalla_registro extends BaseActivity {

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
        ArrayAdapter<String> adapterTipo = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, tiposUsuario);
        actvTipoUsuario.setAdapter(adapterTipo);
        actvTipoUsuario.setText(tiposUsuario[0], false);

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
}