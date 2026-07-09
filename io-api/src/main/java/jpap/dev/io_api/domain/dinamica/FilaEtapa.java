package jpap.dev.io_api.domain.dinamica;

import java.util.List;

/**
 * Una fila de la tabla de una etapa: un estado, todas las decisiones admisibles evaluadas,
 * y cuál gana.
 *
 * Los estados inalcanzables NO producen fila: no se emiten (evita arrastrar infinitos
 * hasta la serialización JSON).
 *
 * @param estado         etiqueta del estado ("s = 3", "edad 2", "nodo B")
 * @param evaluaciones   una por decisión admisible desde ese estado
 * @param decisionOptima etiqueta de la decisión ganadora
 * @param valorOptimo    valor de la función de recurrencia en este estado y etapa
 */
public record FilaEtapa(
        String estado,
        List<EvaluacionDecision> evaluaciones,
        String decisionOptima,
        double valorOptimo
) {}
