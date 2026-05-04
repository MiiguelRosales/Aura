package com.example.aura;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import android.util.TypedValue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class PantallaJuegos extends BaseActivity implements View.OnClickListener {

    private static final String CHANNEL_ID = "juego_notificaciones";
    private static final int NOTIFICATION_ID = 1;
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    private static final long INTERVALO_CONTADOR_MS = 1_000L;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> requestLocationPermissionLauncher;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private String exploradorUid;
    private String codigoExplorador;
    private String nombreExplorador;
    private String guardianVinculadoId;
    private boolean accesoJuegosPermitido;

    private Button btnAccionJuegos;
    private TextView tvContadorListo;
    private CountDownTimer contadorListo;
    private boolean listoConfirmado;

    // Referencias para el simulador y el juego
    private View scrollViewJuegos;
    private LinearLayout layoutSimuladorJuego;
    private Button btnSimularGanar;
    private Button btnSimularSalir;
    private Button btnPiedra;
    private Button btnPapel;
    private Button btnTijera;
    private Button btnAdivinar;
    private Button btnPaginaAnterior;
    private Button btnPaginaSiguiente;
    private GridLayout gridPagina1;
    private GridLayout gridPagina2;
    private LinearLayout layoutNavegacionPaginacion;
    private boolean hayPaginaDos = false;
    private Button[][] tableroBotones = new Button[3][3];
    private TextView textoTurnoJuego;
    private TextView textoPPT;
    private TextView textoNumero;
    private TextView tvNivelNumero;
    private TextView tvEleccionPPT;
    private TextView tvPistaNumero;
    private TextView tvResultadoPPT;
    private TextView tvResultadoNumero;
    private EditText edtNumero;
    private LinearLayout layoutJuegoTresEnRaya;
    private LinearLayout layoutJuegoPPT;
    private LinearLayout layoutJuegoNumero;
    private int contadorRondasJuego = 0;
    private int numeroSecreto = 0;
    private int nivelNumero = 1;
    private int limiteNumero = 10;
    private boolean turnoJugador1Juego = true;
    private boolean ticTacToeActivo = false;
    private int juegoSeleccionado = 0; // 1 = TicTacToe, 2 = PPT, 3 = AdivinaNumero

    private static final String PREFS_JUEGO = "JuegoPrefs";
    private static final String KEY_FECHA_GANADO = "fecha_juego_ganado";
    private static final String KEY_LISTO_CONFIRMADO = "listo_confirmado_hoy";
    private static final String KEY_JUEGOS_SELECCIONADOS = "juegos_seleccionados";

    private final List<JuegoDisponible> juegosDisponibles = Arrays.asList(
            new JuegoDisponible("tres_en_raya", "Tres en raya", R.drawable.tresenraya, 1),
            new JuegoDisponible("piedra_papel_tijera", "Piedra, papel o tijera", R.drawable.piedrapapelotijera, 2),
            new JuegoDisponible("adivina_numero", "Adivina el numero", R.drawable.encuentranumero, 3)
    );

    private static final class JuegoDisponible {
        final String id;
        final String titulo;
        final int imagenRes;
        final int tipoJuego;

        JuegoDisponible(String id, String titulo, int imagenRes, int tipoJuego) {
            this.id = id;
            this.titulo = titulo;
            this.imagenRes = imagenRes;
            this.tipoJuego = tipoJuego;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_juegos);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_juegos), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Registrar token FCM cada vez que se entra por seguridad
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (auth.getCurrentUser() != null) {
                firestore.collection("usuarios").document(auth.getCurrentUser().getUid())
                        .update("fcmToken", token);
            }
        });

        configurarSolicitudUbicacion();
        configurarCallbackUbicacion();

        createNotificationChannel();

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        mostrarNotificacionJuego();
                    } else {
                        Toast.makeText(this, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestLocationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        iniciarPublicacionUbicacion();
                    }
                }
        );

        solicitarPermisoUbicacionSiHaceFalta();
        prepararPerfilExplorador();


        LinearLayout navPerfil = findViewById(R.id.navPerfil);
        navPerfil.setOnClickListener(v -> {
            startActivity(new Intent(PantallaJuegos.this, PantallaPerfil.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navConfiguraciones = findViewById(R.id.navConfiguraciones);
        navConfiguraciones.setOnClickListener(v -> {
            startActivity(new Intent(PantallaJuegos.this, PantallaAjustes.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navChat = findViewById(R.id.navChat);
        navChat.setOnClickListener(v -> {
            startActivity(new Intent(PantallaJuegos.this, PantallaChat.class));
            overridePendingTransition(0, 0);
            finish();
        });

        btnAccionJuegos = findViewById(R.id.btnAccionJuegos);
        tvContadorListo = findViewById(R.id.tvContadorListo);

        scrollViewJuegos = findViewById(R.id.scrollViewJuegos);
        layoutSimuladorJuego = findViewById(R.id.layoutSimuladorJuego);
        btnSimularGanar = findViewById(R.id.btnSimularGanar);
        btnSimularSalir = findViewById(R.id.btnSimularSalir);
        btnPiedra = findViewById(R.id.btnPiedra);
        btnPapel = findViewById(R.id.btnPapel);
        btnTijera = findViewById(R.id.btnTijera);
        btnAdivinar = findViewById(R.id.btnAdivinar);
        textoPPT = findViewById(R.id.textoPPT);
        textoNumero = findViewById(R.id.textoNumero);
        tvNivelNumero = findViewById(R.id.tvNivelNumero);
        tvEleccionPPT = findViewById(R.id.tvEleccionPPT);
        tvPistaNumero = findViewById(R.id.tvPistaNumero);
        tvResultadoPPT = findViewById(R.id.tvResultadoPPT);
        tvResultadoNumero = findViewById(R.id.tvResultadoNumero);
        edtNumero = findViewById(R.id.edtNumero);
        layoutJuegoTresEnRaya = findViewById(R.id.layoutJuegoTresEnRaya);
        layoutJuegoPPT = findViewById(R.id.layoutJuegoPPT);
        layoutJuegoNumero = findViewById(R.id.layoutJuegoNumero);
        btnPaginaAnterior = findViewById(R.id.btnPaginaAnterior);
        btnPaginaSiguiente = findViewById(R.id.btnPaginaSiguiente);
        gridPagina1 = findViewById(R.id.gridPagina1);
        gridPagina2 = findViewById(R.id.gridPagina2);
        layoutNavegacionPaginacion = findViewById(R.id.layoutNavegacionPaginacion);

        inicializarTableroJuego();
        renderizarJuegosSeleccionados();

        btnAccionJuegos.setOnClickListener(v -> confirmarListo());

        // Las cards de juegos se generan dinámicamente; no usar referencias estáticas aquí.

        btnPiedra.setOnClickListener(v -> jugarPPT("Piedra"));
        btnPapel.setOnClickListener(v -> jugarPPT("Papel"));
        btnTijera.setOnClickListener(v -> jugarPPT("Tijera"));
        btnAdivinar.setOnClickListener(v -> jugarAdivinaNumero());

        btnPaginaAnterior.setOnClickListener(v -> mostrarPagina(1));
        btnPaginaSiguiente.setOnClickListener(v -> mostrarPagina(2));

        btnSimularGanar.setOnClickListener(v -> {
            if (juegoSeleccionado == 2) {
                reiniciarJuegoPPT();
            } else if (juegoSeleccionado == 3) {
                reiniciarJuegoAdivinaNumero();
            } else {
                reiniciarJuegoTicTacToe();
            }
        });

        btnSimularSalir.setOnClickListener(v -> {
            scrollViewJuegos.setVisibility(View.VISIBLE);
            layoutSimuladorJuego.setVisibility(View.GONE);
            juegoSeleccionado = 0;
        });

        mostrarPagina(1);
    }

    private void mostrarPagina(int pagina) {
        if (!hayPaginaDos) {
            gridPagina1.setVisibility(View.VISIBLE);
            if (gridPagina2 != null) {
                gridPagina2.setVisibility(View.GONE);
            }
            btnPaginaAnterior.setEnabled(false);
            btnPaginaAnterior.setAlpha(0.35f);
            btnPaginaSiguiente.setEnabled(false);
            btnPaginaSiguiente.setAlpha(0.35f);
            return;
        }

        if (pagina == 1) {
            gridPagina1.setVisibility(View.VISIBLE);
            if (gridPagina2 != null) {
                gridPagina2.setVisibility(View.GONE);
            }
            btnPaginaAnterior.setEnabled(false);
            btnPaginaAnterior.setAlpha(0.35f);
            btnPaginaSiguiente.setEnabled(true);
            btnPaginaSiguiente.setAlpha(1f);
        } else {
            gridPagina1.setVisibility(View.GONE);
            if (gridPagina2 != null) {
                gridPagina2.setVisibility(View.VISIBLE);
            }
            btnPaginaAnterior.setEnabled(true);
            btnPaginaAnterior.setAlpha(1f);
            btnPaginaSiguiente.setEnabled(false);
            btnPaginaSiguiente.setAlpha(0.35f);
        }
    }

    private void renderizarJuegosSeleccionados() {
        if (gridPagina1 == null) {
            return;
        }

        List<String> seleccionados = obtenerJuegosSeleccionados();
        gridPagina1.removeAllViews();
        if (gridPagina2 != null) {
            gridPagina2.removeAllViews();
        }

        gridPagina1.setColumnCount(2);
        gridPagina1.setColumnOrderPreserved(false);
        gridPagina1.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        if (gridPagina2 != null) {
            gridPagina2.setColumnCount(2);
            gridPagina2.setColumnOrderPreserved(false);
            gridPagina2.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        }

        List<View> cardsRender = new ArrayList<>();

        for (String juegoId : seleccionados) {
            JuegoDisponible juego = buscarJuegoPorId(juegoId);
            if (juego != null) {
                cardsRender.add(crearCardJuego(juego));
            }
        }

        cardsRender.add(crearCardAgregarJuego(seleccionados.size() < juegosDisponibles.size()));

        int elementosVisibles = cardsRender.size();
        hayPaginaDos = elementosVisibles > 8;

        // Siempre reservar 4 filas x 2 columnas = 8 slots por página
        gridPagina1.setRowCount(4);
        if (gridPagina2 != null) {
            gridPagina2.setRowCount(4);
        }

        // Añadir tarjetas reales y luego placeholders invisibles para mantener la grilla 4x2
        int addedPage1 = 0;
        int addedPage2 = 0;
        for (int i = 0; i < cardsRender.size(); i++) {
            if (i < 8) {
                View card = cardsRender.get(i);
                aplicarPosicionGrid(card, i);
                gridPagina1.addView(card);
                addedPage1++;
            } else if (gridPagina2 != null) {
                View card = cardsRender.get(i);
                aplicarPosicionGrid(card, i - 8);
                gridPagina2.addView(card);
                addedPage2++;
            }
        }

        // Rellenar con placeholders invisibles para que la grilla mantenga 8 slots
        for (int i = addedPage1; i < 8; i++) {
            View placeholder = new View(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dpToPx(100);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            placeholder.setLayoutParams(params);
            placeholder.setVisibility(View.INVISIBLE);
            aplicarPosicionGrid(placeholder, i);
            gridPagina1.addView(placeholder);
        }

        if (gridPagina2 != null) {
            for (int i = addedPage2; i < 8; i++) {
                View placeholder = new View(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 0;
                params.height = dpToPx(100);
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
                placeholder.setLayoutParams(params);
                placeholder.setVisibility(View.INVISIBLE);
                aplicarPosicionGrid(placeholder, i);
                gridPagina2.addView(placeholder);
            }
        }

        if (layoutNavegacionPaginacion != null) {
            layoutNavegacionPaginacion.setVisibility(View.VISIBLE);
        }
        mostrarPagina(1);
    }

    private List<String> obtenerJuegosSeleccionados() {
        String guardado = obtenerPrefsJuegos().getString(KEY_JUEGOS_SELECCIONADOS, "");
        List<String> seleccionados = new ArrayList<>();
        boolean huboLimpieza = false;

        if (guardado == null || guardado.trim().isEmpty()) {
            return seleccionados;
        }

        for (String item : guardado.split(",")) {
            String id = item.trim();
            if (!id.isEmpty() && buscarJuegoPorId(id) != null && !seleccionados.contains(id)) {
                seleccionados.add(id);
            } else if (!id.isEmpty()) {
                huboLimpieza = true;
            }
        }

        if (huboLimpieza) {
            guardarJuegosSeleccionados(seleccionados);
        }

        return seleccionados;
    }

    private void guardarJuegosSeleccionados(List<String> seleccionados) {
        obtenerPrefsJuegos().edit()
                .putString(KEY_JUEGOS_SELECCIONADOS, String.join(",", seleccionados))
                .apply();
    }

    private JuegoDisponible buscarJuegoPorId(String juegoId) {
        for (JuegoDisponible juego : juegosDisponibles) {
            if (juego.id.equals(juegoId)) {
                return juego;
            }
        }
        return null;
    }

    private com.google.android.material.card.MaterialCardView crearCardJuego(JuegoDisponible juego) {
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(110);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        card.setLayoutParams(params);
        card.setClickable(true);
        card.setFocusable(true);
        card.setCardBackgroundColor(getResources().getColor(R.color.login_dropdown_field_bg));
        card.setRadius(dpToPx(12));
        card.setCardElevation(dpToPx(4));
        card.setStrokeColor(getResources().getColor(R.color.login_card_stroke));
        card.setStrokeWidth(dpToPx(1));

        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView imagen = new ImageView(this);
        imagen.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagen.setImageResource(juego.imagenRes);

        TextView titulo = new TextView(this);
        FrameLayout.LayoutParams tituloParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        titulo.setLayoutParams(tituloParams);
        titulo.setBackgroundColor(0x66000000);
        titulo.setGravity(android.view.Gravity.CENTER);
        titulo.setPadding(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(3));
        titulo.setText(juego.titulo);
        titulo.setTextColor(getResources().getColor(R.color.white));
        titulo.setTextSize(11f);
        titulo.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));

        TextView btnEliminar = new TextView(this);
        FrameLayout.LayoutParams eliminarParams = new FrameLayout.LayoutParams(
                dpToPx(20),
                dpToPx(20),
                android.view.Gravity.TOP | android.view.Gravity.END);
        eliminarParams.topMargin = dpToPx(6);
        eliminarParams.rightMargin = dpToPx(6);
        btnEliminar.setLayoutParams(eliminarParams);
        btnEliminar.setText("✕");
        btnEliminar.setGravity(android.view.Gravity.CENTER);
        btnEliminar.setTextSize(14f);
        btnEliminar.setTextColor(0xFFFFFFFF);
        btnEliminar.setBackgroundColor(0xFFE53935);
        btnEliminar.setPadding(0, 0, 0, 0);
        btnEliminar.setOnClickListener(v -> eliminarJuegoSeleccionado(juego));

        frame.addView(imagen);
        frame.addView(titulo);
        frame.addView(btnEliminar);
        card.addView(frame);
        card.setOnClickListener(v -> abrirJuego(juego.tipoJuego));
        return card;
    }

    private com.google.android.material.card.MaterialCardView crearCardAgregarJuego(boolean habilitada) {
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dpToPx(110);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        card.setLayoutParams(params);
        card.setClickable(true);
        card.setFocusable(true);
        card.setCardBackgroundColor(getResources().getColor(R.color.login_dropdown_field_bg));
        card.setRadius(dpToPx(12));
        card.setCardElevation(dpToPx(4));
        card.setStrokeColor(getResources().getColor(R.color.login_card_stroke));
        card.setStrokeWidth(dpToPx(1));

        LinearLayout content = new LinearLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));
        content.setGravity(android.view.Gravity.CENTER);
        content.setOrientation(LinearLayout.VERTICAL);

        ImageView icono = new ImageView(this);
        icono.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)));
        icono.setImageResource(android.R.drawable.ic_input_add);
        icono.setColorFilter(getResources().getColor(habilitada ? R.color.aura_text_hint : R.color.login_btn_stroke));

        TextView texto = new TextView(this);
        LinearLayout.LayoutParams textoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        textoParams.topMargin = dpToPx(4);
        texto.setLayoutParams(textoParams);
        texto.setText(habilitada ? "Agregar" : "Próximamente");
        texto.setTextColor(getResources().getColor(habilitada ? R.color.aura_text_hint : R.color.login_btn_stroke));
        texto.setTextSize(12f);
        texto.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));

        content.addView(icono);
        content.addView(texto);
        card.addView(content);
        card.setEnabled(habilitada);
        card.setAlpha(habilitada ? 1f : 0.65f);
        card.setOnClickListener(v -> {
            if (habilitada) {
                mostrarDialogoAgregarJuego();
            } else {
                Toast.makeText(this, "Ya no hay mas juegos disponibles", Toast.LENGTH_SHORT).show();
            }
        });
        return card;
    }

    private void mostrarDialogoAgregarJuego() {
        List<String> seleccionados = obtenerJuegosSeleccionados();
        List<JuegoDisponible> disponibles = new ArrayList<>();

        for (JuegoDisponible juego : juegosDisponibles) {
            if (!seleccionados.contains(juego.id)) {
                disponibles.add(juego);
            }
        }

        if (disponibles.isEmpty()) {
            Toast.makeText(this, "Ya agregaste todos los juegos disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] opciones = new String[disponibles.size()];
        for (int i = 0; i < disponibles.size(); i++) {
            opciones[i] = disponibles.get(i).titulo;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Agregar juego")
                .setItems(opciones, (dialog, which) -> {
                    JuegoDisponible juegoSeleccionado = disponibles.get(which);
                    List<String> actualizados = obtenerJuegosSeleccionados();
                    actualizados.add(juegoSeleccionado.id);
                    guardarJuegosSeleccionados(actualizados);
                    renderizarJuegosSeleccionados();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminarJuegoSeleccionado(JuegoDisponible juego) {
        List<String> seleccionados = obtenerJuegosSeleccionados();
        if (!seleccionados.remove(juego.id)) {
            return;
        }
        guardarJuegosSeleccionados(seleccionados);
        renderizarJuegosSeleccionados();
        Toast.makeText(this, "Juego eliminado", Toast.LENGTH_SHORT).show();
    }

    private void abrirJuego(int tipoJuego) {
        switch (tipoJuego) {
            case 1:
                reiniciarJuegoTicTacToe();
                break;
            case 2:
                reiniciarJuegoPPT();
                break;
            case 3:
                reiniciarJuegoAdivinaNumero();
                break;
            default:
                return;
        }

        mostrarJuego(tipoJuego);
        scrollViewJuegos.setVisibility(View.GONE);
        layoutSimuladorJuego.setVisibility(View.VISIBLE);
    }

    private void aplicarPosicionGrid(View card, int indice) {
        if (!(card.getLayoutParams() instanceof GridLayout.LayoutParams)) {
            return;
        }

        GridLayout.LayoutParams params = (GridLayout.LayoutParams) card.getLayoutParams();
        int columna = indice % 2;
        int fila = indice / 2;
        params.columnSpec = GridLayout.spec(columna, 1, 1f);
        params.rowSpec = GridLayout.spec(fila, 1);
        card.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String obtenerFechaActualStr() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());
    }

    private String obtenerPrefsJuegosNombre() {
        FirebaseUser user = auth.getCurrentUser();
        String uid = user != null ? user.getUid() : exploradorUid;
        return uid == null ? PREFS_JUEGO : PREFS_JUEGO + "_" + uid;
    }

    private SharedPreferences obtenerPrefsJuegos() {
        return getSharedPreferences(obtenerPrefsJuegosNombre(), MODE_PRIVATE);
    }

    private void marcarJuegoGanadoHoy() {
        obtenerPrefsJuegos().edit()
                .putString(KEY_FECHA_GANADO, obtenerFechaActualStr())
                .apply();
    }

    private boolean fueJuegoGanadoHoy() {
        String fechaGanado = obtenerPrefsJuegos()
                .getString(KEY_FECHA_GANADO, "");
        return obtenerFechaActualStr().equals(fechaGanado);
    }

    private void habilitarBotonListo() {
        if (!listoConfirmado) {
            btnAccionJuegos.setEnabled(true);
            btnAccionJuegos.setClickable(true);
            btnAccionJuegos.setAlpha(1.0f);
        }
    }

    private void confirmarListo() {
        if (listoConfirmado) return;

        listoConfirmado = true;
        // Guardar que ya se confirmó hoy
        obtenerPrefsJuegos().edit()
                .putString(KEY_LISTO_CONFIRMADO, obtenerFechaActualStr())
                .apply();

        // IMPORTANTE: NO detenemos el contador, sigue hasta medianoche
        btnAccionJuegos.setEnabled(false);
        btnAccionJuegos.setClickable(false);
        btnAccionJuegos.setAlpha(0.5f);

        if (tvContadorListo != null) {
            tvContadorListo.setText("Ya hiciste la misión del día");
            tvContadorListo.setTextColor(ContextCompat.getColor(this, R.color.color_mision_completada));
            tvContadorListo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        }

        cancelarAlarmaMedianoche();
        enviarMensajeAlGuardian("TODO BIEN 👌🏻", "listo_confirmado");

        verificarYMostrarNotificacion();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Notificaciones de Juego";
            String description = "Notificaciones para el inicio de juegos";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void verificarYMostrarNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                mostrarNotificacionJuego();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            mostrarNotificacionJuego();
        }
    }

    private void mostrarNotificacionJuego() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Confirmación enviada")
                .setContentText("Todo bien 👌🏻")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

    }

    private void inicializarTableroJuego() {
        textoTurnoJuego = findViewById(R.id.textoTurno);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String buttonID = "btn" + i + j;
                int resID = getResources().getIdentifier(buttonID, "id", getPackageName());
                tableroBotones[i][j] = findViewById(resID);
                tableroBotones[i][j].setOnClickListener(this);
            }
        }
        reiniciarJuegoTicTacToe();
    }

    @Override
    public void onClick(View v) {
        if (!(v instanceof Button) || !ticTacToeActivo || juegoSeleccionado != 1 || !turnoJugador1Juego) {
            return;
        }

        Button botonTocado = (Button) v;
        if (!botonTocado.getText().toString().equals("")) {
            return;
        }

        botonTocado.setText("X");
        contadorRondasJuego++;

        if (verificarGanadorJuego()) {
            textoTurnoJuego.setText("¡Gana la X!");
            bloquearTableroJuego();
            procesarVictoria(true);
            return;
        }

        if (contadorRondasJuego == 9) {
            textoTurnoJuego.setText("¡Empate!");
            ticTacToeActivo = false;
            return;
        }

        turnoJugador1Juego = false;
        realizarMovimientoIA();
    }

    private boolean verificarGanadorJuego() {
        String[][] campo = new String[3][3];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                campo[i][j] = tableroBotones[i][j].getText().toString();
            }
        }

        for (int i = 0; i < 3; i++) {
            if (campo[i][0].equals(campo[i][1]) && campo[i][0].equals(campo[i][2]) && !campo[i][0].equals("")) {
                return true;
            }
            if (campo[0][i].equals(campo[1][i]) && campo[0][i].equals(campo[2][i]) && !campo[0][i].equals("")) {
                return true;
            }
        }

        if (campo[0][0].equals(campo[1][1]) && campo[0][0].equals(campo[2][2]) && !campo[0][0].equals("")) {
            return true;
        }
        if (campo[0][2].equals(campo[1][1]) && campo[0][2].equals(campo[2][0]) && !campo[0][2].equals("")) {
            return true;
        }

        return false;
    }

    private void bloquearTableroJuego() {
        ticTacToeActivo = false;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tableroBotones[i][j].setEnabled(false);
            }
        }
    }

    private void realizarMovimientoIA() {
        if (!ticTacToeActivo) {
            return;
        }

        int[] movimiento = encontrarMejorMovimiento();
        if (movimiento == null) {
            textoTurnoJuego.setText("¡Empate!");
            ticTacToeActivo = false;
            return;
        }

        Button botonIA = tableroBotones[movimiento[0]][movimiento[1]];
        botonIA.setText("O");
        botonIA.setEnabled(false);
        contadorRondasJuego++;

        if (verificarGanadorJuego()) {
            textoTurnoJuego.setText("¡Gana la O!");
            bloquearTableroJuego();
            procesarVictoria(false);
            return;
        }

        if (contadorRondasJuego == 9) {
            textoTurnoJuego.setText("¡Empate!");
            ticTacToeActivo = false;
            return;
        }

        turnoJugador1Juego = true;
        textoTurnoJuego.setText("Turno de X");
    }

    private int[] encontrarMejorMovimiento() {
        int[] mejor = buscarMovimientoGanador("O");
        if (mejor != null) {
            return mejor;
        }
        int[] bloquear = buscarMovimientoGanador("X");
        if (bloquear != null) {
            return bloquear;
        }
        if (tableroBotones[1][1].getText().toString().equals("")) {
            return new int[]{1, 1};
        }
        int[][] esquinas = {{0,0},{0,2},{2,0},{2,2}};
        for (int[] esquina : esquinas) {
            if (tableroBotones[esquina[0]][esquina[1]].getText().toString().equals("")) {
                return esquina;
            }
        }
        int[][] lados = {{0,1},{1,0},{1,2},{2,1}};
        for (int[] lado : lados) {
            if (tableroBotones[lado[0]][lado[1]].getText().toString().equals("")) {
                return lado;
            }
        }
        return null;
    }

    private int[] buscarMovimientoGanador(String simbolo) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (tableroBotones[i][j].getText().toString().equals("")) {
                    tableroBotones[i][j].setText(simbolo);
                    boolean puedeGanar = verificarGanadorJuego();
                    tableroBotones[i][j].setText("");
                    if (puedeGanar) {
                        return new int[]{i, j};
                    }
                }
            }
        }
        return null;
    }

    private void reiniciarJuegoTicTacToe() {
        juegoSeleccionado = 1;
        contadorRondasJuego = 0;
        turnoJugador1Juego = true;
        ticTacToeActivo = true;
        if (textoTurnoJuego != null) {
            textoTurnoJuego.setText("Turno de X");
        }

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tableroBotones[i][j].setText("");
                tableroBotones[i][j].setEnabled(true);
            }
        }
    }

    private void mostrarJuego(int tipo) {
        juegoSeleccionado = tipo;
        if (layoutJuegoTresEnRaya != null && layoutJuegoPPT != null && layoutJuegoNumero != null) {
            layoutJuegoTresEnRaya.setVisibility(tipo == 1 ? View.VISIBLE : View.GONE);
            layoutJuegoPPT.setVisibility(tipo == 2 ? View.VISIBLE : View.GONE);
            layoutJuegoNumero.setVisibility(tipo == 3 ? View.VISIBLE : View.GONE);
        }
    }

    private void reiniciarJuegoPPT() {
        juegoSeleccionado = 2;
        if (textoPPT != null) {
            textoPPT.setText("Elige piedra, papel o tijera");
        }
        if (tvEleccionPPT != null) {
            tvEleccionPPT.setText("IA aún no ha jugado");
        }
        if (tvResultadoPPT != null) {
            tvResultadoPPT.setText("");
        }
        if (layoutJuegoTresEnRaya != null && layoutJuegoPPT != null && layoutJuegoNumero != null) {
            layoutJuegoTresEnRaya.setVisibility(View.GONE);
            layoutJuegoPPT.setVisibility(View.VISIBLE);
            layoutJuegoNumero.setVisibility(View.GONE);
        }
    }

    private void reiniciarJuegoAdivinaNumero() {
        juegoSeleccionado = 3;
        nivelNumero = 1;
        limiteNumero = 10;
        numeroSecreto = new Random().nextInt(limiteNumero) + 1;
        if (textoNumero != null) {
            textoNumero.setText("Adivina el número");
        }
        if (tvNivelNumero != null) {
            tvNivelNumero.setText("Nivel 1: 1 - 10");
        }
        if (tvPistaNumero != null) {
            tvPistaNumero.setText("");
        }
        if (tvResultadoNumero != null) {
            tvResultadoNumero.setText("");
        }
        if (edtNumero != null) {
            edtNumero.setText("");
        }
        if (layoutJuegoTresEnRaya != null && layoutJuegoPPT != null && layoutJuegoNumero != null) {
            layoutJuegoTresEnRaya.setVisibility(View.GONE);
            layoutJuegoPPT.setVisibility(View.GONE);
            layoutJuegoNumero.setVisibility(View.VISIBLE);
        }
    }

    private void jugarAdivinaNumero() {
        if (juegoSeleccionado != 3 || edtNumero == null || tvPistaNumero == null || tvResultadoNumero == null) {
            return;
        }

        String entrada = edtNumero.getText().toString().trim();
        if (entrada.isEmpty()) {
            Toast.makeText(this, "Ingresa un número válido", Toast.LENGTH_SHORT).show();
            return;
        }

        int valor;
        try {
            valor = Integer.parseInt(entrada);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Ingresa un número válido", Toast.LENGTH_SHORT).show();
            return;
        }

        if (valor < 1 || valor > limiteNumero) {
            tvPistaNumero.setText("El número está entre 1 y " + limiteNumero);
            return;
        }

        if (valor == numeroSecreto) {
            if (nivelNumero < 3) {
                nivelNumero++;
                limiteNumero = nivelNumero == 2 ? 50 : 100;
                numeroSecreto = new Random().nextInt(limiteNumero) + 1;
                tvResultadoNumero.setText("¡Bien! Avanzas al nivel " + nivelNumero + "\nAhora prueba con 1 - " + limiteNumero);
                tvPistaNumero.setText("");
                if (tvNivelNumero != null) {
                    tvNivelNumero.setText("Nivel " + nivelNumero + ": 1 - " + limiteNumero);
                }
                edtNumero.setText("");
            } else {
                tvResultadoNumero.setText("¡Felicidades! Ganaste el juego de Adivina el número");
                tvPistaNumero.setText("");
                marcarJuegoGanadoHoy();
                habilitarBotonListo();
            }
            return;
        }

        if (valor < numeroSecreto) {
            tvPistaNumero.setText("El número es mayor");
        } else {
            tvPistaNumero.setText("El número es menor");
        }
        tvResultadoNumero.setText("");
    }

    private void jugarPPT(String eleccionHumano) {
        if (juegoSeleccionado != 2 || tvEleccionPPT == null || tvResultadoPPT == null) {
            return;
        }

        String[] opciones = {"Piedra", "Papel", "Tijera"};
        String eleccionIA = opciones[new Random().nextInt(opciones.length)];

        tvEleccionPPT.setText("Tú: " + eleccionHumano + "  IA: " + eleccionIA);

        String resultado = calcularResultadoPPT(eleccionHumano, eleccionIA);
        tvResultadoPPT.setText(resultado);

        if ("Ganaste".equals(resultado)) {
            marcarJuegoGanadoHoy();
            habilitarBotonListo();
            Toast.makeText(this, "¡Ganaste! Botón Listo desbloqueado", Toast.LENGTH_SHORT).show();
        } else if ("Perdiste".equals(resultado)) {
            Toast.makeText(this, "La IA ganó. Intenta de nuevo para desbloquear Listo", Toast.LENGTH_SHORT).show();
        }
    }

    private String calcularResultadoPPT(String humano, String ia) {
        if (humano.equals(ia)) {
            return "¡Empate!";
        }
        if ((humano.equals("Piedra") && ia.equals("Tijera")) ||
            (humano.equals("Papel") && ia.equals("Piedra")) ||
            (humano.equals("Tijera") && ia.equals("Papel"))) {
            return "Ganaste";
        }
        return "Perdiste";
    }

    private void procesarVictoria(boolean xGana) {
        if (xGana) {
            marcarJuegoGanadoHoy();
            habilitarBotonListo();
            Toast.makeText(this, "¡Ganaste! Botón Listo desbloqueado", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "La IA ganó. Intenta de nuevo para desbloquear Listo", Toast.LENGTH_SHORT).show();
        }
    }

    private void iniciarContadorListo() {
        detenerContadorListo();

        // Recuperar estado del día actual
        String fechaConfirmado = obtenerPrefsJuegos()
                .getString(KEY_LISTO_CONFIRMADO, "");
        listoConfirmado = obtenerFechaActualStr().equals(fechaConfirmado);

        // Configurar botón según el estado
        if (btnAccionJuegos != null) {
            if (fueJuegoGanadoHoy() && !listoConfirmado) {
                habilitarBotonListo();
            } else {
                btnAccionJuegos.setEnabled(false);
                btnAccionJuegos.setClickable(false);
                btnAccionJuegos.setAlpha(0.5f);
            }
        }

        // Calcular tiempo hasta la próxima medianoche
        java.util.Calendar c = java.util.Calendar.getInstance();
        long now = c.getTimeInMillis();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        long midnight = c.getTimeInMillis();
        long diff = midnight - now;

        contadorListo = new CountDownTimer(diff, INTERVALO_CONTADOR_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvContadorListo != null) {
                    if (listoConfirmado) {
                        tvContadorListo.setText("Ya hiciste la misión del día");
                        tvContadorListo.setTextColor(ContextCompat.getColor(PantallaJuegos.this, R.color.color_mision_completada));
                        tvContadorListo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    } else {
                        long totalSegundos = millisUntilFinished / 1000;
                        long horas = totalSegundos / 3600;
                        long minutos = (totalSegundos % 3600) / 60;
                        long segundos = totalSegundos % 60;

                        tvContadorListo.setText(String.format(Locale.getDefault(),
                                "%02d:%02d:%02d", horas, minutos, segundos));
                        tvContadorListo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);

                        if (horas >= 10) {
                            tvContadorListo.setTextColor(android.graphics.Color.parseColor("#00E676"));
                        } else if (horas >= 5) {
                            tvContadorListo.setTextColor(android.graphics.Color.parseColor("#FF9800"));
                        } else {
                            tvContadorListo.setTextColor(android.graphics.Color.parseColor("#F44336"));
                        }
                    }
                }
            }

            @Override
            public void onFinish() {
                if (tvContadorListo != null) {
                    tvContadorListo.setText("00:00:00");
                    tvContadorListo.setTextColor(android.graphics.Color.parseColor("#F44336"));
                }

                // Lógica principal: Solo enviar alerta si NO ganaron el juego hoy
                if (!fueJuegoGanadoHoy()) {
                    enviarMensajeAlGuardian(
                            "ES IMPORTANTE QUE TE COMUNIQUES CON TU EXPLORADOR",
                            "alerta_timeout_critico"
                    );
                }

                // Limpiar variables para el nuevo día
                obtenerPrefsJuegos().edit()
                        .remove(KEY_FECHA_GANADO)
                        .remove(KEY_LISTO_CONFIRMADO)
                        .apply();
                listoConfirmado = false;

                // Bloquear botón nuevamente
                if (btnAccionJuegos != null) {
                    btnAccionJuegos.setEnabled(false);
                    btnAccionJuegos.setClickable(false);
                    btnAccionJuegos.setAlpha(0.5f);
                }

                // Reiniciar el contador para el siguiente día (24h)
                iniciarContadorListo();
            }
        };

        contadorListo.start();

        // Programar la alarma física por si cierran la app
        if (!listoConfirmado) {
            programarAlarmaMedianoche(diff);
        }
    }

    private void programarAlarmaMedianoche(long diffMs) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerTime = System.currentTimeMillis() + diffMs;

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        }
    }

    private void cancelarAlarmaMedianoche() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }

    private void detenerContadorListo() {
        if (contadorListo != null) {
            contadorListo.cancel();
            contadorListo = null;
        }
    }

    private void configurarSolicitudUbicacion() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
                .setMinUpdateIntervalMillis(5_000)
                .build();
    }

    private void configurarCallbackUbicacion() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    publicarUbicacionExplorador(location);
                }
            }
        };
    }

    private void solicitarPermisoUbicacionSiHaceFalta() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarPublicacionUbicacion();
            return;
        }

        requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void iniciarPublicacionUbicacion() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                publicarUbicacionExplorador(location);
            }
        });
    }

    private void detenerPublicacionUbicacion() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void publicarUbicacionExplorador(Location location) {
        if (exploradorUid == null) {
            return;
        }

        Map<String, Object> ubicacion = new HashMap<>();
        ubicacion.put("exploradorId", exploradorUid);
        if (codigoExplorador != null) {
            ubicacion.put("codigo", codigoExplorador);
        }
        ubicacion.put("lat", location.getLatitude());
        ubicacion.put("lng", location.getLongitude());
        ubicacion.put("actualizadoEn", FieldValue.serverTimestamp());

        firestore.collection("ubicaciones_explorador")
                .document(exploradorUid)
                .set(ubicacion);
    }

    private void prepararPerfilExplorador() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Inicia sesión para continuar", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
            finish();
            return;
        }

        accesoJuegosPermitido = false;
        exploradorUid = user.getUid();
        firestore.collection("usuarios")
                .document(exploradorUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot == null || !documentSnapshot.exists()) {
                        android.util.Log.e("PantallaJuegos", "El documento del usuario no existe");
                        Toast.makeText(this, "Error: No se encontró el perfil", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
                        finish();
                        return;
                    }

                    String tipoUsuario = documentSnapshot.getString("tipoUsuario");
                    if (!TIPO_EXPLORADOR.equals(tipoUsuario)) {
                        exploradorUid = null;
                        Toast.makeText(this,
                                "Esta cuenta no es Explorador", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
                        finish();
                        return;
                    }

                    codigoExplorador = documentSnapshot.getString("codigoExplorador");
                    nombreExplorador = documentSnapshot.getString("nombreUsuario");
                    guardianVinculadoId = documentSnapshot.getString("guardianVinculadoId");

                    if (guardianVinculadoId == null || guardianVinculadoId.trim().isEmpty()) {
                        android.util.Log.d("PantallaJuegos", "Redirigiendo a CompartirCodigo (sin vinculo)");
                        Toast.makeText(PantallaJuegos.this, "Primero debes vincularte con un Guardián", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(PantallaJuegos.this, PantallaCompartirCodigo.class));
                        finish();
                        return;
                    }

                    accesoJuegosPermitido = true;
                    iniciarContadorListo();

                    android.util.Log.d("PantallaJuegos", "Perfil cargado: " + nombreExplorador +
                            ", Guardian: " + guardianVinculadoId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PantallaJuegos", "Error leyendo perfil", e);
                    Toast.makeText(this, "No se pudo validar tu acceso", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(PantallaJuegos.this, pantalla_login.class));
                    finish();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!accesoJuegosPermitido) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarPublicacionUbicacion();
        }
    }

    @Override
    protected void onPause() {
        detenerPublicacionUbicacion();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        detenerContadorListo();
        super.onDestroy();
    }

    private void enviarMensajeAlGuardian(@NonNull String contenido, @NonNull String tipo) {
        if (guardianVinculadoId == null || guardianVinculadoId.trim().isEmpty()) {
            android.util.Log.w("PantallaJuegos", "No hay guardian vinculado");
            return;
        }

        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("remitente", nombreExplorador != null ? nombreExplorador : "Explorador");
        mensaje.put("contenido", contenido);
        mensaje.put("timestamp", FieldValue.serverTimestamp());
        mensaje.put("tipo", tipo);

        String messageId = firestore.collection("mensajes")
                .document(guardianVinculadoId)
                .collection("historial")
                .document().getId();

        firestore.collection("mensajes")
                .document(guardianVinculadoId)
                .collection("historial")
                .document(messageId)
                .set(mensaje)
                .addOnSuccessListener(aVoid -> android.util.Log.d("PantallaJuegos",
                        "Mensaje guardado exitosamente: " + messageId))
                .addOnFailureListener(e -> {
                    android.util.Log.e("PantallaJuegos", "Error al guardar mensaje", e);
                    Toast.makeText(PantallaJuegos.this,
                            "Error al enviar mensaje: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });

            if (exploradorUid != null) {
                enviarMensajeEnChatVinculado(contenido, tipo);
            }
            }

            private void enviarMensajeEnChatVinculado(@NonNull String contenido, @NonNull String tipo) {
            if (guardianVinculadoId == null || guardianVinculadoId.trim().isEmpty() || exploradorUid == null) {
                return;
            }

            String chatId = construirChatId(guardianVinculadoId, exploradorUid);
            Map<String, Object> mensaje = new HashMap<>();
            mensaje.put("remitenteId", exploradorUid);
            mensaje.put("remitenteNombre", nombreExplorador != null ? nombreExplorador : "Explorador");
            mensaje.put("contenido", contenido);
            mensaje.put("tipo", tipo);
            mensaje.put("timestamp", FieldValue.serverTimestamp());

            Map<String, Object> conversacion = new HashMap<>();
            conversacion.put("guardianId", guardianVinculadoId);
            conversacion.put("exploradorId", exploradorUid);
            conversacion.put("actualizadoEn", FieldValue.serverTimestamp());

            firestore.collection("chats")
                .document(chatId)
                .set(conversacion, SetOptions.merge());

            String messageId = firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .document().getId();

            firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .document(messageId)
                .set(mensaje)
                .addOnSuccessListener(aVoid -> android.util.Log.d("PantallaJuegos",
                    "Mensaje de chat guardado: " + messageId))
                .addOnFailureListener(e -> android.util.Log.e("PantallaJuegos",
                    "Error al guardar mensaje de chat", e));
            }

            private String construirChatId(@NonNull String guardianId, @NonNull String exploradorId) {
            return guardianId + "_" + exploradorId;
    }
}
