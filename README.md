# Aura

Aura es una aplicación Android de vigilancia y comunicación en tiempo real entre un "Guardia" y un "Explorador". Utiliza Firebase Authentication y Firestore para gestionar usuarios, vinculación, chats, notificaciones y datos de ubicación.

## Características

- Registro e inicio de sesión con Firebase Authentication.
- Gestión de perfiles de usuario.
- Roles de usuario: `GUARDIAN` y `EXPLORADOR`.
- Vinculación mediante código entre guardian y explorador.
- Chat bidireccional con historial almacenado en Firestore.
- Notificaciones push y servicio de notificaciones en primer plano.
- Página de ajustes con cierre de sesión y eliminación de cuenta.
- Eliminación de cuenta inmediata, borrando usuario, chats, vinculación y datos relacionados.
- Limpieza de historial del guardián en Firestore.
- Envío de mensajes de soporte técnico.
- Visualización de la ubicación del explorador en un WebView de mapa.

## Estructura principal

- `app/src/main/java/com/example/aura/` — código fuente de la aplicación.
- `app/src/main/res/layout/` — archivos de diseño XML para las pantallas y diálogos.
- `app/src/main/AndroidManifest.xml` — configuración del manifiesto de Android.
- `app/build.gradle.kts` — configuración de Gradle para el módulo de la app.

## Flujo de la aplicación

1. El usuario se registra o inicia sesión.
2. Se determina el tipo de usuario (`GUARDIAN` o `EXPLORADOR`).
3. El explorador comparte un código y el guardián se vincula.
4. Ambos pueden comunicarse por chat.
5. El guardián visualiza la ubicación del explorador y notificaciones históricas.
6. En ajustes, el usuario puede cerrar sesión o eliminar su cuenta.

## Seguridad y borrado de datos

- La eliminación de cuenta elimina los datos del usuario en Firestore de inmediato.
- Se eliminan los documentos de:
  - `usuarios/{uid}`
  - `vinculos/*`
  - `chats/{chatId}/mensajes/*`
  - `mensajes/{guardianUid}/historial/*`
- Si el usuario autenticado elimina su cuenta, la sesión también se cierra.

## Desarrolladores

- Ariel Muñoz
- Miguel Rosales

## Cómo ejecutar

1. Abre el proyecto en Android Studio.
2. Configura `google-services.json` en `app/`.
3. Sincroniza Gradle.
4. Ejecuta la app en un dispositivo o emulador.

## Notas

- Aura está diseñada para usarse con Firebase Firestore y Firebase Auth.
- El código actual asume un modelo de datos con colecciones como `usuarios`, `vinculos`, `chats`, `mensajes` y `soporte_tecnico`.
- El botón de "Limpiar historial" del guardián elimina el historial remoto en Firestore y actualiza la vista.
