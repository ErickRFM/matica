# Evidencia — marca, splash y login (2026-11-06)

Rama `feat/bloque-3-ui-navigation`. Dispositivo: `companero_v12_qa_20261009(AVD) - 11`
(`emulator-5554`, Android 11 / API 30, 1080×2340, densidad 440 → 392,72 dp de ancho en retrato),
primero en tema oscuro y después en claro (`cmd uimode night no`).

## Qué se construyó

| Pieza | Archivo | Medida o regla |
|---|---|---|
| Marca dibujada | `design-system/src/commonMain/kotlin/mx/matica/design/brand/MaticaMark.kt` | «M» como ruta en `Canvas`; geometría pura (`maticaMarkSegments`, `maticaMarkPoint`); sin PNG ni fuente de iconos |
| Splash | `shared/presentation/src/commonMain/kotlin/mx/matica/presentation/app/MaticaSplash.kt` | capa opaca sobre la app ya compuesta, sólo en arranque frío; un `Animatable` alimenta trazo, mota, rótulo y barra; termina solo (~1,1 s) |
| Host de arranque | `MaticaSplash.kt` (`MaticaLaunch`) | `rememberSaveable` recuerda que terminó, no que empezó: rotar o cambiar de tema no lo repite |
| Login | `shared/presentation/.../app/MaticaNavigationScreens.kt` | cabecera con atrás + píldora «Cuentas en preparación», dos campos desactivados con motivo por campo, CTA local, título `heading` |
| Campo con pista | `shared/presentation/.../app/MaticaScreen.kt` (`MaticaField`) | `hint` separado de `error`; `PasswordVisualTransformation` cuando el teclado es de contraseña |
| Icono y tema de sistema | `apps/androidApp/src/main/res/**` | icono adaptativo (`mipmap-anydpi-v26` + vectores) y `Theme.Matica` con `windowBackground` para que el primer cuadro no sea blanco |

## Batería ejecutada

```
./gradlew allTests
# BUILD SUCCESSFUL in 16s

./gradlew :apps:androidApp:assembleDebug
adb -s emulator-5554 install -r apps/androidApp/build/outputs/apk/debug/androidApp-debug.apk
# Success

./gradlew :apps:androidApp:connectedDebugAndroidTest
# BUILD SUCCESSFUL in 2m 10s
```

Los conteos se leen de los XML, nunca del código de salida.

**Instrumentados** — `apps/androidApp/build/outputs/androidTest-results/connected/debug/TEST-*.xml`:
`tests="45" failures="0" errors="0" skipped="0"`.

| Clase | Casos | Qué mide en el dispositivo |
|---|---|---|
| `MaticaAppUiTest` | 12 | incluye la nueva `elLoginExplicaQueLasCuentasEstanEnPreparacion`: título con semántica `heading`, píldora de estado, los dos campos desactivados (2 nodos con `Disabled`), el aviso y el CTA |
| `MaticaSplashUiTest` | 2 | la barra expone `ProgressBarRangeInfo` y el splash llama a `onFinished` sin entrada del usuario |
| `MaticaWindowInsetsTest` | 4 | insets, scrim y atrás con teclado (sigue verde con el host nuevo) |
| `MaticaLayoutMetricsTest` | 5 | 48 dp, tope de 640 dp, ritmo 8/16 dp |
| `MaticaMathComponentsTest` | 6 | los cuatro componentes matemáticos |
| `MaticaStateViewsTest` | 5 | carga/vacío/error/offline/éxito |
| `MaticaScreenInventoryTest` | 4 | los 18 destinos, uno a uno |
| `MaticaMotionTest` | 3 | escala 0 con fundido topado |
| `MaticaThemeRenderTest` | 3 | píxel real contra el tema |
| `MaticaNavigatorRestoreTest` | 1 | la pila sobrevive a `recreate()` |

**Unitarios** (por módulo, contando `jvmTest` y `testAndroidHostTest`): `:design-system` 33 por target
(+5 de `MaticaMarkTest`), `:shared:presentation` 122 por target (+5 de `MaticaSplashTest`),
`:shared:math-engine` 132, `:shared:learning-engine` 66, `:shared:exercises` 25, `:shared:math-remote`
11 + 10. Total 777 casos, 0 fallos.

## Recorrido visual

Capturas en `%TEMP%\matica-brand-shots` (convención del repo: las capturas no se versionan).

| Archivo | Qué muestra |
|---|---|
| `04-splash.png`, `08-splash-b.png`, `09-splash-c.png` | splash en tres momentos: inicio, ~60 % (marca completa, mota en la ruta, barra a la mitad) y final (punto de llegada, barra llena, «Preparando tu ruta») |
| `18-splash-light-a.png`, `19-splash-light-b.png` | el mismo arranque en claro, con la marca azul sobre `BlueSoft` y el fondo degradado entre `#F5F8FF` y el contenedor primario |
| `10-login-dark.png`, `13-login-dark-v2.png` | login oscuro con la marca, la píldora y el formulario bloqueado; la v2 corrige el placeholder de contraseña |
| `15-login-light.png` | el mismo login tras `cmd uimode night no`; la actividad se recreó, volvió a la ruta de login y **el splash no se repitió** |
| `16-home.png`, `17-drawer.png` | escritorio y cajón de apps con el icono de Mática (M blanca + punto lima sobre degradado azul) |

Sondas que valieron la pena:

1. **El splash translúcido se veía sucio.** La primera versión usaba `copy(alpha = …)` en el
   degradado y dejaba ver la Bienvenida debajo. Se cambió a `lerp(background, primaryContainer, …)`
   con el tema efectivo (`LocalMaticaDarkTheme`), no con `isSystemInDarkTheme`: la capa ya es opaca.
2. **La mota tenía ruido de color.** Con el color semántico de mascota se leía como un anillo pálido.
   Ahora es un punto del color del destino con halo al 28 %, del mismo tamaño relativo que el punto
   de llegada.
3. **Los primeros cuadros en blanco no eran un defecto del tema.** El arranque frío del emulador tarda
   ~3 s; `05-splash-mid.png` y `07-splash-a.png` capturaron antes del primer frame y muestran
   `#0E1626` (el `windowBackground` de `Theme.Matica`), no blanco. La prueba de que el tema de arranque
   sirve es justamente esa.
4. **El placeholder `••••••••` parecía una contraseña ya escrita.** Se cambió a «Tu contraseña» en el
   campo desactivado.

## Lo que NO se ejecutó (y no se declara)

- **Auditoría con TalkBack real** y recorrido con teclado físico: sigue pendiente en Bloque 7. Aquí
  sólo se verificó el árbol de semántica (`heading`, `Disabled`, `ProgressBarRangeInfo`).
- **Splash con movimiento reducido en el dispositivo**: las fases y la duración de 350 ms están
  cubiertas por `MaticaSplashTest`, y `MaticaLaunch` resuelve `motionScale = 0`, pero no se hizo la
  captura con la escala del sistema en 0 ni con «Transiciones reducidas» activo.
- **Splash del sistema (Android 12+)**: el icono de arranque nativo no se probó porque el emulador es
  API 30; en API 31+ el sistema puede mostrar su propia pantalla antes del primer frame.
- **Iconos temáticos de Android 13+** y máscaras de otros lanzadores: sólo se vio el cajón del
  lanzador de API 30.
- **Dispositivo físico y tableta**: todo corrió en el emulador; el login se verificó a 392,72 dp y en
  claro/oscuro, no en ventana ancha.
- **Perfilado de arranque**: el tiempo de ~1,1 s es la animación configurada, no una medición de
  `Displayed` con `am start -W` (esa cifra fue ~2,9 s de arranque en frío del emulador, que incluye
  la composición de la app debajo).
