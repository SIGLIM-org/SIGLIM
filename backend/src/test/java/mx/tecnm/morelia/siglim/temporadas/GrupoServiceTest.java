package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.temporadas.CategoriaRepository.FilaCategoria;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto.CategoriaResumenDto;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto.GrupoResumenDto;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.CategoriaAsignada;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.ContextoCategoriaTemporada;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.GrupoDeCategoria;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.GrupoTemporada;
import mx.tecnm.morelia.siglim.temporadas.GrupoRepository.TemporadaEstado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** Reglas de {@link GrupoService}. Sin Spring ni base de datos. */
class GrupoServiceTest {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.temporada:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID CATEGORIA_TEMPORADA = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID INEXISTENTE = UUID.fromString("99999999-9999-4999-8999-999999999999");

    private final GrupoRepository grupos = mock(GrupoRepository.class);
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final GrupoService servicio = new GrupoService(grupos, categorias, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static TemporadaEstado temporada(EstadoTemporada estado) {
        return new TemporadaEstado(TEMPORADA, "Temporada 2030", estado);
    }

    private void existeTemporada(EstadoTemporada estado) {
        given(grupos.buscarTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(temporada(estado)));
        given(grupos.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.of(temporada(estado)));
    }

    private void existeCategoria(boolean activa) {
        given(categorias.buscarParaAsignar(LIGA, CATEGORIA))
                .willReturn(Optional.of(new FilaCategoria(CATEGORIA, "Libre", 1, null, activa)));
    }

    private void existeCategoriaTemporada(EstadoTemporada estado) {
        ContextoCategoriaTemporada contexto = new ContextoCategoriaTemporada(CATEGORIA_TEMPORADA, temporada(estado),
                new ReferenciaDto(CATEGORIA, "Libre"));
        given(grupos.buscarCategoriaTemporada(LIGA, CATEGORIA_TEMPORADA)).willReturn(Optional.of(contexto));
        given(grupos.bloquearCategoriaTemporada(LIGA, CATEGORIA_TEMPORADA)).willReturn(Optional.of(contexto));
    }

    private static GrupoTemporada grupo(EstadoTemporada estado) {
        return new GrupoTemporada(new GrupoDto(GRUPO, "A", CATEGORIA_TEMPORADA, TEMPORADA,
                new ReferenciaDto(CATEGORIA, "Libre"), 2), temporada(estado));
    }

    private void existeGrupo(EstadoTemporada estado) {
        given(grupos.buscarGrupoParaActualizar(LIGA, GRUPO)).willReturn(Optional.of(grupo(estado)));
        given(grupos.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(grupo(estado)));
    }

    private void categoriaAsignadaConGrupo() {
        given(grupos.listarCategorias(TEMPORADA, CATEGORIA_TEMPORADA)).willReturn(List.of(new CategoriaAsignada(
                CATEGORIA_TEMPORADA, new CategoriaResumenDto(CATEGORIA, "Libre", TipoCategoria.LIBRE, 1, null))));
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

    private void rechazaAsignar(HttpStatus estado, String codigo) {
        assertThatThrownBy(() -> servicio.asignarCategoria(TEMPORADA, new AsignarCategoriaRequest(CATEGORIA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(estado, codigo));
        verify(grupos, never()).asignarCategoria(any(), any());
        verifyNoInteractions(auditoria);
    }

    private void rechazaCrearGrupo(HttpStatus estado, String codigo) {
        assertThatThrownBy(() -> servicio.crear(new CrearGrupoRequest(CATEGORIA_TEMPORADA, "A")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(estado, codigo));
        verify(grupos, never()).insertarGrupo(any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Categorías de la temporada ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-004, D8: lista las categorías de la temporada con sus grupos anidados")
    void listarCategoriasConGrupos() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        UUID otraCategoriaTemporada = UUID.randomUUID();
        given(grupos.listarCategorias(TEMPORADA, null)).willReturn(List.of(
                new CategoriaAsignada(CATEGORIA_TEMPORADA,
                        new CategoriaResumenDto(CATEGORIA, "Libre", TipoCategoria.LIBRE, 1, null)),
                new CategoriaAsignada(otraCategoriaTemporada,
                        new CategoriaResumenDto(UUID.randomUUID(), "Veteranos", TipoCategoria.VETERANOS, 2, null))));
        given(grupos.listarGruposDeTemporada(TEMPORADA, null)).willReturn(List.of(
                new GrupoDeCategoria(CATEGORIA_TEMPORADA, new GrupoResumenDto(GRUPO, "A", 3))));

        List<CategoriaTemporadaDto> lista = servicio.listarCategorias(TEMPORADA);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        assertThat(lista).extracting(CategoriaTemporadaDto::id).containsExactly(CATEGORIA_TEMPORADA,
                otraCategoriaTemporada);
        assertThat(lista.getFirst().grupos()).containsExactly(new GrupoResumenDto(GRUPO, "A", 3));
        assertThat(lista.getLast().grupos()).isEmpty();
    }

    @Test
    @DisplayName("RF-004: las categorías de una temporada inexistente responden 404")
    void listarCategoriasDeTemporadaInexistente() {
        given(grupos.buscarTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.listarCategorias(TEMPORADA)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("RF-004: una categoría de otra temporada responde 404")
    void consultarCategoriaDeOtraTemporada() {
        existeTemporada(EstadoTemporada.ACTIVA);
        given(grupos.listarCategorias(TEMPORADA, CATEGORIA_TEMPORADA)).willReturn(List.of());

        assertThatThrownBy(() -> servicio.consultarCategoria(TEMPORADA, CATEGORIA_TEMPORADA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-015, D1: sin CONSULTAR las categorías de la temporada responden 403")
    void listarCategoriasSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.listarCategorias(TEMPORADA))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, ErroresTecnicos.SIN_PERMISO));
        verifyNoInteractions(grupos);
    }

    @Test
    @DisplayName("RF-004: asigna la categoría en configuración y audita ASIGNAR_CATEGORIA_TEMPORADA")
    void asignarCategoria() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        existeCategoria(true);
        given(grupos.asignarCategoria(TEMPORADA, CATEGORIA)).willReturn(CATEGORIA_TEMPORADA);
        categoriaAsignadaConGrupo();

        CategoriaTemporadaDto asignada = servicio.asignarCategoria(TEMPORADA, new AsignarCategoriaRequest(CATEGORIA));

        assertThat(asignada.id()).isEqualTo(CATEGORIA_TEMPORADA);
        assertThat(asignada.grupos()).isEmpty();
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-02");
        assertThat(evento.accion()).isEqualTo("ASIGNAR_CATEGORIA_TEMPORADA");
        assertThat(evento.entidad()).isEqualTo("categorias_temporada");
        assertThat(evento.entidadId()).isEqualTo(CATEGORIA_TEMPORADA.toString());
        assertThat(evento.detalle()).containsEntry("temporadaId", TEMPORADA.toString())
                .containsEntry("categoriaId", CATEGORIA.toString());
    }

    @Test
    @DisplayName("VAL-015, D1: sin EDITAR asignar responde 403 sin escribir")
    void asignarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.asignarCategoria(TEMPORADA, new AsignarCategoriaRequest(CATEGORIA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, ErroresTecnicos.SIN_PERMISO));
        verifyNoInteractions(grupos, categorias, auditoria);
    }

    @Test
    @DisplayName("RF-004: asignar en una temporada inexistente responde 404")
    void asignarEnTemporadaInexistente() {
        given(grupos.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.asignarCategoria(TEMPORADA, new AsignarCategoriaRequest(CATEGORIA)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-004: una categoría inexistente responde 422 REFERENCIA_INVALIDA")
    void asignarCategoriaInexistente() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        given(categorias.buscarParaAsignar(LIGA, CATEGORIA)).willReturn(Optional.empty());

        rechazaAsignar(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA);
    }

    @Test
    @DisplayName("RF-004: una categoría inactiva responde 409 RF-004")
    void asignarCategoriaInactiva() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        existeCategoria(false);

        rechazaAsignar(HttpStatus.CONFLICT, "RF-004");
    }

    @Test
    @DisplayName("RF-004: una categoría ya asignada responde 409 DUPLICADO")
    void asignarCategoriaRepetida() {
        existeTemporada(EstadoTemporada.CONFIGURACION);
        existeCategoria(true);
        given(grupos.categoriaAsignada(TEMPORADA, CATEGORIA)).willReturn(true);

        rechazaAsignar(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO);
    }

    @Test
    @DisplayName("RF-004, D6: asignar en una temporada activa responde 409 RF-004")
    void asignarEnTemporadaActiva() {
        existeTemporada(EstadoTemporada.ACTIVA);
        existeCategoria(true);

        rechazaAsignar(HttpStatus.CONFLICT, "RF-004");
    }

    @Test
    @DisplayName("RF-003, D6: asignar en una temporada cerrada responde 409 RF-003")
    void asignarEnTemporadaCerrada() {
        existeTemporada(EstadoTemporada.CERRADA);
        existeCategoria(true);

        rechazaAsignar(HttpStatus.CONFLICT, "RF-003");
    }

    // ── Grupos ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-005: el listado de grupos sin categoriaTemporadaId responde 400 VALIDACION")
    void listarGruposSinFiltro() {
        assertThatThrownBy(() -> servicio.listar(null))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        verifyNoInteractions(grupos);
    }

    @Test
    @DisplayName("RF-005: el listado de grupos de una categoría de temporada inexistente responde 404")
    void listarGruposDeCategoriaInexistente() {
        given(grupos.buscarCategoriaTemporada(LIGA, INEXISTENTE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.listar(INEXISTENTE)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("RF-005: crea el grupo en configuración y audita REGISTRAR_GRUPO")
    void crearGrupo() {
        existeCategoriaTemporada(EstadoTemporada.CONFIGURACION);
        given(grupos.insertarGrupo(CATEGORIA_TEMPORADA, "A")).willReturn(GRUPO);
        existeGrupo(EstadoTemporada.CONFIGURACION);

        GrupoDto grupo = servicio.crear(new CrearGrupoRequest(CATEGORIA_TEMPORADA, "  A "));

        assertThat(grupo.id()).isEqualTo(GRUPO);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_GRUPO");
        assertThat(evento.entidad()).isEqualTo("grupos");
        assertThat(evento.entidadId()).isEqualTo(GRUPO.toString());
        assertThat(evento.detalle()).containsEntry("categoriaTemporadaId", CATEGORIA_TEMPORADA.toString())
                .containsEntry("temporadaId", TEMPORADA.toString());
    }

    @Test
    @DisplayName("RF-005: una categoría de temporada inexistente responde 422 REFERENCIA_INVALIDA")
    void crearGrupoEnCategoriaInexistente() {
        given(grupos.bloquearCategoriaTemporada(LIGA, CATEGORIA_TEMPORADA)).willReturn(Optional.empty());

        rechazaCrearGrupo(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA);
    }

    @Test
    @DisplayName("RF-005, D6: crear un grupo con la temporada activa responde 409 RF-005")
    void crearGrupoEnTemporadaActiva() {
        existeCategoriaTemporada(EstadoTemporada.ACTIVA);

        rechazaCrearGrupo(HttpStatus.CONFLICT, "RF-005");
    }

    @Test
    @DisplayName("RF-003, D6: crear un grupo con la temporada cerrada responde 409 RF-003")
    void crearGrupoEnTemporadaCerrada() {
        existeCategoriaTemporada(EstadoTemporada.CERRADA);

        rechazaCrearGrupo(HttpStatus.CONFLICT, "RF-003");
    }

    @Test
    @DisplayName("RF-005: un grupo repetido en la categoría responde 409 DUPLICADO")
    void crearGrupoDuplicado() {
        existeCategoriaTemporada(EstadoTemporada.CONFIGURACION);
        given(grupos.existeNombreGrupo(CATEGORIA_TEMPORADA, "A", null)).willReturn(true);

        rechazaCrearGrupo(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO);
    }

    @Test
    @DisplayName("RF-005: un nombre de grupo en blanco responde 400 VALIDACION")
    void crearGrupoSinNombre() {
        assertThatThrownBy(() -> servicio.crear(new CrearGrupoRequest(CATEGORIA_TEMPORADA, "  ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(grupos, auditoria);
    }

    @Test
    @DisplayName("VAL-015, D1: sin EDITAR crear un grupo responde 403 sin escribir")
    void crearGrupoSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearGrupoRequest(CATEGORIA_TEMPORADA, "A")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, ErroresTecnicos.SIN_PERMISO));
        verifyNoInteractions(grupos, auditoria);
    }

    @Test
    @DisplayName("RF-005, D6: renombra el grupo con la temporada activa y audita EDITAR_GRUPO")
    void renombrarEnTemporadaActiva() {
        existeGrupo(EstadoTemporada.ACTIVA);

        servicio.actualizar(GRUPO, new ActualizarGrupoRequest("Grupo B"));

        verify(grupos).renombrarGrupo(GRUPO, "Grupo B");
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("EDITAR_GRUPO");
        assertThat(evento.detalle()).containsEntry("campos", List.of("nombre"))
                .containsEntry("temporadaId", TEMPORADA.toString());
    }

    @Test
    @DisplayName("RF-003, D6: renombrar un grupo de una temporada cerrada responde 409 RF-003")
    void renombrarEnTemporadaCerrada() {
        existeGrupo(EstadoTemporada.CERRADA);

        assertThatThrownBy(() -> servicio.actualizar(GRUPO, new ActualizarGrupoRequest("Grupo B")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(grupos, never()).renombrarGrupo(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-005: renombrar a un nombre usado en la categoría responde 409 DUPLICADO")
    void renombrarDuplicado() {
        existeGrupo(EstadoTemporada.CONFIGURACION);
        given(grupos.existeNombreGrupo(CATEGORIA_TEMPORADA, "B", GRUPO)).willReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(GRUPO, new ActualizarGrupoRequest("B")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO));
        verify(grupos, never()).renombrarGrupo(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-005: renombrar un grupo inexistente responde 404")
    void renombrarInexistente() {
        given(grupos.buscarGrupoParaActualizar(LIGA, GRUPO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(GRUPO, new ActualizarGrupoRequest("B")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-005: un PATCH sin nombre o con el mismo responde el grupo y no se audita")
    void renombrarSinCambios() {
        existeGrupo(EstadoTemporada.CONFIGURACION);

        assertThat(servicio.actualizar(GRUPO, new ActualizarGrupoRequest(null)).id()).isEqualTo(GRUPO);
        assertThat(servicio.actualizar(GRUPO, new ActualizarGrupoRequest(" A ")).id()).isEqualTo(GRUPO);
        verify(grupos, never()).renombrarGrupo(any(), any());
        verifyNoInteractions(auditoria);
    }
}
