package mx.tecnm.morelia.siglim.compartido.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del sistema en UTC. Se inyecta en lugar de llamar {@code now()} directo para poder fijar
 * la hora en las pruebas (backend/AGENTS.md).
 *
 * <p>Refs: T-04, ADR-0009
 */
@Configuration(proxyBeanMethods = false)
class RelojConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
