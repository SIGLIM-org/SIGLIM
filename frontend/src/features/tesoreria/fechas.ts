import { ZONA_NEGOCIO } from '@/lib/zonaHoraria'

/*
 * Las fechas de un cobro son días `AAAA-MM-DD` sin hora. Se formatean en UTC para que la zona
 * de la máquina no las mueva un día.
 */
const formatoFecha = new Intl.DateTimeFormat('es-MX', {
  timeZone: 'UTC',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

const formatoMoneda = new Intl.NumberFormat('es-MX', {
  style: 'currency',
  currency: 'MXN',
})

function fechaUtc(fecha: string): Date {
  return new Date(`${fecha}T00:00:00Z`)
}

/** «15 nov 2025». */
export function formatearFecha(fecha: string): string {
  return formatoFecha.format(fechaUtc(fecha)).replace(/\./g, '')
}

/** «$1,500.00» en pesos mexicanos. */
export function formatearMonto(monto: number): string {
  return formatoMoneda.format(monto)
}

/**
 * Convierte los días del filtro en las fechas de la API (convenciones §6.13): `desde` se incluye
 * y `hasta`, que es excluyente, se envía como el día siguiente para que el último día elegido
 * entre completo.
 */
export function rangoDeCobros(
  desdeDia?: string,
  hastaDia?: string,
): { desde?: string; hasta?: string } {
  return {
    ...(desdeDia ? { desde: desdeDia } : {}),
    ...(hastaDia ? { hasta: diaSiguiente(hastaDia) } : {}),
  }
}

/** Día siguiente a un `AAAA-MM-DD`, con aritmética UTC para no depender de la zona del navegador. */
export function diaSiguiente(dia: string): string {
  const [anio, mes, numero] = dia.split('-').map(Number)

  return new Date(Date.UTC(anio, mes - 1, numero + 1)).toISOString().slice(0, 10)
}

/* `en-CA` formatea como `AAAA-MM-DD`, el mismo formato de un `<input type="date">`. */
const formatoDiaNegocio = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA_NEGOCIO,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/**
 * Día de hoy `AAAA-MM-DD` en la zona de negocio (convenciones §1). No usa `toISOString()`, que
 * daría el día UTC: de 18:00 a 24:00 en Ciudad de México ya sería mañana.
 */
export function hoyEnZonaNegocio(): string {
  return formatoDiaNegocio.format(new Date())
}

/** «1 cobro» / «3 cobros». */
export function contarCobros(total: number): string {
  return `${total} ${total === 1 ? 'cobro' : 'cobros'}`
}
