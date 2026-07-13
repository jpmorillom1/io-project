package jpap.dev.io_api.domain.entera;

import jpap.dev.io_api.domain.common.ModeloResoluble;
import jpap.dev.io_api.domain.lp.ModeloLP;

import java.util.List;

/**
 * Modelo de Programación Lineal Entera (PLE / MILP).
 *
 * Reutiliza {@link ModeloLP} como <em>relajación lineal</em> (variables, función objetivo y
 * restricciones) y añade, por cada variable, su {@link TipoVariable}. El solver de
 * Branch &amp; Bound resuelve la relajación con el motor Simplex existente y ramifica sobre
 * las variables ENTERA/BINARIA que resulten fraccionarias.
 *
 * {@code tiposVariable} está alineado por índice con {@code relajacion.variables()}.
 */
public record ModeloEntero(
        ModeloLP relajacion,
        List<TipoVariable> tiposVariable
) implements ModeloResoluble {}
