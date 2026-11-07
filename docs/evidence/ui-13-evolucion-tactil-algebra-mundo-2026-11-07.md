# Consolidación UI/UX: Evolución Táctil, Álgebra Dinámica y Mundo Vivo (UI-13) — 2026-11-07

Documentación técnica de la modernización integral de Mática inspirada en los líderes mundiales de EdTech (Duolingo, Brilliant, Photomath) organizada en tres fases de diseño e interacción ejecutadas de forma consolidada.

---

## 1. Resumen de Componentes Creados

| Fase | Componente | Ubicación | Pruebas Unitarias | Propósito en la Experiencia |
|---|---|---|---|---|
| **Fase 1: Tactile & Juice** | `MaticaPhysicalButton` | `:shared:presentation:components` | `MaticaPhysicalButtonTest.kt` | Botón 3D con base extruida de 4 dp que se deprime en Y al presionar con resorte `Spring`. |
| **Fase 1: Tactile & Juice** | `MaticaJuiceProgressBar` | `:shared:presentation:components` | `MaticaJuiceProgressBarTest.kt` | Barra de progreso con destello de haz de luz continuo (*shimmer sheen*) y avance elástico. |
| **Fase 2: Visual Algebra** | `StepStrikeThroughView` | `:shared:presentation:math` | `StepStrikeThroughViewTest.kt` | Cancelación y tachado vectorial animado (`Animatable`) para términos opuestos o fracciones. |
| **Fase 2: Visual Algebra** | `StepFillInBlankCard` | `:shared:presentation:components` | `StepFillInBlankCardTest.kt` | Andamiaje pedagógico intermedio ("rellenar el hueco") con micro-animación de *shake* ante error. |
| **Fase 3: Companion & World** | `LiveCompanionMascot` | `:shared:presentation:components` | `LiveCompanionMascotTest.kt` | Mascota compañera reactiva para cabeceras con estados emocionales (`IDLE`, `THINKING`, `CHEERING`). |
| **Fase 3: Companion & World** | `StreakFlameBadge` | `:shared:presentation:components` | `StreakFlameBadgeTest.kt` | Llama viva en Canvas con vaivén y pulso de fuego continuo para momentum de racha. |
| **Fase 3: Companion & World** | `RouteMapWorldView` | `:shared:presentation:math` | `RouteMapWorldViewTest.kt` | Sendero sinuoso orgánico en curva S con spline Bézier y cofres misteriosos de hito. |

---

## 2. Especificaciones Técnicas por Fase

### Fase 1: Botones Físicos y "Juice" Táctil
* **`MaticaPhysicalButton`:**
  * Base sombreada de $4\,\text{dp}$ (`BevelHeight = 4.dp`) con color oscurecido calibrado por variante (`PRIMARY`, `ACCENT`, `NEUTRAL`, `DESTRUCTIVE`).
  * Desplazamiento dinámico: `offset(y = targetOffset)` donde `targetOffset` es $4\,\text{dp}$ mientras el botón está presionado y $0\,\text{dp}$ en reposo.
  * Física de resorte: `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`.
  * Integración sensorial: disparo de evento auditivo y háptico `feedback.keyTap()`.
  * Cumplimiento WCAG 2.2 AA medido (contraste superior a $4.5:1$) y objetivo táctil $\ge 48\,\text{dp}$ (`MaticaLayout.touchTarget`).

* **`MaticaJuiceProgressBar`:**
  * Pista de fondo con bordes redondeados y barra activa animada con `animateFloatAsState`.
  * Efecto de destello de luz (*shimmer sheen*): gradiente horizontal que viaja a lo largo de la barra con `rememberInfiniteTransition` y `LinearEasing`.
  * Desactivación segura en modo de accesibilidad (`LocalMotionStyle.current.reduced`).

### Fase 2: Álgebra Visual y Andamiaje Progresivo
* **`StepStrikeThroughView`:**
  * Dibuja sobre el término cancelado una línea en Canvas con `StrokeCap.Round` de $0\%$ a $100\%$ de longitud.
  * Provee semántica completa para TalkBack/VoiceOver: `contentDescription = "Término cancelado: $termLabel"`.
  * En modo de movimiento reducido dibuja el trazo completo de inmediato sin retraso.

* **`StepFillInBlankCard`:**
  * Tarjeta interactiva con ranura destacada `[ ? ]`.
  * Admite selección de fichas rápidas y animación de sacudida (*shake animation*: $-6\,\text{dp} \to +6\,\text{dp} \to 0\,\text{dp}$) al equivocarse, sin penalización punitiva.

### Fase 3: Mascota Reactiva y Sendero Sinuoso
* **`LiveCompanionMascot`:**
  * Mascota compacta ($44\,\text{dp}$ a $56\,\text{dp}$) en Canvas con flotación vertical continua (*bobbing* de $\pm 3\,\text{dp}$).
  * Cambia orgánicamente entre estados de ánimo según la interacción del estudiante: concentrado/pensando si se demora, alegre si acierta, o animando si comete un error leve.
  * Bocadillo de diálogo opcional con flecha indicadora.

* **`StreakFlameBadge`:**
  * Trazo vectorial de llama bicapa (halo naranja de fuego exterior y núcleo dorado interior).
  * Función senoidal pura `streakFlameTipSwayPx` para oscilar el vértice superior de la llama.
  * Pulso suave de escala $1.0\times$ a $1.06\times$ en rachas activas ($>0$ días).

* **`RouteMapWorldView`:**
  * Algoritmo senoidal `sCurveNormalizedX(index)` que alterna suavemente las posiciones de las piedras de paso a lo largo de una spline.
  * Inyección automática de cofres de recompensa (`ChestNode`) cada $N$ estaciones con recompensas de gemas y XP.
