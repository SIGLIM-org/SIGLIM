# ADR-0015: Generar la programación con el método del círculo y una heurística voraz validada

- **Estado:** Aceptada
- **Fecha:** 2026-11-24
- **Responsables:** Equipo SIGLIM
- **Trazabilidad:** RF-032, RF-034, RF-035, RF-036, RF-037, RF-039, RF-041, RF-042, RN-012, RN-016, RN-017, RN-020, VAL-003 a VAL-007, VAL-019, VAL-027, CA-002, DD-005, PEN-027, PEN-028, DEC-173 a DEC-186

## Contexto

Hasta el Sprint 3 la programación era manual (DEC-06): el Programador capturaba cada partido y
el sistema rechazaba los conflictos con `ValidadorConflictos` (H-12). Con clubes, equipos y grupos
ya registrados, armar cada fecha a mano es la tarea más repetitiva del sistema. El 24 de noviembre
de 2026 el PO aprobó reabrir la programación automática (DEC-173). El ERS base deja el algoritmo a
justificación del equipo (PEN-028).

Fuerzas:

- Las reglas duras ya existen y están probadas: VAL-003 (equipo ocupado), VAL-004 (cancha
  ocupada), VAL-005 (cancha inactiva, bloqueada o en mantenimiento), VAL-006 (equipo dado de baja
  o sancionado), la restricción GiST y el trigger `tg_partidos_programacion`.
- Las canchas las comparten todas las categorías: asignar grupo por grupo deja sin lugar a los
  últimos.
- El tamaño del problema es pequeño: una liga municipal con unos cien partidos por fecha y menos de
  diez canchas.
- El plazo es de días, no de semanas, y el resultado debe poder explicarse en el informe.
- El Programador revisa y ajusta el resultado; el sistema propone, no publica.

## Decisión

1. **Calendario por el método del círculo (tablas de Berger).** Por cada grupo con equipos
   `ACTIVO`, ordenados por el texto de su `id` (DEC-185), se arma el arreglo `a`: con N impar,
   `[DESCANSO, e0, …, eN−1]` (el descanso es el elemento fijo); con N par, `[e0, …, eN−1]`. Con
   `M = |a|`, en la ronda `r` la mesa `i` empareja `a[i]` con `a[M−1−i]` y después `a[1..M−1]` rota
   una posición a la derecha. Localía (DEC-184): en la mesa 0, `a[0]` es local en las rondas pares y
   visitante en las impares; en la mesa `i ≥ 1`, `a[i]` es local si `i` es impar y `a[M−1−i]` si es
   par. Así la diferencia entre partidos de local y de visitante de cada equipo es como máximo 1 por
   vuelta. Cada vuelta par repite las rondas con la localía invertida. El número de vueltas es
   un parámetro versionado de la temporada (DEC-174). El calendario se guarda una vez por
   temporada en `emparejamientos`.
2. **Cancha y horario con una heurística voraz «más restringido primero».** Por fecha, para todas
   las categorías y grupos a la vez: se construyen ventanas por día y cancha (horario de juego de
   los parámetros o los periodos `DISPONIBLE`, menos bloqueos y mantenimiento); se ordenan los
   partidos por número de días permitidos, `orden` de la categoría, grupo y emparejamiento; cada
   partido toma, día por día y en el orden de canchas de DEC-179, el **inicio más temprano** donde
   cabe el bloque de su categoría (`siglim.bloque_cancha`), probando el inicio de la ventana y el
   fin de cada partido ya colocado en esa cancha (partidos consecutivos, RF-032).
3. **Un solo validador.** El candidato elegido se confirma con el mismo `ValidadorConflictos` de
   H-12 y se evalúa con `ValidadorLocalia` antes de insertarlo. El trigger y la restricción GiST
   siguen como última garantía. Lo que no cabe queda como emparejamiento sin partido, con código y
   causa (VAL-007, RF-042). Todo ocurre en una transacción y es determinista.
4. **Árbitro central con asignación voraz equitativa.** Por partido sin central vigente, ordenado
   por inicio, se elige un árbitro `VALIDADO` que no sea del club de ningún equipo (VAL-019) y sin
   partido traslapado, con la menor carga de designaciones vigentes en la temporada; el desempate
   es determinista. `tg_designaciones_validar` es la garantía final. Sin candidato, el partido
   queda «Sin árbitro» como advertencia.

## Alternativas consideradas

- **Programación con restricciones (CP-SAT u OR-Tools):** encuentra soluciones óptimas y maneja
  bien las restricciones blandas, pero agrega una dependencia nativa, una curva de aprendizaje que
  no cabe en el plazo y un modelo distinto al validador existente.
- **Timefold u OptaPlanner (Java):** se integra con Spring, pero exige modelar el dominio como
  problema de planificación, duplicar las reglas en restricciones propias y ajustar el tiempo de
  búsqueda; excesivo para el tamaño del problema.
- **Programación entera mixta:** formulación limpia, pero requiere un *solver* externo y explicar
  la inviabilidad es más difícil que con una lista de causas por partido.
- **Búsqueda con retroceso completa:** garantiza encontrar una solución si existe, pero su tiempo
  crece de forma exponencial; se deja como mejora si la heurística deja muchos partidos sin asignar.
- **Generar emparejamientos al azar:** más simple, pero no garantiza que cada par se enfrente las
  veces configuradas ni el equilibrio de descansos y localía.

## Consecuencias

- **Positivas:** el algoritmo es O(n²) para el calendario y O(P · C · M) para la asignación
  (partidos, canchas y partidos por cancha); con datos de la liga responde en milisegundos. Las
  reglas duras siguen en un solo lugar, así que la generación automática y la captura manual no
  pueden discrepar. Con los mismos datos se obtiene la misma programación, lo que facilita probar.
- **Negativas:** la heurística no es óptima: en fechas muy cargadas puede dejar partidos sin
  asignar que una búsqueda completa sí colocaría. Se mitiga mostrando la causa y el resumen de
  franjas para que el Programador ajuste a mano.
- **Pendiente:** las restricciones blandas de preferencias de los clubes y de desplazamientos no se
  modelan (no hay datos); la disponibilidad arbitral (RF-022) tampoco. Los valores de horario de
  juego y días por categoría son [DEMO] hasta que la Liga los confirme.
- **Pruebas:** propiedades del calendario (cada par v veces, nadie dos veces por fecha, descansos y
  localía equilibrados, determinismo), exhaustivas con `@ParameterizedTest` sobre N = 2…30 y 1…4
  vueltas, sin jqwik (DEC-183), y una prueba de integración multicategoría con canchas
  compartidas en la que la verificación no encuentra conflictos duros (CA-002).

Detalle de diseño, modelo de datos y API: [`docs/planning/programacion-automatica.md`](../../planning/programacion-automatica.md).

## Corrección (H-32.1, DEC-184)

La primera versión de la Decisión 1 decía que «el equipo fijo es local en las rondas pares y los
demás pares invierten la localía en las impares». Esa regla no cumple la diferencia ≤ 1 por vuelta.
Contraejemplo con N = 4: las rondas quedan `e0–e3, e1–e2`; `e2–e0, e1–e3`; `e0–e1, e2–e3` (local–
visitante) y `e3` juega sus 3 partidos de visitante. Con la regla corregida, la localía de un equipo
que rota depende solo de la paridad de la posición que ocupa (local en las impares de las mesas
`i ≥ 1`) y la mesa 0 alterna por ronda; como cada equipo pasa por todas las posiciones una vez por
vuelta, la diferencia es como máximo 1. Además, con N impar el DESCANSO pasa a ser el elemento fijo
del círculo, así que cada equipo descansa exactamente una vez por vuelta. La implementación es
`programacion.GeneradorCalendario` y la prueba de propiedades `GeneradorCalendarioTest`.
