package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.plugin.core.condition.DayWeekInMonth;
import io.kestra.plugin.core.trigger.Schedule;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

class SchedulerConditionTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Inject
    protected SchedulerExecutionStateInterface executionState;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    private static Flow createScheduleFlow() {
        Schedule schedule = Schedule.builder()
            .id("hourly")
            .type(Schedule.class.getName())
            .cron("0 0 * * *")
            .inputs(Map.of(
                "testInputs", "test-inputs"
            ))
            .conditions(List.of(
                DayWeekInMonth.builder()
                    .type(DayWeekInMonth.class.getName())
                    .date("{{ trigger.date }}")
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .dayInMonth(DayWeekInMonth.DayInMonth.FIRST)
                    .build()
            ))
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    @Test
    void schedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        SchedulerExecutionStateInterface executionRepositorySpy = spy(this.executionState);
        CountDownLatch queueCount = new CountDownLatch(4);

        Flow flow = createScheduleFlow();
        flowRepository.create(flow, flow.generateSource(), flow);

        triggerState.create(Trigger.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId("hourly")
            .date(ZonedDateTime.parse("2021-09-06T02:00:00+01:00[Europe/Paris]"))
            .build()
        );

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // mock the backfill execution is ended
        doAnswer(invocation -> Optional.of(Execution.builder().state(new State().withState(State.Type.SUCCESS)).build()))
            .when(executionRepositorySpy)
            .findById(any(), any());

        // scheduler
        try (AbstractScheduler scheduler = new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        )) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, throwConsumer(either -> {
                Execution execution = either.getLeft();
                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    executionQueue.emit(execution.withState(State.Type.SUCCESS));

                    queueCount.countDown();
                    if (queueCount.getCount() == 0) {
                        assertThat(ZonedDateTime.parse((String) execution.getTrigger().getVariables().get("date")), is(ZonedDateTime.parse("2022-01-03T00:00:00+01:00")));
                    }
                }
                assertThat(execution.getFlowId(), is(flow.getId()));
            }));

            scheduler.run();
            queueCount.await(15, TimeUnit.SECONDS);

            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));
        }
    }
}
