package mx.tecnm.morelia.siglim.organizacion;

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
import mx.tecnm.morelia.siglim.seguridad.AvisoPrivacidadDto;
import mx.tecnm.morelia.siglim.seguridad.ConsentimientoDto;
import mx.tecnm.morelia.siglim.seguridad.ConsentimientoService;
import mx.tecnm.morelia.siglim.seguridad.RegistrarConsentimientoRequest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-09.1 contra PostgreSQL: identidad global por CURP (DEC-78), alta y edición de personas, tutela,
 * consentimiento, aviso vigente y auditoría sin datos personales.
 */
class PersonasTutelaConsentimientoIT extends BaseDatosPrueba {

    private static final LocalDate ADULTO = LocalDate.parse("1990-01-01");
    private static final LocalDate MENOR = LocalDate.parse("2015-01-01");
    private static final List<String> FINALIDADES = List.of("REGISTRO_DEPORTIVO");

    @Autowired
    private ClubService clubes;

    @Autowired
    private PersonaService personas;

    @Autowired
    private ConsentimientoService consentimientos;

    /** Datos personales que ninguna fila de auditoría de la prueba puede contener. */
    private final List<String> datosPersonales = new ArrayList<>();

    @Test
    @DisplayName("DEC-78 · DEC-104 · RF-010: la Directiva del club B encuentra por CURP a personas del club A y sabe si son menores")
    void directivaDeOtroClubEncuentraPersonaPorCurp() {
        iniciarSesionComoAdministrador();
        UUID directivaA = perfilDeDirectiva(nuevoClub("A"), null);
        UUID directivaB = perfilDeDirectiva(nuevoClub("B"), null);
        String curp = curpAleatoria();
        UUID persona = insertarPersona(curp, ADULTO, directivaA);
        String curpMenor = curpAleatoria();
        UUID menor = insertarPersona(curpMenor, MENOR, directivaA);

        iniciarSesion(directivaB);
        PersonaDto encontrada = personas.buscarPorCurp(curp);
        PersonaDto encontradoMenor = personas.buscarPorCurp(curpMenor);

        assertThat(encontrada.id()).isEqualTo(persona);
        assertThat(encontrada.curp()).isEqualTo(curp);
        assertThat(encontrada.esMenor()).as("DEC-104: adulto").isFalse();
        assertThat(encontradoMenor.id()).isEqualTo(menor);
        assertThat(encontradoMenor.esMenor()).as("DEC-104: menor").isTrue();
    }

    @Test
    @DisplayName("RF-010: POST con una CURP creada por otro club responde 409 RF-010 con personaId")
    void altaConCurpDeOtroClub() {
        iniciarSesionComoAdministrador();
        UUID directivaA = perfilDeDirectiva(nuevoClub("A"), null);
        UUID directivaB = perfilDeDirectiva(nuevoClub("B"), null);
        iniciarSesion(directivaA);
        PersonaDetalleDto creada = registrar(curpAleatoria(), ADULTO, "persona.a@correo.mx", "4431112233");

        assertThat(creada.correo()).isEqualTo("persona.a@correo.mx");
        assertThat(eventos("personas", creada.id())).containsExactly("REGISTRAR_PERSONA");

        iniciarSesion(directivaB);
        assertThatThrownBy(() -> registrar(creada.curp(), ADULTO, null, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("RF-010");
                    assertThat(error.propiedades()).containsEntry("personaId", creada.id());
                });
        assertThat(contarPorCurp(creada.curp())).isEqualTo(1);
        verificarAuditoriaSinDatosPersonales();
    }

    @Test
    @DisplayName("RF-106 · DEC-79: tutela correcta, VAL-022 propia cuenta, RF-106 y DUPLICADO")
    void tutela() {
        iniciarSesionComoAdministrador();
        UUID club = nuevoClub("Tutela");
        UUID directiva = perfilDeDirectiva(club, null);
        iniciarSesion(directiva);
        UUID menor = registrar(curpAleatoria(), MENOR, null, null).id();
        UUID otroMenor = registrar(curpAleatoria(), MENOR, null, null).id();
        UUID tutor = registrar(curpAleatoria(), ADULTO, null, null).id();
        UUID adulto = registrar(curpAleatoria(), ADULTO, null, null).id();

        TutelaDto tutela = personas.registrarTutela(menor, new RegistrarTutelaRequest(tutor, " Madre "));

        assertThat(tutela.parentesco()).isEqualTo("Madre");
        assertThat(tutela.vigente()).isTrue();
        assertThat(eventos("tutelas", tutela.id())).containsExactly("REGISTRAR_TUTELA");
        esperarError(() -> personas.registrarTutela(adulto, new RegistrarTutelaRequest(tutor, "Padre")), "RF-106");
        esperarError(() -> personas.registrarTutela(menor, new RegistrarTutelaRequest(otroMenor, "Hermano")), "RF-106");
        esperarError(() -> personas.registrarTutela(menor, new RegistrarTutelaRequest(tutor, "Madre")), "DUPLICADO");

        iniciarSesion(perfilDeDirectiva(club, tutor));
        esperarError(() -> personas.registrarTutela(otroMenor, new RegistrarTutelaRequest(tutor, "Madre")), "VAL-022");
        verificarAuditoriaSinDatosPersonales();
    }

    @Test
    @DisplayName("VAL-025 · DEC-76: consentimiento del menor por su tutor, sin tutela, tutela de otro club y adulto")
    void consentimiento() {
        iniciarSesionComoAdministrador();
        UUID directivaA = perfilDeDirectiva(nuevoClub("A"), null);
        UUID directivaB = perfilDeDirectiva(nuevoClub("B"), null);
        iniciarSesion(directivaA);
        UUID menor = registrar(curpAleatoria(), MENOR, null, null).id();
        UUID menorSinTutela = registrar(curpAleatoria(), MENOR, null, null).id();
        UUID tutor = registrar(curpAleatoria(), ADULTO, null, null).id();
        UUID adulto = registrar(curpAleatoria(), ADULTO, null, null).id();
        personas.registrarTutela(menor, new RegistrarTutelaRequest(tutor, "Padre"));
        UUID aviso = avisoDemostracion();

        ConsentimientoDto delMenor = consentimientos.registrar(menor,
                new RegistrarConsentimientoRequest(aviso, FINALIDADES, tutor));
        assertThat(delMenor.otorgadoPorId()).isEqualTo(tutor);
        assertThat(eventos("consentimientos", delMenor.id())).containsExactly("REGISTRAR_CONSENTIMIENTO");

        esperarError(() -> consentimientos.registrar(menorSinTutela,
                new RegistrarConsentimientoRequest(aviso, FINALIDADES, tutor)), "VAL-025");

        ConsentimientoDto delAdulto = consentimientos.registrar(adulto,
                new RegistrarConsentimientoRequest(aviso, List.of("REGISTRO_DEPORTIVO", "DIFUSION"), null));
        assertThat(delAdulto.otorgadoPorId()).isEqualTo(adulto);
        assertThat(jdbc.sql("SELECT otorgado_por_id = persona_id FROM siglim.consentimientos WHERE id = :id")
                .param("id", delAdulto.id()).query(Boolean.class).single()).isTrue();

        esperarError(() -> consentimientos.registrar(adulto,
                new RegistrarConsentimientoRequest(aviso, List.of("MERCADOTECNIA"), null)), "RF-097");

        iniciarSesion(directivaB);
        ConsentimientoDto otroClub = consentimientos.registrar(menor,
                new RegistrarConsentimientoRequest(aviso, FINALIDADES, tutor));
        assertThat(otroClub.otorgadoPorId()).isEqualTo(tutor);
        assertThat(jdbc.sql("SELECT registrado_por FROM siglim.consentimientos WHERE id = :id")
                .param("id", otroClub.id()).query(UUID.class).single()).isEqualTo(directivaB);
        verificarAuditoriaSinDatosPersonales();
    }

    @Test
    @DisplayName("DEC-77 · DEC-83: la Directiva edita el correo, no el nombre ni personas que no ve; el Secretario sí")
    void edicion() {
        iniciarSesionComoAdministrador();
        UUID directivaA = perfilDeDirectiva(nuevoClub("A"), null);
        UUID directivaB = perfilDeDirectiva(nuevoClub("B"), null);
        UUID secretario = perfilConRol("SECRETARIO");
        iniciarSesion(directivaA);
        PersonaDetalleDto persona = registrar(curpAleatoria(), ADULTO, "antes@correo.mx", null);

        PersonaDetalleDto conCorreo = personas.actualizar(persona.id(), new ActualizarPersonaRequest(
                null, null, null, null, null, "despues@correo.mx", "4430001122"));
        datosPersonales.addAll(List.of("despues@correo.mx", "4430001122"));
        assertThat(conCorreo.correo()).isEqualTo("despues@correo.mx");
        assertThat(conCorreo.telefono()).isEqualTo("4430001122");

        esperarError(() -> personas.actualizar(persona.id(), new ActualizarPersonaRequest(
                null, "Renombrada", null, null, null, null, null)), "VAL-015");

        iniciarSesion(directivaB);
        assertThatThrownBy(() -> personas.actualizar(persona.id(), new ActualizarPersonaRequest(
                null, null, null, null, null, "ajeno@correo.mx", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        iniciarSesion(secretario);
        PersonaDetalleDto renombrada = personas.actualizar(persona.id(), new ActualizarPersonaRequest(
                null, "Corregida", null, null, null, null, null));
        datosPersonales.add("Corregida");
        assertThat(renombrada.nombre()).isEqualTo("Corregida");
        assertThat(eventos("personas", persona.id()))
                .containsExactly("REGISTRAR_PERSONA", "EDITAR_PERSONA", "EDITAR_PERSONA");
        assertThat(jdbc.sql("""
                        SELECT detalle -> 'campos' ->> 0 FROM siglim.auditoria
                         WHERE entidad = 'personas' AND entidad_id = :id AND accion = 'EDITAR_PERSONA'
                         ORDER BY id DESC LIMIT 1
                        """).param("id", persona.id().toString()).query(String.class).single())
                .isEqualTo("nombre");
        assertThat(jdbc.sql("SELECT actualizado_en > creado_en FROM siglim.personas WHERE id = :id")
                .param("id", persona.id()).query(Boolean.class).single()).isTrue();
        verificarAuditoriaSinDatosPersonales();
    }

    @Test
    @DisplayName("DEC-74 · DEC-80: el aviso vigente es la v1 SIMPLIFICADO de demostración")
    void avisoVigente() {
        iniciarSesion(perfilDeDirectiva(nuevoClub("Aviso"), null));

        AvisoPrivacidadDto aviso = consentimientos.avisoVigente(null);

        assertThat(aviso.version()).isEqualTo("v1");
        assertThat(aviso.esDemo()).isTrue();
        assertThat(aviso.finalidades()).containsExactly("REGISTRO_DEPORTIVO", "DIFUSION");
        assertThat(aviso.contenido()).contains("NO OFICIAL");
    }

    // ── Utilidades ──────────────────────────────────────────────────────────────────────────────

    private PersonaDetalleDto registrar(String curp, LocalDate nacimiento, String correo, String telefono) {
        String nombre = "Nombre" + ThreadLocalRandom.current().nextInt(1_000_000);
        datosPersonales.add(curp);
        datosPersonales.add(nombre);
        if (correo != null) {
            datosPersonales.add(correo);
        }
        if (telefono != null) {
            datosPersonales.add(telefono);
        }
        return personas.registrar(new CrearPersonaRequest(curp, nombre, "Prueba", null, nacimiento, correo, telefono));
    }

    /** Ninguna fila de auditoría de la prueba lleva CURP, nombres, correo ni teléfono (RF-093, RNF-012). */
    private void verificarAuditoriaSinDatosPersonales() {
        String auditoria = String.join("\n", jdbc.sql("""
                        SELECT coalesce(detalle::text, '') || ' ' || coalesce(motivo, '') FROM siglim.auditoria
                         WHERE entidad IN ('personas', 'tutelas', 'consentimientos')
                        """).query(String.class).list());
        assertThat(datosPersonales).isNotEmpty();
        for (String dato : datosPersonales) {
            assertThat(auditoria).doesNotContain(dato);
        }
    }

    private UUID avisoDemostracion() {
        return jdbc.sql("""
                        SELECT id FROM siglim.avisos_privacidad
                         WHERE liga_id = 'a0000000-0000-4000-8000-000000000001'
                           AND tipo = 'SIMPLIFICADO' AND version = 'v1'
                        """).query(UUID.class).single();
    }

    private UUID insertarPersona(String curp, LocalDate nacimiento, UUID creadoPor) {
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento, creado_por)
                        VALUES (:curp, 'Persona', 'Prueba', :nacimiento, :creadoPor) RETURNING id
                        """)
                .param("curp", curp).param("nacimiento", nacimiento).param("creadoPor", creadoPor)
                .query(UUID.class).single();
    }

    private int contarPorCurp(String curp) {
        return jdbc.sql("SELECT count(*) FROM siglim.personas WHERE curp = :curp")
                .param("curp", curp).query(Integer.class).single();
    }

    private List<String> eventos(String entidad, UUID id) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE entidad = :entidad AND entidad_id = :id ORDER BY id
                        """)
                .param("entidad", entidad).param("id", id.toString()).query(String.class).list();
    }

    private static String curpAleatoria() {
        String digitos = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return "PETU" + digitos + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
    }

    private UUID nuevoClub(String prefijo) {
        iniciarSesionComoAdministrador();
        return clubes.crear(new CrearClubRequest(prefijo + " " + UUID.randomUUID())).id();
    }

    private UUID perfilDeDirectiva(UUID club, UUID persona) {
        UUID perfil = crearPerfil(persona);
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club
                          FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil(null);
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
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

    private static void esperarError(ThrowingCallable accion, String codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> assertThat(error.codigo()).isEqualTo(codigo));
    }
}
