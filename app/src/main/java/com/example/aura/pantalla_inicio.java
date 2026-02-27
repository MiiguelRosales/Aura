package com.example.aura;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

public class pantalla_inicio extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) { //llamado cuando se crea por primera vez la actividad
        super.onCreate(savedInstanceState); //llamada a su implementacion
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_inicio); //indica a android que debe establecer

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //IMAGEN PARA EL FONDO ANIMADO
        final ImageView ivFondo = (ImageView) findViewById(R.id.ivFondoGif);

        //BOTON PARA IR A LA PANTALLA DE REGISTRO
        final Button btnRegistrate = (Button) findViewById(R.id.btnIrPerfectos);

        //AQUI SE CARGA EL FONDO ANIMADO CON GLIDE
        Glide.with(this)
                .load(R.drawable.inicio) // Carga el archivo webp de inicio
                .into(ivFondo);

        //ESTE ES EL EVENTO DEL BOTON PARA IR A LA PANTALLA DE REGISTRO
        btnRegistrate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Pasamos por la pantalla de carga antes de llegar a pantalla_registro
                Intent intent = new Intent(pantalla_inicio.this, LoadingActivity.class);
                startActivity(intent);
            }
        });
    }
}