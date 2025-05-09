package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.IllegalConditionEvaluation;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.utils.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.OffsetTime;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to allow events between two specific times."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow between two specific times.",
            full = true,
            code = """
                id: schedule_condition_timebetween
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute every 5 minutes between 4pm and 8pm."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "*/5 * * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.TimeBetween
                        after: "16:00:00+02:00"
                        before: "20:00:00+02:00"
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.TimeBetweenCondition", "io.kestra.plugin.core.condition.TimeBetweenCondition"}
)
public class TimeBetween extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The time to test.",
        description = "Can be any variable or any valid ISO 8601 time. By default, it will use the trigger date."
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    private final String date = "{{ trigger.date }}";

    @Schema(
        title = "The time to test must be after this one.",
        description = "Must be a valid ISO 8601 time with offset."
    )
    @PluginProperty
    private OffsetTime after;

    @Schema(
        title = "The time to test must be before this one.",
        description = "Must be a valid ISO 8601 time with offset."
    )
    @PluginProperty
    private OffsetTime before;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(date, conditionContext.getVariables());
        OffsetTime currentDate = DateUtils.parseZonedDateTime(render).toOffsetDateTime().toOffsetTime();

        if (this.before != null && this.after != null) {
            return currentDate.isAfter(after) && currentDate.isBefore(before);
        } else if (this.before != null) {
            return currentDate.isBefore(before);
        } else if (this.after != null) {
            return currentDate.isAfter(after);
        } else {
            throw new IllegalConditionEvaluation("Invalid condition with no before nor after");
        }
    }
}
