# KUIDA

Aplicación móvil Android concebida como apoyo para la orientación, comunicación y acceso a información inclusiva. El proyecto combina funciones de asistencia con recursos de accesibilidad y herramientas de IA.

**Reconocimiento:** primer lugar en la categoría de proyectos de Ingeniería de Sistemas de la Feria STEM 2026 de UNICIT, según el reconocimiento recibido por el equipo.

## Tecnologías

- Java y AndroidX
- Navigation Component, Room y SharedPreferences
- Vosk para reconocimiento de voz local
- YAMNet y ML Kit Digital Ink para funciones asistidas por modelos
- Gemini API para funciones generativas configurables
- Gradle y Android Studio

## Estructura

- `app/`: aplicación Android, interfaz y recursos
- `gradle/`: wrapper y catálogo de versiones

## Configuración local

Copia `local.properties.example` a `local.properties` y agrega tus propias claves de proveedor si deseas habilitar las funciones que las requieren. No publiques claves ni las incluyas en una aplicación distribuida.

## Estado

Proyecto académico en desarrollo. Las integraciones con IA dependen de proveedores externos y de las claves que configure cada persona.
