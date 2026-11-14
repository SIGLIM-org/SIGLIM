import { Baby, UsersRound } from 'lucide-react'
import { Insignia } from '@/components/ui/Insignia'
import { etiquetaEstadoRegistro } from '@/features/registros/documentos/etiquetas'
import type { RegistroJugadorDetalle } from '@/features/registros/tipos'
import { etiquetaEstadoTemporada } from '@/features/temporadas/etiquetas'
import { formatearFecha } from '@/features/temporadas/fechas'

type Props = {
  registro: RegistroJugadorDetalle
  /** Id del título, para que la sección que lo contiene se etiquete con él. */
  idTitulo: string
}

/**
 * Resumen del registro de jugador (DEC-111): nombre, CURP (completa solo si el backend la manda,
 * DEC-110), equipo, club, categoría, temporada con su estado, fecha de alta, estado y, si es menor,
 * sus tutores con nombre y parentesco. Un registro en `BAJA` agrega la fecha y el motivo de la baja
 * (DEC-128). Se usa en la página de documentos y en la bandeja.
 */
export function CabeceraRegistro({ registro, idTitulo }: Props) {
  const estado = etiquetaEstadoRegistro[registro.estado]
  const temporada = etiquetaEstadoTemporada[registro.temporada.estado]
  const curp = registro.persona.curp ?? registro.persona.curpEnmascarada
  const datos: { termino: string; valor: string }[] = [
    { termino: 'Equipo', valor: registro.equipo.nombre },
    { termino: 'Club', valor: registro.club.nombre },
    { termino: 'Categoría', valor: registro.categoria.nombre },
    { termino: 'Temporada', valor: `${registro.temporada.nombre} · ${temporada.texto}` },
    { termino: 'Fecha de alta', valor: formatearFecha(registro.altaEn) },
  ]
  const deBaja = registro.estado === 'BAJA'
  if (deBaja) {
    datos.push({
      termino: 'Fecha de baja',
      valor: registro.bajaEn ? formatearFecha(registro.bajaEn) : 'Sin fecha registrada',
    })
  }

  return (
    <div className="@container flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <h2 id={idTitulo} className="min-w-0 break-words text-headline-lg text-text-main">
            {registro.persona.nombreCompleto}
          </h2>
          <Insignia variante={estado.variante} icono={estado.icono}>
            {estado.texto}
          </Insignia>
          {registro.esMenor && (
            <Insignia variante="informacion" icono={Baby}>
              Menor de edad
            </Insignia>
          )}
        </div>
        <p className="flex flex-wrap items-baseline gap-x-2 text-body-md text-text-muted">
          <span>CURP</span>
          <span className="break-all font-mono text-mono-data text-text-main">{curp}</span>
          {registro.persona.curp === null && <span className="text-body-sm">(parcial)</span>}
        </p>
      </div>

      {/* Con 5 datos el último ocupa la fila en 2 columnas; con 6 (baja) la cuadrícula queda pareja. */}
      <dl
        className={[
          'grid gap-px overflow-hidden rounded-lg border border-border bg-border @sm:grid-cols-2',
          deBaja ? '@4xl:grid-cols-3' : '@4xl:grid-cols-5',
        ].join(' ')}
      >
        {datos.map(({ termino, valor }) => (
          <div
            key={termino}
            className={[
              'flex min-w-0 flex-col gap-0.5 bg-surface px-4 py-3',
              deBaja ? '' : '@sm:last:col-span-2 @4xl:last:col-span-1',
            ].join(' ')}
          >
            <dt className="text-body-sm text-text-muted">{termino}</dt>
            <dd className="break-words text-label-md text-text-main">{valor}</dd>
          </div>
        ))}
      </dl>

      {deBaja && registro.motivoBaja && (
        <div className="flex flex-col gap-1 rounded-lg bg-background p-3">
          <p className="text-label-md text-text-main">Motivo de la baja</p>
          <p className="whitespace-pre-line break-words text-body-md text-text-main">
            {registro.motivoBaja}
          </p>
        </div>
      )}

      {registro.esMenor && (
        <div className="flex flex-col gap-2 rounded-lg bg-background p-3">
          <p className="flex items-center gap-2 text-label-md text-text-main">
            <UsersRound aria-hidden="true" className="size-4 text-text-muted" />
            {registro.tutores.length === 1 ? 'Tutor' : 'Tutores'}
          </p>
          {registro.tutores.length === 0 ? (
            <p className="text-body-md text-text-muted">Sin tutor vigente registrado.</p>
          ) : (
            <ul className="flex flex-col gap-1">
              {registro.tutores.map((tutor) => (
                <li
                  key={`${tutor.nombreCompleto}-${tutor.parentesco}`}
                  className="break-words text-body-md text-text-main"
                >
                  {tutor.nombreCompleto}
                  <span className="text-text-muted"> · {tutor.parentesco}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
