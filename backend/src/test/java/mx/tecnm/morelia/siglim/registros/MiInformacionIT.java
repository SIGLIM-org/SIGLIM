package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.DocumentoPropioDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.RegistroPropioDto;
import mx.tecnm.morelia.siglim.registros.ReglaElegibilidad.Causa;
import mx.tecnm.morelia.siglim.registros.TitularesDto.TitularDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-24.1 contra PostgreSQL con RLS, {@code persona_usuario}, {@code es_titular} y las implementaciones
 * de {@code partidos} y {@code disciplina}: el tutor ve a sus menores, el jugador suspendido ve la causa
 * y la vigencia, nadie más de ámbito {@code PROPIO} (ni la liga) llega al detalle, los documentos no
 * exponen el archivo y los próximos partidos salen solo de jornadas publicadas (RF-144, DEC-135 a
 * DEC-140).
 */
class MiInformacionIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String URL_SECRETA = "https://bucket-secreto.s3.amazonaws.com/registros/foto-ana.jpg";
    private static final String CLAVE_SECRETA = "liga/registros/clave-secreta-ana.jpg";
    private static final String HASH_SECRETO = "ab".repeat(32);

    @Autowired
    private MiInformacionService servicio;

    @Test
    @DisplayName("H-24 criterio 1 · DEC-135: un tutor con dos menores ve a ambos por nombre y el detalle de cada uno")
    void tutorConDosMenores() {
        Equipos equipos = equipos();
        UUID tutor = persona("Tutor", "Pérez", LocalDate.parse("1985-03-03"));
        UUID beto = persona("Beto", "Pérez", LocalDate.parse("2014-05-05"));
        UUID ana = persona("Ana", "Pérez", LocalDate.parse("2016-06-06"));
        tutela(beto, tutor);
        tutela(ana, tutor);
        UUID registroAna = registro(ana, equipos.propio(), "ACTIVO");
        UUID registroBeto = registro(beto, equipos.propio(), "PENDIENTE_VALIDACION");
        UUID perfil = perfilPropio("Tutor de prueba", tutor);

        iniciarSesion(perfil);
        List<TitularDto> titulares = servicio.titulares().titulares();

        assertThat(titulares).containsExactly(
                new TitularDto(tutor, "Tutor Pérez Prueba", true, false),
                new TitularDto(ana, "Ana Pérez Prueba", false, true),
                new TitularDto(beto, "Beto Pérez Prueba", false, true));

        MiInformacionDto deAna = servicio.consultar(ana);
        assertThat(deAna.persona().id()).isEqualTo(ana);
        assertThat(deAna.persona().esMenor()).isTrue();
        assertThat(deAna.persona().curpEnmascarada()).contains("******").hasSize(18);
        assertThat(deAna.registros()).extracting(RegistroPropioDto::id).containsExactly(registroAna);
        assertThat(deAna.registros().getFirst().elegibilidad().elegible()).isTrue();

        MiInformacionDto deBeto = servicio.consultar(beto);
        assertThat(deBeto.registros()).extracting(RegistroPropioDto::id).containsExactly(registroBeto);
        assertThat(deBeto.registros().getFirst().elegibilidad().causas()).extracting(Causa::codigo)
                .containsExactly("RF-019");
    }

    @Test
    @DisplayName("H-24 criterio 2 · RF-020: un jugador con suspensión VIGENTE ve elegible=false con artículo, folio y vigencia")
    void jugadorSuspendido() {
        Equipos equipos = equipos();
        UUID jugador = persona("Jugador", "Sancionado", LocalDate.parse("1995-01-01"));
        UUID registro = registro(jugador, equipos.propio(), "ACTIVO");
        String articulo = "H24-" + ThreadLocalRandom.current().nextInt(1_000_000);
        String folio = "EXP-H24-" + UUID.randomUUID().toString().substring(0, 8);
        UUID sancion = suspender(jugador, equipos.temporada(), articulo, folio);
        UUID perfil = perfilPropio("Jugador de prueba", jugador);

        iniciarSesion(perfil);
        MiInformacionDto informacion = servicio.consultar(jugador);

        RegistroPropioDto propio = informacion.registros().getFirst();
        assertThat(propio.id()).isEqualTo(registro);
        assertThat(propio.elegibilidad().elegible()).isFalse();
        assertThat(propio.elegibilidad().causas()).containsExactly(new Causa("RF-020",
                "Suspensión de 3 partidos por el artículo " + articulo + " («Conducta antideportiva»): faltan 2 "
                        + "partidos, vigente hasta el 31/12/2031."));
        assertThat(informacion.sanciones()).singleElement().satisfies(s -> {
            assertThat(s.id()).isEqualTo(sancion);
            assertThat(s.tipo()).isEqualTo("SUSPENSION_PARTIDOS");
            assertThat(s.estado()).isEqualTo("VIGENTE");
            assertThat(s.partidos()).isEqualTo(3);
            assertThat(s.partidosCumplidos()).isEqualTo(1);
            assertThat(s.vigenteDesde()).isEqualTo(LocalDate.parse("2031-01-15"));
            assertThat(s.vigenteHasta()).isEqualTo(LocalDate.parse("2031-12-31"));
            assertThat(s.articulo().numero()).isEqualTo(articulo);
            assertThat(s.articulo().descripcion()).isEqualTo("Conducta antideportiva");
            assertThat(s.folio()).isEqualTo(folio);
        });
    }

    @Test
    @DisplayName("H-24 criterio 3 · VAL-024: otro usuario PROPIO y uno con ámbito LIGA reciben 404 por quien no tutelan")
    void otrosUsuariosRecibenNoEncontrado() {
        Equipos equipos = equipos();
        UUID tutor = persona("Tutora", "Real", LocalDate.parse("1980-02-02"));
        UUID menor = persona("Menor", "Ajeno", LocalDate.parse("2015-07-07"));
        tutela(menor, tutor);
        registro(menor, equipos.propio(), "ACTIVO");
        UUID otraPersona = persona("Otra", "Persona", LocalDate.parse("1990-09-09"));

        iniciarSesion(perfilPropio("Otro jugador", otraPersona));
        assertThatThrownBy(() -> servicio.consultar(menor)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.consultar(tutor)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.consultar(UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(servicio.titulares().titulares()).extracting(TitularDto::personaId).containsExactly(otraPersona);

        iniciarSesion(perfilDeSecretario());
        assertThatThrownBy(() -> servicio.consultar(menor)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(servicio.titulares().titulares()).isEmpty();
    }

    @Test
    @DisplayName("VAL-015 · DEC-136: una cuenta sin registros.registro:CONSULTAR recibe 403 en ambas rutas")
    void sinPermiso() {
        UUID persona = persona("Sin", "Permiso", LocalDate.parse("1990-10-10"));
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado, persona_id) VALUES ('Sin rol', :persona) RETURNING id")
                .param("persona", persona).query(UUID.class).single();

        iniciarSesion(perfil);
        assertThatThrownBy(() -> servicio.titulares()).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(error.codigo()).isEqualTo(ErroresTecnicos.SIN_PERMISO);
        });
        assertThatThrownBy(() -> servicio.consultar(persona)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> assertThat(error.codigo()).isEqualTo(ErroresTecnicos.SIN_PERMISO));
    }

    @Test
    @DisplayName("RNF-018 · DEC-137: los documentos van con su avance y sin URL, clave ni hash del archivo")
    void documentosSinDatosDelArchivo() {
        Equipos equipos = equipos();
        UUID jugador = persona("Jugadora", "Documentada", LocalDate.parse("1992-02-02"));
        UUID registro = registro(jugador, equipos.propio(), "PENDIENTE_VALIDACION");
        UUID perfil = perfilPropio("Jugadora de prueba", jugador);
        UUID documento = documento(registro, perfil);

        iniciarSesion(perfil);
        MiInformacionDto informacion = servicio.consultar(jugador);

        RegistroPropioDto propio = informacion.registros().getFirst();
        assertThat(propio.documentos().completo()).isFalse();
        assertThat(propio.documentos().requisitos()).isNotEmpty();
        List<DocumentoPropioDto> fotos = propio.documentos().requisitos().stream()
                .filter(requisito -> requisito.tipoDocumento().codigo().equals("FOTOGRAFIA"))
                .flatMap(requisito -> requisito.documentos().stream())
                .toList();
        assertThat(fotos).singleElement().satisfies(foto -> {
            assertThat(foto.id()).isEqualTo(documento);
            assertThat(foto.estadoValidacion()).isEqualTo(EstadoValidacion.RECHAZADO);
            assertThat(foto.observaciones()).isEqualTo("La fotografía está borrosa");
            assertThat(foto.cargadoEn()).isNotNull();
        });
        // El toString de los records recorre todos los campos de la respuesta.
        assertThat(informacion.toString()).doesNotContain(URL_SECRETA, CLAVE_SECRETA, HASH_SECRETO, "bucket-secreto",
                "image/jpeg");
    }

    @Test
    @DisplayName("DEC-140: próximos solo de jornadas PUBLICADO sin FINALIZADO ni CANCELADO; jugados con resultado vigente")
    void partidosDelEquipo() {
        Equipos equipos = equipos();
        UUID jugador = persona("Jugador", "Partidos", LocalDate.parse("1993-03-03"));
        registro(jugador, equipos.propio(), "ACTIVO");
        UUID cancha = cancha();
        UUID publicada = jornada(equipos.grupo(), 1, "PUBLICADO");
        UUID borrador = jornada(equipos.grupo(), 2, "BORRADOR");
        UUID segundo = partido(publicada, equipos.rival(), equipos.propio(), cancha, "2031-05-10T18:00:00-06:00", "PROGRAMADO");
        UUID primero = partido(publicada, equipos.propio(), equipos.rival(), cancha, "2031-05-03T18:00:00-06:00", "PROGRAMADO");
        partido(publicada, equipos.propio(), equipos.rival(), cancha, "2031-05-17T18:00:00-06:00", "CANCELADO");
        partido(borrador, equipos.propio(), equipos.rival(), cancha, "2031-05-24T18:00:00-06:00", "PROGRAMADO");
        UUID jugado = partido(publicada, equipos.propio(), equipos.rival(), cancha, "2031-04-26T18:00:00-06:00",
                "FINALIZADO");
        resultado(jugado, equipos.temporada());
        UUID perfil = perfilPropio("Jugador con partidos", jugador);

        iniciarSesion(perfil);
        RegistroPropioDto propio = servicio.consultar(jugador).registros().getFirst();

        assertThat(propio.proximosPartidos()).extracting(PartidoPropio::id).containsExactly(primero, segundo);
        PartidoPropio local = propio.proximosPartidos().getFirst();
        assertThat(local.esLocal()).isTrue();
        assertThat(local.jornada()).isEqualTo(1);
        assertThat(local.estado()).isEqualTo("PROGRAMADO");
        assertThat(local.inicio()).isEqualTo(OffsetDateTime.parse("2031-05-03T18:00:00-06:00"));
        assertThat(local.local().id()).isEqualTo(equipos.propio());
        assertThat(local.resultado()).isNull();
        assertThat(propio.proximosPartidos().get(1).esLocal()).isFalse();

        assertThat(propio.partidosJugados()).singleElement().satisfies(partido -> {
            assertThat(partido.id()).isEqualTo(jugado);
            assertThat(partido.estado()).isEqualTo("FINALIZADO");
            assertThat(partido.resultado()).isEqualTo(new PartidoPropio.ResultadoDto(2, 1, null, null));
        });
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    /** Dos equipos de un grupo de una temporada abierta con parámetros (para el bloque de cancha). */
    private record Equipos(UUID temporada, UUID grupo, UUID propio, UUID rival) {
    }

    private Equipos equipos() {
        UUID temporada = uuid(jdbc.sql("""
                INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                VALUES (:liga, :nombre, DATE '2031-01-01') RETURNING id
                """).param("liga", LIGA).param("nombre", "Temporada H24 " + UUID.randomUUID()));
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo) VALUES (:t, 1, 'Prueba H-24')")
                .param("t", temporada).update();
        UUID categoria = uuid(jdbc.sql("""
                INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                RETURNING id
                """).param("liga", LIGA).param("nombre", "Libre H24 " + UUID.randomUUID()));
        UUID categoriaTemporada = uuid(jdbc.sql("""
                INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id) VALUES (:t, :c) RETURNING id
                """).param("t", temporada).param("c", categoria));
        UUID grupo = uuid(jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada));
        return new Equipos(temporada, grupo, equipo(grupo, "Halcones"), equipo(grupo, "Lobos"));
    }

    private UUID equipo(UUID grupo, String nombre) {
        UUID club = uuid(jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + nombre + " " + UUID.randomUUID()));
        return uuid(jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", nombre));
    }

    /** Persona con apellido materno «Prueba» y una CURP válida al azar. */
    private UUID persona(String nombre, String apellido, LocalDate nacimiento) {
        String curp = "PEAA" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)) + "HMNRRN"
                + (char) ('A' + ThreadLocalRandom.current().nextInt(26)) + ThreadLocalRandom.current().nextInt(10);
        return uuid(jdbc.sql("""
                INSERT INTO siglim.personas (curp, nombre, apellido_paterno, apellido_materno, fecha_nacimiento)
                VALUES (:curp, :nombre, :apellido, 'Prueba', :nacimiento) RETURNING id
                """).param("curp", curp).param("nombre", nombre).param("apellido", apellido)
                .param("nacimiento", nacimiento));
    }

    private void tutela(UUID menor, UUID tutor) {
        jdbc.sql("INSERT INTO siglim.tutelas (menor_id, tutor_id, parentesco) VALUES (:menor, :tutor, 'Madre')")
                .param("menor", menor).param("tutor", tutor).update();
    }

    private UUID registro(UUID persona, UUID equipo, String estado) {
        return uuid(jdbc.sql("""
                INSERT INTO siglim.registros_jugador (persona_id, equipo_id, estado, alta_en)
                VALUES (:persona, :equipo, CAST(:estado AS siglim.estado_registro), DATE '2031-01-10') RETURNING id
                """).param("persona", persona).param("equipo", equipo).param("estado", estado));
    }

    /** Documento FOTOGRAFIA rechazado con URL, clave y hash reconocibles, que nunca deben salir. */
    private UUID documento(UUID registro, UUID cargadoPor) {
        return uuid(jdbc.sql("""
                INSERT INTO siglim.documentos (tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime,
                                               tamano_bytes, sha256, estado_validacion, observaciones, cargado_por)
                SELECT id, :registro, 'bucket-secreto', :clave, :url, 'image/jpeg', 2048, :sha, 'RECHAZADO',
                       'La fotografía está borrosa', :perfil
                  FROM siglim.tipos_documento WHERE codigo = 'FOTOGRAFIA'
                RETURNING id
                """).param("registro", registro).param("clave", CLAVE_SECRETA + UUID.randomUUID())
                .param("url", URL_SECRETA).param("sha", HASH_SECRETO).param("perfil", cargadoPor));
    }

    /** Artículo → expediente → resolución → sanción VIGENTE de 3 partidos con 1 cumplido. */
    private UUID suspender(UUID persona, UUID temporada, String articuloNumero, String folio) {
        UUID articulo = uuid(jdbc.sql("""
                INSERT INTO siglim.articulos (liga_id, numero, descripcion, tipo_sancion, sujeto, partidos_suspension)
                VALUES (:liga, :numero, 'Conducta antideportiva', 'SUSPENSION_PARTIDOS', 'JUGADOR', 3) RETURNING id
                """).param("liga", LIGA).param("numero", articuloNumero));
        UUID resolucion = resolucion(temporada, folio, articulo);
        return uuid(jdbc.sql("""
                INSERT INTO siglim.sanciones (resolucion_id, tipo, sujeto, efecto, persona_id, temporada_id, partidos,
                                              partidos_cumplidos, vigente_desde, vigente_hasta)
                VALUES (:resolucion, 'SUSPENSION_PARTIDOS', 'JUGADOR', 'HACIA_ADELANTE', :persona, :temporada, 3, 1,
                        DATE '2031-01-15', DATE '2031-12-31')
                RETURNING id
                """).param("resolucion", resolucion).param("persona", persona).param("temporada", temporada));
    }

    private UUID resolucion(UUID temporada, String folio, UUID articulo) {
        UUID expediente = uuid(jdbc.sql("""
                INSERT INTO siglim.expedientes (liga_id, temporada_id, folio, origen, descripcion)
                VALUES (:liga, :temporada, :folio, 'MANUAL', 'Expediente de prueba H-24') RETURNING id
                """).param("liga", LIGA).param("temporada", temporada).param("folio", folio));
        return uuid(jdbc.sql("""
                INSERT INTO siglim.resoluciones (expediente_id, articulo_id, fundamentos, sin_sancion, resuelta_por)
                VALUES (:expediente, :articulo, 'Fundamentos de prueba', :sinSancion, :perfil) RETURNING id
                """).param("expediente", expediente).param("articulo", articulo)
                .param("sinSancion", articulo == null).param("perfil", crearPerfil("Comisión de prueba")));
    }

    private UUID cancha() {
        return uuid(jdbc.sql("""
                INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA') RETURNING id
                """).param("liga", LIGA).param("nombre", "Cancha H24 " + UUID.randomUUID()));
    }

    private UUID jornada(UUID grupo, int numero, String estado) {
        return uuid(jdbc.sql("""
                INSERT INTO siglim.jornadas (grupo_id, numero, estado_publicacion)
                VALUES (:grupo, :numero, CAST(:estado AS siglim.estado_publicacion)) RETURNING id
                """).param("grupo", grupo).param("numero", numero).param("estado", estado));
    }

    private UUID partido(UUID jornada, UUID local, UUID visitante, UUID cancha, String inicio, String estado) {
        return uuid(jdbc.sql("""
                INSERT INTO siglim.partidos (jornada_id, equipo_local_id, equipo_visitante_id, cancha_id, inicio,
                                             fin_bloque, estado)
                VALUES (:jornada, :local, :visitante, :cancha, :inicio, :inicio,
                        CAST(:estado AS siglim.estado_partido))
                RETURNING id
                """).param("jornada", jornada).param("local", local).param("visitante", visitante)
                .param("cancha", cancha).param("inicio", OffsetDateTime.parse(inicio)).param("estado", estado));
    }

    /** Resultado oficial 2-1 por resolución (sin cédula), vigente por {@code tg_resultados_version}. */
    private void resultado(UUID partido, UUID temporada) {
        UUID resolucion = resolucion(temporada, "EXP-RES-" + UUID.randomUUID().toString().substring(0, 8), null);
        jdbc.sql("""
                        INSERT INTO siglim.resultados (partido_id, goles_local, goles_visitante, origen, resolucion_id,
                                                       motivo)
                        VALUES (:partido, 2, 1, 'RESOLUCION', :resolucion, 'Resultado de prueba')
                        """)
                .param("partido", partido).param("resolucion", resolucion).update();
    }

    /** Perfil vinculado a la persona con el rol JUGADOR_TUTOR en ámbito PROPIO. */
    private UUID perfilPropio(String nombre, UUID persona) {
        UUID perfil = uuid(jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado, persona_id) VALUES (:nombre, :persona) RETURNING id")
                .param("nombre", nombre).param("persona", persona));
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, 'PROPIO' FROM siglim.roles WHERE codigo = 'JUGADOR_TUTOR'
                        """)
                .param("perfil", perfil).update();
        return perfil;
    }

    private UUID perfilDeSecretario() {
        UUID perfil = crearPerfil("Secretario de prueba");
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, 'LIGA' FROM siglim.roles WHERE codigo = 'SECRETARIO'
                        """)
                .param("perfil", perfil).update();
        return perfil;
    }

    private UUID crearPerfil(String nombre) {
        return uuid(jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES (:nombre) RETURNING id")
                .param("nombre", nombre));
    }

    private static UUID uuid(StatementSpec sentencia) {
        return sentencia.query(UUID.class).single();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
