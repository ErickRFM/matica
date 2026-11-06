# Evidencia — UI: espaciado, animación y navegación (2026-11-06)

Rama `feat/bloque-3-ui-navigation`. Dispositivo: `companero_v12_qa_20261009(AVD) - 11`
(`emulator-5554`, Android 11 / API 30, 1080×2340, densidad 440 → 392,72 dp de ancho en retrato).

Preparación del entorno, ejecutada antes de la tanda (sin esto, la prueba del teclado no puede
afirmar nada):

```
adb -s emulator-5554 shell settings put secure show_ime_with_hard_keyboard 1
adb -s emulator-5554 shell wm size      # 1080x2340
adb -s emulator-5554 shell wm density   # 440
```

## Batería ejecutada

```
./gradlew allTests :apps:androidApp:connectedDebugAndroidTest
# BUILD SUCCESSFUL in 2m 13s
```

Los conteos se leen de los XML, no del código de salida.

**Instrumentados** — `apps/androidApp/build/outputs/androidTest-results/connected/debug/TEST-*.xml`:
35 casos, 0 fallos, 0 errores (eran 27 en la tanda anterior; +3 de insets, +4 de métricas de layout).

| Clase | Casos | Qué mide en el dispositivo |
|---|---|---|
| `MaticaWindowInsetsTest` | 3 | el título nace debajo de la barra de estado y la última acción no cae bajo la barra de navegación; tocar un hueco real del scrim cierra la hoja **sin remontar** el reproductor (prueba: «Falta la línea» sigue ahí); el atrás con el teclado abierto cierra el teclado y conserva la pantalla |
| `MaticaLayoutMetricsTest` | 4 | flechas y botones ≥ 48 dp leídos en píxeles; cuerpo topado a 640 dp y centrado en una ventana ancha real; 8 dp entre el título de apartado y lo que titula contra 16 dp entre apartados; la barra expone `ProgressBarRangeInfo(0.2, 0..1, steps=14)` |
| `MaticaAppUiTest` | 6 | host, rutas, tema pintado y — nuevo — los dos formularios desactivados de los servicios bloqueados se muestran |
| `MaticaMathComponentsTest` | 6 | los cuatro componentes matemáticos desde el `ExerciseRenderPlan` |
| `MaticaStateViewsTest` | 5 | carga/vacío/error/offline/éxito |
| `MaticaMotionTest` | 3 | escala 0 llega al compositor con fundido topado a 150 ms |
| `MaticaScreenInventoryTest` | 4 | los 18 destinos, uno a uno |
| `MaticaThemeRenderTest` | 3 | píxel real contra los colores del tema |
| `MaticaNavigatorRestoreTest` | 1 | la pila sobrevive a `recreate()` |

**Unitarios** (mismo árbol, por target): `:design-system` 28/0 en `jvmTest` y `testAndroidHostTest`
(antes 23; +4 de escala de movimiento y +1 de la regla de 48 dp), `:shared:presentation` 117/0 en
ambos targets (antes 105; +4 de ritmo de estados y +8 de claves de revelado), `:shared:math-engine`
132/0, `:shared:learning-engine` 66/0, `:shared:exercises` 25/0, `:shared:math-remote` 11/0 y 10/0.
Total 757 casos, 0 fallos.

## Lo que se midió antes de tocar nada (sondas)

Las tres decisiones de layout que siguen no salieron de una opinión: cada una tiene su sonda y su
número.

1. **Ancho tope.** La primera versión del test usó `Box(Modifier.width(900.dp))` dentro de
   `createComposeRule()`. La sonda devolvió `box=[0..392] outer=[0..392] inner=[0..392] cap=[0..392]`:
   la ventana de la regla de pruebas es la del teléfono (392,72 dp) y el ancho pedido se recorta
   contra ese máximo, así que el tope de 640 dp **nunca se ejercitaba** y el test habría pasado sin
   medir nada. Con `requiredWidth(900.dp)` ocurrió lo mismo. Un contenedor con `horizontalScroll`
   sí propaga el ancho (`inner=[130..392]`, es decir el centrado funcionaba), pero `boundsInWindow`
   recorta al rectángulo visible y el ancho seguiría sin poder medirse. La solución ejecutada es
   girar la actividad: `decor 392,72 → 801,45 dp` y área de composición de 753 dp, una ventana ancha
   real. Con eso el cuerpo mide 608 dp (640 − 2×16 de padding) y los dos huecos laterales salen
   iguales.
2. **Ritmo de apartados.** Antes de migrar las 14 secciones, el test de ritmo falló con el número en
   la mano: «Entre la cabecera y SU contenido hay 16 dp, no los 8 dp del token de apartado». La
   cabecera llevaba su propio relleno vertical y el contenedor de la pantalla añadía 16 entre cada
   hijo, así que el título quedaba más lejos de lo que titula que un apartado del siguiente. Tras la
   migración a `MaticaSection` la misma medición da 8 dp dentro y 16 dp fuera.
3. **Objetivo táctil.** Las flechas de la recta y el botón de atrás se midieron en píxeles contra
   los 48 dp de Material; el glifo de la cabecera y los iconos pasaron a `MaticaLayout.touchTarget` y
   `MaticaLayout.glyphFrame` en lugar de valores sueltos (`44.dp` de antes se quedaba 4 dp corto).

## Defectos encontrados por la instrumentación y corregidos en el código

- **Los dos formularios de servicios bloqueados no se pintaban nunca.** `LoginScreen` y
  `ClassroomScreen` tenían su `MaticaCard` (campo desactivado + leyenda) dentro del `content` de
  `MaticaStateView`, y ese `content` sólo se invoca en `MaticaViewState.Success`; con el estado
  `Offline`/`Error` fijo que usan esas pantallas, el campo nunca se componía. Lo cazó la tanda al
  navegar: `onNodeWithText("Correo de la escuela").assertIsDisplayed()` falló en las dos pruebas que
  pasan por ese camino. Corregido en `shared/presentation/app/MaticaNavigationScreens.kt`
  (`LoginScreen`, línea 181, y `ClassroomScreen`, línea 572) sacando la tarjeta como hermana del
  estado, con un test nuevo que fija el invariante
  (`MaticaAppUiTest.losServiciosBloqueadosMuestranSuFormularioDesactivado`).
- **Ritmo invertido entre título y apartado** (ver sonda 2), corregido agrupando en `MaticaSection` y
  quitando el `padding(bottom = md)` suelto de la fila anterior/siguiente de la práctica.
- **El scrim era una capa deslizada, no fundida**, y al cerrarse se llevaba el estado del
  reproductor; ahora es su propia capa de `AnimatedContent` con fundido (alpha) y `clickable` con
  `onClickLabel = "Cerrar las pistas"`, y la prueba verifica que «Falta la línea» — que vive en un
  `remember` a secas — sobrevive al cierre.

## Cambios por pista

| Pista | Ficheros | Verificación |
|---|---|---|
| Tokens de layout | `design-system/.../tokens/MaticaLayout.kt` (nuevo), `ThemeTokensTest.kt` | 1 prueba nueva; regla de 48 dp sostenida por el token y por la medición en pantalla |
| Movimiento que escala | `design-system/.../motion/MaticaMotion.kt` (`stiffnessFactor`), `MotionStyleTest.kt` | 4 pruebas nuevas: 0× deja de rebotar sin congelar el fundido (rigidez 1f), 0,5×/1×/1,5× producen 200/400/600 y 100/200/300 |
| Host, insets y scrim | `MainActivity.kt` (`enableEdgeToEdge`, atrás con IME), `MaticaApp.kt` (`windowInsetsPadding(safeDrawing)`, capa del scrim), `MaticaWindowInsetsTest.kt` (nuevo) | 3/3 en el dispositivo |
| Estados y revelados | `MaticaStateView.kt` (ritmo único, shimmer por alpha, anillo estático con `ProgressBarRangeInfo.Indeterminate`), `RouteMapView.kt`, `CartesianGraphView.kt`, `NumberLineView.kt`, `FractionView.kt` (claves de revelado estable, `PathMeasure` fuera del draw) | `MaticaStateRhythmTest` (4) + `RevealKeyTest` (8) + 5 instrumentadas |
| Capa de pantalla | `MaticaScreen.kt` (tope de ancho centrado, `AnimatedVisibility` en validación, progreso animado con semántica), `MaticaChips.kt` (`MaticaSection`, cabecera privada), `MaticaButton.kt` (ripple recortado a la forma, 48 dp mínimos) | `MaticaLayoutMetricsTest` (4) + `MaticaAppUiTest` (6) |

## Pendiente y bloqueado (no fingido como hecho)

- **Tableta real y doble columna**: el tope de 640 dp y su centrado están medidos en un viewport
  horizontal de emulador; no hay dispositivo físico, así que el comportamiento con dos columnas
  reales sigue sin evidencia.
- **Auditoría con TalkBack y recorrido con teclado físico**: requiere hardware/software externo
  (B3-04 sigue parcial por esto).
- **`MaticaProgress` con `steps`**: la semántica se verificó; la locución real de esa proporción por
  el lector de pantalla no.
- **Fidelidad a Figma** (B0-09): el modo Dev Mode sigue sin sesión, así que las medidas de la
  herramienta de diseño no se compararon con estas.
