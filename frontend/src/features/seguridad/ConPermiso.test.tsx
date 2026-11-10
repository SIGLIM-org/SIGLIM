import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConPermiso } from '@/features/seguridad/ConPermiso'
import type { Permiso } from '@/features/seguridad/tipos'

const { sesion } = vi.hoisted(() => ({
  sesion: {
    yo: {
      perfil: { id: 'perfil-1', nombreMostrado: 'Ana Pérez', correo: null },
      asignaciones: [],
      permisos: [] as Permiso[],
    },
  },
}))

vi.mock('@/features/seguridad/ProveedorSesionActual', () => ({
  useSesionActual: () => sesion,
}))

function permiso(codigo: string, modulo: string): Permiso {
  return { codigo, modulo, ambito: 'LIGA', clubId: null }
}

describe('ConPermiso', () => {
  beforeEach(() => {
    sesion.yo.permisos = []
  })

  it('H-02.4: muestra la acción cuando la sesión tiene el permiso', () => {
    sesion.yo.permisos = [permiso('partidos.partido:CREAR', 'M-08')]

    render(
      <ConPermiso permiso="partidos.partido:CREAR">
        <button type="button">Crear partido</button>
      </ConPermiso>,
    )

    expect(
      screen.getByRole('button', { name: 'Crear partido' }),
    ).toBeInTheDocument()
  })

  it('H-02.4: oculta la acción cuando falta el permiso', () => {
    render(
      <ConPermiso permiso="partidos.partido:CREAR">
        <button type="button">Crear partido</button>
      </ConPermiso>,
    )

    expect(
      screen.queryByRole('button', { name: 'Crear partido' }),
    ).not.toBeInTheDocument()
  })

  it('H-02.4: basta con uno de los permisos indicados', () => {
    sesion.yo.permisos = [permiso('partidos.partido:EDITAR', 'M-08')]

    render(
      <ConPermiso
        permiso={['partidos.partido:CREAR', 'partidos.partido:EDITAR']}
      >
        <button type="button">Editar partido</button>
      </ConPermiso>,
    )

    expect(
      screen.getByRole('button', { name: 'Editar partido' }),
    ).toBeInTheDocument()
  })

  it('H-02.4: muestra la alternativa cuando no hay permiso', () => {
    render(
      <ConPermiso
        permiso="partidos.partido:CREAR"
        alternativa={<p>Sin permiso para crear partidos.</p>}
      >
        <button type="button">Crear partido</button>
      </ConPermiso>,
    )

    expect(
      screen.getByText('Sin permiso para crear partidos.'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
