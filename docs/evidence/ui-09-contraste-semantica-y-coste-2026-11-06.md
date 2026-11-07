# UI-09: contraste, movimiento, semántica y coste por fotograma (2026-11-06)

Continuación de `docs/evidence/ui-afinado-2026-11-06.md` (UI-08). Mática, Compose Multiplatform, rama
`feat/bloque-3-ui-afinado` sobre `769eaa0`. Todo lo que sigue está medido en el AVD
`companero_v12_qa_20261009` (Android 11 / API 30, 1080x2340 a 440 dpi → 392x750 dp, 2 GB de RAM) o en
`jvmTest`, con el comando y el resultado al lado. Lo que no se midió está escrito como NO EJECUTADO, no
como verde.

## 1. Matriz de problemas, con gravedad y pantalla

| # | Problema | Dónde | Gravedad | Qué se hizo |
|---|----------|-------|----------|-------------|
| 1 | El azul de acento `primary` (#426BEB) usado como **texto** no llegaba a AA sobre los fondos reales del tema claro: 4,36:1 sobre `background` (Mist) | Gráfica cartesiana (etiqueta de la intersección) y recta numérica (valor seguido) | Alta: texto legible sólo con buena luz | Rol nuevo `accentText`: `BlueDeep` en claro, `BlueLight` en oscuro; medido 9,2–10,4:1 claro y 6,3–7,9:1 oscuro |
| 2 | El par de acento no estaba en la lista que verifican las pruebas: sólo se medían pares `onX/sobreX`, así que un texto de acento pintado directamente contra el fondo nunca se auditaba | `:design-system` a11y | Alta: el auditor no miraba la categoría que fallaba | Tres pares nuevos (`accentText` contra `background`, `surface`, `surfaceVariant`) añadidos a `maticaContrastPairs` y exigidos por `ThemeTokensTest` en los dos temas |
| 3 | Con rebote (`popSpec`, amortiguación 0,42 → sobretiro ~23 %) aparecían **contenidos**: el marcador de la recta, la barra de la fracción y las celdas del reparto pintaban durante el salto valores que no existen | Reproductor de paso, `NumberLineView`, `FractionView` | Alta: el estudiante lee un número falso | `contentSpec` (amortiguación 0,86, sin sobretiro) para contenido; `popSpec` reservado al feedback de pulsación |
| 4 | «Movimiento reducido» endurecía la amortiguación pero seguía dejando viajar el contenido varios cientos de ms | Transiciones de pantalla y entrada de lista | Media: el ajuste del sistema no se respetaba de verdad | `slideSpec` devuelve `snap()` con `reduced`; el revelado (`Reveal.kt`) hace `snapTo` |
| 5 | Las tres escalas de movimiento del sistema se leían con `Settings.Global.getFloat` en **cada recomposición**, incluida la ruta del arrastre | `platformMotionScale()` (Android) | Media: coste del sistema en la misma ruta que el dibujo | Valor recordado con `remember` + un único `ContentObserver` sobre las tres claves; el cambio del sistema sigue llegando sin reiniciar |
| 6 | El botón de la lección sin plantilla decía «Reintentar» y no reintentaba nada: devolvía a la ruta | Lista de lecciones | Media: el estudiante pulsa dos veces buscando el botón que sí reintenta | `retryLabel` por caso: «Volver a la ruta» / «Volver a la lista», y `onRetry = null` cuando no hay reintento |
| 7 | «Empezar de nuevo» borraba la pizarra sin pedir permiso | Reproductor de paso | Media-alta: pérdida de trabajo no recuperable | `MaticaConfirmDialog` como overlay antes de borrar; los intentos ya revisados se conservan |
| 8 | La pantalla de Aula mostraba el mismo aviso genérico sin distinguir «sin conexión» de «hace falta cuenta» | Aula | Media | Dos estados separados, medidos en `elAulaDistingueSinConexionDeSinBackend` |
| 9 | Un `OutlinedTextField` con `colors` explícito no se nombra a sí mismo ante TalkBack y el rótulo suelto de arriba no pertenece al campo | Campos de login/alta | Media-alta para lector de pantalla | `semantics { contentDescription = label }` en el campo (`labelFor`/`id` no existen en este Compose, buscado en los artefactos 1.10.2) |
| 10 | El chip animado exponía los números interpolados del salto («3 XP», «11 XP», «24 XP»…) y la racha se escribía siempre en plural («1 días») | Hoy y Logros | Media-alta | `clearAndSetSemantics` con el valor final + `liveRegion = Polite`, y `pluralEs` para la unidad |
| 11 | El motivo del rechazo del motor se pintaba dos veces, pegado (campo y tarjeta recibían el mismo `feedback`) | Reproductor de paso | Media | Motivo corto en el campo, veredicto entero una sola vez en la tarjeta |

## 2. Correcciones y números antes/después

Contraste (sonda temporal `ContrasteSondaProbeTest`, ejecutada en `:design-system:jvmTest` el 2026-11-06;
los valores salen de `Contrast.ratio`, la misma fórmula WCAG que exigen las pruebas; la sonda se borró
después de cosechar):

| Par | Antes | Después |
|-----|-------|---------|
| Acento como texto sobre `Paper` (superficie, claro) | `#426BEB` → 4,631:1 | — |
| Acento como texto sobre `Mist` (fondo, claro) | `#426BEB` → **4,356:1 (no AA)** | `accentText` `#2C3E6E` → 9,773:1 |
| Acento como texto sobre `BlueMist` (tarjeta, claro) | `#426BEB` → **4,111:1 (no AA)** | `#2C3E6E` → 9,223:1 |
| Acento como texto sobre `Paper` (superficie, claro) | — | `#2C3E6E` → 10,389:1 |
| Acento como texto sobre `Night` (fondo, oscuro) | — | `#8FA8FF` → 7,932:1 |
| Acento como texto sobre `NightSurface` | — | `#8FA8FF` → 6,650:1 |
| Acento como texto sobre `NightRaised` (tarjeta, oscuro) | — | `#8FA8FF` → 6,305:1 |

Los dos valores «antes» del azul viejo se leyeron de la propia inversión (§3): el mensaje de
`ThemeTokensTest` al volver `accentText` a `#426BEB` imprimió
`Fallas AA: valor seguido sobre la pantalla = 4.356393499400399, valor seguido sobre la tarjeta =
4.111569803808278`. Sobre `Paper` el azul viejo sí llegaba (4,631:1); el par que se salva es el de la
superficie, y los dos que se pintan sobre fondo y sobre tarjeta estaban por debajo de 4,5:1. La
conclusión: en claro el acento como texto no cumplía AA en dos de los tres fondos.

Trazo de error del campo rechazado (`MaticaLayoutMetricsTest#elRechazoDelMotorSePintaUnaSolaVezYElCampoQuedaEnRojo`,
AVD, píxeles contados dentro de `boundsInWindow` del campo):

| Estado del código | Píxeles del rojo del tema dentro de la caja |
|-------------------|--------------------------------------------|
| Prueba heredada contando `DangerLight` con el AVD en oscuro (UI-08) | 7.811 → pasaba |
| La misma prueba, mismo APK, AVD recién formateado (arranca en claro) | 0 → FALLA |
| Con `errorBorderColor` explícito añadido y tema fijado en la prueba | 7.811 de `Danger` → pasa |
| **Sin** esa línea (estado final) | 7.811 de `Danger` → pasa |

O sea: el trazo siempre estuvo; lo que estaba mal era la prueba (§4).

`jvmTest` de los dos módulos de diseño y presentación, leídos de los XML (no del `BUILD SUCCESSFUL`),
tanda del 2026-11-06 22:12–22:13 local:

| Módulo | Suites | Pruebas | Fallos |
|--------|--------|---------|--------|
| `:design-system` | 4 | 33 | 0 |
| `:shared:presentation` | 19 | 143 | 0 |
| Batería completa (`jvmTest` + `testAndroidHostTest` de los seis módulos compartidos + `testDebugUnitTest` de `androidApp`) | 50 | 366 | 0 |

`ThemeTokensTest` se volvió a correr dos veces más durante la inversión de contraste (falla 1 con el
color viejo, 0 con el actual); el resto de la tabla es la corrida limpia de las 22:12 y la verificación
de las 22:5x después de revertir.

Batería instrumentada en el AVD, una invocación `am instrument` por clase (un proceso por clase: la
tanda completa en un solo proceso caía por falta de memoria a las once pruebas). Tanda del 2026-11-06
22:31–22:46 local, sobre el AVD ya reiniciado y con los dos APK de la misma compilación:

| Clase | Pruebas | Resultado |
|-------|---------|-----------|
| `MaticaLayoutMetricsTest` | 10 | OK |
| `MaticaMathComponentsTest` | 11 | OK |
| `MaticaStateViewsTest` | 5 | OK |
| `MaticaThemeRenderTest` | 3 | OK |
| `MaticaWindowInsetsTest` | 4 | OK |
| `MaticaSplashUiTest` | 2 | OK |
| `MaticaNavigatorRestoreTest` | 1 | OK a la segunda; la primera corrida murió con `shortMsg=Process crashed` y sin `FATAL EXCEPTION` en logcat |
| `MaticaAppUiTest` | 13 | OK |
| `MaticaSemanticsTest` | 3 | OK |
| `MaticaConfirmGuardTest` | 3 | OK |
| `MaticaMotionTest` | 3 | OK |
| `MaticaRevealTest` | 2 | OK |
| `MaticaKeyboardWalkTest` | 4 | OK |
| `MaticaScreenInventoryTest` | 4 | OK |
| **Total** | **68** | **0 fallos** |

El recuento se leyó de la salida de cada `am instrument` (`OK (N tests)` / `Tests run: N, Failures: M`),
no del estado del comando ni de un `BUILD SUCCESSFUL`.

## 3. Aserciones nuevas y su inversión

- **`ThemeTokensTest` (JVM, 10)** exige AA 4,5:1 a todos los pares de `maticaContrastPairs`, incluidos los
  tres de `accentText`. **Inversión ejecutada** (2026-11-06, `:design-system:jvmTest --tests ThemeTokensTest`):
  con `accentText` del tema claro vuelto a `#426BEB`, la suite falla 1 de 10 con
  `Fallas AA: valor seguido sobre la pantalla = 4.3564, valor seguido sobre la tarjeta = 4.1116`; con
  `BlueDeep` vuelve a 0 fallos. El par sobre `Paper` (4,631) no aparece en la lista porque sí cumplía.
- **`ComponentContrastTest` (JVM, 4)** sobre los pares que dibujan los componentes.
- **`MaticaMotionTest` (instrumentada, 3)** y **`MotionStyleTest` (JVM, 12)**: la escala 0 del sistema
  topa el fundido y `slideSpec`/`Reveal` dejan de viajar.
- **`MaticaMathComponentsTest`** y **`MaticaSemanticsTest`**: el chip expone el valor final y no los
  cuadros interpolados, el valor es región viva, y «1 día» no se escribe «1 días».
- **`MaticaConfirmGuardTest` (3)**: el borrado de pizarra pide confirmación y no tira los intentos ya
  revisados.
- **`MaticaLayoutMetricsTest` (10)**: el motivo corto una sola vez, el veredicto entero una sola vez, y
  el trazo de error dentro de la caja del campo.

Inversión medida de la aserción del trazo de error: puesta la línea `errorBorderColor` y quitada, el
conteo es el mismo (7.811), así que la aserción **no** dependía de ella; lo que la hacía fallar era el
tema. Con la prueba sin fijar el tema (`ThemeMode.SYSTEM`) en un AVD en claro, el conteo del rojo del
tema oscuro es 0 y la aserción falla con «sólo 0 píxeles». Eso es lo que se veía antes de tocar nada.

De las demás aserciones de la lista, la única inversión vuelta a ejecutar en esta tanda es la del
contraste. El resto (confirmación de la pizarra, chip sin números interpolados, `snap` con movimiento
reducido, motivo corto una sola vez) se mide por primera vez con su defecto ya corregido y queda verde;
su inversión se documentó en la tanda UI-08 (`docs/evidence/ui-afinado-2026-11-06.md`) o no se llegó a
hacer. Decirlo aquí es parte de la evidencia: una aserción verde sin inversión probada sólo prueba que
el código actual pasa el test que escribí, no que el test detecte el defecto.

## 4. Rectificaciones a la propia auditoría

1. **Caí de nuevo en una hipótesis que la tanda UI-08 ya había tumbado.** Atribuí la ausencia del trazo
   rojo a que dar un objeto `colors` explícito a `OutlinedTextField` «quita el teñido de error por
   defecto». `docs/evidence/ui-afinado-2026-11-06.md` §4.4 ya decía que eso es falso y ya había medido
   7.811 píxeles del rojo del tema dentro de la caja. En esta versión (Material3 de
   `composeBom 2026.09.00`) los parámetros omitidos valen `Color.Unspecified` y Material los resuelve
   contra el esquema: medido con y sin `errorBorderColor`, 7.811 píxeles en ambos casos. La línea que
   añadí se quitó; sólo queda un comentario que lo documenta en
   `shared/presentation/.../MaticaScreen.kt:206`.
2. **El parámetro que escribí no existe.** Mi primer intento usó `focusedErrorBorderColor` /
   `unfocusedErrorBorderColor`; en este Material3 el parámetro se llama `errorBorderColor` y no distingue
   foco. Lo dijo el compilador (`No parameter with name 'focusedErrorBorderColor' found`), no la
   documentación.
3. **La prueba era dependiente del aparato.** Contaba un solo rojo (`DangerLight`, el del tema oscuro)
   mientras la app seguía el modo noche del sistema (`ThemeMode.SYSTEM`). Pasaba en un AVD en oscuro y
   fallaba en uno recién formateado, que arranca en claro. Se corrigió fijando el tema en la propia
   prueba (`ses.updateSettings { it.copy(themeMode = ThemeMode.LIGHT) }`) y contando el rojo de ese tema.
4. **«0 píxeles» no significaba «no hay trazo».** Es el mismo mensaje de fallo para un defecto real y
   para un condeo contra el color equivocado. La aserción sigue siendo válida; la interpretación que hice
   de ella, no.
5. **Dos falsos fallos por el aparato, no por el código.** `logrosNoRepiteLasInsigniasDeLaCabecera` y
   `laRachaConcuerdaConElNumeroQueMuestra` fallaron en la tanda con el AVD corrupto; la sonda de
   semántica en un AVD sano imprimió `SONDA HOY 0 XP` / `SONDA HOY 0 días` y ambas clases quedaron
   verdes. No cambié ni una aserción para lograrlo.

## 5. Lo que se tocó

- `design-system`: `MaticaSemanticColors` (rol `accentText`), `ThemeContrast` (tres pares medidos),
  `MaticaMotion` (`contentSpec`, `slideSpec` con `snap` reducido, `popSpec` reservado),
  `Reveal.kt`, `MotionScale.android.kt` (memoria + `ContentObserver`), `MaticaLayout`.
- `shared/presentation`: `MaticaScreen` (campo autodenominado, `contentSpec` en el contador de progreso),
  `MaticaScreenSupport` (motivo corto del rechazo y `graphWindow` como única fuente de la ventana x),
  `MaticaLessonScreens` (confirmación, rótulos de reintento por caso), `MaticaNavigationScreens` (Aula
  distinguiendo offline de sin cuenta), `MaticaChips`, `MaticaButton`, `MaticaGlyph`,
  `CartesianGraphView`, `NumberLineView`, `FractionView`, `GeometryView`, `RouteMapView`.
- `apps/androidApp`: pruebas instrumentadas.

## 6. Pendiente, bloqueado y lo que esta evidencia NO certifica

- **Coste por fotograma NO medido con `gfxinfo`.** La auditoría UI-09 prometía «cuánto cuesta cada
  recomposición»; lo que hay es una lectura de código: las tres escalas del sistema ya no se leen por
  fotograma y el revelado tiene una clave por componente (`RevealKeyTest`, 8 verdes). No hay número de
  frames caídos, ni `Janky frames`, ni comparativa antes/después de rendimiento. **NO EJECUTADO.**
- **Habla del lector de pantalla no escuchada.** TalkBack no está instalado en el AVD: las aserciones de
  semántica leen el árbol, no lo que dice el motor de voz. Un test simulado no certifica accesibilidad
  real.
- **Figma sigue sin existir como fuente.** La fidelidad al diseño (tarea #27) no puede medirse contra un
  archivo que no está en el repositorio.
- **Dispositivo físico**: el POCO `SO69SCMFM7HQU86T` está conectado pero rechaza la instalación de
  pruebas (`INSTALL_FAILED_USER_RESTRICTED`), así que ninguna medida de esta tanda es de hardware real.
- **Inestabilidad del AVD durante la tanda.** A las 22:23 el emulador se quedó `offline` a media batería y
  hubo que matar el proceso QEMU (PID 36060) y volver a arrancarlo sin `-wipe-data`, así que los datos se
  conservaron. Después de reiniciar, las dos primeras clases de la tanda devolvieron
  `shortMsg=Process crashed` y `MaticaNavigatorRestoreTest` volvió a morir una vez más; re-corridas
  individualmente, todas quedaron verdes y el total de 68 se compone de 14 corridas por clase, no de una
  sola pasada limpia. Un proceso que se cae y pasa a la segunda no es la misma garantía que una tanda
  seguida: queda anotado.
- **CI en el PR apilado.** El trabajo va en `feat/bloque-3-ui-afinado` (tres commits sobre `769eaa0`) con
  PR #12 contra esa rama, no contra `main`. `.github/workflows/ci.yml` dispara `pull_request` sólo con
  base `main`, así que **el PR no recibe checks por sí solo**; se lanzó una corrida manual por
  `workflow_dispatch` sobre la rama (`gh run 42343961778`). Sin esa corrida en verde no se fusiona nada, y
  el verde de una corrida manual no es el mismo check que exige la protección de `main`.
- Este documento certifica pruebas ejecutadas en un emulador local. No certifica operación en producción,
  ni CI, ni que el PR esté fusionado.
