import { CircleCheck, CircleX } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import type { Temporada } from '@/features/temporadas/tipos'

type Props = {
  temporada: Temporada
}

/**
 * Lista de verificación de activación de una temporada en configuración (F-10, H-04.2, RF-100).
 * Muestra cada requisito con su código del ERS, si se cumple y qué falta. El resumen no depende
 * solo del color: lleva texto y un icono distinto por estado (RNF-008).
 */
export function ListaVerificacion({ temporada }: Props) {
  const requisitos = temporada.activacion?.requisitos ?? []
  const cumplidos = requisitos.filter((requisito) => requisito.cumple).length
  const listo = temporada.activacion?.puedeActivarse ?? false
  const idTitulo = `requisitos-${temporada.id}`

  return (
    <section
      id={`panel-${temporada.id}`}
      aria-labelledby={idTitulo}
      tabIndex={-1}
      className="flex scroll-mt-24 flex-col gap-4 rounded-lg border border-border bg-surface p-4"
    >
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 id={idTitulo} className="text-headline-sm text-text-main">
            Requisitos para activar «{temporada.nombre}»
          </h3>
          <p className="mt-1 text-body-sm text-text-muted">
            La temporada se activa cuando se cumplen todos los requisitos.
          </p>
        </div>
        <Insignia variante={listo ? 'exito' : 'advertencia'}>
          {listo ? 'Lista para activar' : 'Faltan requisitos'}
        </Insignia>
      </div>

      <p className="text-label-md text-text-main">
        {cumplidos} de {requisitos.length} requisitos cumplidos
      </p>

      <ul className="flex flex-col gap-2">
        {requisitos.map((requisito) => (
          <li
            key={requisito.codigo}
            className="flex items-start gap-2 rounded-md bg-background p-3"
          >
            {requisito.cumple ? (
              <CircleCheck
                aria-hidden="true"
                className="mt-0.5 size-4 shrink-0 text-success-strong"
              />
            ) : (
              <CircleX
                aria-hidden="true"
                className="mt-0.5 size-4 shrink-0 text-error-strong"
              />
            )}
            <div className="min-w-0">
              <p className="text-body-md text-text-main">{requisito.descripcion}</p>
              <p className="mt-0.5 font-mono text-mono-data text-text-muted">
                {requisito.codigo} · {requisito.cumple ? 'Cumplido' : 'Pendiente'}
              </p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
