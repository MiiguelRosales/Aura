package com.example.aura;

import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class BaseActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    private static final String KEY_DARK_MODE = "darkMode";

    @Override
    public void setContentView(int layoutResID) {
        // Aplicar tema guardado antes de inflar el layout
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, true);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.setContentView(layoutResID);
    }

    /**
     * Muestra un mensaje al usuario.
     * - Mensajes cortos (≤60 chars): Toast que se desvanece solo.
     * - Mensajes largos (>60 chars): AlertDialog que el usuario cierra cuando termina de leer.
     */
    protected void showMessage(String mensaje) {
        if (mensaje == null) return;
        if (mensaje.length() <= 60) {
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(mensaje)
                    .setPositiveButton("Entendido", null)
                    .show();
        }
    }
}
