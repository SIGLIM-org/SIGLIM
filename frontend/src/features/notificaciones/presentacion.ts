import { Bell, CalendarDays, FileCheck2, Wallet, type LucideIcon } from 'lucide-react'

type Presentacion = { icono: LucideIcon; categoria: string }

const presentaciones: Record<string, Presentacion> = {
  CEDULA_PENDIENTE: { icono: FileCheck2, categoria: 'Cédulas y partidos' },
  COBRO_VENCIDO: { icono: Wallet, categoria: 'Tesorería' },
  PROGRAMACION_PUBLICADA: { icono: CalendarDays, categoria: 'Programación' },
}

/** Ícono y categoría de un evento; uno que la interfaz no conoce se muestra como aviso genérico. */
export function presentacionDe(evento: string): Presentacion {
  return presentaciones[evento] ?? { icono: Bell, categoria: 'Aviso' }
}

/**
 * El enlace solo se sigue si es una ruta de la aplicación interna: el backend lo arma, pero la
 * interfaz no navega a otro sitio ni a una ruta del portal.
 */
export function enlaceInterno(enlace: string | null): string | null {
  if (!enlace) {
    return null
  }

  return enlace === '/app' || /^\/app\/[^/]/.test(enlace) ? enlace : null
}

const MINUTO = 60_000
const HORA = 60 * MINUTO
const DIA = 24 * HORA

const fechaCorta = new Intl.DateTimeFormat('es-MX', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'America/Mexico_City',
})

const fechaCompleta = new Intl.DateTimeFormat('es-MX', {
  dateStyle: 'full',
  timeStyle: 'short',
  timeZone: 'America/Mexico_City',
})

/** «Hace un momento», «Hace 15 min», «Hace 3 h», «Ayer» o la fecha, en hora de México. */
export function tiempoRelativo(iso: string, ahora: number = Date.now()): string {
  const diferencia = ahora - new Date(iso).getTime()

  if (diferencia < MINUTO) {
    return 'Hace un momento'
  }
  if (diferencia < HORA) {
    return `Hace ${Math.floor(diferencia / MINUTO)} min`
  }
  if (diferencia < DIA) {
    return `Hace ${Math.floor(diferencia / HORA)} h`
  }
  if (diferencia < 2 * DIA) {
    return 'Ayer'
  }

  return fechaCorta.format(new Date(iso))
}

/** Fecha y hora completas para el atributo `title` del tiempo relativo. */
export function fechaDetallada(iso: string): string {
  return fechaCompleta.format(new Date(iso))
}

/** Contador para la insignia de la campana: «99+» a partir de 100. */
export function contadorVisible(total: number): string {
  return total > 99 ? '99+' : String(total)
}
