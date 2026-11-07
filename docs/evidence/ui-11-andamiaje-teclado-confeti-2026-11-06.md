# Consolidación UI/UX: Andamiaje de Ejercicios, Teclado Matemático y Confeti (UI-11) — 2026-11-06

Documentación técnica de la ejecución paralela con subagentes para las Fases 2 y 3 del plan de modernización de UI/UX de Mática, alineando la interacción de ejercicios y la celebración con las pantallas 4 (`11:214`) y 5 (`11:215`) de Figma.

## 1. Entregables Implementados y Verificados

| Componente | Archivo Fuente | Archivo de Prueba | Subagente Responsable | Estado |
|---|---|---|---|---|
| **Andamiaje de Opción Múltiple (`StepChoiceCard`)** | `:shared:presentation:components/StepChoiceCard.kt` | `StepChoiceCardTest.kt` | *Exercise Scaffolding Engineer* | 6 tests aprobados, WCAG AA cumplido |
| **Generador AST de Opciones** | `:shared:presentation:exercise/ExerciseRenderPlan.kt` | `StepChoiceCardTest.kt` | *Exercise Scaffolding Engineer* | Genera opciones A/B/C automáticas |
| **Teclado Matemático Dedicado (`MathKeyboardBar`)** | `:shared:presentation:components/MathKeyboardBar.kt` | `MathKeyboardBarTest.kt` | *Math Keyboard Engineer* | 7 tests aprobados, $\ge 48\,\text{dp}$ |
| **Soporte de Potencias en Lexer (`²`)** | `:shared:math-engine:parser/Lexer.kt` | `ExprParserTest.kt` | *Math Keyboard Engineer* | Parsea `²` como `^ 2` nativamente |
| **Emisor de Confeti Vectorial (`MaticaConfetti`)** | `:shared:presentation:components/MaticaConfetti.kt` | `MaticaConfettiTest.kt` | *Confetti Motion Engineer* | 9 tests aprobados, Canvas puro |
| **Pantalla de Ejercicio (`LessonPlayerScreen`)** | `:shared:presentation:app/MaticaLessonScreens.kt` | Suite de presentación | Integración conjunta | Opciones A/B/C + Teclado matemático |
| **Pantalla de Victoria (`LessonCompleteScreen`)** | `:shared:presentation:app/MaticaLessonScreens.kt` | Suite de presentación | Integración conjunta | Confeti burst + Mascota celebrando |

## 2. Métricas y Especificaciones Técnicas

### 2.1 Andamiaje en Ejercicios (Figma `11:214`)
* **Pregunta:** *"¿Cuál es el primer paso correcto?"*
* **Opciones A/B/C:** Tarjeta interactiva con insignia circular (32x32 dp) que transiciona de borde neutro a azul primario ($2\,\text{dp}$) al seleccionarse.
* **Micro-interacción de error:** Al seleccionar una opción errónea, la tarjeta ejecuta una animación de sacudida horizontal (*horizontal shake*: $-6\,\text{dp} \to +6\,\text{dp} \to 0\,\text{dp}$) con borde ámbar suave, sin penalizar al alumno.

### 2.2 Teclado Matemático Táctil
* **Teclas disponibles:** $x$, $²$, $($, $)$, $+$, $-$, $*$, $/$, $=$, $\leftarrow$.
* **Accesibilidad:** Tamaño táctil $\ge 48\,\text{dp}$ (`MaticaLayout.touchTarget`), feedback elástico de resorte al presionar ($0.94\times$), contraste superior a $4.5:1$ en fondos claro y oscuro.

### 2.3 Partículas de Confeti
* **Física:** Gravedad $g = 650\,\text{px/s}^2$, amortiguación horizontal aerodinámica $k = 0.85\,\text{s}^{-1}$, oscilación flutter y rotación angular normalizada.
* **Sin dependencias externas:** Implementado 100% en Compose Multiplatform `Canvas` (Skia puro), garantizando compatibilidad idéntica en Android, JVM y futuros clientes.
* **Accesibilidad:** Se desactiva automáticamente si `LocalMotionStyle.current.reduced` está activo.

## 3. Estado de la Suite de Pruebas
* `./gradlew test`: **BUILD SUCCESSFUL** (75 tareas ejecutadas, 0 errores, 0 fallos).
