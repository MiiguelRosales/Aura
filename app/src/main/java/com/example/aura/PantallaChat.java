package com.example.aura;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PantallaChat extends BaseActivity {

    private static final String PREFS_NAME = "AuraPrefs";
    // TODO: PEGA AQUÍ TU CLAVE DE SERVIDOR DE FIREBASE CONSOLE
    private static final String FCM_SERVER_KEY = "TU_CLAVE_AQUI";
    private static final String TIPO_GUARDIAN = "GUARDIAN";
    private static final String TIPO_EXPLORADOR = "EXPLORADOR";
    private static final String TIPO_MENSAJE_TEXTO = "texto";
    private static final String TIPO_MENSAJE_IMAGEN = "imagen";
    private static final String TIPO_MENSAJE_ARCHIVO = "archivo";
    private static final int MAX_CHAT_FILE_BYTES = 350 * 1024;
    private static final int MAX_CHAT_IMAGE_BYTES = 280 * 1024;
    private static final String KEY_TIPO_USUARIO = "tipoUsuario";
    private static final String KEY_NOMBRE_USUARIO = "nombreUsuario";
    private static final String KEY_FOTO_PERFIL_URL = "fotoPerfilUrl";
    private static final String KEY_FOTO_PERFIL_BASE64 = "fotoPerfilBase64";
    private static final String KEY_ULTIMA_CONEXION = "ultimaConexion";
    private static final String KEY_ESTADO_CONEXION = "estadoConexion";
    private static final String KEY_GUARDIAN_VINCULADO_ID = "guardianVinculadoId";
    private static final String KEY_EXPLORADOR_VINCULADO_ID = "exploradorVinculadoId";
    private static final String KEY_CODIGO_VINCULADO = "codigoVinculado";

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private ListenerRegistration listenerChat;
    private ListenerRegistration listenerContraparte;
    private ListenerRegistration listenerConversacion;

    private ActivityResultLauncher<String> seleccionarImagenLauncher;

    private TextView tvNavPrincipalIcono;
    private TextView tvNavPrincipalTexto;
    private ImageView ivAvatarContacto;
    private TextView tvNombreContacto;
    private TextView tvEstadoContacto;
    private TextView tvChatVacio;
    private ListView listViewChat;
    private EditText etMensajeChat;
    private View btnMasOpcionesChat;
    private PopupWindow popupAdjuntos;

    private final List<MensajeChat> mensajes = new ArrayList<>();
    private ChatAdapter adapter;

    private String currentUid;
    private String currentNombre;
    private String currentTipo;
    private String counterpartUid;
    private String counterpartNombre;
    private String counterpartFotoUrl;
    private String counterpartFotoBase64;
    private String guardianId;
    private String exploradorId;
    private String chatId;
    private String estadoPresencia = "Sin conexión";
    private String counterpartFcmToken;
    private boolean counterpartEscribiendo;
    private boolean localEscribiendo;

    private static class MensajeChat {
        String remitenteId;
        String remitenteNombre;
        String contenido;
        String tipo;
        String mediaBase64;
        String mediaUrl;
        String fileName;
        String mimeType;
        Timestamp timestamp;
        boolean mio;
    }

    private static class ChatViewHolder {
        View containerRecibido;
        View containerEnviado;
        TextView tvAvatarRecibido;
        TextView tvNombreRecibido;
        TextView tvMensajeRecibido;
        TextView tvHoraRecibido;
        ImageView ivImagenRecibida;
        TextView tvArchivoRecibido;
        TextView tvMensajeEnviado;
        TextView tvHoraEnviado;
        ImageView ivImagenEnviada;
        TextView tvArchivoEnviado;
        TextView tvSeparadorFecha;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.pantalla_chat);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_chat), (v, insets) -> {
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
            this::enviarImagenAdjunta
        );

        tvNavPrincipalIcono = findViewById(R.id.tvNavPrincipalIcono);
        tvNavPrincipalTexto = findViewById(R.id.tvNavPrincipalTexto);
        ivAvatarContacto = findViewById(R.id.ivAvatarContacto);
        tvNombreContacto = findViewById(R.id.tvNombreContacto);
        tvEstadoContacto = findViewById(R.id.tvEstadoContacto);
        tvChatVacio = findViewById(R.id.tvChatVacio);
        listViewChat = findViewById(R.id.listViewChat);
        etMensajeChat = findViewById(R.id.etMensajeChat);
        btnMasOpcionesChat = findViewById(R.id.btnMasOpcionesChat);

        adapter = new ChatAdapter();
        listViewChat.setAdapter(adapter);

        findViewById(R.id.btnEnviarChat).setOnClickListener(v -> enviarMensajeTexto());
        btnMasOpcionesChat.setOnClickListener(this::mostrarMenuAdjuntos);
        ivAvatarContacto.setOnClickListener(v -> mostrarImagenAvatarCompleta());

        etMensajeChat.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean escribiendo = s != null && !TextUtils.isEmpty(s.toString().trim());
                actualizarEstadoEscribiendo(escribiendo);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        configurarNavegacionInferior();
        cargarContextoChat();
    }

    private void configurarNavegacionInferior() {
        String tipoUsuario = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_TIPO_USUARIO, TIPO_GUARDIAN);

        if (TIPO_EXPLORADOR.equals(tipoUsuario)) {
            tvNavPrincipalIcono.setText("🎮");
            tvNavPrincipalTexto.setText("Explorador");
        } else {
            tvNavPrincipalIcono.setText("🛡️");
            tvNavPrincipalTexto.setText("Guardián");
        }

        LinearLayout navPrincipal = findViewById(R.id.navPaginaPrincipalChat);
        navPrincipal.setOnClickListener(v -> {
            startActivity(new Intent(PantallaChat.this,
                    TIPO_EXPLORADOR.equals(tipoUsuario) ? PantallaJuegos.class : PantallaGuardian.class));
            overridePendingTransition(0, 0);
            finish();
        });

        findViewById(R.id.navPerfil).setOnClickListener(v -> {
            startActivity(new Intent(PantallaChat.this, PantallaPerfil.class));
            overridePendingTransition(0, 0);
            finish();
        });

        findViewById(R.id.navConfiguraciones).setOnClickListener(v -> {
            startActivity(new Intent(PantallaChat.this, PantallaAjustes.class));
            overridePendingTransition(0, 0);
            finish();
        });
    }

    private void cargarContextoChat() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            mostrarEstadoSinVinculo("Inicia sesión para usar el chat");
            deshabilitarChat();
            return;
        }

        currentUid = user.getUid();
        firestore.collection("usuarios")
                .document(currentUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        mostrarEstadoSinVinculo("No se encontró tu perfil");
                        deshabilitarChat();
                        return;
                    }

                    currentNombre = defaultString(documentSnapshot.getString(KEY_NOMBRE_USUARIO), "Usuario");
                    currentTipo = documentSnapshot.getString("tipoUsuario");
                    guardianId = documentSnapshot.getString(KEY_GUARDIAN_VINCULADO_ID);
                    exploradorId = documentSnapshot.getString(KEY_EXPLORADOR_VINCULADO_ID);

                    if (TIPO_GUARDIAN.equals(currentTipo)) {
                        guardianId = currentUid;
                        if (TextUtils.isEmpty(exploradorId)) {
                            String codigoVinculado = documentSnapshot.getString(KEY_CODIGO_VINCULADO);
                            if (!TextUtils.isEmpty(codigoVinculado)) {
                                resolverExploradorPorCodigo(codigoVinculado);
                                return;
                            }
                        }
                        counterpartUid = exploradorId;
                    } else if (TIPO_EXPLORADOR.equals(currentTipo)) {
                        exploradorId = currentUid;
                        counterpartUid = guardianId;
                    }

                    if (TextUtils.isEmpty(counterpartUid)) {
                        mostrarEstadoSinVinculo("No tienes un vínculo activo");
                        deshabilitarChat();
                        return;
                    }

                    chatId = construirChatId(guardianId != null ? guardianId : currentUid,
                            exploradorId != null ? exploradorId : counterpartUid);
                    escucharContraparte();
                        escucharConversacion();
                    escucharMensajes();
                    actualizarPresencia(true);
                    asegurarConversacion();
                })
                .addOnFailureListener(e -> {
                    mostrarEstadoSinVinculo("No se pudo cargar tu perfil");
                    deshabilitarChat();
                });
    }

    private void resolverExploradorPorCodigo(@NonNull String codigoVinculado) {
        firestore.collection("vinculos")
                .document(codigoVinculado)
                .get()
                .addOnSuccessListener(vinculoDoc -> {
                    String exploradorEncontrado = vinculoDoc.getString("exploradorId");
                    if (TextUtils.isEmpty(exploradorEncontrado)) {
                        mostrarEstadoSinVinculo("No tienes un Explorador vinculado");
                        deshabilitarChat();
                        return;
                    }

                    guardianId = currentUid;
                    exploradorId = exploradorEncontrado;
                    counterpartUid = exploradorId;
                    chatId = construirChatId(currentUid, exploradorId);
                    escucharContraparte();
                    escucharConversacion();
                    escucharMensajes();
                    actualizarPresencia(true);
                    asegurarConversacion();
                })
                .addOnFailureListener(e -> {
                    mostrarEstadoSinVinculo("No se pudo resolver la vinculación");
                    deshabilitarChat();
                });
    }

    private void escucharContraparte() {
        if (TextUtils.isEmpty(counterpartUid)) {
            return;
        }

        if (listenerContraparte != null) {
            listenerContraparte.remove();
        }

        listenerContraparte = firestore.collection("usuarios")
                .document(counterpartUid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        tvNombreContacto.setText("Contacto no disponible");
                        tvEstadoContacto.setText("Sin conexión");
                        ivAvatarContacto.setImageResource(R.drawable.ic_launcher_foreground);
                        return;
                    }

                    counterpartNombre = defaultString(snapshot.getString(KEY_NOMBRE_USUARIO), "Contacto");
                    counterpartFotoBase64 = snapshot.getString(KEY_FOTO_PERFIL_BASE64);
                    counterpartFotoUrl = snapshot.getString(KEY_FOTO_PERFIL_URL);
                    counterpartFcmToken = snapshot.getString("fcmToken");
                    tvNombreContacto.setText(counterpartNombre);
                    actualizarEstadoContacto(snapshot);
                    cargarAvatarContacto();
                });
    }

    private void escucharConversacion() {
        if (TextUtils.isEmpty(chatId)) {
            return;
        }

        if (listenerConversacion != null) {
            listenerConversacion.remove();
        }

        listenerConversacion = firestore.collection("chats")
                .document(chatId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) {
                        counterpartEscribiendo = false;
                        refrescarEstadoContacto();
                        return;
                    }

                    Map<String, Object> typing = (Map<String, Object>) snapshot.get("typing");
                    boolean escribiendo = false;
                    if (typing != null && counterpartUid != null) {
                        Object valor = typing.get(counterpartUid);
                        if (valor instanceof Boolean) {
                            escribiendo = (Boolean) valor;
                        }
                    }
                    counterpartEscribiendo = escribiendo;
                    refrescarEstadoContacto();
                });
    }

    private void actualizarEstadoContacto(@NonNull DocumentSnapshot snapshot) {
        String estado = snapshot.getString(KEY_ESTADO_CONEXION);
        Timestamp ultimaConexion = snapshot.getTimestamp(KEY_ULTIMA_CONEXION);

        if ("EN_LINEA".equals(estado)) {
            estadoPresencia = "En línea";
            refrescarEstadoContacto();
            return;
        }

        if (ultimaConexion != null) {
            SimpleDateFormat formato = new SimpleDateFormat("dd MMM · hh:mm a", new Locale("es", "MX"));
            estadoPresencia = "Última conexión: " + formato.format(ultimaConexion.toDate());
        } else {
            estadoPresencia = "Sin última conexión";
        }
        refrescarEstadoContacto();
    }

    private void refrescarEstadoContacto() {
        if (counterpartEscribiendo) {
            tvEstadoContacto.setText("Escribiendo...");
            return;
        }
        tvEstadoContacto.setText(estadoPresencia);
    }

    private void cargarAvatarContacto() {
        if (!TextUtils.isEmpty(counterpartFotoBase64)) {
            byte[] data = decodificarBase64Seguro(counterpartFotoBase64);
            if (data != null && data.length > 0) {
                Glide.with(this)
                        .asBitmap()
                        .load(data)
                        .circleCrop()
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(ivAvatarContacto);
                return;
            }
        }

        if (!TextUtils.isEmpty(counterpartFotoUrl)) {
            Glide.with(this)
                    .load(counterpartFotoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(ivAvatarContacto);
        } else {
            Glide.with(this)
                    .load(R.drawable.ic_launcher_foreground)
                    .circleCrop()
                    .into(ivAvatarContacto);
        }
    }

    private void asegurarConversacion() {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(guardianId) || TextUtils.isEmpty(exploradorId)) {
            return;
        }

        Map<String, Object> datos = new HashMap<>();
        datos.put("guardianId", guardianId);
        datos.put("exploradorId", exploradorId);
        datos.put("actualizadoEn", FieldValue.serverTimestamp());

        firestore.collection("chats")
                .document(chatId)
                .set(datos, SetOptions.merge());
    }

    private void escucharMensajes() {
        if (TextUtils.isEmpty(chatId)) {
            return;
        }

        if (listenerChat != null) {
            listenerChat.remove();
        }

        listenerChat = firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "No se pudo cargar el chat", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    mensajes.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            MensajeChat mensaje = new MensajeChat();
                            mensaje.remitenteId = doc.getString("remitenteId");
                            mensaje.remitenteNombre = doc.getString("remitenteNombre");
                            mensaje.contenido = doc.getString("contenido");
                            mensaje.tipo = doc.getString("tipo");
                            mensaje.mediaBase64 = doc.getString("mediaBase64");
                            mensaje.mediaUrl = doc.getString("mediaUrl");
                            mensaje.fileName = doc.getString("fileName");
                            mensaje.mimeType = doc.getString("mimeType");
                            mensaje.timestamp = doc.getTimestamp("timestamp");
                            mensaje.mio = currentUid != null && currentUid.equals(mensaje.remitenteId);

                            if (mensaje.contenido != null
                                    || mensaje.mediaBase64 != null
                                    || mensaje.mediaUrl != null) {
                                mensajes.add(mensaje);
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();
                    tvChatVacio.setVisibility(mensajes.isEmpty() ? View.VISIBLE : View.GONE);
                    if (!mensajes.isEmpty()) {
                        listViewChat.post(() -> listViewChat.setSelection(adapter.getCount() - 1));
                        MensajeChat ultimoMensaje = mensajes.get(mensajes.size() - 1);
                        if (ultimoMensaje.timestamp != null && chatId != null) {
                            marcarChatComoLeido(chatId, ultimoMensaje.timestamp.toDate().getTime());
                            cancelarNotificacionChat(chatId);
                        }
                    }
                });
    }

    private void enviarMensajeTexto() {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(counterpartUid)) {
            Toast.makeText(this, "No hay vínculo activo para chatear", Toast.LENGTH_SHORT).show();
            return;
        }

        String contenido = etMensajeChat.getText().toString().trim();
        if (TextUtils.isEmpty(contenido)) {
            return;
        }

        enviarMensajeBase(TIPO_MENSAJE_TEXTO, contenido, null, null, null, contenido);
        etMensajeChat.setText("");
        actualizarEstadoEscribiendo(false);
    }

    private void enviarImagenAdjunta(Uri uri) {
        if (uri == null) {
            return;
        }
        subirAdjuntoYEnviar(uri, TIPO_MENSAJE_IMAGEN);
    }

    private void subirAdjuntoYEnviar(@NonNull Uri uri, @NonNull String tipoMensaje) {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(counterpartUid)) {
            Toast.makeText(this, "No hay vínculo activo para chatear", Toast.LENGTH_SHORT).show();
            return;
        }

        String mimeType = defaultString(getContentResolver().getType(uri), "application/octet-stream");
        String nombreOriginal = obtenerNombreDesdeUri(uri);
        if (TextUtils.isEmpty(nombreOriginal)) {
            nombreOriginal = TIPO_MENSAJE_IMAGEN.equals(tipoMensaje) ? "imagen.jpg" : "archivo";
        }
        final String nombreOriginalFinal = nombreOriginal;
        final String mimeTypeFinal = mimeType;

        byte[] data = TIPO_MENSAJE_IMAGEN.equals(tipoMensaje)
                ? comprimirImagenAdjunta(uri)
                : leerBytesConLimite(uri, MAX_CHAT_FILE_BYTES);

        if (data == null || data.length == 0) {
            Toast.makeText(this, "No se pudo procesar el adjunto", Toast.LENGTH_SHORT).show();
            return;
        }

        String mediaBase64 = Base64.encodeToString(data, Base64.NO_WRAP);
        String contenido = TIPO_MENSAJE_IMAGEN.equals(tipoMensaje)
                ? "📷 Imagen"
                : "📎 " + nombreOriginalFinal;

        enviarMensajeBase(
                tipoMensaje,
                contenido,
                mediaBase64,
                nombreOriginalFinal,
                mimeTypeFinal,
                contenido
        );
    }

    private void enviarMensajeBase(@NonNull String tipo,
                                   @NonNull String contenido,
                                   String mediaBase64,
                                   String fileName,
                                   String mimeType,
                                   @NonNull String ultimoMensaje) {
        if (TextUtils.isEmpty(chatId)) {
            return;
        }

        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("remitenteId", currentUid);
        mensaje.put("remitenteNombre", currentNombre != null ? currentNombre : "Usuario");
        mensaje.put("contenido", contenido);
        mensaje.put("tipo", tipo);
        if (!TextUtils.isEmpty(mediaBase64)) {
            mensaje.put("mediaBase64", mediaBase64);
        }
        if (!TextUtils.isEmpty(fileName)) {
            mensaje.put("fileName", fileName);
        }
        if (!TextUtils.isEmpty(mimeType)) {
            mensaje.put("mimeType", mimeType);
        }
        mensaje.put("timestamp", FieldValue.serverTimestamp());

        String messageId = firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .document().getId();

        Map<String, Object> conversacion = new HashMap<>();
        conversacion.put("guardianId", guardianId);
        conversacion.put("exploradorId", exploradorId);
        conversacion.put("ultimoMensaje", ultimoMensaje);
        conversacion.put("ultimoRemitenteId", currentUid);
        conversacion.put("actualizadoEn", FieldValue.serverTimestamp());

        firestore.collection("chats")
                .document(chatId)
                .set(conversacion, SetOptions.merge());

        firestore.collection("chats")
                .document(chatId)
                .collection("mensajes")
                .document(messageId)
                .set(mensaje)
                .addOnSuccessListener(unused -> {
                    listViewChat.post(() -> listViewChat.setSelection(adapter.getCount() - 1));
                    enviarNotificacionPush(currentNombre != null ? currentNombre : "Aura", contenido);
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "No se pudo enviar el mensaje", Toast.LENGTH_SHORT).show());
    }

    private void enviarNotificacionPush(String titulo, String cuerpo) {
        if (TextUtils.isEmpty(counterpartFcmToken) || "TU_CLAVE_AQUI".equals(FCM_SERVER_KEY)) {
            android.util.Log.w("FCM", "No se puede enviar notificación: Token o Key faltante");
            return;
        }

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://fcm.googleapis.com/fcm/send");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "key=" + FCM_SERVER_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                org.json.JSONObject notification = new org.json.JSONObject();
                notification.put("title", titulo);
                notification.put("body", cuerpo);
                notification.put("sound", "default");

                org.json.JSONObject data = new org.json.JSONObject();
                data.put("tipo", "chat");

                org.json.JSONObject json = new org.json.JSONObject();
                json.put("to", counterpartFcmToken);
                json.put("notification", notification);
                json.put("data", data);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                android.util.Log.d("FCM", "Response Code: " + responseCode);

            } catch (Exception e) {
                android.util.Log.e("FCM", "Error enviando notificación", e);
            }
        }).start();
    }

    private void actualizarEstadoEscribiendo(boolean escribiendo) {
        if (TextUtils.isEmpty(chatId) || TextUtils.isEmpty(currentUid)) {
            return;
        }
        if (localEscribiendo == escribiendo) {
            return;
        }

        localEscribiendo = escribiendo;
        Map<String, Object> updates = new HashMap<>();
        updates.put("typing." + currentUid, escribiendo);
        updates.put("actualizadoEn", FieldValue.serverTimestamp());

        firestore.collection("chats")
                .document(chatId)
                .set(updates, SetOptions.merge());
    }

    private void actualizarPresencia(boolean enLinea) {
        if (TextUtils.isEmpty(currentUid)) {
            return;
        }

        Map<String, Object> presencia = new HashMap<>();
        presencia.put(KEY_ESTADO_CONEXION, enLinea ? "EN_LINEA" : "ULTIMA_CONEXION");
        presencia.put(KEY_ULTIMA_CONEXION, FieldValue.serverTimestamp());

        firestore.collection("usuarios")
                .document(currentUid)
                .set(presencia, SetOptions.merge());
    }

    private void mostrarEstadoSinVinculo(@NonNull String mensaje) {
        tvNombreContacto.setText("Chat vinculado");
        tvEstadoContacto.setText(mensaje);
        tvChatVacio.setText(mensaje);
        tvChatVacio.setVisibility(View.VISIBLE);
        ivAvatarContacto.setImageResource(R.drawable.ic_launcher_foreground);
    }

    private void deshabilitarChat() {
        etMensajeChat.setEnabled(false);
        findViewById(R.id.btnEnviarChat).setEnabled(false);
        findViewById(R.id.btnMasOpcionesChat).setEnabled(false);
    }

    private void mostrarMenuAdjuntos(@NonNull View anchor) {
        if (popupAdjuntos != null && popupAdjuntos.isShowing()) {
            popupAdjuntos.dismiss();
            return;
        }

        View contenido = LayoutInflater.from(this)
                .inflate(R.layout.popup_chat_adjuntos, null, false);

        View opcionImagen = contenido.findViewById(R.id.opcionAdjuntarImagen);

        popupAdjuntos = new PopupWindow(
                contenido,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupAdjuntos.setOutsideTouchable(true);
        popupAdjuntos.setElevation(dpToPx(8));

        opcionImagen.setOnClickListener(v -> {
            popupAdjuntos.dismiss();
            seleccionarImagenLauncher.launch("image/*");
        });

        contenido.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        int popupWidth = contenido.getMeasuredWidth();
        int popupHeight = contenido.getMeasuredHeight();
        int xOffset = (anchor.getWidth() - popupWidth) / 2;
        int yOffset = -(anchor.getHeight() + popupHeight + dpToPx(8));

        popupAdjuntos.showAsDropDown(anchor, xOffset, yOffset, Gravity.START);
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    private void mostrarImagenAvatarCompleta() {
        if (!TextUtils.isEmpty(counterpartFotoBase64)) {
            mostrarPopupImagen(decodificarBase64Seguro(counterpartFotoBase64), null);
        } else if (!TextUtils.isEmpty(counterpartFotoUrl)) {
            mostrarPopupImagen(null, counterpartFotoUrl);
        } else {
            mostrarPopupImagen(null, null);
        }
    }

    private void mostrarPopupImagen(byte[] data, String url) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.85f);
        }

        ImageView imageView = new ImageView(this);
        int size = dpToPx(350);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (data != null && data.length > 0) {
            Glide.with(this).load(data).into(imageView);
        } else if (!TextUtils.isEmpty(url)) {
            Glide.with(this).load(url).into(imageView);
        } else {
            Glide.with(this).load(R.drawable.ic_launcher_foreground).into(imageView);
        }

        dialog.setContentView(imageView);
        dialog.setCanceledOnTouchOutside(true);
        imageView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String construirChatId(@NonNull String guardian, @NonNull String explorador) {
        return guardian + "_" + explorador;
    }

    private String defaultString(String valor, String porDefecto) {
        return valor != null && !valor.trim().isEmpty() ? valor : porDefecto;
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualizarPresencia(true);
        if (!TextUtils.isEmpty(chatId)) {
            cancelarNotificacionChat(chatId);
        }
    }

    @Override
    protected void onPause() {
        actualizarEstadoEscribiendo(false);
        actualizarPresencia(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (popupAdjuntos != null) {
            popupAdjuntos.dismiss();
            popupAdjuntos = null;
        }
        if (listenerChat != null) {
            listenerChat.remove();
            listenerChat = null;
        }
        if (listenerContraparte != null) {
            listenerContraparte.remove();
            listenerContraparte = null;
        }
        if (listenerConversacion != null) {
            listenerConversacion.remove();
            listenerConversacion = null;
        }
        actualizarEstadoEscribiendo(false);
        actualizarPresencia(false);
        super.onDestroy();
    }

    private class ChatAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mensajes.size();
        }

        @Override
        public Object getItem(int position) {
            return mensajes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ChatViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(PantallaChat.this)
                        .inflate(R.layout.item_chat_mensaje, parent, false);
                holder = new ChatViewHolder();
                holder.containerRecibido = convertView.findViewById(R.id.containerRecibido);
                holder.containerEnviado = convertView.findViewById(R.id.containerEnviado);
                holder.tvAvatarRecibido = convertView.findViewById(R.id.tvAvatarRecibido);
                holder.tvNombreRecibido = convertView.findViewById(R.id.tvNombreRecibido);
                holder.tvMensajeRecibido = convertView.findViewById(R.id.tvMensajeRecibido);
                holder.tvHoraRecibido = convertView.findViewById(R.id.tvHoraRecibido);
                holder.ivImagenRecibida = convertView.findViewById(R.id.ivImagenRecibida);
                holder.tvArchivoRecibido = convertView.findViewById(R.id.tvArchivoRecibido);
                holder.tvMensajeEnviado = convertView.findViewById(R.id.tvMensajeEnviado);
                holder.tvHoraEnviado = convertView.findViewById(R.id.tvHoraEnviado);
                holder.ivImagenEnviada = convertView.findViewById(R.id.ivImagenEnviada);
                holder.tvArchivoEnviado = convertView.findViewById(R.id.tvArchivoEnviado);
                holder.tvSeparadorFecha = convertView.findViewById(R.id.tvSeparadorFecha);
                convertView.setTag(holder);
            } else {
                holder = (ChatViewHolder) convertView.getTag();
            }

            MensajeChat mensaje = mensajes.get(position);
            boolean esMio = mensaje.mio;
            String hora = mensaje.timestamp != null
                    ? new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(mensaje.timestamp.toDate())
                    : "";

            // --- LÓGICA DE SEPARADOR DE FECHA ---
            if (mensaje.timestamp != null) {
                String fechaActual = obtenerFechaLegible(mensaje.timestamp.toDate());
                boolean mostrarSeparador = true;

                if (position > 0) {
                    MensajeChat mensajeAnterior = mensajes.get(position - 1);
                    if (mensajeAnterior.timestamp != null) {
                        String fechaAnterior = obtenerFechaLegible(mensajeAnterior.timestamp.toDate());
                        if (fechaActual.equals(fechaAnterior)) {
                            mostrarSeparador = false;
                        }
                    }
                }

                if (mostrarSeparador) {
                    holder.tvSeparadorFecha.setVisibility(View.VISIBLE);
                    holder.tvSeparadorFecha.setText(fechaActual);
                } else {
                    holder.tvSeparadorFecha.setVisibility(View.GONE);
                }
            } else {
                holder.tvSeparadorFecha.setVisibility(View.GONE);
            }
            // ------------------------------------

            String tipo = defaultString(mensaje.tipo, TIPO_MENSAJE_TEXTO);
            boolean tieneMedia = !TextUtils.isEmpty(mensaje.mediaBase64) || !TextUtils.isEmpty(mensaje.mediaUrl);
            boolean esImagen = TIPO_MENSAJE_IMAGEN.equals(tipo) && tieneMedia;
            boolean esArchivo = TIPO_MENSAJE_ARCHIVO.equals(tipo) && tieneMedia;
            String textoBase = mensaje.contenido;
            if (TextUtils.isEmpty(textoBase)) {
                if (esImagen) {
                    textoBase = "📷 Imagen";
                } else if (esArchivo) {
                    textoBase = "📎 " + defaultString(mensaje.fileName, "Archivo");
                }
            }

            if (esMio) {
                holder.containerRecibido.setVisibility(View.GONE);
                holder.containerEnviado.setVisibility(View.VISIBLE);
                holder.tvMensajeEnviado.setText(defaultString(textoBase, ""));
                holder.tvMensajeEnviado.setVisibility(TextUtils.isEmpty(textoBase) ? View.GONE : View.VISIBLE);
                holder.tvHoraEnviado.setText(hora);

                holder.ivImagenEnviada.setVisibility(esImagen ? View.VISIBLE : View.GONE);
                if (esImagen) {
                    if (!TextUtils.isEmpty(mensaje.mediaBase64)) {
                        byte[] data = decodificarBase64Seguro(mensaje.mediaBase64);
                        if (data != null && data.length > 0) {
                            Glide.with(PantallaChat.this)
                                    .asBitmap()
                                    .load(data)
                                    .centerCrop()
                                    .into(holder.ivImagenEnviada);
                        } else {
                            holder.ivImagenEnviada.setImageDrawable(null);
                        }
                    } else {
                        Glide.with(PantallaChat.this)
                                .load(mensaje.mediaUrl)
                                .centerCrop()
                                .into(holder.ivImagenEnviada);
                    }
                    holder.ivImagenEnviada.setOnClickListener(v -> abrirAdjunto(mensaje));
                } else {
                    holder.ivImagenEnviada.setOnClickListener(null);
                }

                holder.tvArchivoEnviado.setVisibility(esArchivo ? View.VISIBLE : View.GONE);
                if (esArchivo) {
                    holder.tvArchivoEnviado.setText("Abrir: " + defaultString(mensaje.fileName, "Archivo"));
                    holder.tvArchivoEnviado.setOnClickListener(v -> abrirAdjunto(mensaje));
                } else {
                    holder.tvArchivoEnviado.setOnClickListener(null);
                }
            } else {
                holder.containerEnviado.setVisibility(View.GONE);
                holder.containerRecibido.setVisibility(View.VISIBLE);
                holder.tvNombreRecibido.setText(defaultString(mensaje.remitenteNombre,
                        counterpartNombre != null ? counterpartNombre : "Contacto"));
                holder.tvMensajeRecibido.setText(defaultString(textoBase, ""));
                holder.tvMensajeRecibido.setVisibility(TextUtils.isEmpty(textoBase) ? View.GONE : View.VISIBLE);
                holder.tvHoraRecibido.setText(hora);
                holder.tvAvatarRecibido.setText(obtenerIniciales(defaultString(mensaje.remitenteNombre,
                        counterpartNombre != null ? counterpartNombre : "C")));

                holder.ivImagenRecibida.setVisibility(esImagen ? View.VISIBLE : View.GONE);
                if (esImagen) {
                    if (!TextUtils.isEmpty(mensaje.mediaBase64)) {
                        byte[] data = decodificarBase64Seguro(mensaje.mediaBase64);
                        if (data != null && data.length > 0) {
                            Glide.with(PantallaChat.this)
                                    .asBitmap()
                                    .load(data)
                                    .centerCrop()
                                    .into(holder.ivImagenRecibida);
                        } else {
                            holder.ivImagenRecibida.setImageDrawable(null);
                        }
                    } else {
                        Glide.with(PantallaChat.this)
                                .load(mensaje.mediaUrl)
                                .centerCrop()
                                .into(holder.ivImagenRecibida);
                    }
                    holder.ivImagenRecibida.setOnClickListener(v -> abrirAdjunto(mensaje));
                } else {
                    holder.ivImagenRecibida.setOnClickListener(null);
                }

                holder.tvArchivoRecibido.setVisibility(esArchivo ? View.VISIBLE : View.GONE);
                if (esArchivo) {
                    holder.tvArchivoRecibido.setText("Abrir: " + defaultString(mensaje.fileName, "Archivo"));
                    holder.tvArchivoRecibido.setOnClickListener(v -> abrirAdjunto(mensaje));
                } else {
                    holder.tvArchivoRecibido.setOnClickListener(null);
                }
            }

            return convertView;
        }
    }

    private String obtenerIniciales(@NonNull String nombre) {
        String[] partes = nombre.trim().split("\\s+");
        if (partes.length == 0) {
            return "?";
        }
        if (partes.length == 1) {
            return partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase(Locale.getDefault());
        }

        String primera = partes[0].isEmpty() ? "" : partes[0].substring(0, 1);
        String segunda = partes[1].isEmpty() ? "" : partes[1].substring(0, 1);
        return (primera + segunda).toUpperCase(Locale.getDefault());
    }

    private void abrirAdjunto(@NonNull MensajeChat mensaje) {
        if (TIPO_MENSAJE_IMAGEN.equals(mensaje.tipo)) {
            if (!TextUtils.isEmpty(mensaje.mediaBase64)) {
                mostrarPopupImagen(decodificarBase64Seguro(mensaje.mediaBase64), null);
            } else if (!TextUtils.isEmpty(mensaje.mediaUrl)) {
                mostrarPopupImagen(null, mensaje.mediaUrl);
            }
            return;
        }

        if (!TextUtils.isEmpty(mensaje.mediaUrl)) {
            abrirUrlAdjunto(mensaje.mediaUrl, mensaje.mimeType);
            return;
        }

        if (!TextUtils.isEmpty(mensaje.mediaBase64)) {
            Toast.makeText(this, "Archivo listo para descargar", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "No se encontró el adjunto", Toast.LENGTH_SHORT).show();
    }

    private void abrirUrlAdjunto(@NonNull String url, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(url);
            if (!TextUtils.isEmpty(mimeType)) {
                intent.setDataAndType(uri, mimeType);
            } else {
                intent.setData(uri);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir el adjunto", Toast.LENGTH_SHORT).show();
        }
    }

    private String obtenerNombreDesdeUri(@NonNull Uri uri) {
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        return cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        int cut = path.lastIndexOf('/');
        return cut != -1 ? path.substring(cut + 1) : path;
    }

    private String limpiarNombreArchivo(@NonNull String nombre) {
        return nombre.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private byte[] comprimirImagenAdjunta(@NonNull Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                return null;
            }

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int maxDim = 1280;
            if (width > maxDim || height > maxDim) {
                float ratio = Math.min((float) maxDim / width, (float) maxDim / height);
                bitmap = Bitmap.createScaledBitmap(bitmap,
                        Math.round(width * ratio),
                        Math.round(height * ratio),
                        true);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int quality = 80;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            while (out.size() > MAX_CHAT_IMAGE_BYTES && quality > 30) {
                out.reset();
                quality -= 8;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            }

            if (out.size() > MAX_CHAT_IMAGE_BYTES) {
                Toast.makeText(this, "La imagen es demasiado grande para Firestore", Toast.LENGTH_SHORT).show();
                return null;
            }
            return out.toByteArray();
        } catch (Exception e) {
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
                    Toast.makeText(this, "Archivo demasiado grande para Firestore", Toast.LENGTH_SHORT).show();
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
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
    private String obtenerFechaLegible(Date date) {
        Calendar calHoy = Calendar.getInstance();
        Calendar calAyer = Calendar.getInstance();
        calAyer.add(Calendar.DATE, -1);

        Calendar calMsg = Calendar.getInstance();
        calMsg.setTime(date);

        if (esMismoDia(calHoy, calMsg)) {
            return "Hoy";
        } else if (esMismoDia(calAyer, calMsg)) {
            return "Ayer";
        } else {
            return new SimpleDateFormat("dd 'de' MMMM 'de' yyyy", new Locale("es", "ES")).format(date);
        }
    }

    private boolean esMismoDia(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }
}