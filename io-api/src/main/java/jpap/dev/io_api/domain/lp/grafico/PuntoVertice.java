package jpap.dev.io_api.domain.lp.grafico;

public record PuntoVertice(
        double x,
        double y,
        double valorZ,
        boolean esOptimo,
        String etiqueta
) {}
