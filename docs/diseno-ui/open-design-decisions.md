# Decisiones de Diseño Pendientes y Observaciones - SIGLIM

> Actualizado al ERS Rev. 0.4.1 (20 de octubre de 2026). Las pantallas aprobadas están en
> [`pantallas/`](pantallas/README.md) y el sistema visual en [`DESIGN.md`](../../DESIGN.md).

## 1. Decisiones Observadas (Confirmadas por Referencias Visuales)
- Uso de la paleta verde esmeralda (`#006837`) como color primario institucional.
- Estructura de cabecera fija con acceso a la aplicación interna.
- Azul noche (`#131b2e`) para paneles destacados del portal, cabeceras de módulo y el marcador (DESIGN.md).

## 2. Inferencias Razonables
- Los estados de los partidos, cédulas, registros y cobros se alimentan de los enums que devuelve
  el backend (convenciones de API §9.3); la interfaz solo los traduce a texto.
- La responsividad en dispositivos móviles transforma los grids de múltiples columnas en listas
  apiladas de una sola columna (RNF-007: desde 360 px).

## 3. Puntos Pendientes de Validación por el Equipo
- **P-01**: ~~Notificaciones en tiempo real para cambios de marcador~~. Resuelto: no hay tiempo
  real; los avisos se consultan al cargar y cada 60 s **[PROPUESTA]** (convenciones de API §6.14).
- **P-02**: ~~Biblioteca de iconos~~. Resuelto: aprobado por el equipo el 22 de octubre de 2026.
  El HTML de Stitch usa Material Symbols; en React se usa **Lucide React** (`lucide-react`) con el
  ícono equivalente de cada pantalla. Se eligió porque importa cada ícono por separado (el build
  solo incluye los usados), trae tipos de TypeScript y no depende de una fuente externa. Uso en
  [`frontend/AGENTS.md`](../../frontend/AGENTS.md).
- **P-03**: ~~Aprobar el prototipo generado con Stitch~~. Aprobado por el equipo el 20 de octubre de 2026 ([`pantallas/`](pantallas/README.md)); falta validarlo con la Liga (PEN-014). Las diferencias (D-xx, §3) y las preguntas Q-UI-01 a Q-UI-04 (tres resueltas; Q-UI-02 depende de PEN-030) están en [`pantallas/README.md`](pantallas/README.md).

## 4. Conflictos detectados entre el diseño de referencia y el ERS

Las capturas de referencia (Google Stitch, primera ronda) muestran elementos que el ERS deja fuera
del alcance o pendientes. **La implementación sigue al ERS.**

| ID | Elemento del diseño | Qué dice el ERS Rev. 0.4.1 | Qué se implementa | Estado |
| --- | --- | --- | --- | --- |
| C-01 | Marca "LIGA APEX" | El sistema es **SIGLIM**; la Liga es la Liga Municipal de Futbol Amateur Morelia | Logotipo tipográfico «SIGLIM» con el nombre de la liga | Vigente |
| C-02 | Buscador global en el header | RF-089: filtros por fecha, categoría/grupo y equipo; sin buscador general | Barra de filtros; búsqueda de texto solo dentro de un listado | Vigente |
| C-03 | Botón "Portal Delegados" | **Reabierto y resuelto.** La Directiva de club (presidente, secretario, tesorero y delegado) ya es un tipo de usuario (§2.3, DEC-02, DEC-03), pero entra por el mismo inicio de sesión que los demás | Botón «Iniciar sesión» (UI-001); cada usuario ve su panel según permisos y ámbito (F-07, F-08) | Resuelto |
| C-04 | Tarjetas "Máximo goleador" / "Mejor portero" | **Reabierto y resuelto.** Las estadísticas de jugador ya están en el ERS, pero como backlog (RF-054, UI-013) y en el portal solo con consentimiento de difusión (RF-145) | Núcleo: estadísticas de equipo de la tabla (RF-053). Backlog: frame B-01 con «Jugador (dato reservado)» sin consentimiento | Resuelto |
| C-05 | Enlaces a Reglamento, Comité de Competición, Prensa y Medios | Comunicados, calendario, sanciones y equipos en el portal son backlog (RF-085 a RF-088) | Pie con nombre de la liga y «Aviso de privacidad» (RF-097); secciones del portal ampliado en B-10 | Vigente |
| C-06 | Marcador "En vivo" en tiempo real | Sin requisito de tiempo real | Estado del partido al consultar | Vigente |
| C-07 | Selector de temporada y de división | Una temporada activa por liga (RF-001); categorías y grupos reales, no "divisiones" | Texto «Temporada … · Activa» en la barra superior; filtros de categoría y grupo; temporadas cerradas en UI-003 | Vigente |
| C-08 | Resultados de partidos "Finalizados" en el portal | Solo resultados de cédulas aprobadas (RF-056, RF-125, VAL-009) | Lo que devuelve la vista `v_publica_resultados` | Vigente |
