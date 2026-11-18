package mx.tecnm.morelia.siglim.registros;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.ReglaElegibilidad.Elegibilidad;

/**
 * Información de una persona para ella misma o para su tutor: sus registros de temporadas no
 * cerradas con documentos, elegibilidad y partidos, y sus sanciones (H-24.1, RF-144, UI-032, DEC-141).
 *
 * <p>Nunca lleva la CURP completa (solo {@code curpEnmascarada}, RNF-012) ni, de los documentos, la URL,
 * el bucket, la clave, el SHA-256 o el nombre del archivo (RNF-018).
 *
 * <p>Refs: H-24.1, RF-019, RF-020, RF-108, RF-144, RNF-012, RNF-018, UI-032, DEC-141
 *
 * @param registros del más reciente al más antiguo
 * @param sanciones de sujeto {@code JUGADOR}: las {@code VIGENTE} más las de temporadas no cerradas
 */
public record MiInformacionDto(PersonaPropiaDto persona, List<RegistroPropioDto> registros,
                               List<SancionPropia> sanciones) {

    public MiInformacionDto {
        registros = List.copyOf(registros);
        sanciones = List.copyOf(sanciones);
    }

    /**
     * La persona consultada.
     *
     * @param curpEnmascarada con {@link EnmascaradoCurp}
     * @param esMenor         si es menor de edad hoy, en {@code America/Mexico_City}
     */
    public record PersonaPropiaDto(UUID id, String nombreCompleto, String curpEnmascarada, boolean esMenor) {
    }

    /**
     * Un registro de la persona.
     *
     * @param motivoBaja       del historial, solo en {@code BAJA} (DEC-124)
     * @param observaciones    las del rechazo o la validación del registro
     * @param proximosPartidos del equipo, en jornadas publicadas, hasta 5 (DEC-144)
     * @param partidosJugados  del equipo, con resultado oficial vigente, hasta 10 (DEC-144)
     */
    public record RegistroPropioDto(
            UUID id,
            TemporadaResumenDto temporada,
            ReferenciaDto club,
            ReferenciaDto equipo,
            ReferenciaDto categoria,
            EstadoRegistro estado,
            LocalDate altaEn,
            LocalDate bajaEn,
            String motivoBaja,
            String observaciones,
            Elegibilidad elegibilidad,
            DocumentosPropiosDto documentos,
            List<PartidoPropio> proximosPartidos,
            List<PartidoPropio> partidosJugados) {

        public RegistroPropioDto {
            proximosPartidos = List.copyOf(proximosPartidos);
            partidosJugados = List.copyOf(partidosJugados);
        }
    }

    /**
     * Avance de los documentos del registro, calculado con {@link RequisitosDocumentos} igual que F-19.
     *
     * @param completo         todos los requisitos tienen sus documentos cargados
     * @param listoParaValidar todos los requisitos tienen sus documentos aprobados
     */
    public record DocumentosPropiosDto(List<RequisitoPropioDto> requisitos, boolean completo,
                                       boolean listoParaValidar) {

        public DocumentosPropiosDto {
            requisitos = List.copyOf(requisitos);
        }
    }

    /**
     * Un requisito aplicable con su avance.
     *
     * @param esDemo valor de demostración: la interfaz lo rotula «no oficial» (DEC-92)
     * @param faltan {@code max(0, cantidad - cargados)}
     */
    public record RequisitoPropioDto(TipoDocumentoDto tipoDocumento, int cantidad, boolean soloMenores,
                                     boolean esDemo, int cargados, int aprobados, int faltan,
                                     List<DocumentoPropioDto> documentos) {

        public RequisitoPropioDto {
            documentos = List.copyOf(documentos);
        }
    }

    /** Un documento vigente, sin datos del archivo ni de su almacenamiento (RNF-018). */
    public record DocumentoPropioDto(UUID id, TipoDocumentoDto tipo, EstadoValidacion estadoValidacion,
                                     String observaciones, OffsetDateTime cargadoEn) {
    }
}
