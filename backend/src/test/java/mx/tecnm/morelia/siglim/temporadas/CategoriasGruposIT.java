package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * H-05.1 contra PostgreSQL real, con {@link CategoriaService}, {@link GrupoService} y
 * {@link TemporadaService} completos: autorización con la matriz de la migración, SQL, candados,
 * el índice {@code ux_categorias_orden} y la auditoría en la misma transacción.
 *
 * <p>Las pruebas comparten la base con las demás {@code *IT}, que también crean categorías: por eso
 * los nombres son únicos y el reordenamiento trabaja sobre el catálogo completo que haya.
 */
class CategoriasGruposIT extends BaseDatosPrueba {

    private static final LocalDate INICIO = LocalDate.parse("2034-08-01");

    @Autowired
    private CategoriaService categorias;

    @Autowired
    private GrupoService grupos;

    @Autowired
    private TemporadaService temporadas;

    // ── Flujo completo ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-004, RF-005, RF-099: crear categoría, asignarla y crear un grupo cumplen la lista de activación")
    void flujoCompleto() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = temporadas.crear(new CrearTemporadaRequest(unico("Temporada"), INICIO, null));
        assertThat(temporada.categoriasAsignadas()).isZero();

        CategoriaDto categoria = categorias.crear(new CrearCategoriaRequest(" " + unico("Libre") + " ",
                TipoCategoria.LIBRE, null));
        assertThat(categoria.activa()).isTrue();
        assertThat(categoria.nombre()).doesNotStartWith(" ");
        assertThat(categoria.orden()).as("queda al final del catálogo")
                .isEqualTo(categorias.listar(null).stream().mapToInt(CategoriaDto::orden).max().orElseThrow());

        CategoriaTemporadaDto asignada = grupos.asignarCategoria(temporada.id(),
                new AsignarCategoriaRequest(categoria.id()));
        GrupoDto grupo = grupos.crear(new CrearGrupoRequest(asignada.id(), "A"));

        TemporadaDto actual = temporadas.consultar(temporada.id());
        assertThat(actual.categoriasAsignadas()).isEqualTo(1);
        assertThat(actual.gruposCreados()).isEqualTo(1);
        assertThat(actual.activacion().requisitos())
                .filteredOn(requisito -> List.of("RF-004", "RF-005").contains(requisito.codigo()))
                .allSatisfy(requisito -> assertThat(requisito.cumple()).isTrue());

        List<CategoriaTemporadaDto> estructura = grupos.listarCategorias(temporada.id());
        assertThat(estructura).singleElement().satisfies(fila -> {
            assertThat(fila.categoria().id()).isEqualTo(categoria.id());
            assertThat(fila.grupos()).extracting(CategoriaTemporadaDto.GrupoResumenDto::id).containsExactly(grupo.id());
            assertThat(fila.grupos().getFirst().equipos()).isZero();
        });
        assertThat(grupos.consultar(grupo.id()).temporadaId()).isEqualTo(temporada.id());
        assertThat(grupos.listar(asignada.id())).extracting(GrupoDto::id).containsExactly(grupo.id());

        assertThat(acciones("categorias", categoria.id())).containsExactly("REGISTRAR_CATEGORIA");
        assertThat(acciones("categorias_temporada", asignada.id())).containsExactly("ASIGNAR_CATEGORIA_TEMPORADA");
        assertThat(acciones("grupos", grupo.id())).containsExactly("REGISTRAR_GRUPO");

        assertThatThrownBy(() -> grupos.asignarCategoria(temporada.id(), new AsignarCategoriaRequest(categoria.id())))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
    }

    // ── Jerarquía ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-099, D2: A → B → C → A se rechaza con 409 RF-099 y la jerarquía no cambia")
    void cicloDeTresRechazado() {
        iniciarSesionComoAdministrador();
        CategoriaDto c = categorias.crear(new CrearCategoriaRequest(unico("C"), TipoCategoria.LIBRE, null));
        CategoriaDto b = categorias.crear(new CrearCategoriaRequest(unico("B"), TipoCategoria.LIBRE, c.id()));
        CategoriaDto a = categorias.crear(new CrearCategoriaRequest(unico("A"), TipoCategoria.LIBRE, b.id()));

        assertThatThrownBy(() -> categorias.actualizar(c.id(),
                new ActualizarCategoriaRequest(null, null, a.id(), null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-099"));

        assertThat(categorias.consultar(c.id()).categoriaSuperior()).isNull();
        assertThat(categorias.consultar(a.id()).categoriaSuperior().id()).isEqualTo(b.id());
        assertThat(acciones("categorias", c.id())).containsExactly("REGISTRAR_CATEGORIA");
    }

    @Test
    @DisplayName("RF-099, D4: no se desactiva una categoría asignada a una temporada en configuración")
    void desactivarCategoriaEnUso() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = temporadas.crear(new CrearTemporadaRequest(unico("Temporada"), INICIO, null));
        CategoriaDto categoria = categorias.crear(new CrearCategoriaRequest(unico("Veteranos"),
                TipoCategoria.VETERANOS, null));
        grupos.asignarCategoria(temporada.id(), new AsignarCategoriaRequest(categoria.id()));

        assertThatThrownBy(() -> categorias.actualizar(categoria.id(),
                new ActualizarCategoriaRequest(null, null, null, null, false)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-099"));
        assertThat(categorias.consultar(categoria.id()).activa()).isTrue();
    }

    @Test
    @DisplayName("RF-099: una categoría libre se desactiva, sale del filtro activa=true y se reactiva")
    void desactivarYReactivar() {
        iniciarSesionComoAdministrador();
        CategoriaDto categoria = categorias.crear(new CrearCategoriaRequest(unico("Infantil"),
                TipoCategoria.INFANTIL, null));

        categorias.actualizar(categoria.id(), new ActualizarCategoriaRequest(null, null, null, null, false));
        assertThat(categorias.listar(true)).extracting(CategoriaDto::id).doesNotContain(categoria.id());
        assertThat(categorias.listar(false)).extracting(CategoriaDto::id).contains(categoria.id());

        CategoriaDto reactivada = categorias.actualizar(categoria.id(),
                new ActualizarCategoriaRequest(null, null, null, null, true));
        assertThat(reactivada.activa()).isTrue();
        assertThat(acciones("categorias", categoria.id()))
                .containsExactly("REGISTRAR_CATEGORIA", "EDITAR_CATEGORIA", "EDITAR_CATEGORIA");
    }

    @Test
    @DisplayName("RF-110, D5: en una temporada activa la superior de la categoría no cambia, pero el nombre sí")
    void superiorBloqueadaEnTemporadaActiva() {
        iniciarSesionComoAdministrador();
        CategoriaDto libre = categorias.crear(new CrearCategoriaRequest(unico("Libre"), TipoCategoria.LIBRE, null));
        CategoriaDto otra = categorias.crear(new CrearCategoriaRequest(unico("Otra"), TipoCategoria.LIBRE, null));
        CategoriaDto juvenil = categorias.crear(new CrearCategoriaRequest(unico("Juvenil"), TipoCategoria.LIBRE,
                libre.id()));
        TemporadaDto temporada = temporadas.crear(new CrearTemporadaRequest(unico("Temporada"), INICIO, null));
        CategoriaTemporadaDto asignada = grupos.asignarCategoria(temporada.id(),
                new AsignarCategoriaRequest(juvenil.id()));
        grupos.crear(new CrearGrupoRequest(asignada.id(), "A"));

        // Preparación: H-05.2 (parámetros) aún no existe y RN-058 admite una sola temporada activa.
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, motivo) VALUES (:id, 'Parámetros de prueba')")
                .param("id", temporada.id()).update();
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
        assertThat(temporadas.activar(temporada.id()).estado()).isEqualTo(EstadoTemporada.ACTIVA);

        assertThatThrownBy(() -> categorias.actualizar(juvenil.id(),
                new ActualizarCategoriaRequest(null, null, otra.id(), null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-110"));
        assertThatThrownBy(() -> categorias.actualizar(juvenil.id(),
                new ActualizarCategoriaRequest(null, null, null, true, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-110"));
        assertThat(categorias.consultar(juvenil.id()).categoriaSuperior().id())
                .as("la jerarquía no cambia").isEqualTo(libre.id());

        String nombreNuevo = unico("Juvenil varonil");
        CategoriaDto renombrada = categorias.actualizar(juvenil.id(),
                new ActualizarCategoriaRequest(nombreNuevo, null, null, null, null));
        assertThat(renombrada.nombre()).isEqualTo(nombreNuevo);
        assertThat(renombrada.categoriaSuperior().id()).isEqualTo(libre.id());
        assertThat(acciones("categorias", juvenil.id())).containsExactly("REGISTRAR_CATEGORIA", "EDITAR_CATEGORIA");
    }

    // ── Reordenamiento ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-099, D3: el reordenamiento asigna 1..n en una transacción y una lista incompleta no cambia nada")
    void reordenamientoAtomico() {
        iniciarSesionComoAdministrador();
        CategoriaDto inactiva = categorias.crear(new CrearCategoriaRequest(unico("Inactiva"), TipoCategoria.LIBRE, null));
        categorias.actualizar(inactiva.id(), new ActualizarCategoriaRequest(null, null, null, null, false));
        categorias.crear(new CrearCategoriaRequest(unico("Activa"), TipoCategoria.LIBRE, null));

        List<UUID> antes = categorias.listar(null).stream().map(CategoriaDto::id).toList();
        List<UUID> incompleta = antes.subList(1, antes.size());
        assertThatThrownBy(() -> categorias.reordenar(new ReordenarCategoriasRequest(incompleta)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "VALIDACION"));
        assertThat(categorias.listar(null)).extracting(CategoriaDto::id).containsExactlyElementsOf(antes);

        long auditoriasAntes = reordenamientosAuditados();
        List<UUID> invertido = new ArrayList<>(antes);
        Collections.reverse(invertido);
        List<CategoriaDto> despues = categorias.reordenar(new ReordenarCategoriasRequest(invertido));

        assertThat(despues).extracting(CategoriaDto::id).containsExactlyElementsOf(invertido);
        for (int posicion = 0; posicion < despues.size(); posicion++) {
            assertThat(despues.get(posicion).orden()).isEqualTo(posicion + 1);
        }
        assertThat(reordenamientosAuditados()).isEqualTo(auditoriasAntes + 1);
    }

    // ── Grupos ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-005: un grupo con el nombre de otro de la misma categoría responde 409 DUPLICADO")
    void grupoDuplicado() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = temporadas.crear(new CrearTemporadaRequest(unico("Temporada"), INICIO, null));
        CategoriaDto categoria = categorias.crear(new CrearCategoriaRequest(unico("Libre"), TipoCategoria.LIBRE, null));
        CategoriaTemporadaDto asignada = grupos.asignarCategoria(temporada.id(),
                new AsignarCategoriaRequest(categoria.id()));
        GrupoDto a = grupos.crear(new CrearGrupoRequest(asignada.id(), "A"));
        GrupoDto b = grupos.crear(new CrearGrupoRequest(asignada.id(), "B"));

        assertThatThrownBy(() -> grupos.crear(new CrearGrupoRequest(asignada.id(), " A ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));
        assertThatThrownBy(() -> grupos.actualizar(b.id(), new ActualizarGrupoRequest("A")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "DUPLICADO"));

        GrupoDto renombrado = grupos.actualizar(b.id(), new ActualizarGrupoRequest("C"));
        assertThat(renombrado.nombre()).isEqualTo("C");
        assertThat(grupos.listar(asignada.id())).extracting(GrupoDto::id).containsExactly(a.id(), b.id());
        assertThat(acciones("grupos", b.id())).containsExactly("REGISTRAR_GRUPO", "EDITAR_GRUPO");
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private static String unico(String prefijo) {
        return prefijo + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<String> acciones(String entidad, UUID id) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE modulo = 'M-02' AND entidad = :entidad AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("entidad", entidad)
                .param("id", id.toString())
                .query(String.class)
                .list();
    }

    private long reordenamientosAuditados() {
        return jdbc.sql("SELECT count(*) FROM siglim.auditoria WHERE accion = 'REORDENAR_CATEGORIAS'")
                .query(Long.class)
                .single();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}
