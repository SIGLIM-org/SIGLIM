package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.PerfilDto;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionRepository.ResumenAfiliacion;
import mx.tecnm.morelia.siglim.organizacion.AfiliacionRepository.TemporadaAfiliacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas del tarjetón de afiliación de {@link AfiliacionService}. Sin Spring ni base de datos. */
class AfiliacionServiceTest {

    private static final String CAPTURAR = "organizacion.afiliacion:CAPTURAR";
    private static final String APROBAR = "organizacion.afiliacion:APROBAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CATEGORIA_A = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID CATEGORIA_B = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID OTRO_CLUB = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final Instant AHORA = Instant.parse("2030-08-02T12:00:00Z");
    private static final OffsetDateTime RESUELTA_EN = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final OffsetDateTime CAPTURADA_EN = OffsetDateTime.parse("2030-08-02T06:00:00-06:00");
    private static final String FOLIO = "AF-2030-0001";
    private static final String COLORES = "Azul y blanco";
    private static final String OBSERVACIONES = "Faltan las firmas de la directiva";

    private static final ClubDto CLUB_ACTIVO = new ClubDto(CLUB, "Club Halcones", EstadoClub.ACTIVO,
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), null, null, null);
    private static final ClubDto CLUB_CERRADO = new ClubDto(CLUB, "Club Halcones", EstadoClub.CERRADO,
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), LocalDate.parse("2030-06-01"), "Cambio de nombre",
            null);
    private static final TemporadaAfiliacion TEMPORADA_ABIERTA = new TemporadaAfiliacion(TEMPORADA,
            "Temporada 2030", EstadoTemporada.CONFIGURACION, LocalDate.parse("2030-08-01"));
    private static final TemporadaAfiliacion TEMPORADA_CERRADA = new TemporadaAfiliacion(TEMPORADA,
            "Temporada 2030", EstadoTemporada.CERRADA, LocalDate.parse("2030-08-01"));

    private final AfiliacionRepository afiliaciones = mock(AfiliacionRepository.class);
    private final ClubRepository clubes = mock(ClubRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final AfiliacionService servicio = new AfiliacionService(afiliaciones, clubes, autorizacion,
            auditoria, motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC),
            new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        // Por omisión el club del tarjetón sigue ACTIVO; las pruebas de cierre lo cambian.
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static AfiliacionDto afiliacion(EstadoAfiliacion estado, List<UUID> categorias, UUID resueltaPor,
            String observaciones) {
        List<ReferenciaDto> referencias = categorias.stream()
                .map(id -> new ReferenciaDto(id, id.equals(CATEGORIA_A) ? "Libre" : "Femenil"))
                .toList();
        boolean resuelta = estado != EstadoAfiliacion.SOLICITADA;
        return new AfiliacionDto(ID, FOLIO, new ReferenciaDto(CLUB, "Club Halcones"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2030", EstadoTemporada.CONFIGURACION),
                COLORES, estado, referencias, new PerfilDto(PERFIL, "Ana López"), CAPTURADA_EN,
                resuelta ? new PerfilDto(resueltaPor, "Presidente") : null,
                resuelta ? RESUELTA_EN : null, observaciones);
    }

    private static AfiliacionDto solicitada() {
        return afiliacion(EstadoAfiliacion.SOLICITADA, List.of(CATEGORIA_A), null, null);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private static AlcanceAutorizacion liga() {
        return new AlcanceAutorizacion(true, Set.of(), false);
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    // ── Listado y ámbito (H-08.1) ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-101: el listado usa el alcance de CAPTURAR en la consulta y en el total")
    void listarUsaAlcanceDeCaptura() {
        FiltroAfiliaciones filtro = new FiltroAfiliaciones(TEMPORADA, EstadoAfiliacion.SOLICITADA, 0, 20);
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(CLUB), false);
        given(autorizacion.alcance(CAPTURAR)).willReturn(alcance);
        given(afiliaciones.listar(LIGA, filtro, alcance)).willReturn(List.of(solicitada()));
        given(afiliaciones.contar(LIGA, filtro, alcance)).willReturn(1L);

        var pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).extracting(AfiliacionDto::id).containsExactly(ID);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        verify(afiliaciones).listar(LIGA, filtro, alcance);
        verify(afiliaciones).contar(LIGA, filtro, alcance);
    }

    @Test
    @DisplayName("RF-101: el Presidente lista aunque no tenga CAPTURAR, con el alcance de APROBAR")
    void listarPresidenteUsaAprobar() {
        FiltroAfiliaciones filtro = new FiltroAfiliaciones(null, null, 0, 20);
        given(autorizacion.alcance(CAPTURAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(APROBAR)).willReturn(liga());
        given(afiliaciones.listar(LIGA, filtro, liga())).willReturn(List.of(solicitada()));
        given(afiliaciones.contar(LIGA, filtro, liga())).willReturn(1L);

        assertThat(servicio.listar(filtro).contenido()).hasSize(1);
        verify(afiliaciones).listar(LIGA, filtro, liga());
    }

    @Test
    @DisplayName("VAL-015: sin ninguno de los dos permisos el listado responde 403")
    void listarSinPermiso() {
        given(autorizacion.alcance(CAPTURAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(APROBAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listar(new FiltroAfiliaciones(null, null, 0, 20)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }

    @Test
    @DisplayName("RF-101: una página o un tamaño fuera de rango responde 400 VALIDACION")
    void listarRechazaPaginaInvalida() {
        given(autorizacion.alcance(CAPTURAR)).willReturn(liga());

        assertThatThrownBy(() -> servicio.listar(new FiltroAfiliaciones(null, null, 0, 101)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(afiliaciones, never()).listar(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: la consulta usa el alcance de CAPTURAR sobre el club del tarjetón")
    void consultarUsaElAlcanceDelClub() {
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(autorizacion.alcance(CAPTURAR))
                .willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));
        given(autorizacion.alcance(APROBAR)).willThrow(Autorizacion.sinPermiso());

        assertThat(servicio.consultar(ID)).isEqualTo(solicitada());
        verify(autorizacion).alcance(CAPTURAR);
        verify(autorizacion, never()).exigir(any(), any());
    }

    @Test
    @DisplayName("RF-101: el Presidente consulta con APROBAR aunque no tenga CAPTURAR")
    void consultarPresidente() {
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(autorizacion.alcance(CAPTURAR)).willThrow(Autorizacion.sinPermiso());
        given(autorizacion.alcance(APROBAR)).willReturn(liga());

        assertThat(servicio.consultar(ID).folio()).isEqualTo(FOLIO);
        verify(autorizacion).alcance(APROBAR);
        verify(autorizacion, never()).exigir(any(), any());
    }

    @Test
    @DisplayName("VAL-024: un tarjetón de otro club responde 404 y no se convierte en 403")
    void consultarFueraDeAmbito() {
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(autorizacion.alcance(CAPTURAR))
                .willReturn(new AlcanceAutorizacion(false, Set.of(OTRO_CLUB), false));
        given(autorizacion.alcance(APROBAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(autorizacion).alcance(CAPTURAR);
    }

    @Test
    @DisplayName("RF-101, ADR-0007: con CAPTURAR y APROBAR de ámbitos distintos se une el alcance")
    void consultarConDosRolesDeAmbitoDistinto() {
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(autorizacion.alcance(CAPTURAR))
                .willReturn(new AlcanceAutorizacion(false, Set.of(OTRO_CLUB), false));
        given(autorizacion.alcance(APROBAR))
                .willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));

        assertThat(servicio.consultar(ID).folio()).isEqualTo(FOLIO);
        verify(autorizacion).alcance(CAPTURAR);
        verify(autorizacion).alcance(APROBAR);
    }

    @Test
    @DisplayName("RF-101: un tarjetón inexistente responde 404 antes de autorizar")
    void consultarInexistente() {
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    // ── Alta con folio (criterio 1 de H-08) ──────────────────────────────────────────────────

    @Test
    @DisplayName("H-08 criterio 1 · RF-101: la Directiva captura el tarjetón y queda SOLICITADA con folio")
    void crearAsignaFolioYAudita() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
        given(afiliaciones.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(TEMPORADA_ABIERTA));
        given(afiliaciones.buscarPorClubTemporada(LIGA, CLUB, TEMPORADA)).willReturn(Optional.empty());
        given(afiliaciones.categoriasDeTemporada(TEMPORADA)).willReturn(Set.of(CATEGORIA_A));
        given(afiliaciones.contarDeTemporada(TEMPORADA)).willReturn(0L);
        given(afiliaciones.insertar(CLUB, TEMPORADA, FOLIO, COLORES, PERFIL)).willReturn(ID);
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(solicitada()));

        AfiliacionDto creada = servicio.crear(
                new CrearAfiliacionRequest(CLUB, TEMPORADA, "  " + COLORES + "  ", List.of(CATEGORIA_A)));

        assertThat(creada.estado()).isEqualTo(EstadoAfiliacion.SOLICITADA);
        assertThat(creada.folio()).isEqualTo(FOLIO);
        assertThat(creada.categorias()).extracting(ReferenciaDto::id).containsExactly(CATEGORIA_A);
        verify(autorizacion).exigir(CAPTURAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(afiliaciones).insertarCategorias(ID, Set.of(CATEGORIA_A));

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-03");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_AFILIACION");
        assertThat(evento.entidad()).isEqualTo("afiliaciones");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
        assertThat(evento.detalle()).containsEntry("folio", FOLIO)
                .containsEntry("estado", "SOLICITADA")
                .containsEntry("clubId", CLUB.toString());
    }

    @Test
    @DisplayName("RF-101: un club cerrado no puede solicitar la afiliación (409 RN-041)")
    void crearClubCerrado() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_CERRADO));

        assertThatThrownBy(() -> servicio.crear(
                new CrearAfiliacionRequest(CLUB, TEMPORADA, COLORES, List.of(CATEGORIA_A))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
        verify(afiliaciones, never()).bloquearTemporada(any(), any());
    }

    @Test
    @DisplayName("RF-003: no se captura el tarjetón de una temporada cerrada (409)")
    void crearTemporadaCerrada() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
        given(afiliaciones.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(TEMPORADA_CERRADA));

        assertThatThrownBy(() -> servicio.crear(
                new CrearAfiliacionRequest(CLUB, TEMPORADA, COLORES, List.of(CATEGORIA_A))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(afiliaciones, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-101: una temporada que no existe responde 422 REFERENCIA_INVALIDA")
    void crearTemporadaInexistente() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
        given(afiliaciones.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(
                new CrearAfiliacionRequest(CLUB, TEMPORADA, COLORES, List.of(CATEGORIA_A))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
    }

    @Test
    @DisplayName("RF-101: el club ya afiliado en la temporada responde 409 con folio y tarjetón")
    void crearDuplicada() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
        given(afiliaciones.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(TEMPORADA_ABIERTA));
        given(afiliaciones.buscarPorClubTemporada(LIGA, CLUB, TEMPORADA))
                .willReturn(Optional.of(new ResumenAfiliacion(ID, FOLIO, EstadoAfiliacion.RECHAZADA)));

        assertThatThrownBy(() -> servicio.crear(
                new CrearAfiliacionRequest(CLUB, TEMPORADA, COLORES, List.of(CATEGORIA_A))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO).accept(excepcion);
                    assertThat(excepcion.getMessage()).contains(FOLIO).contains("rechazada");
                    assertThat(excepcion.propiedades()).containsEntry("folio", FOLIO)
                            .containsEntry("afiliacionId", ID.toString());
                });
        verify(afiliaciones, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-101: una categoría que no participa en la temporada responde 422 con el detalle")
    void crearCategoriaDeOtraTemporada() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
        given(afiliaciones.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(TEMPORADA_ABIERTA));
        given(afiliaciones.buscarPorClubTemporada(LIGA, CLUB, TEMPORADA)).willReturn(Optional.empty());
        given(afiliaciones.categoriasDeTemporada(TEMPORADA)).willReturn(Set.of(CATEGORIA_A));

        assertThatThrownBy(() -> servicio.crear(new CrearAfiliacionRequest(CLUB, TEMPORADA, COLORES,
                List.of(CATEGORIA_A, CATEGORIA_B))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA)
                            .accept(excepcion);
                    assertThat(excepcion.propiedades()).containsEntry("categoriasInvalidas",
                            List.of(CATEGORIA_B.toString()));
                });
        verify(afiliaciones, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-101: sin categorías el tarjetón no se captura (400 VALIDACION)")
    void crearSinCategorias() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
        given(afiliaciones.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(TEMPORADA_ABIERTA));
        given(afiliaciones.buscarPorClubTemporada(LIGA, CLUB, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(
                new CrearAfiliacionRequest(CLUB, TEMPORADA, COLORES, List.of())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(afiliaciones, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-015: sin CAPTURAR sobre el club no se captura el tarjetón")
    void crearSinPermiso() {
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_ACTIVO));
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(CAPTURAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.crear(
                new CrearAfiliacionRequest(CLUB, TEMPORADA, COLORES, List.of(CATEGORIA_A))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }

    // ── Edición solo en SOLICITADA ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-101: un tarjetón resuelto no se edita (409 RF-101)")
    void actualizarResuelta() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(afiliacion(EstadoAfiliacion.APROBADA, List.of(CATEGORIA_A),
                        PERFIL, null)));

        assertThatThrownBy(() -> servicio.actualizar(ID,
                new ActualizarAfiliacionRequest("Rojo", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-101"));
        verify(afiliaciones, never()).actualizarColores(any(), any());
    }

    @Test
    @DisplayName("RF-101: editar reemplaza categorías, conserva colores y audita EDITAR_AFILIACION")
    void actualizarReemplazaCategorias() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(afiliaciones.categoriasDeTemporada(TEMPORADA)).willReturn(Set.of(CATEGORIA_A, CATEGORIA_B));
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(
                afiliacion(EstadoAfiliacion.SOLICITADA, List.of(CATEGORIA_A, CATEGORIA_B), null, null)));

        AfiliacionDto actualizada = servicio.actualizar(ID,
                new ActualizarAfiliacionRequest(COLORES, List.of(CATEGORIA_A, CATEGORIA_B)));

        assertThat(actualizada.categorias()).extracting(ReferenciaDto::id)
                .containsExactly(CATEGORIA_A, CATEGORIA_B);
        verify(afiliaciones).reemplazarCategorias(ID, Set.of(CATEGORIA_A, CATEGORIA_B));
        verify(afiliaciones, never()).actualizarColores(any(), any());
        assertThat(eventoAuditado().accion()).isEqualTo("EDITAR_AFILIACION");
    }

    @Test
    @DisplayName("RF-101: editar sin cambios responde el tarjetón y no audita")
    void actualizarSinCambios() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));

        AfiliacionDto actual = servicio.actualizar(ID, new ActualizarAfiliacionRequest(COLORES, null));

        assertThat(actual).isEqualTo(solicitada());
        verifyNoInteractions(auditoria);
        verify(afiliaciones, never()).reemplazarCategorias(any(), any());
    }

    @Test
    @DisplayName("RN-041: un club cerrado no admite cambios en el tarjetón")
    void actualizarClubCerrado() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_CERRADO));

        assertThatThrownBy(() -> servicio.actualizar(ID,
                new ActualizarAfiliacionRequest("Rojo", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
        verify(afiliaciones, never()).actualizarColores(any(), any());
    }

    // ── Resolución (criterio 2 de H-08) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("H-08 criterio 2 · RF-101: el Presidente aprueba, se guardan perfil y fecha, y se audita")
    void resolverAprueba() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(
                afiliacion(EstadoAfiliacion.APROBADA, List.of(CATEGORIA_A), PERFIL, "Todo en orden")));

        AfiliacionDto resuelta = servicio.resolver(ID,
                new ResolverAfiliacionRequest(DecisionAfiliacion.APROBADA, "Todo en orden"));

        assertThat(resuelta.estado()).isEqualTo(EstadoAfiliacion.APROBADA);
        assertThat(resuelta.resueltaPor().id()).isEqualTo(PERFIL);
        assertThat(resuelta.resueltaEn()).isEqualTo(RESUELTA_EN);
        verify(autorizacion).exigir(APROBAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(afiliaciones).resolver(ID, EstadoAfiliacion.APROBADA, PERFIL, RESUELTA_EN, "Todo en orden");
        verify(motivoOperacion, never()).fijar(any());
        assertThat(eventoAuditado().accion()).isEqualTo("APROBAR_AFILIACION");
    }

    @Test
    @DisplayName("H-08 criterio 2 · RF-101: rechazar sin observaciones suficientes responde 400")
    void resolverRechazaSinObservaciones() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverAfiliacionRequest(DecisionAfiliacion.RECHAZADA, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-101"));
        verify(afiliaciones, never()).resolver(any(), any(), any(), any(), any());
        verifyNoInteractions(motivoOperacion);
    }

    @Test
    @DisplayName("H-08 criterio 2 · RF-101: el rechazo guarda observaciones, motivo y auditoría")
    void resolverRechazaConObservaciones() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(afiliaciones.buscar(LIGA, ID)).willReturn(Optional.of(afiliacion(EstadoAfiliacion.RECHAZADA,
                List.of(CATEGORIA_A), PERFIL, OBSERVACIONES)));

        AfiliacionDto resuelta = servicio.resolver(ID,
                new ResolverAfiliacionRequest(DecisionAfiliacion.RECHAZADA, "  " + OBSERVACIONES + "  "));

        assertThat(resuelta.estado()).isEqualTo(EstadoAfiliacion.RECHAZADA);
        assertThat(resuelta.observaciones()).isEqualTo(OBSERVACIONES);
        verify(motivoOperacion).fijar(OBSERVACIONES);
        verify(afiliaciones).resolver(ID, EstadoAfiliacion.RECHAZADA, PERFIL, RESUELTA_EN, OBSERVACIONES);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("RECHAZAR_AFILIACION");
        assertThat(evento.motivo()).isEqualTo(OBSERVACIONES);
    }

    @Test
    @DisplayName("RN-041: un club cerrado no permite resolver el tarjetón")
    void resolverClubCerrado() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        given(clubes.buscarParaCompartir(LIGA, CLUB)).willReturn(Optional.of(CLUB_CERRADO));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverAfiliacionRequest(DecisionAfiliacion.APROBADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
        verify(afiliaciones, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-101: las observaciones admiten hasta 500 caracteres (400 VALIDACION)")
    void resolverObservacionesDemasiadoLargas() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverAfiliacionRequest(DecisionAfiliacion.APROBADA, "a".repeat(501))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(afiliaciones, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-101: resolver un tarjetón ya resuelto responde 409 RF-101")
    void resolverRepetida() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(
                afiliacion(EstadoAfiliacion.APROBADA, List.of(CATEGORIA_A), PERFIL, null)));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverAfiliacionRequest(DecisionAfiliacion.RECHAZADA, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-101"));
        verify(afiliaciones, never()).resolver(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-015: la Directiva no puede resolver su propio tarjetón (403)")
    void resolverSinPermiso() {
        given(afiliaciones.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(solicitada()));
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(APROBAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.resolver(ID,
                new ResolverAfiliacionRequest(DecisionAfiliacion.APROBADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }
}
