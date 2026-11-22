package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.CrearPersonaRequest;
import mx.tecnm.morelia.siglim.organizacion.PersonaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-06 contra PostgreSQL: renovación y transferencia entre temporadas, enlace con el registro
 * anterior, configuración de cambio de club (RF-111), VAL-026 dentro de la temporada y reutilización
 * de documentos aprobados en la renovación (RF-015, DEC-146).
 */
class RenovacionesTransferenciasIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate ADULTO = LocalDate.parse("1990-01-01");

    @Autowired
    private RegistroJugadorService registros;

    @Autowired
    private PersonaService personas;

    @Autowired
    private DocumentoRegistroService documentos;

    @Test
    @DisplayName("RF-015 · DEC-146: renueva en el mismo club, enlaza el registro anterior y deja historial")
    void renuevaYEnlazaElRegistroAnterior() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID club = club("Halcones");
        UUID equipoAnterior = equipo(club, anterior, "Halcones 2032");
        UUID equipoNuevo = equipo(club, nueva, "Halcones 2033");
        afiliar(club, anterior, "APROBADA");
        afiliar(club, nueva, "APROBADA");
        UUID directiva = perfilDeDirectiva(club);
        iniciarSesion(directiva);
        UUID persona = registrarPersona();
        RegistroJugadorDto previo = registros.registrar(new CrearRegistroJugadorRequest(persona, equipoAnterior));

        RegistroJugadorDto nuevo = registros.renovar(new MovimientoRegistroRequest(persona, equipoNuevo));

        assertThat(nuevo.estado()).isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);
        assertThat(nuevo.equipo().id()).isEqualTo(equipoNuevo);
        assertThat(nuevo.temporada().id()).isEqualTo(nueva);
        assertThat(anteriorDe(nuevo.id())).isEqualTo(previo.id());
        assertThat(jdbc.sql("""
                        SELECT motivo FROM siglim.registro_estado_historial
                         WHERE registro_id = :id AND estado_nuevo = 'PENDIENTE_VALIDACION'
                        """).param("id", nuevo.id()).query(String.class).single())
                .startsWith("Renovación desde la temporada");
        assertThat(jdbc.sql("""
                        SELECT accion || ' ' || (detalle ->> 'registroAnteriorId') || ' ' || (detalle ->> 'temporadaAnteriorId')
                          FROM siglim.auditoria WHERE entidad = 'registros_jugador' AND entidad_id = :id
                        """).param("id", nuevo.id().toString()).query(String.class).single())
                .isEqualTo("RENOVAR_REGISTRO " + previo.id() + " " + anterior);
    }

    @Test
    @DisplayName("RF-016 · RF-111: transfiere a otro club cuando la temporada lo permite y conserva el enlace")
    void transfiereAOtroClub() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID origen = club("Águilas");
        UUID destino = club("Pumas");
        UUID equipoOrigen = equipo(origen, anterior, "Águilas 2032");
        UUID equipoDestino = equipo(destino, nueva, "Pumas 2033");
        afiliar(origen, anterior, "APROBADA");
        afiliar(destino, nueva, "APROBADA");
        iniciarSesion(perfilDeDirectiva(origen));
        UUID persona = registrarPersona();
        RegistroJugadorDto previo = registros.registrar(new CrearRegistroJugadorRequest(persona, equipoOrigen));

        iniciarSesion(perfilDeDirectiva(destino));
        RegistroJugadorDto nuevo = registros.transferir(new MovimientoRegistroRequest(persona, equipoDestino));

        assertThat(nuevo.club().id()).isEqualTo(destino);
        assertThat(nuevo.temporada().id()).isEqualTo(nueva);
        assertThat(anteriorDe(nuevo.id())).isEqualTo(previo.id());
        assertThat(jdbc.sql("""
                        SELECT motivo FROM siglim.registro_estado_historial
                         WHERE registro_id = :id AND estado_nuevo = 'PENDIENTE_VALIDACION'
                        """).param("id", nuevo.id()).query(String.class).single())
                .startsWith("Transferencia desde el club");
        assertThat(jdbc.sql("""
                        SELECT accion FROM siglim.auditoria WHERE entidad_id = :id
                        """).param("id", nuevo.id().toString()).query(String.class).single())
                .isEqualTo("TRANSFERIR_REGISTRO");
    }

    @Test
    @DisplayName("RF-111 · DEC-13: con permite_cambio_club=false la transferencia responde 409 y no crea registro")
    void transferenciaSinPermisoDeLaTemporada() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID origen = club("Águilas");
        UUID destino = club("Pumas");
        UUID equipoOrigen = equipo(origen, anterior, "Águilas 2032");
        UUID equipoDestino = equipo(destino, nueva, "Pumas 2033");
        afiliar(origen, anterior, "APROBADA");
        afiliar(destino, nueva, "APROBADA");
        parametros(nueva, false);
        iniciarSesion(perfilDeDirectiva(origen));
        UUID persona = registrarPersona();
        registros.registrar(new CrearRegistroJugadorRequest(persona, equipoOrigen));

        iniciarSesion(perfilDeDirectiva(destino));
        assertThatThrownBy(() -> registros.transferir(new MovimientoRegistroRequest(persona, equipoDestino)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("RF-111");
                    assertThat(error.estado().value()).isEqualTo(409);
                });

        assertThat(jdbc.sql("SELECT count(*) FROM siglim.registros_jugador WHERE persona_id = :persona")
                .param("persona", persona).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-015 · VAL-001: la renovación reutiliza los documentos aprobados del mismo club y se activa")
    void renovacionHeredaDocumentosAprobados() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID club = club("Halcones");
        UUID equipoAnterior = equipo(club, anterior, "Halcones 2032");
        UUID equipoNuevo = equipo(club, nueva, "Halcones 2033");
        afiliar(club, anterior, "APROBADA");
        afiliar(club, nueva, "APROBADA");
        UUID directiva = perfilDeDirectiva(club);
        iniciarSesion(directiva);
        UUID persona = registrarPersona();
        RegistroJugadorDto previo = registros.registrar(new CrearRegistroJugadorRequest(persona, equipoAnterior));
        aprobarDocumentosDe(previo.id(), directiva);

        iniciarSesion(perfilDeSecretario());
        registros.validar(previo.id(), new ValidarRegistroRequest(DecisionRegistro.ACTIVO, null));

        iniciarSesion(directiva);
        RegistroJugadorDto nuevo = registros.renovar(new MovimientoRegistroRequest(persona, equipoNuevo));

        RequisitosRegistroDto requisitos = documentos.requisitos(nuevo.id());
        assertThat(requisitos.listoParaValidar()).isTrue();
        assertThat(requisitos.requisitos()).allSatisfy(requisito -> assertThat(requisito.aprobados())
                .isGreaterThanOrEqualTo(requisito.cantidad()));
        assertThat(requisitos.requisitos()).flatExtracting(RequisitosRegistroDto.RequisitoDto::documentos)
                .isNotEmpty();

        iniciarSesion(perfilDeSecretario());
        RegistroJugadorDto activado = registros.validar(nuevo.id(),
                new ValidarRegistroRequest(DecisionRegistro.ACTIVO, null));
        assertThat(activado.estado()).isEqualTo(EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("RF-016 · VAL-001: la transferencia no hereda los documentos aprobados del club de origen")
    void transferenciaNoHeredaDocumentos() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID origen = club("Águilas");
        UUID destino = club("Pumas");
        UUID equipoOrigen = equipo(origen, anterior, "Águilas 2032");
        UUID equipoDestino = equipo(destino, nueva, "Pumas 2033");
        afiliar(origen, anterior, "APROBADA");
        afiliar(destino, nueva, "APROBADA");
        UUID directivaOrigen = perfilDeDirectiva(origen);
        iniciarSesion(directivaOrigen);
        UUID persona = registrarPersona();
        RegistroJugadorDto previo = registros.registrar(new CrearRegistroJugadorRequest(persona, equipoOrigen));
        aprobarDocumentosDe(previo.id(), directivaOrigen);
        iniciarSesion(perfilDeSecretario());
        registros.validar(previo.id(), new ValidarRegistroRequest(DecisionRegistro.ACTIVO, null));

        iniciarSesion(perfilDeDirectiva(destino));
        RegistroJugadorDto nuevo = registros.transferir(new MovimientoRegistroRequest(persona, equipoDestino));

        RequisitosRegistroDto requisitos = documentos.requisitos(nuevo.id());
        assertThat(requisitos.listoParaValidar()).isFalse();
        assertThat(requisitos.requisitos()).flatExtracting(RequisitosRegistroDto.RequisitoDto::documentos).isEmpty();
        iniciarSesion(perfilDeSecretario());
        assertThatThrownBy(() -> registros.validar(nuevo.id(), new ValidarRegistroRequest(DecisionRegistro.ACTIVO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-001");
                    assertThat(error.estado().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("VAL-026: no se puede transferir a otro club dentro de la misma temporada")
    void transferenciaDentroDeLaMismaTemporada() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID origen = club("Águilas");
        UUID destino = club("Pumas");
        UUID equipoOrigenAnterior = equipo(origen, anterior, "Águilas 2032");
        UUID equipoOrigen = equipo(origen, nueva, "Águilas 2033");
        UUID equipoDestino = equipo(destino, nueva, "Pumas 2033");
        afiliar(origen, anterior, "APROBADA");
        afiliar(origen, nueva, "APROBADA");
        afiliar(destino, nueva, "APROBADA");
        UUID directivaOrigen = perfilDeDirectiva(origen);
        iniciarSesion(directivaOrigen);
        UUID persona = registrarPersona();
        registros.registrar(new CrearRegistroJugadorRequest(persona, equipoOrigenAnterior));
        RegistroJugadorDto enLaTemporada = registros.renovar(new MovimientoRegistroRequest(persona, equipoOrigen));
        aprobarDocumentosDe(enLaTemporada.id(), directivaOrigen);

        iniciarSesion(perfilDeSecretario());
        registros.validar(enLaTemporada.id(), new ValidarRegistroRequest(DecisionRegistro.ACTIVO, null));
        iniciarSesion(directivaOrigen);
        registros.darDeBaja(enLaTemporada.id(), new BajaRegistroRequest("El jugador cambió de club"));

        iniciarSesion(perfilDeDirectiva(destino));
        assertThatThrownBy(() -> registros.transferir(new MovimientoRegistroRequest(persona, equipoDestino)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-026");
                    assertThat(error.estado().value()).isEqualTo(409);
                });
    }

    // ── Alta normal y RF-111 (DEC-154) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-111 · DEC-154: el alta normal con registro anterior de otro club y cambio no permitido responde 409")
    void altaNormalSinCambioDeClub() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID origen = club("Águilas");
        UUID destino = club("Pumas");
        UUID equipoOrigen = equipo(origen, anterior, "Águilas 2032");
        UUID equipoDestino = equipo(destino, nueva, "Pumas 2033");
        afiliar(origen, anterior, "APROBADA");
        afiliar(destino, nueva, "APROBADA");
        parametros(nueva, false);
        iniciarSesion(perfilDeDirectiva(origen));
        UUID persona = registrarPersona();
        registros.registrar(new CrearRegistroJugadorRequest(persona, equipoOrigen));

        iniciarSesion(perfilDeDirectiva(destino));
        assertThatThrownBy(() -> registros.registrar(new CrearRegistroJugadorRequest(persona, equipoDestino)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("RF-111");
                    assertThat(error.estado().value()).isEqualTo(409);
                    assertThat(error.getMessage()).doesNotContain("Águilas");
                });
        assertThat(registrosDe(persona)).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-111 · DEC-154: el alta normal con registro anterior de otro club y cambio permitido crea el registro")
    void altaNormalConCambioDeClubPermitido() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID origen = club("Águilas");
        UUID destino = club("Pumas");
        UUID equipoOrigen = equipo(origen, anterior, "Águilas 2032");
        UUID equipoDestino = equipo(destino, nueva, "Pumas 2033");
        afiliar(origen, anterior, "APROBADA");
        afiliar(destino, nueva, "APROBADA");
        parametros(nueva, true);
        iniciarSesion(perfilDeDirectiva(origen));
        UUID persona = registrarPersona();
        registros.registrar(new CrearRegistroJugadorRequest(persona, equipoOrigen));

        iniciarSesion(perfilDeDirectiva(destino));
        RegistroJugadorDto nuevo = registros.registrar(new CrearRegistroJugadorRequest(persona, equipoDestino));

        assertThat(nuevo.estado()).isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);
        assertThat(jdbc.sql("SELECT registro_anterior_id IS NULL FROM siglim.registros_jugador WHERE id = :id")
                .param("id", nuevo.id()).query(Boolean.class).single()).isTrue();
        assertThat(registrosDe(persona)).isEqualTo(2);
    }

    @Test
    @DisplayName("DEC-154: el alta normal en el mismo club no depende de permite_cambio_club")
    void altaNormalDelMismoClub() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID club = club("Halcones");
        UUID equipoAnterior = equipo(club, anterior, "Halcones 2032");
        UUID equipoNuevo = equipo(club, nueva, "Halcones 2033");
        afiliar(club, anterior, "APROBADA");
        afiliar(club, nueva, "APROBADA");
        parametros(nueva, false);
        iniciarSesion(perfilDeDirectiva(club));
        UUID persona = registrarPersona();
        registros.registrar(new CrearRegistroJugadorRequest(persona, equipoAnterior));

        RegistroJugadorDto nuevo = registros.registrar(new CrearRegistroJugadorRequest(persona, equipoNuevo));

        assertThat(nuevo.estado()).isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);
        assertThat(registrosDe(persona)).isEqualTo(2);
    }

    @Test
    @DisplayName("RF-111 · DEC-154: el trigger rechaza un alta sin registro_anterior_id que cambia de club sin permiso")
    void triggerRechazaAltaNormalConCambioDeClub() {
        UUID anterior = temporada(2032);
        UUID nueva = temporada(2033);
        UUID origen = club("Águilas");
        UUID destino = club("Pumas");
        UUID equipoOrigen = equipo(origen, anterior, "Águilas 2032");
        UUID equipoDestino = equipo(destino, nueva, "Pumas 2033");
        afiliar(origen, anterior, "APROBADA");
        afiliar(destino, nueva, "APROBADA");
        parametros(nueva, false);
        UUID directiva = perfilDeDirectiva(origen);
        iniciarSesion(directiva);
        UUID persona = registrarPersona();
        registros.registrar(new CrearRegistroJugadorRequest(persona, equipoOrigen));

        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO siglim.registros_jugador (persona_id, equipo_id, temporada_id, alta_en, creado_por)
                        VALUES (:persona, :equipo, :temporada, DATE '2033-08-15', :perfil)
                        """)
                .param("persona", persona).param("equipo", equipoDestino).param("temporada", nueva)
                .param("perfil", directiva).update())
                .hasMessageContaining("La temporada destino no permite cambios de club.");
        assertThat(registrosDe(persona)).isEqualTo(1);
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

    private UUID temporada(int anio) {
        return jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, :inicio) RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + anio + " " + UUID.randomUUID())
                .param("inicio", LocalDate.of(anio, 8, 1))
                .query(UUID.class).single();
    }

    private UUID club(String prefijo) {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + prefijo + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

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
        return jdbc.sql("""
                        INSERT INTO siglim.equipos (club_id, grupo_id, nombre)
                        VALUES (:club, :grupo, :nombre) RETURNING id
                        """)
                .param("club", club).param("grupo", grupo).param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private void afiliar(UUID club, UUID temporada, String estado) {
        jdbc.sql("""
                        INSERT INTO siglim.afiliaciones (club_id, temporada_id, folio, colores_uniforme, estado,
                                                         capturada_por)
                        VALUES (:club, :temporada, :folio, 'Azul y blanco',
                                CAST(:estado AS siglim.estado_afiliacion), :perfil)
                        """)
                .param("club", club).param("temporada", temporada)
                .param("folio", "F-" + ThreadLocalRandom.current().nextInt(1_000_000_000))
                .param("estado", estado).param("perfil", crearPerfil()).update();
    }

    private void parametros(UUID temporada, boolean permiteCambioClub) {
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, permite_cambio_club, motivo)
                        VALUES (:temporada, :permite, 'Parámetros de prueba')
                        """)
                .param("temporada", temporada).param("permite", permiteCambioClub).update();
    }

    /** Inserta los documentos aprobados que exige LIBRE para un adulto. */
    private void aprobarDocumentosDe(UUID registro, UUID perfil) {
        List<Map<String, Object>> requisitos = jdbc.sql("""
                        SELECT rd.tipo_documento_id, rd.cantidad
                          FROM siglim.requisitos_documento rd
                         WHERE rd.liga_id = :liga AND rd.tipo_categoria = 'LIBRE' AND NOT rd.solo_menores
                         ORDER BY rd.tipo_documento_id
                        """).param("liga", LIGA)
                .query((fila, numero) -> Map.<String, Object>of(
                        "tipo", fila.getLong("tipo_documento_id"), "cantidad", fila.getInt("cantidad")))
                .list();
        for (Map<String, Object> requisito : requisitos) {
            for (int i = 0; i < (int) requisito.get("cantidad"); i++) {
                jdbc.sql("""
                                INSERT INTO siglim.documentos (tipo_documento_id, registro_id, s3_bucket, s3_clave,
                                                               url, tipo_mime, tamano_bytes, sha256, estado_validacion,
                                                               cargado_por)
                                VALUES (:tipo, :registro, 'siglim-documentos-prueba', :clave,
                                        'https://example.test/documento', 'image/jpeg', 1024, :sha, 'APROBADO', :perfil)
                                """)
                        .param("tipo", requisito.get("tipo")).param("registro", registro)
                        .param("clave", UUID.randomUUID().toString())
                        .param("sha", "a".repeat(64)).param("perfil", perfil).update();
            }
        }
    }

    private int registrosDe(UUID persona) {
        return jdbc.sql("SELECT count(*) FROM siglim.registros_jugador WHERE persona_id = :persona")
                .param("persona", persona).query(Integer.class).single();
    }

    private UUID anteriorDe(UUID registro) {
        return jdbc.sql("SELECT registro_anterior_id FROM siglim.registros_jugador WHERE id = :id")
                .param("id", registro).query(UUID.class).single();
    }

    private UUID registrarPersona() {
        String curp = "REJU" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))
                + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
        return personas.registrar(new CrearPersonaRequest(curp,
                "Nombre" + ThreadLocalRandom.current().nextInt(1_000_000), "Prueba", null, ADULTO, null, null)).id();
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

    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club
                          FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private UUID crearPerfil() {
        return jdbc.sql("""
                        INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Prueba') RETURNING id
                        """).query(UUID.class).single();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
