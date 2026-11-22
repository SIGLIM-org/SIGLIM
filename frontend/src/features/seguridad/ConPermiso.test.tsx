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

  it('DEC-116: con todos, uno solo de los permisos no basta', () => {
    sesion.yo.permisos = [permiso('registros.registro:CONSULTAR', 'M-04')]

    render(
      <ConPermiso
        permiso={['registros.registro:CONSULTAR', 'organizacion.equipo:CONSULTAR']}
        todos
        alternativa={<p>Sin acceso a la plantilla.</p>}
      >
        <p>Plantilla</p>
      </ConPermiso>,
    )

    expect(screen.getByText('Sin acceso a la plantilla.')).toBeInTheDocument()
    expect(screen.queryByText('Plantilla')).not.toBeInTheDocument()
  })

  it('DEC-116: con todos y cada permiso indicado, muestra el contenido', () => {
    sesion.yo.permisos = [
      permiso('registros.registro:CONSULTAR', 'M-04'),
      permiso('organizacion.equipo:CONSULTAR', 'M-03'),
    ]

    render(
      <ConPermiso
        permiso={['registros.registro:CONSULTAR', 'organizacion.equipo:CONSULTAR']}
        todos
      >
        <p>Plantilla</p>
      </ConPermiso>,
    )

    expect(screen.getByText('Plantilla')).toBeInTheDocument()
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
