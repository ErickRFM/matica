# Auditoría de entorno — Mática (Bloque 0)

Fecha: 2026-11-04 · Máquina: Windows 11 (10.0.26300), x64 · Workspace: `C:\Users\erik5\OneDrive\Escritorio\mat`

## 1. Verificado con comando

| Elemento | Resultado | Evidencia |
|---|---|---|
| JDK del sistema | OpenJDK 21.0.10 (JetBrains Runtime) | `java -version`, `javac -version` |
| JDK alternativo | Temurin 17 en `~/.gradle/jdks` | listado del directorio |
| Android SDK | `C:\Users\erik5\AppData\Local\Android\Sdk` (`ANDROID_HOME`) | plataformas 34, 35, 36, 36.1, 37.0; build-tools 34→37 |
| Gradle global | No instalado en `PATH` | `gradle: command not found` |
| Gradle cacheado | 8.7 … 9.5.0 en `~/.gradle/wrapper/dists` | usado como semilla del wrapper |
| Git | 2.45.1 | `git --version` |
| Red a repositorios | 200 OK en Google Maven, Maven Central, plugins.gradle.org, services.gradle.org, api.github.com | `curl -I` |
| Dispositivos Android | Ninguno conectado | `adb devices` (lista vacía) |
| Docker | CLI presente, **daemon apagado** | fallo de conexión a `dockerDesktopLinuxEngine` |
| PostgreSQL local | No hay `psql` en `PATH` | `which psql` vacío |

## 2. Toolchain fijado por prueba real, no por suposición

Las combinaciones se eligieron y luego se validaron compilando:

- **Gradle 9.6.1** — AGP 9.5.0 rechazó 9.5.0 con `Minimum supported Gradle version is 9.6.0`.
- **AGP 9.5.0** (última estable; 9.6.0 sigue en alpha) + **Kotlin 2.4.21**.
- **compileSdk 37** — con 36 el build falló: `androidx.core:core:1.19.1 … requires … version 37 or later`.
- **minSdk 26**, **targetSdk 36**, `jvmTarget 17`.

Consecuencias de AGP 9 (documentadas porque cambiaron la forma de escribir los `build.gradle.kts`):

- `org.jetbrains.kotlin.android` **ya no se aplica**: AGP 9 trae Kotlin integrado (`The 'org.jetbrains.kotlin.android' plugin is no longer required`). El bloque usado es `android { kotlin { compilerOptions { jvmTarget } } }`.
- `com.android.library` **no convive** con `org.jetbrains.kotlin.multiplatform`; se usa `com.android.kotlin.multiplatform.library` con el bloque `android { … }` dentro de `kotlin { … }`, y `withHostTest { }` / `withDeviceTest { }` para habilitar tests del target Android.
- Los plugins de AGP se declaran una sola vez en la raíz con `apply false`; solicitar un segundo plugin del mismo artefacto desde un submódulo produce `plugin is already on the classpath with an unknown version`.

## 3. Referencias NO disponibles (limitaciones registradas)

El prompt maestro exige no afirmar lecturas imposibles. Estas son las reales:

1. **`Matica_Blueprint_Full_Kotlin_KMP_Compose_Ktor_2026.docx` no existe en el entorno.** Búsqueda por `*matic*`, `*Blueprint*Kotlin*` y `*Mática*` en OneDrive, Documentos, Descargas, Escritorio y `AndroidStudioProjects` devolvió solo archivos ajenos (p. ej. `Matematicas basicas.pdf`). Los requisitos se tomarán del prompt maestro y se documentarán como fuente única hasta que el blueprint sea aportado.
2. **Figma `kyQeyUyxdNwYP5r4UTmR1i`**: sigue sin conector MCP y sin exportación local, pero el 2026-11-05 se abrió en el navegador integrado con sesión anónima y sí se pudo inspeccionar en modo lectura. Evidencia y límites en `docs/evidence/figma-inspeccion-2026-11-05.md`: se obtuvieron los 13 nodos raíz con su id, las 6 pantallas y una paleta muestreada por píxeles; **no** se obtuvieron radios, tipografía ni espaciado exactos porque inspeccionar exige registrarse. Los tokens que no salieron del archivo se declaran derivación propia en `docs/ARCHITECTURE.md`.
3. **Sin dispositivo o emulador Android conectado**: se puede compilar e instalar un APK, pero no hay captura real de pantalla ni medición de rendimiento en hardware hasta que se conecte un equipo o se arranque un AVD.
4. **Docker daemon apagado**: las pruebas de integración contra PostgreSQL real requieren que el usuario inicie Docker Desktop o indique una instancia de Supabase/PostgreSQL accesible.
5. **Sin credenciales**: no existen `google-services.json` propio de Mática, secretos de Supabase ni keystore de release. FCM y la firma release quedan bloqueadas hasta entonces; no se simulan.
6. **Propios proyectos de referencia** (`Compañero de Clase`, `SIII`, `ManeComb`) están en el disco (`OneDrive\Escritorio\Manecomb`, `…\Sii`, `…\Gatica`), pero no se copiará código de ellos sin revisar licencia y acoplamiento; sólo patrones.

## 4. Estado de la compilación base

```
:shared:math-engine:jvmTest        BUILD SUCCESSFUL (5/5 pruebas Rational)
:apps:androidApp:assembleDebug     BUILD SUCCESSFUL → apps/androidApp/build/outputs/apk/debug/androidApp-debug.apk (14 MB)
```

## 5. Decisiones de investigación de bibliotecas

- **KMath** (`space.kscience:kmath-core`) última publicación **0.6.0**: ofrece estructuras y operaciones numéricas/álgebra, **no un sistema completo de álgebra simbólica**. El motor simbólico (AST, parser, equivalencia, transformaciones) es propio. KMath quedó fuera del camino crítico; se registró en el catálogo para su evaluación y **la auditoría de 2026-11-05 lo descartó y lo retiró del catálogo** (motivos y evidencia en `docs/DEPENDENCIES.md`, §3).
- **BigNum multiplatform** (`com.ionspin.kotlin:bignum` 0.3.10): sí aporta `BigInteger`/`BigDecimal` multiplataforma; se usa como base de `Rational` exacto porque `java.math.BigInteger` no existe en targets no-JVM.
- **Koin 4.2.2** ( compilado contra Kotlin 2.3.20) en lugar de Hilt: Hilt es exclusivo de Android y el prompt pide DI compartida entre targets KMP.
- **SQLDelight 2.4.1**, **Ktor 3.6.1**, **kotlinx-coroutines 1.11.0**, **kotlinx-serialization 1.12.0**, **kotlinx-datetime 0.8.0**, **Compose Multiplatform 1.9.3**, **compose-bom 2026.09.00**, **navigation-compose 2.10.2**, **Turbine 1.2.1**, **JUnit 5 BOM 5.14.4** — versiones estables leídas de Maven Central/Google Maven el 2026-11-04.
