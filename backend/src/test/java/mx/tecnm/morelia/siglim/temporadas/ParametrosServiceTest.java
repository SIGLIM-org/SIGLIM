package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
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
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.TemporadaEstado;
import mx.tecnm.morelia.siglim.temporadas.ParametrosRepository.FilaCategoria;
import mx.tecnm.morelia.siglim.temporadas.ParametrosRepository.FilaVersion;
import mx.tecnm.morelia.siglim.temporadas.ParametrosRepository.VersionCreada;
import mx.tecnm.morelia.siglim.temporadas.ValoresParametros.ValorCategoria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link ParametrosService}. Sin Spring ni base de datos. */
class ParametrosServiceTest {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.parametros:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID OTRA_CATEGORIA = UUID.fromString("78787878-7878-4878-8878-787878787878");
    private static final UUID VERSION_1 = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID VERSION_2 = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PERFIL = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final String MOTIVO = ParametrosPrueba.MOTIVO;

    /** Los 16 campos de una versión, sin {@code categorias}: los de la versión 1 sin filas propias. */
    private static final List<String> TODOS = List.of("puntosVictoria", "puntosEmpate", "puntosDerrota",
            "criteriosDesempate", "golesDefaultGanador", "golesDefaultPerdedor", "plazoValidacionCedulaHoras",
            "duracionJuegoMin", "bloqueCanchaMin", "clasificadosLiguilla", "formatoLiguilla",
            "empateLiguillaPorPenales", "equiposAscienden", "equiposDescienden", "permiteCambioClub", "esDemo");

    private final ParametrosRepository parametros = mock(ParametrosRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ParametrosService servicio = new ParametrosService(parametros, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

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

    private void existeTemporada(EstadoTemporada estado) {
        TemporadaEstado temporada = new TemporadaEstado(TEMPORADA, "Temporada 2030", estado);
        given(parametros.buscarTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(temporada));
        given(parametros.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(temporada));
    }

    private static ValoresParametros predeterminados(boolean esDemo) {
        ParametrosPredeterminadosDto d = ParametrosPredeterminadosDto.DE_LA_TABLA;
        return new ValoresParametros(d.puntosVictoria(), d.puntosEmpate(), d.puntosDerrota(), d.criteriosDesempate(),
                d.golesDefaultGanador(), d.golesDefaultPerdedor(), d.plazoValidacionCedulaHoras(),
                d.duracionJuegoMin(), d.bloqueCanchaMin(), d.clasificadosLiguilla(), d.formatoLiguilla(),
                d.empateLiguillaPorPenales(), d.equiposAscienden(), d.equiposDescienden(), d.permiteCambioClub(),
                esDemo, List.of());
    }

    private static FilaVersion fila(UUID id, int version, ValoresParametros valores) {
        return new FilaVersion(id, TEMPORADA, version, valores, MOTIVO, PERFIL, "Administrador", CREADO_EN);
    }

    private static FilaCategoria filaCategoria(UUID parametrosId, int duracion, int bloque) {
        return new FilaCategoria(parametrosId, new ReferenciaDto(CATEGORIA, "Veteranos"), duracion, bloque);
    }

    /** Prepara la inserción de la versión {@code version} y su lectura posterior. */
    private void seInserta(UUID id, int version, ValoresParametros valores, List<FilaCategoria> categorias) {
        given(parametros.insertar(eq(TEMPORADA), any(ValoresParametros.class), eq(MOTIVO), eq(PERFIL)))
                .willReturn(new VersionCreada(id, version));
        given(parametros.buscarVersion(TEMPORADA, version)).willReturn(Optional.of(fila(id, version, valores)));
        given(parametros.categoriasDeVersion(id)).willReturn(categorias);
    }

    private ValoresParametros valoresInsertados() {
        ArgumentCaptor<ValoresParametros> valores = ArgumentCaptor.forClass(ValoresParametros.class);
        verify(parametros).insertar(eq(TEMPORADA), valores.capture(), eq(MOTIVO), eq(PERFIL));
        return valores.getValue();
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    /** El cuerpo se rechaza antes de tocar la base: ni temporada, ni inserción, ni auditoría. */
    private void rechazaCuerpo(ParametrosPrueba.Juego juego, String codigo, String fragmento) {
        assertThatThrownBy(() -> servicio.registrar(TEMPORADA, juego.peticion()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, codigo))
                .hasMessageContaining(fragmento);
        verifyNoInteractions(parametros, auditoria);
    }

    // ── Registro: caso feliz ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-100, D2: la primera versión sin categorías se registra con esDemo = true por omisión")
    void registrarPrimeraVersion() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        seInserta(VERSION_1, 1, predeterminados(true), List.of());

        ParametrosDto creada = servicio.registrar(TEMPORADA, ParametrosPrueba.valido());

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        ValoresParametros insertados = valoresInsertados();
        assertThat(insertados).isEqualTo(predeterminados(true));
        verify(parametros, never()).insertarCategoria(any(), any());
        assertThat(creada.version()).isEqualTo(1);
        assertThat(creada.esDemo()).isTrue();
        assertThat(creada.camposDemo()).containsExactlyElementsOf(ParametrosService.CAMPOS_DEMO)
                .doesNotContain("duracionJuegoMin", "permiteCambioClub");
        assertThat(creada.creadoPor()).isEqualTo(new ReferenciaDto(PERFIL, "Administrador"));
        assertThat(creada.categorias()).isEmpty();
    }

    @Test
    @DisplayName("RF-100, D7: las filas por categoría asignada se insertan con la versión")
    void registrarConCategorias() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        given(parametros.categoriasAsignadas(TEMPORADA)).willReturn(Set.of(CATEGORIA, OTRA_CATEGORIA));
        seInserta(VERSION_1, 1, predeterminados(true), List.of(filaCategoria(VERSION_1, 70, 90)));

        ParametrosDto creada = servicio.registrar(TEMPORADA, ParametrosPrueba.juego()
                .categoria(CATEGORIA, 70, 90).peticion());

        verify(parametros).insertarCategoria(VERSION_1, new ValorCategoria(CATEGORIA, 70, 90));
        assertThat(creada.categorias()).singleElement().satisfies(fila -> {
            assertThat(fila.categoria().nombre()).isEqualTo("Veteranos");
            assertThat(fila.bloqueCanchaMin()).isEqualTo(90);
        });
        assertThat(eventoAuditado().detalle().get("campos")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).contains("categorias");
    }

    @Test
    @DisplayName("D4: una temporada ACTIVA admite una versión nueva")
    void registrarEnTemporadaActiva() {
        existeTemporada(EstadoTemporada.ACTIVA);
        seInserta(VERSION_1, 1, predeterminados(true), List.of());

        assertThat(servicio.registrar(TEMPORADA, ParametrosPrueba.valido()).version()).isEqualTo(1);
    }

    @Test
    @DisplayName("D2, PEN-021: con esDemo = false los valores son oficiales y camposDemo va vacío")
    void registrarOficial() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        seInserta(VERSION_1, 1, predeterminados(false), List.of());

        ParametrosDto creada = servicio.registrar(TEMPORADA, ParametrosPrueba.juego().esDemo(false).peticion());

        assertThat(valoresInsertados().esDemo()).isFalse();
        assertThat(creada.esDemo()).isFalse();
        assertThat(creada.camposDemo()).isEmpty();
    }

    @Test
    @DisplayName("RF-093, D10: la primera versión se audita con todos los campos y sin versión anterior")
    void auditoriaPrimeraVersion() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        seInserta(VERSION_1, 1, predeterminados(true), List.of());

        servicio.registrar(TEMPORADA, ParametrosPrueba.valido());

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-02");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_PARAMETROS");
        assertThat(evento.entidad()).isEqualTo("parametros_temporada");
        assertThat(evento.entidadId()).isEqualTo(VERSION_1.toString());
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).containsEntry("temporadaId", TEMPORADA.toString())
                .containsEntry("version", 1)
                .containsEntry("versionAnterior", null)
                .containsEntry("esDemo", true)
                .containsEntry("campos", TODOS);
    }

    @Test
    @DisplayName("RF-006, D10: una versión nueva audita solo los campos que cambiaron respecto de la vigente")
    void auditoriaVersionConCambios() {
        existeTemporada(EstadoTemporada.ACTIVA);
        given(parametros.buscarVigente(TEMPORADA)).willReturn(Optional.of(fila(VERSION_1, 1, predeterminados(true))));
        given(parametros.categoriasDeVersion(VERSION_1)).willReturn(List.of());
        given(parametros.categoriasAsignadas(TEMPORADA)).willReturn(Set.of(CATEGORIA));
        seInserta(VERSION_2, 2, predeterminados(true), List.of());

        servicio.registrar(TEMPORADA, ParametrosPrueba.juego().puntos(2, 1, 0).categoria(CATEGORIA, 70, 90)
                .peticion());

        assertThat(eventoAuditado().detalle()).containsEntry("version", 2)
                .containsEntry("versionAnterior", 1)
                .containsEntry("campos", List.of("puntosVictoria", "categorias"));
    }

    @Test
    @DisplayName("D9: la temporada se bloquea antes de leer la versión vigente e insertar la nueva")
    void candadoAntesDeInsertar() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        seInserta(VERSION_1, 1, predeterminados(true), List.of());

        servicio.registrar(TEMPORADA, ParametrosPrueba.valido());

        InOrder orden = inOrder(autorizacion, parametros, auditoria);
        orden.verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        orden.verify(parametros).bloquearTemporada(LIGA, TEMPORADA);
        orden.verify(parametros).buscarVigente(TEMPORADA);
        orden.verify(parametros).insertar(eq(TEMPORADA), any(), eq(MOTIVO), eq(PERFIL));
        orden.verify(auditoria).registrar(any());
        verify(parametros, never()).buscarTemporada(any(), any());
    }

    // ── Registro: rechazos de estado, temporada y referencias ────────────────────────────────

    @Test
    @DisplayName("RNF-003: sin permiso no se valida el cuerpo ni se toca la base")
    void registrarSinPermiso() {
        willThrow(ReglaNegocioException.prohibida("VAL-015", "Sin permiso"))
                .given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.registrar(TEMPORADA, ParametrosPrueba.juego().motivo(null).peticion()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(parametros, auditoria);
    }

    @Test
    @DisplayName("VAL-024: una temporada inexistente o de otra liga responde 404")
    void registrarTemporadaInexistente() {
        given(parametros.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(TEMPORADA, ParametrosPrueba.valido()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(parametros, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-003, D4: una temporada CERRADA responde 409 RF-003 y no crea versión")
    void registrarEnTemporadaCerrada() {
        existeTemporada(EstadoTemporada.CERRADA);

        assertThatThrownBy(() -> servicio.registrar(TEMPORADA, ParametrosPrueba.valido()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"))
                .hasMessageContaining("Temporada 2030");
        verify(parametros, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("D7: una categoría no asignada a la temporada responde 422 REFERENCIA_INVALIDA")
    void categoriaNoAsignada() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        given(parametros.categoriasAsignadas(TEMPORADA)).willReturn(Set.of(OTRA_CATEGORIA));

        assertThatThrownBy(() -> servicio.registrar(TEMPORADA, ParametrosPrueba.juego()
                .categoria(CATEGORIA, 70, 90).peticion()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(excepcion.codigo()).isEqualTo(ErroresTecnicos.REFERENCIA_INVALIDA);
                    assertThat(excepcion.propiedades()).containsEntry("categoriasInvalidas",
                            List.of(CATEGORIA.toString()));
                });
        verify(parametros, never()).insertar(any(), any(), any(), any());
    }

    // ── Registro: motivo (D5, RN-021) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("RN-021: sin motivo responde 400 RN-021")
    void motivoAusente() {
        rechazaCuerpo(ParametrosPrueba.juego().motivo(null), "RN-021", "al menos 10");
    }

    @Test
    @DisplayName("RN-021: un motivo de menos de 10 caracteres sin espacios responde 400 RN-021")
    void motivoCorto() {
        rechazaCuerpo(ParametrosPrueba.juego().motivo("   corto     "), "RN-021", "al menos 10");
    }

    @Test
    @DisplayName("D5: un motivo de más de 500 caracteres responde 400 VALIDACION")
    void motivoLargo() {
        rechazaCuerpo(ParametrosPrueba.juego().motivo("x".repeat(501)), ErroresTecnicos.VALIDACION, "500");
    }

    // ── Registro: criterios de desempate (D6, RF-050) ────────────────────────────────────────

    @Test
    @DisplayName("RF-050, D6: los criterios deben empezar en PUNTOS")
    void criteriosSinPuntosPrimero() {
        rechazaCuerpo(ParametrosPrueba.juego().criterios(CriterioDesempate.DIFERENCIA_GOLES, CriterioDesempate.PUNTOS),
                ErroresTecnicos.VALIDACION, "PUNTOS");
    }

    @Test
    @DisplayName("RF-050, D6: un criterio repetido responde 400 VALIDACION")
    void criteriosRepetidos() {
        rechazaCuerpo(ParametrosPrueba.juego().criterios(CriterioDesempate.PUNTOS, CriterioDesempate.GOLES_FAVOR,
                CriterioDesempate.GOLES_FAVOR), ErroresTecnicos.VALIDACION, "repetido");
    }

    @Test
    @DisplayName("RF-050, D6: el SORTEO solo puede ser el último criterio")
    void sorteoNoAlFinal() {
        rechazaCuerpo(ParametrosPrueba.juego().criterios(CriterioDesempate.PUNTOS, CriterioDesempate.SORTEO,
                CriterioDesempate.GOLES_FAVOR), ErroresTecnicos.VALIDACION, "SORTEO");
    }

    @Test
    @DisplayName("RF-050, D6: sin criterios responde 400 VALIDACION")
    void criteriosVacios() {
        rechazaCuerpo(ParametrosPrueba.juego().criterios(), ErroresTecnicos.VALIDACION, "al menos un criterio");
        rechazaCuerpo(ParametrosPrueba.juego().criterios((CriterioDesempate[]) null), ErroresTecnicos.VALIDACION,
                "al menos un criterio");
    }

    @Test
    @DisplayName("RF-050, D6: más de 8 criterios responde 400 VALIDACION")
    void demasiadosCriterios() {
        CriterioDesempate[] nueve = new CriterioDesempate[9];
        List<CriterioDesempate> todos = List.of(CriterioDesempate.values());
        for (int i = 0; i < nueve.length; i++) {
            nueve[i] = todos.get(i % todos.size());
        }
        rechazaCuerpo(ParametrosPrueba.juego().criterios(nueve), ErroresTecnicos.VALIDACION, "a lo más 8");
    }

    @Test
    @DisplayName("RF-050, D6: solo PUNTOS es un juego de criterios válido")
    void soloPuntos() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        seInserta(VERSION_1, 1, predeterminados(true), List.of());

        servicio.registrar(TEMPORADA, ParametrosPrueba.juego().criterios(CriterioDesempate.PUNTOS).peticion());

        assertThat(valoresInsertados().criteriosDesempate()).containsExactly(CriterioDesempate.PUNTOS);
    }

    // ── Registro: demás campos (CHECK de la tabla replicados en Java) ────────────────────────

    @Test
    @DisplayName("RF-100: los puntos deben cumplir victoria ≥ empate ≥ derrota")
    void puntosFueraDeOrden() {
        rechazaCuerpo(ParametrosPrueba.juego().puntos(1, 3, 0), ErroresTecnicos.VALIDACION, "victoria ≥ empate");
        rechazaCuerpo(ParametrosPrueba.juego().puntos(3, 1, 2), ErroresTecnicos.VALIDACION, "victoria ≥ empate");
    }

    @Test
    @DisplayName("RF-100: puntos fuera de 0..100 responden 400 VALIDACION")
    void puntosFueraDeRango() {
        rechazaCuerpo(ParametrosPrueba.juego().puntos(101, 1, 0), ErroresTecnicos.VALIDACION, "entre 0 y 100");
        rechazaCuerpo(ParametrosPrueba.juego().puntos(3, 1, -1), ErroresTecnicos.VALIDACION, "entre 0 y 100");
    }

    @Test
    @DisplayName("DEC-24: en el marcador por default el ganador tiene más goles que el perdedor")
    void golesDefault() {
        rechazaCuerpo(ParametrosPrueba.juego().goles(0, 0), ErroresTecnicos.VALIDACION, "más goles");
        rechazaCuerpo(ParametrosPrueba.juego().goles(2, 3), ErroresTecnicos.VALIDACION, "más goles");
        rechazaCuerpo(ParametrosPrueba.juego().goles(100, 0), ErroresTecnicos.VALIDACION, "entre 0 y 99");
    }

    @Test
    @DisplayName("DEC-25: el bloque de cancha de la temporada no puede ser menor que la duración de juego")
    void bloqueMenorQueDuracion() {
        rechazaCuerpo(ParametrosPrueba.juego().tiempos(90, 80), ErroresTecnicos.VALIDACION, "bloque de cancha");
    }

    @Test
    @DisplayName("DEC-20: el plazo de la cédula va de 1 a 720 horas")
    void plazoFueraDeRango() {
        rechazaCuerpo(ParametrosPrueba.juego().plazo(0), ErroresTecnicos.VALIDACION, "entre 1 y 720");
        rechazaCuerpo(ParametrosPrueba.juego().plazo(721), ErroresTecnicos.VALIDACION, "entre 1 y 720");
    }

    @Test
    @DisplayName("D3: un juego incompleto responde 400 VALIDACION con todos los errores por campo")
    void juegoIncompleto() {
        ParametrosPrueba.Juego juego = ParametrosPrueba.juego();
        juego.puntosEmpate = null;
        juego.clasificadosLiguilla = 6;
        juego.formatoLiguilla = null;

        assertThatThrownBy(() -> servicio.registrar(TEMPORADA, juego.peticion()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.codigo()).isEqualTo(ErroresTecnicos.VALIDACION);
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> errores = (List<Map<String, String>>) excepcion.propiedades()
                            .get("errores");
                    assertThat(errores).extracting(campo -> campo.get("campo"))
                            .containsExactly("puntosEmpate", "clasificadosLiguilla", "formatoLiguilla");
                });
        verifyNoInteractions(parametros, auditoria);
    }

    @Test
    @DisplayName("D7: el bloque de una categoría no puede ser menor que su duración de juego")
    void bloqueDeCategoriaMenorQueDuracion() {
        rechazaCuerpo(ParametrosPrueba.juego().categoria(CATEGORIA, 70, 60), ErroresTecnicos.VALIDACION,
                "bloque de cancha de la categoría");
    }

    @Test
    @DisplayName("D7: una categoría repetida en el cuerpo responde 400 VALIDACION")
    void categoriaRepetida() {
        rechazaCuerpo(ParametrosPrueba.juego().categoria(CATEGORIA, 70, 90).categoria(CATEGORIA, 60, 80),
                ErroresTecnicos.VALIDACION, "repetida");
    }

    // ── Consultas ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("D8: sin versiones, la consulta exige CONSULTAR y responde los predeterminados de la tabla")
    void consultarSinVersiones() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        given(parametros.listarVersiones(TEMPORADA)).willReturn(List.of());

        ParametrosTemporadaDto respuesta = servicio.consultar(TEMPORADA);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        assertThat(respuesta.vigente()).isNull();
        assertThat(respuesta.historial()).isEmpty();
        assertThat(respuesta.predeterminados()).isEqualTo(ParametrosPredeterminadosDto.DE_LA_TABLA);
        assertThat(respuesta.predeterminados().esDemo()).isTrue();
    }

    @Test
    @DisplayName("RF-006, D8: el historial va de la mayor a la menor con los campos cambiados de cada versión")
    void consultarHistorial() {
        existeTemporada(EstadoTemporada.ACTIVA);
        ValoresParametros segunda = new ValoresParametros(2, 1, 0, List.of(CriterioDesempate.PUNTOS), 3, 0, 48, 90,
                120, 8, FormatoLiguilla.PARTIDO_UNICO, true, 2, 2, true, false, List.of());
        given(parametros.listarVersiones(TEMPORADA)).willReturn(List.of(
                fila(VERSION_1, 1, predeterminados(true)), fila(VERSION_2, 2, segunda)));
        given(parametros.categoriasDeTemporada(TEMPORADA)).willReturn(List.of(filaCategoria(VERSION_2, 70, 90)));

        ParametrosTemporadaDto respuesta = servicio.consultar(TEMPORADA);

        assertThat(respuesta.predeterminados()).isNull();
        assertThat(respuesta.vigente().version()).isEqualTo(2);
        assertThat(respuesta.vigente().camposDemo()).isEmpty();
        assertThat(respuesta.vigente().categorias()).hasSize(1);
        assertThat(respuesta.historial()).extracting(VersionParametrosResumenDto::version).containsExactly(2, 1);
        assertThat(respuesta.historial().getFirst().camposCambiados())
                .containsExactly("puntosVictoria", "criteriosDesempate", "esDemo", "categorias");
        assertThat(respuesta.historial().getLast().camposCambiados()).containsExactlyElementsOf(TODOS);
    }

    @Test
    @DisplayName("RF-006, D8: la consulta por versión exige CONSULTAR y responde la versión pedida")
    void consultarVersion() {
        existeTemporada(EstadoTemporada.ACTIVA);
        given(parametros.buscarVersion(TEMPORADA, 1)).willReturn(Optional.of(fila(VERSION_1, 1, predeterminados(true))));
        given(parametros.categoriasDeVersion(VERSION_1)).willReturn(List.of());

        ParametrosDto version = servicio.consultarVersion(TEMPORADA, 1);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        assertThat(version.id()).isEqualTo(VERSION_1);
        assertThat(version.plazoValidacionCedulaHoras()).isEqualTo(48);
    }

    @Test
    @DisplayName("VAL-024: una versión inexistente responde 404")
    void consultarVersionInexistente() {
        existeTemporada(EstadoTemporada.ACTIVA);
        given(parametros.buscarVersion(eq(TEMPORADA), anyInt())).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarVersion(TEMPORADA, 7))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-024: consultar los parámetros de una temporada inexistente responde 404")
    void consultarTemporadaInexistente() {
        given(parametros.buscarTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(TEMPORADA)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(parametros, never()).listarVersiones(any());
    }
}
