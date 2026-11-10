package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import java.util.Objects;
import java.util.UUID;

/**
 * Ubicación de un documento en S3: {@code liga/{ligaId}/{carpeta}/{entidadId}/{documentoId}.{ext}}
 * (ADR-0013 punto 3). Solo lleva identificadores: nunca nombres ni CURP.
 *
 * @param ligaId      liga a la que pertenece el documento
 * @param carpeta     tipo de documento
 * @param entidadId   registro, persona, afiliación, expediente o pago dueño del documento
 * @param documentoId identificador que tendrá la fila de {@code siglim.documentos}
 */
public record ClaveDocumento(UUID ligaId, CarpetaDocumento carpeta, UUID entidadId, UUID documentoId) {

    public ClaveDocumento {
        Objects.requireNonNull(ligaId, "ligaId es obligatorio");
        Objects.requireNonNull(carpeta, "carpeta es obligatoria");
        Objects.requireNonNull(entidadId, "entidadId es obligatorio");
        Objects.requireNonNull(documentoId, "documentoId es obligatorio");
    }

    /** La clave con la extensión del tipo detectado por su firma binaria. */
    public String valor(TipoArchivo tipo) {
        return "liga/" + ligaId + "/" + carpeta.segmento() + "/" + entidadId + "/" + documentoId + "."
                + tipo.extension();
    }
}
