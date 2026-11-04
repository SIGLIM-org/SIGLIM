package mx.tecnm.morelia.siglim.temporadas;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.TemporadaDto.RequisitoActivacionDto;
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.DatosTemporada;
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.TemporadaActiva;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link TemporadaService}. Sin Spring ni base de datos. */
class TemporadaServiceTest {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.temporada:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTRA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CLUB = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final LocalDate INICIO = LocalDate.parse("2030-08-01");
    private static final LocalDate FIN = LocalDate.parse("2030-12-15");
    private static final String MOTIVO = "Fin del calendario deportivo";

    private final TemporadaRepository temporadas = mock(TemporadaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final TemporadaService servicio = new TemporadaService(temporadas, autorizacion, auditoria,
            motivoOperacion, new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static DatosTemporada temporada(EstadoTemporada estado, Integer version, int categorias, int grupos) {
        return new DatosTemporada(ID, "Temporada 2030", INICIO, FIN, estado, CREADO_EN, categorias, grupos, version,
                null);
    }

    private static DatosTemporada configurada() {
        return temporada(EstadoTemporada.CONFIGURACION, 1, 1, 1);
    }

    private static CrearTemporadaRequest crear(String nombre) {
        return new CrearTemporadaRequest(nombre, INICIO, FIN);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private void permitirEdicion() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));
    }

    // ── Listado y ámbito ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-001: el listado usa el alcance del permiso y arma la lista de verificación")
    void listarUsaAlcanceYVerificacion() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));
        given(temporadas.listar(LIGA, 20, 0, true)).willReturn(List.of(configurada()));
        given(temporadas.contar(LIGA, true)).willReturn(21L);
        given(temporadas.buscarActiva(LIGA, null)).willReturn(Optional.empty());

        var pagina = servicio.listar(0, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(21);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
        assertThat(pagina.contenido().getFirst().activacion().puedeActivarse()).isTrue();
        assertThat(pagina.contenido().getFirst().activacion().requisitos())
                .extracting(RequisitoActivacionDto::codigo)
                .containsExactly("RN-058", "RF-100", "RF-004", "RF-005");
    }

    @Test
    @DisplayName("ADR-0007: un alcance de club recibe la página vacía y no consulta la temporada activa")
    void listarConAlcanceDeClub() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));
        given(temporadas.listar(LIGA, 20, 0, false)).willReturn(List.of());
        given(temporadas.contar(LIGA, false)).willReturn(0L);

        var pagina = servicio.listar(0, 20);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        verify(temporadas, never()).buscarActiva(any(), any());
    }

    @Test
    @DisplayName("RF-001: rechaza una página negativa o un tamaño fuera de 1 a 100")
    void listarValidaPaginacion() {
        permitirEdicion();

        assertThatThrownBy(() -> servicio.listar(-1, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(0, 101))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(temporadas);
    }

    @Test
    @DisplayName("VAL-024: una temporada inexistente responde 404 sin evaluar el permiso")
    void consultarInexistente() {
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("ADR-0007: consultar una temporada exige el permiso con objetivo de liga")
    void consultarUsaPermisoDeLiga() {
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.of(configurada()));
        given(temporadas.buscarActiva(LIGA, ID)).willReturn(Optional.empty());

        assertThat(servicio.consultar(ID).nombre()).isEqualTo("Temporada 2030");

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
    }

    @Test
    @DisplayName("RN-058: la lista de verificación nombra la temporada que ya está activa")
    void verificacionNombraLaActiva() {
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.of(configurada()));
        given(temporadas.buscarActiva(LIGA, ID))
                .willReturn(Optional.of(new TemporadaActiva(OTRA, "Temporada 2029")));

        var temporada = servicio.consultar(ID);

        assertThat(temporada.activacion().puedeActivarse()).isFalse();
        assertThat(temporada.activacion().requisitos().getFirst().cumple()).isFalse();
        assertThat(temporada.activacion().requisitos().getFirst().descripcion()).contains("Temporada 2029");
    }

    // ── Alta y edición ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015: sin permiso de edición no se registra ninguna temporada")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(crear("Temporada 2031")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(temporadas);
    }

    @Test
    @DisplayName("RF-001: al registrar recorta el nombre, usa el perfil de la sesión y audita")
    void crearRegistraYAudita() {
        given(temporadas.existeNombre(LIGA, "Temporada 2031", null)).willReturn(false);
        given(temporadas.insertar(LIGA, "Temporada 2031", INICIO, FIN, PERFIL)).willReturn(ID);
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.of(configurada()));
        given(temporadas.buscarActiva(LIGA, ID)).willReturn(Optional.empty());

        TemporadaDto creada = servicio.crear(crear("  Temporada 2031  "));

        assertThat(creada.estado()).isEqualTo(EstadoTemporada.CONFIGURACION);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_TEMPORADA");
        assertThat(evento.entidad()).isEqualTo("temporadas");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
    }

    @Test
    @DisplayName("RF-001: rechaza una fecha de fin anterior a la de inicio y un nombre repetido")
    void crearValidaFechasYNombre() {
        assertThatThrownBy(() -> servicio.crear(new CrearTemporadaRequest("Temporada 2031", FIN, INICIO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verifyNoInteractions(temporadas);

        given(temporadas.existeNombre(LIGA, "Temporada 2030", null)).willReturn(true);
        assertThatThrownBy(() -> servicio.crear(crear("Temporada 2030")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        verify(temporadas, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-003: una temporada cerrada es de solo lectura")
    void actualizarCerrada() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CERRADA, 1, 1, 1)));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarTemporadaRequest("Otra", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(temporadas, never()).actualizar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-001: editar cambia solo lo indicado y audita los campos modificados")
    void actualizarCampos() {
        DatosTemporada actual = temporada(EstadoTemporada.CONFIGURACION, 1, 1, 1);
        DatosTemporada editada = new DatosTemporada(ID, "Temporada 2031", INICIO.plusDays(1), FIN,
                EstadoTemporada.CONFIGURACION, CREADO_EN, 1, 1, 1, null);
        given(temporadas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(actual));
        given(temporadas.existeNombre(LIGA, "Temporada 2031", ID)).willReturn(false);
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.of(editada));
        given(temporadas.buscarActiva(LIGA, ID)).willReturn(Optional.empty());

        TemporadaDto resultado = servicio.actualizar(ID,
                new ActualizarTemporadaRequest("Temporada 2031", INICIO.plusDays(1), null));

        assertThat(resultado.nombre()).isEqualTo("Temporada 2031");
        verify(temporadas).actualizar(ID, "Temporada 2031", INICIO.plusDays(1), FIN);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("EDITAR_TEMPORADA");
        assertThat(evento.detalle()).containsEntry("campos", List.of("nombre", "fechaInicio"));
    }

    @Test
    @DisplayName("RF-003: un PATCH sin cambios no escribe ni audita")
    void actualizarSinCambios() {
        DatosTemporada actual = temporada(EstadoTemporada.CONFIGURACION, 1, 1, 1);
        given(temporadas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(actual));

        servicio.actualizar(ID, new ActualizarTemporadaRequest(" Temporada 2030 ", null, null));

        verify(temporadas, never()).actualizar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-001: una temporada activa sí se puede editar")
    void actualizarActiva() {
        DatosTemporada actual = temporada(EstadoTemporada.ACTIVA, 1, 1, 1);
        given(temporadas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(actual));
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.of(actual));

        servicio.actualizar(ID, new ActualizarTemporadaRequest(null, null, FIN.plusDays(1)));

        verify(temporadas).actualizar(ID, "Temporada 2030", INICIO, FIN.plusDays(1));
    }

    // ── Activación (RF-004, RF-005, RF-100, RN-058) ──────────────────────────────────────────

    @Test
    @DisplayName("RN-058: no activa si otra temporada ya está activa y la nombra en el mensaje")
    void activarConOtraActiva() {
        given(temporadas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(configurada()));
        given(temporadas.buscarActiva(LIGA, ID))
                .willReturn(Optional.of(new TemporadaActiva(OTRA, "Temporada 2029")));

        assertThatThrownBy(() -> servicio.activar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RN-058").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("Temporada 2029");
                });
        verify(temporadas, never()).cambiarEstado(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-100: no activa sin parámetros deportivos y devuelve lo que falta")
    void activarSinParametros() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION, null, 1, 1)));
        given(temporadas.buscarActiva(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.activar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RF-100").accept(excepcion);
                    List<RequisitoActivacionDto> requisitos = requisitos(excepcion);
                    assertThat(requisitos).extracting(RequisitoActivacionDto::codigo).contains("RF-100");
                });
        verify(temporadas, never()).cambiarEstado(any(), any());
    }

    @Test
    @DisplayName("RF-004: no activa sin categorías asignadas")
    void activarSinCategorias() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION, 1, 0, 0)));
        given(temporadas.buscarActiva(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.activar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-004"));
    }

    @Test
    @DisplayName("RF-005: no activa sin grupos creados")
    void activarSinGrupos() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION, 1, 1, 0)));
        given(temporadas.buscarActiva(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.activar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-005"));
    }

    @Test
    @DisplayName("RF-001: con la lista de verificación completa activa y audita")
    void activarConRequisitos() {
        DatosTemporada activa = temporada(EstadoTemporada.ACTIVA, 1, 1, 1);
        given(temporadas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(configurada()));
        given(temporadas.buscarActiva(LIGA, ID)).willReturn(Optional.empty());
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.of(activa));

        TemporadaDto resultado = servicio.activar(ID);

        assertThat(resultado.estado()).isEqualTo(EstadoTemporada.ACTIVA);
        assertThat(resultado.activacion()).as("solo se envía en configuración").isNull();
        verify(temporadas).cambiarEstado(ID, EstadoTemporada.ACTIVA);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ACTIVAR_TEMPORADA");
        assertThat(evento.detalle()).containsEntry("estado", "ACTIVA");
    }

    @Test
    @DisplayName("RF-001: no activa una temporada que no está en configuración")
    void activarFueraDeConfiguracion() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.ACTIVA, 1, 1, 1)));

        assertThatThrownBy(() -> servicio.activar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-001"));
        verify(temporadas, never()).cambiarEstado(any(), any());
    }

    // ── Cierre (RF-003, RN-021, RF-093) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-021: cerrar exige un motivo de al menos 10 caracteres")
    void cerrarSinMotivo() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.ACTIVA, 1, 1, 1)));

        assertThatThrownBy(() -> servicio.cerrar(ID, new CerrarTemporadaRequest(null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.cerrar(ID, new CerrarTemporadaRequest("  Corto  ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(motivoOperacion, never()).fijar(any());
        verify(temporadas, never()).cambiarEstado(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-003: cerrar fija el motivo, cambia el estado y lo deja en auditoría")
    void cerrarConMotivo() {
        DatosTemporada cerrada = temporada(EstadoTemporada.CERRADA, 1, 1, 1);
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.ACTIVA, 1, 1, 1)));
        given(temporadas.buscar(LIGA, ID)).willReturn(Optional.of(cerrada));

        TemporadaDto resultado = servicio.cerrar(ID, new CerrarTemporadaRequest("  " + MOTIVO + "  "));

        assertThat(resultado.estado()).isEqualTo(EstadoTemporada.CERRADA);
        verify(motivoOperacion).fijar(MOTIVO);
        verify(temporadas).cambiarEstado(ID, EstadoTemporada.CERRADA);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CERRAR_TEMPORADA");
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("RF-001: solo una temporada activa se puede cerrar")
    void cerrarFueraDeActiva() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION, 1, 1, 1)));

        assertThatThrownBy(() -> servicio.cerrar(ID, new CerrarTemporadaRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-001"));
        verifyNoInteractions(motivoOperacion, auditoria);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<RequisitoActivacionDto> requisitos(ReglaNegocioException excepcion) {
        Map<String, Object> propiedades = excepcion.propiedades();
        assertThat(propiedades).containsKey("requisitos");
        return (List<RequisitoActivacionDto>) propiedades.get("requisitos");
    }
}
