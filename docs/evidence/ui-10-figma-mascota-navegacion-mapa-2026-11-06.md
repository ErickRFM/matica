# Consolidación UI/UX: Mascota, Barra de Navegación y Mapa de Recorrido Animado (UI-10) — 2026-11-06

Documentación técnica de la Fase 1 del plan de modernización de UI/UX de Mática, alineando la aplicación con la exploración de diseño de Figma (`kyQeyUyxdNwYP5r4UTmR1i`) y el benchmark competitivo.

## 1. Resumen de Entregables Implementados

| Componente / Pantalla | Archivo Fuente | Archivo de Prueba | Estado de Verificación |
|---|---|---|---|
| **Mascota Oficial (`MaticaMascot`)** | `:design-system:brand/MaticaMascot.kt` | `:design-system:brand/MaticaMascotTest.kt` | Compilado y probado en JVM |
| **Barra Inferior (`MaticaNavigationBar`)** | `:shared:presentation:navigation/MaticaNavigationBar.kt` | `:shared:presentation:navigation/MaticaNavigationBarTest.kt` | 4 pestañas accesibles $\ge 48\,\text{dp}$ |
| **Mapa de Recorrido Animado (`RouteMapView`)** | `:shared:presentation:math/RouteMapView.kt` | `:shared:presentation:math/RouteMapTest.kt` | Halo respiratorio + piedras de paso circulares |
| **Pantalla de Bienvenida (`WelcomeScreen`)** | `:shared:presentation:app/MaticaNavigationScreens.kt` | Unit tests existentes | Mascota feliz + copy de Figma |
| **Pantalla de Inicio (`HomeScreen`)** | `:shared:presentation:app/MaticaNavigationScreens.kt` | `MaticaAppUiTest.kt` | Hero «Sigue tu aventura» + Misiones |
| **Pantalla de Fin de Lección (`LessonCompleteScreen`)** | `:shared:presentation:app/MaticaLessonScreens.kt` | Unit tests existentes | Celebración, trío de stats y mascota |
| **Perfil y Logros (`ProfileScreen`)** | `:shared:presentation:app/MaticaNavigationScreens.kt` | `MaticaAppUiTest.kt` | Estadísticas (racha, lecciones, gemas) e insignias |

## 2. Detalles Técnicos de Implementación

### 2.1 `MaticaMascot` (Vectorial en Canvas)
* Reproduce la geometría exacta de Figma (`1:9`, `1:148`): cuerpo circular verde lima (`#B7EF59`), ojos oscuros (`#17263F`) con pupila y brillo blanco, y sonrisa elástica en arco.
* 4 estados emocionales: `IDLE` (con parpadeo periódico cada 2.8 s), `HAPPY` (sonrisa amplia y sonrojo), `THINKING` (ojos divergentes hacia arriba) y `CELEBRATING` (ojos felices en arco y rebote vertical continuo).
* Respeta `LocalMotionStyle.current`: en dispositivos con escala de animación 0 (`reduced = true`), los parpadeos y rebotes se desactivan de forma segura.

### 2.2 `MaticaNavigationBar` (Figma Frame 2)
* Expone los 4 destinos declarados en el diseño: Inicio (`Home`), Ruta (`RouteMap`), Repaso (`ReviewQueue`) y Perfil (`Profile`).
* Indicador de selección animado tipo píldora (`primaryContainer`), tipografía semibold y roles semánticos `Role.Tab`.
* Se oculta automáticamente en pantallas de foco pedagógico (reproductor de lección, práctica, diagnóstico).

### 2.3 `RouteMapView` (Sendero de Aventura)
* Transición de tarjetas rectangulares a **piedras de paso circulares** de $54\,\text{dp}$ (`CircleShape`) con el icono del estado interior (`✓` completado, `★` fluido, `🔒` bloqueado).
* **Halo de respiración/energía:** El nodo sugerido despliega un anillo animado con `rememberInfiniteTransition` que pulsa de $1.0\times$ a $1.38\times$ y se desvanece de $0.5$ a $0.0$.
* **Feedback táctil:** Al pulsar un nodo desbloqueado, se comprime a $0.90\times$ con física de resorte (`popSpec`).
* **Cabecera de Mundo:** Se integró en `RouteMapScreen` la tarjeta «MUNDO 02 · El reino del álgebra» con conteo en tiempo real de habilidades dominadas y porcentaje de avance.

## 3. Estado de la Suite de Pruebas
* `./gradlew test`: **BUILD SUCCESSFUL** (75 tareas ejecutadas o cacheadas, 0 errores, 0 fallos).
