import type { RefObject } from 'react'
import { CircleCheck, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Insignia } from '@/components/ui/Insignia'
import type { CalendarioGenerado } from '@/features/programacion/calendario/tipos'

type Props = {
  idTitulo: string
  tituloRef: RefObject<HTMLHeadingElement | null>
  /** `null` cuando solo se guardaron días de juego, sin generar. */
  generado: CalendarioGenerado | null
  /** «Libre: sábado y domingo», en el orden en que se guardaron. */
  diasGuardados: string[]
  /** «Categoría · Grupo» por `grupoId`, para distinguir grupos homónimos. */
  nombreGrupo: (grupoId: string, nombre: string) => string
  onCerrar: () => void
}

function plural(cantidad: number, singular: string, varios: string): string {
  return `${cantidad} ${cantidad === 1 ? singular : varios}`
}

/**
 * Vista de resultado del modal «Generar calendario» (H-32.4, DEC-200): grupos generados y
 * reemplazados, omitidos con su causa (DEC-187) y advertencias que no bloquean (DEC-193).
 */
export function ResultadoCalendario({
  idTitulo,
  tituloRef,
  generado,
  diasGuardados,
  nombreGrupo,
  onCerrar,
}: Props) {
  return (
    <div className="flex flex-col gap-5 p-6">
      <div className="flex items-start gap-3">
        <CircleCheck aria-hidden="true" className="mt-0.5 size-6 shrink-0 text-success" />
        <div className="min-w-0">
          <h2
            id={idTitulo}
            ref={tituloRef}
            tabIndex={-1}
            className="text-balance text-headline-md outline-none"
          >
            {generado ? 'Calendario generado' : 'Días de juego guardados'}
          </h2>
          {generado && (
            <p className="mt-1 text-body-md text-text-muted">
              {plural(generado.grupos.length, 'grupo', 'grupos')} ·{' '}
              {plural(generado.totalFechas, 'fecha', 'fechas')} en total (
              {plural(generado.fechasCreadas, 'nueva', 'nuevas')}) ·{' '}
              {plural(generado.vueltas, 'vuelta', 'vueltas')} (parámetros versión{' '}
              {generado.versionParametros})
            </p>
          )}
        </div>
      </div>

      {generado && generado.grupos.length > 0 && (
        <section aria-labelledby={`${idTitulo}-generados`} className="flex flex-col gap-2">
          <h3 id={`${idTitulo}-generados`} className="text-label-md text-text-main">
            Grupos generados
          </h3>
          <ul className="divide-y divide-border rounded-lg border border-border">
            {generado.grupos.map((grupo) => (
              <li
                key={grupo.grupoId}
                className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 px-3 py-2"
              >
                <span className="min-w-0 text-body-md text-text-main">
                  {nombreGrupo(grupo.grupoId, grupo.nombre)}
                </span>
                <span className="flex items-center gap-2 text-body-sm text-text-muted">
                  {plural(grupo.equipos, 'equipo', 'equipos')} ·{' '}
                  {plural(grupo.fechas, 'fecha', 'fechas')}
                  {grupo.reemplazado && <Insignia variante="informacion">Reemplazado</Insignia>}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {generado && generado.gruposOmitidos.length > 0 && (
        <section
          aria-labelledby={`${idTitulo}-omitidos`}
          className="flex flex-col gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3"
        >
          <h3
            id={`${idTitulo}-omitidos`}
            className="flex items-center gap-2 text-label-md text-warning-strong"
          >
            <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
            Grupos omitidos
          </h3>
          <ul className="flex flex-col gap-1">
            {generado.gruposOmitidos.map((grupo) => (
              <li key={grupo.grupoId} className="text-body-md text-text-main">
                <span className="font-semibold">{nombreGrupo(grupo.grupoId, grupo.nombre)}:</span>{' '}
                {grupo.causa}
              </li>
            ))}
          </ul>
        </section>
      )}

      {generado && generado.advertencias.length > 0 && (
        <section
          aria-labelledby={`${idTitulo}-advertencias`}
          className="flex flex-col gap-2 rounded-lg border border-warning/40 bg-warning/10 p-3"
        >
          <h3
            id={`${idTitulo}-advertencias`}
            className="flex items-center gap-2 text-label-md text-warning-strong"
          >
            <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
            Advertencias
          </h3>
          <ul className="flex flex-col gap-1">
            {generado.advertencias.map((advertencia, indice) => (
              <li
                key={`${advertencia.tipo}-${advertencia.grupoId ?? 'temporada'}-${indice}`}
                className="break-words text-body-md text-text-main"
              >
                {advertencia.mensaje}
              </li>
            ))}
          </ul>
        </section>
      )}

      {diasGuardados.length > 0 && (
        <section aria-labelledby={`${idTitulo}-dias`} className="flex flex-col gap-2">
          <h3 id={`${idTitulo}-dias`} className="text-label-md text-text-main">
            {generado ? 'También se guardaron los días de juego' : 'Días de juego por categoría'}
          </h3>
          <ul className="flex flex-col gap-1">
            {diasGuardados.map((linea) => (
              <li key={linea} className="text-body-md text-text-main">
                {linea}
              </li>
            ))}
          </ul>
          <p className="text-body-sm text-text-muted">
            Se usan en las próximas asignaciones de cancha y horario; no mueven partidos ya
            programados.
          </p>
        </section>
      )}

      {generado && (
        <p className="text-body-sm text-text-muted">
          La cancha y el horario de cada partido se asignan después, fecha por fecha.
        </p>
      )}

      <div className="flex justify-end">
        <Button onClick={onCerrar}>Cerrar</Button>
      </div>
    </div>
  )
}
