package mx.tecnm.morelia.siglim.arbitraje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.PartidosPrueba;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErrorTraducido;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.compartido.error.TraductorErroresBd;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-14.1 contra PostgreSQL real, con {@link DesignacionService} completo: autorización con la matriz
 * de la migración, bloqueos, {@code tg_designaciones_validar} (RF-113, VAL-019, RF-039) traducido por
 * {@link TraductorErroresBd} como en el API, sustitución y retiro sin borrar, auditoría y
 * «Mis designaciones» con {@code es_titular}.
 *
 * <p>Los criterios de aceptación de H-14: 1 (VAL-019 → {@code 422}), 2 (choque de horario →
 * {@code 409 RF-039}) y 3 (el árbitro ve el partido de una jornada publicada en «Mis designaciones»).
 *
 * <p>Refs: H-14.1, RF-023, RF-039, RF-113, RF-115, VAL-019, DEC-201 a DEC-205
 */
class DesignacionesIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2032-05-08T10:00:00-06:00");
    private static final String MOTIVO = "El árbitro central se lesionó en el calentamiento";

    @Autowired
    private DesignacionService servicio;

    @Autowired
    private TraductorErroresBd traductor;

    @Autowired
    private PlatformTransactionManager transacciones;

    /** Datos creados por la prueba; se retiran para no contaminar a las demás clases *IT. */
    private final List<UUID> arbitrosCreados = new ArrayList<>();
    private final List<UUID> clubesCreados = new ArrayList<>();

    @AfterEach
    void limpiarDatosCreados() {
        for (UUID arbitro : arbitrosCreados) {
            jdbc.sql("DELETE FROM siglim.designaciones WHERE arbitro_id = :id").param("id", arbitro).update();
            jdbc.sql("DELETE FROM siglim.disponibilidad_arbitral WHERE arbitro_id = :id").param("id", arbitro).update();
            jdbc.sql("DELETE FROM siglim.arbitros WHERE id = :id").param("id", arbitro).update();
        }
        for (UUID club : clubesCreados) {
            jdbc.sql("DELETE FROM siglim.clubes WHERE id = :id").param("id", club).update();
        }
        arbitrosCreados.clear();
        clubesCreados.clear();
    }

    // ── Criterios de aceptación de H-14 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-14 criterio 1 · VAL-019: un árbitro del club de un equipo del partido responde 422")
    void arbitroDelMismoClub() {
        UUID partido = partido(INICIO);
        UUID arbitro = arbitro(clubLocal(partido), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));

        assertThatThrownBy(() -> servicio.designar(partido, central(arbitro)))
                .satisfies(errorBd(HttpStatus.UNPROCESSABLE_CONTENT, "VAL-019"));
        assertThat(vigentes(partido)).isZero();
    }

    @Test
    @DisplayName("H-14 criterio 2 · RF-039: un árbitro ya designado a la misma hora en otro partido responde 409")
    void choqueDeHorario() {
        UUID primero = partido(INICIO);
        UUID segundo = partido(INICIO.plusHours(1));
        UUID arbitro = arbitro(club(), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        servicio.designar(primero, central(arbitro));

        assertThatThrownBy(() -> servicio.designar(segundo, central(arbitro)))
                .satisfies(errorBd(HttpStatus.CONFLICT, "RF-039"));

        // Un partido que empieza cuando termina el bloque del primero ya no choca: [inicio, fin_bloque).
        UUID despues = partido(finBloque(primero));
        assertThat(servicio.designar(despues, central(arbitro)).vigente()).isTrue();
    }

    @Test
    @DisplayName("H-14 criterio 3 · DEC-205: el árbitro ve en Mis designaciones la jornada publicada y no la de borrador")
    void misDesignaciones() {
        UUID persona = persona();
        UUID arbitro = arbitro(club(), persona, EstadoArbitro.VALIDADO);
        UUID publicado = partido(INICIO.plusDays(30));
        UUID enBorrador = partido(INICIO.plusDays(31));
        publicarJornadaDe(publicado);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        DesignacionDto designada = servicio.designar(publicado,
                new DesignarArbitroRequest(arbitro, FuncionArbitral.ASISTENTE, null));
        servicio.designar(enBorrador, central(arbitro));

        iniciarSesion(perfilConRolYPersona("ARBITRO", persona));
        Pagina<MiDesignacionDto> pagina = servicio.listarPropias(new FiltroMisDesignaciones(null, null, 0, 20));

        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.contenido()).singleElement().satisfies(mia -> {
            assertThat(mia.designacionId()).isEqualTo(designada.id());
            assertThat(mia.funcion()).isEqualTo(FuncionArbitral.ASISTENTE);
            assertThat(mia.partido().id()).isEqualTo(publicado);
            assertThat(mia.partido().inicio().toInstant()).isEqualTo(INICIO.plusDays(30).toInstant());
            assertThat(mia.partido().jornada().numero()).isEqualTo(1);
            assertThat(mia.cedula()).isNull();
        });
        assertThat(servicio.listarPropias(new FiltroMisDesignaciones(
                INICIO.plusDays(31), INICIO.plusDays(40), 0, 20)).contenido())
                .as("el rango [desde, hasta) excluye el partido publicado").isEmpty();
    }

    // ── Reglas del trigger y del servicio ───────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-113: un árbitro PROPUESTO responde 409 RF-113 desde el trigger")
    void arbitroPropuesto() {
        UUID partido = partido(INICIO.plusDays(1));
        UUID arbitro = arbitro(club(), persona(), EstadoArbitro.PROPUESTO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));

        assertThatThrownBy(() -> servicio.designar(partido, central(arbitro)))
                .satisfies(errorBd(HttpStatus.CONFLICT, "RF-113"));
    }

    @Test
    @DisplayName("DEC-202: sustituir al central deja la anterior sin vigencia con su motivo y audita")
    void sustituirCentral() {
        UUID partido = partido(INICIO.plusDays(2));
        UUID clubArbitros = club();
        UUID primero = arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO);
        UUID segundo = arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        DesignacionDto anterior = servicio.designar(partido, central(primero));

        assertThatThrownBy(() -> servicio.designar(partido, central(segundo)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));

        DesignacionDto nueva = servicio.designar(partido,
                new DesignarArbitroRequest(segundo, FuncionArbitral.CENTRAL, "  " + MOTIVO + " "));

        assertThat(nueva.arbitro().id()).isEqualTo(segundo);
        assertThat(nueva.automatica()).isFalse();
        Map<String, Object> fila = jdbc.sql("SELECT vigente, motivo_cambio FROM siglim.designaciones WHERE id = :id")
                .param("id", anterior.id()).query().singleRow();
        assertThat(fila).containsEntry("vigente", false).containsEntry("motivo_cambio", MOTIVO);
        assertThat(acciones(nueva.id())).containsExactly("SUSTITUIR_ARBITRO");
        assertThat(acciones(anterior.id())).containsExactly("DESIGNAR_ARBITRO");
        assertThat(jdbc.sql("SELECT designado_por FROM siglim.designaciones WHERE id = :id")
                .param("id", nueva.id()).query(UUID.class).single()).isNotNull();
    }

    @Test
    @DisplayName("DEC-203: retirar deja la designación sin vigencia y retirarla otra vez responde 409")
    void retirar() {
        UUID partido = partido(INICIO.plusDays(3));
        UUID arbitro = arbitro(club(), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        DesignacionDto designada = servicio.designar(partido, central(arbitro));

        DesignacionDto retirada = servicio.retirar(designada.id(), new RetirarDesignacionRequest(MOTIVO));

        assertThat(retirada.vigente()).isFalse();
        assertThat(retirada.motivoCambio()).isEqualTo(MOTIVO);
        assertThat(vigentes(partido)).isZero();
        assertThat(acciones(designada.id())).containsExactly("DESIGNAR_ARBITRO", "RETIRAR_DESIGNACION");
        assertThatThrownBy(() -> servicio.retirar(designada.id(), new RetirarDesignacionRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-023"));
        // Sin central vigente, designar otro central no pide motivo.
        assertThat(servicio.designar(partido, central(arbitro)).vigente()).isTrue();
    }

    @Test
    @DisplayName("DEC-201: dos asistentes se designan y el tercero responde 409 RF-023")
    void tercerAsistente() {
        UUID partido = partido(INICIO.plusDays(4));
        UUID clubArbitros = club();
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        servicio.designar(partido, asistente(arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO)));
        servicio.designar(partido, asistente(arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO)));

        UUID tercero = arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO);
        assertThatThrownBy(() -> servicio.designar(partido, asistente(tercero)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-023"));
        assertThat(vigentes(partido)).isEqualTo(2);

        DesignacionesJornadaDto vista = servicio.listarPorJornada(jornadaDe(partido));
        assertThat(vista.avance()).isEqualTo(new DesignacionesJornadaDto.AvanceDto(1, 0, 1));
        assertThat(vista.partidos()).singleElement().satisfies(p -> {
            assertThat(p.central()).isNull();
            assertThat(p.asistentes()).hasSize(2);
            assertThat(p.designable()).isTrue();
        });
    }

    @Test
    @DisplayName("DEC-204: los candidatos marcan VAL-019, RF-039, YA_DESIGNADO y RF-022, y elegible coincide con el POST")
    void candidatos() {
        UUID partido = partido(INICIO.plusDays(5));
        UUID otroPartido = partido(INICIO.plusDays(5).plusMinutes(30));
        UUID clubArbitros = club();
        UUID mismoClub = arbitro(clubLocal(partido), persona(), EstadoArbitro.VALIDADO);
        UUID conChoque = arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO);
        UUID yaDesignado = arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO);
        UUID noDisponible = arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO);
        UUID libre = arbitro(clubArbitros, persona(), EstadoArbitro.VALIDADO);
        UUID propuesto = arbitro(clubArbitros, persona(), EstadoArbitro.PROPUESTO);
        jdbc.sql("""
                        INSERT INTO siglim.disponibilidad_arbitral (arbitro_id, periodo, disponible)
                        VALUES (:arbitro, tstzrange(CAST(:desde AS timestamptz), CAST(:hasta AS timestamptz), '[)'), false)
                        """)
                .param("arbitro", noDisponible)
                .param("desde", INICIO.plusDays(5).minusHours(1))
                .param("hasta", INICIO.plusDays(5).plusHours(1))
                .update();
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));
        servicio.designar(otroPartido, central(conChoque));
        servicio.designar(partido, asistente(yaDesignado));

        Map<UUID, CandidatoArbitralDto> candidatos = servicio.candidatos(partido).stream()
                .filter(c -> arbitrosCreados.contains(c.arbitro().id()))
                .collect(Collectors.toMap(c -> c.arbitro().id(), c -> c));

        assertThat(candidatos).as("los PROPUESTO no son candidatos").doesNotContainKey(propuesto)
                .containsOnlyKeys(mismoClub, conChoque, yaDesignado, noDisponible, libre);
        assertThat(codigos(candidatos.get(mismoClub).causas())).containsExactly("VAL-019");
        assertThat(codigos(candidatos.get(conChoque).causas())).containsExactly("RF-039");
        assertThat(candidatos.get(conChoque).causas().get(0).mensaje())
                .startsWith("Ya está designado en Local vs Visitante a las 10:30 (Cancha ");
        assertThat(codigos(candidatos.get(yaDesignado).causas())).containsExactly(ElegibilidadArbitral.YA_DESIGNADO);
        assertThat(candidatos.get(yaDesignado).causas().get(0).mensaje()).endsWith("como asistente.");
        assertThat(candidatos.get(yaDesignado).carga()).isEqualTo(1);
        assertThat(candidatos.get(conChoque).carga()).as("el otro partido es de otra temporada").isZero();
        assertThat(candidatos.get(noDisponible).elegible()).isTrue();
        assertThat(codigos(candidatos.get(noDisponible).advertencias())).containsExactly("RF-022");
        assertThat(candidatos.get(libre).elegible()).isTrue();
        assertThat(candidatos.get(libre).advertencias()).isEmpty();

        // elegible coincide con lo que acepta el POST (cada intento con motivo por si sustituye).
        for (CandidatoArbitralDto candidato : candidatos.values()) {
            UUID arbitro = candidato.arbitro().id();
            DesignarArbitroRequest peticion = new DesignarArbitroRequest(arbitro, FuncionArbitral.CENTRAL, MOTIVO);
            if (candidato.elegible()) {
                assertThat(servicio.designar(partido, peticion).vigente()).as("elegible %s", arbitro).isTrue();
            } else {
                assertThatThrownBy(() -> servicio.designar(partido, peticion)).as("no elegible %s", arbitro)
                        .isInstanceOf(RuntimeException.class);
            }
        }
    }

    // ── Permisos y liga ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015: un usuario sin arbitraje.designacion:EDITAR recibe 403")
    void sinPermisoDeEditar() {
        UUID partido = partido(INICIO.plusDays(6));
        UUID arbitro = arbitro(club(), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        assertThatThrownBy(() -> servicio.designar(partido, central(arbitro)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThatThrownBy(() -> servicio.candidatos(partido))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThat(servicio.listarPorJornada(jornadaDe(partido)).partidos())
                .as("el Programador sí consulta").hasSize(1);
    }

    @Test
    @DisplayName("VAL-024: un partido de otra liga responde 404 en candidatos y al designar")
    void partidoDeOtraLiga() {
        UUID partido = partido(INICIO.plusDays(7));
        UUID arbitro = arbitro(club(), persona(), EstadoArbitro.VALIDADO);
        iniciarSesion(perfilConRol("COMISION_ARBITRAJE"));

        // La liga ajena solo existe dentro de esta transacción, que se revierte al final.
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            UUID otraLiga = jdbc.sql("INSERT INTO siglim.ligas (nombre) VALUES (:nombre) RETURNING id")
                    .param("nombre", "Liga ajena " + UUID.randomUUID()).query(UUID.class).single();
            jdbc.sql("""
                            UPDATE siglim.temporadas SET liga_id = :liga
                             WHERE id = (SELECT ct.temporada_id FROM siglim.partidos p
                                           JOIN siglim.jornadas j ON j.id = p.jornada_id
                                           JOIN siglim.grupos g ON g.id = j.grupo_id
                                           JOIN siglim.categorias_temporada ct ON ct.id = g.categoria_temporada_id
                                          WHERE p.id = :partido)
                            """)
                    .param("liga", otraLiga).param("partido", partido).update();

            assertThatThrownBy(() -> servicio.candidatos(partido)).isInstanceOf(RecursoNoEncontradoException.class);
            assertThatThrownBy(() -> servicio.designar(partido, central(arbitro)))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            estado.setRollbackOnly();
        });
        assertThatThrownBy(() -> servicio.designar(UUID.randomUUID(), central(arbitro)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private UUID partido(OffsetDateTime inicio) {
        return new PartidosPrueba(jdbc).programar(crearCancha(), inicio);
    }

    private UUID crearCancha() {
        return jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID clubLocal(UUID partido) {
        return jdbc.sql("""
                        SELECT e.club_id FROM siglim.partidos p JOIN siglim.equipos e ON e.id = p.equipo_local_id
                         WHERE p.id = :id
                        """).param("id", partido).query(UUID.class).single();
    }

    private UUID jornadaDe(UUID partido) {
        return jdbc.sql("SELECT jornada_id FROM siglim.partidos WHERE id = :id")
                .param("id", partido).query(UUID.class).single();
    }

    private OffsetDateTime finBloque(UUID partido) {
        return jdbc.sql("SELECT fin_bloque FROM siglim.partidos WHERE id = :id")
                .param("id", partido).query(OffsetDateTime.class).single();
    }

    /** Envía y aprueba la jornada del partido con dos perfiles distintos (RF-121). */
    private void publicarJornadaDe(UUID partido) {
        UUID jornada = jornadaDe(partido);
        jdbc.sql("""
                        UPDATE siglim.jornadas SET estado_publicacion = 'REVISION', enviada_por = :perfil,
                                                   enviada_en = now()
                         WHERE id = :id
                        """).param("perfil", crearPerfil()).param("id", jornada).update();
        jdbc.sql("""
                        UPDATE siglim.jornadas SET estado_publicacion = 'PUBLICADO', aprobada_por = :perfil,
                                                   aprobada_en = now()
                         WHERE id = :id
                        """).param("perfil", crearPerfil()).param("id", jornada).update();
    }

    private long vigentes(UUID partido) {
        return jdbc.sql("SELECT count(*) FROM siglim.designaciones WHERE partido_id = :id AND vigente")
                .param("id", partido).query(Long.class).single();
    }

    private List<String> acciones(UUID designacion) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE modulo = 'M-05' AND entidad = 'designaciones' AND entidad_id = :id
                         ORDER BY id
                        """).param("id", designacion.toString()).query(String.class).list();
    }

    private static DesignarArbitroRequest central(UUID arbitro) {
        return new DesignarArbitroRequest(arbitro, FuncionArbitral.CENTRAL, null);
    }

    private static DesignarArbitroRequest asistente(UUID arbitro) {
        return new DesignarArbitroRequest(arbitro, FuncionArbitral.ASISTENTE, null);
    }

    private static List<String> codigos(List<CandidatoArbitralDto.CausaDto> causas) {
        return causas.stream().map(CandidatoArbitralDto.CausaDto::codigo).toList();
    }

    private UUID arbitro(UUID club, UUID persona, EstadoArbitro estado) {
        UUID id = jdbc.sql("""
                        INSERT INTO siglim.arbitros (persona_id, club_id, estado)
                        VALUES (:persona, :club, CAST(:estado AS siglim.estado_arbitro))
                        RETURNING id
                        """)
                .param("persona", persona)
                .param("club", club)
                .param("estado", estado.name())
                .query(UUID.class)
                .single();
        arbitrosCreados.add(id);
        return id;
    }

    private UUID club() {
        UUID id = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA)
                .param("nombre", "Árbitros " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        clubesCreados.add(id);
        return id;
    }

    /** Persona con una CURP válida y única; su identidad es global (RF-010, DEC-10). */
    private UUID persona() {
        String sufijo = "" + letra() + letra() + letra() + letra();
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento)
                        VALUES (:curp, 'Ramón', 'Designado', :nacimiento)
                        RETURNING id
                        """)
                .param("curp", "DERA850505HMN" + sufijo.substring(0, 3) + sufijo.charAt(3) + "7")
                .param("nacimiento", LocalDate.of(1985, 5, 5))
                .query(UUID.class)
                .single();
    }

    private static char letra() {
        return (char) ('A' + ThreadLocalRandom.current().nextInt(26));
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class)
                .single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        asignar(perfil, rol);
        return perfil;
    }

    /** Perfil del árbitro: la persona del catálogo queda vinculada a su cuenta (ámbito PROPIO). */
    private UUID perfilConRolYPersona(String rol, UUID persona) {
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (persona_id, nombre_mostrado)
                        VALUES (:persona, 'Árbitro de prueba')
                        RETURNING id
                        """)
                .param("persona", persona)
                .query(UUID.class)
                .single();
        asignar(perfil, rol);
        return perfil;
    }

    private void asignar(UUID perfil, String rol) {
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
                .update();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    /** El error de la base como lo respondería el API: estado HTTP y código del ERS. */
    private ThrowingConsumer<Throwable> errorBd(HttpStatus estado, String codigo) {
        return excepcion -> {
            ErrorTraducido traducido = traductor.traducir(excepcion, false);
            assertThat(traducido.estado()).isEqualTo(estado);
            assertThat(traducido.codigo()).isEqualTo(codigo);
        };
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}
