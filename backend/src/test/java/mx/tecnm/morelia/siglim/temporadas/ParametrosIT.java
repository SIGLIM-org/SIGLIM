package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * H-05.2 contra PostgreSQL real con {@link ParametrosService} completo: autorización con la matriz
 * de la migración, {@code tg_parametros_version}, el candado de la temporada, la lectura del
 * intervalo, {@code bloque_cancha()} y la auditoría en la misma transacción.
 */
class ParametrosIT extends BaseDatosPrueba {

    private static final LocalDate INICIO = LocalDate.parse("2035-08-01");

    @Autowired
    private ParametrosService parametros;

    @Autowired
    private TemporadaService temporadas;

    @Autowired
    private CategoriaService categorias;

    @Autowired
    private GrupoService grupos;

    @Test
    @DisplayName("RF-006, RF-100: cada POST crea una versión nueva, la anterior se conserva y el historial compara versiones")
    void versionesConservadas() {
        UUID perfil = iniciarSesionComoAdministrador();
        TemporadaDto temporada = nuevaTemporada();
        UUID conBloquePropio = asignarCategoria(temporada.id());
        UUID sinBloquePropio = asignarCategoria(temporada.id());

        ParametrosDto primera = parametros.registrar(temporada.id(), ParametrosPrueba.valido());
        ParametrosDto segunda = parametros.registrar(temporada.id(), ParametrosPrueba.juego()
                .puntos(2, 1, 0).plazo(36).esDemo(false).categoria(conBloquePropio, 70, 90)
                .motivo("  Ajuste de la asamblea de la Liga  ").peticion());

        assertThat(primera.version()).isEqualTo(1);
        assertThat(segunda.version()).isEqualTo(2);
        assertThat(segunda.motivo()).isEqualTo("Ajuste de la asamblea de la Liga");
        assertThat(segunda.plazoValidacionCedulaHoras()).as("36 h se leen como 36").isEqualTo(36);
        assertThat(segunda.creadoPor().id()).isEqualTo(perfil);
        assertThat(segunda.camposDemo()).isEmpty();
        assertThat(segunda.categorias()).singleElement().satisfies(fila -> {
            assertThat(fila.categoria().id()).isEqualTo(conBloquePropio);
            assertThat(fila.duracionJuegoMin()).isEqualTo(70);
            assertThat(fila.bloqueCanchaMin()).isEqualTo(90);
        });

        ParametrosDto conservada = parametros.consultarVersion(temporada.id(), 1);
        assertThat(conservada.puntosVictoria()).isEqualTo(3);
        assertThat(conservada.plazoValidacionCedulaHoras()).isEqualTo(48);
        assertThat(conservada.esDemo()).isTrue();
        assertThat(conservada.categorias()).isEmpty();

        ParametrosTemporadaDto consulta = parametros.consultar(temporada.id());
        assertThat(consulta.vigente().version()).isEqualTo(2);
        assertThat(consulta.predeterminados()).isNull();
        assertThat(consulta.historial()).extracting(VersionParametrosResumenDto::version).containsExactly(2, 1);
        assertThat(consulta.historial().getFirst().camposCambiados())
                .containsExactly("puntosVictoria", "plazoValidacionCedulaHoras", "esDemo", "categorias");
        assertThat(consulta.historial().getLast().camposCambiados()).hasSize(16).doesNotContain("categorias");
        assertThat(temporadas.consultar(temporada.id()).versionParametros()).isEqualTo(2);

        assertThat(bloqueCancha(temporada.id(), conBloquePropio)).as("fila propia de la categoría").isEqualTo(90);
        assertThat(bloqueCancha(temporada.id(), sinBloquePropio)).as("valor de la temporada").isEqualTo(120);

        List<String> auditadas = jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE modulo = 'M-02' AND entidad = 'parametros_temporada' AND entidad_id = :id
                        """)
                .param("id", segunda.id().toString()).query(String.class).list();
        assertThat(auditadas).containsExactly("REGISTRAR_PARAMETROS");
        String detalle = jdbc.sql("""
                        SELECT detalle::text FROM siglim.auditoria
                         WHERE entidad = 'parametros_temporada' AND entidad_id = :id
                        """)
                .param("id", segunda.id().toString()).query(String.class).single();
        assertThat(detalle).contains("\"versionAnterior\": 1", "\"esDemo\": false", "\"categorias\"");
        String motivo = jdbc.sql("SELECT motivo FROM siglim.auditoria WHERE entidad_id = :id")
                .param("id", segunda.id().toString()).query(String.class).single();
        assertThat(motivo).isEqualTo("Ajuste de la asamblea de la Liga");
    }

    @Test
    @DisplayName("D8: los predeterminados de Java coinciden con los DEFAULT reales de parametros_temporada")
    void predeterminadosIgualesALaTabla() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = nuevaTemporada();
        assertThat(parametros.consultar(temporada.id()).predeterminados())
                .isEqualTo(ParametrosPredeterminadosDto.DE_LA_TABLA);

        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, motivo) VALUES (:id, 'Solo los DEFAULT')")
                .param("id", temporada.id()).update();
        ParametrosDto tabla = parametros.consultarVersion(temporada.id(), 1);

        assertThat(new ParametrosPredeterminadosDto(tabla.puntosVictoria(), tabla.puntosEmpate(), tabla.puntosDerrota(),
                tabla.criteriosDesempate(), tabla.golesDefaultGanador(), tabla.golesDefaultPerdedor(),
                tabla.plazoValidacionCedulaHoras(), tabla.duracionJuegoMin(), tabla.bloqueCanchaMin(),
                tabla.clasificadosLiguilla(), tabla.formatoLiguilla(), tabla.empateLiguillaPorPenales(),
                tabla.equiposAscienden(), tabla.equiposDescienden(), tabla.permiteCambioClub(), tabla.esDemo()))
                .isEqualTo(ParametrosPredeterminadosDto.DE_LA_TABLA);
    }

    @Test
    @DisplayName("RF-100: con parámetros registrados por el servicio la temporada se activa")
    void activaConParametros() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = nuevaTemporada();
        UUID categoria = asignarCategoria(temporada.id());
        assertThat(temporadas.consultar(temporada.id()).activacion().requisitos())
                .filteredOn(requisito -> requisito.codigo().equals("RF-100"))
                .singleElement().satisfies(requisito -> assertThat(requisito.cumple()).isFalse());

        parametros.registrar(temporada.id(), ParametrosPrueba.valido());
        crearGrupo(temporada.id(), categoria);

        // RN-058 admite una sola temporada activa y la base es compartida con las demás *IT.
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
        TemporadaDto activa = temporadas.activar(temporada.id());
        assertThat(activa.estado()).isEqualTo(EstadoTemporada.ACTIVA);

        assertThat(parametros.registrar(temporada.id(), ParametrosPrueba.juego().puntos(2, 1, 0).peticion())
                .version()).as("D4: una temporada activa admite una versión nueva").isEqualTo(2);
    }

    @Test
    @DisplayName("RF-003: una temporada CERRADA responde 409 RF-003 y no crea versión")
    void temporadaCerrada() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = nuevaTemporada();
        UUID categoria = asignarCategoria(temporada.id());
        parametros.registrar(temporada.id(), ParametrosPrueba.valido());
        crearGrupo(temporada.id(), categoria);
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
        temporadas.activar(temporada.id());
        temporadas.cerrar(temporada.id(), new CerrarTemporadaRequest("Fin de la temporada de prueba"));

        assertThatThrownBy(() -> parametros.registrar(temporada.id(), ParametrosPrueba.valido()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        assertThat(parametros.consultar(temporada.id()).historial()).hasSize(1);
    }

    @Test
    @DisplayName("D7: una categoría no asignada a la temporada responde 422 y no crea versión")
    void categoriaNoAsignada() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = nuevaTemporada();
        CategoriaDto ajena = categorias.crear(new CrearCategoriaRequest(unico("Ajena"), TipoCategoria.LIBRE, null));

        assertThatThrownBy(() -> parametros.registrar(temporada.id(),
                ParametrosPrueba.juego().categoria(ajena.id(), 70, 90).peticion()))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
        assertThat(parametros.consultar(temporada.id()).vigente()).isNull();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private TemporadaDto nuevaTemporada() {
        return temporadas.crear(new CrearTemporadaRequest(unico("Parámetros"), INICIO, null));
    }

    private UUID asignarCategoria(UUID temporada) {
        CategoriaDto categoria = categorias.crear(new CrearCategoriaRequest(unico("Categoría"), TipoCategoria.LIBRE,
                null));
        grupos.asignarCategoria(temporada, new AsignarCategoriaRequest(categoria.id()));
        return categoria.id();
    }

    private void crearGrupo(UUID temporada, UUID categoria) {
        UUID categoriaTemporada = grupos.listarCategorias(temporada).stream()
                .filter(fila -> fila.categoria().id().equals(categoria))
                .findFirst().orElseThrow().id();
        grupos.crear(new CrearGrupoRequest(categoriaTemporada, "A"));
    }

    /** {@code bloque_cancha()} de la base, en minutos. */
    private int bloqueCancha(UUID temporada, UUID categoria) {
        return jdbc.sql("SELECT (extract(epoch FROM siglim.bloque_cancha(:t, :c)) / 60)::int")
                .param("t", temporada).param("c", categoria).query(Integer.class).single();
    }

    private static String unico(String prefijo) {
        return prefijo + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}
