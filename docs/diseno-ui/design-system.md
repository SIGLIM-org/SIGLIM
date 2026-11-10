# Sistema de Diseño - SIGLIM (Liga Apex)

> [!WARNING]
> **Reemplazado** por [`DESIGN.md`](../../DESIGN.md) (prototipo aprobado el 20 de octubre de 2026). Se conserva como referencia de la primera ronda de capturas.

## 1. Personalidad Visual y Carácter
- **Nivel de formalidad**: Alto (sistema oficial de federación deportiva).
- **Carácter**: Profesional, dinámico, centrado en datos deportivos en tiempo real y gestión administrativa rigurosa.
- **Identidad de Marca**: LIGA APEX combina la solemnidad institucional con la emoción del fútbol profesional.

## 2. Elementos Constantes en Todas las Pantallas
- **Cabecera Global (Global Header)**: Logo institucional "LIGA APEX", selector de temporada ("Temporada 2024/25"), barra de navegación principal, selector de división ("Primera División"), buscador global y botón de acceso al portal de gestión / perfil de usuario.
- **Pie de Página (Footer)**: Copyright oficial, enlaces a Reglamento Oficial, Comité de Competición y Prensa y Medios.

## 3. Principios de Arquitectura Frontend (React)
- **Modularidad**: Componentes atómicos basados en Atomic Design o estructura por dominios funcionales.
- **Estilos**: Uso de Tailwind CSS o CSS Modules siguiendo rigurosamente los tokens de diseño documentados.
- **Tipado**: TypeScript estricto para props y modelos de datos (Clubes, Partidos, Jugadores, Clasificación).