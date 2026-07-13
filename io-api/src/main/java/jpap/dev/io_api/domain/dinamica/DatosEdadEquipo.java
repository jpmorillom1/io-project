package jpap.dev.io_api.domain.dinamica;

/**
 * Fila de la tabla de un equipo según su edad, para el modelo REEMPLAZO_EQUIPOS.
 * El estado de la recursión es precisamente la edad.
 *
 * @param edad           edad del equipo en años (0 = equipo nuevo)
 * @param ingreso        ingreso anual que genera el equipo a esa edad
 * @param costoOperacion costo anual de operarlo y mantenerlo a esa edad
 * @param valorRescate   valor de reventa del equipo a esa edad
 */
public record DatosEdadEquipo(
        int edad,
        double ingreso,
        double costoOperacion,
        double valorRescate
) {}
