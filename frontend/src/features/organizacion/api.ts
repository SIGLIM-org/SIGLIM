import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ActualizarEquipoPeticion,
  BajaEquipoPeticion,
  CambiarNombreClubPeticion,
  CargoDirectiva,
  Club,
  CrearClubPeticion,
  CrearEquipoPeticion,
  Equipo,
  FiltrosClubes,
  FiltrosEquipos,
  Persona,
  RegistrarCargoDirectivaPeticion,
} from '@/features/organizacion/tipos'
import { clavesUsuarios } from '@/features/usuarios/api'
import { ApiError } from '@/lib/api/ApiError'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

/** Equipos que pide la pestaña de un club; si hay más, se avisa en pantalla. */
export const TAMANO_EQUIPOS = 100

export const clavesOrganizacion = {
  clubes: ['organizacion', 'clubes'] as const,
  listaClubes: (filtros: FiltrosClubes) => ['organizacion', 'clubes', 'lista', filtros] as const,
  club: (id: string) => ['organizacion', 'clubes', 'detalle', id] as const,
  directiva: (clubId: string) => ['organizacion', 'directiva', clubId] as const,
  equipos: ['organizacion', 'equipos'] as const,
  listaEquipos: (filtros: FiltrosEquipos) => ['organizacion', 'equipos', 'lista', filtros] as const,
}

function consultaDeClubes({ q, estado, pagina, tamano }: FiltrosClubes): string {
  const parametros = new URLSearchParams()

  if (q) {
    parametros.set('q', q)
  }
  if (estado) {
    parametros.set('estado', estado)
  }
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return parametros.toString()
}

function consultaDeEquipos({ clubId, estado }: FiltrosEquipos): string {
  const parametros = new URLSearchParams({ clubId })

  if (estado) {
    parametros.set('estado', estado)
  }
  parametros.set('pagina', '0')
  parametros.set('tamano', String(TAMANO_EQUIPOS))

  return parametros.toString()
}

/** Clubes por nombre, filtrados por el ámbito de la sesión en el backend (RF-008, ADR-0007). */
export function useClubes(filtros: FiltrosClubes) {
  return useQuery({
    queryKey: clavesOrganizacion.listaClubes(filtros),
    queryFn: () => api.get<Pagina<Club>>(`clubes?${consultaDeClubes(filtros)}`),
    placeholderData: keepPreviousData,
  })
}

/** Un club fuera de ámbito responde 404 igual que uno inexistente (VAL-024). */
export function useClub(id: string) {
  return useQuery({
    queryKey: clavesOrganizacion.club(id),
    queryFn: () => api.get<Club>(`clubes/${id}`),
  })
}

/**
 * El club nuevo también debe aparecer en el selector de clubes del modal de invitar de F-12,
 * que guarda su catálogo con `clavesUsuarios.clubes`.
 */
export function useCrearClub() {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CrearClubPeticion) => api.post<Club>('clubes', peticion),
    onSuccess: () =>
      Promise.all([
        clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.clubes }),
        clienteConsultas.invalidateQueries({ queryKey: clavesUsuarios.clubes }),
      ]),
  })
}

/**
 * Cambio de nombre (RF-105, DEC-15, DEC-49): el backend registra un club nuevo y cierra el actual.
 * Se invalidan:
 * - `clavesOrganizacion.clubes`: el prefijo cubre las listas (aparece el nuevo, el anterior pasa a
 *   CERRADO) y el detalle del club anterior.
 * - `clavesUsuarios.todas`: las asignaciones de ámbito CLUB pasan al club nuevo (F-12).
 * - `clavesUsuarios.clubes`: el selector de clubes del modal de invitar debe ofrecer el nuevo.
 */
export function useCambiarNombreClub(id: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: CambiarNombreClubPeticion) => api.post<Club>(`clubes/${id}/cambio-nombre`, peticion),
    onSuccess: () =>
      Promise.all([
        clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.clubes }),
        clienteConsultas.invalidateQueries({ queryKey: clavesUsuarios.todas }),
        clienteConsultas.invalidateQueries({ queryKey: clavesUsuarios.clubes }),
      ]),
  })
}

/** Historial de la directiva, con los cambios pendientes incluidos (RF-102). */
export function useDirectiva(clubId: string) {
  return useQuery({
    queryKey: clavesOrganizacion.directiva(clubId),
    queryFn: () => api.get<CargoDirectiva[]>(`clubes/${clubId}/directiva`),
  })
}

/** El cambio nace pendiente; se recarga la lista para que aparezca de inmediato (RF-102). */
export function useRegistrarCargoDirectiva(clubId: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (peticion: RegistrarCargoDirectivaPeticion) =>
      api.post<CargoDirectiva>(`clubes/${clubId}/directiva`, peticion),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.directiva(clubId) }),
  })
}

/** Al autorizar, el backend termina el cargo vigente anterior; se recarga la lista (RF-102). */
export function useAutorizarCargoDirectiva(clubId: string) {
  const clienteConsultas = useQueryClient()

  return useMutation({
    mutationFn: (cargoId: string) => api.post<CargoDirectiva>(`cargos-directiva/${cargoId}/autorizacion`),
    onSuccess: () => clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.directiva(clubId) }),
    // Un VAL-003 significa que el cambio ya no está pendiente (otra sesión lo autorizó o la
    // vigencia se traslapa): refrescar la lista evita seguir mostrando un estado viejo.
    onError: (error) => {
      if (error instanceof ApiError && error.codigo === 'VAL-003') {
        void clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.directiva(clubId) })
      }
    },
  })
}

/** Busca una persona por CURP para confirmar su identidad antes de registrar un cargo (RF-010). */
export function useBuscarPersona() {
  return useMutation({
    mutationFn: (curp: string) => api.get<Persona>(`personas?curp=${encodeURIComponent(curp)}`),
  })
}

/** Equipos de un club, ordenados en el backend por el orden de la categoría, grupo y nombre (RF-009, DEC-16). */
export function useEquipos(filtros: FiltrosEquipos) {
  return useQuery({
    queryKey: clavesOrganizacion.listaEquipos(filtros),
    queryFn: () => api.get<Pagina<Equipo>>(`equipos?${consultaDeEquipos(filtros)}`),
    placeholderData: keepPreviousData,
  })
}

/**
 * Un cambio en los equipos cambia la pestaña, el club y el conteo de equipos de los grupos que
 * muestra la estructura de la temporada (F-11).
 */
function useInvalidarEquipos() {
  const clienteConsultas = useQueryClient()

  return (equipo: Equipo) =>
    Promise.all([
      clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.equipos }),
      clienteConsultas.invalidateQueries({ queryKey: clavesOrganizacion.club(equipo.club.id) }),
      clienteConsultas.invalidateQueries({ queryKey: ['temporadas', 'categorias'] }),
    ])
}

export function useCrearEquipo() {
  const invalidar = useInvalidarEquipos()

  return useMutation({
    mutationFn: (peticion: CrearEquipoPeticion) => api.post<Equipo>('equipos', peticion),
    onSuccess: invalidar,
  })
}

export function useActualizarEquipo(id: string) {
  const invalidar = useInvalidarEquipos()

  return useMutation({
    mutationFn: (peticion: ActualizarEquipoPeticion) => api.patch<Equipo>(`equipos/${id}`, peticion),
    onSuccess: invalidar,
  })
}

/** La baja conserva el equipo y su historial; solo cambia su estado (RF-009, RN-005). */
export function useDarDeBajaEquipo(id: string) {
  const invalidar = useInvalidarEquipos()

  return useMutation({
    mutationFn: (peticion: BajaEquipoPeticion) => api.post<Equipo>(`equipos/${id}/baja`, peticion),
    onSuccess: invalidar,
  })
}
