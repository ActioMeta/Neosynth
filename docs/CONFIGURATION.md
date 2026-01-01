# Configuración y Uso

## Primera Configuración

### Conexión al Servidor Navidrome

1. **Abrir NeoSynth** por primera vez
2. **Pantalla de Login** se mostrará automáticamente
3. **Introducir datos del servidor:**

   - **URL del servidor:** 
     - Formato: `https://music.tudominio.com` o `http://192.168.1.100:4533`
     - Incluye el protocolo (`http://` o `https://`)
     - No incluyas `/rest` al final
   
   - **Usuario:** 
     - Tu nombre de usuario en Navidrome
   
   - **Contraseña:**
     - Tu contraseña de Navidrome
     - Se genera un token MD5, la contraseña no se almacena

4. **Tocar "Login"**
5. **Verificación:**
   - Si es exitoso, verás la pantalla principal con tus álbumes
   - Si falla, revisa la URL y credenciales

### Configuración de Múltiples Servidores

NeoSynth permite conectarte a varios servidores Navidrome simultáneamente:

1. **Ir a Settings** (ícono de engranaje en la pantalla principal)
2. **Tocar "Add Server"**
3. **Introducir datos del nuevo servidor**
4. **Seleccionar servidor activo:**
   - En la lista de servidores, toca el que deseas usar
   - El servidor activo se marca con un indicador
5. **Cambiar entre servidores:**
   - Vuelve a Settings y selecciona otro servidor
   - La app recargará el contenido del nuevo servidor

## Navegación por la Interfaz

### Pantalla Principal (Home)

**Carrusel de Álbumes**
- Muestra los álbumes recién agregados al servidor
- Desliza horizontalmente para ver más
- Toca un álbum para ver sus detalles

**Botones Principales**
- **Library:** Acceso a toda la biblioteca (proximamente)
- **Mix Aleatorio:** Reproduce canciones al azar de tu biblioteca

**Menú Contextual en Álbumes**
- **Long press** en un álbum para abrir el menú:
  - **Reproducir:** Reproduce el álbum en orden
  - **Shuffle:** Reproduce el álbum en orden aleatorio
  - **Descargar:** Descarga todas las canciones del álbum
  - **Ir al Artista:** Navega a la página del artista (proximamente)

### Pantalla de Álbum

**Información del Álbum**
- Carátula grande
- Nombre del álbum
- Nombre del artista
- Año de lanzamiento

**Lista de Canciones**
- Número de track
- Título de la canción
- Duración
- Ícono de descarga si está disponible offline

**Acciones**
- **Botón Play (grande):** Reproduce el álbum completo
- **Botón Shuffle:** Reproduce en orden aleatorio
- **Botón Descargar:** Descarga el álbum completo
- **Toque en canción:** Reproduce desde esa canción

### Reproductor de Música

**Mini Reproductor**
- Visible en la parte inferior de todas las pantallas
- Muestra: carátula, título, artista
- Controles: play/pause, siguiente
- Toca para expandir al reproductor completo

**Reproductor Completo**
- **Carátula grande:** Imagen del álbum
- **Información:** Título, artista, álbum
- **Controles:**
  - Anterior
  - Play/Pause
  - Siguiente
  - Shuffle (aleatorio)
  - Repeat (repetir: off/all/one)
- **Barra de progreso:**
  - Desliza para cambiar posición
  - Tiempo actual / Tiempo total
- **Gestos:**
  - Desliza hacia abajo para minimizar
  - Desliza horizontalmente en la carátula para siguiente/anterior

### Pantalla de Descargas

**Vista General**
- Lista de todas las canciones descargadas
- Organización alfabética con sticky headers (A-Z)
- Modo offline: reproduce sin conexión

**Secciones**
1. **Playlists Descargadas**
   - Muestra playlists completas offline
   - Toca para reproducir
   - Long press para eliminar

2. **Canciones Individuales**
   - Ordenadas alfabéticamente por título
   - Headers pegajosos (A, B, C...)
   - Reproducción directa

**Filtros (FAB Expandible)**
- **Todos:** Muestra todas las descargas
- **Por Álbum:** Agrupa canciones por álbum
- **Por Artista:** Agrupa canciones por artista

**Acciones en Canciones**
- **Toque simple:** Reproduce la canción
- **Long press:** Abre menú contextual
  - Eliminar descarga
  - Ver álbum
  - Ver artista

### Pantalla de Playlists

**Lista de Playlists**
- Muestra todas las playlists del servidor
- Carátula compuesta de las primeras canciones
- Contador de canciones

**Detalle de Playlist**
- Nombre de la playlist
- Lista de canciones con carátulas
- Botón de reproducción
- Botón de descarga de playlist completa

**Acciones**
- **Reproducir:** Reproduce la playlist en orden
- **Descargar:** Descarga todas las canciones
- **Toque en canción:** Reproduce desde esa canción

## Reproducción de Música

### Streaming en Tiempo Real

**Reproducción Normal**
1. Navega a un álbum o playlist
2. Toca el botón de reproducción o una canción
3. La música se transmite desde el servidor
4. Requiere conexión a internet

**Cola de Reproducción**
- Añade canciones a la cola actual
- Visualiza la lista de próximas canciones
- Reordena canciones en la cola
- Limpia la cola

### Reproducción Offline

**Descargar Contenido**

1. **Canción Individual:**
   - Abre el detalle de álbum/playlist
   - Toca el ícono de descarga junto a la canción

2. **Álbum Completo:**
   - Long press en el álbum
   - Selecciona "Descargar"
   - Alternativamente, abre el álbum y toca el botón de descarga

3. **Playlist Completa:**
   - Abre la playlist
   - Toca el botón de descarga
   - Todas las canciones se descargarán en segundo plano

**Gestión de Descargas**
- Las descargas se realizan en segundo plano
- Notificación muestra el progreso
- Continúan aunque cierres la app
- Requieren conexión de red activa

**Reproducir Offline**
1. Ve a la pantalla "Downloads"
2. Selecciona la canción/playlist descargada
3. Reproduce sin conexión a internet

### Controles de Reproducción

**Notificación de Reproducción**
- Siempre visible mientras se reproduce
- Controles: play/pause, siguiente, anterior
- Información de la canción actual
- Toca para abrir la app

**Controles del Sistema**
- **Bloqueo de pantalla:** Controles de media en lockscreen
- **Bluetooth/Auto:** Controles en dispositivos conectados
- **Auriculares:** Play/pause al desconectar

**Modos de Reproducción**
- **Normal:** Reproduce la cola una vez
- **Repeat All:** Repite toda la cola indefinidamente
- **Repeat One:** Repite la canción actual
- **Shuffle:** Orden aleatorio de la cola

## Integración con Google Assistant

### Configuración

**Requisitos**
- Android 9+ (API 28+)
- Google app instalada y configurada
- Micrófono habilitado

**Primera Configuración**
1. Di: "Ok Google, reproduce música en NeoSynth"
2. Si se solicita, selecciona NeoSynth como app de música
3. Otorga permisos necesarios

### Comandos de Voz Disponibles

**Reproducir por Título**
```
"Ok Google, reproduce Bohemian Rhapsody en NeoSynth"
"Ok Google, pon Despacito en NeoSynth"
```

**Reproducir por Artista**
```
"Ok Google, reproduce música de Queen en NeoSynth"
"Ok Google, pon canciones de Coldplay en NeoSynth"
```

**Reproducir por Álbum**
```
"Ok Google, reproduce el álbum Dark Side of the Moon en NeoSynth"
"Ok Google, pon el disco Thriller en NeoSynth"
```

**Reproducción General**
```
"Ok Google, reproduce música en NeoSynth"
"Ok Google, pon mi música en NeoSynth"
```

**Limitaciones**
- Solo busca en canciones descargadas (no streaming por voz)
- Requiere coincidencias exactas o muy cercanas
- No soporta playlists por voz actualmente

## Favoritos y Sincronización

### Marcar como Favorito

1. **Desde Álbum/Playlist:**
   - Abre el detalle
   - Toca el ícono de estrella junto a una canción

2. **Desde Reproductor:**
   - Mientras se reproduce
   - Toca el ícono de estrella

**Sincronización con Servidor**
- Los favoritos se sincronizan con Navidrome
- Marca una canción como "starred" en el servidor
- Visible en otros clientes de Navidrome

### Ver Favoritos
- Ir a "Library" > "Favoritos" (próximamente)
- Filtrar descargas por favoritos

## Configuración Avanzada

### Preferencias

**Calidad de Audio**
- **Streaming:** Calidad ajustada automáticamente por Navidrome
- **Descargas:** Formato original del servidor

**Almacenamiento**
- Ubicación de descargas: `/Android/data/com.example.neosynth/files/Music/`
- Limpieza automática: deshabilitada (manual)

**Tema**
- **Material You (Android 12+):** Colores dinámicos del wallpaper
- **Android 9-11:** Paleta estática
- **Modo Oscuro:** Automático según sistema o manual

### Gestión de Datos

**Ver Espacio Usado**
1. Ve a Settings
2. Sección "Storage"
3. Muestra MB/GB usados por descargas

**Limpiar Caché**
1. Settings > Storage
2. "Clear Cache"
3. Limpia imágenes y datos temporales

**Eliminar Todas las Descargas**
1. Settings > Storage
2. "Delete All Downloads"
3. Confirmar acción

**Backup de Base de Datos**
- La base de datos Room se almacena en:
  `/data/data/com.example.neosynth/databases/`
- Requiere root para acceso manual
- Backup automático de Android lo incluye

## Resolución de Problemas

### Problemas de Reproducción

**No se Reproduce el Audio**
1. Verifica conexión a internet (si es streaming)
2. Comprueba que el archivo esté descargado (si es offline)
3. Reinicia la app
4. Verifica que el servidor esté accesible

**Audio Entrecortado**
- Revisa la velocidad de conexión
- Prueba con una red más rápida
- Descarga para reproducir offline

**No se Muestran Carátulas**
- Verifica conexión a internet
- Limpia la caché de imágenes en Settings

### Problemas de Descargas

**Descargas No Inician**
1. Verifica conexión a red
2. Comprueba espacio disponible en dispositivo
3. Revisa permisos de almacenamiento

**Descargas se Pausan**
- Android puede pausar por batería baja
- Desactiva optimización de batería para NeoSynth:
  - Settings > Apps > NeoSynth > Battery > Unrestricted

**Descargas Fallan**
- Revisa conexión al servidor
- Verifica que el archivo exista en el servidor
- Elimina y reintenta la descarga

### Problemas de Conexión

**No se Conecta al Servidor**
1. Verifica la URL (con `http://` o `https://`)
2. Comprueba que Navidrome esté ejecutándose
3. Prueba la URL en un navegador
4. Verifica firewall/router

**Conexión se Pierde Frecuentemente**
- Revisa estabilidad de la red
- Considera descargar contenido
- Verifica timeout del servidor Navidrome

## Shortcuts y Atajos

### Atajos de App (Android 7.1+)

**Long Press en el Ícono**
- **Shuffle All:** Reproducción aleatoria inmediata
- **Descargas:** Abre directamente las descargas
- **Continuar:** Reanuda última reproducción

### Widgets (Próximamente)
- Widget de reproductor en pantalla de inicio
- Widget de controles rápidos
- Widget mini player

## Consejos y Trucos

### Optimizar Almacenamiento
1. Descarga solo álbumes favoritos
2. Limpia descargas antiguas regularmente
3. Usa streaming para música ocasional

### Mejor Experiencia de Audio
1. Usa auriculares/audífonos de calidad
2. Habilita ecualizador del sistema si está disponible
3. Descarga música para evitar buffering

### Organización de Biblioteca
1. Usa playlists para agrupaciones temáticas
2. Descarga playlists completas para viajes
3. Marca favoritos para acceso rápido

### Ahorro de Datos
1. Descarga por WiFi antes de salir
2. Usa modo offline cuando sea posible
3. Evita streaming en datos móviles

## Glosario

**Streaming:** Reproducción directa desde el servidor sin descargar
**Offline:** Reproducción de archivos descargados sin internet
**Navidrome:** Servidor de música open source compatible con Subsonic API
**Material You:** Sistema de diseño dinámico de Android 12+
**StateFlow:** Mecanismo de actualización reactiva de UI
**WorkManager:** Sistema de tareas en segundo plano de Android
