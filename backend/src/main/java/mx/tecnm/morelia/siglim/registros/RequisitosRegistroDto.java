package mx.tecnm.morelia.siglim.registros;

import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;

/**
 * Documentos que exige un registro según el tipo de su categoría y si la persona es menor, con los
 * documentos vigentes de cada uno (RF-108, DEC-92, DEC-96). Lo calcula {@link RequisitosDocumentos}.
 *
 * <p>Refs: H-10.1, RF-108, RN-055, VAL-001, DEC-92, DEC-96
 *
 * @param esMenor          si la persona era menor en la fecha de alta del registro (DEC-89)
 * @param completo         todos los requisitos tienen sus documentos cargados (vigentes no rechazados)
 * @param listoParaValidar todos los requisitos tienen sus documentos aprobados
 */
public record RequisitosRegistroDto(
        UUID registroId,
        EstadoRegistro estadoRegistro,
        CategoriaDto categoria,
        boolean esMenor,
        List<RequisitoDto> requisitos,
        boolean completo,
        boolean listoParaValidar) {

    /** Categoría del equipo del registro con el tipo que decide los requisitos. */
    public record CategoriaDto(UUID id, String nombre, TipoCategoria tipo) {
    }

    /**
     * Un requisito con su avance.
     *
     * @param esDemo     valor de demostración: la interfaz lo rotula «no oficial» (DEC-92)
     * @param cargados   documentos vigentes no rechazados
     * @param aprobados  documentos vigentes aprobados
     * @param faltan     {@code max(0, cantidad - cargados)}
     * @param documentos documentos vigentes de cualquier estado, por fecha de carga, para que la
     *                   Directiva vea las observaciones de un rechazo
     */
    public record RequisitoDto(
            TipoDocumentoDto tipoDocumento,
            int cantidad,
            boolean soloMenores,
            boolean esDemo,
            int cargados,
            int aprobados,
            int faltan,
            List<DocumentoDto> documentos) {
    }
}
