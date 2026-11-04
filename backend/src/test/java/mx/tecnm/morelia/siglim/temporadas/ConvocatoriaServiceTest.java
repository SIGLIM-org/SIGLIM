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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.ConvocatoriaRepository.DatosConvocatoria;
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.DatosTemporada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** Reglas de {@link ConvocatoriaService}. Sin Spring ni base de datos. */
class ConvocatoriaServiceTest {

    private static final String EDITAR = "temporadas.convocatoria:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CONVOCATORIA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final LocalDate INICIO = LocalDate.parse("2030-08-01");
    private static final LocalDate FIN = LocalDate.parse("2030-12-15");
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.of(2030, 3, 1, 6, 0, 0, 0, ZoneOffset.UTC);
    private static final String ENLACE = "https://liga.mx/convocatoria-2030.pdf";

    private final ConvocatoriaRepository convocatorias = mock(ConvocatoriaRepository.class);
    private final TemporadaRepository temporadas = mock(TemporadaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ConvocatoriaService servicio = new ConvocatoriaService(convocatorias, temporadas,
            autorizacion, auditoria, new SiglimPropiedades(null, null, null, LIGA));

    private static DatosTemporada temporada(EstadoTemporada estado) {
        return new DatosTemporada(ID, "Temporada 2030", INICIO, FIN, estado, CREADO_EN, 1, 1, 1, null);
    }

    private static DatosConvocatoria convocatoria(String referencia, String version, String documentoUrl) {
        return new DatosConvocatoria(CONVOCATORIA, ID, referencia, version, documentoUrl, CREADO_EN);
    }

    private static ReemplazarConvocatoriaRequest peticion(String referencia, String version, String enlace) {
        return new ReemplazarConvocatoriaRequest(referencia, version, enlace);
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

    @Test
    @DisplayName("RF-002: asocia la convocatoria, recorta los textos y audita con la temporada")
    void asociaYAudita() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION)));
        given(convocatorias.guardar(ID, "Convocatoria 2030", "2026-1", ENLACE)).willReturn(CONVOCATORIA);
        given(convocatorias.buscar(LIGA, ID)).willReturn(Optional.empty(),
                Optional.of(convocatoria("Convocatoria 2030", "2026-1", ENLACE)));

        ConvocatoriaDto resultado = servicio.reemplazar(ID,
                peticion("  Convocatoria 2030  ", " 2026-1 ", " " + ENLACE + " "));

        assertThat(resultado.referencia()).isEqualTo("Convocatoria 2030");
        assertThat(resultado.version()).isEqualTo("2026-1");
        assertThat(resultado.documentoUrl()).isEqualTo(ENLACE);
        assertThat(resultado.temporadaId()).isEqualTo(ID);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("ASOCIAR_CONVOCATORIA");
        assertThat(evento.entidad()).isEqualTo("convocatorias");
        assertThat(evento.entidadId()).isEqualTo(CONVOCATORIA.toString());
        assertThat(evento.detalle()).containsEntry("temporadaId", ID.toString());
        assertThat(evento.detalle()).containsEntry("referencia", "Convocatoria 2030");
        assertThat(evento.detalle()).containsEntry("version", "2026-1");
        assertThat(evento.detalle()).containsEntry("documentoUrl", ENLACE);
        assertThat(evento.detalle()).as("no había convocatoria anterior")
                .doesNotContainKeys("referenciaAnterior", "versionAnterior", "documentoUrlAnterior");
    }

    @Test
    @DisplayName("RF-093: al reemplazar, la auditoría conserva la convocatoria anterior")
    void reemplazoConservaLaAnterior() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION)));
        given(convocatorias.guardar(ID, "Convocatoria 2031", null, null)).willReturn(CONVOCATORIA);
        given(convocatorias.buscar(LIGA, ID))
                .willReturn(Optional.of(convocatoria("Convocatoria 2030", "2026-1", ENLACE)),
                        Optional.of(convocatoria("Convocatoria 2031", null, null)));

        servicio.reemplazar(ID, peticion("Convocatoria 2031", null, null));

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.detalle()).containsEntry("referencia", "Convocatoria 2031");
        assertThat(evento.detalle()).containsEntry("referenciaAnterior", "Convocatoria 2030");
        assertThat(evento.detalle()).containsEntry("versionAnterior", "2026-1");
        assertThat(evento.detalle()).containsEntry("documentoUrlAnterior", ENLACE);
    }

    @Test
    @DisplayName("RF-002: los campos opcionales vacíos se guardan en nulo")
    void opcionalesVacios() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.ACTIVA)));
        given(convocatorias.guardar(ID, "Convocatoria 2030", null, null)).willReturn(CONVOCATORIA);
        given(convocatorias.buscar(LIGA, ID)).willReturn(Optional.empty(),
                Optional.of(convocatoria("Convocatoria 2030", null, null)));

        ConvocatoriaDto resultado = servicio.reemplazar(ID, peticion("Convocatoria 2030", "  ", ""));

        assertThat(resultado.version()).isNull();
        assertThat(resultado.documentoUrl()).isNull();
    }

    @Test
    @DisplayName("RF-003: una temporada cerrada no admite cambios en su convocatoria")
    void temporadaCerrada() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CERRADA)));

        assertThatThrownBy(() -> servicio.reemplazar(ID, peticion("Convocatoria 2030", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(convocatorias, never()).guardar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso de la convocatoria no se escribe")
    void sinPermiso() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION)));
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.reemplazar(ID, peticion("Convocatoria 2030", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verify(convocatorias, never()).guardar(any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: una temporada inexistente responde 404 sin evaluar el permiso")
    void temporadaInexistente() {
        given(temporadas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reemplazar(ID, peticion("Convocatoria 2030", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion, convocatorias, auditoria);
    }

    @Test
    @DisplayName("RF-002: rechaza una referencia vacía, una versión larga y un enlace que no es URL")
    void validaContenido() {
        given(temporadas.buscarParaActualizar(LIGA, ID))
                .willReturn(Optional.of(temporada(EstadoTemporada.CONFIGURACION)));

        assertThatThrownBy(() -> servicio.reemplazar(ID, peticion("  ", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.reemplazar(ID, peticion("Convocatoria 2030", "x".repeat(31), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.reemplazar(ID, peticion("Convocatoria 2030", null, "liga.mx/convocatoria")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(convocatorias, never()).guardar(any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }
}
