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
# BUILD SUCCESSFUL in 2m 13s   ← duración de la primera corrida del día (35 casos)
```

Los conteos se leen de los XML, no del código de salida.

**Instrumentados** — `apps/androidApp/build/outputs/androidTest-results/connected/debug/TEST-*.xml`:
39 casos, 0 fallos, 0 errores (27 → 35 en la primera tanda del día, por las 4 de métricas de layout y
las 3 de insets; 35 → 39 con la segunda tanda documentada más abajo: +3 en `MaticaAppUiTest` y +1 en
`MaticaLayoutMetricsTest`).

| Clase | Casos | Qué mide en el dispositivo |
|---|---|---|
| `MaticaWindowInsetsTest` | 3 | el título nace debajo de la barra de estado y la última acción no cae bajo la barra de navegación; tocar un hueco real del scrim cierra la hoja **sin remontar** el reproductor (prueba: «Falta la línea» sigue ahí); el atrás con el teclado abierto cierra el teclado y conserva la pantalla |
| `MaticaLayoutMetricsTest` | 5 | flechas y botones ≥ 48 dp leídos en píxeles; cuerpo topado a 640 dp y centrado en una ventana ancha real; 8 dp entre el título de apartado y lo que titula contra 16 dp entre apartados; la barra expone `ProgressBarRangeInfo(0.2, 0..1, steps=14)`; las cuatro etiquetas de movimiento caben en una línea (≤ 56 dp de alto cada una) |
| `MaticaAppUiTest` | 9 | host, rutas, tema pintado y — nuevo — los dos formularios desactivados de los servicios bloqueados se muestran; la hoja de pistas pinta una superficie opaca sobre el reproductor; las opciones del diagnóstico se recortan sobre la tarjeta; «Mi perfil» se abre desde Hoy |
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

## Segunda tanda del día: caminata visual con capturas

La batería instrumentada mide geometrías y píxeles concretos, pero no mira la pantalla entera. Esta
segunda pasada se hizo caminando la app destino a destino en el emulador y tomando 23 capturas
(`C:/Users/erik5/AppData/Local/Temp/matica-shots/01-bienvenida.png` … `23-hoy-perfil-nuevo.png`,
más los volcados `ui.xml`, `ui2.xml`, `ui3.xml` de `uiautomator dump`). Siete defectos salieron de
ahí; ninguno de los siete se arregló tocando una aserción.

Las capturas 22 y 23 se tomaron al final, ya con el APK del commit (instalado a las 09:10 sobre
`emulator-5554`) y con el perfil recién creado desde «Explorar la ruta sin diagnóstico», porque la
pantalla de Hoy de la tanda anterior (`16-hoy-accesos.png`, 2:53) seguía mostrando el botón
«Abrir habilidad» sin recortar: había que verificar el después con una imagen que existiera.

| # | Defecto | Dónde se vio | Corrección |
|---|---|---|---|
| 1 | La ruta modal se dibujaba **transparente** encima de la pantalla que la abrió: la cabecera «Pistas» caía en la misma coordenada que el título del ejercicio («Operaciones con enteros · ejercicio 1» se leía debajo de «Pistas») y el reproductor se filtraba a través de los huecos de las tarjetas | antes `10-hoja-pistas.png` | `MaticaApp.kt`: la capa del scrim pasa a hoja anclada abajo (`SHEET_HEIGHT = 0.78f`), con `Surface` opaca y radio superior del sistema; la franja de arriba sigue siendo scrim tocable. Después: `11-hoja-opaca.png` y cierre por scrim en `12-scrim-cerrado.png` |
| 2 | `MaticaButton` aceptaba el `modifier` del llamador y **no lo aplicaba**: la fila construía su propio ancho, así que el `weight(1f)` de las filas de opciones se perdía y la última etiqueta, «Rápida», se escribía una letra por renglón en los 392,72 dp del teléfono | antes `03-ajustes.png` (fila de Movimiento: Ninguna / Lenta / Normal / R-á-p-i-d-a) | `MaticaButton.kt`: el `modifier` del llamador va primero en la cadena, antes del ancho y del `heightIn`. Después: `04-ajustes-despues.png` (ya en dos filas de dos, etiquetas enteras) |
| 3 | Las tres opciones de grado usaban `TEXT`: sin superficie, «1.º de secundaria» y «3.º de secundaria» se leían como etiquetas sueltas y sólo la elegida parecía un botón | antes `04-ajustes-despues.png` | `MaticaNavigationScreens.kt`: grado en `NEUTRAL`, una opción por línea y a ancho completo. No van en fila de tres: «2.º de secundaria» necesita ~150 dp de etiqueta y el teléfono deja ~116 dp por celda. Después: `05-ajustes-grado.png` |
| 4 | La frase de estrategia del ejercicio usaba `titleMedium`: dos líneas de texto corrido compuestas con peso de titular, por encima de la propia ecuación | leído en pantalla durante la caminata; **no quedó captura del antes** (el fix se aplicó en la misma pasada) | `MaticaLessonScreens.kt`: `bodyLarge`. Después: `19-practica-tipografia.png` |
| 5 | Logros pintaba su propia fila de tres insignias y **duplicaba** «0 XP» y «0 días» a 40 px de la barra de arriba | antes `14-logros.png` | `showChips = false` en `RewardsScreen` y también en `HintSheetScreen` (allí repetía el mismo par dentro del 78 % de la hoja, quitando sitio a las pistas). Después: `17-logros-sin-duplicado.png` |
| 6 | `MaticaRoute.Profile` **no tenía entrada ninguna**: la pantalla existía y se probaba montando la ruta a mano, pero ningún botón llevaba a ella, así que el reinicio del perfil local era inalcanzable para el estudiante | inventario de rutas + ausencia de acceso en las capturas de Hoy | Apartado «Accesos» de Hoy pasa a dos filas de dos e incluye «Mi perfil» (en tres por fila la etiqueta no cabía: ~114 dp por celda contra ~118 del texto); el camino del estudiante se recorre en el test `desdeHoySePuedeAbrirElPerfilLocal`. Después: `16-hoy-accesos.png`, `18-perfil.png` y `23-hoy-perfil-nuevo.png` |
| 7 | Las opciones del diagnóstico y «Abrir habilidad» usaban `NEUTRAL` **dentro de una `MaticaCard`**: el relleno del neutro en claro es `BlueMist` y la tarjeta se pinta con `surfaceVariant`, que en claro también es `BlueMist`, así que el botón desaparecía sobre su propia tarjeta | antes `20-diagnostico.png` (cuatro respuestas como texto suelto) y `16-hoy-accesos.png` («Abrir habilidad» sin recortar) | Variante `OUTLINE` dentro de tarjeta (es la variante pensada para ese fondo: «borde sobre tarjeta»); «Abrir habilidad» además en `fullWidth`. Después: `21-diagnostico-bordes.png` y `23-hoy-perfil-nuevo.png` |

Cuatro pruebas nuevas, una por cada defecto que puede medirse sin mirar la captura (2, 1, 7 y 6 en ese
orden de lectura):

- `MaticaAppUiTest.laHojaDePistasPintaUnaSuperficieOpacaSobreElReproductor`: calibra el píxel **con la
  hoja cerrada** en el centro de donde estaba «Comprobar paso», abre la pista y exige que ese píxel ya
  no sea el mismo (la hoja tapa lo que había debajo) y que el título «Pistas» nazca por debajo del 22 %
  de la zona del scrim. No fija un color escrito a mano.
- `MaticaAppUiTest.lasOpcionesDelDiagnosticoSeRecortanSobreLaTarjeta`: lee la primera pregunta pendiente
  desde el propio `session`, toma el nodo `hasText(opción) and hasClickAction()` (sin el `hasClickAction`
  uiautomator devuelve el nodo de texto, no el del botón, y el rectángulo no sirve) y exige que al menos
  uno de los 13 px del borde izquierdo difiera del píxel de la tarjeta 8 px a su izquierda.
- `MaticaAppUiTest.desdeHoySePuedeAbrirElPerfilLocal`: Hoy → clic en «Mi perfil» → «Reiniciar el perfil
  local» visible y `navigator.current == MaticaRoute.Profile`.
- `MaticaLayoutMetricsTest.lasCuatroEtiquetasDeMovimientoCabenEnUnaLinea`: cada píldora mide ≤ 56 dp de
  alto; es la que habría cazado el «Rápida» partido antes de mirar la captura.

### Las pruebas nuevas tienen dientes (experimento de inversión)

Una aserción que pasa con el defecto puesto no prueba nada. Se hicieron dos inversiones temporales del
código de producción, cada una con su corrida:

1. Se restauró la capa modal transparente (copia de seguridad `MaticaApp.kt` previa al fix) y se corrió
   la clase: salió rojo con el mensaje medido — «El título «Pistas» arranca en y=185.0 dentro de una
   zona de 2063.0 px». Repuesto el fix, verde.
2. Se volvió a `NEUTRAL` la variante de las opciones del diagnóstico y se corrió `MaticaAppUiTest`:
   el XML devolvió `tests="9" failures="1"`, con la aserción del recorte como única fallida. Repuesto
   `OUTLINE`, verde otra vez.

Después de ambas reposiciones se volvió a ejecutar la batería completa: 39 instrumentados y 757
unitarios, 0 fallos (los números de arriba se leyeron de esa corrida).

## Pendiente y bloqueado (no fingido como hecho)

- **Tres de los siete fixes de la caminata no tienen aserción propia**: la variante de las opciones de
  grado (3), el peso del párrafo de estrategia (4) y los chips duplicados de Logros (5) están
  sostenidos sólo por su captura antes/después. No hay ningún test que busque «Grado», «Logros» o
  `showChips` en el árbol instrumentado; se verificó con `grep` sobre
  `apps/androidApp/src/androidTest`. Si alguien vuelve a poner `TEXT` en grado o reactiva los chips,
  nada lo va a pintar rojo.
- **Las 23 capturas no viven en el repositorio**: quedaron en
  `C:/Users/erik5/AppData/Local/Temp/matica-shots/` (borrador de la sesión), igual que las de Figma.
  Son evidencia de una fecha, no un archivo controlado por Git.

- **Tableta real y doble columna**: el tope de 640 dp y su centrado están medidos en un viewport
  horizontal de emulador; no hay dispositivo físico, así que el comportamiento con dos columnas
  reales sigue sin evidencia.
- **Auditoría con TalkBack y recorrido con teclado físico**: requiere hardware/software externo
  (B3-04 sigue parcial por esto).
- **`MaticaProgress` con `steps`**: la semántica se verificó; la locución real de esa proporción por
  el lector de pantalla no.
- **Fidelidad a Figma** (B0-09): el modo Dev Mode sigue sin sesión, así que las medidas de la
  herramienta de diseño no se compararon con estas.
