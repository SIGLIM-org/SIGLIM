package mx.tecnm.morelia.siglim.compartido.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas ({@code @Scheduled}) de los módulos (ADR-0009 punto 1).
 *
 * <p>Cada tarea vive en el servicio de su módulo, corre como el perfil Sistema con
 * {@code compartido.contexto.EjecutorSistema} y se configura bajo {@code siglim.tareas.*}
 * ({@code cron} y {@code habilitada}). El planificador de Spring Boot usa un solo hilo: las tareas no
 * se traslapan entre sí, y cada una es idempotente por estado aunque se repita.
 *
 * <p>Refs: B-31.1, ADR-0009
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class TareasConfig {
}
