package mx.tecnm.morelia.siglim.registros;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.DocumentoPropioDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.DocumentosPropiosDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.PersonaPropiaDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.RegistroPropioDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionDto.RequisitoPropioDto;
import mx.tecnm.morelia.siglim.registros.MiInformacionRepository.PersonaTitular;
import mx.tecnm.morelia.siglim.registros.MiInformacionRepository.RegistroDePersona;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.Avance;
import mx.tecnm.morelia.siglim.registros.RequisitosRegistroDto.RequisitoDto;
import mx.tecnm.morelia.siglim.registros.TitularesDto.TitularDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta propia del jugador o de su tutor (M-04.6, RF-144, UI-032, pantalla F-35): a quién puede
 * consultar la cuenta y, de cada persona, sus registros con documentos, elegibilidad y partidos, y sus
 * sanciones.
 *
 * <ul>
 *   <li><b>Autorización (DEC-140, ADR-0007).</b> Primero {@code registros.registro:CONSULTAR} en
 *       cualquier ámbito ({@link Autorizacion#alcance}); sin él, {@code 403 VAL-015}. El detalle exige
 *       además que la persona sea la de la cuenta o un menor bajo su tutela vigente
 *       ({@code siglim.es_titular}); si no, o si no existe, {@code 404} igual que un recurso
 *       inexistente (VAL-024). Vale también para cuentas con ámbito {@code LIGA} o {@code CLUB}: es
 *       «mi» información, no una consulta administrativa; esas usan {@code /registros-jugador}.</li>
 *   <li><b>Datos personales (RNF-012, RNF-018).</b> La CURP va enmascarada ({@link EnmascaradoCurp}) y
 *       los documentos sin URL, bucket, clave, SHA-256 ni nombre de archivo.</li>
 *   <li><b>Documentos (DEC-141).</b> Los mismos requisitos y avance que F-19, calculados con
 *       {@link RequisitosDocumentos} y la edad en la fecha de alta del registro (DEC-89).</li>
 *   <li><b>Elegibilidad (DEC-142).</b> {@link ReglaElegibilidad} con el estado del registro y las
 *       sanciones de la persona.</li>
 *   <li><b>Dependencias (DEC-143, ADR-0006).</b> Partidos y sanciones llegan por
 *       {@link ConsultaPartidosPropios} y {@link ConsultaSancionesPropias}, que implementan
 *       {@code partidos} y {@code disciplina} dentro de esta misma transacción.</li>
 *   <li><b>Sin auditoría:</b> es una consulta y no cambia nada.</li>
 * </ul>
 *
 * <p>Refs: H-24.1, RF-019, RF-020, RF-108, RF-144, RNF-012, RNF-018, UI-032, VAL-015, VAL-024, ADR-0006,
 * ADR-0007, DEC-139 a DEC-145
 */
@Service
public class MiInformacionService {

    private static final String CONSULTAR = "registros.registro:CONSULTAR";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    /** [PROPUESTA] DEC-144: cuántos próximos partidos se muestran por registro. */
    static final int LIMITE_PROXIMOS = 5;
    /** [PROPUESTA] DEC-144: cuántos partidos jugados se muestran por registro. */
    static final int LIMITE_JUGADOS = 10;

    private final MiInformacionRepository repositorio;
    private final DocumentoRegistroRepository documentos;
    private final ConsultaPartidosPropios partidos;
    private final ConsultaSancionesPropias sanciones;
    private final Autorizacion autorizacion;
    private final Clock reloj;
    private final UUID ligaId;

    MiInformacionService(MiInformacionRepository repositorio, DocumentoRegistroRepository documentos,
            ConsultaPartidosPropios partidos, ConsultaSancionesPropias sanciones, Autorizacion autorizacion,
            Clock reloj, SiglimPropiedades propiedades) {
        this.repositorio = repositorio;
        this.documentos = documentos;
        this.partidos = partidos;
        this.sanciones = sanciones;
        this.autorizacion = autorizacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Personas que la cuenta puede consultar: primero la suya (si la cuenta tiene persona vinculada) y
     * después los menores bajo su tutela vigente, por nombre (DEC-139). Sin persona vinculada, la lista
     * va vacía.
     *
     * <p>Errores: sin {@code registros.registro:CONSULTAR} en ningún ámbito, {@code 403 VAL-015}.
     */
    @Transactional(readOnly = true)
    public TitularesDto titulares() {
        autorizacion.alcance(CONSULTAR);
        Optional<UUID> propia = repositorio.personaDelUsuario();
        if (propia.isEmpty()) {
            return new TitularesDto(List.of());
        }
        LocalDate hoy = hoy();
        List<TitularDto> titulares = new ArrayList<>();
        repositorio.persona(propia.get(), hoy).ifPresent(persona ->
                titulares.add(new TitularDto(persona.id(), persona.nombreCompleto(), true, persona.esMenor())));
        titulares.addAll(repositorio.menoresTutelados(propia.get(), hoy));
        return new TitularesDto(titulares);
    }

    /**
     * Información de una persona para ella misma o para su tutor (DEC-141).
     *
     * <p>Orden de errores (DEC-140):
     * <ol>
     *   <li>Sin {@code registros.registro:CONSULTAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Persona que no es la de la cuenta ni un menor bajo su tutela vigente, o inexistente:
     *       {@code 404} (VAL-024), con cualquier ámbito.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public MiInformacionDto consultar(UUID personaId) {
        autorizacion.alcance(CONSULTAR);
        if (personaId == null || !repositorio.esTitular(personaId)) {
            throw new RecursoNoEncontradoException("Persona " + personaId);
        }
        PersonaTitular persona = repositorio.persona(personaId, hoy())
                .orElseThrow(() -> new RecursoNoEncontradoException("Persona " + personaId));

        List<SancionPropia> deLaPersona = sanciones.dePersona(personaId);
        List<RegistroPropioDto> registros = repositorio.registros(ligaId, personaId).stream()
                .map(registro -> armar(persona.id(), registro, deLaPersona))
                .toList();
        return new MiInformacionDto(
                new PersonaPropiaDto(persona.id(), persona.nombreCompleto(), EnmascaradoCurp.enmascarar(persona.curp()),
                        persona.esMenor()),
                registros, deLaPersona);
    }

    private RegistroPropioDto armar(UUID personaId, RegistroDePersona registro, List<SancionPropia> deLaPersona) {
        return new RegistroPropioDto(registro.id(), registro.temporada(), registro.club(), registro.equipo(),
                registro.categoria(), registro.estado(), registro.altaEn(), registro.bajaEn(), registro.motivoBaja(),
                registro.observaciones(),
                ReglaElegibilidad.evaluar(registro.estado(), deLaPersona),
                documentosDe(personaId, registro),
                partidos.proximos(registro.equipo().id(), LIMITE_PROXIMOS),
                partidos.jugados(registro.equipo().id(), LIMITE_JUGADOS));
    }

    /** Igual que los requisitos de F-19: menor en la fecha de alta (DEC-89) y documentos vigentes. */
    private DocumentosPropiosDto documentosDe(UUID personaId, RegistroDePersona registro) {
        boolean esMenor = documentos.esMenor(personaId, registro.altaEn())
                .orElseThrow(() -> new IllegalStateException("La persona del registro " + registro.id() + " no existe"));
        Avance avance = RequisitosDocumentos.calcular(documentos.requisitos(ligaId, registro.categoriaTipo()), esMenor,
                documentos.vigentes(registro.id()));
        return new DocumentosPropiosDto(avance.requisitos().stream().map(MiInformacionService::requisito).toList(),
                avance.completo(), avance.listoParaValidar());
    }

    /** Copia el requisito sin los datos del archivo de cada documento (RNF-018). */
    private static RequisitoPropioDto requisito(RequisitoDto requisito) {
        List<DocumentoPropioDto> documentos = requisito.documentos().stream()
                .map(documento -> new DocumentoPropioDto(documento.id(), documento.tipoDocumento(),
                        documento.estadoValidacion(), documento.observaciones(), documento.cargadoEn()))
                .toList();
        return new RequisitoPropioDto(requisito.tipoDocumento(), requisito.cantidad(), requisito.soloMenores(),
                requisito.esDemo(), requisito.cargados(), requisito.aprobados(), requisito.faltan(), documentos);
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
    }
}
