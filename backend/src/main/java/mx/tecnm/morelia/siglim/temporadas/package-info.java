/**
 * M-02 Temporadas y normativa.
 *
 * <p>Temporadas, convocatoria, categorías y su jerarquía, grupos y parámetros deportivos
 * versionados. Los días de juego de cada categoría de la temporada (DEC-177) se editan aquí con el
 * permiso de programación {@code programacion.jornada:EDITAR} (H-32.2, DEC-191): es solo un texto de
 * permiso, no una dependencia del paquete {@code programacion}.
 *
 * <p>Nivel 2 de dependencias (ADR-0006): solo puede usar los paquetes de los niveles 0 y 1. Si
 * necesita información de un módulo de nivel superior, lo hace con una interfaz definida en este
 * paquete o con un evento de dominio en {@code eventos}.
 */
package mx.tecnm.morelia.siglim.temporadas;
