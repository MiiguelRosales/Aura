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
import android.view.animation.LinearInterpolator;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import android.view.ViewGroup;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
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
    private Handler botonHandler;
    private Runnable botonRunnable;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityResultLauncher<String> pushPermissionLauncher = registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> { /* Permiso manejado por el sistema */ }
                );
                pushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //IMAGEN PARA EL FONDO ANIMADO
        final ImageView ivFondo = (ImageView) findViewById(R.id.ivFondoGif);
        final View blobTopRight = findViewById(R.id.blobTopRight);
        final View blobBottomLeft = findViewById(R.id.blobBottomLeft);

        //BOTON PARA IR A LA PANTALLA INICIO
        final Button btnInicio = (Button) findViewById(R.id.btnInicio);
        final View glowComenzar = findViewById(R.id.viewGlowComenzar);

        // TEXT SWITCHER PARA LAS FRASES ANIMADAS
        final TextSwitcher textSwitcher = findViewById(R.id.textSwitcherFrase);
        final Typeface fuenteCaveat = ResourcesCompat.getFont(this, R.font.caveat_brush);
        textSwitcher.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                TextView tv = new TextView(pantalla_inicio.this);
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 72f);
                tv.setTextColor(ContextCompat.getColor(pantalla_inicio.this, R.color.inicio_text_primary));
                tv.setTypeface(fuenteCaveat, Typeface.BOLD);
                tv.setGravity(Gravity.CENTER);
                tv.setShadowLayer(10f, 0f, 3f, Color.parseColor("#45000000"));
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
//        Glide.with(this)
//                .load(R.drawable.pantalla_vincular)
//                .into(ivFondo);

        blobTopRight.post(new Runnable() {
            @Override
            public void run() {
                iniciarMovimientoFondoConLimites(
                        blobTopRight,
                        new float[]{0f, -0.95f, 0.95f, -0.92f, 0.92f, 0f},
                        new float[]{0f, 0.92f, -0.94f, 0.96f, -0.90f, 0f},
                        new float[]{0f, 10f, -10f, 10f, -10f, 0f},
                        new float[]{1f, 0.99f, 0.98f, 0.99f, 0.98f, 1f},
                        new float[]{1f, 0.99f, 0.98f, 0.99f, 0.98f, 1f},
                        20000);
            }
        });

        blobBottomLeft.post(new Runnable() {
            @Override
            public void run() {
                iniciarMovimientoFondoConLimites(
                        blobBottomLeft,
                        new float[]{0f, 0.95f, -0.95f, 0.90f, -0.90f, 0f},
                        new float[]{0f, -0.94f, 0.92f, -0.96f, 0.88f, 0f},
                        new float[]{0f, -10f, 10f, -10f, 10f, 0f},
                        new float[]{1f, 0.99f, 0.98f, 0.99f, 0.98f, 1f},
                        new float[]{1f, 0.99f, 0.98f, 0.99f, 0.98f, 1f},
                        22000);
            }
        });


        //EVENTO PARA IR A LA PANTALLA DE LOGIN
        btnInicio.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean guardarLogin = prefs.getBoolean("guardarLogin", false);
                com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
                
                if (guardarLogin && auth.getCurrentUser() != null) {
                    btnInicio.setEnabled(false);
                    btnInicio.setText("Cargando...");
                    
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("usuarios")
                            .document(auth.getCurrentUser().getUid())
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                if (documentSnapshot.exists()) {
                                    String tipoReal = documentSnapshot.getString("tipoUsuario");
                                    Intent intentDestino;
                                    if ("GUARDIAN".equals(tipoReal)) {
                                        String exploradorVinculadoId = documentSnapshot.getString("exploradorVinculadoId");
                                        boolean tieneVinculo = exploradorVinculadoId != null && !exploradorVinculadoId.trim().isEmpty();
                                        intentDestino = tieneVinculo
                                                ? new Intent(pantalla_inicio.this, PantallaGuardian.class)
                                                : new Intent(pantalla_inicio.this, PantallaVincular.class);
                                    } else {
                                        String guardianVinculadoId = documentSnapshot.getString("guardianVinculadoId");
                                        boolean tieneVinculo = guardianVinculadoId != null && !guardianVinculadoId.trim().isEmpty();
                                        intentDestino = tieneVinculo
                                                ? new Intent(pantalla_inicio.this, PantallaJuegos.class)
                                                : new Intent(pantalla_inicio.this, PantallaCompartirCodigo.class);
                                    }
                                    startActivity(intentDestino);
                                    finish();
                                } else {
                                    btnInicio.setEnabled(true);
                                    btnInicio.setText("COMENZAR");
                                    Intent intent = new Intent(pantalla_inicio.this, pantalla_login.class);
                                    startActivity(intent);
                                }
                            })
                            .addOnFailureListener(e -> {
                                btnInicio.setEnabled(true);
                                btnInicio.setText("COMENZAR");
                                Intent intent = new Intent(pantalla_inicio.this, pantalla_login.class);
                                startActivity(intent);
                            });
                } else {
                    Intent intent = new Intent(pantalla_inicio.this, pantalla_login.class);
                    startActivity(intent);
                }
            }
        });

        iniciarPulsoAutomatico(btnInicio, glowComenzar);

    }

    private void iniciarPulsoAutomatico(final View boton, final View brillo) {
        botonHandler = new Handler(Looper.getMainLooper());
        botonRunnable = new Runnable() {
            @Override
            public void run() {
                ObjectAnimator scaleXUp = ObjectAnimator.ofFloat(boton, View.SCALE_X, 1f, 1.06f, 1f);
                ObjectAnimator scaleYUp = ObjectAnimator.ofFloat(boton, View.SCALE_Y, 1f, 1.06f, 1f);
                ObjectAnimator brilloScaleX = ObjectAnimator.ofFloat(brillo, View.SCALE_X, 1f, 1.08f, 1f);
                ObjectAnimator brilloScaleY = ObjectAnimator.ofFloat(brillo, View.SCALE_Y, 1f, 1.08f, 1f);
                ObjectAnimator brilloAlpha = ObjectAnimator.ofFloat(brillo, View.ALPHA, 0.45f, 0.95f, 0.45f);

                scaleXUp.setDuration(650);
                scaleYUp.setDuration(650);
                brilloScaleX.setDuration(650);
                brilloScaleY.setDuration(650);
                brilloAlpha.setDuration(650);

                scaleXUp.start();
                scaleYUp.start();
                brilloScaleX.start();
                brilloScaleY.start();
                brilloAlpha.start();

                botonHandler.postDelayed(this, 2000);
            }
        };
        botonHandler.postDelayed(botonRunnable, 2000);
    }


    private void iniciarMovimientoFondoConLimites(View figura, float[] normalizedTranslationXValues, float[] normalizedTranslationYValues, float[] rotationValues, float[] scaleXValues, float[] scaleYValues, long duration) {
        ViewGroup parent = (ViewGroup) figura.getParent();
        float minTranslationX = -figura.getX();
        float maxTranslationX = parent.getWidth() - figura.getWidth() - figura.getX();
        float minTranslationY = -figura.getY();
        float maxTranslationY = parent.getHeight() - figura.getHeight() - figura.getY();

        ObjectAnimator moveX = ObjectAnimator.ofFloat(figura, View.TRANSLATION_X,
            convertirDesplazamiento(normalizedTranslationXValues[0], minTranslationX, maxTranslationX),
            convertirDesplazamiento(normalizedTranslationXValues[1], minTranslationX, maxTranslationX),
            convertirDesplazamiento(normalizedTranslationXValues[2], minTranslationX, maxTranslationX),
            convertirDesplazamiento(normalizedTranslationXValues[3], minTranslationX, maxTranslationX),
            convertirDesplazamiento(normalizedTranslationXValues[4], minTranslationX, maxTranslationX),
                0f);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(figura, View.TRANSLATION_Y,
            convertirDesplazamiento(normalizedTranslationYValues[0], minTranslationY, maxTranslationY),
            convertirDesplazamiento(normalizedTranslationYValues[1], minTranslationY, maxTranslationY),
            convertirDesplazamiento(normalizedTranslationYValues[2], minTranslationY, maxTranslationY),
            convertirDesplazamiento(normalizedTranslationYValues[3], minTranslationY, maxTranslationY),
            convertirDesplazamiento(normalizedTranslationYValues[4], minTranslationY, maxTranslationY),
                0f);
        ObjectAnimator rotate = ObjectAnimator.ofFloat(figura, View.ROTATION, rotationValues);
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(figura, View.SCALE_X, scaleXValues);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(figura, View.SCALE_Y, scaleYValues);

        moveX.setDuration(duration);
        moveY.setDuration(duration);
        rotate.setDuration(duration);
        scaleXAnim.setDuration(duration);
        scaleYAnim.setDuration(duration);

        moveX.setInterpolator(new LinearInterpolator());
        moveY.setInterpolator(new LinearInterpolator());
        rotate.setInterpolator(new LinearInterpolator());
        scaleXAnim.setInterpolator(new LinearInterpolator());
        scaleYAnim.setInterpolator(new LinearInterpolator());

        moveX.setRepeatCount(ObjectAnimator.INFINITE);
        moveY.setRepeatCount(ObjectAnimator.INFINITE);
        rotate.setRepeatCount(ObjectAnimator.INFINITE);
        scaleXAnim.setRepeatCount(ObjectAnimator.INFINITE);
        scaleYAnim.setRepeatCount(ObjectAnimator.INFINITE);

        moveX.setRepeatMode(ObjectAnimator.REVERSE);
        moveY.setRepeatMode(ObjectAnimator.REVERSE);
        rotate.setRepeatMode(ObjectAnimator.REVERSE);
        scaleXAnim.setRepeatMode(ObjectAnimator.REVERSE);
        scaleYAnim.setRepeatMode(ObjectAnimator.REVERSE);

        moveX.start();
        moveY.start();
        rotate.start();
        scaleXAnim.start();
        scaleYAnim.start();
    }

    private float limitar(float valor, float minimo, float maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }

    private float convertirDesplazamiento(float fraccion, float limiteNegativo, float limitePositivo) {
        float valor = fraccion >= 0f
                ? limitePositivo * fraccion
                : limiteNegativo * Math.abs(fraccion);
        return limitar(valor, limiteNegativo, limitePositivo);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
        if (botonHandler != null && botonRunnable != null) {
            botonHandler.removeCallbacks(botonRunnable);
        }
    }
}
