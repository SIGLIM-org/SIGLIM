package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** Reglas de {@link CategoriaService}. Sin Spring ni base de datos. */
class CategoriaServiceTest {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.temporada:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID C = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID INEXISTENTE = UUID.fromString("99999999-9999-4999-8999-999999999999");

    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final CategoriaService servicio = new CategoriaService(categorias, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static FilaCategoria fila(UUID id, String nombre, int orden, UUID superior, boolean activa) {
        return new FilaCategoria(id, nombre, orden, superior, activa);
    }

    private static CategoriaDto dto(FilaCategoria fila) {
        return new CategoriaDto(fila.id(), fila.nombre(), TipoCategoria.LIBRE, fila.orden(),
                fila.categoriaSuperiorId() == null ? null : new ReferenciaDto(fila.categoriaSuperiorId(), "Superior"),
                fila.activa(), OffsetDateTime.parse("2030-03-01T06:00:00-06:00"));
    }

    /** El catálogo bloqueado y el detalle de cada categoría. */
    private void catalogo(FilaCategoria... filas) {
        given(categorias.bloquearCatalogo(LIGA)).willReturn(List.of(filas));
        for (FilaCategoria fila : filas) {
            given(categorias.buscar(LIGA, fila.id())).willReturn(Optional.of(dto(fila)));
        }
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

    private static ActualizarCategoriaRequest superior(UUID superiorId) {
        return new ActualizarCategoriaRequest(null, null, superiorId, null, null);
    }

    private static ActualizarCategoriaRequest activa(boolean activa) {
        return new ActualizarCategoriaRequest(null, null, null, null, activa);
    }

    private void rechaza(UUID id, ActualizarCategoriaRequest peticion, HttpStatus estado, String codigo) {
        assertThatThrownBy(() -> servicio.actualizar(id, peticion))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(estado, codigo));
        verify(categorias, never()).actualizar(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    // ── Consultas ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-099, D1: el listado exige CONSULTAR sobre la liga y pasa el filtro activa")
    void listarExigeConsultar() {
        given(categorias.listar(LIGA, true)).willReturn(List.of(dto(fila(A, "Libre", 1, null, true))));

        assertThat(servicio.listar(true)).hasSize(1);
        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
    }

    @Test
    @DisplayName("VAL-015, D1: sin CONSULTAR el listado responde 403 sin leer el catálogo")
    void listarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.listar(null))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, ErroresTecnicos.SIN_PERMISO));
        verifyNoInteractions(categorias);
    }

    @Test
    @DisplayName("RF-099: una categoría inexistente o de otra liga responde 404")
    void consultarInexistente() {
        given(categorias.buscar(LIGA, INEXISTENTE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(INEXISTENTE)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
    }

    // ── Alta ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-099: crea la categoría con el nombre sin espacios y audita REGISTRAR_CATEGORIA")
    void crear() {
        catalogo(fila(A, "Libre", 1, null, true));
        FilaCategoria nueva = fila(B, "Juvenil", 2, A, true);
        given(categorias.insertar(LIGA, "Juvenil", TipoCategoria.LIBRE, A)).willReturn(B);
        given(categorias.buscar(LIGA, B)).willReturn(Optional.of(dto(nueva)));

        CategoriaDto creada = servicio.crear(new CrearCategoriaRequest("  Juvenil ", TipoCategoria.LIBRE, A));

        assertThat(creada.id()).isEqualTo(B);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-02");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_CATEGORIA");
        assertThat(evento.entidad()).isEqualTo("categorias");
        assertThat(evento.entidadId()).isEqualTo(B.toString());
        assertThat(evento.motivo()).isNull();
        assertThat(evento.detalle()).containsEntry("orden", 2).containsEntry("categoriaSuperiorId", A.toString());
    }

    @Test
    @DisplayName("VAL-015, D1: sin EDITAR crear responde 403 sin tocar el catálogo")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearCategoriaRequest("Libre", TipoCategoria.LIBRE, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.FORBIDDEN, ErroresTecnicos.SIN_PERMISO));
        verifyNoInteractions(categorias, auditoria);
    }

    @Test
    @DisplayName("RF-099: un nombre en blanco responde 400 VALIDACION sin bloquear el catálogo")
    void crearSinNombre() {
        assertThatThrownBy(() -> servicio.crear(new CrearCategoriaRequest("   ", TipoCategoria.LIBRE, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(categorias, auditoria);
    }

    @Test
    @DisplayName("RF-099, D2: una superior inexistente o de otra liga responde 422 REFERENCIA_INVALIDA")
    void crearConSuperiorInexistente() {
        catalogo(fila(A, "Libre", 1, null, true));

        assertThatThrownBy(() -> servicio.crear(new CrearCategoriaRequest("Juvenil", TipoCategoria.LIBRE,
                INEXISTENTE)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
        verify(categorias, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099, D2: una superior inactiva responde 409 RF-099")
    void crearConSuperiorInactiva() {
        catalogo(fila(A, "Libre", 1, null, false));

        assertThatThrownBy(() -> servicio.crear(new CrearCategoriaRequest("Juvenil", TipoCategoria.LIBRE, A)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-099"));
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099: un nombre ya usado en la liga responde 409 DUPLICADO")
    void crearDuplicada() {
        catalogo(fila(A, "Libre", 1, null, true));
        given(categorias.existeNombre(LIGA, "Libre", null)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearCategoriaRequest("Libre", TipoCategoria.LIBRE, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO));
        verify(categorias, never()).insertar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    // ── Jerarquía (D2) ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-099, D2: A → B → A forma un ciclo y responde 409 RF-099")
    void cicloDeDos() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, A, true));

        rechaza(A, superior(B), HttpStatus.CONFLICT, "RF-099");
    }

    @Test
    @DisplayName("RF-099, D2: A → B → C → A forma un ciclo y responde 409 RF-099 con la cadena")
    void cicloDeTres() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, C, true), fila(C, "Infantil", 3, A, true));

        assertThatThrownBy(() -> servicio.actualizar(A, superior(B)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-099"))
                .hasMessageContaining("Libre → Juvenil → Infantil → Libre");
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099, D2: una categoría no puede ser su propia superior (409 RF-099)")
    void propiaSuperior() {
        catalogo(fila(A, "Libre", 1, null, true));

        rechaza(A, superior(A), HttpStatus.CONFLICT, "RF-099");
    }

    @Test
    @DisplayName("RF-099, D2: cambiar a una superior inexistente responde 422 REFERENCIA_INVALIDA")
    void actualizarConSuperiorInexistente() {
        catalogo(fila(A, "Libre", 1, null, true));

        rechaza(A, superior(INEXISTENTE), HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA);
    }

    @Test
    @DisplayName("RF-099, D2: cambiar a una superior inactiva responde 409 RF-099")
    void actualizarConSuperiorInactiva() {
        catalogo(fila(A, "Libre", 1, null, false), fila(B, "Juvenil", 2, null, true));

        rechaza(B, superior(A), HttpStatus.CONFLICT, "RF-099");
    }

    @Test
    @DisplayName("RF-099: categoriaSuperiorId y quitarSuperior juntos responden 400 VALIDACION")
    void superiorYQuitarSuperior() {
        assertThatThrownBy(() -> servicio.actualizar(A, new ActualizarCategoriaRequest(null, null, B, true, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(categorias, auditoria);
    }

    @Test
    @DisplayName("RF-099: editar una categoría inexistente responde 404")
    void actualizarInexistente() {
        catalogo(fila(A, "Libre", 1, null, true));

        assertThatThrownBy(() -> servicio.actualizar(INEXISTENTE, activa(false)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099: asigna una superior válida y audita EDITAR_CATEGORIA con los campos cambiados")
    void cambiarSuperior() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, null, true));

        servicio.actualizar(B, superior(A));

        verify(categorias).actualizar(B, "Juvenil", TipoCategoria.LIBRE, A, true);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("EDITAR_CATEGORIA");
        assertThat(evento.entidadId()).isEqualTo(B.toString());
        assertThat(evento.detalle()).containsEntry("campos", List.of("categoriaSuperiorId"))
                .containsEntry("categoriaSuperiorId", A.toString());
    }

    @Test
    @DisplayName("RF-099: un PATCH sin cambios responde la categoría y no se audita")
    void actualizarSinCambios() {
        catalogo(fila(A, "Libre", 1, null, true));

        CategoriaDto resultado = servicio.actualizar(A, new ActualizarCategoriaRequest("Libre", TipoCategoria.LIBRE,
                null, null, true));

        assertThat(resultado.id()).isEqualTo(A);
        verify(categorias, never()).actualizar(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099: renombrar a un nombre ya usado responde 409 DUPLICADO")
    void renombrarDuplicado() {
        catalogo(fila(A, "Libre", 1, null, true));
        given(categorias.existeNombre(LIGA, "Veteranos", A)).willReturn(true);

        rechaza(A, new ActualizarCategoriaRequest("Veteranos", null, null, null, null), HttpStatus.CONFLICT,
                ErroresTecnicos.DUPLICADO);
    }

    // ── Jerarquía en uso (D5) ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-110, D5: cambiar la superior de una categoría en una temporada activa responde 409 RF-110")
    void cambiarSuperiorEnTemporadaActiva() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, null, true));
        given(categorias.temporadasConCategoria(B, List.of(EstadoTemporada.ACTIVA))).willReturn(List.of("Temporada 2030"));

        rechaza(B, superior(A), HttpStatus.CONFLICT, "RF-110");
    }

    @Test
    @DisplayName("RF-110, D5: quitar la superior de una categoría en una temporada activa responde 409 RF-110")
    void quitarSuperiorEnTemporadaActiva() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, A, true));
        given(categorias.temporadasConCategoria(B, List.of(EstadoTemporada.ACTIVA))).willReturn(List.of("Temporada 2030"));

        rechaza(B, new ActualizarCategoriaRequest(null, null, null, true, null), HttpStatus.CONFLICT, "RF-110");
    }

    // ── Desactivar y reactivar (D4) ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-099, D4: desactivar una categoría de una temporada en configuración responde 409 RF-099")
    void desactivarEnUso() {
        catalogo(fila(A, "Libre", 1, null, true));
        given(categorias.temporadasConCategoria(A, List.of(EstadoTemporada.CONFIGURACION, EstadoTemporada.ACTIVA)))
                .willReturn(List.of("Temporada 2031"));

        rechaza(A, activa(false), HttpStatus.CONFLICT, "RF-099");
    }

    @Test
    @DisplayName("RF-099, D4: desactivar la superior de otra categoría activa responde 409 RF-099")
    void desactivarSuperiorDeActiva() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, A, true));
        given(categorias.temporadasConCategoria(eq(A), anyList())).willReturn(List.of());

        rechaza(A, activa(false), HttpStatus.CONFLICT, "RF-099");
    }

    @Test
    @DisplayName("RF-099, D4: se desactiva si solo es superior de categorías inactivas y no está en uso")
    void desactivar() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, A, false));
        given(categorias.temporadasConCategoria(eq(A), anyList())).willReturn(List.of());

        servicio.actualizar(A, activa(false));

        verify(categorias).actualizar(A, "Libre", TipoCategoria.LIBRE, null, false);
        assertThat(eventoAuditado().detalle()).containsEntry("campos", List.of("activa")).containsEntry("activa", false);
    }

    @Test
    @DisplayName("RF-099, D4: reactivar una categoría sin superior procede sin revisar sus temporadas")
    void reactivar() {
        catalogo(fila(A, "Libre", 1, null, false));

        servicio.actualizar(A, activa(true));

        verify(categorias).actualizar(A, "Libre", TipoCategoria.LIBRE, null, true);
        verify(categorias, never()).temporadasConCategoria(any(), anyList());
        assertThat(eventoAuditado().accion()).isEqualTo("EDITAR_CATEGORIA");
    }

    @Test
    @DisplayName("RF-099, D2: reactivar una categoría cuya superior está inactiva responde 409 RF-099")
    void reactivarConSuperiorInactiva() {
        catalogo(fila(A, "Libre", 1, null, false), fila(B, "Juvenil", 2, A, false));

        assertThatThrownBy(() -> servicio.actualizar(B, activa(true)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-099"))
                .hasMessageContaining("«Libre» está inactiva: reactívala primero o quita la superior de «Juvenil»");
        verify(categorias, never()).actualizar(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099, D2: reactivar quitando en el mismo PATCH la superior inactiva procede")
    void reactivarQuitandoSuperiorInactiva() {
        catalogo(fila(A, "Libre", 1, null, false), fila(B, "Juvenil", 2, A, false));

        servicio.actualizar(B, new ActualizarCategoriaRequest(null, null, null, true, true));

        verify(categorias).actualizar(B, "Juvenil", TipoCategoria.LIBRE, null, true);
        assertThat(eventoAuditado().detalle()).containsEntry("campos", List.of("categoriaSuperiorId", "activa"));
    }

    // ── Reordenamiento (D3) ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-099, D3: reordena todas las categorías y audita una sola vez REORDENAR_CATEGORIAS")
    void reordenar() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, null, false), fila(C, "Infantil", 3, null, true));

        servicio.reordenar(new ReordenarCategoriasRequest(List.of(C, A, B)));

        verify(categorias).reordenar(LIGA, List.of(C, A, B));
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REORDENAR_CATEGORIAS");
        assertThat(evento.entidad()).isEqualTo("categorias");
        assertThat(evento.entidadId()).isNull();
        assertThat(evento.detalle()).containsEntry("ids", List.of(C.toString(), A.toString(), B.toString()));
        verify(categorias).listar(LIGA, null);
    }

    @Test
    @DisplayName("RF-099, D3: si el orden pedido es el actual, no escribe ni audita")
    void reordenarSinCambios() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, null, true));

        servicio.reordenar(new ReordenarCategoriasRequest(List.of(A, B)));

        verify(categorias, never()).reordenar(any(), anyList());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099, D3: si falta una categoría responde 400 VALIDACION con los faltantes")
    void reordenarConFaltantes() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, null, false));

        assertThatThrownBy(() -> servicio.reordenar(new ReordenarCategoriasRequest(List.of(A))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION).accept(excepcion);
                    assertThat(excepcion.propiedades()).containsEntry("faltantes", List.of(B));
                });
        verify(categorias, never()).reordenar(any(), anyList());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099, D3: si sobra una categoría responde 400 VALIDACION con los sobrantes")
    void reordenarConSobrantes() {
        catalogo(fila(A, "Libre", 1, null, true));

        assertThatThrownBy(() -> servicio.reordenar(new ReordenarCategoriasRequest(List.of(A, INEXISTENTE))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION).accept(excepcion);
                    assertThat(excepcion.propiedades()).containsEntry("sobrantes", List.of(INEXISTENTE));
                });
        verify(categorias, never()).reordenar(any(), anyList());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099, D3: si se repite una categoría responde 400 VALIDACION con los repetidos")
    void reordenarConRepetidos() {
        catalogo(fila(A, "Libre", 1, null, true), fila(B, "Juvenil", 2, null, true));

        assertThatThrownBy(() -> servicio.reordenar(new ReordenarCategoriasRequest(List.of(A, B, A))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION).accept(excepcion);
                    assertThat(excepcion.propiedades()).containsEntry("repetidos", List.of(A));
                });
        verify(categorias, never()).reordenar(any(), anyList());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-099, D3: una lista con elementos nulos responde 400 VALIDACION")
    void reordenarConNulos() {
        List<UUID> ids = new ArrayList<>();
        ids.add(A);
        ids.add(null);

        assertThatThrownBy(() -> servicio.reordenar(new ReordenarCategoriasRequest(ids)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verifyNoInteractions(categorias, auditoria);
    }
}
