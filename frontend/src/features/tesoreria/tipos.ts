/** DTO de tesorería: los mismos nombres del contrato (convenciones de API §6.13). */

import type { Pagina } from '@/lib/api/tipos'

/** Tipo del cobro; el alta manual siempre crea `MANUAL` (RF-137). */
export type TipoCobro = 'ARBITRAJE' | 'CANCHA' | 'MULTA' | 'MANUAL'

/** Estado del cobro (DEC-36). */
export type EstadoCobro = 'PENDIENTE' | 'PAGADO' | 'RECHAZADO' | 'VENCIDO'

/** Referencia anidada de club o equipo que devuelve la API. */
export type ReferenciaCobro = {
  id: string
  nombre: string
}

/** Cobro a un club o a un equipo (RF-137, RF-140). */
export type Cobro = {
  id: string
  tipo: TipoCobro
  concepto: string
  /** BigDecimal con dos decimales; JSON lo entrega como número. */
  monto: number
  /** Día desde el que aplica el cobro; la API lo omite si no se indicó. */
  periodoInicio?: string
  fechaLimite: string
  estado: EstadoCobro
  club: ReferenciaCobro
  /** Se omite en un cobro cargado al club. */
  equipo?: ReferenciaCobro
  creadoEn: string
}

/** Filtros de `GET /cobros` (convenciones de API §6.13). */
export type FiltrosCobros = {
  clubId?: string
  equipoId?: string
  tipo?: TipoCobro
  estado?: EstadoCobro
  /** Día `AAAA-MM-DD`; la API lo recibe como fecha inclusiva. */
  desde?: string
  /** Día `AAAA-MM-DD`; se envía el día siguiente porque la API lo excluye. */
  hasta?: string
  pagina: number
  tamano: number
  orden?: string
}

/** Cuerpo de `POST /cobros`; `equipoId` es opcional. */
export type CrearCobroPeticion = {
  clubId: string
  equipoId?: string
  concepto: string
  monto: number
  periodoInicio?: string
  fechaLimite: string
}

/** Medio de un pago recibido fuera del sistema (RF-139); `EN_LINEA_SIMULADO` no se captura. */
export type MedioPago = 'EFECTIVO' | 'TRANSFERENCIA' | 'OTRO'

/** Datos del formulario para `POST /cobros/{id}/pagos` (multipart, convenciones §6.13). */
export type RegistrarPagoPeticion = {
  cobroId: string
  medio: MedioPago
  /** Debe ser igual al monto del cobro; se envía con dos decimales. */
  monto: number
  /** Día `AAAA-MM-DD`, no futuro en `America/Mexico_City`; sin él cuenta el momento del registro. */
  fechaPago?: string
  /** Hasta 60 caracteres, ya recortada. */
  referencia?: string
  /** Comprobante JPEG, PNG, WebP o PDF de hasta 5 MB. */
  archivo: File
}

/** Metadatos del comprobante; la API nunca expone bucket, clave ni URL (§7). */
export type ComprobantePago = {
  documentoId: string
  tipoMime: string
  tamanoBytes: number
}

/** Pago registrado de un cobro (RF-139). */
export type Pago = {
  id: string
  cobroId: string
  medio: MedioPago
  /** BigDecimal con dos decimales; JSON lo entrega como número. */
  monto: number
  /** Se omite si no se capturó. */
  referencia?: string
  /** Instante del pago con desfase, p. ej. `2026-11-05T00:00:00-06:00`. */
  pagadoEn: string
  /** `nombre` se omite si el perfil no es visible para quien consulta. */
  registradoPor: { id: string; nombre?: string }
  comprobante: ComprobantePago
}

/** Club disponible para filtrar o crear un cobro. */
export type ClubResumen = {
  id: string
  nombre: string
}

/** Equipo que puede recibir un cobro; solo se consulta con `organizacion.equipo:CONSULTAR`. */
export type EquipoResumen = {
  id: string
  nombre: string
  club: ReferenciaCobro
  estado: 'ACTIVO' | 'BAJA'
}

/** Suma de adeudos por club y equipo (RF-140). */
export type Adeudos = {
  total: number
  pendiente: number
  vencido: number
  cobros: number
  clubes: ClubAdeudo[]
}

export type ClubAdeudo = {
  club: ReferenciaCobro
  total: number
  pendiente: number
  vencido: number
  cobros: number
  equipos: EquipoAdeudo[]
}

export type EquipoAdeudo = {
  equipo: ReferenciaCobro
  total: number
  pendiente: number
  vencido: number
  cobros: number
}

/** Respuesta paginada de `GET /clubes`. */
export type PaginaClubes = Pagina<ClubResumen>

/** Respuesta paginada de `GET /equipos`. */
export type PaginaEquipos = Pagina<EquipoResumen>
