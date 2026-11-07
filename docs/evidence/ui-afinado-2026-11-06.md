# Afinado de UI del 2026-11-06 (UI-08)

Auditoría senior sobre las cuatro pantallas fotografiadas en el teléfono (Hoy, lista de lecciones,
reproductor de lección y Diagnóstico inicial) y el bloque de correcciones que Erick aprobó con un
«Continua». Todo lo numérico de este documento está medido en la AVD `companero_v12_qa_20261009`
(Android 11 / API 30, 1080x2340, 440 dpi → 392 dp de ancho y 750 dp de alto en las pruebas), no en el
POCO X7 Pro de las fotos: los dp se transfieren entre los dos aparatos, los píxeles no.

Rama: `feat/bloque-3-ui-afinado` sobre `769eaa0`. Esta fila de trabajo **no** va al PR #10.

## 1. Matriz de problemas, con gravedad y pantalla

| # | Hallazgo | Pantalla | Gravedad | Cómo se supo |
|---|---|---|---|---|
| 1 | `GeometryView` no dibujaba nada: `onSizeChanged` estaba importado y nunca aplicado, `canvasSize` se quedaba en `IntSize.Zero` y los dos `Animatable` no crecían | Reproductor (geometría) | **Funcional** | Leído en el código y medido en pantalla: 99,45 % de un solo color, 1.844 px de primaria (el asa) |
| 2 | Título y ecuación de la misma fila de lección a 0 dp | Lista de lecciones | Alta | Sonda de ritmo: `titulo->ecuacion=0 dp` |
| 3 | El rótulo del botón llevaba su propio padding de 24 dp y el botón salía desbalanceado contra el texto | Lista de lecciones | Alta | Sonda: `anchoBotonAbrir=88 dp` |
| 4 | Glifo de la fila dependía del índice (LESSON sólo en la primera, FORWARD en el resto) | Lista de lecciones | Media | Leído en el código; dos filas idénticas parecían cosas distintas |
| 5 | La frase del veredicto se pintaba dos veces, pegada una debajo de otra (230 caracteres x2) | Reproductor | Alta | Sonda `TEXTOS=`: la misma cadena aparecía dos veces |
| 6 | Chips de contadores animados con `popSpec` (amortiguación 0,42): el número rebotaba | Hoy, Logros | Media | Inventariado en `MaticaChips.kt`; el rebote no aporta información |
| 7 | «1 días» en la insignia de la racha y `habilidad(es)`, `prerrequisito(s)`, `día(s)` en los textos del motor | Hoy, Reproductor, Ruta, Repaso | Media | Sonda `TEXTOS=` pintó `1 días`; nueve sitios en el código |
| 8 | Barbas de la flecha dibujadas al lado opuesto de la punta | Cabecera de todas | Media | Sonda de manchas por columna: la punta salía partida |
| 9 | `ROUTE` con trazo punteado de 6 dp sobre un glifo de 24 dp | Iconos | Baja | Leído en `MaticaGlyph.kt` |
| 10 | Opciones del diagnóstico sin estado de selección y con la etiqueta cruda del instrumento (`x^5`) | Diagnóstico | Media | Foto del teléfono + lectura de `DiagnosisInstrument.kt:149` |
| 11 | `MaticaViewState.Loading` no se mostraba en ninguna pantalla | Todas | Media | El inventario de movimiento no encontró ni un llamador |

## 2. Correcciones y números antes/después

| ID | Corrección | Antes (medido) | Después (medido) |
|---|---|---|---|
| UI-08a | `onSizeChanged` aplicado al `Canvas` y relleno/alineación con colores que no colisionan; los dos lados crecen con `contentSpec` y no con `popSpec` (el rebote se salía del marco de 148 dp) | tinta 0,55 %, ranuraH y ranuraV sin figura | tinta 50,29 %, ranuraH 530 px (58,63 %), ranuraV 383 px (94,10 %), caja de tinta 530x383 px |
| UI-08b | Fila de lección: `spacedBy(xs)` entre título y ecuación; el glifo ya no depende del índice | `titulo->ecuacion=0 dp` | 4 dp |
| UI-08b | Padding del botón al `Row`, no al rótulo | `anchoBotonAbrir=88 dp` | 64 dp de ancho x 48 dp de alto |
| UI-08b | Opciones del diagnóstico en su propia `Column` con `md` | 8 dp entre opciones | 12 dp entre opciones (caja de 328x48 dp) |
| UI-08c | Barbas abiertas hacia el palo con `StrokeCap.Round` | punta partida en dos manchas | una sola mancha por punta |
| UI-08c | `ROUTE` en trazo continuo, con círculo de origen relleno y anillo de destino | punteado de 6 dp | continuo |
| UI-08d | `pluralEs(cantidad, singular, plural)` en `:shared:learning-engine` y llamado en los 9 sitios; la insignia recibe `unit`/`pluralUnit` | «1 días», `habilidad(es)`, `prerrequisito(s)`, `día(s)` | «1 día», «0 días», «falta 1 prerrequisito:», «faltan 6 prerrequisitos:», «(3 días)» |
| UI-08d | Etiquetas del diagnóstico pasan por `prettyMath` | `x^5`, `2x^5` | `x⁵`, `2x⁵` |
| UI-08e | Contador con `contentSpec` en vez de `popSpec` | rebote (amortiguación 0,42) | sin rebote (0,86) |
| UI-08e | `checkingConnection` en la sesión; `MaticaStateView(Loading)` mientras la sonda del servidor está en vuelo y botón deshabilitado | `Loading` nunca se pintaba | esqueleto visible durante la comprobación real (hasta 3.500 ms) |

## 3. Aserciones nuevas y su inversión

Cinco aserciones escritas a partir de lo medido, no de lo esperado, más la sexta que sustituye a una
sonda. Con el arreglo puesto: 5/5 verdes en la AVD y 1/1 la de la flecha (XML de resultados, no
`BUILD SUCCESSFUL`). Con cada defecto reintroducido, la suya falla:

| Aserción | Inversión aplicada | Fallo que produjo |
|---|---|---|
| `MaticaMathComponentsTest.laFiguraGeometricaSeDibuja` | quitar `.onSizeChanged { canvasSize = it }` | `La figura apenas pinta (0.54630253745298 % de la imagen es tinta distinta del fondo)` |
| `MaticaLayoutMetricsTest.lasFilasDeLeccionTienenRitmoYTargetTactil` | `xs` → `xxl` entre título y ecuación | `El título y la ecuación de la misma fila están a 32 dp` |
| `MaticaLayoutMetricsTest.lasOpcionesDelDiagnosticoRespiranYSonTocables` | `md` → `xs` entre opciones | `Entre la opción 1 y la 2 hay 4 dp` |
| `MaticaLayoutMetricsTest.elRechazoDelMotorSePintaUnaSolaVezYElCampoQuedaEnRojo` | el campo vuelve a recibir `feedback?.second` | `La misma frase se pinta dos veces en la práctica: [La operación no se aplicó igual a los dos lados…]` |
| `MaticaAppUiTest.laRachaConcuerdaConElNumeroQueMuestra` | el chip escribe siempre `pluralUnit` | `The component … contains '1 día' … is not displayed` |
| `MaticaLayoutMetricsTest.laFlechaRegresaTieneLaPuntaALaIzquierdaDelPalo` | `wing` invertido en `drawArrow` | `Hay 5 dp de tinta antes de que arranque el palo (28 px frente a 41 px): las barbas se abren hacia afuera de la punta` |

La sexta no estaba en la lista original: `sondaDeLaFlechaRegresa` era una sonda que imprimía y
`throw AssertionError(reporte)` a propósito, así que aparecía en la suite como un fallo permanente
(`tests="60" failures="1"` en el XML completo del 2026-11-06). Convertida en aserción con los números
que ella misma medía: tinta corrida de la columna 44 a la 87 de 132 px sin huecos, palo de 44 px, el
grueso del palo 6 px, y la cabeza (columnas que abren el doble del grueso) naciendo en la punta y
muriendo antes del extremo derecho. El intento intermedio de partir la banda por el centro falló con
`cola=46 px` frente a `grosor=6 px` —las barbas terminan justo a la mitad del palo y el `StrokeCap.Round`
les suma tres píxeles más—, así que la frontera central era un umbral inestable y se sustituyó por
«dónde nace y dónde muere la tinta alta».

La prueba de la racha no escribe `session.streak = 1`: el setter es privado a propósito y la racha sólo
crece con práctica, así que la prueba verifica la solución del ejercicio con el motor y deja que la
racha suba sola.

## 4. Rectificaciones a la propia auditoría

Cuatro afirmaciones mías cayeron contra la medición. Quedan aquí porque forman parte de la evidencia:

1. **«La cabecera cae 0 dp sobre el contenido»** — falso: medido 12 dp y 16 dp según la pantalla.
2. **«Los chips van pegados al borde»** — falso: el chip derecho deja 24 dp hasta el borde de la ventana.
3. **«`abajoDeUltimoBoton=0 dp`»** — artefacto de `performScrollTo` (el último botón se desplaza hasta
   el borde visible), no un defecto de espaciado.
4. **«El borde de error del campo no se tiñe»** — falso, y la medición original estaba mal hecha: la
   sonda capturaba la imagen y *después* llamaba a `performScrollTo`, así que la coordenada no
   correspondía a la foto, y además muestreaba a 1, 4 y 9 px por fuera de la caja. Corregido el orden y
   cambiado la línea de píxeles por un conteo de color dentro de la caja: con el paso rechazado hay
   **7.811 píxeles** del color de error (`#ffb4ab`) dentro de los 902x154 px del campo, frente a 15.651
   en toda la pantalla. El perímetro del campo (2·(902+154) = 2.112 px) por un trazo de ~3,7 px da
   justo ese orden de magnitud: el borde sí se pinta de error.

También se retira la etiqueta de defecto para los **303 dp de hueco bajo la última opción del
diagnóstico**: la pantalla muestra una sola pregunta en un ventanal de 750 dp y está alineada arriba,
que es como se lee un formulario. Estirar la tarjeta para llenar el hueco empeoraría la lectura. Se
documenta la decisión en lugar de simular un arreglo.

## 5. Corrección a la trazabilidad

`B3-05` estaba declarado *completo* con la frase «los tres tipos de plantilla se dibujan con sus
componentes». Para geometría eso era falso en pantalla: el componente existía, parseaba y anunciaba su
contenido, pero `GeometryView` no dibujaba nada (hallazgo 1). La fila se corriige en `docs/TRACEABILITY.md`
y ahora queda respaldada por `laFiguraGeometricaSeDibuja`, que mide tinta y ranuras sobre la imagen del
dispositivo.

## 6. Pendiente, bloqueado y lo que esta evidencia NO certifica

- **Pendiente:** estado de selección visible en las opciones del diagnóstico (la elección se registra,
  pero la alternativa pulsada no se pinta distinto antes de avanzar) y `role`/semántica de radio.
- **Bloqueado:** la fidelidad «respecto a Figma» no puede medirse sin una sesión de Dev Mode; Figma no
  está accesible desde este entorno. Mientras no haya sesión, ninguna cifra de este documento es
  comparación con el diseño.
- **Bloqueado:** TalkBack real sigue sin estar instalado en ningún aparato; el recorrido con lector de
  pantalla continúa siendo *parcial* (B3-04).
- **No cubierto por aserción:** `prettyMath` en las opciones del diagnóstico. El ítem de exponentes es
  el último de la banda 4 y ninguna prueba llega a él sin responder los 13 anteriores, así que la
  conversión de `x^5` queda verificada sólo por la prueba unitaria de `prettyMath`
  (`EquationModelTest`), no por una captura de esa pregunta concreta.
- Nada de esto certifica hardware físico: las medidas son de la AVD API 30. Las fotos del POCO X7 Pro
  sirvieron para señalar los defectos, no para contarlos. Tampoco certifica producción: no hay
  despliegue en este bloque, y el backend sigue sin base dedicada (B7-02 / G-07).
