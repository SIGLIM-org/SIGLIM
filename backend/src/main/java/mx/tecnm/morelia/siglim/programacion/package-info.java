/**
 * M-07 Programación manual.
 *
 * <p>Jornadas, partidos programados, conflictos, localía y aprobación de la jornada. Implementado en
 * H-12.1: registro de jornadas y partidos en borrador, con ajuste motivado; en H-12.2: validación
 * explicada de los conflictos duros VAL-003 a VAL-006 con el bloque de cancha de los parámetros; y
 * en H-12.3: verificación de todos los partidos sin guardar, con las mismas reglas; y en H-13.1: envío a
 * aprobación y dictamen del Presidente con separación de funciones (RF-121); y en B-15.1: las
 * advertencias de localía (RF-120, VAL-027) que se aceptan con motivo. La consulta de la ocupación de
 * canchas vive en {@code canchas} (H-07.1).
 *
 * <p>Programación automática (DEC-173): en H-32.1, el generador del calendario por el método del
 * círculo ({@code GeneradorCalendario}) y los días de cada fecha ({@code DiasDeFecha}), como dominio
 * puro sin Spring ni base de datos; en H-35.1, la vista por fecha y su verificación, envío y dictamen
 * completos ({@code FechaService}, DEC-175) y el alta manual de un emparejamiento pendiente (DEC-180);
 * en H-32.2, la generación y consulta del calendario de la temporada ({@code CalendarioService}) con
 * reemplazo lógico al regenerar (DEC-189), las fechas de programación que le corresponden y el cambio
 * de sus días (DEC-193). Las jornadas de la fecha las crea la asignación de H-33 (DEC-188).
 *
 * <p>Nivel 5 de dependencias (ADR-0006): solo puede usar los paquetes de los niveles 0 a 4. Si
 * necesita información de un módulo de nivel superior, lo hace con una interfaz definida en este
 * paquete o con un evento de dominio en {@code eventos}.
 */
package mx.tecnm.morelia.siglim.programacion;
