package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** H-22.1 contra PostgreSQL: pending, autorización, vigencia histórica, ámbito y auditoría (RF-102). */
class CargoDirectivaIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final LocalDate INICIO_ANTERIOR = LocalDate.parse("2029-01-01");
    private static final LocalDate INICIO_NUEVO = LocalDate.parse("2030-01-01");

    @Autowired
    private ClubService clubes;

    @Autowired
    private CargoDirectivaService directiva;

    @Test
    @DisplayName("H-22.1 · RF-102: registra pendiente, requiere autorización y termina el cargo previo")
    void registraYAutorizaCambioDeDirectiva() {
        UUID administrador = iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Directiva " + UUID.randomUUID()));
        UUID personaAnterior = crearPersona("Anterior");
        UUID personaNueva = crearPersona("Nueva");
        UUID cargoAnterior = jdbc.sql("""
                        INSERT INTO siglim.cargos_directiva (club_id, persona_id, cargo, desde, autorizado_por)
                        VALUES (:club, :persona, 'PRESIDENTE', :desde, :perfil) RETURNING id
                        """)
                .param("club", club.id()).param("persona", personaAnterior)
                .param("desde", INICIO_ANTERIOR).param("perfil", administrador)
                .query(UUID.class).single();

        UUID perfilDirectiva = perfilDeDirectiva(club.id());
        iniciarSesion(perfilDirectiva);
        CargoDirectivaDto pendiente = directiva.registrar(club.id(), new RegistrarCargoDirectivaRequest(
                personaNueva, CargoDirectiva.PRESIDENTE, INICIO_NUEVO));

        assertThat(pendiente.estado()).isEqualTo(EstadoCargoDirectiva.PENDIENTE);
        assertThat(pendiente.autorizadoPor()).isNull();
        assertThat(directiva.consultar(club.id())).contains(pendiente);
        assertThat(jdbc.sql("SELECT hasta FROM siglim.cargos_directiva WHERE id = :id")
                .param("id", cargoAnterior).query(LocalDate.class).optional()).isEmpty();

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        CargoDirectivaDto autorizado = directiva.autorizar(pendiente.id());

        assertThat(autorizado.estado()).isEqualTo(EstadoCargoDirectiva.AUTORIZADO);
        assertThat(autorizado.autorizadoPor()).isEqualTo(presidente);
        assertThat(autorizado.hasta()).isNull();
        assertThat(jdbc.sql("SELECT hasta FROM siglim.cargos_directiva WHERE id = :id")
                .param("id", cargoAnterior).query(LocalDate.class).single())
                .isEqualTo(INICIO_NUEVO.minusDays(1));
        assertThat(eventos(pendiente.id())).containsExactly("REGISTRAR_CARGO_DIRECTIVA", "AUTORIZAR_CARGO_DIRECTIVA");
    }

    @Test
    @DisplayName("H-22.1 · VAL-024: una directiva no consulta ni modifica cargos de otro club")
    void directivaNoAlcanzaOtroClub() {
        UUID administrador = iniciarSesionComoAdministrador();
        ClubDto propio = clubes.crear(new CrearClubRequest("Propio " + UUID.randomUUID()));
        ClubDto ajeno = clubes.crear(new CrearClubRequest("Ajeno " + UUID.randomUUID()));
        UUID persona = crearPersona("Representante");
        UUID cargo = jdbc.sql("""
                        INSERT INTO siglim.cargos_directiva (club_id, persona_id, cargo, desde, autorizado_por)
                        VALUES (:club, :persona, 'PRESIDENTE', :desde, :perfil) RETURNING id
                        """)
                .param("club", ajeno.id()).param("persona", persona)
                .param("desde", INICIO_ANTERIOR).param("perfil", administrador)
                .query(UUID.class).single();
        iniciarSesion(perfilDeDirectiva(propio.id()));

        assertThat(directiva.consultar(propio.id())).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> directiva.consultar(ajeno.id()))
                .isInstanceOf(mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> directiva.autorizar(cargo))
                .isInstanceOf(mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException.class);
    }

    private UUID crearPersona(String nombre) {
        String digitos = String.format("%06d", (int) (Math.random() * 1_000_000));
        String curp = "GODE" + digitos + "HDFRNS09";
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, fecha_nacimiento)
                        VALUES (:curp, :nombre, 'Prueba', DATE '1990-01-01') RETURNING id
                        """)
                .param("curp", curp).param("nombre", nombre)
                .query(UUID.class).single();
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

    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Prueba') RETURNING id")
                .query(UUID.class).single();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private java.util.List<String> eventos(UUID id) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE entidad = 'cargos_directiva' AND entidad_id = :id ORDER BY id
                        """)
                .param("id", id.toString()).query(String.class).list();
    }
}
