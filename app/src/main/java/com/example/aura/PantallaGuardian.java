package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PantallaGuardian extends BaseActivity {

    private static final String PREFS_AURA = "AuraPrefs";
    private static final String PREFS_UBICACION = "guardian_ubicacion_explorador";
    private static final String KEY_EXPLORADOR_VINCULADO_ID = "exploradorVinculadoId";
    private static final String KEY_CODIGO_VINCULADO = "codigoVinculado";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_ESTADO = "estado";
    private static final String KEY_ULTIMA_LIMPIEZA = "ultima_limpieza_ms";
    private static final int ZOOM_MAPA = 15;

    private WebView mapWebView;
    private ListView listViewNotificaciones;
    private List<String> notificaciones;
    private ArrayAdapter<String> adapterNotificaciones;
    private TextView tvActualizacion;
    private TextView tvNombreExploradorVinculado;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private ListenerRegistration listenerUbicacionExplorador;
    private ListenerRegistration listenerMensajes;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean internetDisponible = true;

    private static final double ULTIMA_LAT = 19.4326;
    private static final double ULTIMA_LNG = -99.1332;

    private static class PuntoMapa {
        final double lat;
        final double lng;

        PuntoMapa(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_guardian);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_guardian), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ── Mapa OpenStreetMap en WebView (sin dependencia de API key) ──
        mapWebView = findViewById(R.id.webViewGuardian);
        configurarWebViewMapa();

        tvActualizacion = findViewById(R.id.tvUltimaActualizacion);
        tvNombreExploradorVinculado = findViewById(R.id.tvNombreExploradorVinculado);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        registrarMonitoreoInternet();

        // Registrar token FCM cada vez que se entra por seguridad
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (auth.getCurrentUser() != null) {
                firestore.collection("usuarios").document(auth.getCurrentUser().getUid())
                        .update("fcmToken", token);
            }
        });

        // ── Historial de notificaciones (sin precargados) ────────────
        notificaciones = new ArrayList<>();

        listViewNotificaciones = findViewById(R.id.listViewNotificaciones);

        Typeface caveat = ResourcesCompat.getFont(this, R.font.caveat_brush);

        adapterNotificaciones = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, notificaciones) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTypeface(caveat);
                tv.setTextSize(16f);
                tv.setTextColor(getResources().getColor(R.color.aura_text_primary, getTheme()));
                tv.setBackgroundColor(Color.TRANSPARENT);
                return view;
            }
        };
        listViewNotificaciones.setAdapter(adapterNotificaciones);

        // ── Botón limpiar historial ───────────────────────────────────
        Button btnLimpiar = findViewById(R.id.btnLimpiarHistorial);
        btnLimpiar.setOnClickListener(v -> {
            long ahora = System.currentTimeMillis();
            getSharedPreferences(PREFS_AURA, MODE_PRIVATE)
                    .edit()
                    .putLong(KEY_ULTIMA_LIMPIEZA, ahora)
                    .apply();
            
            notificaciones.clear();
            adapterNotificaciones.notifyDataSetChanged();
            Toast.makeText(this, "Historial limpiado localmente", Toast.LENGTH_LONG).show();
        });

        // ── Navegación Inferior ───────────────────────────────────────
        LinearLayout navPerfil = findViewById(R.id.navPerfil);
        navPerfil.setOnClickListener(v -> {
            startActivity(new Intent(PantallaGuardian.this, PantallaPerfil.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navConfiguraciones = findViewById(R.id.navConfiguraciones);
        navConfiguraciones.setOnClickListener(v -> {
            startActivity(new Intent(PantallaGuardian.this, PantallaAjustes.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navChat = findViewById(R.id.navChat);
        navChat.setOnClickListener(v -> {
            startActivity(new Intent(PantallaGuardian.this, PantallaChat.class));
            overridePendingTransition(0, 0);
            finish();
        });

        PuntoMapa ubicacionInicial = obtenerUbicacionGuardada();
        if (ubicacionInicial != null) {
            mostrarUbicacionEnMapa(ubicacionInicial, obtenerEstadoGuardado("Última ubicación guardada"));
        } else {
            mostrarUbicacionEnMapa(new PuntoMapa(ULTIMA_LAT, ULTIMA_LNG), "Sin vínculo activo");
        }

        iniciarEscuchaUbicacionExplorador();
        iniciarEscuchaMensajes();
    }

    private void configurarWebViewMapa() {
        if (mapWebView == null) {
            return;
        }

        mapWebView.setWebViewClient(new WebViewClient());
        WebSettings settings = mapWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
    }

    private String construirUrlMapa(double lat, double lng) {
        double delta = 0.008;
        double minLng = lng - delta;
        double minLat = lat - delta;
        double maxLng = lng + delta;
        double maxLat = lat + delta;

        String bbox = String.format(Locale.US, "%f,%f,%f,%f", minLng, minLat, maxLng, maxLat)
                .replace(",", "%2C");
        String marker = String.format(Locale.US, "%f%%2C%f", lat, lng);
        return "https://www.openstreetmap.org/export/embed.html?bbox=" + bbox
                + "&layer=mapnik&marker=" + marker + "#map=" + ZOOM_MAPA + "/"
                + String.format(Locale.US, "%.6f", lat) + "/" + String.format(Locale.US, "%.6f", lng);
    }

    private void registrarMonitoreoInternet() {
        if (connectivityManager == null) {
            return;
        }

        Network redActiva = connectivityManager.getActiveNetwork();
        NetworkCapabilities capacidades = connectivityManager.getNetworkCapabilities(redActiva);
        internetDisponible = capacidades != null &&
                capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                internetDisponible = true;
                runOnUiThread(() -> {
                    // Mostrar ubicación en caché mientras se reconecta
                    PuntoMapa guardada = obtenerUbicacionGuardada();
                    if (guardada != null) {
                        mostrarUbicacionEnMapa(guardada, "Reconectando...");
                    }
                    // Reintentar escucha de ubicación
                    iniciarEscuchaUbicacionExplorador();
                    iniciarEscuchaMensajes();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                internetDisponible = false;
                runOnUiThread(() -> {
                    PuntoMapa guardada = obtenerUbicacionGuardada();
                    if (guardada != null) {
                        mostrarUbicacionEnMapa(guardada, "Sin internet · última ubicación conocida");
                    } else {
                        tvActualizacion.setText("Sin internet y sin datos guardados");
                    }
                });
            }
        };

        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void iniciarEscuchaUbicacionExplorador() {
        if (!internetDisponible) {
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            tvActualizacion.setText("Inicia sesión como Guardián");
            return;
        }

        String guardianUid = user.getUid();
        firestore.collection("usuarios")
                .document(guardianUid)
                .get()
                .addOnSuccessListener(guardianDoc -> {
                    String tipo = guardianDoc.getString("tipoUsuario");
                    if (!TIPO_GUARDIAN.equals(tipo)) {
                        tvActualizacion.setText("Esta cuenta no es Guardián");
                        return;
                    }

                    String exploradorId = guardianDoc.getString("exploradorVinculadoId");
                    if (exploradorId != null && !exploradorId.trim().isEmpty()) {
                        cargarExploradorVinculado(exploradorId);
                        return;
                    }

                    String codigoVinculado = guardianDoc.getString(KEY_CODIGO_VINCULADO);
                    if (codigoVinculado == null || codigoVinculado.trim().isEmpty()) {
                        tvActualizacion.setText("No hay Explorador vinculado");
                        tvNombreExploradorVinculado.setText("Explorador: Sin vincular");
                        return;
                    }

                    resolverExploradorPorCodigo(guardianUid, codigoVinculado);
                })
                .addOnFailureListener(e -> tvActualizacion.setText("No se pudo leer la vinculación"));
    }

    private void resolverExploradorPorCodigo(@NonNull String guardianUid, @NonNull String codigoVinculado) {
        firestore.collection("vinculos")
                .document(codigoVinculado)
                .get()
                .addOnSuccessListener(vinculoDoc -> {
                    String exploradorId = vinculoDoc.getString("exploradorId");
                    if (exploradorId == null || exploradorId.trim().isEmpty()) {
                        tvActualizacion.setText("No hay Explorador vinculado");
                        tvNombreExploradorVinculado.setText("Explorador: Sin vincular");
                        return;
                    }

                    // Normaliza datos del guardián para siguientes sesiones.
                    firestore.collection("usuarios")
                            .document(guardianUid)
                            .update(KEY_EXPLORADOR_VINCULADO_ID, exploradorId);

                    cargarExploradorVinculado(exploradorId);
                })
                .addOnFailureListener(e -> {
                    tvActualizacion.setText("No se pudo resolver la vinculación");
                    tvNombreExploradorVinculado.setText("Explorador: Sin vincular");
                });
    }

    private void cargarExploradorVinculado(@NonNull String exploradorId) {
        getSharedPreferences(PREFS_AURA, MODE_PRIVATE)
                .edit()
                .putString(KEY_EXPLORADOR_VINCULADO_ID, exploradorId)
                .apply();

        // Obtener el nombre del Explorador
        firestore.collection("usuarios")
                .document(exploradorId)
                .get()
                .addOnSuccessListener(exploradorDoc -> {
                    String nombreExplorador = exploradorDoc.getString("nombreUsuario");
                    if (nombreExplorador != null) {
                        tvNombreExploradorVinculado.setText("🌟 Explorador: " + nombreExplorador);
                    } else {
                        tvNombreExploradorVinculado.setText("🌟 Explorador: Desconocido");
                    }
                    escucharUbicacionExplorador(exploradorId);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("PantallaGuardian", "Error leyendo Explorador", e);
                    tvNombreExploradorVinculado.setText("🌟 Explorador: Error");
                    escucharUbicacionExplorador(exploradorId);
                });
    }

    private void escucharUbicacionExplorador(@NonNull String exploradorId) {
        detenerEscuchaUbicacionExplorador();
        listenerUbicacionExplorador = firestore.collection("ubicaciones_explorador")
                .document(exploradorId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        tvActualizacion.setText("Error consultando ubicación");
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        buscarUbicacionPorCampoExplorador(exploradorId);
                        return;
                    }

                    procesarUbicacionExplorador(snapshot);
                });
    }

    private void buscarUbicacionPorCampoExplorador(@NonNull String exploradorId) {
        firestore.collection("ubicaciones_explorador")
                .whereEqualTo("exploradorId", exploradorId)
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        tvActualizacion.setText("Explorador sin ubicación publicada");
                        return;
                    }

                    procesarUbicacionExplorador(query.getDocuments().get(0));
                })
                .addOnFailureListener(e -> tvActualizacion.setText("Error consultando ubicación"));
    }

    private void detenerEscuchaUbicacionExplorador() {
        if (listenerUbicacionExplorador != null) {
            listenerUbicacionExplorador.remove();
            listenerUbicacionExplorador = null;
        }
    }

    private void iniciarEscuchaMensajes() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            android.util.Log.w("PantallaGuardian", "Usuario no autenticado");
            return;
        }

        String guardianUid = user.getUid();
        
        if (listenerMensajes != null) {
            listenerMensajes.remove();
        }

        // Escuchar nuevos mensajes ordenados por timestamp descendente
        listenerMensajes = firestore.collection("mensajes")
                .document(guardianUid)
                .collection("historial")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        android.util.Log.e("PantallaGuardian", "Error en listener de mensajes", error);
                        return;
                    }

                    if (snapshot != null) {
                        long ultimaLimpieza = getSharedPreferences(PREFS_AURA, MODE_PRIVATE)
                                .getLong(KEY_ULTIMA_LIMPIEZA, 0);

                        notificaciones.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Timestamp ts = doc.getTimestamp("timestamp");
                            
                            // Solo mostrar mensajes posteriores a la última limpieza
                            if (ts != null && (ts.toDate().getTime() > ultimaLimpieza)) {
                                String remitente = doc.getString("remitente");
                                String contenido = doc.getString("contenido");
                                
                                if (contenido != null) {
                                    String hora = formatearFechaActualizacion(ts.toDate());
                                    String mensaje = (remitente != null ? remitente + ": " : "") + 
                                                   contenido + " · " + hora;
                                    notificaciones.add(mensaje);
                                }
                            }
                        }
                        adapterNotificaciones.notifyDataSetChanged();
                    }
                });
    }

    private void detenerEscuchaMensajes() {
        if (listenerMensajes != null) {
            listenerMensajes.remove();
            listenerMensajes = null;
        }
    }

    private void procesarUbicacionExplorador(@NonNull DocumentSnapshot snapshot) {
        Double lat = snapshot.getDouble("lat");
        Double lng = snapshot.getDouble("lng");

        if (lat == null || lng == null) {
            tvActualizacion.setText("Ubicación del Explorador incompleta");
            return;
        }

        PuntoMapa ubicacionExplorador = new PuntoMapa(lat, lng);
        Timestamp ts = snapshot.getTimestamp("actualizadoEn");
        String estado = ts != null ? formatearFechaActualizacion(ts.toDate()) : "Actualizado";

        // IMPORTANTE: Guardar SIEMPRE en caché offline
        guardarUbicacion(ubicacionExplorador, estado);
        mostrarUbicacionEnMapa(ubicacionExplorador, estado);
    }

    private void mostrarUbicacionEnMapa(@NonNull PuntoMapa ubicacion, @NonNull String estado) {
        if (mapWebView == null) {
            return;
        }

        mapWebView.loadUrl(construirUrlMapa(ubicacion.lat, ubicacion.lng));
        tvActualizacion.setText(estado);
    }

    private void guardarUbicacion(@NonNull PuntoMapa ubicacion, @NonNull String estado) {
        SharedPreferences prefs = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAT, String.valueOf(ubicacion.lat))
                .putString(KEY_LNG, String.valueOf(ubicacion.lng))
                .putString(KEY_ESTADO, estado)
                .apply();
    }

    private PuntoMapa obtenerUbicacionGuardada() {
        SharedPreferences prefs = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        String lat = prefs.getString(KEY_LAT, null);
        String lng = prefs.getString(KEY_LNG, null);

        if (lat == null || lng == null) {
            return null;
        }

        try {
            return new PuntoMapa(Double.parseDouble(lat), Double.parseDouble(lng));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String obtenerEstadoGuardado(@NonNull String porDefecto) {
        SharedPreferences prefs = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        return prefs.getString(KEY_ESTADO, porDefecto);
    }

    private String formatearFechaActualizacion(@NonNull Date fecha) {
        Locale locale = new Locale("es", "MX");
        SimpleDateFormat formato = new SimpleDateFormat("dd MMM · hh:mm a", locale);
        return formato.format(fecha);
    }

    // ── Ciclo de vida de MapView ──────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        if (mapWebView != null) {
            mapWebView.onResume();
        }
        iniciarEscuchaUbicacionExplorador();
        iniciarEscuchaMensajes();
    }

    @Override
    protected void onPause() {
        super.onPause();
        detenerEscuchaUbicacionExplorador();
        detenerEscuchaMensajes();
        if (mapWebView != null) {
            mapWebView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        detenerEscuchaUbicacionExplorador();
        detenerEscuchaMensajes();

        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        super.onDestroy();
        if (mapWebView != null) {
            mapWebView.destroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }
}
