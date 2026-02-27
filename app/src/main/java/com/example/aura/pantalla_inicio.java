package com.example.aura;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class pantalla_inicio extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Configuración de pantalla Edge-to-Edge
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_inicio);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 2. Buscamos el ImageView que creamos en el XML
        ImageView ivFondo = findViewById(R.id.ivFondoGif);

        // 3. Cargamos el archivo usando Glide
        Glide.with(this)
                .load(R.drawable.inicio) // Carga el archivo webp de inicio
                .into(ivFondo);

        // 4. Programar el botón REGISTRATE para ir a la pantalla de registro
        com.google.android.material.button.MaterialButton btnRegistrate = findViewById(R.id.btnIrPerfectos);

        btnRegistrate.setOnClickListener(v -> {
            // Creamos el intent para viajar a pantalla_registro
            Intent intent = new Intent(pantalla_inicio.this, pantalla_registro.class);
            startActivity(intent);
        });
    }
}