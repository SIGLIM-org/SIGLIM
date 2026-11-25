<div align="center">

# ⚽ SIGLIM

### Sistema de Gestión de la Liga Municipal de Futbol Amateur Morelia

Plataforma web para configurar temporadas, programar partidos, oficializar resultados
y publicar la tabla de posiciones de la Liga, con trazabilidad de cada cambio.

![Estado](https://img.shields.io/badge/estado-en%20desarrollo-f59e0b?style=for-the-badge)
![ERS](https://img.shields.io/badge/ERS-IEEE%20830%20·%20Rev.%200.4-006837?style=for-the-badge)
![Scrum](https://img.shields.io/badge/metodología-Scrum-0f172a?style=for-the-badge)

![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?style=flat-square&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?style=flat-square&logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-4-06B6D4?style=flat-square&logo=tailwindcss&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-3ECF8E?style=flat-square&logo=supabase&logoColor=white)

**Instituto Tecnológico de Morelia** · Ingeniería en Sistemas Computacionales · Fundamentos de Ingeniería de Software

[Documentación](docs/README.md) · [ERS](docs/requisitos/ERS.md) · [Arquitectura](docs/arquitectura/arquitectura-y-diseno-jerarquico.md) · [Base de datos](docs/base-de-datos/diccionario-de-datos.md) · [Diseño UI](docs/diseno-ui/README.md) · [Cómo contribuir](CONTRIBUTING.md)

</div>

---

## 📌 ¿Qué es SIGLIM?

La Liga Municipal de Futbol Amateur Morelia organiza su operación en
**Temporada → Categoría → Grupo → Equipos → Partidos**. SIGLIM digitaliza ese flujo en una
sola aplicación web con **dos caras**:

| | Aplicación interna | Portal público |
| --- | --- | --- |
| **Quién** | 11 tipos de usuario: autoridades y comisiones de la Liga, programación, captura, clubes, árbitros y jugadores | Cualquier visitante |
| **Acceso** | Inicio de sesión y permisos por rol y ámbito (liga, club o propio) | Sin cuenta, solo lectura |
| **Qué hace** | Afilia, registra, programa, captura y valida la cédula, sanciona, cobra y publica | Consulta programación, resultados, tablas, comunicados y sanciones |

La programación y los comunicados llegan al portal **después de la aprobación del Presidente**;
los resultados se publican al aprobarse la cédula. No existe forma de editar datos desde el portal.

## 🔄 Flujo central de la entrega

```mermaid
flowchart LR
    A[🗓️ Temporada] --> B[🛡️ Afiliación de clubes]
    B --> C[🧑 Registro y validación de jugadores]
    C --> D[📋 Programación y designación]
    D --> E[⚽ Partido]
    E --> F[📝 Cédula validada por el árbitro]
    F --> G[🔢 Resultado oficial]
    G --> H[📊 Tabla]
    H --> I[🌐 Publicación]
    F --> J[⚖️ Disciplina]
    D --> K[💰 Cobros]
    style I fill:#006837,color:#fff
```

Cada cambio crítico queda registrado con **usuario, fecha y motivo** (auditoría).

## 🧩 Módulos

| ID | Módulo | Qué cubre |
| --- | --- | --- |
| M-01 | Administración y seguridad | Usuarios, roles, permisos con ámbito y control dual, consentimiento, auditoría |
| M-02 | Temporadas y normativa | Temporada, categorías con jerarquía, grupos y parámetros deportivos |
| M-03 | Organización deportiva | Clubes, afiliación, directiva, equipos, venta y plaza, personas y tutores |
| M-04 | Registros y elegibilidad | Registro por CURP, documentos, validación, elegibilidad y alineación |
| M-05 | Árbitros y designaciones | Árbitros de los clubes, validación y designación |
| M-06 | Canchas y recursos | Canchas de liga y de clubes, préstamos, ofertas, disponibilidad |
| M-07 | Programación manual | Jornadas, conflictos, localía, aprobación y publicación |
| M-08 | Partidos, cédula y resultados | Estados, cédula validada, resultado oficial, tabla y estadísticas |
| M-09 | Disciplina | Catálogos, expedientes, sanciones, bajas y recursos |
| M-10 | Competencias y fases finales | Liguilla, ascensos y descensos |
| M-11 | Portal público y publicación | Programación, resultados, tablas, comunicados y sanciones |
| M-12 | Reportes y documentos | Reportes y exportación PDF/Excel |
| M-13 | Tesorería y cobros | Tarifas, cobros, pago simulado y adeudos |
| M-14 | Notificaciones | Avisos en el sistema y por correo |

Cada requisito del [ERS Rev. 0.4](docs/requisitos/ERS.md) indica si es núcleo comprometido (N),
backlog priorizado (B) o fuera del alcance (F, sección 2.6: pasarela de pago real, integraciones no
aprobadas). La programación automática, que el ERS deja fuera, se reabrió con DEC-173
([diseño](docs/planning/programacion-automatica.md)).

## 🏗️ Arquitectura

Cliente-servidor de tres niveles: **SPA en React**, **monolito modular en Spring Boot** con un
paquete por módulo, **PostgreSQL y Auth administrados por Supabase** y **AWS S3** para imágenes y
documentos.

```mermaid
flowchart TB
    subgraph Navegador["🖥️ Navegador (escritorio / móvil)"]
        P[Portal público<br/>solo lectura]
        I[Aplicación interna<br/>autenticada]
    end
    subgraph API["☕ Spring Boot · 14 módulos"]
        S[Filtro de seguridad<br/>JWT] --> C[Controladores REST]
        C --> SV[Servicios<br/>permiso y ámbito · reglas · transacción · auditoría · eventos]
        SV --> R[Repositorios JDBC]
        T[Tareas programadas] --> SV
    end
    subgraph SB["☁️ Supabase"]
        AUTH[Auth<br/>emite JWT]
        DB[(PostgreSQL · esquema siglim<br/>RLS · historial · vistas públicas)]
    end
    ST[(AWS S3 privado<br/>imágenes y documentos)]
    MAIL[[Correo SMTP]]
    I -- login --> AUTH
    P -- HTTPS / REST --> C
    I -- HTTPS / REST + JWT --> S
    S -. valida firma con JWKS .-> AUTH
    R -- JDBC / TLS --> DB
    SV -- carga y URL prefirmadas --> ST
    SV -- bandeja de salida --> MAIL
```

El detalle (HIPO de los 14 módulos, dependencias entre módulos, secuencias, seguridad y respaldo) está en
[Arquitectura y diseño jerárquico](docs/arquitectura/arquitectura-y-diseno-jerarquico.md)
y en los [ADR](docs/arquitectura/adr/).

## 📁 Estructura del repositorio

```text
siglim/
├── README.md                 ← estás aquí
├── AGENTS.md                 ← reglas para agentes de IA (Claude, Codex, Copilot, Cursor…)
├── CLAUDE.md                 ← puente para Claude Code (importa AGENTS.md)
├── CONTRIBUTING.md           ← ramas, commits, PR y Definición de Terminado
│
├── docs/                     ← fuente de verdad del proyecto
│   ├── requisitos/           ← ERS (IEEE 830), BPMN, decisiones, fuentes e histórico
│   ├── arquitectura/         ← arquitectura y HIPO, convenciones de API, ADR e histórico
│   ├── base-de-datos/        ← diccionario de datos (+ histórico)
│   └── diseno-ui/            ← sistema de diseño, tokens, componentes, accesibilidad
│
├── supabase/
│   └── migrations/           ← esquema SQL versionado (única fuente del esquema)
│
├── frontend/                 ← React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 4
└── backend/                  ← Spring Boot 4.1 + Java 21 (monolito modular)
```

## 🚀 Puesta en marcha

### Requisitos previos

| Herramienta | Versión |
| --- | --- |
| Node.js | 22 LTS o superior |
| JDK | 21 (Temurin recomendado) |
| Git | 2.40+ |
| Cuenta de Supabase | Proyecto creado con el esquema de `supabase/migrations` aplicado |

### 1. Clonar

```bash
git clone https://github.com/<organizacion>/siglim.git
cd siglim
```

### 2. Base de datos

Ejecuta `supabase/migrations/20261020090000_esquema_inicial.sql` en el **SQL Editor** de tu
proyecto de Supabase (o `supabase db push` si usan la CLI) y registra al administrador inicial
con `siglim.registrar_administrador_inicial('correo')`. Detalles en
[`supabase/README.md`](supabase/README.md).

### 3. Backend

```bash
cd backend
cp .env.example .env          # llena las variables de Supabase
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

API en `http://localhost:8080` · salud en `http://localhost:8080/actuator/health`

### 4. Frontend

```bash
cd frontend
cp .env.example .env.local    # llena las variables de Supabase y la URL del API
npm install
npm run dev
```

Aplicación en `http://localhost:5173`

## 🤖 Desarrollo asistido por IA

Este repositorio está preparado para trabajar con agentes de programación. Antes de pedirle algo
a una IA, asegúrate de que lea [`AGENTS.md`](AGENTS.md): ahí están las reglas del proyecto, el
orden de prioridad de los documentos y lo que **no** se debe implementar todavía.

## 👥 Equipo

| Integrante | Rol Scrum | Responsabilidad principal |
| --- | --- | --- |
| **Olvera Lobato David Geovani** | Product Owner | Backlog, historias de usuario y validación de requisitos |
| **López Meza Leonardo** | Scrum Master | Ceremonias, tablero, pruebas y verificación de entregables |
| **Olvera Bucio Jose Israel** | Desarrollo · Arquitectura y datos | Arquitectura, diseño de datos, repositorio y entorno |

**Profesor:** Claudio Ernesto Florian Arenas — Fundamentos de Ingeniería de Software

## 📄 Estado del proyecto

Proyecto académico en desarrollo. Los requisitos del ERS **aún no están aprobados formalmente
por la Liga**; lo marcado como `[PENDIENTE]` no forma parte del alcance hasta que se decida.

<div align="center">
<sub>Instituto Tecnológico de Morelia · 2026</sub>
</div>
