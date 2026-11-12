package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
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
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.DocumentoRegistroRepository.RegistroDeDocumentos;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDetalleDto.PersonaDetalleDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDetalleDto.TutorDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorResumenDto.PersonaListadoDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/** Consulta de registros de jugador: listado y detalle (H-10.5, RF-017, DEC-109, DEC-110), sin Spring ni BD. */
class ConsultaRegistrosServiceTest {

    private static final String CONSULTAR = "registros.registro:CONSULTAR";
    private static final String CONSULTAR_PERSONA = "organizacion.persona:CONSULTAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERSONA = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID EQUIPO = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID CLUB = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID TEMPORADA = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORIA = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final LocalDate HOY = LocalDate.parse("2026-11-11");
    private static final String CURP = "GAPA750101HMNRRS05";
    private static final AlcanceAutorizacion DE_CLUB = new AlcanceAutorizacion(false, Set.of(CLUB), false);
    private static final RegistroDeDocumentos REGISTRO_ENCONTRADO = new RegistroDeDocumentos(REGISTRO, PERSONA, CLUB,
            HOY, EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.ACTIVA, CATEGORIA, "Libre", TipoCategoria.LIBRE,
            EQUIPO, true, TEMPORADA, null);
    private static final List<TutorDto> TUTORES = List.of(new TutorDto("María Pérez", "Madre"));

    private final RegistroJugadorRepository registros = mock(RegistroJugadorRepository.class);
    private final DocumentoRegistroRepository documentos = mock(DocumentoRegistroRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final RegistroJugadorService servicio = new RegistroJugadorService(registros, documentos, autorizacion,
            mock(AuditoriaService.class), mock(MotivoOperacion.class),
            Clock.fixed(Instant.parse("2026-11-11T18:00:00Z"), ZoneOffset.UTC),
            new SiglimPropiedades(null, null, null, LIGA));

    // ── Listado ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-017 · DEC-109: lista con el alcance del permiso en el listado y en el conteo")
    void listaConElAlcance() {
        FiltroRegistros filtro = new FiltroRegistros("ACTIVO,PENDIENTE_VALIDACION", TEMPORADA, null, null,
                "nombre,asc", 1, 2);
        RegistroJugadorResumenDto fila = resumen();
        given(autorizacion.alcance(CONSULTAR)).willReturn(DE_CLUB);
        given(registros.listar(LIGA, filtro, DE_CLUB)).willReturn(List.of(fila));
        given(registros.contar(LIGA, filtro, DE_CLUB)).willReturn(3L);

        Pagina<RegistroJugadorResumenDto> pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).containsExactly(fila);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(2);
        assertThat(pagina.totalElementos()).isEqualTo(3);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
        assertThat(filtro.estados()).containsExactly(EstadoRegistro.ACTIVO, EstadoRegistro.PENDIENTE_VALIDACION);
        assertThat(filtro.ordenElegido()).isEqualTo(FiltroRegistros.Orden.NOMBRE_ASC);
    }

    @Test
    @DisplayName("DEC-109: sin orden ni estado se ordena por creadoEn,asc y no se filtra por estado")
    void valoresPorDefecto() {
        FiltroRegistros filtro = new FiltroRegistros(null, null, null, null, null, 0, 20);

        assertThat(filtro.ordenElegido()).isEqualTo(FiltroRegistros.Orden.CREADO_EN_ASC);
        assertThat(filtro.estados()).isEmpty();
        assertThat(new FiltroRegistros(" ", null, null, null, "creadoEn,desc", 0, 20).ordenElegido())
                .isEqualTo(FiltroRegistros.Orden.CREADO_EN_DESC);
    }

    @ParameterizedTest(name = "estado={0} orden={1} pagina={2} tamano={3}")
    @CsvSource(nullValues = "null", value = {
            "VIGENTE, null, 0, 20",
            "'ACTIVO,', null, 0, 20",
            "activo, null, 0, 20",
            "null, 'apellido,asc', 0, 20",
            "null, 'creadoEn,ASC', 0, 20",
            "null, 'nombre,desc', 0, 20",
            "null, null, -1, 20",
            "null, null, 0, 0",
            "null, null, 0, 101"})
    @DisplayName("DEC-109: un estado u orden desconocido o una página fuera de rango responde 400 VALIDACION sin consultar")
    void filtroInvalido(String estado, String orden, int pagina, int tamano) {
        given(autorizacion.alcance(CONSULTAR)).willReturn(DE_CLUB);
        FiltroRegistros filtro = new FiltroRegistros(estado, null, null, null, orden, pagina, tamano);

        assertThatThrownBy(() -> servicio.listar(filtro)).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.codigo()).isEqualTo(ErroresTecnicos.VALIDACION);
        });
        verifyNoInteractions(registros);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso el listado responde 403 antes de validar los filtros")
    void listarSinPermiso() {
        given(autorizacion.alcance(CONSULTAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listar(new FiltroRegistros("NO_EXISTE", null, null, null, null, 0, 20)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VAL-015"));
        verifyNoInteractions(registros);
    }

    // ── Detalle ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015: sin el permiso el detalle responde 403 sin buscar el registro")
    void detalleSinPermiso() {
        given(autorizacion.alcance(CONSULTAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.consultar(REGISTRO)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> assertThat(error.codigo()).isEqualTo("VAL-015"));
        verifyNoInteractions(documentos, registros);
    }

    @Test
    @DisplayName("RF-017: un registro inexistente en la liga responde 404 antes de exigir el ámbito")
    void detalleInexistente() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(DE_CLUB);
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(REGISTRO)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(autorizacion, never()).exigir(any(), any());
        verifyNoInteractions(registros);
    }

    @Test
    @DisplayName("VAL-024: fuera de ámbito responde 404 después del 403 y de buscar, con el club y la persona del registro")
    void detalleFueraDeAmbito() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(DE_CLUB);
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(REGISTRO_ENCONTRADO));
        willThrow(Autorizacion.fueraDeAmbito(CONSULTAR)).given(autorizacion).exigir(any(), any());

        assertThatThrownBy(() -> servicio.consultar(REGISTRO)).isInstanceOf(RecursoNoEncontradoException.class);

        InOrder orden = inOrder(autorizacion, documentos);
        orden.verify(autorizacion).alcance(CONSULTAR);
        orden.verify(documentos).buscarRegistro(LIGA, REGISTRO);
        orden.verify(autorizacion).exigir(CONSULTAR,
                ObjetivoAutorizacion.deClubesYPersonas(List.of(CLUB), List.of(PERSONA)));
        verifyNoInteractions(registros);
    }

    @Test
    @DisplayName("DEC-110 · RNF-012: con organizacion.persona:CONSULTAR sobre el club el detalle lleva la CURP completa")
    void detalleConCurp() {
        prepararDetalle(false);
        given(autorizacion.permite(CONSULTAR_PERSONA, ObjetivoAutorizacion.deClubes(CLUB))).willReturn(true);

        RegistroJugadorDetalleDto detalle = servicio.consultar(REGISTRO);

        assertThat(detalle.persona().curp()).isEqualTo(CURP);
        assertThat(detalle.persona().curpEnmascarada()).isEqualTo("GAPA******HMN***05");
        verify(autorizacion).permite(CONSULTAR_PERSONA, ObjetivoAutorizacion.deClubes(CLUB));
    }

    @Test
    @DisplayName("DEC-110 · RNF-012: sin organizacion.persona:CONSULTAR sobre el club la CURP es null y queda la enmascarada")
    void detalleSinCurp() {
        prepararDetalle(false);
        given(autorizacion.permite(CONSULTAR_PERSONA, ObjetivoAutorizacion.deClubes(CLUB))).willReturn(false);

        RegistroJugadorDetalleDto detalle = servicio.consultar(REGISTRO);

        assertThat(detalle.persona().curp()).isNull();
        assertThat(detalle.persona().curpEnmascarada()).isEqualTo("GAPA******HMN***05");
        assertThat(detalle.persona().nombreCompleto()).isEqualTo("Ana López");
    }

    @Test
    @DisplayName("DEC-109: un adulto no tiene tutores y no se consultan")
    void adultoSinTutores() {
        prepararDetalle(false);

        RegistroJugadorDetalleDto detalle = servicio.consultar(REGISTRO);

        assertThat(detalle.esMenor()).isFalse();
        assertThat(detalle.tutores()).isEmpty();
        verify(registros, never()).tutoresVigentes(any());
    }

    @Test
    @DisplayName("DEC-109 · VAL-025: un menor lleva sus tutores vigentes, sin CURP ni contacto")
    void menorConTutores() {
        prepararDetalle(true);
        given(registros.tutoresVigentes(PERSONA)).willReturn(TUTORES);

        RegistroJugadorDetalleDto detalle = servicio.consultar(REGISTRO);

        assertThat(detalle.esMenor()).isTrue();
        assertThat(detalle.tutores()).containsExactlyElementsOf(TUTORES);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private void prepararDetalle(boolean menor) {
        given(autorizacion.alcance(CONSULTAR)).willReturn(DE_CLUB);
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(REGISTRO_ENCONTRADO));
        given(registros.buscarDetalle(REGISTRO)).willReturn(Optional.of(detalle(menor)));
    }

    private static RegistroJugadorDetalleDto detalle(boolean menor) {
        return new RegistroJugadorDetalleDto(REGISTRO,
                new PersonaDetalleDto(PERSONA, "Ana López", EnmascaradoCurp.enmascarar(CURP), CURP),
                new ReferenciaDto(EQUIPO, "Halcones"), new ReferenciaDto(CLUB, "Club Halcones"),
                new ReferenciaDto(CATEGORIA, "Libre"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2026", EstadoTemporada.ACTIVA),
                EstadoRegistro.PENDIENTE_VALIDACION, menor, HOY, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"),
                null, null, null, List.of());
    }

    private static RegistroJugadorResumenDto resumen() {
        return new RegistroJugadorResumenDto(REGISTRO,
                new PersonaListadoDto(PERSONA, "Ana López", "GAPA******HMN***05"),
                new ReferenciaDto(EQUIPO, "Halcones"), new ReferenciaDto(CLUB, "Club Halcones"),
                new ReferenciaDto(CATEGORIA, "Libre"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2026", EstadoTemporada.ACTIVA),
                EstadoRegistro.ACTIVO, false, HOY, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"));
    }
}
