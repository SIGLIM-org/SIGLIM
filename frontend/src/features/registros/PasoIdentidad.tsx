import { useId, type FormEvent } from 'react'
import { ArrowRight, Info, Search, UserRoundPlus } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { AvisoBloqueo } from '@/features/registros/AvisoBloqueo'
import { TarjetaPersona } from '@/features/registros/TarjetaPersona'
import { TituloPaso } from '@/features/registros/TituloPaso'
import type { PersonaConMinoria } from '@/features/registros/tipos'

type Props = {
  enfocar: boolean
  curp: string
  errorCurp?: string
  /** Fallo de la consulta que no es de la CURP (red, permiso). */
  errorGeneral?: string
  buscando: boolean
  jugador: PersonaConMinoria | null
  jugadorNuevo: boolean
  onCambiarCurp: (valor: string) => void
  onBuscar: () => void
  onContinuar: () => void
}

/**
 * Paso 1 (F-18): solo lectura (DEC-103). La CURP se busca en todo el sistema (RF-010, DEC-78): si
 * existe se reutiliza la identidad y, si no, sus datos se capturan en el paso 2.
 */
export function PasoIdentidad({
  enfocar,
  curp,
  errorCurp,
  errorGeneral,
  buscando,
  jugador,
  jugadorNuevo,
  onCambiarCurp,
  onBuscar,
  onContinuar,
}: Props) {
  const id = useId()
  const resuelto = Boolean(jugador) || jugadorNuevo

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    if (resuelto) {
      onContinuar()
    } else {
      onBuscar()
    }
  }

  return (
    <form onSubmit={alEnviar} noValidate aria-labelledby={`${id}-titulo`} className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <TituloPaso id={`${id}-titulo`} enfocar={enfocar}>
          Identidad del jugador
        </TituloPaso>
        <p className="max-w-prose text-body-md text-text-muted">
          Busca a la persona por su CURP. Si ya está en el sistema se reutiliza su identidad, aunque
          haya jugado en otro club; si no, capturas sus datos en el siguiente paso.
        </p>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1 sm:max-w-md">
          <CampoTexto
            id={`${id}-curp`}
            etiqueta="CURP del jugador"
            required
            maxLength={18}
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            value={curp}
            error={errorCurp}
            ayuda="18 caracteres, tal como aparecen en el documento."
            disabled={buscando}
            onChange={(evento) => onCambiarCurp(evento.target.value)}
          />
        </div>
        <Button
          variante={resuelto ? 'secundario' : 'primario'}
          icono={Search}
          cargando={buscando}
          onClick={onBuscar}
          className="shrink-0 sm:mt-6.5"
        >
          Buscar CURP
        </Button>
      </div>

      {errorGeneral && <AvisoBloqueo titulo="No se pudo buscar la CURP" mensaje={errorGeneral} />}

      {jugador && <TarjetaPersona rol="Persona encontrada" persona={jugador} />}

      {jugadorNuevo && (
        <div className="flex gap-3 rounded-lg border border-border bg-background p-4">
          <UserRoundPlus aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-primary" />
          <div className="flex flex-col gap-1">
            <p className="text-headline-sm text-text-main">Persona nueva</p>
            <p className="flex items-start gap-1.5 text-body-md text-text-muted">
              <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              No hay nadie con la CURP <span className="font-mono text-text-main">{curp}</span>.
              Captura sus datos en el siguiente paso; la identidad se registra una sola vez.
            </p>
          </div>
        </div>
      )}

      <div className="flex justify-end border-t border-border pt-4">
        <Button type="submit" icono={ArrowRight} disabled={!resuelto || buscando}>
          Continuar
        </Button>
      </div>
    </form>
  )
}
