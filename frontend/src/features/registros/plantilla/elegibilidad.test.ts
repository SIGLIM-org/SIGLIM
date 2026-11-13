import { CircleCheck, CircleX, Clock } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { elegibilidadDeRegistro } from '@/features/registros/plantilla/elegibilidad'
import type { EstadoRegistro } from '@/features/registros/tipos'

describe('elegibilidadDeRegistro (F-17, DEC-117)', () => {
  it.each<[EstadoRegistro, string, string, unknown]>([
    ['ACTIVO', 'Elegible', 'exito', CircleCheck],
    ['PENDIENTE_VALIDACION', 'En validación', 'advertencia', Clock],
    ['SUSPENDIDO', 'No elegible', 'error', CircleX],
    ['BAJA', 'No elegible', 'error', CircleX],
    ['RECHAZADO', 'No elegible', 'error', CircleX],
  ])('RF-017 · DEC-117: %s se muestra como «%s»', (estado, texto, variante, icono) => {
    expect(elegibilidadDeRegistro(estado)).toEqual({ texto, variante, icono })
  })
})
