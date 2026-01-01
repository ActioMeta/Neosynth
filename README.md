# 🎵 NeoSynth

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="120" alt="NeoSynth Logo"/>
</p>

<p align="center">
  <strong>Cliente de música moderno para servidores Navidrome/Subsonic</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-28%2B-green?logo=android" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material3-blue?logo=jetpackcompose" alt="Compose"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License"/>
</p>

---

## 📱 Descripción

**NeoSynth** es un cliente de música para Android que se conecta a servidores **Navidrome** y **Subsonic**, permitiéndote reproducir tu biblioteca musical personal desde cualquier lugar. Diseñado con las últimas tecnologías de Android y siguiendo las guías de **Material Design 3**.

---

## ✨ Características

### 🎶 Reproducción
- Streaming de audio en tiempo real desde tu servidor
- Reproducción en segundo plano con notificaciones de control
- Cola de reproducción con soporte para shuffle y repeat
- Mini reproductor persistente durante la navegación
- Reproductor a pantalla completa con controles gestuales

### 📥 Descargas
- Descarga de canciones individuales o álbumes completos
- Descargas en segundo plano con WorkManager
- Organización alfabética con sticky headers (A-Z)
- Modo de selección múltiple
- Reproducción offline de contenido descargado

### 🏠 Interfaz Home
- Carrusel de álbumes recién agregados
- Mix aleatorio con un toque
- Menú contextual en cada álbum (long press):
  - ▶️ Reproducir
  - 🔀 Reproducir aleatorio
  - 📥 Descargar álbum
  - 👤 Ir al artista

### 🔐 Multi-servidor
- Soporte para múltiples servidores Navidrome/Subsonic
- Autenticación segura con tokens MD5
- Cambio rápido entre servidores

---

## 🎨 Material Design 3

NeoSynth implementa completamente **Material Design 3** (Material You):

### Theming Dinámico
```kotlin
@Composable
fun NeoSynth_androidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Colores del wallpaper
    content: @Composable () -> Unit
)
```

### Componentes M3 Utilizados
| Componente | Uso en NeoSynth |
|------------|-----------------|
| `Scaffold` | Estructura base de pantallas |
| `TopAppBar` | Barras superiores dinámicas |
| `FloatingActionButton` | FAB expandible en Downloads |
| `Card` | Carátulas de álbumes |
| `DropdownMenu` | Menú contextual de acciones |
| `Slider` | Control de progreso del reproductor |
| `Surface` | Botón de play/pause |
| `IconButton` | Controles de reproducción |

### Tokens de Color
```kotlin
MaterialTheme.colorScheme.primary          // Acentos principales
MaterialTheme.colorScheme.surface          // Fondos de tarjetas
MaterialTheme.colorScheme.surfaceVariant   // Fondos secundarios
MaterialTheme.colorScheme.onPrimary        // Texto sobre primary
MaterialTheme.colorScheme.primaryContainer // Botones activos
```

### Tipografía M3
```kotlin
MaterialTheme.typography.displayLarge   // Títulos hero
MaterialTheme.typography.headlineMedium // Título de canción
MaterialTheme.typography.titleLarge     // Secciones
MaterialTheme.typography.bodyMedium     // Texto general
```

---

## 🏗️ Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                         UI LAYER                            │
│  Jetpack Compose + Material 3 + Navigation                  │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                      VIEWMODEL LAYER                        │
│  Hilt + StateFlow + Coroutines                              │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                       DATA LAYER                            │
│  Room (local) + Retrofit (remote) + WorkManager             │
└─────────────────────────────────────────────────────────────┘
```

### Estructura de Carpetas
```
app/src/main/java/com/example/neosynth/
├── data/
│   ├── local/          # Room Database, DAOs, Entities
│   ├── remote/         # Retrofit API, DTOs, Mappers
│   ├── repository/     # Repositorios de datos
│   └── worker/         # WorkManager para descargas
├── domain/
│   ├── model/          # Modelos de negocio (Song, Album, etc.)
│   └── provider/       # Interfaces de proveedores
├── player/
│   ├── MusicController # Controlador de reproducción
│   └── PlaybackService # Servicio foreground Media3
├── ui/
│   ├── components/     # Componentes reutilizables
│   ├── home/           # Pantalla principal
│   ├── player/         # Reproductor completo
│   ├── downloads/      # Gestión de descargas
│   ├── login/          # Autenticación
│   ├── navigation/     # NavGraph y rutas
│   └── theme/          # Material 3 Theme
├── depsInjection/      # Módulos Hilt
└── utils/              # Utilidades
```

---

## 🛠️ Stack Tecnológico

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| **Kotlin** | 2.0+ | Lenguaje principal |
| **Jetpack Compose** | 1.7+ | UI declarativa |
| **Material 3** | 1.3+ | Design system |
| **Hilt** | 2.51+ | Inyección de dependencias |
| **Room** | 2.6+ | Base de datos local |
| **Retrofit** | 2.11+ | Cliente HTTP |
| **Media3/ExoPlayer** | 1.5+ | Reproducción de audio |
| **WorkManager** | 2.9+ | Tareas en background |
| **Coil** | 2.7+ | Carga de imágenes |
| **Coroutines** | 1.8+ | Programación asíncrona |

---

## 📋 Requisitos

- **Android**: 9.0 (API 28) o superior
- **Servidor**: Navidrome o cualquier servidor compatible con Subsonic API

---

## 🚀 Instalación

### Desde código fuente
```bash
# Clonar repositorio
git clone https://github.com/tu-usuario/NeoSynth_android.git
cd NeoSynth_android

# Compilar e instalar
./gradlew installDebug
```

### Configuración
1. Abre la app
2. Ingresa la URL de tu servidor Navidrome (ej: `https://music.tudominio.com`)
3. Introduce tu usuario y contraseña
4. ¡Listo para escuchar música!

---

## 📸 Capturas de Pantalla

| Home | Player | Downloads |
|------|--------|-----------|
| Carrusel de álbumes | Reproductor completo | Lista de descargas |

---

## 🤝 Contribuir

Las contribuciones son bienvenidas. Por favor:

1. Fork el repositorio
2. Crea una rama (`git checkout -b feature/nueva-funcion`)
3. Commit tus cambios (`git commit -m 'Add: nueva función'`)
4. Push a la rama (`git push origin feature/nueva-funcion`)
5. Abre un Pull Request

---

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

---

## 🙏 Agradecimientos

- [Navidrome](https://www.navidrome.org/) - Servidor de música open source
- [Material Design 3](https://m3.material.io/) - Sistema de diseño
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI toolkit moderno

---

<p align="center">
  Hecho con ❤️ y Kotlin
</p>
