package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.RegistroPropioDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionRepository.PersonaTitular;
import mx.tecnm.morelia.siglim.registros.MiInformacionRepository.RegistroDePersona;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.DocumentoRegistro;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.RequisitoDocumento;
import mx.tecnm.morelia.siglim.registros.TitularesDto.TitularDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/** Consulta propia del jugador o de su tutor (H-24.1, RF-144, DEC-135 a DEC-138), sin Spring ni BD. */
class MiInformacionServiceTest {

    private static final String CONSULTAR = "registros.registro:CONSULTAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERSONA = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID MENOR = UUID.fromString("21000000-0000-4000-8000-000000000002");
    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID EQUIPO = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final LocalDate HOY = LocalDate.parse("2026-11-11");
    private static final AlcanceAutorizacion PROPIO = new AlcanceAutorizacion(false, Set.of(), true);
    private static final TipoDocumentoDto FOTOGRAFIA = new TipoDocumentoDto(5, "FOTOGRAFIA", "Fotografía");

    private final MiInformacionRepository repositorio = mock(MiInformacionRepository.class);
    private final DocumentoRegistroRepository documentos = mock(DocumentoRegistroRepository.class);
    private final ConsultaPartidosPropios partidos = mock(ConsultaPartidosPropios.class);
    private final ConsultaSancionesPropias sanciones = mock(ConsultaSancionesPropias.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final MiInformacionService servicio = new MiInformacionService(repositorio, documentos, partidos, sanciones,
            autorizacion, Clock.fixed(Instant.parse("2026-11-11T18:00:00Z"), ZoneOffset.UTC),
            new SiglimPropiedades(null, null, null, LIGA));

    // ── Titulares ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-144 · DEC-135: primero la persona de la cuenta y después los menores que tutela")
    void titulares() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(PROPIO);
        given(repositorio.personaDelUsuario()).willReturn(Optional.of(PERSONA));
        given(repositorio.persona(PERSONA, HOY)).willReturn(Optional.of(
                new PersonaTitular(PERSONA, "Ana Pérez", "PEAA800101MMNRRN05", false)));
        given(repositorio.menoresTutelados(PERSONA, HOY)).willReturn(List.of(
                new TitularDto(MENOR, "Luis Pérez", false, true)));

        TitularesDto titulares = servicio.titulares();

        assertThat(titulares.titulares()).containsExactly(
                new TitularDto(PERSONA, "Ana Pérez", true, false),
                new TitularDto(MENOR, "Luis Pérez", false, true));
    }

    @Test
    @DisplayName("DEC-135: una cuenta sin persona vinculada recibe la lista vacía")
    void titularesSinPersona() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(PROPIO);
        given(repositorio.personaDelUsuario()).willReturn(Optional.empty());

        assertThat(servicio.titulares().titulares()).isEmpty();
        verify(repositorio, never()).menoresTutelados(any(), any());
    }

    @Test
    @DisplayName("VAL-015 · DEC-136: sin registros.registro:CONSULTAR los titulares responden 403 sin consultar nada")
    void titularesSinPermiso() {
        given(autorizacion.alcance(CONSULTAR)).willThrow(Autorizacion.sinPermiso());

        esperar403(servicio::titulares);
        verifyNoInteractions(repositorio);
    }

    // ── Detalle ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015 · DEC-136: sin el permiso el detalle responde 403 antes de mirar la titularidad (403 antes que 404)")
    void detalleSinPermiso() {
        given(autorizacion.alcance(CONSULTAR)).willThrow(Autorizacion.sinPermiso());

        esperar403(() -> servicio.consultar(MENOR));
        verifyNoInteractions(repositorio, documentos, partidos, sanciones);
    }

    @Test
    @DisplayName("VAL-024 · DEC-136: una persona de la que no es titular responde 404 aun con ámbito LIGA")
    void detalleNoTitular() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));
        given(repositorio.esTitular(MENOR)).willReturn(false);

        assertThatThrownBy(() -> servicio.consultar(MENOR)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(repositorio, never()).persona(any(), any());
        verifyNoInteractions(documentos, partidos, sanciones);
    }

    @Test
    @DisplayName("DEC-136: primero el permiso y después la titularidad")
    void ordenDeAutorizacion() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(PROPIO);
        given(repositorio.esTitular(MENOR)).willReturn(false);

        assertThatThrownBy(() -> servicio.consultar(MENOR)).isInstanceOf(RecursoNoEncontradoException.class);

        InOrder orden = inOrder(autorizacion, repositorio);
        orden.verify(autorizacion).alcance(CONSULTAR);
        orden.verify(repositorio).esTitular(MENOR);
    }

    @Test
    @DisplayName("RF-144 · DEC-137 · DEC-138: arma el detalle con CURP enmascarada, documentos, elegibilidad, partidos y sanciones")
    void detalle() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(PROPIO);
        given(repositorio.esTitular(PERSONA)).willReturn(true);
        given(repositorio.persona(PERSONA, HOY)).willReturn(Optional.of(
                new PersonaTitular(PERSONA, "Ana Pérez", "GAPA750101HMNRRS05", false)));
        given(repositorio.registros(LIGA, PERSONA)).willReturn(List.of(new RegistroDePersona(REGISTRO,
                EstadoRegistro.ACTIVO, HOY, null, null, "Documentos revisados",
                new TemporadaResumenDto(UUID.randomUUID(), "Temporada 2026", EstadoTemporada.ACTIVA),
                new ReferenciaDto(UUID.randomUUID(), "Club Halcones"), new ReferenciaDto(EQUIPO, "Halcones"),
                new ReferenciaDto(UUID.randomUUID(), "Libre"), TipoCategoria.LIBRE)));
        SancionPropia suspension = new SancionPropia(UUID.randomUUID(), "SUSPENSION_PARTIDOS", "VIGENTE", 2, 0, HOY,
                null, new SancionPropia.ArticuloDto("12", "Juego brusco"), "EXP-1");
        given(sanciones.dePersona(PERSONA)).willReturn(List.of(suspension));
        given(documentos.esMenor(PERSONA, HOY)).willReturn(Optional.of(false));
        given(documentos.requisitos(LIGA, TipoCategoria.LIBRE)).willReturn(List.of(
                new RequisitoDocumento(FOTOGRAFIA, 1, false, true)));
        DocumentoDto foto = new DocumentoDto(UUID.randomUUID(), REGISTRO, FOTOGRAFIA, "image/jpeg", 2048,
                EstadoValidacion.APROBADO, null, null, null, null, OffsetDateTime.parse("2026-11-10T10:00:00-06:00"));
        given(documentos.vigentes(REGISTRO)).willReturn(List.of(
                new DocumentoRegistro(foto, UUID.randomUUID(), null, "bucket", "clave/secreta")));
        PartidoPropio proximo = new PartidoPropio(UUID.randomUUID(), 3, OffsetDateTime.parse("2026-11-15T10:00:00-06:00"),
                "Cancha 1", new ReferenciaDto(EQUIPO, "Halcones"), new ReferenciaDto(UUID.randomUUID(), "Lobos"), true,
                "PROGRAMADO", null);
        given(partidos.proximos(EQUIPO, MiInformacionService.LIMITE_PROXIMOS)).willReturn(List.of(proximo));
        given(partidos.jugados(EQUIPO, MiInformacionService.LIMITE_JUGADOS)).willReturn(List.of());

        MiInformacionDto detalle = servicio.consultar(PERSONA);

        assertThat(detalle.persona().curpEnmascarada()).isEqualTo("GAPA******HMN***05");
        assertThat(detalle.persona().nombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(detalle.sanciones()).containsExactly(suspension);
        RegistroPropioDto registro = detalle.registros().getFirst();
        assertThat(registro.elegibilidad().elegible()).isFalse();
        assertThat(registro.elegibilidad().causas()).extracting(ReglaElegibilidad.Causa::codigo).containsExactly("RF-020");
        assertThat(registro.documentos().listoParaValidar()).isTrue();
        assertThat(registro.documentos().requisitos().getFirst().documentos().getFirst().id()).isEqualTo(foto.id());
        assertThat(registro.proximosPartidos()).containsExactly(proximo);
        assertThat(registro.partidosJugados()).isEmpty();
    }

    private static void esperar403(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(error.codigo()).isEqualTo(ErroresTecnicos.SIN_PERMISO);
        });
    }
}
