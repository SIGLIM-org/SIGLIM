package mx.tecnm.morelia.siglim.canchas;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Formatos 1 y 2 completos para las pruebas de los trámites de cancha (B-14). */
final class DatosSolicitudPrueba {

    static final String TELEFONO = "4431234567";

    private DatosSolicitudPrueba() {
    }

    static DatosPrestamo prestamo() {
        return prestamo(LocalTime.of(10, 0), LocalTime.of(12, 0));
    }

    static DatosPrestamo prestamo(LocalTime inicio, LocalTime termino) {
        return new DatosPrestamo("Morelia, Michoacán", DestinatarioPrestamo.DUENO_EXTERNO,
                "Ing. Laura Pérez", "Administración de la Unidad Deportiva", "Mario Ruiz",
                "Presidente del club", "Cancha 2, Unidad Deportiva", "Jornada de liga",
                LocalDate.of(2031, 9, 7), inicio, termino, "Partido oficial de la jornada 3",
                "Halcones contra Águilas", 80, "Mario Ruiz", TELEFONO, null);
    }

    static DatosOferta oferta() {
        return new DatosOferta("Morelia, Michoacán", "Rosa Gómez", "Propietaria", null,
                "Campo La Huerta", "Camino a La Huerta s/n", "Pasto natural",
                "Sábados y domingos de 8:00 a 14:00", 300, "Sanitarios y estacionamiento",
                "$400 por partido", "Pago al término de la jornada", "Rosa Gómez", TELEFONO, null);
    }

    static CrearSolicitudCanchaRequest prestamoDe(UUID club) {
        return new CrearSolicitudCanchaRequest(TipoSolicitudCancha.PRESTAMO, club, null, null, prestamo(), null);
    }

    static CrearSolicitudCanchaRequest ofertaExterna(String oferente) {
        return new CrearSolicitudCanchaRequest(TipoSolicitudCancha.OFERTA, null, oferente, null, null, oferta());
    }
}
