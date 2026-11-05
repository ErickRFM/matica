# Evidencia en dispositivo real — 2026-11-05

## Equipo

| Dato | Valor medido |
|---|---|
| Modelo | POCO X7 Pro (`rodin_global`, `2412DPC0AG`) |
| Serial | `SO69SCMFM7HQU86T` |
| Android | 16 (API 36) |
| Pantalla | 1220 x 2712 px, densidad 520 dpi (3.25x) |
| `font_scale` | 1.0 (sin agrandar texto) |
| `ui_night_mode` al empezar | 2 (oscuro forzado) |

`targetSdk = 36` y `minSdk = 26`: el equipo entra exactamente en el rango soportado.

## Instalación y arranque

- `adb install -r -t apps/androidApp/build/outputs/apk/debug/androidApp-debug.apk` → `Success`.
- `adb shell am start -W -n mx.matica.debug/mx.matica.app.MainActivity` → `Status: ok`,
  `TotalTime: 778` ms en frío. Con el APK reconstruido después de añadir las dependencias de
  instrumentación, el mismo comando devolvió `Status: ok` y `TotalTime: 938` ms. Son dos medidas,
  no un promedio: el rango observado en este equipo es 778-938 ms.
- La pantalla muestra `Mática` y `1/2 + 1/3 = 5/6`: el motor de racionales exactos funciona en
  hardware, no sólo en la JVM de las pruebas.

## Los tokens llegaron al hardware

Muestreo de píxeles sobre `screencap` (recuadro de 20 382 muestras del área de pantalla):

| Ajuste del sistema | Fondo medido | Token | Texto medido | Token |
|---|---|---|---|---|
| oscuro (`ui_night_mode=2`) | `#0E1626` | `MaticaPalette.Night` | `#EAF0FF` | `MaticaPalette.MistText` |
| claro (`ui_night_mode=1`) | `#F5F8FF` | `MaticaPalette.Mist` | `#17263F` | `MaticaPalette.Ink` |

Coincidencia exacta, canal por canal. El tema oscuro se activó sin tocar el código: `MaticaTheme`
sigue `isSystemInDarkTheme()` y el `Scaffold` pinta `colorScheme.background`.

## Prueba de instrumentación (`MaticaThemeRenderTest`)

Tres casos con `createComposeRule` + `captureToImage()`, ejecutados y contados desde el XML de
resultados: `tests=3 failures=0 errors=0`.

1. `elSmokeScreenSeMuestraConElTemaClaro` — los dos nodos de texto visibles.
2. `laSuperficieQuePintaElTelefonoEsElTokenMedido` — el píxel de `colorScheme.surface` es
   `MaticaPalette.Paper`.
3. `elFondoDePantallaQuePintaElTelefonoEsElTokenMedido` — el píxel de `colorScheme.background`
   es `MaticaPalette.Mist`.

El caso 3 **nació fallando** y el fallo fue útil: la primera versión asumía que `Surface {}` pinta
`background`, y pinta `surface`. El resultado fue `esperado #fff5f8ff real #ffffffff`. Se corrigió
la aserción separando los dos roles en dos pruebas, en lugar de aflojar el umbral.

Ejecutadas en el emulador `sdk_gphone_x86_64` (Android 11, API 30).

## Lo que el teléfono NO permitió

- **Instalar el APK de instrumentación**: `INSTALL_FAILED_USER_RESTRICTED: Install canceled by
  user`. HyperOS exige habilitar «Instalar mediante USB» / «Depuración USB (ajustes de
  seguridad)» en Opciones de desarrollador. Hasta hacerlo, las pruebas Compose corren en
  emulador, no en este equipo.
- **Cambiar el modo oscuro por `settings put secure`**: `SecurityException: WRITE_SECURE_SETTINGS`.
  Sí se pudo con `adb shell cmd uimode night no|yes`; el ajuste se devolvió a su valor original
  (`2`) al terminar.
- **Rotación**: se intentó forzar con `system user_rotation` y la captura siguió en vertical, así
  que la rotación **no queda verificada** en este informe.

## Trampa detectada: verde sin pruebas

`connectedDebugAndroidTest` sobre el teléfono terminó en `BUILD SUCCESSFUL` mientras el runner no
había instalado el APK. El XML lo confirma: `tests="0" failures="0"`. Un CI que sólo mire el código
de salida reportaría verde sin haber ejecutado nada. La cuenta de casos por XML es obligatoria
como evidencia.

## Capturas

Fuera del repositorio, en `C:/Users/erik5/AppData/Local/Temp/matica-device/`:
`app-light.png` (tema oscuro del sistema), `app-claro.png` (tema claro).
