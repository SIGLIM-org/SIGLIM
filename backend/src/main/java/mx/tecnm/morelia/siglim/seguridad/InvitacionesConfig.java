package mx.tecnm.morelia.siglim.seguridad;

import mx.tecnm.morelia.siglim.compartido.config.PropiedadesInvitacion;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Adaptador HTTP de las invitaciones a Supabase Auth (ADR-0003 punto 5, RF-094).
 *
 * <p>La configuración de las invitaciones se habilita aquí y no en {@link SeguridadConfig} para que
 * las pruebas de controlador no exijan la clave secreta: sustituyen el servicio y nunca llaman a
 * Supabase.
 *
 * <p>Refs: H-03.1, RF-094, ADR-0003
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesInvitacion.class)
class InvitacionesConfig {

    @Bean
    InvitadorAuth invitadorAuth(SiglimPropiedades propiedades, PropiedadesInvitacion invitaciones) {
        return new InvitacionSupabase(RestClient.builder(), propiedades.supabase().url(), invitaciones);
    }
}
