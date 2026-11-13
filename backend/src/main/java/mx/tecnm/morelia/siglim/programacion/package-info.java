/**
 * M-07 Programación manual.
 *
 * <p>Jornadas, partidos programados, conflictos, localía y aprobación de la jornada. Implementado en
 * H-12.1: registro de jornadas y partidos en borrador, con ajuste motivado; y en H-12.2: validación
 * explicada de los conflictos duros VAL-003 a VAL-006 con el bloque de cancha de los parámetros. La
 * consulta de la ocupación de canchas vive en {@code canchas} (H-07.1).
 *
 * <p>Nivel 5 de dependencias (ADR-0006): solo puede usar los paquetes de los niveles 0 a 4. Si
 * necesita información de un módulo de nivel superior, lo hace con una interfaz definida en este
 * paquete o con un evento de dominio en {@code eventos}.
 */
package mx.tecnm.morelia.siglim.programacion;
