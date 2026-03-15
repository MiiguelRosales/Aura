package com.example.aura;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;

public class pantalla_inicio extends AppCompatActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_DARK_MODE = "darkMode";

    private final String[] frases = {"AURA", "ES VIDA", "ES SEGURIDAD", "ES FAMILIA", "ES AMIGO", "ES AURA"};
    private int fraseIndex = 0;
    private Handler handler;
    private Runnable runnable;
    private boolean isDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Aplicar tema guardado ANTES de setContentView
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isDarkMode = prefs.getBoolean(KEY_DARK_MODE, true);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_inicio);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //IMAGEN PARA EL FONDO ANIMADO
        final ImageView ivFondo = (ImageView) findViewById(R.id.ivFondoGif);

        //BOTON PARA IR A LA PANTALLA DE REGISTRO
        final Button btnRegistro = (Button) findViewById(R.id.btnRegistro);

        //BOTON PARA IR A LA PANTALLA INICIO
        final Button btnInicio = (Button) findViewById(R.id.btnInicio);

        //BOTON PARA IR A LA PANTALLA DE VINCULAR
        final Button btnVincular = (Button) findViewById(R.id.btnVincular);

        // TEXT SWITCHER PARA LAS FRASES ANIMADAS
        final TextSwitcher textSwitcher = findViewById(R.id.textSwitcherFrase);
        final Typeface fuenteCaveat = ResourcesCompat.getFont(this, R.font.caveat_brush);
        textSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                TextView tv = new TextView(pantalla_inicio.this);
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 62f);
                tv.setTextColor(Color.WHITE);
                tv.setTypeface(fuenteCaveat, Typeface.BOLD);
                tv.setGravity(Gravity.CENTER);
                tv.setShadowLayer(4f, 22f, 22f, Color.parseColor("#80000000"));
                return tv;
            }
        });
        textSwitcher.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom));
        textSwitcher.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_top));
        textSwitcher.setText(frases[fraseIndex]);

        handler = new Handler(Looper.getMainLooper());
        runnable = new Runnable() {
            @Override
            public void run() {
                fraseIndex = (fraseIndex + 1) % frases.length;
                textSwitcher.setText(frases[fraseIndex]);
                handler.postDelayed(this, 2000);
            }
        };
        handler.postDelayed(runnable, 2000);

        //AQUI SE CARGA EL FONDO ANIMADO CON GLIDE
        Glide.with(this)
                .load(R.drawable.pantalla_vincular)
                .into(ivFondo);

        // TOGGLE DE TEMA (sol / luna)
        final FrameLayout layoutToggle = findViewById(R.id.layoutThemeToggle);
        final ImageView ivThumb = findViewById(R.id.ivThumbToggle);
        actualizarToggleUI(layoutToggle, ivThumb, isDarkMode);

        layoutToggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                isDarkMode = !isDarkMode;
                // Guardar preferencia
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, isDarkMode).apply();
                // Aplicar tema globalmente y recrear
                AppCompatDelegate.setDefaultNightMode(
                        isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                recreate();
            }
        });

        //EVENTO PARA IR A LA PANTALLA DE LOGIN
        btnInicio.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(pantalla_inicio.this, pantalla_login.class);
                startActivity(intent);
            }
        });

        //ESTE ES EL EVENTO DEL BOTON PARA IR A LA PANTALLA DE REGISTRO
        btnRegistro.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(pantalla_inicio.this, pantalla_registro.class);
                startActivity(intent);
            }
        });

        //EVENTO PARA IR A VINCULAR
        btnVincular.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(pantalla_inicio.this, PantallaVincular.class);
                startActivity(intent);
            }
        });
    }

    private void actualizarToggleUI(FrameLayout track, ImageView thumb, boolean dark) {
        float density = getResources().getDisplayMetrics().density;
        if (dark) {
            track.setBackground(getDrawable(R.drawable.toggle_track_night));
            thumb.setImageResource(R.drawable.ic_moon);
            // Thumb a la derecha: 64dp track - 26dp thumb - 4dp margin = 34dp
            ObjectAnimator.ofFloat(thumb, "translationX", 34 * density).setDuration(300).start();
        } else {
            track.setBackground(getDrawable(R.drawable.toggle_track_day));
            thumb.setImageResource(R.drawable.ic_sun);
            ObjectAnimator.ofFloat(thumb, "translationX", 0f).setDuration(300).start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }
}
