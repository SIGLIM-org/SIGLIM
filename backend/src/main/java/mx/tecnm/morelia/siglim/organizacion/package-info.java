/**
 * M-03 Organización deportiva.
 *
 * <p>Clubes, afiliación por temporada, directiva, equipos, venta de equipo y plaza, personas y
 * tutores.
 *
 * <p>Implementado hasta ahora (H-06.1, H-08.1, B-04, H-22):
 *
 * <ul>
 *   <li><b>Afiliación (RF-101).</b> {@code AfiliacionController},
 *       {@link mx.tecnm.morelia.siglim.organizacion.AfiliacionService} y
 *       {@code AfiliacionRepository}: tarjetón del club por temporada con folio, categorías,
 *       edición mientras está {@code SOLICITADA} y resolución del Presidente con observaciones.
 *       El folio lo asigna el sistema y la temporada se bloquea para que el consecutivo no se
 *       repita. Las operaciones se autorizan con ámbito del club y se auditan en {@code M-03}.
 *       {@code GET /afiliaciones/contexto} entrega temporadas, categorías y grupos con los mismos
 *       permisos de afiliación, para que la Directiva no dependa de M-02 (DEC-09, CU-018, UI-023).</li>
 *   <li><b>Clubes (RF-008, RF-105).</b> {@code ClubController},
 *       {@link mx.tecnm.morelia.siglim.organizacion.ClubService} y {@code ClubRepository}: listado,
 *       detalle, alta y cambio de nombre. Sin edición: el cambio de nombre crea un club nuevo y cierra
 *       el anterior, solo entre temporadas y sin cobros pendientes; las asignaciones de rol del club
 *       se trasladan con {@code seguridad.TrasladoAsignacionesClub} y los adeudos se consultan con
 *       {@link mx.tecnm.morelia.siglim.organizacion.ConsultaAdeudosClub}, que implementa
 *       {@code tesoreria} (RN-041, DEC-15, DEC-49, DEC-61).</li>
 *   <li><b>Equipos (RF-009).</b> {@code EquipoController},
 *       {@link mx.tecnm.morelia.siglim.organizacion.EquipoService} y {@code EquipoRepository}:
 *       listado, detalle, alta, edición de nombre y grupo, y baja con motivo. El repositorio lee el
 *       grupo y el estado de su temporada solo para validar; el CRUD de grupos es de M-02.</li>
 *   <li><b>Directiva (RF-102).</b> {@code CargoDirectivaController},
 *       {@link mx.tecnm.morelia.siglim.organizacion.CargoDirectivaService} y
 *       {@code CargoDirectivaRepository}: consulta del historial, registro de un cambio pendiente y
 *       autorización que termina la vigencia anterior. {@link mx.tecnm.morelia.siglim.organizacion.PersonaController}
 *       y {@link mx.tecnm.morelia.siglim.organizacion.PersonaService} consultan la identidad por CURP
 *       para que F-15 envíe el {@code personaId} (RF-010, RF-011).</li>
 * </ul>
 *
 * <p>Todas las operaciones se autorizan con el ámbito del club (ADR-0007).
 *
 * <p>Nivel 3 de dependencias (ADR-0006): solo puede usar los paquetes de los niveles 0 a 2. Si
 * necesita información de un módulo de nivel superior, lo hace con una interfaz definida en este
 * paquete o con un evento de dominio en {@code eventos}.
 */
package mx.tecnm.morelia.siglim.organizacion;
