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
41 casos, 0 fallos, 0 errores (27 → 35 en la primera tanda del día, por las 4 de métricas de layout y
las 3 de insets; 35 → 39 con la caminata visual: +3 en `MaticaAppUiTest` y +1 en
`MaticaLayoutMetricsTest`; 39 → 41 con las dos aserciones que cerraron el hueco documentado abajo).

| Clase | Casos | Qué mide en el dispositivo |
|---|---|---|
| `MaticaWindowInsetsTest` | 3 | el título nace debajo de la barra de estado y la última acción no cae bajo la barra de navegación; tocar un hueco real del scrim cierra la hoja **sin remontar** el reproductor (prueba: «Falta la línea» sigue ahí); el atrás con el teclado abierto cierra el teclado y conserva la pantalla |
| `MaticaLayoutMetricsTest` | 5 | flechas y botones ≥ 48 dp leídos en píxeles; cuerpo topado a 640 dp y centrado en una ventana ancha real; 8 dp entre el título de apartado y lo que titula contra 16 dp entre apartados; la barra expone `ProgressBarRangeInfo(0.2, 0..1, steps=14)`; las cuatro etiquetas de movimiento caben en una línea (≤ 56 dp de alto cada una) |
| `MaticaAppUiTest` | 11 | host, rutas, tema pintado y — nuevo — los dos formularios desactivados de los servicios bloqueados se muestran; la hoja de pistas pinta una superficie opaca sobre el reproductor; las opciones del diagnóstico se recortan sobre la tarjeta; «Mi perfil» se abre desde Hoy; la opción de grado sin elegir tiene superficie propia y Logros no repite sus insignias |
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
| 3 | Las tres opciones de grado usaban `TEXT`: sin superficie, «1.º de secundaria» y «3.º de secundaria» se leían como etiquetas sueltas y sólo la elegida parecía un botón | antes `04-ajustes-despues.png` | `MaticaNavigationScreens.kt`: grado en `NEUTRAL`, una opción por línea y a ancho completo. No van en fila de tres: «2.º de secundaria» necesita ~150 dp de etiqueta y el teléfono deja ~116 dp por celda. Después: `05-ajustes-grado.png`. Fijado después por `laOpcionDeGradoNoElegidaTieneSuperficiePropia` |
| 4 | La frase de estrategia del ejercicio usaba `titleMedium`: dos líneas de texto corrido compuestas con peso de titular, por encima de la propia ecuación | leído en pantalla durante la caminata; **no quedó captura del antes** (el fix se aplicó en la misma pasada) | `MaticaLessonScreens.kt`: `bodyLarge`. Después: `19-practica-tipografia.png` |
| 5 | Logros pintaba su propia fila de tres insignias y **duplicaba** «0 XP» y «0 días» a 40 px de la barra de arriba | antes `14-logros.png` | `showChips = false` en `RewardsScreen` y también en `HintSheetScreen` (allí repetía el mismo par dentro del 78 % de la hoja, quitando sitio a las pistas). Después: `17-logros-sin-duplicado.png`, fijado por `logrosNoRepiteLasInsigniasDeLaCabecera` |
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

Una aserción que pasa con el defecto puesto no prueba nada. Se hicieron cuatro inversiones temporales
del código de producción, cada una con su corrida:

1. Se restauró la capa modal transparente (copia de seguridad `MaticaApp.kt` previa al fix) y se corrió
   la clase: salió rojo con el mensaje medido — «El título «Pistas» arranca en y=185.0 dentro de una
   zona de 2063.0 px». Repuesto el fix, verde.
2. Se volvió a `NEUTRAL` la variante de las opciones del diagnóstico y se corrió `MaticaAppUiTest`:
   el XML devolvió `tests="9" failures="1"`, con la aserción del recorte como única fallida. Repuesto
   `OUTLINE`, verde otra vez.

Después de ambas reposiciones se volvió a ejecutar la batería completa: 39 instrumentados y 757
unitarios, 0 fallos.

### Cerrando el hueco: grado e insignias duplicadas pasan a medirse

Quedaban tres fixes sostenidos sólo por su captura. Dos se convirtieron en aserción el mismo día; el
terno se explica abajo y sigue sin prueba.

- `MaticaAppUiTest.laOpcionDeGradoNoElegidaTieneSuperficiePropia`: toma la opción de grado que **no**
  está elegida (se lee de `session.settings.grade`, no se escribe a mano), pide el nodo clicable y
  compara el píxel 12 px dentro del borde izquierdo contra el de 8 px fuera. Se mide el relleno y no
  el centro porque en el centro siempre hay tinta de la letra: con `TEXT` la prueba habría pasado por
  los pelos midiendo el glifo.
- `MaticaAppUiTest.logrosNoRepiteLasInsigniasDeLaCabecera`: exige que `"${session.xp} XP"` y
  `"${session.streak} días"` aparezcan **una sola vez** en el árbol. Con `hasText(..., substring)`
  fallaría por otro motivo — el apartado de marcas también lleva «XP» en cada línea («Primer bloque de
  práctica · 60 XP») — así que la matcher es el texto exacto del contador.

Ambas se corrieron primero con el fix puesto (11/0) y luego con los dos defectos reuestos a la vez
(`TEXT` en grado y `showChips` quitado de `RewardsScreen`), y el XML devolvió `tests="11"
failures="2"` con los dos mensajes leídos del dispositivo:

```
«0 XP» aparece 2 veces en Logros: la pantalla pinta su propia fila de insignias encima de la de
la cabecera y el estudiante ve el mismo contador dos veces a 40 px.

«1.º de secundaria» vale #fff5f8ff por dentro y #fff5f8ff ocho px a su izquierda: la opción sin
elegir no tiene superficie, se lee como una etiqueta más del apartado y no como algo que se
puede tocar.
```

El segundo mensaje es el que deja el defecto retratado en un número: `#F5F8FF` es `Mist`, el fondo de
pantalla, así que el botón pintaba exactamente el color de lo que había detrás.

Tras reponer los dos fixes, batería completa otra vez: **41 instrumentados y 757 unitarios, 0 fallos**
(`BUILD SUCCESSFUL in 1m 53s`, `tests="41" failures="0" errors="0"`).

## Tercera tanda: el tema oscuro caminado pantalla por pantalla

La caminata anterior se hizo en claro. Al recorrer las mismas rutas en oscuro apareció un defecto que
ninguna captura en claro podía enseñar: **las barras del sistema no seguían el tema de la app**.

- **Sonda** (`MaticaWindowInsetsTest.elTemaOscuroDeLaAppTambienCambiaLaAparicionDeLasBarrasDelSistema`):
  navega Hoy → Ajustes → pulsa «Oscuro» y lee el flag real de la ventana con
  `WindowInsetsControllerCompat`, dentro de la misma activity instrumentada. No se infiere de la
  captura: se lee el dato que el sistema usa para pintar la hora y la batería. Con el fix ausente el
  XML devolvió el mensaje leído del dispositivo:

  ```
  Con el tema de la app en «Oscuro» la ventana sigue declarando isAppearanceLightStatusBars=true:
  los iconos del sistema se pintan para fondo claro y sobre la superficie oscura de la app desaparecen.
  ```

- **Causa**: `MainActivity` llama a `enableEdgeToEdge()` sin argumentos, que deja
  `SystemBarStyle.auto`. `auto` obedece al ajuste **del sistema**, no a la opción que el estudiante
  eligió dentro de la app, así que un móvil en claro con Mática en oscuro conservaba iconos oscuros
  sobre `#0E1626`.
- **Arreglo**: `MaticaSystemBarAppearance(dark)` en `design-system` como `expect`/`actual` (la misma
  forma que ya usa `platformMotionScale`): el `actual` de Android escribe
  `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` en el `WindowInsetsControllerCompat`
  desde un `SideEffect` de `MaticaTheme`, y el de JVM no hace nada. El módulo necesitó su primera
  dependencia de Android (`androidx.core.ktx`, sólo para ese flag).
- **Verde medido**: `MaticaWindowInsetsTest` con 4 casos, 0 fallos (`tests="4" failures="0"
  errors="0"`, lectura de las 09:35). **Ojo con el alcance de esa lectura**: el XML de esa corrida ya no
  está en disco — otro proceso del proyecto reinstaló la app sobre el emulador mientras se hacía la
  caminata en oscuro y limpió los resultados — así que el número queda citado de la lectura del
  momento y la repetición de la batería completa está anotada abajo como pendiente.

### Lo que enseñan (y lo que no pueden enseñar) las capturas en oscuro

`31-oscuro-bienvenida`, `32-oscuro-diagnostico`, `33-oscuro-hoy`, `34-oscuro-ruta`,
`35-oscuro-lecciones`, `36-oscuro-reproductor`, `38-oscuro-logros`, `39-oscuro-perfil`,
`40-oscuro-ajustes` en `C:/Users/erik5/AppData/Local/Temp/matica-shots/`.

Están tomadas con el sistema en modo noche y `Tema = Sistema`, así que **no aíslan el arreglo**: en esa
configuración `SystemBarStyle.auto` también pintaría los iconos claros. Lo que sí queda demostrado en
ellas es el resto del oscuro — contraste de las insignias, superficie propia de la opción de grado sin
elegir, la fila única de contadores en Logros y «Mi perfil» reachable desde los accesos — y la
aclaración del flag la sostiene la prueba instrumentada, que es la que pone la app en oscuro
independientemente del sistema.

Falta `37-oscuro-hoja-pistas`: al pulsar «Pista» el proceso concurrente reinstaló el APK y la caminata
salió al launcher. No se documenta como hecha.

## Pendiente y bloqueado (no fingido como hecho)

- **Repetir la batería completa con el arreglo de barras puesto**: la tanda de las 41 instrumentadas se
  corrió antes de `MaticaSystemBarAppearance`. Hay que volver a correr
  `./gradlew allTests :apps:androidApp:connectedDebugAndroidTest` y, para que la prueba de barras sea
  realmente aisladora, con el sistema en claro (`cmd uimode night no`). Bloqueado mientras otro proceso
  del proyecto tenga el emulador: instalar dos veces el mismo APK en mitad de una caminata invalida
  todas las capturas.
- **La hoja de pistas en oscuro** (`37`), sin captura por el motivo de arriba.
- **Uno de los siete fixes de la caminata sigue sin aserción**: el peso del párrafo de estrategia (4).
  Grado (3) e insignias duplicadas (5) se midieron en el dispositivo en la misma tarde, así que ya no
  dependen de la captura. Lo del párrafo no se convirtió en prueba porque la API de semántica de
  Compose no expone el `TextStyle` aplicado: el nodo dice qué texto hay, no con qué tipografía se pintó.
  Medirlo obligaría a inferir la cuerpo por altura de tinta en la captura, y una aserción así se cae
  con cualquier cambio de escala de fuente. Queda sostenida por `19-practica-tipografia.png` y por la
  convención escrita en `docs/ARCHITECTURE.md`.
- **Las capturas no viven en el repositorio**: las 23 de la tanda en claro más las 9 en oscuro
  (`31`–`36`, `38`–`40`) quedaron en
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
