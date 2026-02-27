package com.example.aura;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

public class pantalla_registro extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Configuración de pantalla completa
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_registro);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_registro), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 2. Cargar el fondo animado con Glide
        ImageView ivFondo = findViewById(R.id.ivFondoGif);
        Glide.with(this)
                .load(R.drawable.registro) // Tu archivo webp animado
                .into(ivFondo);

        // 3. Programar la flecha de regreso a Inicio
        ImageButton btnRegresar = findViewById(R.id.imageButtonRegistroIzquierda);

        btnRegresar.setOnClickListener(v -> {
            // Creamos el intent para ir a la pantalla de inicio
            Intent intent = new Intent(pantalla_registro.this, pantalla_inicio.class);
            startActivity(intent);
            // Cerramos la pantalla actual para que no se acumulen
            finish();
        });
    }
}