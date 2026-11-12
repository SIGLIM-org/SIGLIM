package mx.tecnm.morelia.siglim.registros;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.RequisitosRegistroDto.RequisitoDto;

/**
 * Cálculo de los documentos exigidos a un registro y de su avance (RF-108, DEC-92, DEC-96). Es la
 * única fuente de esta regla: la usan los requisitos ({@code GET /registros-jugador/{id}/requisitos})
 * y la carga de documentos, y la reutilizará la validación del registro (H-10.2, VAL-001).
 *
 * <p>Sin Spring ni base de datos: recibe los requisitos de la liga para el tipo de la categoría, si
 * la persona es menor en la fecha de alta y los documentos del registro.
 *
 * <ul>
 *   <li><b>Aplicables.</b> Los de {@code solo_menores} solo cuentan si la persona es menor.</li>
 *   <li><b>Vigentes.</b> Solo cuentan los documentos sin {@code reemplazado_por_id}.</li>
 *   <li><b>Cargado.</b> Un requisito está cargado cuando sus vigentes no rechazados son
 *       {@code >= cantidad}; <b>aprobado</b>, cuando sus vigentes {@code APROBADO} lo son.</li>
 *   <li><b>Cupo.</b> Un tipo admite {@code cantidad} documentos vigentes de cualquier estado: un
 *       rechazado ocupa su lugar hasta que se reemplaza (DEC-92).</li>
 * </ul>
 *
 * <p>Refs: H-10.1, RF-108, RN-055, VAL-001, DEC-92, DEC-96
 */
final class RequisitosDocumentos {

    private RequisitosDocumentos() {
    }

    /** Requisitos que se exigen a esta persona, en el orden recibido. */
    static List<RequisitoDocumento> aplicables(List<RequisitoDocumento> requisitos, boolean esMenor) {
        return requisitos.stream().filter(requisito -> esMenor || !requisito.soloMenores()).toList();
    }

    /** El requisito aplicable de ese tipo, o {@code null} si el registro no lo exige. */
    static RequisitoDocumento exigido(List<RequisitoDocumento> requisitos, boolean esMenor, long tipoDocumentoId) {
        return aplicables(requisitos, esMenor).stream()
                .filter(requisito -> requisito.tipoDocumento().id() == tipoDocumentoId)
                .findFirst()
                .orElse(null);
    }

    /** Documentos vigentes de ese tipo, de cualquier estado: los que ocupan el cupo (DEC-92). */
    static long vigentesDelTipo(List<DocumentoRegistro> documentos, long tipoDocumentoId) {
        return vigentes(documentos).stream()
                .filter(documento -> documento.dto().tipoDocumento().id() == tipoDocumentoId)
                .count();
    }

    /** Avance de cada requisito aplicable y si el registro está completo y listo para validarse. */
    static Avance calcular(List<RequisitoDocumento> requisitos, boolean esMenor, List<DocumentoRegistro> documentos) {
        List<DocumentoRegistro> vigentes = vigentes(documentos);
        List<RequisitoDto> resultado = new ArrayList<>();
        boolean completo = true;
        boolean listo = true;
        for (RequisitoDocumento requisito : aplicables(requisitos, esMenor)) {
            List<DocumentoDto> delTipo = vigentes.stream()
                    .map(DocumentoRegistro::dto)
                    .filter(documento -> documento.tipoDocumento().id() == requisito.tipoDocumento().id())
                    .sorted(Comparator.comparing(DocumentoDto::cargadoEn, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(DocumentoDto::id))
                    .toList();
            int cargados = (int) delTipo.stream()
                    .filter(documento -> documento.estadoValidacion() != EstadoValidacion.RECHAZADO).count();
            int aprobados = (int) delTipo.stream()
                    .filter(documento -> documento.estadoValidacion() == EstadoValidacion.APROBADO).count();
            completo &= cargados >= requisito.cantidad();
            listo &= aprobados >= requisito.cantidad();
            resultado.add(new RequisitoDto(requisito.tipoDocumento(), requisito.cantidad(), requisito.soloMenores(),
                    requisito.esDemo(), cargados, aprobados, Math.max(0, requisito.cantidad() - cargados), delTipo));
        }
        return new Avance(List.copyOf(resultado), completo, listo);
    }

    private static List<DocumentoRegistro> vigentes(List<DocumentoRegistro> documentos) {
        return documentos.stream().filter(documento -> documento.reemplazadoPorId() == null).toList();
    }

    /** Fila de {@code requisitos_documento} con su tipo. */
    record RequisitoDocumento(TipoDocumentoDto tipoDocumento, int cantidad, boolean soloMenores, boolean esDemo) {

        RequisitoDocumento {
            Objects.requireNonNull(tipoDocumento, "tipoDocumento");
        }
    }

    /**
     * Documento de un registro con los datos internos que no van en la respuesta: quién lo cargó
     * (VAL-022), a quién lo reemplazó y dónde está en S3 (solo para firmar el enlace).
     */
    record DocumentoRegistro(DocumentoDto dto, UUID cargadoPor, UUID reemplazadoPorId, String s3Bucket,
                             String s3Clave) {

        DocumentoRegistro {
            Objects.requireNonNull(dto, "dto");
        }
    }

    /** Resultado del cálculo: requisitos con su avance, {@code completo} y {@code listoParaValidar}. */
    record Avance(List<RequisitoDto> requisitos, boolean completo, boolean listoParaValidar) {
    }
}
