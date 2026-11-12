package mx.tecnm.morelia.siglim.registros;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.AlmacenamientoDocumentos;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ArchivoAlmacenado;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.CarpetaDocumento;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ClaveDocumento;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.EnlaceTemporal;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.DocumentoRegistroRepository.RegistroDeDocumentos;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.Avance;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.DocumentoRegistro;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.RequisitoDocumento;
import mx.tecnm.morelia.siglim.registros.RequisitosRegistroDto.CategoriaDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Documentos de un registro de jugador: carga, reemplazo, requisitos, enlace de lectura y validación
 * de cada documento (RF-108, RF-109, CU-004, CU-021).
 *
 * <ul>
 *   <li><b>Permisos y ámbito (DEC-91, ADR-0007).</b> Cargar y reemplazar exigen
 *       {@code registros.documento:CREAR} y validar {@code registros.documento:VALIDAR}, sobre el club
 *       del equipo del registro ({@link ObjetivoAutorizacion#deClubes}). Los requisitos
 *       ({@code registros.registro:CONSULTAR}) y el enlace ({@code registros.documento:CONSULTAR}) usan
 *       {@link ObjetivoAutorizacion#deClubesYPersonas} con la persona del registro, para que el
 *       jugador o su tutor (ámbito {@code PROPIO}) también lleguen. Nunca se comparan roles.</li>
 *   <li><b>Orden de errores (DEC-91).</b> {@code alcance} (403 {@code VAL-015}) → obligatorios
 *       (400) → registro o documento inexistente o fuera de ámbito (404, VAL-024) → reglas de
 *       DEC-92 a DEC-96 → archivo (400 vacío, 413, 415, del almacenamiento) → escritura y
 *       auditoría. La subida a S3 va al final para no dejar objetos huérfanos por un error
 *       previsible; si la transacción falla después, el objeto queda huérfano a propósito
 *       ({@link AlmacenamientoDocumentos}).</li>
 *   <li><b>Concurrencia (DEC-93).</b> Toda escritura bloquea primero el registro
 *       ({@code FOR UPDATE}); los {@code UPDATE} de documentos son condicionales y, si no devuelven
 *       fila, responden {@code 409 RF-109}. No se atrapa {@code DuplicateKeyException}.</li>
 *   <li><b>Auditoría (DEC-95, RF-093).</b> {@code CARGAR_DOCUMENTO}, {@code REEMPLAZAR_DOCUMENTO},
 *       {@code VALIDAR_DOCUMENTO} y {@code CONSULTAR_DOCUMENTO} en {@code M-04}, entidad
 *       {@code documentos}, en la misma transacción. El detalle nunca lleva la URL (guardada o
 *       prefirmada), el bucket, la clave, el SHA-256, el nombre del archivo ni datos personales.</li>
 * </ul>
 *
 * <p>No cambia el estado del registro: la validación del registro y VAL-001 son de H-10.2, que
 * reutiliza {@link RequisitosDocumentos}.
 *
 * <p>Refs: H-10.1, RF-003, RF-017, RF-093, RF-108, RF-109, RN-055, RNF-018, VAL-015, VAL-022, VAL-024,
 * CU-004, CU-021, ADR-0007, ADR-0013, DEC-91 a DEC-96
 */
@Service
public class DocumentoRegistroService {

    private static final String CREAR = "registros.documento:CREAR";
    private static final String VALIDAR = "registros.documento:VALIDAR";
    private static final String CONSULTAR_DOCUMENTO = "registros.documento:CONSULTAR";
    private static final String CONSULTAR_REGISTRO = "registros.registro:CONSULTAR";
    private static final String MODULO = "M-04";
    private static final String ENTIDAD = "documentos";
    private static final int MINIMO_OBSERVACIONES = 10;
    private static final int MAXIMO_OBSERVACIONES = 500;

    private final DocumentoRegistroRepository documentos;
    private final AlmacenamientoDocumentos almacenamiento;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    DocumentoRegistroService(DocumentoRegistroRepository documentos, AlmacenamientoDocumentos almacenamiento,
            Autorizacion autorizacion, AuditoriaService auditoria, Clock reloj, SiglimPropiedades propiedades) {
        this.documentos = documentos;
        this.almacenamiento = almacenamiento;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Carga un documento de un tipo que el registro exige; queda {@code PENDIENTE} (RF-108).
     *
     * <p>Orden de errores (DEC-91 a DEC-93):
     * <ol>
     *   <li>Sin {@code registros.documento:CREAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Falta el archivo o {@code tipoDocumentoId}: {@code 400 VALIDACION}.</li>
     *   <li>Registro inexistente en la liga o de un club fuera de ámbito: {@code 404} (VAL-024).</li>
     *   <li>Temporada {@code CERRADA}: {@code 409 RF-003}.</li>
     *   <li>Registro que no está {@code PENDIENTE_VALIDACION}: {@code 409 RF-017}.</li>
     *   <li>Tipo inexistente: {@code 422 REFERENCIA_INVALIDA}.</li>
     *   <li>Tipo que el registro no exige (otra categoría, INE del tutor de un adulto…):
     *       {@code 422 RF-108}.</li>
     *   <li>Cupo del tipo lleno con documentos vigentes: {@code 409 DUPLICADO}.</li>
     *   <li>Archivo vacío ({@code 400 VALIDACION}), mayor que el límite
     *       ({@code 413 ARCHIVO_DEMASIADO_GRANDE}) o de una firma no permitida
     *       ({@code 415 TIPO_ARCHIVO_NO_PERMITIDO}).</li>
     * </ol>
     *
     * @param archivo bytes del archivo; se guarda el MIME detectado por su firma, nunca el nombre
     */
    @Transactional
    public DocumentoDto cargar(UUID registroId, Long tipoDocumentoId, byte[] archivo) {
        autorizacion.alcance(CREAR);
        if (archivo == null || tipoDocumentoId == null) {
            throw invalida("Indica el tipo de documento y el archivo.");
        }
        RegistroDeDocumentos registro = registroParaEscribir(registroId, CREAR);
        validarRegistroEditable(registro);

        TipoDocumentoDto tipo = documentos.tipoDocumento(tipoDocumentoId)
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "El tipo de documento indicado no existe. Actualiza la página e intenta de nuevo."));
        boolean esMenor = esMenor(registro);
        RequisitoDocumento requisito = RequisitosDocumentos.exigido(
                documentos.requisitos(ligaId, registro.categoriaTipo()), esMenor, tipo.id());
        if (requisito == null) {
            throw ReglaNegocioException.noProcesable("RF-108", noExigido(tipo, registro, esMenor));
        }
        if (RequisitosDocumentos.vigentesDelTipo(documentos.vigentes(registro.id()), tipo.id()) >= requisito.cantidad()) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Este registro ya tiene " + cantidad(requisito.cantidad()) + " de «" + tipo.nombre()
                            + "». Para cambiar uno, reemplázalo.");
        }

        UUID documentoId = UUID.randomUUID();
        ArchivoAlmacenado guardado = almacenamiento.guardar(
                new ClaveDocumento(ligaId, CarpetaDocumento.REGISTROS, registro.id(), documentoId), archivo);
        documentos.insertar(documentoId, tipo.id(), registro.id(), guardado, UsuarioActual.idRequerido());

        Map<String, Object> detalle = detalleArchivo(registro.id(), tipo.codigo(), guardado);
        auditar("CARGAR_DOCUMENTO", documentoId, null, detalle);
        return buscarInsertado(documentoId);
    }

    /**
     * Carga un documento nuevo en lugar de uno vigente {@code PENDIENTE} o {@code RECHAZADO}, con el
     * mismo tipo y registro; el anterior conserva su estado y queda con {@code reemplazado_por_id}
     * (DEC-93). No se borra nada en S3 (ADR-0013).
     *
     * <p>Orden de errores: {@code 403 VAL-015} → falta el archivo ({@code 400 VALIDACION}) → documento
     * inexistente, de otro dueño o fuera de ámbito ({@code 404}) → temporada {@code CERRADA}
     * ({@code 409 RF-003}) → registro no {@code PENDIENTE_VALIDACION} ({@code 409 RF-017}) → documento
     * {@code APROBADO} o ya reemplazado ({@code 409 RF-109}) → archivo (400, 413, 415). Si otra
     * petición lo reemplazó o lo aprobó entre tanto, {@code 409 RF-109}.
     */
    @Transactional
    public DocumentoDto reemplazar(UUID documentoId, byte[] archivo) {
        autorizacion.alcance(CREAR);
        if (archivo == null) {
            throw invalida("Selecciona el archivo que reemplaza al documento.");
        }
        DocumentoRegistro anterior = buscarDocumento(documentoId);
        RegistroDeDocumentos registro = registroParaEscribir(anterior.dto().registroId(), CREAR);
        validarRegistroEditable(registro);
        if (anterior.reemplazadoPorId() != null) {
            throw ReglaNegocioException.conflicto("RF-109",
                    "Este documento ya fue reemplazado. Actualiza la página para ver el documento vigente.");
        }
        if (anterior.dto().estadoValidacion() == EstadoValidacion.APROBADO) {
            throw ReglaNegocioException.conflicto("RF-109",
                    "Este documento ya está aprobado y no se reemplaza.");
        }

        UUID nuevoId = UUID.randomUUID();
        ArchivoAlmacenado guardado = almacenamiento.guardar(
                new ClaveDocumento(ligaId, CarpetaDocumento.REGISTROS, registro.id(), nuevoId), archivo);
        documentos.insertar(nuevoId, anterior.dto().tipoDocumento().id(), registro.id(), guardado,
                UsuarioActual.idRequerido());
        if (!documentos.marcarReemplazado(anterior.dto().id(), nuevoId)) {
            throw ReglaNegocioException.conflicto("RF-109",
                    "Otra persona cambió este documento al mismo tiempo. Actualiza la página e intenta de nuevo.");
        }

        Map<String, Object> detalle = detalleArchivo(registro.id(), anterior.dto().tipoDocumento().codigo(), guardado);
        detalle.put("documentoAnteriorId", anterior.dto().id().toString());
        auditar("REEMPLAZAR_DOCUMENTO", nuevoId, null, detalle);
        return buscarInsertado(nuevoId);
    }

    /**
     * Documentos que exige el registro y su avance (RF-108, DEC-92, DEC-96).
     *
     * <p>Orden de errores: {@code 403 VAL-015} sin {@code registros.registro:CONSULTAR} →
     * {@code 404} si el registro no existe en la liga o está fuera de ámbito (club o persona del
     * registro).
     */
    @Transactional(readOnly = true)
    public RequisitosRegistroDto requisitos(UUID registroId) {
        autorizacion.alcance(CONSULTAR_REGISTRO);
        RegistroDeDocumentos registro = documentos.buscarRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
        autorizacion.exigir(CONSULTAR_REGISTRO, objetivoDeConsulta(registro));

        boolean esMenor = esMenor(registro);
        Avance avance = RequisitosDocumentos.calcular(documentos.requisitos(ligaId, registro.categoriaTipo()), esMenor,
                documentos.vigentes(registro.id()));
        return new RequisitosRegistroDto(registro.id(), registro.estado(),
                new CategoriaDto(registro.categoriaId(), registro.categoriaNombre(), registro.categoriaTipo()),
                esMenor, avance.requisitos(), avance.completo(), avance.listoParaValidar());
    }

    /**
     * URL prefirmada de lectura de 60 s (RNF-018, DEC-95). Un documento reemplazado también se puede
     * consultar: es historial. La URL no se guarda, no se audita ni va a la bitácora; la consulta se
     * audita como {@code CONSULTAR_DOCUMENTO}, por eso la transacción no es de solo lectura.
     *
     * <p>Orden de errores: {@code 403 VAL-015} sin {@code registros.documento:CONSULTAR} →
     * {@code 404} si el documento no existe, no es de un registro o está fuera de ámbito →
     * {@code 503 SERVICIO_NO_DISPONIBLE} si no se puede firmar.
     */
    @Transactional
    public EnlaceTemporal enlace(UUID documentoId) {
        autorizacion.alcance(CONSULTAR_DOCUMENTO);
        DocumentoRegistro documento = buscarDocumento(documentoId);
        RegistroDeDocumentos registro = documentos.buscarRegistro(ligaId, documento.dto().registroId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro del documento " + documentoId));
        autorizacion.exigir(CONSULTAR_DOCUMENTO, objetivoDeConsulta(registro));

        EnlaceTemporal enlace = almacenamiento.generarEnlaceLectura(documento.s3Bucket(), documento.s3Clave());
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("registroId", registro.id().toString());
        detalle.put("tipoDocumento", documento.dto().tipoDocumento().codigo());
        auditar("CONSULTAR_DOCUMENTO", documentoId, null, detalle);
        return enlace;
    }

    /**
     * Aprueba o rechaza un documento vigente {@code PENDIENTE} (RF-109, DEC-96). Guarda el estado,
     * quién y cuándo validó y las observaciones; <b>no</b> cambia el estado del registro (H-10.2).
     *
     * <p>Orden de errores:
     * <ol>
     *   <li>Sin {@code registros.documento:VALIDAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Falta {@code decision}: {@code 400 VALIDACION}; observaciones de más de 500 caracteres:
     *       {@code 400 VALIDACION}; rechazo sin observaciones de al menos 10 caracteres (sin contar
     *       los espacios de los extremos): {@code 400 RF-109}.</li>
     *   <li>Documento inexistente, de otro dueño o fuera de ámbito: {@code 404} (VAL-024).</li>
     *   <li>Quien valida es quien cargó el documento: {@code 403 VAL-022} <b>[PROPUESTA]</b>
     *       (separación de funciones, como DEC-79 y VAL-021).</li>
     *   <li>Temporada {@code CERRADA}: {@code 409 RF-003}; registro no {@code PENDIENTE_VALIDACION}:
     *       {@code 409 RF-017}.</li>
     *   <li>Documento reemplazado o que ya no está {@code PENDIENTE}, también si otra petición lo
     *       resolvió entre tanto: {@code 409 RF-109}.</li>
     * </ol>
     */
    @Transactional
    public DocumentoDto validar(UUID documentoId, ValidarDocumentoRequest peticion) {
        autorizacion.alcance(VALIDAR);
        DecisionDocumento decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si el documento se aprueba o se rechaza.");
        }
        String observaciones = textoOpcional(peticion.observaciones());
        if (decision == DecisionDocumento.RECHAZADO && (observaciones == null
                || observaciones.length() < MINIMO_OBSERVACIONES)) {
            throw ReglaNegocioException.invalida("RF-109", "Escribe observaciones de al menos "
                    + MINIMO_OBSERVACIONES + " caracteres para rechazar el documento: la Directiva las verá "
                    + "para corregirlo.");
        }

        DocumentoRegistro documento = buscarDocumento(documentoId);
        RegistroDeDocumentos registro = registroParaEscribir(documento.dto().registroId(), VALIDAR);
        UUID perfil = UsuarioActual.idRequerido();
        // [PROPUESTA] DEC-91: separación de funciones, como DEC-79 y VAL-021.
        if (perfil.equals(documento.cargadoPor())) {
            throw ReglaNegocioException.prohibida("VAL-022",
                    "No puedes validar un documento que cargaste tú. Pide a otra persona de la liga que lo revise.");
        }
        validarRegistroEditable(registro);
        if (documento.reemplazadoPorId() != null) {
            throw ReglaNegocioException.conflicto("RF-109",
                    "Este documento ya fue reemplazado; valida el documento vigente.");
        }
        if (documento.dto().estadoValidacion() != EstadoValidacion.PENDIENTE) {
            throw ReglaNegocioException.conflicto("RF-109", "Este documento ya está "
                    + texto(documento.dto().estadoValidacion()) + "; solo se valida un documento pendiente.");
        }

        if (!documentos.resolver(documentoId, decision.estado(), perfil, OffsetDateTime.now(reloj), observaciones)) {
            throw ReglaNegocioException.conflicto("RF-109",
                    "Otra persona validó o reemplazó este documento al mismo tiempo. Actualiza la página.");
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("registroId", registro.id().toString());
        detalle.put("tipoDocumento", documento.dto().tipoDocumento().codigo());
        detalle.put("decision", decision.name());
        auditar("VALIDAR_DOCUMENTO", documentoId, observaciones, detalle);
        return buscarInsertado(documentoId);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Registro de la liga (404 si no existe o no se ve), autorizado sobre su club y después bloqueado
     * hasta el fin de la transacción (DEC-93); devuelve el estado leído con el bloqueo.
     */
    private RegistroDeDocumentos registroParaEscribir(UUID registroId, String permiso) {
        RegistroDeDocumentos registro = documentos.buscarRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
        autorizacion.exigir(permiso, ObjetivoAutorizacion.deClubes(registro.clubId()));
        return documentos.bloquearRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
    }

    private DocumentoRegistro buscarDocumento(UUID documentoId) {
        return documentos.buscarDocumento(ligaId, documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento " + documentoId));
    }

    private DocumentoDto buscarInsertado(UUID documentoId) {
        return documentos.buscarDocumento(ligaId, documentoId)
                .map(DocumentoRegistro::dto)
                .orElseThrow(() -> new IllegalStateException("El documento " + documentoId + " no es visible tras guardarlo"));
    }

    /** El club del registro y la persona: así llegan la Directiva y el jugador o su tutor (DEC-91). */
    private static ObjetivoAutorizacion objetivoDeConsulta(RegistroDeDocumentos registro) {
        return ObjetivoAutorizacion.deClubesYPersonas(List.of(registro.clubId()), List.of(registro.personaId()));
    }

    /** RF-003 (como DEC-86) y RF-017: solo un registro pendiente de una temporada abierta (DEC-93). */
    private static void validarRegistroEditable(RegistroDeDocumentos registro) {
        if (registro.temporadaEstado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada del registro está cerrada: sus documentos ya no cambian.");
        }
        if (registro.estado() != EstadoRegistro.PENDIENTE_VALIDACION) {
            throw ReglaNegocioException.conflicto("RF-017", "El registro está " + texto(registro.estado())
                    + ": sus documentos solo se cargan y validan mientras está pendiente de validación.");
        }
    }

    /** Menor en la fecha de alta del registro (DEC-89, DEC-92). */
    private boolean esMenor(RegistroDeDocumentos registro) {
        return documentos.esMenor(registro.personaId(), registro.altaEn())
                .orElseThrow(() -> new IllegalStateException("La persona del registro " + registro.id() + " no existe"));
    }

    private static String noExigido(TipoDocumentoDto tipo, RegistroDeDocumentos registro, boolean esMenor) {
        if ("INE_TUTOR".equals(tipo.codigo()) && !esMenor) {
            return "El jugador no era menor de edad en la fecha de alta, así que su registro no lleva «"
                    + tipo.nombre() + "».";
        }
        return "El registro no exige «" + tipo.nombre() + "»: no está entre los documentos de su categoría ("
                + registro.categoriaNombre() + "). Revisa la lista de requisitos.";
    }

    private static String cantidad(int cantidad) {
        return cantidad == 1 ? "el documento" : "los " + cantidad + " documentos";
    }

    private static Map<String, Object> detalleArchivo(UUID registroId, String tipoDocumento, ArchivoAlmacenado archivo) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("registroId", registroId.toString());
        detalle.put("tipoDocumento", tipoDocumento);
        detalle.put("tipoMime", archivo.tipoMime());
        detalle.put("tamanoBytes", archivo.tamanoBytes());
        return detalle;
    }

    private void auditar(String accion, UUID documentoId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, documentoId.toString(), motivo,
                detalle));
    }

    private static String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String valor = texto.strip();
        if (valor.length() > MAXIMO_OBSERVACIONES) {
            throw invalida("Las observaciones admiten hasta " + MAXIMO_OBSERVACIONES + " caracteres.");
        }
        return valor;
    }

    private static String texto(Enum<?> estado) {
        return estado.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
