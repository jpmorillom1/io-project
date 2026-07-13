package jpap.dev.io_api.domain.dinamica;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validación de entrada de los modelos de Programación Dinámica. Una entrada malformada
 * (parámetro faltante, fuera de rango o incoherente) es un error del cliente y lanza
 * IllegalArgumentException.
 *
 * La infactibilidad NUNCA lanza: que no exista una ruta al destino o que la capacidad de
 * producción no alcance a cubrir la demanda son resultados válidos (SolveStatus.INFACTIBLE).
 */
public final class DinamicaValidador {

    private DinamicaValidador() {}

    public static void validarAsignacionRecursos(ModeloDinamico m) {
        exigirModelo(m);
        if (m.recursoTotal() == null || m.recursoTotal() < 0) {
            throw new IllegalArgumentException("El recurso total a repartir (recursoTotal) debe ser un entero >= 0.");
        }
        if (m.actividades() == null || m.actividades().isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos una actividad (actividades).");
        }
        int esperado = m.recursoTotal() + 1;
        for (ActividadRecurso a : m.actividades()) {
            exigirNombre(a == null ? null : a.nombre(), "cada actividad");
            if (a.retornos() == null || a.retornos().size() != esperado) {
                throw new IllegalArgumentException(String.format(
                        "La actividad '%s' debe traer exactamente %d retornos (uno por cada asignación "
                        + "posible x = 0..%d); trae %s.",
                        a.nombre(), esperado, m.recursoTotal(),
                        a.retornos() == null ? "ninguno" : String.valueOf(a.retornos().size())));
            }
            if (a.retornos().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Los retornos de la actividad '" + a.nombre() + "' no pueden contener nulos.");
            }
        }
    }

    public static void validarMochila(ModeloDinamico m) {
        exigirModelo(m);
        exigirSentido(m, SentidoOptimizacion.MAXIMIZAR, "la mochila siempre maximiza el valor cargado");
        if (m.capacidad() == null || m.capacidad() < 0) {
            throw new IllegalArgumentException("La capacidad de la mochila debe ser un entero >= 0.");
        }
        if (m.articulos() == null || m.articulos().isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos un artículo (articulos).");
        }
        for (ArticuloMochila a : m.articulos()) {
            exigirNombre(a == null ? null : a.nombre(), "cada artículo");
            if (a.peso() <= 0) {
                throw new IllegalArgumentException(
                        "El peso del artículo '" + a.nombre() + "' debe ser un entero positivo.");
            }
            if (a.unidadesMaximas() != null && a.unidadesMaximas() < 1) {
                throw new IllegalArgumentException(
                        "unidadesMaximas del artículo '" + a.nombre() + "' debe ser >= 1 (omítelo para el caso 0/1).");
            }
        }
    }

    public static void validarRutaEtapas(ModeloDinamico m) {
        exigirModelo(m);
        List<EtapaRuta> etapas = m.etapasRuta();
        if (etapas == null || etapas.size() < 2) {
            throw new IllegalArgumentException(
                    "Una red por etapas necesita al menos 2 etapas (origen y destino) en etapasRuta.");
        }
        Set<Integer> numeros = new HashSet<>();
        Set<String> nodos = new HashSet<>();
        for (EtapaRuta e : etapas) {
            if (e == null || e.nodos() == null || e.nodos().isEmpty()) {
                throw new IllegalArgumentException("Cada etapa debe declarar al menos un nodo.");
            }
            if (!numeros.add(e.etapa())) {
                throw new IllegalArgumentException("La etapa " + e.etapa() + " está declarada dos veces.");
            }
            for (String n : e.nodos()) {
                exigirNombre(n, "cada nodo");
                if (!nodos.add(n)) {
                    throw new IllegalArgumentException(
                            "El nodo '" + n + "' aparece en más de una etapa; los nombres deben ser únicos.");
                }
            }
        }
        for (int k = 1; k <= etapas.size(); k++) {
            if (!numeros.contains(k)) {
                throw new IllegalArgumentException(
                        "Las etapas deben numerarse consecutivamente desde 1; falta la etapa " + k + ".");
            }
        }
        EtapaRuta primera = etapas.stream().filter(e -> e.etapa() == 1).findFirst().orElseThrow();
        if (primera.nodos().size() != 1) {
            throw new IllegalArgumentException(
                    "La etapa 1 debe tener exactamente un nodo: el origen del recorrido.");
        }
        if (m.arcos() == null || m.arcos().isEmpty()) {
            throw new IllegalArgumentException("Debes indicar los arcos de la red (arcos).");
        }
        for (ArcoRuta arco : m.arcos()) {
            if (arco == null) throw new IllegalArgumentException("Hay un arco nulo en la lista.");
            int etapaOrigen = etapaDe(etapas, arco.origen());
            int etapaDestino = etapaDe(etapas, arco.destino());
            if (etapaOrigen == 0) {
                throw new IllegalArgumentException(
                        "El arco declara el origen '" + arco.origen() + "', que no pertenece a ninguna etapa.");
            }
            if (etapaDestino == 0) {
                throw new IllegalArgumentException(
                        "El arco declara el destino '" + arco.destino() + "', que no pertenece a ninguna etapa.");
            }
            if (etapaDestino != etapaOrigen + 1) {
                throw new IllegalArgumentException(String.format(
                        "El arco %s -> %s va de la etapa %d a la %d; en una red por etapas cada arco debe "
                        + "avanzar exactamente una etapa.",
                        arco.origen(), arco.destino(), etapaOrigen, etapaDestino));
            }
        }
    }

    public static void validarPlanificacionProduccion(ModeloDinamico m) {
        exigirModelo(m);
        exigirSentido(m, SentidoOptimizacion.MINIMIZAR, "la planificación de producción minimiza el costo total");
        if (m.demandas() == null || m.demandas().isEmpty()) {
            throw new IllegalArgumentException("Debes indicar la demanda de cada periodo (demandas).");
        }
        for (Integer d : m.demandas()) {
            if (d == null || d < 0) {
                throw new IllegalArgumentException("Cada demanda por periodo debe ser un entero >= 0.");
            }
        }
        exigirNoNegativo(m.costoPreparacion(), "el costo de preparación por lote (costoPreparacion)");
        exigirNoNegativo(m.costoUnitarioProduccion(), "el costo unitario de producción (costoUnitarioProduccion)");
        exigirNoNegativo(m.costoMantener(), "el costo de mantener una unidad un periodo (costoMantener)");
        exigirEnteroNoNegativo(m.capacidadProduccion(), "capacidadProduccion");
        exigirEnteroNoNegativo(m.capacidadAlmacen(), "capacidadAlmacen");
        exigirEnteroNoNegativo(m.inventarioInicial(), "inventarioInicial");
        exigirEnteroNoNegativo(m.inventarioFinal(), "inventarioFinal");
    }

    public static void validarReemplazoEquipos(ModeloDinamico m) {
        exigirModelo(m);
        exigirSentido(m, SentidoOptimizacion.MAXIMIZAR, "el reemplazo de equipos maximiza el ingreso neto");
        if (m.horizonteAnios() == null || m.horizonteAnios() < 1) {
            throw new IllegalArgumentException("El horizonte de planeación (horizonteAnios) debe ser >= 1.");
        }
        if (m.edadMaxima() == null || m.edadMaxima() < 1) {
            throw new IllegalArgumentException("La edad máxima del equipo (edadMaxima) debe ser >= 1.");
        }
        if (m.edadInicial() != null && (m.edadInicial() < 0 || m.edadInicial() > m.edadMaxima())) {
            throw new IllegalArgumentException(
                    "La edad inicial del equipo debe estar entre 0 y edadMaxima (" + m.edadMaxima() + ").");
        }
        exigirNoNegativo(m.costoCompra(), "el precio de un equipo nuevo (costoCompra)");
        if (m.tablaEdades() == null || m.tablaEdades().isEmpty()) {
            throw new IllegalArgumentException("Debes indicar la tabla de ingresos/costos por edad (tablaEdades).");
        }
        Set<Integer> edades = new HashSet<>();
        for (DatosEdadEquipo fila : m.tablaEdades()) {
            if (fila == null) throw new IllegalArgumentException("Hay una fila nula en tablaEdades.");
            if (fila.edad() < 0 || fila.edad() > m.edadMaxima()) {
                throw new IllegalArgumentException(
                        "tablaEdades trae la edad " + fila.edad() + ", fuera del rango 0.." + m.edadMaxima() + ".");
            }
            if (!edades.add(fila.edad())) {
                throw new IllegalArgumentException("La edad " + fila.edad() + " está declarada dos veces en tablaEdades.");
            }
        }
        for (int e = 0; e <= m.edadMaxima(); e++) {
            if (!edades.contains(e)) {
                throw new IllegalArgumentException(
                        "tablaEdades debe cubrir todas las edades 0.." + m.edadMaxima() + "; falta la edad " + e + ".");
            }
        }
    }

    /** Etapa a la que pertenece un nodo, o 0 si no pertenece a ninguna. */
    private static int etapaDe(List<EtapaRuta> etapas, String nodo) {
        for (EtapaRuta e : etapas) {
            if (e.nodos().contains(nodo)) return e.etapa();
        }
        return 0;
    }

    private static void exigirModelo(ModeloDinamico m) {
        if (m == null) throw new IllegalArgumentException("El modelo de programación dinámica es obligatorio.");
    }

    /** Los submodelos con sentido fijo rechazan el sentido contrario en lugar de ignorarlo en silencio. */
    private static void exigirSentido(ModeloDinamico m, SentidoOptimizacion esperado, String razon) {
        if (m.sentido() != null && m.sentido() != esperado) {
            throw new IllegalArgumentException(String.format(
                    "El sentido de este modelo es %s: %s. Omite 'sentido' o indícalo como %s.",
                    esperado, razon, esperado));
        }
    }

    private static void exigirNombre(String nombre, String que) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Debes darle un nombre a " + que + ".");
        }
    }

    private static void exigirNoNegativo(Double valor, String nombre) {
        if (valor == null || valor < 0) {
            throw new IllegalArgumentException("Debes indicar " + nombre + " con un valor >= 0.");
        }
    }

    private static void exigirEnteroNoNegativo(Integer valor, String nombre) {
        if (valor != null && valor < 0) {
            throw new IllegalArgumentException("El campo " + nombre + " no puede ser negativo.");
        }
    }
}
