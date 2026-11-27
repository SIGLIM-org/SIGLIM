package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-33.2 contra PostgreSQL real: asignar la fecha, volver a asignar solo pendientes, regenerar con
 * motivo y comprobar que lo anterior queda {@code CANCELADO} con historial y fuera de los listados,
 * que hay una auditoría por operación y que los mismos datos dan la misma asignación.
 *
 * <p>Escenario: temporada que empieza el sábado 5 de marzo de 2033; «Libre» (juega el domingo) y
 * «Veteranos» (juega el sábado), un grupo de 4 equipos cada una y dos canchas de la unidad deportiva.
 * Sin árbitros {@code VALIDADO} (los de otras clases pasan a {@code BAJA} mientras corre): la designación
 * automática (H-34.2) deja todos los partidos en {@code sinArbitro}; la de árbitros se prueba en
 * {@link AsignacionFechaArbitrosIT}.
 *
 * <p>Refs: H-33.2, RF-035, RF-039, RF-041, RF-044, RN-021, DEC-177, DEC-181, DEC-206, CA-002
 */
class AsignacionFechaIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate INICIO = LocalDate.parse("2033-03-05");
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final String MOTIVO = "Se reprogramó la fecha por el torneo relámpago";

    @Autowired
    private CalendarioService calendario;

    @Autowired
    private AsignacionFechaService asignacion;

    @Autowired
    private FechaService fechas;

    @Autowired
    private JornadaService jornadas;

    private List<UUID> arbitrosAjenos = List.of();

    @BeforeEach
    void aislarArbitros() {
        arbitrosAjenos = aislarArbitrosValidados(LIGA);
    }

    @AfterEach
    void devolverArbitros() {
        restaurarArbitros(arbitrosAjenos);
    }

    @Test
    @DisplayName("DEC-181, CA-002: asigna sin conflictos, PENDIENTES no toca nada y REGENERAR cancela y repite la misma asignación")
    void asignarYRegenerar() {
        Escenario e = escenario();
        calendario.generar(e.temporada(), null);
        UUID fecha = fechas.listar(e.temporada(), 0, 20).contenido().getFirst().id();

        AsignacionFechaDto primera = asignacion.asignar(fecha, new AsignarFechaRequest(ModoAsignacion.PENDIENTES, null));

        assertThat(primera.totales()).isEqualTo(new AsignacionFechaDto.Totales(4, 0, 0, 0, 4));
        assertThat(primera.noAsignados()).isEmpty();
        assertThat(primera.fecha().jornadas()).as("una jornada por grupo (DEC-175)").isEqualTo(2);
        assertThat(primera.fecha().sinAsignar()).isZero();
        List<FechaDto.FilaPartido> antes = partidos(primera.fecha());
        assertThat(antes).hasSize(4).allSatisfy(partido -> {
            assertThat(partido.asignadoAutomaticamente()).isTrue();
            assertThat(partido.emparejamientoId()).isNotNull();
        });
        assertThat(primera.fecha().bloques()).allSatisfy(bloque -> {
            DayOfWeek dia = bloque.diasJuego().contains(7) ? DayOfWeek.SUNDAY : DayOfWeek.SATURDAY;
            assertThat(bloque.partidos()).allSatisfy(partido -> {
                assertThat(partido.inicio().atZoneSameInstant(ZONA).getDayOfWeek()).as("DEC-177").isEqualTo(dia);
                assertThat(partido.inicio().atZoneSameInstant(ZONA).getHour()).isBetween(8, 15);
            });
        });
        assertThat(fechas.verificar(fecha).conflictos()).as("sin conflictos duros (CA-002)").isEmpty();
        assertThat(jdbc.sql("""
                        SELECT generada_por = :perfil AND generada_en IS NOT NULL
                               AND parametros_generacion ->> 'modo' = 'PENDIENTES'
                               AND (parametros_generacion ->> 'asignados')::int = 4
                          FROM siglim.fechas_programacion WHERE id = :fecha
                        """)
                .param("perfil", e.programador()).param("fecha", fecha).query(Boolean.class).single())
                .as("RF-041").isTrue();

        AsignacionFechaDto otraVez = asignacion.asignar(fecha, new AsignarFechaRequest(ModoAsignacion.PENDIENTES, null));

        assertThat(otraVez.totales()).as("sin pendientes no cambia nada; los 4 siguen sin árbitro (DEC-217)")
                .isEqualTo(new AsignacionFechaDto.Totales(0, 0, 0, 0, 4));
        assertThat(partidos(otraVez.fecha())).isEqualTo(antes);

        AsignacionFechaDto regenerada = asignacion.asignar(fecha, new AsignarFechaRequest(ModoAsignacion.REGENERAR, MOTIVO));

        assertThat(regenerada.totales()).isEqualTo(new AsignacionFechaDto.Totales(4, 0, 4, 0, 4));
        List<FechaDto.FilaPartido> despues = partidos(regenerada.fecha());
        assertThat(despues).extracting(FechaDto.FilaPartido::id)
                .as("los partidos son nuevos").doesNotContainAnyElementsOf(antes.stream().map(FechaDto.FilaPartido::id).toList());
        assertThat(firma(despues)).as("mismos datos, misma asignación").isEqualTo(firma(antes));
        for (FechaDto.FilaPartido anterior : antes) {
            assertThat(jdbc.sql("SELECT estado::text FROM siglim.partidos WHERE id = :id")
                    .param("id", anterior.id()).query(String.class).single()).as("RF-044: no se borra").isEqualTo("CANCELADO");
            assertThat(jdbc.sql("""
                            SELECT count(*) FROM siglim.partido_estado_historial
                             WHERE partido_id = :id AND estado_nuevo = 'CANCELADO' AND motivo = :motivo
                            """)
                    .param("id", anterior.id()).param("motivo", MOTIVO).query(Long.class).single())
                    .as("RN-021: historial con motivo").isEqualTo(1);
        }
        for (UUID jornada : regenerada.fecha().bloques().stream().map(FechaDto.Bloque::jornadaId).toList()) {
            assertThat(jornadas.consultar(jornada).partidos()).as("DEC-181: el listado de la jornada excluye CANCELADO")
                    .hasSize(2).allSatisfy(partido -> assertThat(partido.estado()).isEqualTo(EstadoPartido.PROGRAMADO));
        }
        assertThat(fechas.verificar(fecha).conflictos()).isEmpty();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.auditoria
                         WHERE modulo = 'M-07' AND accion = 'GENERAR_FECHA' AND entidad = 'fechas_programacion'
                           AND entidad_id = :fecha AND perfil_id = :perfil
                        """)
                .param("fecha", fecha.toString()).param("perfil", e.programador()).query(Long.class).single())
                .as("una auditoría por operación").isEqualTo(3);
        assertThat(jdbc.sql("""
                        SELECT motivo = :motivo AND detalle ->> 'modo' = 'REGENERAR'
                               AND (detalle ->> 'cancelados')::int = 4
                          FROM siglim.auditoria
                         WHERE accion = 'GENERAR_FECHA' AND entidad_id = :fecha AND detalle ->> 'modo' = 'REGENERAR'
                        """)
                .param("fecha", fecha.toString()).param("motivo", MOTIVO).query(Boolean.class).single()).isTrue();
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private static List<FechaDto.FilaPartido> partidos(FechaDto fecha) {
        return fecha.bloques().stream().flatMap(bloque -> bloque.partidos().stream()).toList();
    }

    /** Emparejamiento, cancha e inicio de cada partido, ordenados: la asignación sin los ids de partido. */
    private static List<String> firma(List<FechaDto.FilaPartido> partidos) {
        return partidos.stream()
                .map(p -> p.emparejamientoId() + "@" + p.cancha().id() + "@" + p.inicio().toInstant())
                .sorted()
                .toList();
    }

    private record Escenario(UUID programador, UUID temporada) {
    }

    private Escenario escenario() {
        UUID programador = iniciarSesion(perfilConRol("PROGRAMADOR"));
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, :inicio) RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).param("inicio", INICIO)
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo)
                        VALUES (:temporada, 1, 'Parámetros de prueba')
                        """)
                .param("temporada", temporada).update();
        crearGrupo(crearCategoriaTemporada(temporada, "Libre", "LIBRE"), 4);
        crearGrupo(crearCategoriaTemporada(temporada, "Veteranos", "VETERANOS"), 4);
        for (int i = 0; i < 2; i++) {
            jdbc.sql("""
                            INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                            VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA')
                            """)
                    .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID()).update();
        }
        return new Escenario(programador, temporada);
    }

    private UUID crearCategoriaTemporada(UUID temporada, String prefijo, String tipo) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, CAST(:tipo AS siglim.tipo_categoria), coalesce(max(orden), 0) + 1
                          FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", prefijo + " " + UUID.randomUUID()).param("tipo", tipo)
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria)
                .query(UUID.class).single();
    }

    private void crearGrupo(UUID categoriaTemporada, int equipos) {
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'Grupo 1') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
        for (int i = 0; i < equipos; i++) {
            UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                    .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                    .query(UUID.class).single();
            jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre)")
                    .param("club", club).param("grupo", grupo).param("nombre", "Equipo " + UUID.randomUUID())
                    .update();
        }
    }

    private UUID iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    private UUID perfilConRol(String codigoRol) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("INSERT INTO auth.users (id, email, aud, role) VALUES (:id, :correo, 'authenticated', 'authenticated')")
                .param("id", usuarioAuth).param("correo", usuarioAuth + "@prueba.mx").update();
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth).param("nombre", codigoRol + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA' FROM siglim.roles r WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", codigoRol).update();
        return perfil;
    }
}
