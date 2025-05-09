package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.models.executions.Execution;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

import static io.kestra.core.utils.MapUtils.mergeWithNullableValues;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition based on the outputs of an upstream execution.",
    description = "The condition returns `false` if the execution has no output. If the result is an empty string, a space, or `false`, the condition will also be considered as `false`."
)
@Plugin(
    examples = {
        @Example(
            title = """
                The upstream `flow_a` must explicitly define its outputs 
                to be used in the `ExecutionOutputs` condition. 

                ```yaml
                id: flow_a
                namespace: company.team

                inputs:
                  - id: user_value
                    type: STRING
                    defaults: hello

                tasks:
                  - id: hello
                    type: io.kestra.plugin.core.debug.Return
                    format: "{{ inputs.user_value }}"

                outputs:
                  - id: flow_a_output
                    type: STRING
                    value: "{{ outputs.hello.value }}"
                ```

                The `flow_condition_executionoutputs` will run whenever `flow_a` finishes successfully 
                and returns an output matching the value 'hello':
                """,
            full = true,
            code = """
                id: flow_condition_executionoutputs
                namespace: company.team

                tasks:
                  - id: upstream_outputs
                    type: io.kestra.plugin.core.log.Log
                    message: hello from a downstream flow

                triggers:
                  - id: condition_on_flow_execution_outputs
                    type: io.kestra.plugin.core.trigger.Flow
                    states:
                      - SUCCESS
                    conditions:
                      - type: io.kestra.plugin.core.condition.ExecutionOutputs
                        expression: "{{ trigger.outputs.flow_a_output == 'hello' }}"
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.ExecutionOutputsCondition", "io.kestra.plugin.core.condition.ExecutionOutputsCondition"}
)
public class ExecutionOutputs extends Condition implements ScheduleCondition {

    private static final String TRIGGER_VAR = "trigger";
    private static final String OUTPUTS_VAR = "outputs";

    @NotNull
    @NotEmpty
    @PluginProperty
    private String expression;

    /** {@inheritDoc} **/
    @SuppressWarnings("unchecked")
    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {

        if (hasNoOutputs(conditionContext.getExecution())) {
            return false; // shortcut for not evaluating the expression.
        }

        Map<String, Object> variables = mergeWithNullableValues(
            conditionContext.getVariables(),
            Map.of(TRIGGER_VAR, Map.of(OUTPUTS_VAR, conditionContext.getExecution().getOutputs()))
        );

        String render = conditionContext.getRunContext().render(expression, variables);
        return !(render.isBlank() || render.isEmpty() || render.trim().equals("false"));
    }

    private boolean hasNoOutputs(final Execution execution) {
        return execution.getOutputs() == null || execution.getOutputs().isEmpty();
    }
}
