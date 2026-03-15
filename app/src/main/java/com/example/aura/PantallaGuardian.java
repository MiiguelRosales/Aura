package com.example.aura;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;

public class PantallaGuardian extends BaseActivity implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;
    private ListView listViewNotificaciones;
    private List<String> notificaciones;
    private ArrayAdapter<String> adapterNotificaciones;

    private ActivityResultLauncher<String> locationPermissionLauncher;

    // Última ubicación demo (se reemplazará con datos reales de Firebase)
    private static final LatLng ULTIMA_UBICACION = new LatLng(19.4326, -99.1332); // CDMX demo

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

        // Registrar lanzador para permiso de ubicación
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        mapView.getMapAsync(this);
                    } else {
                        Toast.makeText(this,
                                "Se necesita permiso de ubicación para mostrar el mapa",
                                Toast.LENGTH_SHORT).show();
                        mapView.getMapAsync(this); // Carga mapa sin capa Mi-Ubicación
                    }
                }
        );

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mapView.getMapAsync(this);
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // ── Historial de notificaciones (datos demo) ─────────────────
        notificaciones = new ArrayList<>();
        notificaciones.add("✅  Explorador llegó a casa  —  14 Mar 18:32");
        notificaciones.add("📍  Nueva ubicación registrada  —  14 Mar 16:05");
        notificaciones.add("⚠️  Explorador salió de la zona segura  —  14 Mar 14:50");
        notificaciones.add("📍  Nueva ubicación registrada  —  14 Mar 12:18");
        notificaciones.add("✅  Explorador llegó al colegio  —  14 Mar 07:45");
        notificaciones.add("📍  Nueva ubicación registrada  —  13 Mar 20:10");
        notificaciones.add("ℹ️  Sesión iniciada por Explorador  —  13 Mar 08:00");

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

        // Mostrar mi ubicación si hay permiso
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        }

        // Marcador de última ubicación registrada
        googleMap.addMarker(new MarkerOptions()
                .position(ULTIMA_UBICACION)
                .title("Última ubicación del Explorador")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)));

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ULTIMA_UBICACION, 14f));

        // Actualizar etiqueta de última actualización
        TextView tvActualizacion = findViewById(R.id.tvUltimaActualizacion);
        tvActualizacion.setText("14 Mar · 18:32");
    }

    // ── Ciclo de vida de MapView ──────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
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
