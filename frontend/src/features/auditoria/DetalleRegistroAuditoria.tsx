import type { ReactNode } from 'react'
import type { RegistroAuditoria } from '@/features/auditoria/tipos'

const CLASE_MONO = 'font-mono text-mono-data break-all text-text-main'

function Dato({ termino, children }: { termino: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-3">
      <dt className="break-words text-label-md text-text-muted">{termino}</dt>
      <dd className="min-w-0">{children}</dd>
    </div>
  )
}

/** Un valor del detalle, siempre como texto (RNF-012): primitivos tal cual, lo demás en JSON. */
function ValorDetalle({ valor }: { valor: unknown }) {
  if (typeof valor === 'object') {
    return <code className={CLASE_MONO}>{JSON.stringify(valor)}</code>
  }

  return <span className="break-words text-body-md text-text-main">{String(valor)}</span>
}

/** Registro completo de una fila; `GET /auditoria` ya lo trae (convenciones de API §6.1). */
export function DetalleRegistroAuditoria({ registro }: { registro: RegistroAuditoria }) {
  const detalle = Object.entries(registro.detalle ?? {})

  return (
    <div className="flex flex-col gap-4">
      <dl className="flex flex-col gap-2">
        {registro.motivo && (
          <Dato termino="Motivo">
            <p className="whitespace-pre-wrap break-words text-body-md text-text-main">
              {registro.motivo}
            </p>
          </Dato>
        )}
        <Dato termino="Entidad">
          <code className={CLASE_MONO}>{registro.entidad}</code>
        </Dato>
        {registro.entidadId && (
          <Dato termino="ID de la entidad">
            <code className={CLASE_MONO}>{registro.entidadId}</code>
          </Dato>
        )}
        <Dato termino="ID del registro">
          <code className={CLASE_MONO}>{registro.id}</code>
        </Dato>
      </dl>
      {detalle.length > 0 && (
        <section aria-label="Detalle" className="flex flex-col gap-2 border-t border-border pt-3">
          <h3 className="text-label-md text-text-main">Detalle</h3>
          <dl className="flex flex-col gap-2">
            {detalle.map(([clave, valor]) => (
              <Dato key={clave} termino={clave}>
                <ValorDetalle valor={valor} />
              </Dato>
            ))}
          </dl>
        </section>
      )}
    </div>
  )
}
