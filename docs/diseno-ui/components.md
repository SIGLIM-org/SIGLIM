# Componentes Visuales Reutilizables - SIGLIM

> [!WARNING]
> **Reemplazado** por [`DESIGN.md`](../../DESIGN.md) (prototipo aprobado el 20 de octubre de 2026). Se conserva como referencia de la primera ronda de capturas.

## 1. Botones (`Button`)
- **Variantes**: 
  - `Primary`: Fondo verde esmeralda (`#006837`), texto blanco, esquinas ligeramente redondeadas (`rounded-lg` o `rounded-md`), icono opcional a la derecha (e.g. flecha).
  - `Secondary / Outline`: Fondo transparente u opalino, borde sutil, texto oscuro o blanco según el fondo.
- **Estados**: Normal, Hover (`--color-primary-dark`), Focus (anillo de foco accesible), Disabled (opacidad 50%).

## 2. Tarjetas Deportivas (`SportsCard` / `MatchCard`)
- **Propósito**: Mostrar enfrentamientos, partidos en directo o tarjetas de jugadores clave.
- **Apariencia**: Superficie blanca o azul oscuro nocturno (`#131b2e`), bordes sutiles, padding interno de `16px` a `24px`.
- **Variantes**: Partido de la Semana (Hero card con fondo oscuro y gradiente sutil), Tarjetas de Estadísticas de Jugador (Máximo goleador, Mejor portero).

## 3. Tablas de Clasificación (`StandingsTable`)
- **Estructura**: Columnas definidas para Posición (`POS`), Club (`CLUB`), Partidos Jugados (`PJ`), Diferencia de Goles (`DG`), Puntos (`PTS`).
- **Estilo**: Encabezados en mayúsculas con texto gris muted, filas con separación limpia y resaltado especial para zonas de clasificación (franja verde lateral).

## 4. Campos de Formulario (`Input` / `Select`)
- **Apariencia**: Fondo gris claro (`#f1f5f9`), bordes sutiles (`#e2e8f0`), esquinas redondeadas, iconos descriptivos a la izquierda (ej. icono de sobre para correo, candado para contraseña).