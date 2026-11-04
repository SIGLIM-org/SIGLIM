package mx.tecnm.morelia.siglim.disciplina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.disciplina.ArticulosRepository.DatosArticulo;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Reglas de {@link ArticulosService}. Sin Spring ni base de datos. */
class ArticulosServiceTest {

    private static final String EDITAR = "disciplina.catalogo:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ArticuloDto BAJA = new ArticuloDto(ID, "DEMO-1", "Baja por riña", TipoSancion.BAJA,
            SujetoSancion.EQUIPO, EfectoSancion.HACIA_ADELANTE, false, null, null, null, true, true, true);
    private static final ArticuloDto SUSPENSION = new ArticuloDto(ID, "A-1", "Suspensión por expulsión",
            TipoSancion.SUSPENSION_PARTIDOS, SujetoSancion.JUGADOR, EfectoSancion.HACIA_ADELANTE, false,
            3, null, null, true, true, false);
    private static final ArticuloDto SUSPENSION_GRAVE = new ArticuloDto(ID, "A-1", "Suspensión grave",
            TipoSancion.SUSPENSION_PARTIDOS, SujetoSancion.JUGADOR, EfectoSancion.HACIA_ADELANTE, false,
            3, null, null, true, true, false);
    private static final ArticuloDto MULTA = new ArticuloDto(ID, "A-1", "Suspensión por expulsión",
            TipoSancion.MULTA, SujetoSancion.JUGADOR, EfectoSancion.HACIA_ADELANTE, false,
            null, null, new BigDecimal("500.00"), true, true, false);

    private final ArticulosRepository articulos = mock(ArticulosRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ArticulosService servicio = new ArticulosService(articulos, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

    private static CrearArticuloRequest crear(String numero, String descripcion, TipoSancion tipo,
            SujetoSancion sujeto, Integer partidos, Integer puntos, BigDecimal monto) {
        return new CrearArticuloRequest(numero, descripcion, tipo, sujeto, null, null, partidos, puntos, monto,
                null);
    }

    private static ActualizarArticuloRequest cambiarDescripcion(String descripcion) {
        return new ActualizarArticuloRequest(null, descripcion, null, null, null, null, null, null, null, null,
                null);
    }

    private static ActualizarArticuloRequest cambiarTipo(TipoSancion tipo, BigDecimal monto) {
        return new ActualizarArticuloRequest(null, null, tipo, null, null, null, null, null, monto, null, null);
    }

    private EventoAuditoria capturarAuditoria() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private DatosArticulo capturarDatosInsertados() {
        ArgumentCaptor<DatosArticulo> datos = ArgumentCaptor.forClass(DatosArticulo.class);
        verify(articulos).insertar(eq(LIGA), datos.capture());
        return datos.getValue();
    }

    // ── Permisos y ámbito ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADR-0007: el listado exige el permiso de catálogo sobre la liga")
    void listarExigePermisoDeLiga() {
        given(articulos.listar(LIGA, true)).willReturn(List.of(BAJA));

        assertThat(servicio.listar(true)).containsExactly(BAJA);

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
    }

    @Test
    @DisplayName("VAL-015: sin permiso de catálogo no registra nada")
    void crearSinPermisoNoEscribe() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(crear("DEMO-1", "Baja por riña", TipoSancion.BAJA,
                SujetoSancion.EQUIPO, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(articulos);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin permiso de catálogo no edita nada")
    void actualizarSinPermisoNoEscribe() {
        given(articulos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(BAJA));
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarDescripcion("Baja por riña grave")))
                .isInstanceOf(ReglaNegocioException.class);

        verify(articulos, never()).actualizar(any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    // ── Alta ──────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-129: aplica los valores por defecto de efecto, aplicación automática y partidos restantes")
    void crearAplicaLosValoresPorDefecto() {
        given(articulos.existeNumero(LIGA, "DEMO-1", null)).willReturn(false);
        given(articulos.insertar(eq(LIGA), any(DatosArticulo.class))).willReturn(ID);
        given(articulos.buscar(LIGA, ID)).willReturn(Optional.of(BAJA));

        ArticuloDto creado = servicio.crear(crear(" DEMO-1 ", " Baja por riña ", TipoSancion.BAJA,
                SujetoSancion.EQUIPO, null, null, null));

        assertThat(creado).isEqualTo(BAJA);
        DatosArticulo datos = capturarDatosInsertados();
        assertThat(datos.efecto()).isEqualTo(EfectoSancion.HACIA_ADELANTE);
        assertThat(datos.aplicacionAutomatica()).isFalse();
        assertThat(datos.otorgaDefaultRestantes()).isTrue();
        assertThat(datos.partidosSuspension()).isNull();
        assertThat(datos.puntos()).isNull();
        assertThat(datos.montoMulta()).isNull();
        EventoAuditoria evento = capturarAuditoria();
        assertThat(evento.modulo()).isEqualTo("M-09");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_ARTICULO");
        assertThat(evento.entidad()).isEqualTo("articulos");
    }

    @Test
    @DisplayName("RF-129: una suspensión guarda sus partidos y ningún otro valor")
    void crearSuspensionConPartidos() {
        given(articulos.existeNumero(LIGA, "A-1", null)).willReturn(false);
        given(articulos.insertar(eq(LIGA), any(DatosArticulo.class))).willReturn(ID);
        given(articulos.buscar(LIGA, ID)).willReturn(Optional.of(SUSPENSION));

        servicio.crear(crear("A-1", "Suspensión por expulsión", TipoSancion.SUSPENSION_PARTIDOS,
                SujetoSancion.JUGADOR, 3, null, null));

        DatosArticulo datos = capturarDatosInsertados();
        assertThat(datos.partidosSuspension()).isEqualTo(3);
        assertThat(datos.puntos()).isNull();
        assertThat(datos.montoMulta()).isNull();
    }

    @Test
    @DisplayName("RF-129: una suspensión sin partidos responde 400 VALIDACION")
    void crearSuspensionSinPartidos() {
        assertThatThrownBy(() -> servicio.crear(crear("A-1", "Suspensión", TipoSancion.SUSPENSION_PARTIDOS,
                SujetoSancion.JUGADOR, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("partidos de suspensión");

        verifyNoInteractions(articulos);
    }

    @Test
    @DisplayName("RF-129: una pérdida de puntos sin puntos responde 400 VALIDACION")
    void crearPerdidaPuntosSinPuntos() {
        assertThatThrownBy(() -> servicio.crear(crear("A-2", "Pérdida de puntos", TipoSancion.PERDIDA_PUNTOS,
                SujetoSancion.EQUIPO, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("puntos");

        verifyNoInteractions(articulos);
    }

    @Test
    @DisplayName("RF-129: una multa sin monto responde 400 VALIDACION")
    void crearMultaSinMonto() {
        assertThatThrownBy(() -> servicio.crear(crear("A-3", "Multa", TipoSancion.MULTA,
                SujetoSancion.CLUB, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("monto");

        verifyNoInteractions(articulos);
    }

    @Test
    @DisplayName("RF-129: un valor de otro tipo de sanción responde 400 VALIDACION")
    void crearConValoresDeOtroTipo() {
        assertThatThrownBy(() -> servicio.crear(crear("A-3", "Multa", TipoSancion.MULTA,
                SujetoSancion.CLUB, 3, null, new BigDecimal("500.00"))))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no lleves");

        verifyNoInteractions(articulos);
    }

    @Test
    @DisplayName("RF-129: un número repetido en la liga responde 409 DUPLICADO")
    void crearConNumeroDuplicado() {
        given(articulos.existeNumero(LIGA, "DEMO-1", null)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(crear("DEMO-1", "Baja por riña", TipoSancion.BAJA,
                SujetoSancion.EQUIPO, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Ya existe");

        verify(articulos, never()).insertar(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-129: un número vacío responde 400 VALIDACION")
    void crearConNumeroVacio() {
        assertThatThrownBy(() -> servicio.crear(crear("  ", "Baja por riña", TipoSancion.BAJA,
                SujetoSancion.EQUIPO, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("obligatorio");

        verifyNoInteractions(articulos);
    }

    // ── Edición ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-129: la edición conserva lo ausente y audita los campos cambiados")
    void actualizarConservaLoAusente() {
        given(articulos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(SUSPENSION));
        given(articulos.buscar(LIGA, ID)).willReturn(Optional.of(SUSPENSION_GRAVE));

        ArticuloDto resultado = servicio.actualizar(ID, cambiarDescripcion(" Suspensión grave "));

        assertThat(resultado).isEqualTo(SUSPENSION_GRAVE);
        ArgumentCaptor<DatosArticulo> datos = ArgumentCaptor.forClass(DatosArticulo.class);
        verify(articulos).actualizar(eq(ID), datos.capture(), eq(true));
        assertThat(datos.getValue().tipoSancion()).isEqualTo(TipoSancion.SUSPENSION_PARTIDOS);
        assertThat(datos.getValue().sujeto()).isEqualTo(SujetoSancion.JUGADOR);
        assertThat(datos.getValue().partidosSuspension()).isEqualTo(3);
        assertThat(datos.getValue().otorgaDefaultRestantes()).isTrue();
        EventoAuditoria evento = capturarAuditoria();
        assertThat(evento.accion()).isEqualTo("EDITAR_ARTICULO");
        assertThat(evento.detalle()).containsEntry("campos", List.of("descripcion"));
    }

    @Test
    @DisplayName("RF-129: cambiar de tipo exige el valor del nuevo tipo")
    void actualizarCambiaTipoExigeElNuevoValor() {
        given(articulos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(SUSPENSION));

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarTipo(TipoSancion.MULTA, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("monto");

        verify(articulos, never()).actualizar(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("RF-129: cambiar de tipo limpia los valores del anterior")
    void actualizarCambiaTipoLimpiaLosAnteriores() {
        given(articulos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(SUSPENSION));
        given(articulos.buscar(LIGA, ID)).willReturn(Optional.of(MULTA));

        servicio.actualizar(ID, cambiarTipo(TipoSancion.MULTA, new BigDecimal("500.00")));

        ArgumentCaptor<DatosArticulo> datos = ArgumentCaptor.forClass(DatosArticulo.class);
        verify(articulos).actualizar(eq(ID), datos.capture(), eq(true));
        assertThat(datos.getValue().partidosSuspension()).isNull();
        assertThat(datos.getValue().montoMulta()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("RF-129: cambiar el número a uno repetido responde 409 DUPLICADO")
    void actualizarConNumeroDuplicado() {
        given(articulos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(BAJA));
        given(articulos.existeNumero(LIGA, "DEMO-2", ID)).willReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarArticuloRequest("DEMO-2", null, null,
                null, null, null, null, null, null, null, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Ya existe");

        verify(articulos, never()).actualizar(any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-024: un artículo inexistente responde 404 sin evaluar el permiso")
    void actualizarInexistente() {
        given(articulos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarDescripcion("Baja por riña grave")))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("RF-129: una edición sin cambios no escribe ni audita")
    void actualizarSinCambiosNoEscribe() {
        given(articulos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(BAJA));

        assertThat(servicio.actualizar(ID, new ActualizarArticuloRequest(null, null, null, null, null, null,
                null, null, null, null, null))).isEqualTo(BAJA);

        verify(articulos, never()).actualizar(any(), any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }
}
