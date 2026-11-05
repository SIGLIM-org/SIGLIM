package mx.tecnm.morelia.siglim.canchas;

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
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** Reglas de {@link SedeService}. Sin Spring ni base de datos. */
class SedeServiceTest {

    private static final String CONSULTAR = "canchas.cancha:CONSULTAR";
    private static final String EDITAR = "canchas.cancha:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final SedeDto NORTE = new SedeDto(ID, "Unidad Norte", "Av. Siempre Viva 742");

    private final SedeRepository sedes = mock(SedeRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final SedeService servicio = new SedeService(sedes, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

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

    private void sedeExistente() {
        given(sedes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(NORTE));
        given(sedes.buscar(LIGA, ID)).willReturn(Optional.of(NORTE));
    }

    // ── Listado ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-028: el listado exige CONSULTAR con cualquier ámbito y devuelve las sedes de la liga (D1)")
    void listar() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(false, Set.of(UUID.randomUUID()),
                false));
        given(sedes.listar(LIGA)).willReturn(List.of(NORTE));

        assertThat(servicio.listar()).containsExactly(NORTE);
        verify(autorizacion).alcance(CONSULTAR);
    }

    @Test
    @DisplayName("VAL-015: sin CONSULTAR el listado se rechaza sin leer")
    void listarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).alcance(CONSULTAR);

        assertThatThrownBy(servicio::listar)
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(sedes);
    }

    // ── Alta ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-028: crear guarda el nombre sin espacios en los extremos y audita sin la dirección")
    void crear() {
        given(sedes.insertar(LIGA, "Unidad Norte", "Av. Siempre Viva 742")).willReturn(ID);
        given(sedes.buscar(LIGA, ID)).willReturn(Optional.of(NORTE));

        SedeDto sede = servicio.crear(new CrearSedeRequest("  Unidad Norte ", " Av. Siempre Viva 742 "));

        assertThat(sede).isEqualTo(NORTE);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-06");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_SEDE");
        assertThat(evento.entidad()).isEqualTo("sedes");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
        assertThat(evento.detalle()).containsEntry("campos", List.of("nombre", "direccion"));
        assertThat(evento.detalle().toString()).doesNotContain("Siempre Viva");
    }

    @Test
    @DisplayName("RF-028: una dirección en blanco se guarda vacía")
    void crearConDireccionEnBlanco() {
        given(sedes.insertar(LIGA, "Unidad Sur", null)).willReturn(ID);
        given(sedes.buscar(LIGA, ID)).willReturn(Optional.of(new SedeDto(ID, "Unidad Sur", null)));

        servicio.crear(new CrearSedeRequest("Unidad Sur", "   "));

        verify(sedes).insertar(LIGA, "Unidad Sur", null);
    }

    @Test
    @DisplayName("RF-028: un nombre repetido en la liga responde 409 DUPLICADO sin escribir")
    void crearDuplicada() {
        given(sedes.existeNombre(LIGA, "Unidad Norte", null)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearSedeRequest("Unidad Norte", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"))
                .hasMessage("Ya existe una sede llamada «Unidad Norte» en la liga.");
        verify(sedes, never()).insertar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin EDITAR no registra nada")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearSedeRequest("Unidad Norte", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(sedes, auditoria);
    }

    // ── Edición ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-028: renombrar a un nombre libre escribe y audita el campo nombre")
    void renombrar() {
        sedeExistente();

        servicio.actualizar(ID, new ActualizarSedeRequest(" Unidad Oriente ", null));

        verify(sedes).existeNombre(LIGA, "Unidad Oriente", ID);
        verify(sedes).actualizar(ID, "Unidad Oriente", "Av. Siempre Viva 742");
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("EDITAR_SEDE");
        assertThat(evento.detalle()).containsEntry("campos", List.of("nombre"));
    }

    @Test
    @DisplayName("RF-028: renombrar a un nombre ocupado responde 409 DUPLICADO sin escribir")
    void renombrarDuplicado() {
        sedeExistente();
        given(sedes.existeNombre(LIGA, "Unidad Sur", ID)).willReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarSedeRequest("Unidad Sur", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        verify(sedes, never()).actualizar(any(), any(), any());
    }

    @Test
    @DisplayName("RF-028: renombrar al mismo nombre no busca conflicto ni escribe")
    void renombrarAlMismoNombre() {
        sedeExistente();

        SedeDto sede = servicio.actualizar(ID, new ActualizarSedeRequest("Unidad Norte ", null));

        assertThat(sede).isEqualTo(NORTE);
        verify(sedes, never()).existeNombre(any(), any(), any());
        verify(sedes, never()).actualizar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-093: un PATCH sin cambios no escribe ni audita")
    void sinCambios() {
        sedeExistente();

        servicio.actualizar(ID, new ActualizarSedeRequest(null, null));

        verify(sedes, never()).actualizar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-028: una dirección en blanco se vacía y se audita sin su valor")
    void vaciarDireccion() {
        sedeExistente();

        servicio.actualizar(ID, new ActualizarSedeRequest(null, " "));

        verify(sedes).actualizar(ID, "Unidad Norte", null);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.detalle()).containsEntry("campos", List.of("direccion"));
        assertThat(evento.detalle().toString()).doesNotContain("Siempre Viva");
    }

    @Test
    @DisplayName("RF-028: un nombre en blanco responde 400 VALIDACION")
    void nombreEnBlanco() {
        sedeExistente();

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarSedeRequest("   ", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(sedes, never()).actualizar(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: una sede inexistente o de otra liga responde 404 sin evaluar el permiso")
    void sedeInexistente() {
        given(sedes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarSedeRequest("Otra", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("VAL-015: sin EDITAR no edita")
    void actualizarSinPermiso() {
        sedeExistente();
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarSedeRequest("Otra", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(sedes, never()).actualizar(any(), any(), any());
    }
}
