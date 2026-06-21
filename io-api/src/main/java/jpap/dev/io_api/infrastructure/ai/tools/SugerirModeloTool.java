package jpap.dev.io_api.infrastructure.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.output.structured.Description;
import jpap.dev.io_api.domain.lp.FuncionObjetivo;
import jpap.dev.io_api.domain.lp.ModeloLP;
import jpap.dev.io_api.domain.lp.Restriccion;
import jpap.dev.io_api.domain.lp.TipoObjetivo;
import jpap.dev.io_api.domain.lp.TipoRestriccion;
import jpap.dev.io_api.infrastructure.ai.ChatContextStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool que el tutor invoca cuando tiene suficiente información para formular el modelo LP.
 * Escribe el ModeloLP en ChatContextStore para que el controlador lo incluya en ChatResponse
 * y la UI pueda pre-llenar el formulario automáticamente.
 */
@Slf4j
@Component
public class SugerirModeloTool {

    private final ChatContextStore contextStore;

    public SugerirModeloTool(ChatContextStore contextStore) {
        this.contextStore = contextStore;
    }

    public record RestriccionInput(
            @Description("Coeficientes de la restricción en el mismo orden que las variables")
            List<Double> coeficientes,
            @Description("Tipo de restricción: LEQ (<=), GEQ (>=) o EQ (=)")
            TipoRestriccion tipo,
            @Description("Término independiente (lado derecho de la restricción)")
            double rhs
    ) {}

    @Tool("""
            Registra el modelo LP sugerido para que la interfaz lo muestre al estudiante.
            INVOCA esto cuando hayas identificado suficiente información del enunciado para
            formular el modelo completo (variables, función objetivo y restricciones).
            La UI pre-llenará el formulario con este modelo — el estudiante puede revisarlo
            y modificarlo antes de validar o resolver.
            También úsalo si necesitas proponer un modelo CORREGIDO después de validar.
            """)
    public String registrarModeloSugerido(
            @P("Nombres de las variables de decisión, ej: [\"x1\", \"x2\"]")
            List<String> variables,

            @P("Coeficientes de la función objetivo en el mismo orden que las variables")
            List<Double> objetivoCoeficientes,

            @P("Tipo de optimización: MAXIMIZAR o MINIMIZAR")
            TipoObjetivo tipoObjetivo,

            @P("Lista de restricciones. Cada una tiene coeficientes, tipo (LEQ/GEQ/EQ) y rhs")
            List<RestriccionInput> restricciones
    ) {
        List<Restriccion> restriccionesLP = restricciones.stream()
                .map(r -> new Restriccion(r.coeficientes(), r.tipo(), r.rhs()))
                .toList();

        ModeloLP modelo = new ModeloLP(
                variables,
                new FuncionObjetivo(objetivoCoeficientes, tipoObjetivo),
                restriccionesLP
        );

        contextStore.obtener().modeloSugerido = modelo;

        log.info("[TOOL] registrarModeloSugerido — vars={}, tipo={}, restricciones={}",
                variables, tipoObjetivo, restricciones.size());

        return "Modelo registrado en la interfaz. El estudiante puede verlo y editarlo en el formulario.";
    }
}
