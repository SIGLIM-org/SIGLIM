package mx.tecnm.morelia.siglim.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
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
import mx.tecnm.morelia.siglim.canchas.CanchaRepository.DatosCancha;
import mx.tecnm.morelia.siglim.canchas.PartidoEnCanchaDto.EquipoDto;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link CanchaService}. Sin Spring ni base de datos. */
class CanchaServiceTest {

    private static final String CONSULTAR = "canchas.cancha:CONSULTAR";
    private static final String EDITAR = "canchas.cancha:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PERFIL = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final OffsetDateTime DESDE = OffsetDateTime.parse("2030-03-10T10:00:00-06:00");
    private static final OffsetDateTime HASTA = DESDE.plusHours(2);
    private static final Intervalo PERIODO = new Intervalo(DESDE, HASTA);
    private static final String MOTIVO = "Riego de la cancha programado";
    private static final PartidoEnCanchaDto PARTIDO = new PartidoEnCanchaDto(
            UUID.fromString("44444444-4444-4444-8444-444444444444"), "PROGRAMADO", PERIODO,
            new EquipoDto(UUID.randomUUID(), "Halcones"), new EquipoDto(UUID.randomUUID(), "Toros"));
    private static final CanchaDto DE_LIGA = new CanchaDto(ID, "Cancha 1", UbicacionCancha.UNIDAD_DEPORTIVA, null, null,
            PropietarioCancha.LIGA, null, null, null, true, DESDE);
    private static final UUID SEDE = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final SedeDto SEDE_NORTE = new SedeDto(SEDE, "Unidad Norte", null);
    private static final CanchaDto CON_SEDE = new CanchaDto(ID, "Cancha 1", UbicacionCancha.UNIDAD_DEPORTIVA,
            new CanchaDto.SedeResumenDto(SEDE, "Unidad Norte"), null, PropietarioCancha.LIGA, null, null, null, true,
            DESDE);
    private static final DisponibilidadDto BLOQUEO_GUARDADO = new DisponibilidadDto(UUID.randomUUID(),
            TipoDisponibilidad.BLOQUEO, PERIODO, MOTIVO, DESDE);
    private static final DisponibilidadDto MANTENIMIENTO_GUARDADO = new DisponibilidadDto(UUID.randomUUID(),
            TipoDisponibilidad.MANTENIMIENTO, PERIODO, MOTIVO, DESDE);

    private final CanchaRepository canchas = mock(CanchaRepository.class);
    private final SedeRepository sedes = mock(SedeRepository.class);
    private final DisponibilidadCanchaRepository disponibilidades = mock(DisponibilidadCanchaRepository.class);
    private final ConsultaPartidosEnCancha partidos = mock(ConsultaPartidosEnCancha.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final CanchaService servicio = new CanchaService(canchas, sedes, disponibilidades, partidos, autorizacion,
            auditoria, motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC),
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

    private static CrearCanchaRequest crear(PropietarioCancha propietario, UUID club, String externo) {
        return new CrearCanchaRequest(" Cancha 1 ", UbicacionCancha.UNIDAD_DEPORTIVA, "Calle Reforma 1", propietario,
                club, externo, null, null);
    }

    private static ActualizarCanchaRequest cambiarActiva(boolean activa, String motivo, Boolean confirmar) {
        return new ActualizarCanchaRequest(null, null, null, null, null, null, null, activa, motivo, confirmar,
                null, null);
    }

    private static ActualizarCanchaRequest cambiarNombre(String nombre) {
        return new ActualizarCanchaRequest(nombre, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static ActualizarCanchaRequest cambiarSede(UUID sedeId, Boolean quitarSede) {
        return new ActualizarCanchaRequest(null, null, null, null, null, null, null, null, null, null, sedeId,
                quitarSede);
    }

    private static RegistrarDisponibilidadRequest bloqueo(String motivo, Boolean confirmar) {
        return new RegistrarDisponibilidadRequest(TipoDisponibilidad.BLOQUEO, PERIODO, motivo, confirmar);
    }

    private static RegistrarDisponibilidadRequest mantenimiento(String motivo, Boolean confirmar) {
        return new RegistrarDisponibilidadRequest(TipoDisponibilidad.MANTENIMIENTO, PERIODO, motivo, confirmar);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private void canchaBloqueable() {
        given(canchas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    // ── Existencia y ámbito ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-024: una cancha inexistente o de otra liga responde 404 sin evaluar el permiso")
    void canchaInexistente() {
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("ADR-0007: una cancha de la liga se consulta con objetivo de liga")
    void objetivoDeLiga() {
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));

        assertThat(servicio.consultar(ID)).isEqualTo(DE_LIGA);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
    }

    @Test
    @DisplayName("ADR-0007: una cancha de club se consulta con el objetivo de ese club")
    void objetivoDeClub() {
        CanchaDto deClub = new CanchaDto(ID, "Cancha club", UbicacionCancha.EXTERNA, null, null, PropietarioCancha.CLUB,
                new CanchaDto.ClubDto(CLUB, "Halcones"), null, null, true, DESDE);
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(deClub));

        servicio.consultar(ID);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(CLUB));
    }

    @Test
    @DisplayName("VAL-015: sin permiso de edición no crea nada")
    void crearSinPermiso() {
        org.mockito.BDDMockito.willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(crear(PropietarioCancha.LIGA, null, null)))
                .isInstanceOf(ReglaNegocioException.class);
        verifyNoInteractions(canchas);
    }

    @Test
    @DisplayName("ADR-0007: crear una cancha de club exige el permiso sobre ese club")
    void crearDeClubUsaSuObjetivo() {
        given(canchas.insertar(eq(LIGA), any(DatosCancha.class))).willReturn(ID);
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));

        servicio.crear(crear(PropietarioCancha.CLUB, CLUB, null));

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));
    }

    @Test
    @DisplayName("ADR-0007: el listado usa el alcance del permiso y la liga activa")
    void listarUsaAlcance() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(CLUB), false);
        FiltroCanchas filtro = new FiltroCanchas(null, null, null, null, null, 0, 20, "nombre,asc");
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(canchas.listar(LIGA, filtro, alcance)).willReturn(List.of(DE_LIGA));
        given(canchas.contar(LIGA, filtro, alcance)).willReturn(21L);

        var pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).containsExactly(DE_LIGA);
        assertThat(pagina.totalElementos()).isEqualTo(21);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("RF-027: rechaza un orden por un campo no permitido y un tamaño mayor que 100")
    void listarValidaOrdenYTamano() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));

        assertThatThrownBy(() -> servicio.listar(new FiltroCanchas(null, null, null, null, null, 0, 20, "direccion,asc")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(new FiltroCanchas(null, null, null, null, null, 0, 101, "nombre,asc")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
    }

    // ── Propietario y nombre ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-027: rechaza propietario CLUB sin club")
    void clubSinClub() {
        assertThatThrownBy(() -> servicio.crear(crear(PropietarioCancha.CLUB, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
    }

    @Test
    @DisplayName("RF-027: rechaza propietario EXTERNO sin propietario externo o con club")
    void externoIncoherente() {
        assertThatThrownBy(() -> servicio.crear(crear(PropietarioCancha.EXTERNO, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.crear(crear(PropietarioCancha.EXTERNO, CLUB, "Sr. Pérez")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
    }

    @Test
    @DisplayName("RF-027: rechaza propietario LIGA con club o propietario externo")
    void ligaIncoherente() {
        assertThatThrownBy(() -> servicio.crear(crear(PropietarioCancha.LIGA, CLUB, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.crear(crear(PropietarioCancha.LIGA, null, "Sr. Pérez")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
    }

    @Test
    @DisplayName("RF-027: en PATCH valida el estado resultante: una cancha de liga no acepta solo un club")
    void patchValidaResultante() {
        canchaBloqueable();
        var peticion = new ActualizarCanchaRequest(null, null, null, null, CLUB, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> servicio.actualizar(ID, peticion))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(canchas, never()).actualizar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("RF-027: en PATCH, al cambiar de propietario no se arrastran los datos del anterior")
    void patchCambiaPropietario() {
        CanchaDto externa = new CanchaDto(ID, "Cancha 1", UbicacionCancha.EXTERNA, null, null, PropietarioCancha.EXTERNO,
                null, "Sr. Pérez", null, true, DESDE);
        given(canchas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(externa));
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));
        var peticion = new ActualizarCanchaRequest(null, null, null, PropietarioCancha.LIGA, null, null, null, null,
                null, null, null, null);

        servicio.actualizar(ID, peticion);

        ArgumentCaptor<DatosCancha> datos = ArgumentCaptor.forClass(DatosCancha.class);
        verify(canchas).actualizar(eq(ID), datos.capture(), eq(true));
        assertThat(datos.getValue().propietarioExterno()).isNull();
    }

    @Test
    @DisplayName("RF-027: rechaza un nombre que ya existe en la liga con 409 DUPLICADO")
    void nombreDuplicado() {
        given(canchas.existeNombre(LIGA, "Cancha 1", null)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(crear(PropietarioCancha.LIGA, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        verify(canchas, never()).insertar(any(), any());
    }

    @Test
    @DisplayName("RF-027: un PATCH con nombre en blanco se rechaza")
    void patchNombreEnBlanco() {
        canchaBloqueable();

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarNombre("   ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
    }

    // ── Sede (B-11.1) ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-028: crear con una sede de la liga la guarda y la audita con su id")
    void crearConSede() {
        given(sedes.buscar(LIGA, SEDE)).willReturn(Optional.of(SEDE_NORTE));
        given(canchas.insertar(eq(LIGA), any(DatosCancha.class))).willReturn(ID);
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(CON_SEDE));

        CanchaDto cancha = servicio.crear(new CrearCanchaRequest("Cancha 1", UbicacionCancha.UNIDAD_DEPORTIVA, null,
                PropietarioCancha.LIGA, null, null, null, SEDE));

        assertThat(cancha.sede().nombre()).isEqualTo("Unidad Norte");
        ArgumentCaptor<DatosCancha> datos = ArgumentCaptor.forClass(DatosCancha.class);
        verify(canchas).insertar(eq(LIGA), datos.capture());
        assertThat(datos.getValue().sedeId()).isEqualTo(SEDE);
        assertThat(eventoAuditado().detalle()).containsEntry("sedeId", SEDE.toString());
    }

    @Test
    @DisplayName("RF-028: crear con una sede inexistente o de otra liga responde 422 REFERENCIA_INVALIDA sin escribir")
    void crearConSedeInexistente() {
        given(sedes.buscar(LIGA, SEDE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(new CrearCanchaRequest("Cancha 1", UbicacionCancha.UNIDAD_DEPORTIVA,
                null, PropietarioCancha.LIGA, null, null, null, SEDE)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(canchas, never()).insertar(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-028: editar asigna la sede y audita el campo sede")
    void asignarSede() {
        canchaBloqueable();
        given(sedes.buscar(LIGA, SEDE)).willReturn(Optional.of(SEDE_NORTE));

        servicio.actualizar(ID, cambiarSede(SEDE, null));

        ArgumentCaptor<DatosCancha> datos = ArgumentCaptor.forClass(DatosCancha.class);
        verify(canchas).actualizar(eq(ID), datos.capture(), eq(true));
        assertThat(datos.getValue().sedeId()).isEqualTo(SEDE);
        assertThat(eventoAuditado().detalle()).containsEntry("campos", List.of("sede"));
    }

    @Test
    @DisplayName("RF-028: con quitarSede la cancha queda sin sede")
    void quitarSede() {
        given(canchas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(CON_SEDE));
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));

        servicio.actualizar(ID, cambiarSede(null, true));

        ArgumentCaptor<DatosCancha> datos = ArgumentCaptor.forClass(DatosCancha.class);
        verify(canchas).actualizar(eq(ID), datos.capture(), eq(true));
        assertThat(datos.getValue().sedeId()).isNull();
        verifyNoInteractions(sedes);
    }

    @Test
    @DisplayName("RF-028: sin sedeId ni quitarSede la sede se conserva y no se escribe nada")
    void conservaSede() {
        given(canchas.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(CON_SEDE));

        servicio.actualizar(ID, cambiarNombre("Cancha 1"));

        verify(canchas, never()).actualizar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-028: sedeId y quitarSede juntos responden 400 VALIDACION")
    void sedeYQuitarSede() {
        canchaBloqueable();

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarSede(SEDE, true)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        verify(canchas, never()).actualizar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("RF-028: editar con una sede inexistente responde 422 REFERENCIA_INVALIDA sin escribir")
    void asignarSedeInexistente() {
        canchaBloqueable();
        given(sedes.buscar(LIGA, SEDE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarSede(SEDE, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA"));
        verify(canchas, never()).actualizar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // ── Intervalo, tipo y motivo ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-029: rechaza un periodo cuyo inicio no es anterior a su fin")
    void periodoInvertido() {
        canchaBloqueable();
        var peticion = new RegistrarDisponibilidadRequest(TipoDisponibilidad.DISPONIBLE, new Intervalo(HASTA, DESDE),
                null, null);

        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, peticion))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
    }

    @Test
    @DisplayName("RF-029: la consulta exige las dos fechas y un rango de 31 días como máximo")
    void rangoDeConsulta() {
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));

        assertThatThrownBy(() -> servicio.listarDisponibilidad(ID, DESDE, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThatThrownBy(() -> servicio.consultarOcupacion(ID, DESDE, DESDE.plusDays(31).plusSeconds(1)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
    }

    @Test
    @DisplayName("RN-021: sin motivo o con motivo de menos de 10 caracteres, el mantenimiento responde 400 RN-021 y no inserta")
    void mantenimientoSinMotivo() {
        canchaBloqueable();

        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, mantenimiento(null, true)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, mantenimiento("  Pintura  ", true)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(disponibilidades, never()).insertar(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RN-021: exige motivo de al menos 10 caracteres al bloquear")
    void bloqueoSinMotivo() {
        canchaBloqueable();

        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, bloqueo(null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, bloqueo("  Lluvia  ", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
    }

    @Test
    @DisplayName("RN-021: exige motivo al cambiar activa y lo fija en la transacción")
    void desactivarExigeMotivo() {
        canchaBloqueable();
        given(partidos.listarProgramadosDesde(eq(ID), any())).willReturn(List.of());

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarActiva(false, "corto", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));

        servicio.actualizar(ID, cambiarActiva(false, MOTIVO, null));
        verify(motivoOperacion).fijar(MOTIVO);
    }

    // ── Traslapes y bloqueos ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-029: rechaza disponibilidad traslapada y nombra el intervalo existente")
    void disponibilidadTraslapada() {
        canchaBloqueable();
        given(disponibilidades.buscarDisponibleTraslapado(ID, PERIODO)).willReturn(Optional.of(
                new DisponibilidadDto(UUID.randomUUID(), TipoDisponibilidad.DISPONIBLE, PERIODO, null, DESDE)));
        var peticion = new RegistrarDisponibilidadRequest(TipoDisponibilidad.DISPONIBLE, PERIODO, null, null);

        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, peticion))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-029"))
                .hasMessageContaining("10/03/2030 10:00");
        verify(disponibilidades, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-030: un BLOQUEO no se compara contra la disponibilidad existente")
    void bloqueoPuedeTraslaparse() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of());
        given(disponibilidades.insertar(ID, TipoDisponibilidad.BLOQUEO, PERIODO, MOTIVO, PERFIL))
                .willReturn(BLOQUEO_GUARDADO);

        servicio.registrarDisponibilidad(ID, bloqueo(MOTIVO, null));

        verify(disponibilidades, never()).buscarDisponibleTraslapado(any(), any());
    }

    @Test
    @DisplayName("RF-030: lista los partidos afectados y no guarda sin confirmación")
    void bloqueoSinConfirmacion() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of(PARTIDO));

        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, bloqueo(MOTIVO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("RF-030");
                    assertThat(excepcion.getMessage()).contains("1 partido");
                    assertThat(excepcion.propiedades()).containsEntry("aceptable", true)
                            .containsEntry("partidosAfectados", List.of(PARTIDO));
                });
        verify(disponibilidades, never()).insertar(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-030: guarda con confirmación y devuelve los partidos, que no se tocan")
    void bloqueoConConfirmacion() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of(PARTIDO));
        given(disponibilidades.insertar(ID, TipoDisponibilidad.BLOQUEO, PERIODO, MOTIVO, PERFIL))
                .willReturn(BLOQUEO_GUARDADO);

        var registro = servicio.registrarDisponibilidad(ID, bloqueo(MOTIVO, true));

        assertThat(registro.partidosAfectados()).containsExactly(PARTIDO);
        assertThat(registro.disponibilidad()).isEqualTo(BLOQUEO_GUARDADO);
    }

    @Test
    @DisplayName("RF-031: un mantenimiento con partidos afectados y sin confirmación responde 409 RF-030 aceptable y no inserta")
    void mantenimientoSinConfirmacion() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of(PARTIDO));

        assertThatThrownBy(() -> servicio.registrarDisponibilidad(ID, mantenimiento(MOTIVO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("RF-030");
                    assertThat(excepcion.getMessage())
                            .isEqualTo("El mantenimiento afecta a 1 partido(s), que no se reprogramarán solos.");
                    assertThat(excepcion.propiedades()).containsEntry("aceptable", true)
                            .containsEntry("partidosAfectados", List.of(PARTIDO));
                });
        verify(disponibilidades, never()).insertar(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-031: con confirmación guarda el tipo MANTENIMIENTO y devuelve los partidos afectados")
    void mantenimientoConConfirmacion() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of(PARTIDO));
        given(disponibilidades.insertar(ID, TipoDisponibilidad.MANTENIMIENTO, PERIODO, MOTIVO, PERFIL))
                .willReturn(MANTENIMIENTO_GUARDADO);

        var registro = servicio.registrarDisponibilidad(ID, mantenimiento(MOTIVO, true));

        assertThat(registro.partidosAfectados()).containsExactly(PARTIDO);
        assertThat(registro.disponibilidad()).isEqualTo(MANTENIMIENTO_GUARDADO);
    }

    @Test
    @DisplayName("RF-031: un MANTENIMIENTO no se compara contra la disponibilidad existente")
    void mantenimientoPuedeTraslaparse() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of());
        given(disponibilidades.insertar(ID, TipoDisponibilidad.MANTENIMIENTO, PERIODO, MOTIVO, PERFIL))
                .willReturn(MANTENIMIENTO_GUARDADO);

        servicio.registrarDisponibilidad(ID, mantenimiento(MOTIVO, null));

        verify(disponibilidades, never()).buscarDisponibleTraslapado(any(), any());
    }

    @Test
    @DisplayName("RF-030: desactivar una cancha con partidos futuros sigue el mismo flujo de dos pasos")
    void desactivarConPartidos() {
        canchaBloqueable();
        given(partidos.listarProgramadosDesde(ID, OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC)))
                .willReturn(List.of(PARTIDO));

        assertThatThrownBy(() -> servicio.actualizar(ID, cambiarActiva(false, MOTIVO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.codigo()).isEqualTo("RF-030");
                    assertThat(excepcion.propiedades()).containsEntry("aceptable", true);
                });
        verify(canchas, never()).actualizar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());

        servicio.actualizar(ID, cambiarActiva(false, MOTIVO, true));
        verify(canchas).actualizar(eq(ID), any(DatosCancha.class), eq(false));
    }

    // ── Auditoría ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-093: registrar una cancha se audita sin dirección ni propietario externo")
    void auditaRegistroSinDatosPersonales() {
        given(canchas.insertar(eq(LIGA), any(DatosCancha.class))).willReturn(ID);
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));

        servicio.crear(crear(PropietarioCancha.EXTERNO, null, "Sr. Pérez"));

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-06");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_CANCHA");
        assertThat(evento.ligaId()).isEqualTo(LIGA);
        assertThat(evento.detalle().toString()).doesNotContain("Pérez").doesNotContain("Reforma");
    }

    @Test
    @DisplayName("RF-093: un bloqueo se audita con su motivo y los partidos afectados")
    void auditaBloqueo() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of(PARTIDO));
        given(disponibilidades.insertar(ID, TipoDisponibilidad.BLOQUEO, PERIODO, MOTIVO, PERFIL))
                .willReturn(BLOQUEO_GUARDADO);

        servicio.registrarDisponibilidad(ID, bloqueo(MOTIVO, true));

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("BLOQUEAR_CANCHA");
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        Map<String, Object> detalle = evento.detalle();
        assertThat(detalle).containsEntry("partidosAfectados", 1);
        assertThat(detalle).containsEntry("partidosAfectadosIds", List.of(PARTIDO.id().toString()));
    }

    @Test
    @DisplayName("RF-093: un mantenimiento se audita como REGISTRAR_MANTENIMIENTO con su motivo y los partidos afectados")
    void auditaMantenimiento() {
        canchaBloqueable();
        given(partidos.listarProgramados(ID, PERIODO)).willReturn(List.of(PARTIDO));
        given(disponibilidades.insertar(ID, TipoDisponibilidad.MANTENIMIENTO, PERIODO, MOTIVO, PERFIL))
                .willReturn(MANTENIMIENTO_GUARDADO);

        servicio.registrarDisponibilidad(ID, mantenimiento(MOTIVO, true));

        verify(motivoOperacion).fijar(MOTIVO);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_MANTENIMIENTO");
        assertThat(evento.entidadId()).isEqualTo(MANTENIMIENTO_GUARDADO.id().toString());
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        Map<String, Object> detalle = evento.detalle();
        assertThat(detalle).containsEntry("canchaId", ID.toString())
                .containsEntry("tipo", "MANTENIMIENTO")
                .containsEntry("partidosAfectados", 1)
                .containsEntry("partidosAfectadosIds", List.of(PARTIDO.id().toString()));
    }

    @Test
    @DisplayName("RF-093: un PATCH sin cambios no escribe ni audita")
    void patchSinCambios() {
        canchaBloqueable();

        servicio.actualizar(ID, cambiarNombre("Cancha 1"));

        verify(canchas, never()).actualizar(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        verifyNoInteractions(auditoria);
    }

    // ── Ocupación ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-039: la ocupación une registros y partidos, ordenados por inicio")
    void ocupacionOrdenada() {
        given(canchas.buscar(LIGA, ID)).willReturn(Optional.of(DE_LIGA));
        Intervalo rango = new Intervalo(DESDE.minusDays(1), DESDE.plusDays(6));
        given(disponibilidades.listar(ID, rango)).willReturn(List.of(new DisponibilidadDto(UUID.randomUUID(),
                TipoDisponibilidad.BLOQUEO, new Intervalo(HASTA, HASTA.plusHours(1)), MOTIVO, DESDE)));
        given(partidos.listarNoCancelados(ID, rango)).willReturn(List.of(PARTIDO));

        OcupacionDto ocupacion = servicio.consultarOcupacion(ID, rango.desde(), rango.hasta());

        assertThat(ocupacion.bloques()).extracting(OcupacionDto.BloqueOcupacionDto::tipo)
                .containsExactly(TipoBloqueOcupacion.PARTIDO, TipoBloqueOcupacion.BLOQUEO);
        assertThat(ocupacion.cancha().nombre()).isEqualTo("Cancha 1");
    }
}
