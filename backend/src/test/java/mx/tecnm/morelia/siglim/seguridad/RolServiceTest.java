package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaParIncompatible;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaPermiso;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaRol;
import mx.tecnm.morelia.siglim.seguridad.SolicitudCambioPermisosRepository.FilaSolicitud;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link RolService} (RF-095, RF-096, VAL-022, VAL-023). Sin Spring ni base de datos. */
class RolServiceTest {

    private static final String CONSULTAR = "seguridad.rol:CONSULTAR";
    private static final String EDITAR = "seguridad.rol:EDITAR";
    private static final String APROBAR = "seguridad.permisos:APROBAR_INCOMPATIBLE";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ROL = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ADMINISTRADOR = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PRESIDENTE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CLUB = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID SOLICITUD = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID OTRA_SOLICITUD = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final OffsetDateTime FECHA = OffsetDateTime.parse("2031-02-01T10:00:00-06:00");
    private static final String MOTIVO = "Ajuste de la matriz aprobado por la Liga";

    private static final FilaPermiso CONSULTAR_ROL = permiso(1, "seguridad.rol:CONSULTAR", "M-01");
    private static final FilaPermiso EDITAR_ROL = permiso(2, "seguridad.rol:EDITAR", "M-01");
    private static final FilaPermiso APROBAR_INCOMPATIBLE =
            permiso(3, "seguridad.permisos:APROBAR_INCOMPATIBLE", "M-01");

    private final RolRepository roles = mock(RolRepository.class);
    private final SolicitudCambioPermisosRepository solicitudes = mock(SolicitudCambioPermisosRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final RolService servicio = new RolService(roles, solicitudes, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        iniciarSesionComo(ADMINISTRADOR);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Edición de la matriz (RF-095, VAL-022) ──────────────────────────────────────────────────

    @Test
    @DisplayName("RF-095: un cambio sin pares incompatibles se aplica y se audita con su motivo")
    void aplicaCambioSinParesIncompatibles() {
        nuevoRolConPermisos(List.of(CONSULTAR_ROL, EDITAR_ROL),
                List.of(CONSULTAR_ROL, APROBAR_INCOMPATIBLE));
        given(roles.listarPermisosPorCodigos(anyCollection()))
                .willReturn(List.of(CONSULTAR_ROL, APROBAR_INCOMPATIBLE));
        given(roles.listarPermisos()).willReturn(List.of(CONSULTAR_ROL, EDITAR_ROL, APROBAR_INCOMPATIBLE));
        given(roles.listarParesIncompatibles()).willReturn(List.of());

        ResultadoCambioPermisos resultado = servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of("seguridad.rol:CONSULTAR",
                        "seguridad.permisos:APROBAR_INCOMPATIBLE"), MOTIVO));

        assertThat(resultado.pendiente()).isFalse();
        assertThat(resultado.rol().permisos()).extracting(PermisoDto::codigo)
                .containsExactly("seguridad.rol:CONSULTAR", "seguridad.permisos:APROBAR_INCOMPATIBLE");
        verify(roles).aplicarCambios(ROL, List.of(APROBAR_INCOMPATIBLE.id()), List.of(EDITAR_ROL.id()));
        verifyNoInteractions(solicitudes);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CAMBIAR_PERMISOS_ROL");
        assertThat(evento.entidad()).isEqualTo("roles");
        assertThat(evento.entidadId()).isEqualTo(ROL.toString());
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).containsEntry("rol", "PROGRAMADOR");
        assertThat(evento.detalle().toString())
                .contains("seguridad.permisos:APROBAR_INCOMPATIBLE", "seguridad.rol:EDITAR");
    }

    @Test
    @DisplayName("H-20 criterio 2: un par incompatible responde 202 y deja la solicitud pendiente")
    void parIncompatibleQuedaPendiente() {
        nuevoRolConPermisos(List.of(CONSULTAR_ROL), List.of(CONSULTAR_ROL));
        given(roles.listarPermisos()).willReturn(List.of(CONSULTAR_ROL, EDITAR_ROL, APROBAR_INCOMPATIBLE));
        given(roles.listarParesIncompatibles()).willReturn(List.of(new FilaParIncompatible(
                "seguridad.rol:EDITAR", "seguridad.permisos:APROBAR_INCOMPATIBLE",
                "Modificar permisos y aprobar pares incompatibles (RN-053)")));
        // El permiso editado se agrega al rol, pero la solicitud lo deja sin aplicar.
        given(roles.listarPermisosPorCodigos(anyCollection()))
                .willReturn(List.of(EDITAR_ROL, APROBAR_INCOMPATIBLE));
        given(solicitudes.insertar(eq(ROL), anyCollection(), anyCollection(), eq(MOTIVO),
                eq(ADMINISTRADOR))).willReturn(SOLICITUD);
        given(solicitudes.buscar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudPendiente()));

        ResultadoCambioPermisos resultado = servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of("seguridad.rol:EDITAR",
                        "seguridad.permisos:APROBAR_INCOMPATIBLE"), MOTIVO));

        assertThat(resultado.pendiente()).isTrue();
        assertThat(resultado.solicitud().id()).isEqualTo(SOLICITUD);
        assertThat(resultado.solicitud().estado()).isEqualTo(EstadoSolicitudPermisos.SOLICITADO);
        assertThat(resultado.solicitud().permisosAgregados())
                .containsExactly("seguridad.permisos:APROBAR_INCOMPATIBLE", "seguridad.rol:EDITAR");
        verify(roles, never()).aplicarCambios(any(), anyCollection(), anyCollection());
        verify(solicitudes).insertar(ROL, List.of(EDITAR_ROL.id(), APROBAR_INCOMPATIBLE.id()),
                List.of(CONSULTAR_ROL.id()), MOTIVO, ADMINISTRADOR);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("SOLICITAR_CAMBIO_PERMISOS");
        assertThat(evento.entidad()).isEqualTo("solicitudes_cambio_permisos");
        assertThat(evento.entidadId()).isEqualTo(SOLICITUD.toString());
        assertThat(evento.detalle()).containsEntry("rol", "PROGRAMADOR");
    }

    @Test
    @DisplayName("VAL-023: un par que ya estaba en el rol no vuelve a bloquear la edición")
    void parYaAprobadoNoBloquea() {
        nuevoRolConPermisos(List.of(EDITAR_ROL, APROBAR_INCOMPATIBLE),
                List.of(CONSULTAR_ROL, EDITAR_ROL, APROBAR_INCOMPATIBLE));
        given(roles.listarPermisosPorCodigos(anyCollection()))
                .willReturn(List.of(EDITAR_ROL, APROBAR_INCOMPATIBLE, CONSULTAR_ROL));
        given(roles.listarPermisos())
                .willReturn(List.of(CONSULTAR_ROL, EDITAR_ROL, APROBAR_INCOMPATIBLE));
        given(roles.listarParesIncompatibles()).willReturn(List.of(new FilaParIncompatible(
                "seguridad.rol:EDITAR", "seguridad.permisos:APROBAR_INCOMPATIBLE", "RN-053")));

        ResultadoCambioPermisos resultado = servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of("seguridad.rol:EDITAR",
                        "seguridad.permisos:APROBAR_INCOMPATIBLE", "seguridad.rol:CONSULTAR"), MOTIVO));

        assertThat(resultado.pendiente()).isFalse();
        verify(roles).aplicarCambios(ROL, List.of(CONSULTAR_ROL.id()), List.of());
        verifyNoInteractions(solicitudes);
    }

    @Test
    @DisplayName("H-20 criterio 4: el Administrador no edita su propio rol (403 VAL-022), sin escribir")
    void rolPropioRespondeVal022() {
        given(roles.buscarParaActualizar(LIGA, ROL)).willReturn(Optional.of(rol("PROGRAMADOR")));
        given(roles.tieneAsignacionActiva(ADMINISTRADOR, ROL)).willReturn(true);

        assertThatThrownBy(() -> servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of("seguridad.rol:CONSULTAR"), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-022"));
        verify(roles, never()).aplicarCambios(any(), anyCollection(), anyCollection());
        verify(solicitudes, never()).insertar(any(), anyCollection(), anyCollection(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso de editar no se lee ni se escribe la matriz")
    void sinPermisoDeEdicion() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of("seguridad.rol:CONSULTAR"), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(roles, solicitudes, auditoria);
    }

    @Test
    @DisplayName("RN-021: un cambio sin motivo suficiente responde 400 y no toca la matriz")
    void sinMotivo() {
        given(roles.buscarParaActualizar(LIGA, ROL)).willReturn(Optional.of(rol("PROGRAMADOR")));
        given(roles.tieneAsignacionActiva(ADMINISTRADOR, ROL)).willReturn(false);

        assertThatThrownBy(() -> servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of(), "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(roles, never()).listarPermisosPorCodigos(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VALIDACION: un permiso fuera del catálogo responde 400 y no se aplica")
    void permisoDesconocido() {
        given(roles.buscarParaActualizar(LIGA, ROL)).willReturn(Optional.of(rol("PROGRAMADOR")));
        given(roles.tieneAsignacionActiva(ADMINISTRADOR, ROL)).willReturn(false);
        given(roles.listarPermisosPorCodigos(anyCollection())).willReturn(List.of(CONSULTAR_ROL));

        assertThatThrownBy(() -> servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of("seguridad.rol:CONSULTAR",
                        "seguridad.rol:BORRAR_TODO"), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION))
                .hasMessageContaining("seguridad.rol:BORRAR_TODO");
        verify(roles, never()).aplicarCambios(any(), anyCollection(), anyCollection());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-095: repetir el mismo conjunto no escribe ni audita")
    void sinCambios() {
        nuevoRolConPermisos(List.of(CONSULTAR_ROL), List.of(CONSULTAR_ROL));
        given(roles.listarPermisosPorCodigos(anyCollection())).willReturn(List.of(CONSULTAR_ROL));

        ResultadoCambioPermisos resultado = servicio.actualizarPermisos(ROL,
                new ActualizarPermisosRolRequest(List.of("seguridad.rol:CONSULTAR"), MOTIVO));

        assertThat(resultado.pendiente()).isFalse();
        assertThat(resultado.rol().permisos()).extracting(PermisoDto::codigo)
                .containsExactly("seguridad.rol:CONSULTAR");
        verify(roles, never()).aplicarCambios(any(), anyCollection(), anyCollection());
        verifyNoInteractions(solicitudes, auditoria);
    }

    // ── Dictamen de la solicitud (RF-096) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("H-20 criterio 3: el Presidente aprueba y el cambio pendiente se aplica")
    void apruebaSolicitud() {
        given(solicitudes.buscarParaActualizar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudPendiente()));
        given(roles.buscarParaActualizar(LIGA, ROL)).willReturn(Optional.of(rol("PROGRAMADOR")));
        given(solicitudes.buscar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudResuelta(
                EstadoSolicitudPermisos.APROBADO, PRESIDENTE, MOTIVO)));
        iniciarSesionComo(PRESIDENTE);

        SolicitudCambioPermisosDto resultado = servicio.resolverSolicitud(SOLICITUD,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.APROBADO, MOTIVO));

        assertThat(resultado.estado()).isEqualTo(EstadoSolicitudPermisos.APROBADO);
        verify(autorizacion).exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        verify(roles).aplicarCambios(ROL, List.of(EDITAR_ROL.id()), List.of(CONSULTAR_ROL.id()));
        verify(solicitudes).resolver(SOLICITUD, EstadoSolicitudPermisos.APROBADO, PRESIDENTE, MOTIVO);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("APROBAR_CAMBIO_PERMISOS");
        assertThat(evento.entidad()).isEqualTo("solicitudes_cambio_permisos");
        assertThat(evento.entidadId()).isEqualTo(SOLICITUD.toString());
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).containsEntry("rol", "PROGRAMADOR");
    }

    @Test
    @DisplayName("H-20 criterio 3: el Presidente rechaza y el cambio no se aplica")
    void rechazaSolicitud() {
        given(solicitudes.buscarParaActualizar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudPendiente()));
        given(solicitudes.buscar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudResuelta(
                EstadoSolicitudPermisos.RECHAZADO, PRESIDENTE, MOTIVO)));
        iniciarSesionComo(PRESIDENTE);

        SolicitudCambioPermisosDto resultado = servicio.resolverSolicitud(SOLICITUD,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.RECHAZADO, MOTIVO));

        assertThat(resultado.estado()).isEqualTo(EstadoSolicitudPermisos.RECHAZADO);
        verify(roles, never()).aplicarCambios(any(), anyCollection(), anyCollection());
        verify(solicitudes).resolver(SOLICITUD, EstadoSolicitudPermisos.RECHAZADO, PRESIDENTE, MOTIVO);
        assertThat(eventoAuditado().accion()).isEqualTo("RECHAZAR_CAMBIO_PERMISOS");
    }

    @Test
    @DisplayName("DEC-04: quien solicitó el cambio no puede aprobarlo (403 RF-096), sin aplicar")
    void quienSolicitaNoAprueba() {
        given(solicitudes.buscarParaActualizar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudPendiente()));
        iniciarSesionComo(ADMINISTRADOR);

        assertThatThrownBy(() -> servicio.resolverSolicitud(SOLICITUD,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.APROBADO, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "RF-096"));
        verify(roles, never()).buscarParaActualizar(any(), any());
        verify(solicitudes, never()).resolver(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-096: una solicitud ya resuelta no se reabre y responde 409")
    void solicitudYaResuelta() {
        given(solicitudes.buscarParaActualizar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudResuelta(
                EstadoSolicitudPermisos.APROBADO, PRESIDENTE, MOTIVO)));
        iniciarSesionComo(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolverSolicitud(SOLICITUD,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.RECHAZADO, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, "RF-096"));
        verify(solicitudes, never()).resolver(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso de aprobar pares incompatibles no se resuelve")
    void sinPermisoDeAprobar() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        iniciarSesionComo(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolverSolicitud(SOLICITUD,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.APROBADO, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(solicitudes, roles, auditoria);
    }

    @Test
    @DisplayName("RN-021: el dictamen exige un motivo de al menos 10 caracteres")
    void dictamenSinMotivo() {
        given(solicitudes.buscarParaActualizar(LIGA, SOLICITUD)).willReturn(Optional.of(solicitudPendiente()));
        iniciarSesionComo(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolverSolicitud(SOLICITUD,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.RECHAZADO, "no")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(solicitudes, never()).resolver(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-024: una solicitud de otra liga responde 404 como inexistente")
    void solicitudInexistente() {
        given(solicitudes.buscarParaActualizar(LIGA, SOLICITUD)).willReturn(Optional.empty());
        iniciarSesionComo(PRESIDENTE);

        assertThatThrownBy(() -> servicio.resolverSolicitud(SOLICITUD,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.APROBADO, MOTIVO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(roles, never()).buscarParaActualizar(any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Listado de solicitudes (RF-096) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-096: el listado pagina, filtra por estado y rol y cuenta con el ámbito")
    void listarSolicitudes() {
        given(autorizacion.alcance(APROBAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));
        given(solicitudes.listar(LIGA, EstadoSolicitudPermisos.SOLICITADO, ROL, 10, 20L))
                .willReturn(List.of(solicitudPendiente()));
        given(solicitudes.contar(LIGA, EstadoSolicitudPermisos.SOLICITADO, ROL)).willReturn(11L);

        var pagina = servicio.listarSolicitudes(EstadoSolicitudPermisos.SOLICITADO, ROL, 2, 10);

        assertThat(pagina.contenido()).singleElement()
                .satisfies(solicitud -> assertThat(solicitud.estado())
                        .isEqualTo(EstadoSolicitudPermisos.SOLICITADO));
        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.totalElementos()).isEqualTo(11);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("VAL-024: con ámbito de club el listado de solicitudes responde 404")
    void listarSolicitudesFueraDeAmbito() {
        given(autorizacion.alcance(APROBAR))
                .willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));

        assertThatThrownBy(() -> servicio.listarSolicitudes(null, null, 0, 20))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(solicitudes);
    }

    @Test
    @DisplayName("VAL-015: el listado sin el permiso de aprobar responde 403")
    void listarSolicitudesSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).alcance(APROBAR);

        assertThatThrownBy(() -> servicio.listarSolicitudes(null, null, 0, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(solicitudes);
    }

    @Test
    @DisplayName("VALIDACION: una página o un tamaño inválidos responden 400")
    void listarSolicitudesPaginacionInvalida() {
        given(autorizacion.alcance(APROBAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));

        assertThatThrownBy(() -> servicio.listarSolicitudes(null, null, -1, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        assertThatThrownBy(() -> servicio.listarSolicitudes(null, null, 0, 0))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        assertThatThrownBy(() -> servicio.listarSolicitudes(null, null, 0, 101))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(solicitudes);
    }

    @Test
    @DisplayName("VAL-015: el catálogo de permisos exige seguridad.rol:CONSULTAR")
    void catalogoExigePermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(servicio::catalogoPermisos)
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(roles);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private void nuevoRolConPermisos(Collection<FilaPermiso> actuales, Collection<FilaPermiso> despues) {
        given(roles.buscarParaActualizar(LIGA, ROL)).willReturn(Optional.of(rol("PROGRAMADOR")));
        given(roles.tieneAsignacionActiva(ADMINISTRADOR, ROL)).willReturn(false);
        given(roles.listarPermisosDeRol(ROL)).willReturn(List.copyOf(actuales), List.copyOf(despues));
    }

    private static FilaRol rol(String codigo) {
        return new FilaRol(ROL, codigo, "Programador de partidos", "Programa las jornadas",
                "LIGA", true);
    }

    private static FilaPermiso permiso(long id, String codigo, String modulo) {
        return new FilaPermiso(id, codigo, modulo, "Permiso de prueba");
    }

    private static FilaSolicitud solicitudPendiente() {
        return new FilaSolicitud(SOLICITUD, ROL, "PROGRAMADOR", "Programador de partidos",
                "Programa las jornadas", "LIGA", true,
                List.of(EDITAR_ROL.id()), List.of(CONSULTAR_ROL.id()),
                List.of("seguridad.permisos:APROBAR_INCOMPATIBLE", "seguridad.rol:EDITAR"),
                List.of("seguridad.rol:CONSULTAR"),
                MOTIVO, EstadoSolicitudPermisos.SOLICITADO, ADMINISTRADOR, "Ana López", FECHA,
                null, null, null, null);
    }

    private static FilaSolicitud solicitudResuelta(EstadoSolicitudPermisos estado, UUID resueltoPor,
            String observaciones) {
        return new FilaSolicitud(SOLICITUD, ROL, "PROGRAMADOR", "Programador de partidos",
                "Programa las jornadas", "LIGA", true,
                List.of(EDITAR_ROL.id()), List.of(CONSULTAR_ROL.id()),
                List.of("seguridad.permisos:APROBAR_INCOMPATIBLE", "seguridad.rol:EDITAR"),
                List.of("seguridad.rol:CONSULTAR"),
                MOTIVO, estado, ADMINISTRADOR, "Ana López", FECHA,
                resueltoPor, "Luis Pérez", FECHA.plusDays(1), observaciones);
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static void iniciarSesionComo(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}
