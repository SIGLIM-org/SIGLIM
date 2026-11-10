# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

SIGLIM sirve a la **Liga Municipal de Futbol Amateur Morelia**: cerca de 200 clubes, más de 600
equipos, 40 canchas en su unidad deportiva y 5,000 a 6,000 asistentes por fin de semana. Tiene 12
tipos de usuario (ERS §2.3); cada uno ve solo lo de su ámbito (liga, club o propio).

Dos escenarios pesan más cuando una decisión de diseño obliga a elegir (confirmado por el equipo):

1. **En cancha, con celular, en fin de semana.** El Capturista llena la cédula del partido
   (alineación, marcador, goles, tarjetas, incidencias, sustituciones) y el Árbitro la aprueba o la
   devuelve, en la unidad deportiva, con prisa, a pleno sol y con señal irregular. De esa cédula
   sale el resultado oficial.
2. **Liga en oficina, con escritorio, entre semana.** Secretario, Programador de partidos,
   Presidente, Tesorero y las Comisiones trabajan con tablas densas: validan documentos e
   identidad, arman jornadas sin conflictos, aprueban, resuelven expedientes y cobran.

También lo usan, con menor peso en las decisiones: la **Directiva de club** (registra jugadores,
sube documentos y paga adeudos, a menudo desde el celular), el **Jugador o su tutor** (consulta su
información) y el **público** (consulta el portal de solo lectura).

## Product Purpose

Administrar el ciclo completo de una temporada de la Liga y publicar solo lo autorizado. Sustituye
una operación hoy **dispersa y en papel** (tarjetones, cédulas arbitrales y expedientes físicos;
ERS §1.1).

El éxito se ve así: resultados oficializados con una cédula validada por el árbitro; jugadores
registrados en dos clubes («cachirules») detectados por CURP; sanciones aplicadas según el
reglamento con trazabilidad; y un portal que muestra solo lo publicado.

## Positioning

Es el sistema de **una liga concreta**, construido sobre sus procesos reales y no sobre un modelo
genérico de torneos: cédula con validación arbitral, separación entre quien captura y quien valida,
disciplina por artículos del reglamento, control de identidad por CURP y reglas deportivas
configurables por temporada. La programación de partidos es manual: el sistema detecta y explica
los conflictos, pero no genera calendarios.

## Operating Context

- Estructura de la competencia: Temporada → Categoría → Grupo → Equipos → Partidos.
- Flujos con estado visible y acciones permitidas por estado: partido, cédula, registro,
  expediente, cobro y publicación (Borrador, Revisión, Publicado).
- Control dual: capturar ≠ validar la cédula; programar ≠ aprobar; resolver ≠ resolver el recurso;
  redactar ≠ aprobar comunicados; cambiar permisos ≠ aprobar pares incompatibles.
- Toda transición crítica lleva motivo y queda auditada.
- Se usa en navegador de escritorio y móvil. Las fotos de documentos se toman con la cámara o los
  archivos del dispositivo.
- Por confirmar con la Liga: si hoy también usan Excel, Facebook o WhatsApp para operar o publicar.
  Solo el papel está confirmado.

## Capabilities and Constraints

- 14 módulos (ERS §2.2). Cada requisito es núcleo (N), backlog (B) o fuera de alcance (F).
- **Fuera de alcance:** programación automática, pasarela de pago real (el pago es simulado),
  reconocimiento facial, WhatsApp/SMS, mapas, API pública, inicio de sesión con proveedores
  externos, importación masiva de históricos y varias ligas en operación.
- Las reglas deportivas no aprobadas son **[DEMO]**: se configuran, no se fijan en código, y la
  interfaz las rotula como «no oficial».
- La interfaz debe: mostrar la causa de cada bloqueo y de cada conflicto de programación;
  distinguir los datos oficiales de los indicadores calculados; y ocultar lo que el usuario no
  puede ver o hacer, aunque el backend sea la barrera real.
- El portal nunca muestra datos personales; los nombres de jugadores solo aparecen con su
  consentimiento.
- Las tareas clave (capturar la cédula, aprobarla, registrar un jugador, pagar un cobro) se hacen en
  tres pasos o menos desde el panel (RNF-006).
- Los requisitos siguen en revisión: el ERS (IEEE 830, Rev. 0.4.1) aún no lo aprueba la Liga.
- Stack ya decidido en el código: React 19 con TypeScript, Vite y Tailwind CSS 4 en el frontend;
  Spring Boot en el backend.

## Brand Commitments

- Nombres: **SIGLIM** y **Liga Municipal de Futbol Amateur Morelia**.
- Idioma: español de México. Términos de la Liga: cédula, tarjetón, jornada, cachirules,
  castigados, liguilla, comodato.
- Voz: institucional y sobria. Los mensajes dicen qué pasó, por qué y qué hacer, sin jerga técnica.
- La autoridad visual es [`DESIGN.md`](DESIGN.md) (raíz del repositorio), aprobada por el
  equipo junto con las pantallas de `docs/diseno-ui/pantallas/`. No se crea otro DESIGN.md.

## Evidence on Hand

- ERS: [`docs/requisitos/ERS.md`](docs/requisitos/ERS.md). Arquitectura y ADR en
  `docs/arquitectura/`. Base de datos en `supabase/migrations/`.
- Prototipo aprobado de las pantallas F-01 a F-35 en `docs/diseno-ui/pantallas/`, con sus
  diferencias (D-xx) de [`pantallas/README.md` §3](docs/diseno-ui/pantallas/README.md) y conflictos C-01 a C-08, que no se implementan.
- **No existen** y no se deben inventar: logotipo oficial de la Liga, datos reales de clubes,
  jugadores o partidos, testimonios ni cifras de uso. Los datos de las capturas («Liga Apex»,
  «Halcones», «Temporada 2024/25») son de ejemplo.

## Product Principles

1. **Lo oficial se distingue.** Un resultado validado, un dato calculado y un valor [DEMO] nunca se
   ven iguales.
2. **Todo bloqueo se explica.** Si algo no se puede hacer, la interfaz dice la causa, el dato que
   choca y qué hacer.
3. **El estado manda.** Cada trámite muestra en qué estado está y solo las acciones que ese estado
   y los permisos del usuario permiten.
4. **La cancha no espera.** La captura y la validación de la cédula funcionan a una mano en un
   celular, con prisa y con mala señal.
5. **Se ve solo lo propio.** Cada usuario ve su ámbito y el portal nunca expone datos personales.

## Accessibility & Inclusion

- WCAG 2.1 nivel AA en las pantallas del núcleo (RNF-008).
- Todas las pantallas son operables sin desplazamiento horizontal desde 360 px (RNF-007).
- Los estados nunca dependen solo del color: llevan ícono y texto.
- Hay usuarios con manejo básico del teléfono (jugadores, tutores, público) y personal que usa la
  aplicación con prisa en cancha.
