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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PantallaGuardian extends BaseActivity implements OnMapReadyCallback {

    private static final String PREFS_AURA = "AuraPrefs";
    private static final String PREFS_UBICACION = "guardian_ubicacion_explorador";
    private static final String KEY_EXPLORADOR_VINCULADO_ID = "exploradorVinculadoId";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_ESTADO = "estado";
    private static final float ZOOM_MAPA = 15f;

    private MapView mapView;
    private GoogleMap googleMap;
    private Marker markerUltimaUbicacion;
    private ListView listViewNotificaciones;
    private List<String> notificaciones;
    private ArrayAdapter<String> adapterNotificaciones;
    private TextView tvActualizacion;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private ListenerRegistration listenerUbicacionExplorador;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean internetDisponible = true;

    private static final LatLng ULTIMA_UBICACION = new LatLng(19.4326, -99.1332);

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

        // ── MapView ──────────────────────────────────────────────────
        mapView = findViewById(R.id.mapViewGuardian);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        tvActualizacion = findViewById(R.id.tvUltimaActualizacion);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        registrarMonitoreoInternet();

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
            notificaciones.clear();
            adapterNotificaciones.notifyDataSetChanged();
            Toast.makeText(this, "Historial limpiado", Toast.LENGTH_SHORT).show();
        });

        // ── Botón regresar ────────────────────────────────────────────
        ImageButton btnRegresar = findViewById(R.id.imageButtonRegresarGuardian);
        btnRegresar.setOnClickListener(v -> {
            startActivity(new Intent(PantallaGuardian.this, pantalla_inicio.class));
            finish();
        });
    }

    // ── Callback mapa listo ───────────────────────────────────────────
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        LatLng ubicacionInicial = obtenerUbicacionGuardada();
        if (ubicacionInicial != null) {
            mostrarUbicacionEnMapa(ubicacionInicial, obtenerEstadoGuardado("Última ubicación guardada"));
        } else {
            mostrarUbicacionEnMapa(ULTIMA_UBICACION, "Sin vínculo activo");
        }

        iniciarEscuchaUbicacionExplorador();
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
                runOnUiThread(() -> iniciarEscuchaUbicacionExplorador());
            }

            @Override
            public void onLost(@NonNull Network network) {
                internetDisponible = false;
                runOnUiThread(() -> {
                    LatLng guardada = obtenerUbicacionGuardada();
                    if (guardada != null) {
                        mostrarUbicacionEnMapa(guardada, "Sin internet · última del Explorador");
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
                    if (exploradorId == null || exploradorId.trim().isEmpty()) {
                        tvActualizacion.setText("No hay Explorador vinculado");
                        return;
                    }

                    getSharedPreferences(PREFS_AURA, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_EXPLORADOR_VINCULADO_ID, exploradorId)
                            .apply();

                    escucharUbicacionExplorador(exploradorId);
                })
                .addOnFailureListener(e -> tvActualizacion.setText("No se pudo leer la vinculación"));
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
                        tvActualizacion.setText("Explorador sin ubicación publicada");
                        return;
                    }

                    procesarUbicacionExplorador(snapshot);
                });
    }

    private void detenerEscuchaUbicacionExplorador() {
        if (listenerUbicacionExplorador != null) {
            listenerUbicacionExplorador.remove();
            listenerUbicacionExplorador = null;
        }
    }

    private void procesarUbicacionExplorador(@NonNull DocumentSnapshot snapshot) {
        Double lat = snapshot.getDouble("lat");
        Double lng = snapshot.getDouble("lng");

        if (lat == null || lng == null) {
            tvActualizacion.setText("Ubicación del Explorador incompleta");
            return;
        }

        LatLng ubicacionExplorador = new LatLng(lat, lng);
        Timestamp ts = snapshot.getTimestamp("actualizadoEn");
        String estado = ts != null ? formatearFechaActualizacion(ts.toDate()) : "Actualizado";

        guardarUbicacion(ubicacionExplorador, estado);
        mostrarUbicacionEnMapa(ubicacionExplorador, estado);
    }

    private void mostrarUbicacionEnMapa(@NonNull LatLng ubicacion, @NonNull String estado) {
        if (googleMap == null) {
            return;
        }

        if (markerUltimaUbicacion == null) {
            markerUltimaUbicacion = googleMap.addMarker(new MarkerOptions()
                    .position(ubicacion)
                    .title("Última ubicación del Explorador")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)));
        } else {
            markerUltimaUbicacion.setPosition(ubicacion);
        }

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ubicacion, ZOOM_MAPA));
        tvActualizacion.setText(estado);
    }

    private void guardarUbicacion(@NonNull LatLng ubicacion, @NonNull String estado) {
        SharedPreferences prefs = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAT, String.valueOf(ubicacion.latitude))
                .putString(KEY_LNG, String.valueOf(ubicacion.longitude))
                .putString(KEY_ESTADO, estado)
                .apply();
    }

    private LatLng obtenerUbicacionGuardada() {
        SharedPreferences prefs = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        String lat = prefs.getString(KEY_LAT, null);
        String lng = prefs.getString(KEY_LNG, null);

        if (lat == null || lng == null) {
            return null;
        }

        try {
            return new LatLng(Double.parseDouble(lat), Double.parseDouble(lng));
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
        SimpleDateFormat formato = new SimpleDateFormat("dd MMM · HH:mm", locale);
        return formato.format(fecha);
    }

    // ── Ciclo de vida de MapView ──────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        iniciarEscuchaUbicacionExplorador();
    }

    @Override
    protected void onPause() {
        super.onPause();
        detenerEscuchaUbicacionExplorador();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        detenerEscuchaUbicacionExplorador();

        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
