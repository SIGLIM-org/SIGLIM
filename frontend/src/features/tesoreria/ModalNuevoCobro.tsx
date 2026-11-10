import { useId, useState, type FormEvent } from 'react'
import { Info, Plus, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { CampoSeleccion } from '@/components/ui/CampoSeleccion'
import { CampoTexto } from '@/components/ui/CampoTexto'
import { useDialogoModal } from '@/components/ui/useDialogoModal'
import { useSesionActual } from '@/features/seguridad/ProveedorSesionActual'
import { tienePermiso } from '@/features/seguridad/permisos'
import { useCrearCobro, useEquiposDeClub } from '@/features/tesoreria/api'
import type { ClubResumen, Cobro, CrearCobroPeticion } from '@/features/tesoreria/tipos'
import { ApiError } from '@/lib/api/ApiError'

type Props = {
  clubes: readonly ClubResumen[]
  cargandoClubes: boolean
  errorClubes: unknown
  onReintentarClubes: () => void
  onCerrar: () => void
  onCreado: (cobro: Cobro) => void
}

type Valores = {
  clubId: string
  equipoId: string
  concepto: string
  monto: string
  periodoInicio: string
  fechaLimite: string
}

const VALORES_INICIALES: Valores = {
  clubId: '',
  equipoId: '',
  concepto: '',
  monto: '',
  periodoInicio: '',
  fechaLimite: '',
}

const CAMPOS_FORMULARIO = new Set([
  'clubId',
  'equipoId',
  'concepto',
  'monto',
  'periodoInicio',
  'fechaLimite',
])

function detalleDe(error: unknown): string {
  return error instanceof ApiError
    ? error.problema.detail
    : 'No se pudieron cargar los clubes. Intenta de nuevo.'
}

/**
 * Modal de alta de un cobro manual (F-33, H-29.2, RF-137). Solo captura los campos del contrato:
 * club, equipo opcional, concepto, monto, periodo de inicio opcional y fecha límite. El cobro nace
 * PENDIENTE en el backend; aquí no se cobra ni se registra ningún pago.
 */
export function ModalNuevoCobro({
  clubes,
  cargandoClubes,
  errorClubes,
  onReintentarClubes,
  onCerrar,
  onCreado,
}: Props) {
  const dialogoRef = useDialogoModal()
  const { yo } = useSesionActual()
  const puedeVerEquipos = tienePermiso(yo.permisos, 'organizacion.equipo:CONSULTAR')
  const crear = useCrearCobro()
  const [valores, setValores] = useState<Valores>(VALORES_INICIALES)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const id = useId()
  const idTitulo = `${id}-titulo`
  const equipos = useEquiposDeClub(valores.clubId, puedeVerEquipos)
  const guardando = crear.isPending

  const limpiarErrores = (campos: readonly string[]) => {
    setErrores((actuales) => {
      const siguiente = { ...actuales }
      for (const campo of campos) {
        delete siguiente[campo]
      }
      return siguiente
    })
  }

  const cambiar = (cambios: Partial<Valores>) => {
    setValores((actuales) => ({ ...actuales, ...cambios }))
    limpiarErrores(Object.keys(cambios))
    setErrorGeneral(null)
  }

  const alFallar = (error: unknown) => {
    if (!(error instanceof ApiError)) {
      setErrorGeneral('No se pudo crear el cobro. Intenta de nuevo.')
      return
    }

    const deCampos = Object.fromEntries(
      (error.problema.errores ?? [])
        .filter(({ campo }) => CAMPOS_FORMULARIO.has(campo))
        .map(({ campo, mensaje }) => [campo, mensaje]),
    )

    setErrores(deCampos)
    setErrorGeneral(error.problema.detail)
  }

  const validar = (): Record<string, string> => {
    const encontrados: Record<string, string> = {}
    const concepto = valores.concepto.trim()
    const monto = valores.monto.trim()

    if (!valores.clubId) {
      encontrados.clubId = 'Elige el club del cobro.'
    }
    if (!concepto) {
      encontrados.concepto = 'Escribe el concepto del cobro.'
    } else if (concepto.length > 200) {
      encontrados.concepto = 'El concepto admite hasta 200 caracteres.'
    }
    if (!monto) {
      encontrados.monto = 'Escribe el monto del cobro.'
    } else if (!/^\d+(\.\d{1,2})?$/.test(monto)) {
      encontrados.monto = 'Escribe un monto mayor que cero, con hasta dos decimales.'
    } else {
      const [entero] = monto.split('.')
      const numero = Number(monto)

      if (!Number.isFinite(numero) || numero <= 0) {
        encontrados.monto = 'El monto debe ser mayor que cero.'
      } else if (entero.replace(/^0+/, '').length > 10) {
        encontrados.monto = 'El monto admite hasta diez dígitos enteros.'
      }
    }
    if (!valores.fechaLimite) {
      encontrados.fechaLimite = 'Elige la fecha límite del cobro.'
    } else if (
      valores.periodoInicio &&
      valores.fechaLimite < valores.periodoInicio
    ) {
      encontrados.fechaLimite = 'La fecha límite no puede ser anterior al inicio del periodo.'
    }

    return encontrados
  }

  const alEnviar = (evento: FormEvent<HTMLFormElement>) => {
    evento.preventDefault()
    setErrorGeneral(null)

    const encontrados = validar()
    setErrores(encontrados)

    if (Object.keys(encontrados).length > 0) {
      return
    }

    const peticion: CrearCobroPeticion = {
      clubId: valores.clubId,
      concepto: valores.concepto.trim(),
      monto: Number(valores.monto),
      fechaLimite: valores.fechaLimite,
      ...(valores.equipoId ? { equipoId: valores.equipoId } : {}),
      ...(valores.periodoInicio ? { periodoInicio: valores.periodoInicio } : {}),
    }

    crear.mutate(peticion, { onSuccess: onCreado, onError: alFallar })
  }

  return (
    <dialog
      ref={dialogoRef}
      aria-labelledby={idTitulo}
      onCancel={(evento) => {
        evento.preventDefault()
        if (!guardando) {
          onCerrar()
        }
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-lg border border-border bg-surface p-0 text-text-main shadow-overlay backdrop:bg-surface-dark/50"
    >
      <form onSubmit={alEnviar} noValidate>
        <div className="flex items-start gap-3 border-b border-border p-5">
          <span className="grid size-10 shrink-0 place-items-center rounded-md bg-primary/10 text-primary">
            <Plus aria-hidden="true" className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 id={idTitulo} className="text-headline-md">
              Nuevo cobro
            </h2>
            <p className="mt-1 text-body-sm text-text-muted">
              Registra un cobro manual a un club o a uno de sus equipos. Queda pendiente de pago.
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-4 p-5">
          <CampoSeleccion
            id={`${id}-club`}
            etiqueta="Club"
            required
            disabled={cargandoClubes || guardando}
            error={errores.clubId}
            ayuda={
              cargandoClubes
                ? 'Cargando clubes…'
                : clubes.length === 0
                  ? 'No hay clubes disponibles para crear un cobro.'
                  : undefined
            }
            value={valores.clubId}
            onChange={(evento) => cambiar({ clubId: evento.target.value, equipoId: '' })}
          >
            <option value="">Elige el club</option>
            {clubes.map((club) => (
              <option key={club.id} value={club.id}>
                {club.nombre}
              </option>
            ))}
          </CampoSeleccion>

          {errorClubes !== null && errorClubes !== undefined && (
            <div
              role="alert"
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-background px-3 py-2"
            >
              <p className="text-body-sm text-text-main">{detalleDe(errorClubes)}</p>
              <Button variante="secundario" icono={RotateCw} onClick={onReintentarClubes}>
                Reintentar
              </Button>
            </div>
          )}

          {puedeVerEquipos && valores.clubId && (
            <CampoSeleccion
              id={`${id}-equipo`}
              etiqueta="Equipo (opcional)"
              disabled={equipos.isPending || guardando}
              ayuda={
                equipos.isPending
                  ? 'Cargando equipos…'
                  : equipos.isError
                    ? 'No se pudieron cargar los equipos; puedes crear el cobro al club.'
                    : 'Si lo dejas vacío, el cobro se carga al club.'
              }
              value={valores.equipoId}
              onChange={(evento) => cambiar({ equipoId: evento.target.value })}
            >
              <option value="">Cobrar al club</option>
              {(equipos.data ?? []).map((equipo) => (
                <option key={equipo.id} value={equipo.id}>
                  {equipo.nombre}
                </option>
              ))}
            </CampoSeleccion>
          )}

          {puedeVerEquipos && equipos.isError && (
            <div
              role="alert"
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-background px-3 py-2"
            >
              <p className="text-body-sm text-text-main">
                No se pudieron cargar los equipos de este club; el cobro puede quedar al club.
              </p>
              <Button
                variante="secundario"
                icono={RotateCw}
                onClick={() => void equipos.refetch()}
              >
                Reintentar
              </Button>
            </div>
          )}

          {!puedeVerEquipos && (
            <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong">
              <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
              Tu perfil no puede consultar el catálogo de equipos; el cobro se cargará al club.
            </p>
          )}

          <CampoTexto
            id={`${id}-concepto`}
            etiqueta="Concepto"
            required
            maxLength={200}
            error={errores.concepto}
            value={valores.concepto}
            onChange={(evento) => cambiar({ concepto: evento.target.value })}
          />

          <div className="grid gap-3 sm:grid-cols-3">
            <CampoTexto
              id={`${id}-monto`}
              etiqueta="Monto en MXN"
              required
              type="number"
              min="0.01"
              step="0.01"
              inputMode="decimal"
              placeholder="0.00"
              error={errores.monto}
              value={valores.monto}
              onChange={(evento) => cambiar({ monto: evento.target.value })}
            />
            <CampoTexto
              id={`${id}-periodo`}
              etiqueta="Inicio del periodo"
              type="date"
              error={errores.periodoInicio}
              ayuda="Opcional; si falta, se usa el día de hoy."
              value={valores.periodoInicio}
              onChange={(evento) => cambiar({ periodoInicio: evento.target.value })}
            />
            <CampoTexto
              id={`${id}-limite`}
              etiqueta="Fecha límite"
              required
              type="date"
              min={valores.periodoInicio || undefined}
              error={errores.fechaLimite}
              value={valores.fechaLimite}
              onChange={(evento) => cambiar({ fechaLimite: evento.target.value })}
            />
          </div>

          <p className="flex items-start gap-2 rounded-md bg-info/8 p-3 text-body-sm text-info-strong">
            <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            El cobro queda PENDIENTE; este alta no registra ningún pago.
          </p>

          {errorGeneral && (
            <p role="alert" className="rounded-md bg-error/8 p-3 text-body-md text-error-strong">
              {errorGeneral}
            </p>
          )}

          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button variante="secundario" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button type="submit" icono={Plus} cargando={guardando}>
              Crear cobro
            </Button>
          </div>
        </div>
      </form>
    </dialog>
  )
}
