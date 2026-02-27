package com.example.aura;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.appcompat.app.AppCompatActivity;

/**
 * LoadingActivity — pantalla de carga animada (efecto libro con páginas girando)
 * que se muestra al navegar de pantalla_inicio → pantalla_registro.
 *
 * La animación replica el CSS book-loader de Uiverse.io (by Nawsome):
 *   - Duración de ciclo: 3 000 ms (infinito mientras dure la pantalla)
 *   - Cada página hace rotateY 180° → 0° en su ventana de tiempo, con fade-in/out
 *   - Después de LOADING_DURATION ms se navega a pantalla_registro
 */
public class LoadingActivity extends AppCompatActivity {

    /** Duración de UN ciclo de animación en ms (coincide con --duration: 3s del CSS). */
    private static final long CYCLE_MS = 3_000L;

    /** Tiempo total que permanece la pantalla de carga antes de continuar. */
    private static final long LOADING_DURATION = 3_500L;

    // Animadores en curso (para cancelar si la actividad se destruye)
    private ValueAnimator[] animators;

    // Handler para navegar tras LOADING_DURATION
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        // Ocultar barra de estado para pantalla limpia
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Navegar a pantalla_registro después de LOADING_DURATION
        handler.postDelayed(() -> {
            Intent intent = new Intent(LoadingActivity.this, pantalla_registro.class);
            startActivity(intent);
            finish();
        }, LOADING_DURATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Las vistas ya están medidas aquí, podemos establecer el pivote y arrancar
        if (hasFocus) {
            iniciarAnimaciones();
        }
    }

    /**
     * Configura y arranca la animación para cada página.
     *
     * Tiempos (en fracción del ciclo) traducidos del CSS:
     *   page-2: rotateY  0% → 50%  |  fade-in  0% → 20%  |  fade-out 35% → 100%
     *   page-3: rotateY 15% → 65%  |  fade-in 15% → 35%  |  fade-out 50% → 100%
     *   page-4: rotateY 30% → 80%  |  fade-in 30% → 50%  |  fade-out 65% → 100%
     *   page-5: rotateY 45% → 95%  |  fade-in 45% → 65%  |  fade-out 80% → 100%
     */
    private void iniciarAnimaciones() {
        View page2 = findViewById(R.id.page2);
        View page3 = findViewById(R.id.page3);
        View page4 = findViewById(R.id.page4);
        View page5 = findViewById(R.id.page5);

        // Establecer el pivote de rotación en el borde DERECHO de la página
        // (replicando transform-origin: 100% 50% del CSS)
        configurarPivote(page2);
        configurarPivote(page3);
        configurarPivote(page4);
        configurarPivote(page5);

        animators = new ValueAnimator[]{
                crearAnimadorPagina(page2, 0.00f, 0.50f, 0.00f, 0.20f, 0.35f),
                crearAnimadorPagina(page3, 0.15f, 0.65f, 0.15f, 0.35f, 0.50f),
                crearAnimadorPagina(page4, 0.30f, 0.80f, 0.30f, 0.50f, 0.65f),
                crearAnimadorPagina(page5, 0.45f, 0.95f, 0.45f, 0.65f, 0.80f)
        };

        for (ValueAnimator anim : animators) {
            anim.start();
        }
    }

    /**
     * Sitúa el pivote de la vista en su borde derecho, mitad vertical.
     */
    private void configurarPivote(View view) {
        view.setPivotX(view.getWidth());
        view.setPivotY(view.getHeight() / 2f);
    }

    /**
     * Crea un ValueAnimator que modifica rotationY y alpha de {@code page}
     * siguiendo los tiempos del CSS loader.
     *
     * @param page         Vista de la página a animar
     * @param rotStart     Fracción del ciclo donde empieza el giro (0.0 – 1.0)
     * @param rotEnd       Fracción del ciclo donde termina el giro
     * @param fadeInStart  Fracción donde comienza el fade-in
     * @param fadeInEnd    Fracción donde termina el fade-in
     * @param fadeOutStart Fracción donde comienza el fade-out
     */
    private ValueAnimator crearAnimadorPagina(View page,
                                              float rotStart, float rotEnd,
                                              float fadeInStart, float fadeInEnd,
                                              float fadeOutStart) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());

        animator.addUpdateListener(animation -> {
            float f = animation.getAnimatedFraction(); // 0.0 → 1.0

            // ── RotationY: 180° → 0° dentro del rango [rotStart, rotEnd] ──────
            float rotY;
            if (f < rotStart) {
                rotY = 180f;
            } else if (f > rotEnd) {
                rotY = 0f;
            } else {
                float t = (f - rotStart) / (rotEnd - rotStart);
                rotY = 180f * (1f - t);
            }

            // ── Alpha ──────────────────────────────────────────────────────────
            float alpha;
            if (f < fadeInStart) {
                alpha = 0f;
            } else if (f < fadeInEnd) {
                // fade-in lineal
                alpha = (f - fadeInStart) / (fadeInEnd - fadeInStart);
            } else if (f < fadeOutStart) {
                // completamente visible
                alpha = 1f;
            } else {
                // fade-out lineal hasta el final del ciclo
                alpha = 1f - (f - fadeOutStart) / (1f - fadeOutStart);
            }

            page.setRotationY(rotY);
            page.setAlpha(alpha);
        });

        return animator;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancelar animaciones y callbacks al destruir la actividad
        handler.removeCallbacksAndMessages(null);
        if (animators != null) {
            for (ValueAnimator anim : animators) {
                if (anim != null) anim.cancel();
            }
        }
    }
}
