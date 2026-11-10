import type { ReactNode } from 'react'
import { InsigniaNoOficial } from '@/components/ui/InsigniaNoOficial'
import {
  etiquetaCampoParametro,
  etiquetaCriterio,
  etiquetaFormatoLiguilla,
  type CampoParametro,
} from '@/features/temporadas/etiquetas'
import type { VersionParametros } from '@/features/temporadas/tipos'

type Props = {
  version: VersionParametros
}

type Dato = { campo: CampoParametro; valor: ReactNode }

function siNo(valor: boolean): string {
  return valor ? 'Sí' : 'No'
}

/**
 * Lectura de una versión de parámetros (F-11, RF-006): en solo lectura y en la instantánea del
 * historial. Las insignias «No oficial» salen de los `camposDemo` de ESA versión (D4), no de la
 * lista fija del formulario.
 */
export function ResumenParametros({ version }: Props) {
  const demo = new Set(version.camposDemo)

  const secciones: { titulo: string; datos: Dato[] }[] = [
    {
      titulo: 'Puntuación',
      datos: [
        { campo: 'puntosVictoria', valor: version.puntosVictoria },
        { campo: 'puntosEmpate', valor: version.puntosEmpate },
        { campo: 'puntosDerrota', valor: version.puntosDerrota },
      ],
    },
    {
      titulo: 'Marcador por default y cédula',
      datos: [
        { campo: 'golesDefaultGanador', valor: version.golesDefaultGanador },
        { campo: 'golesDefaultPerdedor', valor: version.golesDefaultPerdedor },
        { campo: 'plazoValidacionCedulaHoras', valor: `${version.plazoValidacionCedulaHoras} h` },
      ],
    },
    {
      titulo: 'Duración y bloque de cancha',
      datos: [
        { campo: 'duracionJuegoMin', valor: `${version.duracionJuegoMin} min` },
        { campo: 'bloqueCanchaMin', valor: `${version.bloqueCanchaMin} min` },
      ],
    },
    {
      titulo: 'Liguilla',
      datos: [
        { campo: 'clasificadosLiguilla', valor: version.clasificadosLiguilla },
        { campo: 'formatoLiguilla', valor: etiquetaFormatoLiguilla[version.formatoLiguilla] },
        { campo: 'empateLiguillaPorPenales', valor: siNo(version.empateLiguillaPorPenales) },
      ],
    },
    {
      titulo: 'Ascensos, descensos y registros',
      datos: [
        { campo: 'equiposAscienden', valor: version.equiposAscienden },
        { campo: 'equiposDescienden', valor: version.equiposDescienden },
        { campo: 'permiteCambioClub', valor: siNo(version.permiteCambioClub) },
      ],
    },
  ]

  return (
    <div className="flex flex-col gap-5">
      {secciones.map(({ titulo, datos }) => (
        <section key={titulo} className="flex flex-col gap-2">
          <h3 className="text-headline-sm text-text-main">{titulo}</h3>
          <dl className="grid gap-x-4 gap-y-2 sm:grid-cols-3">
            {datos.map(({ campo, valor }) => (
              <div key={campo} className="flex min-w-0 flex-col gap-1">
                <dt className="flex flex-wrap items-center gap-2 text-body-sm text-text-muted">
                  {etiquetaCampoParametro[campo]}
                  {demo.has(campo) && <InsigniaNoOficial />}
                </dt>
                <dd className="font-mono text-mono-data text-text-main">{valor}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}

      <section className="flex flex-col gap-2">
        <h3 className="flex flex-wrap items-center gap-2 text-headline-sm text-text-main">
          Criterios de desempate
          {demo.has('criteriosDesempate') && <InsigniaNoOficial />}
        </h3>
        <ol className="flex flex-col gap-1">
          {version.criteriosDesempate.map((criterio, indice) => (
            <li key={criterio} className="flex items-center gap-2 text-body-md text-text-main">
              <span className="font-mono text-mono-data text-text-muted">{indice + 1}.</span>
              {etiquetaCriterio[criterio].texto}
            </li>
          ))}
        </ol>
      </section>

      <section className="flex flex-col gap-2">
        <h3 className="text-headline-sm text-text-main">Duración y bloque por categoría</h3>
        {version.categorias.length === 0 ? (
          <p className="text-body-md text-text-muted">
            Todas las categorías usan los valores de la temporada.
          </p>
        ) : (
          <ul className="flex flex-col divide-y divide-border rounded-md border border-border">
            {version.categorias.map((fila) => (
              <li
                key={fila.categoria.id}
                className="flex flex-wrap items-center justify-between gap-2 px-3 py-2"
              >
                <span className="text-label-md text-text-main">{fila.categoria.nombre}</span>
                <span className="font-mono text-mono-data text-text-main">
                  {fila.duracionJuegoMin} min · bloque {fila.bloqueCanchaMin} min
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
