package mx.tecnm.morelia.siglim.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Reglas de autorización, validación y reemplazo de horarios (B-13.1, RF-119). */
class HorariosCanchaClubServiceTest {

    private static final UUID LIGA = UUID.randomUUID();
    private static final UUID CANCHA_ID = UUID.randomUUID();
    private static final UUID CLUB_ID = UUID.randomUUID();
    private static final UUID TEMPORADA_ID = UUID.randomUUID();
    private static final UUID CATEGORIA_ID = UUID.randomUUID();
    private static final OffsetDateTime CREADA = OffsetDateTime.parse("2030-03-10T10:00:00-06:00");
    private static final CanchaDto CANCHA = new CanchaDto(CANCHA_ID, "Cancha", UbicacionCancha.EXTERNA,
            null, null, PropietarioCancha.CLUB, new CanchaDto.ClubDto(CLUB_ID, "Club"), null, null, true, CREADA);

    private final HorariosCanchaClubRepository horarios = mock(HorariosCanchaClubRepository.class);
    private final CanchaRepository canchas = mock(CanchaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final HorariosCanchaClubService servicio = new HorariosCanchaClubService(
            horarios, canchas, autorizacion, new SiglimPropiedades(null, null, null, LIGA));

    @Test
    @DisplayName("RF-119: consulta exige permiso con el ámbito del club y devuelve su lista")
    void consultaConAmbitoDelClub() {
        given(canchas.buscar(LIGA, CANCHA_ID)).willReturn(Optional.of(CANCHA));
        given(horarios.listarPorCancha(CANCHA_ID)).willReturn(List.of());

        assertThat(servicio.listar(CANCHA_ID)).isEmpty();

        verify(autorizacion).exigir("canchas.cancha:CONSULTAR", ObjetivoAutorizacion.deClubes(CLUB_ID));
        verify(horarios).listarPorCancha(CANCHA_ID);
    }

    @Test
    @DisplayName("VAL-024: cancha inexistente responde 404 sin consultar horarios")
    void canchaInexistente() {
        given(canchas.buscar(LIGA, CANCHA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.listar(CANCHA_ID)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(autorizacion, horarios);
    }

    @Test
    @DisplayName("RF-119: PUT reemplaza atómicamente y deriva clubId de la cancha cuando se omite")
    void reemplazaConClubDeLaCancha() {
        RegistrarHorarioRequest request = solicitud(horario(1, LocalTime.of(18, 0), LocalTime.of(20, 0)));
        given(canchas.buscarParaActualizar(LIGA, CANCHA_ID)).willReturn(Optional.of(CANCHA));
        given(horarios.existeCategoriaEnTemporada(LIGA, TEMPORADA_ID, CATEGORIA_ID)).willReturn(true);
        given(horarios.existeClubDeLiga(LIGA, CLUB_ID)).willReturn(true);
        HorariosCanchaClubDto guardado = new HorariosCanchaClubDto(UUID.randomUUID(), CANCHA_ID, CLUB_ID,
                TEMPORADA_ID, CATEGORIA_ID, 1, LocalTime.of(18, 0), LocalTime.of(20, 0), false);
        given(horarios.listarPorCancha(CANCHA_ID)).willReturn(List.of(guardado));

        assertThat(servicio.reemplazar(CANCHA_ID, request)).containsExactly(guardado);

        verify(autorizacion).exigir("canchas.cancha:EDITAR", ObjetivoAutorizacion.deClubes(CLUB_ID));
        verify(canchas).buscarParaActualizar(LIGA, CANCHA_ID);
        verify(horarios).eliminarPorCancha(CANCHA_ID);
        verify(horarios).insertar(CANCHA_ID, CLUB_ID, TEMPORADA_ID, CATEGORIA_ID, 1,
                LocalTime.of(18, 0), LocalTime.of(20, 0), false);
    }

    @Test
    @DisplayName("RF-119: PUT permite una lista vacía para quitar todos los horarios")
    void reemplazaConListaVacia() {
        given(canchas.buscarParaActualizar(LIGA, CANCHA_ID)).willReturn(Optional.of(CANCHA));
        given(horarios.listarPorCancha(CANCHA_ID)).willReturn(List.of());

        assertThat(servicio.reemplazar(CANCHA_ID, new RegistrarHorarioRequest(List.of()))).isEmpty();

        verify(horarios).eliminarPorCancha(CANCHA_ID);
        verify(horarios, never()).insertar(any(), any(), any(), any(), anyInt(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("RF-119: PUT rechaza horas que no forman un intervalo antes de borrar")
    void rechazaIntervaloInvalido() {
        given(canchas.buscarParaActualizar(LIGA, CANCHA_ID)).willReturn(Optional.of(CANCHA));
        RegistrarHorarioRequest request = solicitud(horario(1, LocalTime.of(20, 0), LocalTime.of(18, 0)));

        assertThatThrownBy(() -> servicio.reemplazar(CANCHA_ID, request))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.codigo()).isEqualTo("VALIDACION");
                });

        verify(horarios, never()).eliminarPorCancha(any());
    }

    @Test
    @DisplayName("RF-119: PUT rechaza horarios traslapados de la misma categoría y día")
    void rechazaHorarioTraslapado() {
        given(canchas.buscarParaActualizar(LIGA, CANCHA_ID)).willReturn(Optional.of(CANCHA));
        given(horarios.existeCategoriaEnTemporada(LIGA, TEMPORADA_ID, CATEGORIA_ID)).willReturn(true);
        given(horarios.existeClubDeLiga(LIGA, CLUB_ID)).willReturn(true);
        RegistrarHorarioRequest request = solicitud(
                horario(1, LocalTime.of(18, 0), LocalTime.of(19, 0)),
                horario(1, LocalTime.of(18, 30), LocalTime.of(20, 0)));

        assertThatThrownBy(() -> servicio.reemplazar(CANCHA_ID, request))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("RF-119");
                });

        verify(horarios, never()).eliminarPorCancha(any());
    }

    @Test
    @DisplayName("RF-119: PUT acepta horarios contiguos de la misma categoría y día")
    void aceptaHorariosContiguos() {
        given(canchas.buscarParaActualizar(LIGA, CANCHA_ID)).willReturn(Optional.of(CANCHA));
        given(horarios.existeCategoriaEnTemporada(LIGA, TEMPORADA_ID, CATEGORIA_ID)).willReturn(true);
        given(horarios.existeClubDeLiga(LIGA, CLUB_ID)).willReturn(true);
        given(horarios.listarPorCancha(CANCHA_ID)).willReturn(List.of());
        RegistrarHorarioRequest request = solicitud(
                horario(1, LocalTime.of(18, 0), LocalTime.of(19, 0)),
                horario(1, LocalTime.of(19, 0), LocalTime.of(20, 0)));

        assertThat(servicio.reemplazar(CANCHA_ID, request)).isEmpty();

        verify(horarios).insertar(CANCHA_ID, CLUB_ID, TEMPORADA_ID, CATEGORIA_ID, 1,
                LocalTime.of(18, 0), LocalTime.of(19, 0), false);
        verify(horarios).insertar(CANCHA_ID, CLUB_ID, TEMPORADA_ID, CATEGORIA_ID, 1,
                LocalTime.of(19, 0), LocalTime.of(20, 0), false);
    }

    @Test
    @DisplayName("RF-119: PUT rechaza una categoría que no pertenece a la temporada")
    void rechazaCategoriaAjenaALaTemporada() {
        given(canchas.buscarParaActualizar(LIGA, CANCHA_ID)).willReturn(Optional.of(CANCHA));
        given(horarios.existeCategoriaEnTemporada(LIGA, TEMPORADA_ID, CATEGORIA_ID)).willReturn(false);

        assertThatThrownBy(() -> servicio.reemplazar(CANCHA_ID,
                solicitud(horario(1, LocalTime.of(18, 0), LocalTime.of(20, 0)))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("REFERENCIA_INVALIDA");
                });

        verify(horarios, never()).eliminarPorCancha(any());
    }

    private static RegistrarHorarioRequest solicitud(RegistrarHorarioRequest.HorarioCanchaClubItem... items) {
        return new RegistrarHorarioRequest(List.of(items));
    }

    private static RegistrarHorarioRequest.HorarioCanchaClubItem horario(
            int dia, LocalTime inicio, LocalTime fin) {
        return new RegistrarHorarioRequest.HorarioCanchaClubItem(
                TEMPORADA_ID, CATEGORIA_ID, dia, inicio, fin, null, false);
    }
}
