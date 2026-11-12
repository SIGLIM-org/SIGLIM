package mx.tecnm.morelia.siglim.registros;

import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;

/**
 * Documento de un registro de jugador (RF-108, RF-109). Nunca lleva {@code s3Bucket},
 * {@code s3Clave}, la {@code url} guardada ni el {@code sha256}: el archivo se lee con
 * {@code GET /documentos/{id}/enlace} (RNF-018, ADR-0013).
 *
 * <p>Refs: H-10.1, RF-108, RF-109, RNF-018, DEC-95
 *
 * @param tipoMime    MIME detectado por la firma binaria, no el que declaró el cliente
 * @param validadoPor perfil que validó, con {@code perfiles.nombre_mostrado} (el nombre es
 *                    {@code null} si RLS no muestra ese perfil); {@code null} si sigue pendiente
 * @param reemplazaA  documento al que sustituye, si lo hay
 * @param cargadoEn   fecha y hora de carga en la zona de negocio
 */
public record DocumentoDto(
        UUID id,
        UUID registroId,
        TipoDocumentoDto tipoDocumento,
        String tipoMime,
        int tamanoBytes,
        EstadoValidacion estadoValidacion,
        String observaciones,
        ReferenciaDto validadoPor,
        OffsetDateTime validadoEn,
        UUID reemplazaA,
        OffsetDateTime cargadoEn) {

    /** Tipo de documento del catálogo {@code tipos_documento}. */
    public record TipoDocumentoDto(long id, String codigo, String nombre) {
    }
}
