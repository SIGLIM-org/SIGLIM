package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.CrearPersonaRequest;
import mx.tecnm.morelia.siglim.organizacion.PersonaService;
import mx.tecnm.morelia.siglim.organizacion.RegistrarTutelaRequest;
import mx.tecnm.morelia.siglim.seguridad.ConsentimientoService;
import mx.tecnm.morelia.siglim.seguridad.RegistrarConsentimientoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-09.2 contra PostgreSQL: criterios 1 a 4 de H-09 (alta pendiente con historial, VAL-017 entre
 * clubes con nombres, VAL-025 de un menor e identidad reutilizada entre temporadas), ámbito de club,
 * afiliación aprobada y auditoría sin datos personales.
 */
class RegistrosJugadorIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate ADULTO = LocalDate.parse("1990-01-01");
    private static final LocalDate MENOR = LocalDate.parse("2015-01-01");

    @Autowired
    private RegistroJugadorService registros;

    @Autowired
    private PersonaService personas;

    @Autowired
    private ConsentimientoService consentimientos;

    /** Datos personales que ninguna fila de auditoría de la prueba puede contener. */
    private final List<String> datosPersonales = new ArrayList<>();

    @Test
    @DisplayName("H-09 criterio 1 · RF-013: una CURP nueva queda PENDIENTE_VALIDACION con historial y auditoría")
    void curpNuevaQuedaPendiente() {
        UUID temporada = temporada();
        Club club = clubAfiliado(temporada, "Halcones");
        iniciarSesion(club.directiva());
        UUID persona = registrarPersona(ADULTO);

        RegistroJugadorDto registro = registros.registrar(new CrearRegistroJugadorRequest(persona, club.equipo()));

        assertThat(registro.estado()).isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);
        assertThat(registro.persona().id()).isEqualTo(persona);
        assertThat(registro.persona().nombreCompleto()).endsWith("Prueba");
        assertThat(registro.equipo().id()).isEqualTo(club.equipo());
        assertThat(registro.club().id()).isEqualTo(club.id());
        assertThat(registro.temporada().id()).isEqualTo(temporada);
        assertThat(registro.categoria().nombre()).startsWith("Categoría");
        assertThat(registro.altaEn()).isNotNull();
        assertThat(jdbc.sql("""
                        SELECT estado_nuevo || ' ' || motivo FROM siglim.registro_estado_historial
                         WHERE registro_id = :id
                        """).param("id", registro.id()).query(String.class).list())
                .containsExactly("PENDIENTE_VALIDACION Alta del registro");
        assertThat(jdbc.sql("""
                        SELECT accion || ' ' || modulo || ' ' || (detalle ->> 'estado') || ' ' || (detalle ->> 'temporadaId')
                          FROM siglim.auditoria WHERE entidad = 'registros_jugador' AND entidad_id = :id
                        """).param("id", registro.id().toString()).query(String.class).list())
                .containsExactly("REGISTRAR_REGISTRO_JUGADOR M-04 PENDIENTE_VALIDACION " + temporada);
        verificarAuditoriaSinDatosPersonales();
    }

    @Test
    @DisplayName("H-09 criterio 2 · VAL-017: la Directiva B ve el club y el equipo donde ya está la CURP, sin su estado")
    void curpConRegistroActivoEnOtroClub() {
        UUID temporada = temporada();
        Club clubA = clubAfiliado(temporada, "Águilas");
        Club clubB = clubAfiliado(temporada, "Pumas");
        iniciarSesion(clubA.directiva());
        UUID persona = registrarPersona(ADULTO);
        registros.registrar(new CrearRegistroJugadorRequest(persona, clubA.equipo()));

        iniciarSesion(clubB.directiva());
        assertThatThrownBy(() -> registros.registrar(new CrearRegistroJugadorRequest(persona, clubB.equipo())))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-017");
                    assertThat(error.propiedades()).containsOnlyKeys("club", "equipo");
                    assertThat(error.propiedades()).containsEntry("club", clubA.nombre())
                            .containsEntry("equipo", clubA.nombreEquipo());
                    assertThat(error.getMessage()).contains(clubA.nombre(), clubA.nombreEquipo())
                            .doesNotContain("PENDIENTE");
                });
        assertThat(registrosDe(persona)).isEqualTo(1);
    }

    @Test
    @DisplayName("H-09 criterio 3 · VAL-025: un menor sin tutor y sin consentimiento no se registra; con ambos sí")
    void menorNecesitaTutorYConsentimiento() {
        UUID temporada = temporada();
        Club club = clubAfiliado(temporada, "Infantil");
        iniciarSesion(club.directiva());
        UUID menor = registrarPersona(MENOR);
        UUID tutor = registrarPersona(ADULTO);
        CrearRegistroJugadorRequest peticion = new CrearRegistroJugadorRequest(menor, club.equipo());

        esperarRequisito(peticion, "TUTOR");
        personas.registrarTutela(menor, new RegistrarTutelaRequest(tutor, "Madre"));
        esperarRequisito(peticion, "CONSENTIMIENTO");
        assertThat(registrosDe(menor)).isZero();

        consentimientos.registrar(menor, new RegistrarConsentimientoRequest(avisoDemostracion(),
                List.of("REGISTRO_DEPORTIVO"), tutor));
        assertThat(registros.registrar(peticion).estado()).isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);
    }

    @Test
    @DisplayName("H-09 criterio 4 · RF-010: la persona de una temporada anterior se reutiliza sin duplicarse")
    void personaDeOtraTemporadaSeReutiliza() {
        UUID anterior = temporada();
        UUID nueva = temporada();
        Club enAnterior = clubAfiliado(anterior, "Veteranos");
        iniciarSesion(enAnterior.directiva());
        String curp = curpAleatoria();
        UUID persona = registrarPersona(curp, ADULTO);
        registros.registrar(new CrearRegistroJugadorRequest(persona, enAnterior.equipo()));

        UUID equipoNuevo = equipo(enAnterior.id(), nueva, "Veteranos " + UUID.randomUUID());
        afiliar(enAnterior.id(), nueva, "APROBADA");
        RegistroJugadorDto enNueva = registros.registrar(new CrearRegistroJugadorRequest(persona, equipoNuevo));

        assertThat(enNueva.persona().id()).isEqualTo(persona);
        assertThat(enNueva.temporada().id()).isEqualTo(nueva);
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.personas WHERE curp = :curp")
                .param("curp", curp).query(Integer.class).single()).isEqualTo(1);
        assertThat(registrosDe(persona)).isEqualTo(2);
    }

    @Test
    @DisplayName("VAL-024 · DEC-85: la Directiva de otro club recibe 404 sobre un equipo ajeno")
    void directivaDeOtroClub() {
        UUID temporada = temporada();
        Club clubA = clubAfiliado(temporada, "Leones");
        Club clubB = clubAfiliado(temporada, "Tigres");
        iniciarSesion(clubB.directiva());
        UUID persona = registrarPersona(ADULTO);

        assertThatThrownBy(() -> registros.registrar(new CrearRegistroJugadorRequest(persona, clubA.equipo())))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(registrosDe(persona)).isZero();
    }

    @Test
    @DisplayName("RF-101 · DEC-87: un club con el tarjetón sin aprobar responde 409 RF-101")
    void afiliacionNoAprobada() {
        UUID temporada = temporada();
        Club club = club(temporada, "Pendiente", "SOLICITADA");
        iniciarSesion(club.directiva());
        UUID persona = registrarPersona(ADULTO);

        assertThatThrownBy(() -> registros.registrar(new CrearRegistroJugadorRequest(persona, club.equipo())))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("RF-101"));
        assertThat(registrosDe(persona)).isZero();
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────────────────────

    /** Club con su directiva, un equipo en la temporada y su afiliación. */
    private record Club(UUID id, String nombre, UUID equipo, String nombreEquipo, UUID directiva) {
    }

    private Club clubAfiliado(UUID temporada, String prefijo) {
        return club(temporada, prefijo, "APROBADA");
    }

    private Club club(UUID temporada, String prefijo, String estadoAfiliacion) {
        String nombre = "Club " + prefijo + " " + UUID.randomUUID();
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", nombre).query(UUID.class).single();
        String nombreEquipo = prefijo + " " + UUID.randomUUID();
        UUID equipo = equipo(club, temporada, nombreEquipo);
        afiliar(club, temporada, estadoAfiliacion);
        return new Club(club, nombre, equipo, nombreEquipo, perfilDeDirectiva(club));
    }

    /** Equipo del club en un grupo nuevo de la temporada. */
    private UUID equipo(UUID club, UUID temporada, String nombre) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria).query(UUID.class).single();
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", nombre).query(UUID.class).single();
    }

    private void afiliar(UUID club, UUID temporada, String estado) {
        jdbc.sql("""
                        INSERT INTO siglim.afiliaciones (club_id, temporada_id, folio, colores_uniforme, estado,
                                                         capturada_por)
                        VALUES (:club, :temporada, :folio, 'Azul y blanco', CAST(:estado AS siglim.estado_afiliacion),
                                :perfil)
                        """)
                .param("club", club).param("temporada", temporada)
                .param("folio", "F-" + ThreadLocalRandom.current().nextInt(1_000_000_000))
                .param("estado", estado).param("perfil", crearPerfil(null)).update();
    }

    /** Temporada en configuración (no ACTIVA: solo puede haber una por liga). */
    private UUID temporada() {
        return jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2033-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID registrarPersona(LocalDate nacimiento) {
        return registrarPersona(curpAleatoria(), nacimiento);
    }

    private UUID registrarPersona(String curp, LocalDate nacimiento) {
        String nombre = "Nombre" + ThreadLocalRandom.current().nextInt(1_000_000);
        datosPersonales.add(curp);
        datosPersonales.add(nombre);
        return personas.registrar(new CrearPersonaRequest(curp, nombre, "Prueba", null, nacimiento, null, null)).id();
    }

    private void esperarRequisito(CrearRegistroJugadorRequest peticion, String requisito) {
        assertThatThrownBy(() -> registros.registrar(peticion)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-025");
                    assertThat(error.propiedades()).containsEntry("requisito", requisito);
                });
    }

    private int registrosDe(UUID persona) {
        return jdbc.sql("SELECT count(*) FROM siglim.registros_jugador WHERE persona_id = :persona")
                .param("persona", persona).query(Integer.class).single();
    }

    /** Ninguna fila de auditoría de registros lleva CURP ni nombres (RF-093, RNF-012). */
    private void verificarAuditoriaSinDatosPersonales() {
        String auditoria = String.join("\n", jdbc.sql("""
                        SELECT coalesce(detalle::text, '') || ' ' || coalesce(motivo, '') FROM siglim.auditoria
                         WHERE entidad = 'registros_jugador'
                        """).query(String.class).list());
        assertThat(datosPersonales).isNotEmpty();
        for (String dato : datosPersonales) {
            assertThat(auditoria).doesNotContain(dato);
        }
    }

    private UUID avisoDemostracion() {
        return jdbc.sql("""
                        SELECT id FROM siglim.avisos_privacidad
                         WHERE liga_id = :liga AND tipo = 'SIMPLIFICADO' AND version = 'v1'
                        """).param("liga", LIGA).query(UUID.class).single();
    }

    private static String curpAleatoria() {
        String digitos = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return "REJU" + digitos + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
    }

    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil(null);
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club
                          FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private UUID crearPerfil(UUID persona) {
        return jdbc.sql("""
                        INSERT INTO siglim.perfiles (nombre_mostrado, persona_id) VALUES ('Prueba', :persona)
                        RETURNING id
                        """)
                .param("persona", persona).query(UUID.class).single();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
