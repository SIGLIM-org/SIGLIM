import { keepPreviousData, useQuery } from '@tanstack/react-query'
import type {
  OpcionesFiltroAuditoria,
  ParametrosAuditoria,
  RegistroAuditoria,
} from '@/features/auditoria/tipos'
import { api } from '@/lib/api/cliente'
import type { Pagina } from '@/lib/api/tipos'

export const clavesAuditoria = {
  registros: (parametros: ParametrosAuditoria) => ['auditoria', 'registros', parametros] as const,
  opcionesFiltro: ['auditoria', 'opciones-filtro'] as const,
}

function consultaDeParametros({
  perfilId,
  modulo,
  accion,
  desde,
  hasta,
  pagina,
  tamano,
  orden,
}: ParametrosAuditoria): string {
  const parametros = new URLSearchParams()

  if (perfilId) {
    parametros.set('perfilId', perfilId)
  }
  if (modulo?.length) {
    parametros.set('modulo', modulo.join(','))
  }
  if (accion?.length) {
    parametros.set('accion', accion.join(','))
  }
  if (desde) {
    parametros.set('desde', desde)
  }
  if (hasta) {
    parametros.set('hasta', hasta)
  }
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))
  parametros.set('orden', `ocurridoEn,${orden}`)

  return parametros.toString()
}

export function useRegistrosAuditoria(parametros: ParametrosAuditoria) {
  return useQuery({
    queryKey: clavesAuditoria.registros(parametros),
    queryFn: () =>
      api.get<Pagina<RegistroAuditoria>>(`auditoria?${consultaDeParametros(parametros)}`),
    placeholderData: keepPreviousData,
  })
}

export function useOpcionesFiltroAuditoria() {
  return useQuery({
    queryKey: clavesAuditoria.opcionesFiltro,
    queryFn: () => api.get<OpcionesFiltroAuditoria>('auditoria/opciones-filtro'),
  })
}
