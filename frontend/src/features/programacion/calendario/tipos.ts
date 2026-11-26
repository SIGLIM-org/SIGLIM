/**
 * DTO del calendario de la temporada: los mismos nombres del contrato (convenciones de API §6.7,
 * «Programación automática (H-32.2)»).
 */

import type { Referencia } from '@/features/temporadas/tipos'

/**
 * Cuerpo opcional de `POST /temporadas/{id}/calendario` (DEC-189, DEC-193). Lo ausente toma su
 * valor por defecto: el inicio de la temporada y todos los grupos.
 */
export type GenerarCalendarioPeticion = {
  /** `AAAA-MM-DD`; la fecha 1 es el primer sábado en o después de ella (DEC-186). */
  fechaInicio?: string
  grupoIds?: string[]
  /** Obligatorio, de al menos 10 caracteres, si algún grupo elegido ya tiene calendario (RN-021). */
  motivo?: string
}

/** Grupo generado en esta petición; `reemplazado` si ya tenía calendario (DEC-189). */
export type GrupoGenerado = {
  grupoId: string
  nombre: string
  equipos: number
  fechas: number
  reemplazado: boolean
}

/** Grupo con menos de 2 equipos `ACTIVO`: no se genera (DEC-187). */
export type GrupoOmitido = {
  grupoId: string
  nombre: string
  equiposActivos: number
  causa: string
}

/** Aviso que no impide generar (DEC-193). */
export type AdvertenciaCalendario = {
  tipo: 'FECHAS_DESPUES_DEL_FIN' | 'JORNADAS_MANUALES' | (string & {})
  /** Se omite si el aviso es de toda la temporada. */
  grupoId?: string
  mensaje: string
}

/** `201` de `POST /temporadas/{id}/calendario` (H-32.2). */
export type CalendarioGenerado = {
  versionParametros: number
  vueltas: number
  /** La última fecha del calendario vigente de la temporada. */
  totalFechas: number
  /** Fechas de programación creadas en esta generación. */
  fechasCreadas: number
  grupos: GrupoGenerado[]
  gruposOmitidos: GrupoOmitido[]
  advertencias: AdvertenciaCalendario[]
}

/** Equipo con su estado (`estado_equipo`), para advertir uno dado de baja. */
export type EquipoCalendario = {
  id: string
  nombre: string
  estado: string
}

/** Enfrentamiento del calendario; `partidoId` solo cuando ya se le asignó cancha y horario. */
export type EmparejamientoCalendario = {
  emparejamientoId: string
  local: EquipoCalendario
  visitante: EquipoCalendario
  partidoId?: string
}

/** Una fecha del calendario del grupo; `descansa` se omite si el grupo es par (RF-037). */
export type FechaCalendario = {
  numero: number
  vuelta: number
  partidos: EmparejamientoCalendario[]
  descansa?: EquipoCalendario
}

/** Calendario de un grupo; sin generar, `fechas` viene vacía. */
export type GrupoCalendario = {
  grupoId: string
  nombre: string
  categoria: Referencia
  generado: boolean
  fechas: FechaCalendario[]
}

/** `GET /temporadas/{id}/calendario`: todos los grupos, por categoría y nombre. */
export type Calendario = {
  temporadaId: string
  grupos: GrupoCalendario[]
}
