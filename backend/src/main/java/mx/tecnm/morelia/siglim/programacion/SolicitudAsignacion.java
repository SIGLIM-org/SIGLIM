package mx.tecnm.morelia.siglim.programacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Todo lo que necesita {@link MotorAsignacion} para asignar cancha y horario a los partidos de una
 * fecha. Quien llama (H-33.2) lo lee de la base de datos; el motor no consulta nada más.
 *
 * <p>Refs: H-33.1, RF-035, DEC-176, DEC-177, DEC-179, DEC-181, ADR-0015
 *
 * @param dias                      días de la fecha ({@code fechas_programacion.dias}), al menos uno
 * @param horaInicioJuego           inicio del horario de juego por defecto ({@code hora_inicio_juego},
 *                                  08:00 [DEMO])
 * @param horaFinJuego              fin del horario de juego por defecto ({@code hora_fin_juego}, 18:00
 *                                  [DEMO]), posterior al inicio
 * @param zona                      zona de negocio en la que se leen los días y las horas
 * @param fechaFinalUnidadDeportiva {@code true} si la fecha es de las últimas
 *                                  {@code fechas_finales_unidad_deportiva} (DEC-179); la calcula quien
 *                                  llama
 * @param canchas                   canchas de la liga, activas o no, con sus periodos
 * @param partidos                  emparejamientos de la fecha por colocar (sin descansos)
 * @param ocupaciones               partidos que ya tienen cancha y horario; el motor no los mueve
 */
record SolicitudAsignacion(
        List<LocalDate> dias,
        LocalTime horaInicioJuego,
        LocalTime horaFinJuego,
        ZoneId zona,
        boolean fechaFinalUnidadDeportiva,
        List<CanchaAsignable> canchas,
        List<PartidoPorColocar> partidos,
        List<Ocupacion> ocupaciones) {

    SolicitudAsignacion {
        Objects.requireNonNull(dias, "Los días de la fecha son obligatorios.");
        if (dias.isEmpty()) {
            throw new IllegalArgumentException("La fecha necesita al menos un día.");
        }
        Objects.requireNonNull(horaInicioJuego, "La hora de inicio de juego es obligatoria.");
        Objects.requireNonNull(horaFinJuego, "La hora de fin de juego es obligatoria.");
        if (!horaFinJuego.isAfter(horaInicioJuego)) {
            throw new IllegalArgumentException("La hora de fin de juego debe ser posterior a la de inicio.");
        }
        Objects.requireNonNull(zona, "La zona de negocio es obligatoria.");
        dias = List.copyOf(dias);
        canchas = List.copyOf(canchas);
        partidos = List.copyOf(partidos);
        ocupaciones = List.copyOf(ocupaciones);
        if (partidos.stream().map(PartidoPorColocar::emparejamientoId).distinct().count() != partidos.size()) {
            throw new IllegalArgumentException("Un emparejamiento no puede venir dos veces.");
        }
    }

    /** Tipo de un periodo de {@code disponibilidad_canchas} ({@code tipo_disponibilidad}). */
    enum TipoPeriodo {
        DISPONIBLE, BLOQUEO, MANTENIMIENTO
    }

    /**
     * Periodo de disponibilidad, bloqueo o mantenimiento de una cancha, en {@code [desde, hasta)}.
     */
    record PeriodoCancha(TipoPeriodo tipo, OffsetDateTime desde, OffsetDateTime hasta) {

        PeriodoCancha {
            Objects.requireNonNull(tipo, "El tipo del periodo es obligatorio.");
            Objects.requireNonNull(desde, "El inicio del periodo es obligatorio.");
            Objects.requireNonNull(hasta, "El fin del periodo es obligatorio.");
            if (!hasta.isAfter(desde)) {
                throw new IllegalArgumentException("El fin del periodo debe ser posterior a su inicio.");
            }
        }
    }

    /**
     * Cancha de la liga.
     *
     * @param unidadDeportiva {@code true} si está en la unidad deportiva ({@code ubicacion})
     * @param periodos        periodos de {@code disponibilidad_canchas} que tocan los días de la fecha
     */
    record CanchaAsignable(UUID id, String nombre, boolean activa, boolean unidadDeportiva,
            List<PeriodoCancha> periodos) {

        CanchaAsignable {
            Objects.requireNonNull(id, "La cancha necesita id.");
            Objects.requireNonNull(nombre, "La cancha necesita nombre.");
            periodos = List.copyOf(periodos);
        }
    }

    /**
     * Categoría de la temporada del partido.
     *
     * @param orden          {@code categorias.orden}: la menor toma los horarios más tempranos (DEC-178)
     * @param diasJuego      {@code categorias_temporada.dias_juego} (DEC-177)
     * @param bloqueMinutos  bloque de cancha de la categoría ({@code siglim.bloque_cancha}, DEC-25)
     */
    record CategoriaAsignable(UUID id, String nombre, int orden, Set<DayOfWeek> diasJuego, int bloqueMinutos) {

        CategoriaAsignable {
            Objects.requireNonNull(id, "La categoría necesita id.");
            Objects.requireNonNull(nombre, "La categoría necesita nombre.");
            diasJuego = Set.copyOf(diasJuego);
            if (bloqueMinutos <= 0) {
                throw new IllegalArgumentException("El bloque de cancha debe ser mayor que cero.");
            }
        }
    }

    /**
     * Equipo de un partido.
     *
     * @param causaNoElegible por qué no puede jugar (baja o sanción vigente, VAL-006); {@code null} si
     *                        es elegible
     */
    record EquipoAsignable(UUID id, String nombre, String causaNoElegible) {

        EquipoAsignable {
            Objects.requireNonNull(id, "El equipo necesita id.");
            Objects.requireNonNull(nombre, "El equipo necesita nombre.");
        }

        boolean elegible() {
            return causaNoElegible == null;
        }
    }

    /**
     * Emparejamiento de la fecha sin partido.
     *
     * @param categoria       categoría del equipo local: de ella sale el bloque, como en el trigger
     * @param canchasDelLocal canchas del club del local para esta categoría (propias, ofrecidas o en
     *                        comodato), las mismas de {@code LocaliaRepository.canchasDelClub}; vacía
     *                        si el club no tiene cancha
     */
    record PartidoPorColocar(UUID emparejamientoId, UUID grupoId, CategoriaAsignable categoria,
            EquipoAsignable local, EquipoAsignable visitante, Set<UUID> canchasDelLocal) {

        PartidoPorColocar {
            Objects.requireNonNull(emparejamientoId, "El partido necesita emparejamiento.");
            Objects.requireNonNull(grupoId, "El partido necesita grupo.");
            Objects.requireNonNull(categoria, "El partido necesita categoría.");
            Objects.requireNonNull(local, "El partido necesita equipo local.");
            Objects.requireNonNull(visitante, "El partido necesita equipo visitante.");
            if (local.id().equals(visitante.id())) {
                throw new IllegalArgumentException("Un equipo no puede jugar contra sí mismo.");
            }
            canchasDelLocal = Set.copyOf(canchasDelLocal);
        }
    }

    /**
     * Partido que ya tiene cancha y horario en la fecha (asignado antes o ajustado a mano), o de los
     * mismos equipos en otra fecha que se traslape. El motor lo respeta y nunca lo mueve (DEC-181).
     */
    record Ocupacion(UUID canchaId, UUID equipoLocalId, UUID equipoVisitanteId, OffsetDateTime inicio,
            OffsetDateTime fin) {

        Ocupacion {
            Objects.requireNonNull(canchaId, "La ocupación necesita cancha.");
            Objects.requireNonNull(equipoLocalId, "La ocupación necesita equipo local.");
            Objects.requireNonNull(equipoVisitanteId, "La ocupación necesita equipo visitante.");
            Objects.requireNonNull(inicio, "La ocupación necesita inicio.");
            Objects.requireNonNull(fin, "La ocupación necesita fin.");
            if (!fin.isAfter(inicio)) {
                throw new IllegalArgumentException("El fin de la ocupación debe ser posterior a su inicio.");
            }
        }
    }
}
