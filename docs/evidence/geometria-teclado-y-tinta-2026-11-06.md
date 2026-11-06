# Evidencia — geometría, arrastre en el plano, teclado y tinta (2026-11-06)

Rama `feat/bloque-3-geometria-y-teclado`, sobre `main` en `634d99c`.
Dispositivo: `companero_v12_qa_20261009(AVD) - 11` (`emulator-5554`, Android 11 / API 30, 1080×2340,
densidad 440 → 392,72 dp de ancho en retrato). Tema claro.

Cierra los cuatro huecos que quedaron abiertos al terminar la tanda de marca: geometría sin
componente ni plantilla, arrastrar un punto de la gráfica, el recorrido con teclado y la medida del
párrafo de estrategia. El quinto (Docker → Postgres → Ktor) se midió también, pero el diagnóstico con
el que entró en la lista estaba equivocado y hay que corregirlo dos veces: primero resultó que
Postgres sí estaba arriba, y después, al integrar `main`, que el módulo de backend ya existía. Está
documentado abajo.

## Qué se construyó

| Pieza | Archivo | Regla o medida |
|---|---|---|
| Lectura geométrica honesta | `shared/presentation/src/commonMain/kotlin/mx/matica/presentation/math/GeometryView.kt` | `rectangleFigureOf` sólo acepta `2(x + b) = P` (perímetro) y `a·x = A` (área); rechaza lados escritos como resta, lados no positivos y totales que no cuadran contra la solución del propio ejercicio: devuelve `null` y la pantalla no dibuja |
| Figura con lado arrastrable | `GeometryView.kt` (`RectangleFigureView`) | arrastre horizontal enganchado a las marcas exactas (`figureValueAt`), dos flechas de 48 dp que llegan al mismo valor (WCAG 2.1.1), lectura «Lado n · perímetro m» y descripción de semántica que dice si coincide |
| Escala de dibujo | `GeometryView.kt` (`figureScales`) | proporción real recortada a 4:1 por lado; un 1×12 se vería como una raya. El recorte afecta sólo al dibujo: las etiquetas conservan los valores exactos |
| Plantilla de perímetro | `shared/exercises/src/commonMain/kotlin/mx/matica/exercises/LinearTemplates.kt` | `rectangle-perimeter` construye `2(x + b) = 2(k + b)`, así que la solución del ejercicio ES el lado de la figura; catálogo de `ExerciseRenderCatalog` sube a cinco entradas |
| Slot de geometría | `shared/presentation/src/commonMain/kotlin/mx/matica/presentation/exercise/ExerciseRenderPlan.kt` | `ExerciseSlot.GEOMETRY` en TWO_STEP y DISTRIBUTIVE, `plan.figure` derivado del AST en el propio plan |
| Punto arrastrable en el plano | `shared/presentation/src/commonMain/kotlin/mx/matica/presentation/math/CartesianGraphView.kt` | `trackerXAt(fraction, xFrom, xTo, candidates)` traduce el dedo a la marca más cercana dentro de la ventana dibujada; la Y sale de la recta (`trackerY`), nunca del arrastre: el punto no puede caerse de la recta ni parar en un valor que el motor no sepa nombrar |
| Plano y recta comparten conjetura | `shared/presentation/src/commonMain/kotlin/mx/matica/presentation/app/MaticaLessonScreens.kt` | `trackerX = selected`: la marca que mueve la recta numérica es la que mueve el punto del plano |
| Recorrido con teclado | `apps/androidApp/src/androidTest/kotlin/mx/matica/app/MaticaKeyboardWalkTest.kt` | 4 pruebas en el dispositivo |
| Tinta del párrafo de estrategia | `apps/androidApp/src/androidTest/kotlin/mx/matica/app/MaticaLayoutMetricsTest.kt` | `elParrafoDeEstrategiaPesaComoCuerpoYNoComoTitular`, calibrada en la misma pasada |

## Sondeado antes de codificar

Las tres expectaciones que escribí sin medir resultaron falsas; las corrí primero y luego las codifiqué.

1. **El teclado ya funcionaba y la inyección de la prueba mentía.** `performKeyInput { pressKey(Key.Tab) }`
   inyecta la tecla en los manejadores *de ese nodo*, no en la ventana, así que el recorrido que
   mostraba era un artefacto del instrumento. Medido con
   `InstrumentationRegistry…sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)` —la tecla real— el orden de foco de
   Compose ya recorre todos los controles. Consecuencia: el `keyboardTraversal` + `onPreviewKeyEvent`
   que había añadido a `MaticaApp.kt` era código muerto con nombre de arreglo, y se revirtió; la
   inversión lo confirmó (recorrido idéntico con el handler inerte). Lo que sí era un hueco real: el
   punto del plano sólo se movía con el dedo.
2. **El enganche del plano.** La primera aserción esperaba que 0,56 de un ancho de 8 unidades cayera en
   la marca 1; falló (`expected:<1> but was:<0>`) porque el número estaba inventado. Recalculado: cae en
   0, y 0,62/0,66 sí caen en 1 —menos de media marca de separación no saca el punto de su marca. Eso es
   lo que quedó escrito en `PlotSeriesTest`.
3. **La tinta.** `titleMedium` y `bodyLarge` miden los dos 16 sp con 24 sp de interlineado, así que el
   párrafo de estrategia no se puede distinguir de un titular por su tamaño: la única señal en píxeles
   es el grosor del trazo. Medido con la MISMA frase en los dos estilos dentro de la misma composición:
   cuerpo 22,10 %, titular 26,96 % (1,22 veces más tinta), y el párrafo dibujado por la app 22,99 %.
   La prueba exige además que la regla separe ambos pesos (≥ 1,10) para que la comparación no pueda
   quedarse a ciegas sin dejar de estar en verde.

## Inversiones ejecutadas

Cada aserción nueva se vio fallar con su defecto repuesto:

| Defecto repuesto | Resultado medido |
|---|---|
| `trackerX = selected` fuera del plano (el punto vuelve a ser sólo táctil) | falla `lasFlechasDeLaRectaMuevenElPuntoDelPlano` |
| `pointerInput` del plano eliminado | falla la prueba de arrastre del plano en `MaticaMathComponentsTest` |
| párrafo de estrategia devuelto a `titleMedium` | `MaticaLayoutMetricsTest` cierra con `tests="6" failures="1"`; el mensaje medido: «El párrafo de estrategia tiene 28.16 % de tinta: más cerca del titular (26.96 %) que del cuerpo (22.1 %)» |
| handler de teclado añadido a `MaticaApp` y luego inertado | el recorrido NO cambia → el cambio de producción se revirtió (no había defecto que arreglar) |

## Batería ejecutada

```
./gradlew allTests :apps:androidApp:assembleDebug   # BUILD SUCCESSFUL in 1m 8s
./gradlew :apps:androidApp:connectedDebugAndroidTest
```

Conteos leídos de los XML de resultados, no del código de salida.

| Módulo | `jvmTest` | `testAndroidHostTest` |
|---|---|---|
| `:shared:math-engine` | 132 / 0 | 132 / 0 |
| `:shared:math-remote` | 11 / 0 | 10 / 0 |
| `:shared:learning-engine` | 66 / 0 | 66 / 0 |
| `:shared:exercises` | 27 / 0 | 27 / 0 |
| `:design-system` | 33 / 0 | 33 / 0 |
| `:shared:presentation` | 138 / 0 | 138 / 0 |
| **total** | **407 / 0** | **406 / 0** |

Lo nuevo por módulo: `RectangleFigureTest` (14) y dos enganches de `PlotSeriesTest` en presentación;
`LinearTemplatesTest` sube a cinco plantillas en exercises.

La segunda invocación a `allTests`, ya con el recorrido de teclado corregido, devolvió
`BUILD SUCCESSFUL in 7s` con todas las tareas `UP-TO-DATE`: Gradle sólo marca así un target cuando no
cambiaron sus entradas, así que la pasada corta confirma que las 813 unitarias de la tabla siguen
correspondiendo al código de este commit. Lo que sí cambió después de esa pasada es código de
`androidTest`, que no entra en `allTests` y que se volvió a ejecutar entero abajo.

**Instrumentación en el dispositivo: 54 pruebas, 0 fallos, 0 errores** en `companero_v12_qa_20261009(AVD) - 11`
(`BUILD SUCCESSFUL in 3m 32s`), leídas del XML de resultados:

| Clase | Casos |
|---|---|
| `MaticaAppUiTest` | 12 |
| `MaticaMathComponentsTest` | 10 |
| `MaticaLayoutMetricsTest` | 6 |
| `MaticaStateViewsTest` | 5 |
| `MaticaKeyboardWalkTest` | 4 |
| `MaticaScreenInventoryTest` | 4 |
| `MaticaWindowInsetsTest` | 4 |
| `MaticaMotionTest` | 3 |
| `MaticaThemeRenderTest` | 3 |
| `MaticaSplashUiTest` | 2 |
| `MaticaNavigatorRestoreTest` | 1 |

Las 4 nuevas son el recorrido con teclado; `MaticaMathComponentsTest` pasó de 6 a 10 con la figura, el
arrastre del plano y el enganche recta↔punto, y `MaticaLayoutMetricsTest` de 5 a 6 con la medida de tinta.

**Un verde falso que había que anotar.** La primera pasada de esta tanda cerró con `BUILD SUCCESSFUL in
1m 13s` y, sin embargo, el XML decía `tests="1" failures="1"`: `INSTRUMENTATION_FAILED … Unable to find
instrumentation info for mx.matica.debug.test`, precedido de `Failed to uninstall package mx.matica.debug`
y `…debug.test` con `DELETE_FAILED_INTERNAL_ERROR` —el paquete había quedado en un estado intermedio por la
tanda que se detuvo a mitad. Los dos APK se reinstalaron a mano (`adb install -r -t`, `Success` en ambos) y
la segunda pasada es la de la tabla. Si se hubiera leído el código de salida, esta tanda se habría reportado
como verde sin haber ejecutado ni un solo test.

## Backend: el diagnóstico que escribí esta misma tarde ya estaba obsoleto

Escribí «`:backend:api` no existe» contra `main` en `634d99c`, y era cierto en ese commit. Al integrar
`origin/main` para esta PR apareció el PR #9 (temas sin grados escolares + conectividad Ktor), que crea
`backend/api` con `Application.kt`, `PostgresProbe.kt` y `ApiTest.kt` y lo añade a `settings.gradle.kts`.
La corrección va en el documento porque la afirmación anterior, dejada como estaba, sería falsa.

Ejecutado contra el árbol integrado:

```
./gradlew :backend:api:test          # ApiTest: 5 pruebas, 0 fallos, 0 errores
./gradlew :backend:api:run           # PORT=10000
curl -i http://127.0.0.1:10000/healthz     # 200 {"status":"ok","service":"matica-api"}
curl -i http://127.0.0.1:10000/v1/topics   # 200 {"version":1,"topics":[{"id":"integer-operations","title":"Operaciones con enteros"}, …
curl -i http://127.0.0.1:10000/readyz      # 503 {"status":"database_unavailable",…}
```

Es la primera vez que estas pruebas se ejecutan en esta máquina y que el servidor responde de verdad:
`/healthz` y `/v1/topics` en 200, y `/readyz` en 503 exactamente porque no hay `DATABASE_URL`. El 503 no
es un fallo del servidor, es el contrato que documenta `docs/BACKEND_CONNECT.md`. El proceso se detuvo
después de medir (`taskkill` al PID que escuchaba en 10000; el `run` de Gradle deja el JVM hijo vivo al
cancelar la tarea, había que matarlo a mano).

**Defecto encontrado y arreglado: el módulo no compilaba fuera del CI.** `backend/api/build.gradle.kts`
fijaba `jvmTarget = JVM_17` para Kotlin pero no anclaba Java, así que `compileJava` usaba el JDK del
daemon y la validación de consistencia abortaba antes de compilar:

```
> Task :backend:api:compileKotlin FAILED
  Inconsistent JVM-target compatibility detected for tasks 'compileJava' (21) and 'compileKotlin' (17)
```

CI no lo ve porque `actions/setup-java` fija `java-version: '17'` y ahí ambos coinciden; cualquier
desarrollador con un JDK más nuevo no podía ni ejecutar `:backend:api:test`. El arreglo es el mismo
anclaje que ya usan los demás módulos (`androidApp` fija `sourceCompatibility`/`targetCompatibility` a
17): `java { sourceCompatibility = VERSION_17; targetCompatibility = VERSION_17 }`, que funciona con
JDK 17 y con 21 sin exigir un JDK 17 aparte (la alternativa, toolchain, obligaría a instalarlo). Después
del cambio: `BUILD SUCCESSFUL` y 5/5 en verde. Verificación de que el arreglo es el que tiene que ser:
con el bloque quitado el fallo es exactamente el de arriba, y con él puesto la prueba se ejecuta.

## La tanda sobre el árbol integrado volvió a medirse, y encontró óxido de `main`

Los 54 de arriba se midieron sobre `634d99c`. Al integrar `origin/main` (PR #9) la tanda se repitió
entera —el merge toca producción de pantallas, `MainActivity`, `MaticaSession` y el `build` de la app—
y cerró `BUILD FAILED` con `tests="54" failures="2"` en el XML, los dos en `MaticaWindowInsetsTest`:

```
Assert failed: The component with Text + InputText + EditableText contains 'Correo de la escuela' … is not displayed
  at mx.matica.app.MaticaWindowInsetsTest.irAlReproductor(MaticaWindowInsetsTest.kt:216)
```

El helper recorre la ruta real hasta el reproductor y valida cada paso con su marca, y el PR #9 renombró
esa etiqueta en producción («Cuenta de la escuela» → «Cuenta de Mática», «Correo de la escuela» →
«Correo electrónico», por la política de no nombrar etapas escolares) actualizando `MaticaAppUiTest` pero
dejando atrás `MaticaWindowInsetsTest`. La marca seguía comprobando lo mismo —que el campo del login está
pintado de camino al reproductor—, así que lo obsoleto era el texto, no la aserción: se cambió a
«Correo electrónico» y la clase pasó 4/4 (`BUILD SUCCESSFUL in 42s` con `-Pandroid.testInstrumentation
RunnerArguments.class=mx.matica.app.MaticaWindowInsetsTest`). Busqué después si otro test del módulo
pedía alguna de las frases que el PR #9 borró (`escuela`, `no pide tu edad`, `perfil vive`,
`datos de adultos`, `repasos y logros`): ninguna otra aparición.

Por qué el CI del PR #9 no lo vio: `ci.yml` **compila** las pruebas instrumentadas
(`:apps:androidApp:compileDebugAndroidTestKotlin`, añadido en ese propio PR) pero no las ejecuta, porque
no hay emulador en el runner. Un aserto obsoleto en `androidTest` sólo se ve en máquina.

Terminada la corrección, la tanda completa sobre el árbol integrado cerró `BUILD SUCCESSFUL in 3m 11s`
con **`tests="54" failures="0" errors="0"`** en el XML y el mismo reparto por clase de la tabla de arriba.
La batería unitaria sobre ese árbol: **815** en los dos targets KMP (`jvmTest` 408 + `testAndroidHostTest`
407, 0 fallos) más las **5** de `:backend:api`, y `assembleDebug` en verde. La diferencia con los 813
medidos antes del merge es `TopicNamingPolicyTest` (1 por target), que llega con el PR #9.

## Lo que esta tanda NO certifica

- **TalkBack real.** El AVD no tiene ningún lector de pantalla instalado:
  `settings get secure enabled_accessibility_services` devuelve `null`,
  `dumpsys package com.google.android.marvin.talkback` responde «Unable to find package» y la consulta
  de servicios `android.accessibilityservice.AccessibilityService` no devuelve ninguno. Las
  descripciones se verifican leyendo el árbol de semántica, no con un lector de pantalla que las
  pronuncie; B3-04 sigue sin auditoría con TalkBack real.
- **Teclado físico.** Se inyectan las teclas del sistema (`KEYCODE_TAB`, `KEYCODE_DPAD_*`, `KEYCODE_ENTER`)
  desde `Instrumentation`, no desde un teclado USB; la navegación entre destinos está probada, no la
  latencia ni el comportamiento con un hardware concreto.
- **Persistencia real contra PostgreSQL (`/readyz=200`).** El servidor ya existe y ya corre (arriba),
  pero `/readyz` responde 503 porque no hay `DATABASE_URL` con credenciales de una base dedicada a
  Mática: `psql -U postgres -h 127.0.0.1 -p 5432` se queda pidiendo contraseña, en el repo no hay
  ninguna credencial y la instancia local también sirve a SIII y Compañero, así que no se adivina la
  clave ni se crea una base a ciegas en terreno ajeno. Falta esa decisión de Erick, no el código.
- **Docker Desktop.** El CLI responde (`29.9.2`) pero el engine no: `docker info` falla contra el pipe
  `dockerDesktopLinuxEngine` y la distribución WSL `docker-desktop` está `Stopped`. No bloqueó nada de
  lo de arriba: el servidor corrió con `:backend:api:run` directamente, sin contenedor.
- **CI de este push.** Se consulta al abrir la PR; nada de este documento declara un verde que no se
  haya visto.
