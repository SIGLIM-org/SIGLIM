package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.temporadas.ActualizarDiasJuegoRequest;
import mx.tecnm.morelia.siglim.temporadas.CategoriaTemporadaDto;
import mx.tecnm.morelia.siglim.temporadas.GrupoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-32.2 contra PostgreSQL real: generar el calendario de la temporada, regenerarlo con reemplazo
 * lógico, el bloqueo RN-020, las fechas y sus días, y los días de juego de la categoría.
 *
 * <p>Escenario: temporada que empieza el sábado 1 de marzo de 2031 con 2 vueltas y dos categorías.
 * Categoría A: «Grupo 1» con 4 equipos y «Grupo 2» con 1; categoría B: «Grupo 1» con 5. F = 2·5 = 10.
 *
 * <p>Refs: H-32.2, RF-034, RF-037, RN-012, RN-020, RN-021, DEC-187, DEC-189, DEC-191, DEC-192, DEC-193
 */
class CalendarioIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate INICIO = LocalDate.parse("2031-03-01");
    private static final String MOTIVO = "Se dio de alta un equipo nuevo en el grupo";

    @Autowired
    private CalendarioService calendario;

    @Autowired
    private FechaService fechas;

    @Autowired
    private JornadaService jornadas;

    @Autowired
    private GrupoService grupos;

    // ── Generación ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-034, RN-012, RF-037: genera 2 vueltas por grupo, un descanso por fecha en el impar y omite el grupo de 1")
    void generar() {
        Escenario e = escenario();

        CalendarioGeneradoDto resultado = calendario.generar(e.temporada(), null);

        assertThat(resultado.vueltas()).isEqualTo(2);
        assertThat(resultado.versionParametros()).isEqualTo(1);
        assertThat(resultado.totalFechas()).as("F = 2 vueltas × 5 fechas del grupo de 5").isEqualTo(10);
        assertThat(resultado.fechasCreadas()).isEqualTo(10);
        assertThat(resultado.grupos()).extracting(CalendarioGeneradoDto.GrupoGenerado::grupoId)
                .containsExactly(e.grupo4(), e.grupo5());
        assertThat(resultado.grupos()).extracting(CalendarioGeneradoDto.GrupoGenerado::fechas).containsExactly(6, 10);
        assertThat(resultado.gruposOmitidos()).singleElement().satisfies(omitido -> {
            assertThat(omitido.grupoId()).isEqualTo(e.grupo1());
            assertThat(omitido.equiposActivos()).isEqualTo(1);
        });

        assertThat(vigentes(e.grupo4())).as("4 equipos: 2 partidos × 6 fechas").isEqualTo(12);
        assertThat(vigentes(e.grupo5())).as("5 equipos: 3 filas × 10 fechas").isEqualTo(30);
        assertThat(vigentes(e.grupo1())).isZero();
        assertThat(repetidosEnUnaFecha(e.temporada())).as("RN-012 en la base").isZero();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM (
                          SELECT numero_fecha FROM siglim.emparejamientos
                           WHERE grupo_id = :grupo AND vigente
                           GROUP BY numero_fecha
                          HAVING count(*) FILTER (WHERE equipo_visitante_id IS NULL) = 1) f
                        """)
                .param("grupo", e.grupo5()).query(Long.class).single())
                .as("un descanso en cada una de las 10 fechas").isEqualTo(10);
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.emparejamientos WHERE grupo_id = :grupo AND creado_por = :perfil")
                .param("grupo", e.grupo5()).param("perfil", e.programador()).query(Long.class).single())
                .as("creado_por es la sesión (DEC-192)").isEqualTo(30);

        List<FechaDto> listado = fechas.listar(e.temporada(), 0, 20).contenido();
        assertThat(listado).extracting(FechaDto::numero).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(listado.getFirst().dias()).containsExactly(INICIO, INICIO.plusDays(1));
        assertThat(listado.get(9).dias()).containsExactly(INICIO.plusWeeks(9), INICIO.plusWeeks(9).plusDays(1));
        assertThat(listado.getFirst().estadoPublicacion()).as("sin jornadas (DEC-188)").isEqualTo(EstadoJornada.BORRADOR);
        assertThat(listado.getFirst().jornadas()).isZero();
        assertThat(listado.getFirst().sinAsignar()).as("2 del grupo de 4 y 2 del de 5").isEqualTo(4);
        assertThat(listado.getFirst().descansos()).isEqualTo(1);
        assertThat(listado.get(6).sinAsignar()).as("la fecha 7 solo es del grupo de 5").isEqualTo(2);

        CalendarioDto consulta = calendario.consultar(e.temporada());
        assertThat(consulta.grupos()).extracting(CalendarioDto.GrupoDto::grupoId)
                .as("por orden de categoría y nombre").containsExactly(e.grupo4(), e.grupo1(), e.grupo5());
        assertThat(consulta.grupos().get(1).generado()).isFalse();
        CalendarioDto.GrupoDto impar = consulta.grupos().get(2);
        assertThat(impar.fechas()).hasSize(10).allSatisfy(fecha -> {
            assertThat(fecha.partidos()).hasSize(2);
            assertThat(fecha.descansa()).isNotNull();
        });
        assertThat(impar.fechas().get(4).vuelta()).isEqualTo(1);
        assertThat(impar.fechas().get(5).vuelta()).isEqualTo(2);

        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.auditoria
                         WHERE modulo = 'M-07' AND accion = 'GENERAR_CALENDARIO' AND entidad = 'temporadas'
                           AND entidad_id = :temporada AND perfil_id = :perfil
                           AND (detalle ->> 'totalFechas')::int = 10 AND (detalle ->> 'emparejamientos')::int = 42
                        """)
                .param("temporada", e.temporada().toString()).param("perfil", e.programador())
                .query(Long.class).single()).isEqualTo(1);
    }

    // ── Regeneración (DEC-189) ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-189: regenerar con motivo conserva las filas anteriores como no vigentes, que no aparecen ni cuentan")
    void regenerarConMotivo() {
        Escenario e = escenario();
        calendario.generar(e.temporada(), null);
        CalendarioDto antes = calendario.consultar(e.temporada());
        UUID reemplazado = jdbc.sql("""
                        SELECT id FROM siglim.emparejamientos
                         WHERE grupo_id = :grupo AND numero_fecha = 1 AND equipo_visitante_id IS NOT NULL
                         LIMIT 1
                        """)
                .param("grupo", e.grupo4()).query(UUID.class).single();

        assertThatThrownBy(() -> calendario.generar(e.temporada(), null))
                .as("sin motivo")
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));

        CalendarioGeneradoDto resultado = calendario.generar(e.temporada(),
                new GenerarCalendarioRequest(null, null, MOTIVO));

        assertThat(resultado.grupos()).allSatisfy(grupo -> assertThat(grupo.reemplazado()).isTrue());
        assertThat(resultado.fechasCreadas()).as("las 10 fechas ya existían").isZero();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.emparejamientos
                         WHERE grupo_id IN (:g4, :g5) AND NOT vigente AND reemplazado_por = :perfil
                           AND reemplazado_en IS NOT NULL AND motivo_reemplazo = :motivo
                        """)
                .param("g4", e.grupo4()).param("g5", e.grupo5()).param("perfil", e.programador())
                .param("motivo", MOTIVO).query(Long.class).single()).isEqualTo(42);
        assertThat(vigentes(e.grupo4()) + vigentes(e.grupo5())).isEqualTo(42);

        CalendarioDto despues = calendario.consultar(e.temporada());
        assertThat(forma(despues)).as("misma forma que antes").isEqualTo(forma(antes));
        assertThat(despues.grupos()).flatExtracting(CalendarioDto.GrupoDto::fechas)
                .flatExtracting(CalendarioDto.FechaDto::partidos)
                .extracting(CalendarioDto.PartidoDto::emparejamientoId)
                .doesNotContain(reemplazado);

        FechaDto primera = fechas.listar(e.temporada(), 0, 20).contenido().getFirst();
        assertThat(primera.sinAsignar()).as("solo las filas vigentes cuentan").isEqualTo(4);
        assertThat(primera.descansos()).isEqualTo(1);
        FechaDto detalle = fechas.consultar(primera.id());
        assertThat(detalle.bloques()).flatExtracting(FechaDto.Bloque::sinAsignar).hasSize(4)
                .extracting(FechaDto.SinAsignar::emparejamientoId).doesNotContain(reemplazado);

        // Un emparejamiento reemplazado no se puede asignar a mano (JornadaRepository filtra vigente).
        UUID cancha = crearCancha();
        UUID jornada = jdbc.sql("""
                        INSERT INTO siglim.jornadas (grupo_id, numero, fecha_id, creado_por)
                        VALUES (:grupo, 1, :fecha, :perfil) RETURNING id
                        """)
                .param("grupo", e.grupo4()).param("fecha", primera.id()).param("perfil", e.programador())
                .query(UUID.class).single();
        UUID[] equipos = jdbc.sql("SELECT equipo_local_id, equipo_visitante_id FROM siglim.emparejamientos WHERE id = :id")
                .param("id", reemplazado)
                .query((fila, n) -> new UUID[] {fila.getObject(1, UUID.class), fila.getObject(2, UUID.class)})
                .single();
        assertThatThrownBy(() -> jornadas.registrarPartido(jornada, new CrearPartidoRequest(equipos[0], equipos[1],
                cancha, OffsetDateTime.parse("2031-03-02T10:00:00-06:00"), null, reemplazado)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
    }

    @Test
    @DisplayName("RN-020: con un partido ligado a un emparejamiento, regenerar responde 409 y no cambia nada")
    void regenerarConPartido() {
        Escenario e = escenario();
        calendario.generar(e.temporada(), null);
        UUID jornadaManual = jdbc.sql("""
                        INSERT INTO siglim.jornadas (grupo_id, numero, creado_por) VALUES (:grupo, 1, :perfil) RETURNING id
                        """)
                .param("grupo", e.grupo4()).param("perfil", e.programador()).query(UUID.class).single();
        UUID emparejamiento = jdbc.sql("""
                        SELECT id FROM siglim.emparejamientos
                         WHERE grupo_id = :grupo AND numero_fecha = 1 AND vigente AND equipo_visitante_id IS NOT NULL
                         LIMIT 1
                        """)
                .param("grupo", e.grupo4()).query(UUID.class).single();
        UUID[] equipos = jdbc.sql("SELECT equipo_local_id, equipo_visitante_id FROM siglim.emparejamientos WHERE id = :id")
                .param("id", emparejamiento)
                .query((fila, n) -> new UUID[] {fila.getObject(1, UUID.class), fila.getObject(2, UUID.class)})
                .single();
        UUID partido = jornadas.registrarPartido(jornadaManual, new CrearPartidoRequest(equipos[0], equipos[1],
                crearCancha(), OffsetDateTime.parse("2031-03-02T10:00:00-06:00"), null)).id();
        jdbc.sql("UPDATE siglim.emparejamientos SET partido_id = :partido WHERE id = :id")
                .param("partido", partido).param("id", emparejamiento).update();
        long vigentesAntes = vigentes(e.grupo4()) + vigentes(e.grupo5());

        assertThatThrownBy(() -> calendario.generar(e.temporada(), new GenerarCalendarioRequest(null, null, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("RN-020");
                    assertThat(excepcion.propiedades().get("grupos").toString()).contains(e.grupo4().toString());
                });

        assertThat(vigentes(e.grupo4()) + vigentes(e.grupo5())).isEqualTo(vigentesAntes);
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.emparejamientos WHERE grupo_id IN (:g4, :g5) AND NOT vigente")
                .param("g4", e.grupo4()).param("g5", e.grupo5()).query(Long.class).single()).isZero();

        // Los demás grupos sí se regeneran, con la advertencia de la jornada manual 1 solo si es de ellos.
        CalendarioGeneradoDto soloImpar = calendario.generar(e.temporada(),
                new GenerarCalendarioRequest(null, List.of(e.grupo5()), MOTIVO));
        assertThat(soloImpar.grupos()).extracting(CalendarioGeneradoDto.GrupoGenerado::grupoId)
                .containsExactly(e.grupo5());
        assertThat(soloImpar.advertencias()).isEmpty();
    }

    // ── Días de la fecha (DEC-193) ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-193: PATCH de la fecha agrega un día con motivo; traslape 422, sin motivo 400 y en revisión 409")
    void cambiarDiasDeLaFecha() {
        Escenario e = escenario();
        calendario.generar(e.temporada(), null);
        List<FechaDto> listado = fechas.listar(e.temporada(), 0, 20).contenido();
        FechaDto primera = listado.getFirst();
        LocalDate viernes = INICIO.minusDays(1);

        FechaDto cambiada = fechas.actualizarDias(primera.id(), new ActualizarFechaRequest(
                List.of(INICIO.plusDays(1), viernes, INICIO), "La liga pidió jugar también el viernes"));

        assertThat(cambiada.dias()).containsExactly(viernes, INICIO, INICIO.plusDays(1));
        assertThat(cambiada.bloques()).isNull();
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM siglim.auditoria
                         WHERE accion = 'CAMBIAR_DIAS_FECHA' AND entidad = 'fechas_programacion' AND entidad_id = :id
                           AND motivo = 'La liga pidió jugar también el viernes'
                        """)
                .param("id", primera.id().toString()).query(Long.class).single()).isEqualTo(1);

        assertThatThrownBy(() -> fechas.actualizarDias(listado.get(1).id(), new ActualizarFechaRequest(
                List.of(INICIO.plusDays(1), INICIO.plusDays(7)), "Mover la fecha 2 un día antes")))
                .as("el domingo ya es de la fecha 1")
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.VALIDACION));
        assertThatThrownBy(() -> fechas.actualizarDias(listado.get(1).id(),
                new ActualizarFechaRequest(List.of(INICIO.plusDays(7)), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));

        UUID jornada = jdbc.sql("""
                        INSERT INTO siglim.jornadas (grupo_id, numero, fecha_id, creado_por)
                        VALUES (:grupo, 3, :fecha, :perfil) RETURNING id
                        """)
                .param("grupo", e.grupo4()).param("fecha", listado.get(2).id()).param("perfil", e.programador())
                .query(UUID.class).single();
        jdbc.sql("UPDATE siglim.jornadas SET estado_publicacion = 'REVISION' WHERE id = :id").param("id", jornada).update();
        assertThatThrownBy(() -> fechas.actualizarDias(listado.get(2).id(), new ActualizarFechaRequest(
                List.of(INICIO.plusWeeks(2)), "Solo se juega el sábado de esta fecha")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-090"));

        assertThat(fechas.consultar(listado.get(1).id()).dias()).as("sin cambios tras los rechazos")
                .containsExactly(INICIO.plusDays(7), INICIO.plusDays(8));
    }

    // ── Días de juego de la categoría (DEC-191) ────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-191: el Programador cambia los días de juego de la categoría; se guardan ordenados y se auditan")
    void cambiarDiasDeJuego() {
        Escenario e = escenario();
        UUID categoriaTemporada = jdbc.sql("""
                        SELECT categoria_temporada_id FROM siglim.grupos WHERE id = :grupo
                        """)
                .param("grupo", e.grupo4()).query(UUID.class).single();

        CategoriaTemporadaDto categoria = grupos.actualizarDiasJuego(e.temporada(), categoriaTemporada,
                new ActualizarDiasJuegoRequest(List.of(7, 6)));

        assertThat(categoria.diasJuego()).containsExactly(6, 7);
        assertThat(grupos.consultarCategoria(e.temporada(), categoriaTemporada).diasJuego()).containsExactly(6, 7);
        assertThat(jdbc.sql("""
                        SELECT detalle -> 'diasAnteriores' = '[7]'::jsonb AND detalle -> 'diasJuego' = '[6, 7]'::jsonb
                          FROM siglim.auditoria
                         WHERE modulo = 'M-02' AND accion = 'CAMBIAR_DIAS_JUEGO' AND entidad_id = :id
                        """)
                .param("id", categoriaTemporada.toString()).query(Boolean.class).single()).isTrue();
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    private record Escenario(UUID programador, UUID temporada, UUID grupo4, UUID grupo1, UUID grupo5) {
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
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo, vueltas)
                        VALUES (:temporada, 1, 'Parámetros de prueba', 2)
                        """)
                .param("temporada", temporada).update();
        UUID categoriaA = crearCategoriaTemporada(temporada, "Categoría A");
        UUID categoriaB = crearCategoriaTemporada(temporada, "Categoría B");
        UUID grupo4 = crearGrupo(categoriaA, "Grupo 1", 4);
        UUID grupo1 = crearGrupo(categoriaA, "Grupo 2", 1);
        UUID grupo5 = crearGrupo(categoriaB, "Grupo 1", 5);
        return new Escenario(programador, temporada, grupo4, grupo1, grupo5);
    }

    private UUID crearCategoriaTemporada(UUID temporada, String prefijo) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", prefijo + " " + UUID.randomUUID())
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria)
                .query(UUID.class).single();
    }

    private UUID crearGrupo(UUID categoriaTemporada, String nombre, int equipos) {
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, :nombre) RETURNING id")
                .param("ct", categoriaTemporada).param("nombre", nombre).query(UUID.class).single();
        for (int i = 0; i < equipos; i++) {
            UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                    .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                    .query(UUID.class).single();
            jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre)")
                    .param("club", club).param("grupo", grupo).param("nombre", "Equipo " + UUID.randomUUID())
                    .update();
        }
        return grupo;
    }

    private UUID crearCancha() {
        return jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private long vigentes(UUID grupo) {
        return jdbc.sql("SELECT count(*) FROM siglim.emparejamientos WHERE grupo_id = :grupo AND vigente")
                .param("grupo", grupo).query(Long.class).single();
    }

    /** Equipos que aparecen más de una vez en una fecha de su grupo, en cualquier papel (RN-012). */
    private long repetidosEnUnaFecha(UUID temporada) {
        return jdbc.sql("""
                        SELECT count(*) FROM (
                          SELECT e.grupo_id, e.numero_fecha, equipo
                            FROM siglim.emparejamientos e
                            JOIN siglim.grupos g ON g.id = e.grupo_id
                            JOIN siglim.categorias_temporada ct ON ct.id = g.categoria_temporada_id
                            CROSS JOIN LATERAL (VALUES (e.equipo_local_id), (e.equipo_visitante_id)) AS x(equipo)
                           WHERE ct.temporada_id = :temporada AND e.vigente AND equipo IS NOT NULL
                           GROUP BY 1, 2, 3
                          HAVING count(*) > 1) r
                        """)
                .param("temporada", temporada).query(Long.class).single();
    }

    /** Por grupo: número de fechas y, por fecha, cuántos partidos y si hay descanso. */
    private static List<String> forma(CalendarioDto calendarioDto) {
        return calendarioDto.grupos().stream()
                .map(grupo -> grupo.grupoId() + ":" + grupo.fechas().stream()
                        .map(fecha -> fecha.numero() + "/" + fecha.partidos().size() + "/" + (fecha.descansa() != null))
                        .toList())
                .toList();
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

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}
