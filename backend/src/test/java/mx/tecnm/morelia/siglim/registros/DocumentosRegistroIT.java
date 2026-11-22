package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.AlmacenamientoEnMemoriaPrueba.AlmacenamientoEnMemoria;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.EnlaceTemporal;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.organizacion.CrearPersonaRequest;
import mx.tecnm.morelia.siglim.organizacion.PersonaService;
import mx.tecnm.morelia.siglim.organizacion.RegistrarTutelaRequest;
import mx.tecnm.morelia.siglim.seguridad.ConsentimientoService;
import mx.tecnm.morelia.siglim.seguridad.RegistrarConsentimientoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-10.1 contra PostgreSQL con RLS: criterios 1, 2 y 4 de H-10 (documentos PENDIENTE con tipo,
 * tamaño y hash; 415 por firma; enlace sin guardar ni auditar la URL), ámbito de club, reemplazo por
 * otro directivo del mismo club (DEC-94), validación por la liga y requisitos completos (DEC-96).
 */
class DocumentosRegistroIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private DocumentoRegistroService documentos;

    @Autowired
    private RegistroJugadorService registros;

    @Autowired
    private PersonaService personas;

    @Autowired
    private ConsentimientoService consentimientos;

    @Autowired
    private AlmacenamientoEnMemoria almacenamiento;

    @Test
    @DisplayName("H-10 criterios 1 y 4 · RF-108 · RF-109: la Directiva carga, otro directivo reemplaza, el Secretario valida y el enlace no se audita")
    void flujoCompletoDeUnMenor() {
        Escenario escenario = escenarioDeMenor();
        UUID registro = escenario.registro();

        iniciarSesion(escenario.directiva());
        RequisitosRegistroDto inicial = documentos.requisitos(registro);
        assertThat(inicial.esMenor()).isTrue();
        assertThat(inicial.categoria().tipo().name()).isEqualTo("INFANTIL");
        assertThat(inicial.requisitos()).extracting(r -> r.tipoDocumento().codigo())
                .containsExactly("CONSTANCIA_ESTUDIOS", "IDENTIFICACION", "FOTOGRAFIA", "INE_TUTOR");
        assertThat(inicial.requisitos().get(2).esDemo()).isTrue();

        DocumentoDto constancia = documentos.cargar(registro, tipo("CONSTANCIA_ESTUDIOS"), JPEG);
        DocumentoDto identificacion = documentos.cargar(registro, tipo("IDENTIFICACION"), PDF);
        DocumentoDto foto1 = documentos.cargar(registro, tipo("FOTOGRAFIA"), JPEG);
        DocumentoDto foto2 = documentos.cargar(registro, tipo("FOTOGRAFIA"), JPEG);
        DocumentoDto ineTutor = documentos.cargar(registro, tipo("INE_TUTOR"), JPEG);

        for (DocumentoDto documento : List.of(constancia, identificacion, foto1, foto2, ineTutor)) {
            assertThat(documento.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
            assertThat(documento.registroId()).isEqualTo(registro);
            Map<String, Object> fila = jdbc.sql("""
                            SELECT tipo_mime, tamano_bytes, sha256, s3_clave, cargado_por FROM siglim.documentos
                             WHERE id = :id
                            """).param("id", documento.id()).query().singleRow();
            assertThat(((String) fila.get("sha256")).strip()).hasSize(64).matches("[0-9a-f]{64}");
            assertThat(fila.get("tamano_bytes")).isEqualTo(documento.tamanoBytes());
            assertThat((String) fila.get("s3_clave")).startsWith("liga/" + LIGA + "/registros/" + registro + "/");
            assertThat(fila.get("cargado_por")).isEqualTo(escenario.directiva());
        }
        assertThat(identificacion.tipoMime()).isEqualTo("application/pdf");
        assertThat(documentos.requisitos(registro).completo()).isTrue();

        // Otro club no ve el registro ni sus documentos (VAL-024).
        iniciarSesion(escenario.directivaOtroClub());
        assertThatThrownBy(() -> documentos.cargar(registro, tipo("FOTOGRAFIA"), JPEG))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> documentos.requisitos(registro)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> documentos.reemplazar(foto1.id(), JPEG)).isInstanceOf(RecursoNoEncontradoException.class);

        // El Secretario aprueba todo menos una fotografía, que rechaza.
        iniciarSesion(escenario.secretario());
        for (DocumentoDto documento : List.of(constancia, identificacion, foto2, ineTutor)) {
            DocumentoDto aprobado = documentos.validar(documento.id(),
                    new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null));
            assertThat(aprobado.estadoValidacion()).isEqualTo(EstadoValidacion.APROBADO);
            assertThat(aprobado.validadoPor().id()).isEqualTo(escenario.secretario());
            assertThat(aprobado.validadoEn()).isNotNull();
        }
        DocumentoDto rechazada = documentos.validar(foto1.id(),
                new ValidarDocumentoRequest(DecisionDocumento.RECHAZADO, "La fotografía está borrosa"));
        assertThat(rechazada.observaciones()).isEqualTo("La fotografía está borrosa");
        RequisitosRegistroDto conRechazo = documentos.requisitos(registro);
        assertThat(conRechazo.completo()).isFalse();
        assertThat(conRechazo.requisitos().get(2).faltan()).isEqualTo(1);

        // Un segundo directivo del mismo club reemplaza la fotografía que cargó su compañero (DEC-94).
        iniciarSesion(escenario.otraDirectiva());
        DocumentoDto nueva = documentos.reemplazar(foto1.id(), JPEG);
        assertThat(nueva.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
        assertThat(nueva.reemplazaA()).isEqualTo(foto1.id());
        assertThat(nueva.tipoDocumento().codigo()).isEqualTo("FOTOGRAFIA");
        assertThat(jdbc.sql("SELECT reemplazado_por_id::text || ' ' || estado_validacion FROM siglim.documentos WHERE id = :id")
                .param("id", foto1.id()).query(String.class).single())
                .isEqualTo(nueva.id() + " RECHAZADO");
        assertThatThrownBy(() -> documentos.reemplazar(foto1.id(), JPEG))
                .isInstanceOfSatisfying(mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("RF-109"));

        iniciarSesion(escenario.secretario());
        documentos.validar(nueva.id(), new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null));
        RequisitosRegistroDto listo = documentos.requisitos(registro);
        assertThat(listo.completo()).isTrue();
        assertThat(listo.listoParaValidar()).isTrue();
        assertThat(listo.estadoRegistro()).as("validar documentos no cambia el registro (H-10.2)")
                .isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);

        // Enlace del documento reemplazado (historial) y auditoría sin URL (RNF-018, DEC-95).
        EnlaceTemporal enlace = documentos.enlace(foto1.id());
        assertThat(enlace.url()).startsWith("https://");
        List<String> auditoria = jdbc.sql("""
                        SELECT accion || ' ' || coalesce(detalle::text, '') || ' ' || coalesce(motivo, '')
                          FROM siglim.auditoria WHERE entidad = 'documentos' AND detalle ->> 'registroId' = :registro
                        """).param("registro", registro.toString()).query(String.class).list();
        assertThat(auditoria).extracting(linea -> linea.substring(0, linea.indexOf(' ')))
                .contains("CARGAR_DOCUMENTO", "REEMPLAZAR_DOCUMENTO", "VALIDAR_DOCUMENTO", "CONSULTAR_DOCUMENTO");
        assertThat(auditoria).anyMatch(linea -> linea.startsWith("VALIDAR_DOCUMENTO") && linea.contains("borrosa"));
        String todo = String.join("\n", auditoria);
        assertThat(todo).doesNotContain("https://", "amazonaws", "X-Amz", "s3_clave", "sha256", enlace.url(),
                "liga/" + LIGA);
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.documentos WHERE url LIKE '%X-Amz%'")
                .query(Integer.class).single()).as("la URL prefirmada no se guarda").isZero();
    }

    @Test
    @DisplayName("H-10 criterio 2 · RF-108: un PDF (aunque se llame .jpg) se guarda como application/pdf; un texto responde 415 sin fila")
    void tipoPorFirma() {
        Escenario escenario = escenarioDeMenor();
        iniciarSesion(escenario.directiva());

        DocumentoDto pdf = documentos.cargar(escenario.registro(), tipo("IDENTIFICACION"), PDF);
        assertThat(pdf.tipoMime()).isEqualTo("application/pdf");

        int objetos = almacenamiento.objetos().size();
        byte[] texto = "Esto no es una imagen".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> documentos.cargar(escenario.registro(), tipo("FOTOGRAFIA"), texto))
                .isInstanceOfSatisfying(ArchivoRechazadoException.class,
                        error -> assertThat(error.estado()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.documentos WHERE registro_id = :registro")
                .param("registro", escenario.registro()).query(Integer.class).single()).isEqualTo(1);
        assertThat(almacenamiento.objetos()).hasSize(objetos);
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    /** Registro pendiente de un menor en INFANTIL, con dos directivos de su club, uno de otro club y el Secretario. */
    private record Escenario(UUID registro, UUID directiva, UUID otraDirectiva, UUID directivaOtroClub,
                             UUID secretario) {
    }

    private Escenario escenarioDeMenor() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2034-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).query(UUID.class).single();
        UUID club = club(temporada);
        UUID otroClub = club(temporada);
        UUID equipo = equipoInfantil(club, temporada);
        UUID directiva = perfilDeDirectiva(club);

        iniciarSesion(directiva);
        UUID menor = registrarPersona(LocalDate.parse("2015-03-03"));
        UUID tutor = registrarPersona(LocalDate.parse("1985-03-03"));
        personas.registrarTutela(menor, new RegistrarTutelaRequest(tutor, "Madre"));
        consentimientos.registrar(menor, new RegistrarConsentimientoRequest(avisoDemostracion(),
                List.of("REGISTRO_DEPORTIVO"), tutor));
        UUID registro = registros.registrar(new CrearRegistroJugadorRequest(menor, equipo)).id();
        SecurityContextHolder.clearContext();

        return new Escenario(registro, directiva, perfilDeDirectiva(club), perfilDeDirectiva(otroClub),
                perfilDeSecretario());
    }

    private UUID club(UUID temporada) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club Documentos " + UUID.randomUUID()).query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.afiliaciones (club_id, temporada_id, folio, colores_uniforme, estado,
                                                         capturada_por)
                        VALUES (:club, :temporada, :folio, 'Rojo', 'APROBADA', :perfil)
                        """)
                .param("club", club).param("temporada", temporada)
                .param("folio", "D-" + ThreadLocalRandom.current().nextInt(1_000_000_000))
                .param("perfil", crearPerfil()).update();
        return club;
    }

    private UUID equipoInfantil(UUID club, UUID temporada) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'INFANTIL', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Infantil " + UUID.randomUUID()).query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria).query(UUID.class).single();
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", "Infantil " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID registrarPersona(LocalDate nacimiento) {
        String digitos = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        String curp = "DOCU" + digitos + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
        return personas.registrar(new CrearPersonaRequest(curp, "Nombre", "Documentos", null, nacimiento, null, null))
                .id();
    }

    private long tipo(String codigo) {
        return jdbc.sql("SELECT id FROM siglim.tipos_documento WHERE codigo = :codigo")
                .param("codigo", codigo).query(Long.class).single();
    }

    private UUID avisoDemostracion() {
        return jdbc.sql("""
                        SELECT id FROM siglim.avisos_privacidad
                         WHERE liga_id = :liga AND tipo = 'SIMPLIFICADO' AND version = 'v1'
                        """).param("liga", LIGA).query(UUID.class).single();
    }

    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private UUID perfilDeSecretario() {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, 'LIGA' FROM siglim.roles WHERE codigo = 'SECRETARIO'
                        """)
                .param("perfil", perfil).update();
        return perfil;
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Prueba documentos') RETURNING id")
                .query(UUID.class).single();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
