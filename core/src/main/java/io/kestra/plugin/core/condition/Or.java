package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwPredicate;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to have at least one condition validated."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow when any of the condition is satisfied.",
            full = true,
            code = """
                id: schedule_condition_or
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute on Sundays and Mondays at 11am."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 11 * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.Or
                        conditions:
                          - type: io.kestra.plugin.core.condition.DayWeek
                            dayOfWeek: "MONDAY"
                          - type: io.kestra.plugin.core.condition.DayWeek
                            dayOfWeek: "SUNDAY"
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.OrCondition", "io.kestra.plugin.core.condition.OrCondition"}
)
public class Or extends Condition implements ScheduleCondition {
    @NotNull
    @NotEmpty
    @Schema(
        title = "The list of conditions to validate.",
        description = "If any condition is true, it will allow the event's execution."
    )
    @PluginProperty
    private List<Condition> conditions;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        return this.conditions
            .stream()
            .anyMatch(throwPredicate(condition -> condition.test(conditionContext)));
    }
}
