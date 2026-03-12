package com.example.aura;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

public class PantallaCompartirCodigo extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_compartir_codigo);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_vincular), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // CORRECCIÓN AQUÍ: Usar ImageButton en lugar de Button
        final ImageButton imageButtonRegresar = findViewById(R.id.imageButtonRegresar);

        // EVENTO PARA REGRESAR
        imageButtonRegresar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PantallaCompartirCodigo.this, PantallaJuegos.class);
                startActivity(intent);
            }
        });
    }
}