package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.organizacion.CrearPersonaRequest;
import mx.tecnm.morelia.siglim.organizacion.PersonaService;
import mx.tecnm.morelia.siglim.organizacion.RegistrarTutelaRequest;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDetalleDto.TutorDto;
import mx.tecnm.morelia.siglim.seguridad.ConsentimientoService;
import mx.tecnm.morelia.siglim.seguridad.RegistrarConsentimientoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-10.5 contra PostgreSQL: el ámbito del listado en el {@code WHERE} y en el {@code COUNT}, los
 * filtros y el orden, y el detalle con la CURP según {@code organizacion.persona:CONSULTAR},
 * {@code esMenor} y los tutores (RF-017, DEC-109, DEC-110). Cada prueba filtra por su propia
 * temporada para no depender de los registros de otras pruebas.
 */
class ConsultaRegistrosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate ADULTO = LocalDate.parse("1990-01-01");
    private static final LocalDate MENOR = LocalDate.parse("2015-01-01");

    @Autowired
    private RegistroJugadorService registros;

    @Autowired
    private PersonaService personas;

    @Autowired
    private ConsentimientoService consentimientos;

    @Test
    @DisplayName("RF-017 · DEC-109 · VAL-024: la Directiva solo ve los registros de su club y totalElementos cuenta solo esos")
    void directivaSoloVeSuClub() {
        UUID temporada = temporada();
        Club clubA = clubAfiliado(temporada, "Halcones");
        Club clubB = clubAfiliado(temporada, "Pumas");
        iniciarSesion(clubA.directiva());
        registrar(clubA, "Uno", ADULTO);
        registrar(clubA, "Dos", ADULTO);
        iniciarSesion(clubB.directiva());
        registrar(clubB, "Tres", ADULTO);

        iniciarSesion(clubA.directiva());
        Pagina<RegistroJugadorResumenDto> propios = registros.listar(filtro(temporada, null, null, null, 0, 1));

        assertThat(propios.totalElementos()).isEqualTo(2);
        assertThat(propios.totalPaginas()).isEqualTo(2);
        assertThat(propios.contenido()).hasSize(1)
                .allSatisfy(fila -> assertThat(fila.club().id()).isEqualTo(clubA.id()));
        assertThat(registros.listar(filtro(temporada, null, null, null, 0, 20)).contenido())
                .extracting(fila -> fila.club().id()).containsOnly(clubA.id());
    }

    @Test
    @DisplayName("DEC-109 · VAL-024: un clubId ajeno devuelve una página vacía, no 403 ni 404")
    void clubAjenoDaPaginaVacia() {
        UUID temporada = temporada();
        Club clubA = clubAfiliado(temporada, "Leones");
        Club clubB = clubAfiliado(temporada, "Tigres");
        iniciarSesion(clubB.directiva());
        registrar(clubB, "Ajeno", ADULTO);

        iniciarSesion(clubA.directiva());
        Pagina<RegistroJugadorResumenDto> pagina = registros.listar(
                new FiltroRegistros(null, temporada, clubB.id(), null, null, 0, 20));

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        assertThat(pagina.totalPaginas()).isZero();
    }

    @Test
    @DisplayName("RF-017 · DEC-109: el Secretario (ámbito LIGA) ve los registros de todos los clubes")
    void secretarioVeTodos() {
        UUID temporada = temporada();
        Club clubA = clubAfiliado(temporada, "Águilas");
        Club clubB = clubAfiliado(temporada, "Coyotes");
        iniciarSesion(clubA.directiva());
        registrar(clubA, "Uno", ADULTO);
        iniciarSesion(clubB.directiva());
        registrar(clubB, "Dos", ADULTO);

        iniciarSesion(perfilConRol("SECRETARIO"));
        Pagina<RegistroJugadorResumenDto> pagina = registros.listar(filtro(temporada, null, null, null, 0, 20));

        assertThat(pagina.totalElementos()).isEqualTo(2);
        assertThat(pagina.contenido()).extracting(fila -> fila.club().id())
                .containsExactlyInAnyOrder(clubA.id(), clubB.id());
        assertThat(pagina.contenido()).allSatisfy(fila -> {
            assertThat(fila.persona().curpEnmascarada()).matches("[A-Z]{4}\\*{6}[A-Z]{3}\\*{3}[0-9A-Z]{2}");
            assertThat(fila.esMenor()).isFalse();
            assertThat(fila.temporada().id()).isEqualTo(temporada);
        });
    }

    @Test
    @DisplayName("DEC-109: filtra por estado y ordena por creadoEn,asc por defecto, creadoEn,desc y nombre,asc")
    void filtroPorEstadoYOrden() {
        UUID temporada = temporada();
        Club club = clubAfiliado(temporada, "Venados");
        iniciarSesion(club.directiva());
        UUID zapata = registrar(club, "Zapata", ADULTO);
        UUID alvarez = registrar(club, "Alvarez", ADULTO);
        UUID mendez = registrar(club, "Mendez", ADULTO);
        iniciarSesion(perfilConRol("SECRETARIO"));
        registros.validar(mendez, new ValidarRegistroRequest(DecisionRegistro.RECHAZADO,
                "La CURP no corresponde a la persona"));

        iniciarSesion(club.directiva());
        assertThat(ids(filtro(temporada, null, null, null, 0, 20))).containsExactly(zapata, alvarez, mendez);
        assertThat(ids(filtro(temporada, null, "creadoEn,desc", null, 0, 20))).containsExactly(mendez, alvarez, zapata);
        assertThat(ids(filtro(temporada, null, "nombre,asc", null, 0, 20))).containsExactly(alvarez, mendez, zapata);
        assertThat(ids(filtro(temporada, "PENDIENTE_VALIDACION", null, null, 0, 20))).containsExactly(zapata, alvarez);
        assertThat(ids(filtro(temporada, "RECHAZADO,ACTIVO", null, null, 0, 20))).containsExactly(mendez);
        assertThat(registros.listar(filtro(temporada, "RECHAZADO", null, null, 0, 20)).totalElementos()).isEqualTo(1);
        assertThat(ids(new FiltroRegistros(null, temporada, null, club.equipo(), null, 0, 20)))
                .containsExactly(zapata, alvarez, mendez);
    }

    @Test
    @DisplayName("DEC-110 · RNF-012: el detalle da la CURP completa al Secretario y a la Directiva del club, no a la Comisión")
    void detalleConCurpSegunPermiso() {
        UUID temporada = temporada();
        Club clubA = clubAfiliado(temporada, "Jaguares");
        Club clubB = clubAfiliado(temporada, "Zorros");
        iniciarSesion(clubA.directiva());
        String curp = curpAleatoria();
        UUID registro = registrar(clubA, curp, "Ruiz", ADULTO);

        RegistroJugadorDetalleDto directiva = registros.consultar(registro);
        assertThat(directiva.persona().curp()).isEqualTo(curp);
        assertThat(directiva.persona().curpEnmascarada()).isEqualTo(EnmascaradoCurp.enmascarar(curp));
        assertThat(directiva.esMenor()).isFalse();
        assertThat(directiva.tutores()).isEmpty();
        assertThat(directiva.club().id()).isEqualTo(clubA.id());

        iniciarSesion(perfilConRol("SECRETARIO"));
        assertThat(registros.consultar(registro).persona().curp()).isEqualTo(curp);

        // La Comisión tiene registros.registro:CONSULTAR (LIGA) pero no organizacion.persona:CONSULTAR:
        // permite responde false sin dañar la transacción de solo lectura.
        iniciarSesion(perfilConRol("COMISION_DISCIPLINARIA"));
        RegistroJugadorDetalleDto comision = registros.consultar(registro);
        assertThat(comision.persona().curp()).isNull();
        assertThat(comision.persona().curpEnmascarada()).isEqualTo(EnmascaradoCurp.enmascarar(curp));

        iniciarSesion(clubB.directiva());
        assertThatThrownBy(() -> registros.consultar(registro)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> registros.consultar(UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("DEC-109 · VAL-025: el registro de un menor dice esMenor y lista su tutor vigente sin CURP")
    void menorConTutores() {
        UUID temporada = temporada();
        Club club = clubAfiliado(temporada, "Infantil");
        iniciarSesion(club.directiva());
        UUID menor = registrarPersona(curpAleatoria(), "Menor", MENOR);
        UUID tutor = registrarPersona(curpAleatoria(), "Tutora", ADULTO);
        personas.registrarTutela(menor, new RegistrarTutelaRequest(tutor, "Madre"));
        consentimientos.registrar(menor, new RegistrarConsentimientoRequest(avisoDemostracion(),
                List.of("REGISTRO_DEPORTIVO"), tutor));
        UUID registro = registros.registrar(new CrearRegistroJugadorRequest(menor, club.equipo())).id();

        RegistroJugadorDetalleDto detalle = registros.consultar(registro);

        assertThat(detalle.esMenor()).isTrue();
        assertThat(detalle.tutores()).extracting(TutorDto::parentesco).containsExactly("Madre");
        assertThat(detalle.tutores().getFirst().nombreCompleto()).contains("Tutora");
        assertThat(registros.listar(filtro(temporada, null, null, null, 0, 20)).contenido())
                .singleElement().satisfies(fila -> assertThat(fila.esMenor()).isTrue());
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────────────────────

    /** Club con su directiva, un equipo en la temporada y su afiliación aprobada. */
    private record Club(UUID id, UUID equipo, UUID directiva) {
    }

    private List<UUID> ids(FiltroRegistros filtro) {
        return registros.listar(filtro).contenido().stream().map(RegistroJugadorResumenDto::id).toList();
    }

    private static FiltroRegistros filtro(UUID temporada, String estado, String orden, UUID equipo, int pagina,
            int tamano) {
        return new FiltroRegistros(estado, temporada, null, equipo, orden, pagina, tamano);
    }

    private UUID registrar(Club club, String apellido, LocalDate nacimiento) {
        return registrar(club, curpAleatoria(), apellido, nacimiento);
    }

    private UUID registrar(Club club, String curp, String apellido, LocalDate nacimiento) {
        UUID persona = registrarPersona(curp, apellido, nacimiento);
        return registros.registrar(new CrearRegistroJugadorRequest(persona, club.equipo())).id();
    }

    private UUID registrarPersona(String curp, String apellido, LocalDate nacimiento) {
        return personas.registrar(new CrearPersonaRequest(curp, "Nombre", apellido, null, nacimiento, null, null)).id();
    }

    private Club clubAfiliado(UUID temporada, String prefijo) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + prefijo + " " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Categoría " + UUID.randomUUID()).query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria).query(UUID.class).single();
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
        UUID equipo = jdbc.sql("""
                        INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre)
                        RETURNING id
                        """)
                .param("club", club).param("grupo", grupo).param("nombre", prefijo + " " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.afiliaciones (club_id, temporada_id, folio, colores_uniforme, estado,
                                                         capturada_por)
                        VALUES (:club, :temporada, :folio, 'Azul y blanco', 'APROBADA', :perfil)
                        """)
                .param("club", club).param("temporada", temporada)
                .param("folio", "F-" + ThreadLocalRandom.current().nextInt(1_000_000_000))
                .param("perfil", crearPerfil()).update();
        UUID directiva = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", directiva).param("club", club).update();
        return new Club(club, equipo, directiva);
    }

    /** Temporada en configuración (no ACTIVA: solo puede haber una por liga). */
    private UUID temporada() {
        return jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2033-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).query(UUID.class).single();
    }

    /** Perfil con un rol de ámbito {@code LIGA}. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, 'LIGA' FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Prueba') RETURNING id")
                .query(UUID.class).single();
    }

    private UUID avisoDemostracion() {
        return jdbc.sql("""
                        SELECT id FROM siglim.avisos_privacidad
                         WHERE liga_id = :liga AND tipo = 'SIMPLIFICADO' AND version = 'v1'
                        """).param("liga", LIGA).query(UUID.class).single();
    }

    private static String curpAleatoria() {
        String digitos = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return "COJU" + digitos + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
