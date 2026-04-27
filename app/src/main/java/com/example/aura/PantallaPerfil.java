package com.example.aura;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class PantallaPerfil extends BaseActivity {
    private static final int MAX_PROFILE_IMAGE_BYTES = 350 * 1024;
    private static final java.util.regex.Pattern PATRON_USUARIO = java.util.regex.Pattern.compile("^[A-Za-z]{1,15}$");

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    
    private ImageView ivFotoPerfil;
    private EditText etNombre;
    private EditText etCorreo;
    private TextView tvTipoUsuario;
    private EditText etCelular;
    private EditText etFechaNac;
    private EditText etContrasena;
    private Button btnModificar;

    private boolean enModoEdicion = false;

    // Almacenar valores originales para comparar
    private String originalNombre = "";
    private String originalCorreo = "";
    private String originalCelular = "";
    private String originalFechaNac = "";
    private String originalFotoPerfilBase64 = "";
    private String originalFotoPerfilUrl = "";

    private ActivityResultLauncher<String> seleccionarImagenLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_perfil);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_perfil), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        seleccionarImagenLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::subirFotoPerfil
        );

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil);
        etNombre = findViewById(R.id.etNombreUsuarioPerfil);
        etCorreo = findViewById(R.id.etCorreoPerfil);
        tvTipoUsuario = findViewById(R.id.tvTipoUsuarioPerfil);
        etCelular = findViewById(R.id.etCelularPerfil);
        etFechaNac = findViewById(R.id.etFechaNacPerfil);
        etContrasena = findViewById(R.id.etContrasenaPerfil);
        btnModificar = findViewById(R.id.btnModificar);

        cargarDatosPerfil();

        ivFotoPerfil.setOnClickListener(v -> seleccionarImagenLauncher.launch("image/*"));

        btnModificar.setOnClickListener(v -> {
            if (!enModoEdicion) {
                habilitarEdicion();
            } else {
                guardarCambios();
            }
        });

        // ── Navegación Inferior ───────────────────────────────────────
        SharedPreferences prefs = getSharedPreferences("AuraPrefs", MODE_PRIVATE);
        String tipoUsuario = prefs.getString("tipoUsuario", "GUARDIAN");

        // Actualizar icono y texto del primer tab según el tipo de usuario
        TextView tvNavIcono = findViewById(R.id.tvNavPrincipalIcono);
        TextView tvNavTexto = findViewById(R.id.tvNavPrincipalTexto);
        if ("EXPLORADOR".equals(tipoUsuario)) {
            tvNavIcono.setText("🎮");
            tvNavTexto.setText("Explorador");
        }

        LinearLayout navPaginaGuardian = findViewById(R.id.navPaginaGuardian);
        navPaginaGuardian.setOnClickListener(v -> {
            Intent intent = "EXPLORADOR".equals(tipoUsuario)
                    ? new Intent(PantallaPerfil.this, PantallaJuegos.class)
                    : new Intent(PantallaPerfil.this, PantallaGuardian.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navChat = findViewById(R.id.navChat);
        navChat.setOnClickListener(v -> {
            startActivity(new Intent(PantallaPerfil.this, PantallaChat.class));
            overridePendingTransition(0, 0);
            finish();
        });

        LinearLayout navConfiguraciones = findViewById(R.id.navConfiguraciones);
        navConfiguraciones.setOnClickListener(v -> {
            startActivity(new Intent(PantallaPerfil.this, PantallaAjustes.class));
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void cargarDatosPerfil() {
        FirebaseUser user = auth.getCurrentUser();
        
        // Limpieza inicial para evitar ver "datos fijos" mientras carga
        etNombre.setText("");
        tvTipoUsuario.setText("");
        etCorreo.setText("");
        etCelular.setText("");
        etFechaNac.setText("");

        if (user != null) {
            originalCorreo = user.getEmail() != null ? user.getEmail() : "";
            etCorreo.setText(originalCorreo);

            firestore.collection("usuarios")
                    .document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            originalNombre = documentSnapshot.getString("nombreUsuario");
                            String tipo = documentSnapshot.getString("tipoUsuario");
                            originalCelular = documentSnapshot.getString("celular");
                            originalFechaNac = documentSnapshot.getString("fechaNacimiento");
                            originalFotoPerfilBase64 = documentSnapshot.getString("fotoPerfilBase64");
                            originalFotoPerfilUrl = documentSnapshot.getString("fotoPerfilUrl");

                            originalNombre = originalNombre != null ? originalNombre : "";
                            originalCelular = originalCelular != null ? originalCelular : "";
                            originalFechaNac = originalFechaNac != null ? originalFechaNac : "";
                            originalFotoPerfilBase64 = originalFotoPerfilBase64 != null ? originalFotoPerfilBase64 : "";
                            originalFotoPerfilUrl = originalFotoPerfilUrl != null ? originalFotoPerfilUrl : "";

                            etNombre.setText(originalNombre);
                            
                            if ("GUARDIAN".equals(tipo)) {
                                tvTipoUsuario.setText("🛡️ Guardián");
                            } else if ("EXPLORADOR".equals(tipo)) {
                                tvTipoUsuario.setText("🌟 Explorador");
                            } else {
                                tvTipoUsuario.setText(tipo != null ? tipo : "");
                            }

                            etCelular.setText(originalCelular);
                            etFechaNac.setText(originalFechaNac);
                            cargarFotoPerfil(originalFotoPerfilBase64, originalFotoPerfilUrl);
                        } else {
                            etNombre.setText("");
                            tvTipoUsuario.setText("");
                            etCelular.setText("");
                            etFechaNac.setText("");
                            cargarFotoPerfil(null, null);
                        }
                    })
                    .addOnFailureListener(e -> {
                        etNombre.setText("");
                        tvTipoUsuario.setText("");
                        etCelular.setText("");
                        etFechaNac.setText("");
                        cargarFotoPerfil(null, null);
                    });
        } else {
            etNombre.setText("Invitado");
            etCorreo.setText("");
            tvTipoUsuario.setText("");
            etCelular.setText("");
            etFechaNac.setText("");
            cargarFotoPerfil(null, null);
        }
    }

    private void cargarFotoPerfil(String fotoBase64, String fotoUrl) {
        if (!TextUtils.isEmpty(fotoBase64)) {
            byte[] data = decodificarBase64Seguro(fotoBase64);
            if (data != null && data.length > 0) {
                Glide.with(this)
                        .asBitmap()
                        .load(data)
                        .circleCrop()
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(ivFotoPerfil);
                return;
            }
        }

        if (!TextUtils.isEmpty(fotoUrl)) {
            Glide.with(this)
                    .load(fotoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(ivFotoPerfil);
            return;
        }

        Glide.with(this)
                .load(R.drawable.ic_launcher_foreground)
                .circleCrop()
                .into(ivFotoPerfil);
    }

    private void subirFotoPerfil(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        String mimeType = getContentResolver().getType(imageUri);
        if (mimeType == null || !mimeType.startsWith("image/")) {
            Toast.makeText(this, "Selecciona una imagen válida", Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Inicia sesión para actualizar la foto", Toast.LENGTH_LONG).show();
            return;
        }

        ivFotoPerfil.setEnabled(false);
        Toast.makeText(this, "Procesando foto de perfil...", Toast.LENGTH_LONG).show();

        byte[] imagenComprimida = comprimirImagen(imageUri);
        if (imagenComprimida == null || imagenComprimida.length == 0) {
            Toast.makeText(this, "No se pudo procesar la foto seleccionada", Toast.LENGTH_LONG).show();
            ivFotoPerfil.setEnabled(true);
            return;
        }

        String fotoBase64 = Base64.encodeToString(imagenComprimida, Base64.NO_WRAP);
        Map<String, Object> updates = new HashMap<>();
        updates.put("fotoPerfilBase64", fotoBase64);
        updates.put("fotoPerfilUrl", null);
        updates.put("actualizadoEn", FieldValue.serverTimestamp());

        firestore.collection("usuarios")
                .document(user.getUid())
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    originalFotoPerfilBase64 = fotoBase64;
                    originalFotoPerfilUrl = "";
                    cargarFotoPerfil(originalFotoPerfilBase64, null);
                    Toast.makeText(this, "Foto de perfil actualizada", Toast.LENGTH_LONG).show();
                    ivFotoPerfil.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "No se pudo guardar la foto en perfil", Toast.LENGTH_LONG).show();
                    ivFotoPerfil.setEnabled(true);
                });
    }

    private byte[] comprimirImagen(@NonNull Uri uri) {
        try {
            int maxDim = 1024;
            Bitmap bitmap = decodificarBitmapSeguro(uri, maxDim);

            if (bitmap == null) {
                // Algunos proveedores/fuentes entregan formatos que no decodifican con BitmapFactory.
                return leerBytesConLimite(uri, MAX_PROFILE_IMAGE_BYTES);
            }

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > maxDim || height > maxDim) {
                float ratio = Math.min((float) maxDim / width, (float) maxDim / height);
                int targetW = Math.round(width * ratio);
                int targetH = Math.round(height * ratio);
                bitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int quality = 78;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            while (baos.size() > MAX_PROFILE_IMAGE_BYTES && quality > 30) {
                baos.reset();
                quality -= 8;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            }

            int intentosEscala = 0;
            while (baos.size() > MAX_PROFILE_IMAGE_BYTES && intentosEscala < 3) {
                int nuevoAncho = Math.max(300, Math.round(bitmap.getWidth() * 0.85f));
                int nuevoAlto = Math.max(300, Math.round(bitmap.getHeight() * 0.85f));
                bitmap = Bitmap.createScaledBitmap(bitmap, nuevoAncho, nuevoAlto, true);
                baos.reset();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos);
                intentosEscala++;
            }

            if (baos.size() > MAX_PROFILE_IMAGE_BYTES) {
                return null;
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap decodificarBitmapSeguro(@NonNull Uri uri, int maxDim) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    int maxLado = Math.max(width, height);
                    if (maxLado > maxDim) {
                        float ratio = (float) maxDim / (float) maxLado;
                        decoder.setTargetSize(
                                Math.max(1, Math.round(width * ratio)),
                                Math.max(1, Math.round(height * ratio))
                        );
                    }
                });
            }
        } catch (Exception ignored) {
        }

        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) {
                    return null;
                }
                BitmapFactory.decodeStream(stream, null, bounds);
            }

            int sampleSize = 1;
            while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = Math.max(sampleSize, 1);
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(stream, null, decodeOptions);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] leerBytesConLimite(@NonNull Uri uri, int maxBytes) {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (input == null) {
                return null;
            }

            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] decodificarBase64Seguro(String base64) {
        try {
            return Base64.decode(base64, Base64.DEFAULT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void habilitarEdicion() {
        enModoEdicion = true;
        btnModificar.setText("GUARDAR CAMBIOS");

        // Habilitar campos
        etNombre.setEnabled(true);
        etCorreo.setEnabled(true);
        etCelular.setEnabled(true);
        etFechaNac.setEnabled(true);
        etContrasena.setEnabled(true);

        // Dar un poco de estilo para que parezcan campos de texto editables
        int bg = R.drawable.card_inner_bg; // Reutilizando un fondo oscuro sutil
        etNombre.setBackgroundResource(bg);
        etCorreo.setBackgroundResource(bg);
        etCelular.setBackgroundResource(bg);
        etFechaNac.setBackgroundResource(bg);
        etContrasena.setBackgroundResource(bg);

        // Limpiar y poner hints de opcional para todos menos la fecha de nacimiento
        etNombre.setText("");
        etNombre.setHint("(Opcional) Nuevo nombre");

        etCorreo.setText("");
        etCorreo.setHint("(Opcional) Nuevo correo");
        
        etCelular.setText("");
        etCelular.setHint("(Opcional) Nuevo celular");

        etContrasena.setText("");
        etContrasena.setHint("(Opcional) Nueva contraseña");
    }

    private void deshabilitarEdicion() {
        enModoEdicion = false;
        btnModificar.setText("MODIFICAR DATOS");

        // Deshabilitar campos
        etNombre.setEnabled(false);
        etCorreo.setEnabled(false);
        etCelular.setEnabled(false);
        etFechaNac.setEnabled(false);
        etContrasena.setEnabled(false);

        // Quitar fondos
        etNombre.setBackground(null);
        etCorreo.setBackground(null);
        etCelular.setBackground(null);
        etFechaNac.setBackground(null);
        etContrasena.setBackground(null);

        // Restaurar textos y hints
        etNombre.setHint("Nombre");
        etNombre.setText(originalNombre);

        etCorreo.setHint("Correo");
        etCorreo.setText(originalCorreo.isEmpty() ? "Sin correo" : originalCorreo);
        
        etCelular.setHint("Celular");
        etCelular.setText(originalCelular);

        etFechaNac.setText(originalFechaNac);

        etContrasena.setText("••••••••");
    }

    private void guardarCambios() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String tempNombre = etNombre.getText().toString().trim();
        if (!tempNombre.isEmpty() && !PATRON_USUARIO.matcher(tempNombre).matches()) {
            Toast.makeText(this, "Nombre de usuario: solo letras (máx 12)", Toast.LENGTH_LONG).show();
            return;
        }
        final String nuevoNombre = tempNombre.isEmpty() ? originalNombre : tempNombre;
        
        final String nuevaFechaNac = etFechaNac.getText().toString().trim();
        
        String tempCorreo = etCorreo.getText().toString().trim();
        final String nuevoCorreo = tempCorreo.isEmpty() ? originalCorreo : tempCorreo;
        
        String tempCelular = etCelular.getText().toString().trim();
        final String nuevoCelular = tempCelular.isEmpty() ? originalCelular : tempCelular;

        String nuevaContrasena = etContrasena.getText().toString().trim();

        if (TextUtils.isEmpty(nuevaFechaNac)) {
            Toast.makeText(this, "Completa la fecha de nacimiento", Toast.LENGTH_LONG).show();
            return;
        }

        boolean cambioDatos = !nuevoNombre.equals(originalNombre) 
                || !nuevoCorreo.equals(originalCorreo) 
                || !nuevoCelular.equals(originalCelular) 
                || !nuevaFechaNac.equals(originalFechaNac);
        
        boolean cambioContrasena = !TextUtils.isEmpty(nuevaContrasena);

        if (!cambioDatos && !cambioContrasena) {
            // No hubo cambios
            deshabilitarEdicion();
            return;
        }

        btnModificar.setEnabled(false);
        btnModificar.setText("GUARDANDO...");

        if (cambioDatos) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("nombreUsuario", nuevoNombre);
            updates.put("correo", nuevoCorreo);
            updates.put("celular", nuevoCelular);
            updates.put("fechaNacimiento", nuevaFechaNac);

            // Guardar en Firestore
            firestore.collection("usuarios")
                    .document(user.getUid())
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        // Actualizar original values locales
                        originalNombre = nuevoNombre;
                        originalCelular = nuevoCelular;
                        originalFechaNac = nuevaFechaNac;

                        if (!nuevoCorreo.equals(originalCorreo)) {
                            // Actualizar correo en Firebase Auth
                            user.updateEmail(nuevoCorreo)
                                    .addOnSuccessListener(aVoid1 -> {
                                        originalCorreo = nuevoCorreo;
                                        procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
                                    })
                                    .addOnFailureListener(e -> {
                                        showMessage("Perfil guardado. Error al actualizar correo de sesión.");
                                        procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
                                    });
                        } else {
                            procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error al guardar perfil", Toast.LENGTH_LONG).show();
                        btnModificar.setEnabled(true);
                        btnModificar.setText("GUARDAR CAMBIOS");
                    });
        } else {
            // Solo cambió la contraseña
            procesarCambioContrasena(user, nuevaContrasena, cambioContrasena);
        }
    }

    private void procesarCambioContrasena(FirebaseUser user, String nuevaContrasena, boolean cambioContrasena) {
        if (cambioContrasena) {
            user.updatePassword(nuevaContrasena)
                    .addOnSuccessListener(aVoid1 -> {
                        Toast.makeText(this, "Perfil y datos actualizados correctamente", Toast.LENGTH_LONG).show();
                        finalizarGuardado();
                    })
                    .addOnFailureListener(e -> {
                        showMessage("Cambios guardados, pero hubo error al cambiar la contraseña (intenta cerrar sesión e iniciar de nuevo).");
                        finalizarGuardado();
                    });
        } else {
            Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_LONG).show();
            finalizarGuardado();
        }
    }

    private void finalizarGuardado() {
        btnModificar.setEnabled(true);
        deshabilitarEdicion();
    }
}
