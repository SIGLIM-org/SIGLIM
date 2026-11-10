package mx.tecnm.morelia.siglim.temporadas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.CategoriaRepository.FilaCategoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de categorías de la liga con su orden y su categoría superior inmediata (RF-099,
 * DEC-16): alta, edición, activación y reordenamiento.
 *
 * <ul>
 *   <li><b>Permisos (D1).</b> Las consultas exigen {@code temporadas.temporada:CONSULTAR} y las
 *       escrituras {@code temporadas.temporada:EDITAR}, ambas sobre la liga
 *       ({@link ObjetivoAutorizacion#deLiga()}).</li>
 *   <li><b>Jerarquía (RF-099, D2).</b> La superior debe existir en la liga ({@code 422
 *       REFERENCIA_INVALIDA}), estar activa y no formar un ciclo ({@code 409 RF-099}). El ciclo se
 *       detecta recorriendo la cadena de superiores desde la propuesta, con el catálogo bloqueado
 *       ({@code FOR UPDATE}) para que dos ediciones concurrentes no lo formen.</li>
 *   <li><b>Jerarquía en uso (RF-110, D5).</b> El trigger de alineaciones decide con
 *       {@code categoria_superior_id} si un jugador puede jugar en otra categoría; por eso la
 *       superior de una categoría que participa en una temporada {@code ACTIVA} no cambia
 *       ({@code 409 RF-110}).</li>
 *   <li><b>Desactivar (D4).</b> No procede si la categoría participa en una temporada en
 *       configuración o activa, o si es la superior de otra categoría activa ({@code 409 RF-099}).
 *       Reactivar procede salvo que su superior resultante esté inactiva ({@code 409 RF-099}): toda
 *       categoría activa tiene una superior activa o ninguna. No hay borrado.</li>
 *   <li><b>Orden (D3).</b> Una categoría nueva queda al final; el reordenamiento recibe todas las
 *       categorías de la liga y asigna 1..n en una sola transacción con una sola auditoría.</li>
 *   <li><b>Auditoría (RF-093, D9).</b> {@code REGISTRAR_CATEGORIA}, {@code EDITAR_CATEGORIA} y
 *       {@code REORDENAR_CATEGORIAS} en el módulo M-02, sin motivo y con solo identificadores y
 *       datos no personales en el detalle. Una edición sin cambios no se audita.</li>
 * </ul>
 *
 * <p>Refs: H-05.1, RF-004, RF-093, RF-099, RF-110, DEC-16, ADR-0007, ADR-0011
 */
@Service
public class CategoriaService {

    private static final String CONSULTAR = "temporadas.temporada:CONSULTAR";
    private static final String EDITAR = "temporadas.temporada:EDITAR";
    private static final String MODULO = "M-02";
    private static final String ENTIDAD = "categorias";
    private static final int LONGITUD_NOMBRE = 80;

    private final CategoriaRepository categorias;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    CategoriaService(CategoriaRepository categorias, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.categorias = categorias;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /** Catálogo por orden y nombre, sin paginar (RF-099); {@code activa} nulo no filtra. */
    @Transactional(readOnly = true)
    public List<CategoriaDto> listar(Boolean activa) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return categorias.listar(ligaId, activa);
    }

    /** Una categoría del catálogo; inexistente o de otra liga responde 404 (RF-099). */
    @Transactional(readOnly = true)
    public CategoriaDto consultar(UUID id) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return buscar(id);
    }

    /**
     * Registra una categoría activa al final del catálogo (RF-099).
     *
     * <p>Orden: permiso → nombre ({@code 400}) → superior existe ({@code 422}) → superior activa
     * ({@code 409 RF-099}) → nombre libre en la liga ({@code 409 DUPLICADO}).
     */
    @Transactional
    public CategoriaDto crear(CrearCategoriaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = nombreValido(peticion.nombre());
        if (peticion.tipo() == null) {
            throw invalida("El tipo de la categoría es obligatorio.");
        }
        Map<UUID, FilaCategoria> catalogo = bloquearCatalogo();
        UUID superiorId = peticion.categoriaSuperiorId();
        if (superiorId != null) {
            FilaCategoria superior = superiorExistente(catalogo, superiorId);
            exigirSuperiorActiva(nombre, superior);
        }
        validarNombreLibre(nombre, null);

        UUID id = categorias.insertar(ligaId, nombre, peticion.tipo(), superiorId);
        CategoriaDto creada = buscar(id);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tipo", creada.tipo().name());
        detalle.put("orden", creada.orden());
        if (superiorId != null) {
            detalle.put("categoriaSuperiorId", superiorId.toString());
        }
        auditar("REGISTRAR_CATEGORIA", id, detalle);
        return creada;
    }

    /**
     * Edita nombre, tipo, superior o activa; lo ausente conserva su valor (RF-099). Sin cambios
     * responde la categoría sin auditar.
     *
     * <p>Orden: permiso → cuerpo ({@code 400}) → categoría existe ({@code 404}) → superior existe
     * ({@code 422}) → superior propia, inactiva o en ciclo ({@code 409 RF-099}) → jerarquía en uso
     * ({@code 409 RF-110}) → desactivación ({@code 409 RF-099}) → nombre libre ({@code 409
     * DUPLICADO}).
     */
    @Transactional
    public CategoriaDto actualizar(UUID id, ActualizarCategoriaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        boolean quitarSuperior = Boolean.TRUE.equals(peticion.quitarSuperior());
        if (quitarSuperior && peticion.categoriaSuperiorId() != null) {
            throw invalida("Indica una categoría superior o pide quitarla, pero no las dos cosas a la vez.");
        }
        String nombrePedido = peticion.nombre() == null ? null : nombreValido(peticion.nombre());

        Map<UUID, FilaCategoria> catalogo = bloquearCatalogo();
        FilaCategoria actual = catalogo.get(id);
        if (actual == null) {
            throw noExiste(id);
        }
        CategoriaDto previa = buscar(id);

        String nombre = nombrePedido == null ? actual.nombre() : nombrePedido;
        TipoCategoria tipo = peticion.tipo() == null ? previa.tipo() : peticion.tipo();
        UUID superiorId;
        if (quitarSuperior) {
            superiorId = null;
        } else {
            superiorId = peticion.categoriaSuperiorId() == null
                    ? actual.categoriaSuperiorId() : peticion.categoriaSuperiorId();
        }
        boolean activa = peticion.activa() == null ? actual.activa() : peticion.activa();

        List<String> campos = new ArrayList<>();
        if (!nombre.equals(actual.nombre())) {
            campos.add("nombre");
        }
        if (tipo != previa.tipo()) {
            campos.add("tipo");
        }
        boolean cambiaSuperior = !Objects.equals(superiorId, actual.categoriaSuperiorId());
        if (cambiaSuperior) {
            campos.add("categoriaSuperiorId");
        }
        if (activa != actual.activa()) {
            campos.add("activa");
        }
        if (campos.isEmpty()) {
            return previa;
        }

        if (cambiaSuperior) {
            validarNuevaSuperior(actual, superiorId, catalogo);
            exigirFueraDeTemporadaActiva(actual);
        }
        if (!actual.activa() && activa && superiorId != null) {
            exigirSuperiorActivaAlReactivar(actual, catalogo.get(superiorId));
        }
        if (actual.activa() && !activa) {
            validarDesactivacion(actual, catalogo);
        }
        if (!nombre.equals(actual.nombre())) {
            validarNombreLibre(nombre, id);
        }

        categorias.actualizar(id, nombre, tipo, superiorId, activa);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        if (cambiaSuperior) {
            detalle.put("categoriaSuperiorId", superiorId == null ? null : superiorId.toString());
        }
        if (activa != actual.activa()) {
            detalle.put("activa", activa);
        }
        auditar("EDITAR_CATEGORIA", id, detalle);
        return buscar(id);
    }

    /**
     * Asigna el orden 1..n en el orden recibido, de forma atómica (RF-099, D3). Si el orden ya es
     * ese, responde el catálogo sin auditar.
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} si falta, sobra o se repite alguna
     *                               categoría, con las extensiones {@code faltantes},
     *                               {@code sobrantes} y {@code repetidos}
     */
    @Transactional
    public List<CategoriaDto> reordenar(ReordenarCategoriasRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        List<UUID> ids = peticion == null ? null : peticion.ids();
        if (ids == null) {
            throw invalida("Envía la lista de categorías en el orden nuevo.");
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw invalida("La lista de categorías tiene elementos vacíos.");
        }
        Set<UUID> vistos = new HashSet<>();
        Set<UUID> repetidos = new LinkedHashSet<>();
        for (UUID categoriaId : ids) {
            if (!vistos.add(categoriaId)) {
                repetidos.add(categoriaId);
            }
        }

        List<FilaCategoria> filas = categorias.bloquearCatalogo(ligaId);
        Set<UUID> existentes = filas.stream().map(FilaCategoria::id).collect(Collectors.toSet());
        List<UUID> faltantes = filas.stream().map(FilaCategoria::id).filter(existente -> !vistos.contains(existente))
                .toList();
        List<UUID> sobrantes = vistos.stream().filter(pedido -> !existentes.contains(pedido)).toList();
        if (!repetidos.isEmpty() || !faltantes.isEmpty() || !sobrantes.isEmpty()) {
            throw invalida(mensajeReordenamiento(repetidos.size(), faltantes.size(), sobrantes.size()))
                    .con("faltantes", faltantes)
                    .con("sobrantes", sobrantes)
                    .con("repetidos", List.copyOf(repetidos));
        }

        if (yaOrdenado(ids, filas)) {
            return categorias.listar(ligaId, null);
        }
        categorias.reordenar(ligaId, ids);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("ids", ids.stream().map(UUID::toString).toList());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REORDENAR_CATEGORIAS", ENTIDAD, null, null,
                detalle));
        return categorias.listar(ligaId, null);
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private CategoriaDto buscar(UUID id) {
        return categorias.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Categoría " + id);
    }

    private Map<UUID, FilaCategoria> bloquearCatalogo() {
        return categorias.bloquearCatalogo(ligaId).stream()
                .collect(Collectors.toMap(FilaCategoria::id, Function.identity()));
    }

    private static FilaCategoria superiorExistente(Map<UUID, FilaCategoria> catalogo, UUID superiorId) {
        FilaCategoria superior = catalogo.get(superiorId);
        if (superior == null) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "La categoría superior indicada no existe.");
        }
        return superior;
    }

    private static void exigirSuperiorActiva(String nombre, FilaCategoria superior) {
        if (!superior.activa()) {
            throw ReglaNegocioException.conflicto("RF-099",
                    "La categoría «" + superior.nombre() + "» está inactiva: reactívala primero o quita la "
                            + "superior de «" + nombre + "».");
        }
    }

    /**
     * Invariante de RF-099: toda categoría activa tiene una superior activa o ninguna. Al reactivar,
     * la superior resultante (la actual o la del mismo {@code PATCH}) debe estar activa.
     */
    private static void exigirSuperiorActivaAlReactivar(FilaCategoria categoria, FilaCategoria superior) {
        if (superior != null) {
            exigirSuperiorActiva(categoria.nombre(), superior);
        }
    }

    /** D2: existe (422), no es ella misma, está activa y no forma un ciclo (409 RF-099). */
    private static void validarNuevaSuperior(FilaCategoria categoria, UUID superiorId,
            Map<UUID, FilaCategoria> catalogo) {
        if (superiorId == null) {
            return;
        }
        FilaCategoria superior = superiorExistente(catalogo, superiorId);
        if (superiorId.equals(categoria.id())) {
            throw ReglaNegocioException.conflicto("RF-099",
                    "La categoría «" + categoria.nombre() + "» no puede ser su propia superior.");
        }
        exigirSuperiorActiva(categoria.nombre(), superior);

        List<String> cadena = new ArrayList<>(List.of(categoria.nombre()));
        Set<UUID> recorridas = new HashSet<>();
        UUID siguiente = superiorId;
        while (siguiente != null && recorridas.add(siguiente)) {
            FilaCategoria eslabon = catalogo.get(siguiente);
            if (eslabon == null) {
                return;
            }
            cadena.add(eslabon.nombre());
            if (eslabon.categoriaSuperiorId() != null && eslabon.categoriaSuperiorId().equals(categoria.id())) {
                cadena.add(categoria.nombre());
                throw ReglaNegocioException.conflicto("RF-099",
                        "«" + superior.nombre() + "» no puede ser la superior de «" + categoria.nombre()
                                + "» porque la jerarquía formaría un ciclo: " + String.join(" → ", cadena) + ".");
            }
            siguiente = eslabon.categoriaSuperiorId();
        }
    }

    /** D5: el trigger de alineaciones usa la jerarquía de las temporadas activas (RF-110). */
    private void exigirFueraDeTemporadaActiva(FilaCategoria categoria) {
        List<String> activas = categorias.temporadasConCategoria(categoria.id(), List.of(EstadoTemporada.ACTIVA));
        if (!activas.isEmpty()) {
            throw ReglaNegocioException.conflicto("RF-110",
                    "La categoría «" + categoria.nombre() + "» participa en la temporada activa «"
                            + activas.getFirst() + "»: su categoría superior no cambia hasta que la temporada "
                            + "cierre, porque decide en qué equipos puede alinearse un jugador.");
        }
    }

    /** D4: no se desactiva una categoría en uso ni la superior de otra activa (RF-099). */
    private void validarDesactivacion(FilaCategoria categoria, Map<UUID, FilaCategoria> catalogo) {
        List<String> enUso = categorias.temporadasConCategoria(categoria.id(),
                List.of(EstadoTemporada.CONFIGURACION, EstadoTemporada.ACTIVA));
        if (!enUso.isEmpty()) {
            throw ReglaNegocioException.conflicto("RF-099",
                    "La categoría «" + categoria.nombre() + "» participa en la temporada «" + enUso.getFirst()
                            + "», que no está cerrada: no se puede desactivar.");
        }
        List<String> subordinadas = catalogo.values().stream()
                .filter(otra -> otra.activa() && categoria.id().equals(otra.categoriaSuperiorId()))
                .map(FilaCategoria::nombre)
                .sorted()
                .toList();
        if (!subordinadas.isEmpty()) {
            throw ReglaNegocioException.conflicto("RF-099",
                    "La categoría «" + categoria.nombre() + "» es la superior de «" + String.join("», «", subordinadas)
                            + "»: cambia primero su categoría superior y después desactívala.");
        }
    }

    private void validarNombreLibre(String nombre, UUID excepto) {
        if (categorias.existeNombre(ligaId, nombre, excepto)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe una categoría llamada «" + nombre + "» en la liga.");
        }
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw invalida("El nombre de la categoría es obligatorio.");
        }
        if (texto.length() > LONGITUD_NOMBRE) {
            throw invalida("El nombre de la categoría admite hasta " + LONGITUD_NOMBRE + " caracteres.");
        }
        return texto;
    }

    /** El catálogo ya tiene el orden 1..n en el orden pedido. */
    private static boolean yaOrdenado(List<UUID> ids, List<FilaCategoria> filas) {
        Map<UUID, Integer> orden = filas.stream().collect(Collectors.toMap(FilaCategoria::id, FilaCategoria::orden));
        for (int posicion = 0; posicion < ids.size(); posicion++) {
            if (orden.get(ids.get(posicion)) != posicion + 1) {
                return false;
            }
        }
        return true;
    }

    private static String mensajeReordenamiento(int repetidos, int faltantes, int sobrantes) {
        List<String> causas = new ArrayList<>();
        if (faltantes > 0) {
            causas.add("faltan " + faltantes);
        }
        if (sobrantes > 0) {
            causas.add(sobrantes + " no existen en la liga");
        }
        if (repetidos > 0) {
            causas.add(repetidos + " están repetidas");
        }
        return "La lista debe incluir cada categoría de la liga una sola vez, activas e inactivas: "
                + String.join(", ", causas) + ". Actualiza la página e intenta de nuevo.";
    }

    // ── Auditoría (RF-093): solo identificadores y datos no personales ─────────────────────────

    private void auditar(String accion, UUID entidadId, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(), null,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
