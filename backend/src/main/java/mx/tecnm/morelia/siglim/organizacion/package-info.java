/**
 * M-03 Organización deportiva.
 *
 * <p>Clubes, afiliación por temporada, directiva, equipos, venta de equipo y plaza, personas y
 * tutores.
 *
 * <p>Implementado hasta ahora (H-06.1):
 *
 * <ul>
 *   <li><b>Clubes (RF-008).</b> {@code ClubController}, {@link mx.tecnm.morelia.siglim.organizacion.ClubService}
 *       y {@code ClubRepository}: listado, detalle y alta. Sin edición: el cambio de nombre es B-04
 *       (RN-041, DEC-49).</li>
 *   <li><b>Equipos (RF-009).</b> {@code EquipoController},
 *       {@link mx.tecnm.morelia.siglim.organizacion.EquipoService} y {@code EquipoRepository}:
 *       listado, detalle, alta, edición de nombre y grupo, y baja con motivo. El repositorio lee el
 *       grupo y el estado de su temporada solo para validar; el CRUD de grupos es de M-02.</li>
 * </ul>
 *
 * <p>Todas las operaciones se autorizan con el ámbito del club (ADR-0007).
 *
 * <p>Nivel 3 de dependencias (ADR-0006): solo puede usar los paquetes de los niveles 0 a 2. Si
 * necesita información de un módulo de nivel superior, lo hace con una interfaz definida en este
 * paquete o con un evento de dominio en {@code eventos}.
 */
package mx.tecnm.morelia.siglim.organizacion;
