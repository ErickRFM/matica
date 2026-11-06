# Evidencia — tanda de instrumentación de la capa de presentación (2026-11-06)

Dispositivo: `companero_v12_qa_20261009(AVD) - 11` (emulador `emulator-5554`, Android 11 / API 30).
Antes de correr se verificó que la actividad en primer plano no era de otra sesión (`mResumedActivity: com.google.android.apps.nexuslauncher/.NexusLauncherActivity`) y que no había `uiautomator` vivo; el emulador se comparte con el proyecto Compañero.

## Ejecutado

```
./gradlew :apps:androidApp:connectedDebugAndroidTest
```

Resultado leído por XML (`apps/androidApp/build/outputs/androidTest-results/connected/debug/TEST-*.xml`), no por el código de salida:

```
<testsuites tests="27" failures="0" errors="0">
```

| Clase | Casos | Qué observa |
|---|---|---|
| `MaticaThemeRenderTest` | 3 | píxel real contra `colorScheme.surface`/`background` (`captureToImage()`) |
| `MaticaAppUiTest` | 5 | host montado, tema, cabecera y navegación básica |
| `MaticaScreenInventoryTest` | 4 | los 18 destinos se montan uno a uno con su texto exacta una vez; pila empuja/regresa; hoja abierta por enlace profundo no se duplica; historial corrupto conserva las rutas válidas |
| `MaticaNavigatorRestoreTest` | 1 | `MainActivity.recreate()` devuelve la pila `welcome → home → route → skill` y cada atrás deshace un nivel |
| `MaticaStateViewsTest` | 5 | carga/vacío/error/offline/éxito dibujados y el reintento invocado |
| `MaticaMotionTest` | 3 | escala 0 llega al compositor con fundido topado a 150 ms; «Transiciones reducidas» y «Ninguna» guardados sin dejar pantalla vacía |
| `MaticaMathComponentsTest` | 6 | ecuación/fracción/recta/plano desde el `ExerciseRenderPlan`, salto por marcas exactas, las **cuatro** plantillas del catálogo releeles por el motor y la fracción de la solución sólo tras pedirla |

Batería unitaria del mismo commit, por target (`build/test-results/**`, leído con script y no a mano):

| Módulo | `jvmTest` | `testAndroidHostTest` |
|---|---|---|
| `:shared:math-engine` | 132 / 0 | 132 / 0 |
| `:shared:math-remote` | 11 / 0 | 10 / 0 |
| `:shared:learning-engine` | 66 / 0 | 66 / 0 |
| `:shared:exercises` | 25 / 0 | 25 / 0 |
| `:design-system` | 23 / 0 | 23 / 0 |
| `:shared:presentation` | 105 / 0 | 105 / 0 |

Más `:apps:androidApp:assembleDebug` en verde. Las 2 nuevas de presentación son `ParseNumberTest` y el invariante `lasPlantillasConocidasSonLasDelCatalogo` en `MaticaRouteTest`.

## Cuatro defectos que encontró la tanda (arreglados en el código, no en las aserciones)

1. **Hoja modal pintada dos veces.** Un historial que llega directo a `HintSheet` (`lesson/X/1/hints`) la montaba como `base` y también como capa modal. Arreglo: `shared/presentation/src/commonMain/kotlin/mx/matica/presentation/app/MaticaApp.kt:84` deja el `base` vacío cuando la hoja ya es la propia base.
2. **Flechas duplicadas en la práctica.** `NumberLineView` ya expone «Marca anterior»/«Marca siguiente»; la pantalla de práctica repetía los dos botones, así que un lector de pantalla anunciaba el control dos veces. Arreglo: se eliminó el `Row` duplicado en `MaticaLessonScreens.kt:448`.
3. **Fuga de la solución.** La fracción mixta del resultado se dibujaba siempre, antes de que el estudiante escribiera nada. Arreglo: `MaticaLessonScreens.kt:515` la muestra sólo con `revealed`, que activa «Mostrar la solución».
4. **`parseNumber` tiraba negativos y fracciones.** Aceptaba sólo `Expr.Const`, pero el AST conserva la forma escrita (`-9` es `Neg(Const(9))`, `3/4` es un cociente), así que devolvía `null` para los mismos formatos que el campo promete aceptar — y en la recta numérica el marcador volvía a la solución, lo que se veía como «las flechas no hacen nada». Arreglo: `MaticaScreenSupport.kt:26` evalúa la expresión constante con `MathEngine`; variables, ecuaciones, magnitudes con unidad y `1/0` siguen dando `null`. Sonda que lo demostró: `ParseNumberTest` falló en la línea de `-9` antes del arreglo y pasa completo después.

## Lo que esta tanda NO certifica

- TalkBack real ni recorrido con teclado físico: las descripciones se verifican en el árbol de semántica, no con un lector de pantalla.
- Rotación en el POCO X7 Pro físico: verificada sólo en el emulador.
- Integración backend Ktor: no ejecutada (sin Docker ni PostgreSQL en el entorno).
- Fidelidad métrica al Figma: Dev Mode exige sesión; los tokens siguen siendo derivación propia medida por píxel.
- CI de este push: se consulta al abrir la PR; nada de este documento declara un verde que no se haya visto.

## Trampa de método que quedó documentada

Probar la rotación con `createAndroidComposeRule<ComponentActivity>()` + `rule.setContent { ... }` **no puede funcionar**: el `setContent` de la prueba no forma parte del `savedInstanceState`, así que la actividad nueva nace sin composición y el `Saver` nunca se ejercita. La prueba real pasa por el host de la app (`createAndroidComposeRule<MainActivity>()` + `recreate()`), y ahí apareció otro hecho del grafo: en un perfil recién creado el único nudo tocable del mapa es `INTEGER_OPERATIONS`, porque `BALANCE_PRINCIPLE` exige distribuir, recoger semejantes y signos — pulsar un nudo bloqueado no navega, y la prueba debe recorrer el camino que el dedo sí puede recorrer.

## Segundo paso: cerrar el hueco de `integer-ops` que la propia tanda documentó

El cierre de B2-04 dejó escrito que `integer-ops` tenía plan pero no era destino de `Practice`. Se convirtió ese hueco en aserción y luego se arregló el código, en ese orden:

1. **Invariante unitario** — `MaticaRouteTest.lasPlantillasConocidasSonLasDelCatalogo` exige `KNOWN_TEMPLATES == ExerciseTemplates.all.map { it.id }` y que cada id del catálogo parseé como `Practice(id, 12)`. **Falló antes del arreglo** (el catálogo tenía 4 ids y la lista a mano 3), que es la evidencia de que el hueco era real y no teórico.
2. **Arreglo en producción** — `shared/presentation/src/commonMain/kotlin/mx/matica/presentation/route/MaticaRoute.kt:90` deriva `KNOWN_TEMPLATES` del catálogo de `:shared:exercises` con `mapTo(LinkedHashSet())`, conservando el orden del currículo. La lista escrita a mano quedó eliminada: era la causa del desfase, no un detalle olvidado.
3. **Prueba en dispositivo** — el bucle `lasPlantillasDelCatalogoSeDibujanConSusComponentes` recorre ahora las **cuatro** plantillas reales, no tres nombradas en la prueba. Salió a la luz un segundo defecto de UI al ampliarse la cobertura: `ExerciseBody` pintaba el plano `if (plan.hasGraph)` sin rama `else`, así que en `integer-ops` la sección desaparecía en silencio; ahora hay un aviso explícito (`MaticaLessonScreens.kt:472`) y la prueba lo exige.
4. **Ajuste de aserción, no de expectativa** — el bucle asumía que toda plantilla declara `ANSWER_FRACTION`. `integer-ops` declara `EQUATION, NUMBER_LINE, VERIFICATION` porque su solución es un entero y pintarla como fracción mixta era ruido. La prueba se bifurcó por el propio plan: con fracción, se exige la `FractionView` tras «Mostrar la solución»; sin fracción, se exige `Marcado: <solución>` en la recta y se comprueba que la fracción mixta **no** aparece ni revelando.

Resultado de la re-verificación completa: `<testsuites tests="27" failures="0" errors="0">` en el mismo AVD y 723 pruebas unitarias (362 × 2 targets, con la diferencia habitual de `math-remote`: 11 en `jvmTest` por el caso que sólo corre en JVM) sin fallos.
