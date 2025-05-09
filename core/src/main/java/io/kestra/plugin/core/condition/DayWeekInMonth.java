package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.conditions.ScheduleCondition;
import io.kestra.core.utils.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Condition to execute tasks on a specific day of the week relative to the current month (first, last, ...)"
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger condition to execute the flow only on the first Monday of the month.",
            full = true,
            code = """
                id: schedule_condition_dayweekinmonth
                namespace: company.team

                tasks:
                  - id: log_message
                    type: io.kestra.plugin.core.log.Log
                    message: "This flow will execute only on the first Monday of the month at 11:00 am."

                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "0 11 * * *"
                    conditions:
                      - type: io.kestra.plugin.core.condition.DayWeekInMonth
                        dayOfWeek: "MONDAY"
                        dayInMonth: FIRST
                """
        )
    },
    aliases = {"io.kestra.core.models.conditions.types.DayWeekInMonthCondition", "io.kestra.plugin.core.condition.DayWeekInMonthCondition"}
)
public class DayWeekInMonth extends Condition implements ScheduleCondition {
    @NotNull
    @Schema(
        title = "The date to test.",
        description = "Can be any variable or any valid ISO 8601 datetime. By default, it will use the trigger date."
    )
    @Builder.Default
    @PluginProperty(dynamic = true)
    private final String date = "{{ trigger.date }}";

    @NotNull
    @Schema(title = "The day of week.")
    @PluginProperty
    private DayOfWeek dayOfWeek;

    @NotNull
    @Schema(title = "Are you looking for the first or the last day in the month?")
    @PluginProperty
    private DayWeekInMonth.DayInMonth dayInMonth;

    @Override
    public boolean test(ConditionContext conditionContext) throws InternalException {
        String render = conditionContext.getRunContext().render(date, conditionContext.getVariables());
        LocalDate currentDate = DateUtils.parseLocalDate(render);
        LocalDate computed;

        if (dayInMonth.equals(DayInMonth.FIRST)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.LAST)) {
            computed = currentDate.with(TemporalAdjusters.lastInMonth(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.SECOND)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.THIRD)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek));
        } else if (dayInMonth.equals(DayInMonth.FOURTH)) {
            computed = currentDate.with(TemporalAdjusters.firstInMonth(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek)).with(TemporalAdjusters.next(dayOfWeek));
        } else {
            throw new IllegalArgumentException("Invalid dayInMonth");
        }

        return computed.isEqual(currentDate);
    }

    public enum DayInMonth {
        FIRST,
        LAST,
        SECOND,
        THIRD,
        FOURTH,
    }
}
