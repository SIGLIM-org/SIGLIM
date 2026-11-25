package mx.tecnm.morelia.siglim.programacion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Calendario de un grupo por el método del círculo (tablas de Berger). Dominio puro: sin Spring,
 * sin base de datos y sin otros módulos.
 *
 * <ol>
 *   <li><b>Orden (DEC-185).</b> Los equipos se ordenan por el texto de su UUID, el mismo orden que
 *       {@code ORDER BY id} de PostgreSQL; así el resultado no depende del orden de entrada.</li>
 *   <li><b>Descanso fijo (DEC-184, RF-037).</b> Con N impar se agrega un DESCANSO al inicio del
 *       arreglo, en la posición fija; con N par el fijo es el primer equipo. El arreglo tiene M
 *       elementos (M par) y la vuelta tiene {@code M − 1} rondas.</li>
 *   <li><b>Emparejamiento.</b> En la ronda {@code r}, la mesa {@code i} enfrenta a {@code a[i]} con
 *       {@code a[M−1−i]}; después de cada ronda, {@code a[1..M−1]} rota una posición a la
 *       derecha.</li>
 *   <li><b>Localía (DEC-184).</b> En la mesa 0, {@code a[0]} es local en las rondas pares y
 *       visitante en las impares. En la mesa {@code i ≥ 1}, {@code a[i]} es local si {@code i} es
 *       impar y {@code a[M−1−i]} si es par. Como cada equipo que rota pasa por todas las posiciones
 *       una vez por vuelta y la localía depende solo de la paridad de la posición, la diferencia
 *       entre partidos de local y de visitante es como máximo 1 por vuelta.</li>
 *   <li><b>Vueltas (DEC-174).</b> La vuelta {@code v} repite las rondas con la localía invertida en
 *       las vueltas pares (los descansos no cambian); la fecha es {@code (v−1)·R + r + 1}.</li>
 *   <li><b>RN-012.</b> En cada fecha cada equipo aparece exactamente una vez, jugando o
 *       descansando.</li>
 * </ol>
 *
 * <p>Las pruebas de propiedades recorren N = 2…30 y todas las vueltas (DEC-183).
 *
 * <p>Refs: H-32.1, RN-012, RF-037, DEC-174, DEC-183, DEC-184, DEC-185, ADR-0015
 */
final class GeneradorCalendario {

    /** Máximo de vueltas: el mismo límite que {@code ck_parametros_vueltas}. */
    static final int VUELTAS_MAXIMAS = 4;

    /** Marca del descanso en el arreglo del círculo; nunca sale en el resultado. */
    private static final UUID DESCANSO = null;

    private GeneradorCalendario() {
    }

    /**
     * Fechas que ocupa una vuelta: {@code N − 1} con N par y {@code N} con N impar (por el descanso).
     *
     * @param equipos número de equipos del grupo, al menos 2
     */
    static int fechasPorVuelta(int equipos) {
        if (equipos < 2) {
            throw new IllegalArgumentException("Un grupo necesita al menos 2 equipos para tener calendario.");
        }
        return equipos % 2 == 0 ? equipos - 1 : equipos;
    }

    /**
     * Genera el calendario completo del grupo.
     *
     * @param equipos identificadores de los equipos, sin nulos ni repetidos, al menos 2; el orden no
     *                importa
     * @param vueltas número de vueltas, de 1 a {@link #VUELTAS_MAXIMAS}
     * @return lista inmutable ordenada por número de fecha y, dentro de la fecha, por mesa
     * @throws IllegalArgumentException si los equipos o las vueltas no son válidos
     */
    static List<Emparejamiento> generar(Collection<UUID> equipos, int vueltas) {
        List<UUID> ordenados = validarYOrdenar(equipos);
        if (vueltas < 1 || vueltas > VUELTAS_MAXIMAS) {
            throw new IllegalArgumentException(
                    "Las vueltas deben estar entre 1 y " + VUELTAS_MAXIMAS + "; se recibió " + vueltas + ".");
        }

        int n = ordenados.size();
        UUID[] a;
        if (n % 2 == 1) {
            a = new UUID[n + 1];
            a[0] = DESCANSO;
            for (int k = 0; k < n; k++) {
                a[k + 1] = ordenados.get(k);
            }
        } else {
            a = ordenados.toArray(UUID[]::new);
        }
        int m = a.length;
        int rondas = m - 1;

        // Rondas de la primera vuelta: [ronda][mesa] = {local, visitante}; visitante null = descanso.
        UUID[][][] primeraVuelta = new UUID[rondas][m / 2][];
        for (int r = 0; r < rondas; r++) {
            for (int i = 0; i < m / 2; i++) {
                UUID x = a[i];
                UUID y = a[m - 1 - i];
                if (x == DESCANSO) {
                    primeraVuelta[r][i] = new UUID[] {y, null};
                } else if (y == DESCANSO) {
                    primeraVuelta[r][i] = new UUID[] {x, null};
                } else {
                    boolean localX = i == 0 ? r % 2 == 0 : i % 2 == 1;
                    primeraVuelta[r][i] = localX ? new UUID[] {x, y} : new UUID[] {y, x};
                }
            }
            rotarALaDerecha(a);
        }

        List<Emparejamiento> calendario = new ArrayList<>(vueltas * rondas * (m / 2));
        for (int v = 1; v <= vueltas; v++) {
            boolean invertir = v % 2 == 0;
            for (int r = 0; r < rondas; r++) {
                int numeroFecha = (v - 1) * rondas + r + 1;
                for (UUID[] mesa : primeraVuelta[r]) {
                    UUID local = mesa[0];
                    UUID visitante = mesa[1];
                    if (invertir && visitante != null) {
                        calendario.add(new Emparejamiento(v, numeroFecha, visitante, local));
                    } else {
                        calendario.add(new Emparejamiento(v, numeroFecha, local, visitante));
                    }
                }
            }
        }
        return List.copyOf(calendario);
    }

    private static List<UUID> validarYOrdenar(Collection<UUID> equipos) {
        if (equipos == null) {
            throw new IllegalArgumentException("La lista de equipos es obligatoria.");
        }
        Set<UUID> vistos = new HashSet<>();
        for (UUID equipo : equipos) {
            if (equipo == null) {
                throw new IllegalArgumentException("La lista de equipos no puede tener elementos vacíos.");
            }
            if (!vistos.add(equipo)) {
                throw new IllegalArgumentException("El equipo " + equipo + " está repetido en la lista.");
            }
        }
        if (vistos.size() < 2) {
            throw new IllegalArgumentException(
                    "Un grupo necesita al menos 2 equipos para tener calendario; tiene " + vistos.size() + ".");
        }
        // DEC-185: se ordena por el texto del UUID y no con UUID.compareTo. compareTo compara los dos
        // long del UUID con signo, así que un id que empieza con 8–f queda antes que uno con 0–7; en
        // cambio PostgreSQL ordena uuid byte a byte sin signo, que es el orden del texto en minúsculas.
        // Con el texto, el calendario de Java coincide con ORDER BY id de la base.
        List<UUID> ordenados = new ArrayList<>(vistos);
        ordenados.sort(Comparator.comparing(UUID::toString));
        return ordenados;
    }

    /** {@code a = [a0, a[M−1], a1, a2, …, a[M−2]]}: el elemento 0 queda fijo. */
    private static void rotarALaDerecha(UUID[] a) {
        int ultimo = a.length - 1;
        UUID temporal = a[ultimo];
        System.arraycopy(a, 1, a, 2, ultimo - 1);
        a[1] = temporal;
    }
}
