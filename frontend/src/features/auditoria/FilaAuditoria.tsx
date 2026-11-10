import { useId, useState } from 'react'
import { ChevronRight } from 'lucide-react'
import { DetalleRegistroAuditoria } from '@/features/auditoria/DetalleRegistroAuditoria'
import { formatearOcurridoEn } from '@/features/auditoria/fechas'
import type { RegistroAuditoria } from '@/features/auditoria/tipos'
import { etiquetaModulo } from '@/lib/modulos'

/** Número de columnas de la tabla, para que el detalle las abarque todas. */
const COLUMNAS = 7

/** Nombre de la columna dentro de la tarjeta, donde el encabezado de la tabla no se ve (RNF-008). */
function EtiquetaTarjeta({ children }: { children: string }) {
  return <span className="block text-label-sm text-text-muted xl:hidden">{children}</span>
}

/**
 * Bajo `xl` la fila se apila como tarjeta y el botón queda arriba a la derecha (RNF-007). Los
 * roles explícitos conservan la semántica de tabla, que `display: grid` quita en algunos
 * navegadores.
 */
export function FilaAuditoria({ registro }: { registro: RegistroAuditoria }) {
  const idDetalle = useId()
  const [abierta, setAbierta] = useState(false)

  return (
    <>
      <tr
        role="row"
        className="relative block border-b border-border py-2 pr-14 pl-3 transition-colors hover:bg-background xl:table-row xl:p-0"
      >
        <td role="cell" className="block py-0.5 xl:table-cell xl:py-2 xl:pr-3 xl:pl-1">
          <span className="absolute right-1.5 top-1.5 xl:static">
            <button
              type="button"
              aria-expanded={abierta}
              aria-controls={abierta ? idDetalle : undefined}
              aria-label={`${abierta ? 'Ocultar' : 'Ver'} detalle del registro ${registro.id}`}
              onClick={() => setAbierta((actual) => !actual)}
              className="inline-flex size-11 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-border/50 hover:text-text-main"
            >
              <ChevronRight
                aria-hidden="true"
                className={['size-4 transition-transform', abierta ? 'rotate-90' : ''].join(' ')}
              />
            </button>
          </span>
        </td>
        <td role="cell" className="block py-0.5 align-middle xl:table-cell xl:py-2 xl:pr-3">
          <time dateTime={registro.ocurridoEn} className="font-mono text-mono-data tabular-nums text-text-main">
            {formatearOcurridoEn(registro.ocurridoEn)}
          </time>
        </td>
        <td role="cell" className="block py-0.5 align-middle text-body-md xl:table-cell xl:py-2 xl:pr-3">
          <EtiquetaTarjeta>Usuario</EtiquetaTarjeta>
          {registro.perfil ? (
            <span className="block break-words text-text-main">{registro.perfil.nombreMostrado}</span>
          ) : (
            <span className="text-text-muted">Sin usuario</span>
          )}
        </td>
        <td role="cell" className="block py-0.5 align-middle text-body-md text-text-main xl:table-cell xl:py-2 xl:pr-3">
          <EtiquetaTarjeta>Módulo</EtiquetaTarjeta>
          {etiquetaModulo(registro.modulo)}
        </td>
        <td role="cell" className="block py-0.5 align-middle xl:table-cell xl:py-2 xl:pr-3">
          <EtiquetaTarjeta>Acción</EtiquetaTarjeta>
          <span className="block font-mono text-mono-data break-all text-text-main">{registro.accion}</span>
        </td>
        <td role="cell" className="block py-0.5 align-middle xl:table-cell xl:py-2 xl:pr-3">
          <EtiquetaTarjeta>Entidad</EtiquetaTarjeta>
          <span className="block font-mono text-mono-data break-all text-text-main">{registro.entidad}</span>
          {registro.entidadId && (
            <span className="block truncate font-mono text-mono-data text-text-muted">{registro.entidadId}</span>
          )}
        </td>
        <td role="cell" className="block py-0.5 align-middle xl:table-cell xl:py-2 xl:pr-3">
          {registro.motivo && (
            <>
              <EtiquetaTarjeta>Motivo</EtiquetaTarjeta>
              <span className="block truncate text-body-md text-text-muted">{registro.motivo}</span>
            </>
          )}
        </td>
      </tr>
      {abierta && (
        <tr role="row" id={idDetalle} className="block border-b border-border bg-background xl:table-row">
          <td role="cell" colSpan={COLUMNAS} className="block px-3 py-4 xl:table-cell xl:px-12">
            <DetalleRegistroAuditoria registro={registro} />
          </td>
        </tr>
      )}
    </>
  )
}
