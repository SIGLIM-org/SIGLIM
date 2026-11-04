package mx.tecnm.morelia.siglim.disciplina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reglas de {@link TiposIncidenciaService}. Sin Spring ni base de datos. */
class TiposIncidenciaServiceTest {

    private static final String EDITAR = "disciplina.catalogo:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final long ID = 7L;
    private static final TipoIncidenciaDto TIPO =
            new TipoIncidenciaDto(ID, "EXPULSION", "Expulsión", true, true, true);

    private final TiposIncidenciaRepository tipos = mock(TiposIncidenciaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final TiposIncidenciaService servicio = new TiposIncidenciaService(tipos, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    private EventoAuditoria capturarAuditoria() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    // ── Permisos y ámbito ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADR-0007: el listado exige el permiso de catálogo sobre la liga")
    void listarExigePermisoDeLiga() {
        given(tipos.listar(LIGA, true)).willReturn(List.of(TIPO));

        assertThat(servicio.listar(true)).containsExactly(TIPO);

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
    }

    @Test
    @DisplayName("VAL-015: sin permiso de catálogo no registra nada")
    void crearSinPermisoNoEscribe() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(
                new CrearTipoIncidenciaRequest("EXPULSION", "Expulsión", true)))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(tipos);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin permiso de catálogo no edita nada")
    void actualizarSinPermisoNoEscribe() {
        given(tipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(TIPO));
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.actualizar(ID,
                new ActualizarTipoIncidenciaRequest(null, "Expulsión grave", null, null)))
                .isInstanceOf(ReglaNegocioException.class);

        verify(tipos, never()).actualizar(anyLong(), anyString(), anyString(), anyBoolean(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    // ── Alta ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-128: registra el tipo y lo audita con el módulo M-09")
    void crearRegistraYAudita() {
        given(tipos.existeCodigo(LIGA, "EXPULSION", null)).willReturn(false);
        given(tipos.insertar(LIGA, "EXPULSION", "Expulsión", true)).willReturn(ID);
        given(tipos.buscar(LIGA, ID)).willReturn(Optional.of(TIPO));

        TipoIncidenciaDto creado = servicio.crear(
                new CrearTipoIncidenciaRequest(" EXPULSION ", " Expulsión ", true));

        assertThat(creado).isEqualTo(TIPO);
        EventoAuditoria evento = capturarAuditoria();
        assertThat(evento.modulo()).isEqualTo("M-09");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_TIPO_INCIDENCIA");
        assertThat(evento.entidad()).isEqualTo("tipos_incidencia");
        assertThat(evento.entidadId()).isEqualTo(Long.toString(ID));
    }

    @Test
    @DisplayName("RF-128: un código repetido en la liga responde 409 DUPLICADO")
    void crearConCodigoDuplicado() {
        given(tipos.existeCodigo(LIGA, "RINA", null)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearTipoIncidenciaRequest("RINA", "Riña", true)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Ya existe");

        verify(tipos, never()).insertar(any(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-128: un código vacío responde 400 VALIDACION")
    void crearConCodigoVacio() {
        assertThatThrownBy(() -> servicio.crear(new CrearTipoIncidenciaRequest("  ", "Riña", true)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("obligatorio");

        verifyNoInteractions(tipos);
    }

    // ── Edición ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-128: la edición conserva lo ausente y audita los campos cambiados")
    void actualizarConservaLoAusente() {
        TipoIncidenciaDto editado =
                new TipoIncidenciaDto(ID, "EXPULSION", "Expulsión grave", true, true, true);
        given(tipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(TIPO));
        given(tipos.buscar(LIGA, ID)).willReturn(Optional.of(editado));

        TipoIncidenciaDto resultado = servicio.actualizar(ID,
                new ActualizarTipoIncidenciaRequest(null, " Expulsión grave ", null, null));

        assertThat(resultado).isEqualTo(editado);
        verify(tipos).actualizar(ID, "EXPULSION", "Expulsión grave", true, true);
        EventoAuditoria evento = capturarAuditoria();
        assertThat(evento.accion()).isEqualTo("EDITAR_TIPO_INCIDENCIA");
        assertThat(evento.detalle()).containsEntry("campos", List.of("nombre"));
    }

    @Test
    @DisplayName("RF-128: desactivar un tipo no lo borra y lo deja inactivo")
    void actualizarDesactiva() {
        TipoIncidenciaDto inactivo =
                new TipoIncidenciaDto(ID, "EXPULSION", "Expulsión", true, false, true);
        given(tipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(TIPO));
        given(tipos.buscar(LIGA, ID)).willReturn(Optional.of(inactivo));

        TipoIncidenciaDto resultado = servicio.actualizar(ID,
                new ActualizarTipoIncidenciaRequest(null, null, null, false));

        assertThat(resultado.activo()).isFalse();
        verify(tipos).actualizar(ID, "EXPULSION", "Expulsión", true, false);
        assertThat(capturarAuditoria().detalle()).containsEntry("campos", List.of("activo"));
    }

    @Test
    @DisplayName("RF-128: cambiar el código a uno repetido responde 409 DUPLICADO")
    void actualizarConCodigoDuplicado() {
        given(tipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(TIPO));
        given(tipos.existeCodigo(LIGA, "RINA", ID)).willReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID,
                new ActualizarTipoIncidenciaRequest("RINA", null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Ya existe");

        verify(tipos, never()).actualizar(anyLong(), anyString(), anyString(), anyBoolean(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-024: un tipo inexistente responde 404 sin evaluar el permiso")
    void actualizarInexistente() {
        given(tipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID,
                new ActualizarTipoIncidenciaRequest(null, "Expulsión grave", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("RF-128: una edición sin cambios no escribe ni audita")
    void actualizarSinCambiosNoEscribe() {
        given(tipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(TIPO));

        assertThat(servicio.actualizar(ID, new ActualizarTipoIncidenciaRequest(null, null, null, null)))
                .isEqualTo(TIPO);

        verify(tipos, never()).actualizar(anyLong(), anyString(), anyString(), anyBoolean(), anyBoolean());
        verifyNoInteractions(auditoria);
    }
}
