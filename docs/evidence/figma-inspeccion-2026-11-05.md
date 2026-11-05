# Evidencia de inspección del Figma de Mática — 2026-11-05

## Cómo se obtuvo

- Archivo: `https://www.figma.com/design/kyQeyUyxdNwYP5r4UTmR1i/` (nombre real del documento:
  *Mática — App educativa | UX/UI exploración v1*).
- Medio: navegador integrado de la app, **sesión anónima**. El archivo abre en modo lectura.
- No existe conector MCP de Figma en este entorno (`mcp_list` con la palabra `figma` devolvió
  0 herramientas) y no hay exportación local del diseño en el repo.
- Capturas guardadas fuera del repositorio, en
  `C:/Users/erik5/AppData/Local/Temp/figma-matica/`: `board-17.png` (tablero al 17 %) y
  `panel-left-1.png` (panel de capas abierto).
- El árbol de capas se leyó del DOM del panel izquierdo: cada fila expone un
  `data-testid` con el id de nodo de Figma, así que los ids de esta lista son los del archivo,
  no inventados.

## Estructura real del archivo

Una sola página: `Page 1`. Trece nodos de primer nivel:

| Id Figma | Nombre | Tipo en el panel |
|---|---|---|
| `3:4` | iPhone 16 - 1 | Marco |
| `3:5` | iPhone 16 - 2 | Marco |
| `3:6` | iPhone 16 - 3 | Marco |
| `11:214` | iPhone 16 - 4 | Marco |
| `11:215` | iPhone 16 - 5 | Marco |
| `11:216` | iPhone 16 - 6 | Marco |
| `11:218` | gemas | Marco |
| `11:220` | xp | Marco |
| `11:3`, `11:10`, `11:21`, `11:32`, `11:43` | Boton | Componente (5 variantes) |

Contenido visible en las seis pantallas (leído en la captura del tablero, texto propio del diseño):

1. **iPhone 16 - 1** — Bienvenida: rótulo «MÁTICA», «¡Las matemáticas pueden ser divertidas!»,
   mascota circular verde y CTA «¡Vamos a empezar!».
2. **iPhone 16 - 2** — Inicio: saludo «¡Hola, explorador!», tarjeta «Algunos bloques»,
   sección «Tu misión de hoy» y «Recomendado para ti»; barra de navegación inferior con 5 destinos.
3. **iPhone 16 - 3** — «Tu ruta matemática» con el subtítulo «El mapa del álgebra» y un grafo de
   nodos circulares conectados.
4. **iPhone 16 - 4** — «Lección 3: Ecuaciones · Encuentra el valor de x», ecuación `2(x + 3) = 14`,
   pregunta «¿Cuál es el primer paso correcto?» con tres opciones y botón «Comprobar respuesta».
5. **iPhone 16 - 5** — Cierre de lección: «¡EXCELENTE!», «¡Lección completada!», tres métricas
   (`120 XP`, `88 %`, `06:30`) y acciones «Ver detalles de la lección» / «Siguiente lección».
6. **iPhone 16 - 6** — «Mi perfil»: «Explorador matemático», tres contadores (`7`, `14`, `280`),
   «Mi progreso» con cuatro insignias circulares y «Configuración y accesibilidad».

Los marcos `xp` y `gemas` son exploraciones de gamificación separadas de las pantallas.

## Paleta muestreada

No se pudo leer Dev Mode: la barra del editor dice literalmente «Regístrate para hacer un
comentario, editar, **inspeccionar** y más», así que los hex no son exportados del archivo.
Los valores siguientes se muestrearon por conteo de píxeles sobre `board-17.png` (recorte de la
zona de pantallas, 158 400 píxeles). Son colores del render, con el sesgo propio de un raster
al 17 % de zoom.

| Hex | Área del recorte | Papel observado |
|---|---|---|
| `#426BEB` | 3.07 % | primario: CTAs y selección |
| `#B7EF59` | 1.26 % | lima de acento y mascota |
| `#9075EF` | 1.13 % | violeta de la ruta |
| `#14BFA6` | 0.33 % | teal de insignias |
| `#F5F8FF` | 15.47 % | fondo de pantalla |
| `#FFFFFF` | 8.58 % | superficie de tarjeta |
| `#EAF2FF` | 4.76 % | chip / superficie variante |
| `#E7FAF3` | 3.38 % | superficie de acierto |
| `#D5E1FF` | 0.30 % | divisores y contenedores suaves |
| `#F0EAFF` | 0.19 % | tinte violeta |
| `#17263F` | 124 px | texto fuerte (no es negro puro) |
| `#4C596F` | 51 px | texto secundario |

## Lo que NO se vio

- Radios, interlineados, tamaños de fuente y tokens de espaciado exactos: requieren inspección.
- Estados de error, vacío, carga u offline: las seis pantallas están en su camino feliz.
- Tema oscuro: el archivo sólo muestra pantallas en claro.
- Los 18 destinos que pide B3-02: aquí hay 6 pantallas, no 18.

En `docs/ARCHITECTURE.md` «Sistema visual» queda marcado qué tokens son del diseño y cuáles son
derivación propia por falta de acceso a Dev Mode.
