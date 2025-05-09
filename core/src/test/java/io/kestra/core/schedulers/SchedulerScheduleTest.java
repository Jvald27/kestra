package io.kestra.core.schedulers;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.plugin.core.condition.Expression;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class SchedulerScheduleTest extends AbstractSchedulerTest {
    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    protected SchedulerTriggerStateInterface triggerState;

    @Inject
    protected SchedulerExecutionStateInterface executionState;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logQueue;

    private Schedule.ScheduleBuilder<?, ?> createScheduleTrigger(String zone, String cron, String triggerId, boolean invalid) {
        return Schedule.builder()
            .id(triggerId + (invalid ? "-invalid" : ""))
            .type(Schedule.class.getName())
            .cron(cron)
            .timezone(zone)
            .inputs(Map.of(
                "testInputs", "test-inputs"
            ));
    }

    private FlowWithSource createScheduleFlow(String zone, String triggerId, boolean invalid) {
        Schedule schedule = createScheduleTrigger(zone, "0 * * * *", triggerId, invalid).build();

        return createFlow(Collections.singletonList(schedule));
    }

    private ZonedDateTime date(int minus) {
        return ZonedDateTime.now()
            .minusHours(minus)
            .truncatedTo(ChronoUnit.HOURS);
    }

    protected AbstractScheduler scheduler(FlowListeners flowListenersServiceSpy, SchedulerExecutionStateInterface executionStateSpy) {
        return new JdbcScheduler(
            applicationContext,
            flowListenersServiceSpy
        );
    }


    @Test
    void schedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        SchedulerExecutionStateInterface executionStateSpy = spy(this.executionState);
        CountDownLatch queueCount = new CountDownLatch(6);
        CountDownLatch invalidLogCount = new CountDownLatch(1);
        Set<String> date = new HashSet<>();
        Set<String> executionId = new HashSet<>();

        // Create a flow with a backfill of 5 hours
        // then flow should be executed 6 times
        FlowWithSource invalid = createScheduleFlow("Asia/Delhi", "schedule", true);
        FlowWithSource flow = createScheduleFlow("Europe/Paris", "schedule", false);

        flowRepository.create(flow, flow.generateSource(), flow);
        doReturn(List.of(invalid, flow))
            .when(flowListenersServiceSpy)
            .flows();

        Trigger trigger = Trigger
            .builder()
            .triggerId("schedule")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now())
            .backfill(
                Backfill.builder()
                    .start(date(5))
                    .end(ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS))
                    .currentDate(date(5))
                    .previousNextExecutionDate(ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS))
                    .build()
            )
            .build();

        triggerState.create(trigger);
        triggerState.create(trigger.toBuilder().triggerId("schedule-invalid").flowId(invalid.getId()).build());

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionStateSpy)) {
            // wait for execution
            Flux<Execution> receiveExecutions = TestsUtils.receive(executionQueue, throwConsumer(either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getInputs().get("testInputs"), is("test-inputs"));
                assertThat(execution.getInputs().get("def"), is("awesome"));

                date.add((String) execution.getTrigger().getVariables().get("date"));
                executionId.add(execution.getId());

                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    executionQueue.emit(execution.withState(State.Type.SUCCESS));
                }
                assertThat(execution.getFlowId(), is(flow.getId()));
                queueCount.countDown();
            }));

            Flux<LogEntry> receiveLogs = TestsUtils.receive(logQueue, e -> {
                if (e.getLeft().getMessage().contains("Unknown time-zone ID: Asia/Delhi")) {
                    invalidLogCount.countDown();
                }
            });

            scheduler.run();
            queueCount.await(1, TimeUnit.MINUTES);
            invalidLogCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            receiveExecutions.blockLast();
            receiveLogs.blockLast();

            assertThat(queueCount.getCount(), is(0L));
            assertThat(invalidLogCount.getCount(), is(0L));
            assertThat(date.size(), greaterThanOrEqualTo(3));
            assertThat(executionId.size(), greaterThanOrEqualTo(3));
        }
    }

    // Test to ensure behavior between 0.14 > 0.15
    @Test
    void retroSchedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        FlowWithSource flow = createScheduleFlow("Europe/Paris", "retroSchedule", false);

        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        Trigger trigger = Trigger
            .builder()
            .triggerId("retroSchedule")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now())
            .build();

        triggerState.create(trigger);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            scheduler.run();

            Await.until(() -> {
                Optional<Trigger> optionalTrigger = this.triggerState.findLast(trigger);
                return optionalTrigger.filter(value -> value.getNextExecutionDate() != null).isPresent();
            }, Duration.ofSeconds(1), Duration.ofSeconds(60));

            assertThat(this.triggerState.findLast(trigger).get().getNextExecutionDate().isAfter(trigger.getDate()), is(true));
        }
    }

    @Test
    void recoverALLMissing() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        FlowWithSource flow = createScheduleFlow(null, "recoverALLMissing", false);
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        ZonedDateTime lastDate = ZonedDateTime.now().minusHours(3L);
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("recoverALLMissing")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(lastDate)
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getFlowId(), is(flow.getId()));
                queueCount.countDown();
            });

            scheduler.run();

            queueCount.await(1, TimeUnit.MINUTES);
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));
            Trigger newTrigger = this.triggerState.findLast(lastTrigger).orElseThrow();
            assertThat(newTrigger.getDate().toLocalDateTime(), is(lastDate.plusHours(1L).truncatedTo(ChronoUnit.HOURS).toLocalDateTime()));
            assertThat(newTrigger.getNextExecutionDate().toLocalDateTime(), is(lastDate.plusHours(2L).truncatedTo(ChronoUnit.HOURS).toLocalDateTime()));
        }
    }

    @Test
    void recoverLASTMissing() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        Schedule schedule = createScheduleTrigger(null, "0 * * * *", "recoverLASTMissing", false)
            .recoverMissedSchedules(RecoverMissedSchedules.LAST)
            .build();
        FlowWithSource flow = createFlow(List.of(schedule));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        ZonedDateTime lastDate = ZonedDateTime.now().minusHours(3L);
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("recoverLASTMissing")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(lastDate)
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getFlowId(), is(flow.getId()));
                queueCount.countDown();
            });

            scheduler.run();

            queueCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));
            Trigger newTrigger = this.triggerState.findLast(lastTrigger).orElseThrow();
            assertThat(newTrigger.getDate().toLocalDateTime(), is(lastDate.plusHours(3L).truncatedTo(ChronoUnit.HOURS).toLocalDateTime()));
            assertThat(newTrigger.getNextExecutionDate().toLocalDateTime(), is(lastDate.plusHours(4L).truncatedTo(ChronoUnit.HOURS).toLocalDateTime()));
        }
    }

    @Test
    void recoverNONEMissing() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        Schedule schedule = createScheduleTrigger(null, "0 * * * *", "recoverNONEMissing", false)
            .recoverMissedSchedules(RecoverMissedSchedules.NONE)
            .build();
        FlowWithSource flow = createFlow(List.of(schedule));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        ZonedDateTime lastDate = ZonedDateTime.now().minusHours(3L);
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("recoverNONEMissing")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(lastDate)
            .build();
        triggerState.create(lastTrigger);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            scheduler.run();

            Await.until(() -> scheduler.isReady(), Duration.ofMillis(100), Duration.ofSeconds(5));

            Trigger newTrigger = this.triggerState.findLast(lastTrigger).orElseThrow();
            assertThat(newTrigger.getNextExecutionDate().toLocalDateTime(), is(lastDate.plusHours(4L).truncatedTo(ChronoUnit.HOURS).toLocalDateTime()));
        }
    }

    @Test
    void backfill() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        String triggerId = "backfill";

        FlowWithSource flow = createScheduleFlow("Europe/Paris", triggerId, false);

        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // Used to find last
        Trigger trigger = Trigger
            .builder()
            .triggerId(triggerId)
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .build();

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            scheduler.run();

            Await.until(() -> {
                Optional<Trigger> optionalTrigger = this.triggerState.findLast(trigger);
                return optionalTrigger.filter(value -> value.getNextExecutionDate() != null).isPresent();
            }, Duration.ofSeconds(1), Duration.ofSeconds(15));

            Trigger lastTrigger = this.triggerState.findLast(trigger).get();

            assertThat(lastTrigger.getNextExecutionDate(),
                greaterThanOrEqualTo(ZonedDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)));

            triggerState.update(lastTrigger.toBuilder()
                .backfill(
                    Backfill.builder()
                        .start(date(5))
                        .end(ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS))
                        .currentDate(date(5))
                        .previousNextExecutionDate(lastTrigger.getNextExecutionDate().truncatedTo(ChronoUnit.HOURS))
                        .build()
                ).build()
            );

            Await.until(() -> {
                Optional<Trigger> optionalTrigger = this.triggerState.findLast(lastTrigger);
                return optionalTrigger.filter(value -> value.getBackfill() != null).isPresent();
            }, Duration.ofSeconds(1), Duration.ofSeconds(15));

            Trigger lastTrigger2 = this.triggerState.findLast(trigger).get();

            assertThat(lastTrigger2.getNextExecutionDate(),
                lessThanOrEqualTo(lastTrigger.getNextExecutionDate().truncatedTo(ChronoUnit.HOURS)));

        }
    }

    @Test
    void disabled() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        String triggerId = "disabled";

        FlowWithSource flow = createScheduleFlow("Europe/Paris", triggerId, false);

        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS);
        // Shortcut the scheduler and create trigger before it
        // and set the trigger to disabled with a specific nextExecutionDate
        Trigger trigger = Trigger
            .builder()
            .triggerId(triggerId)
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now())
            .nextExecutionDate(now)
            .disabled(true)
            .build();

        triggerState.create(trigger);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            scheduler.run();

            // Wait 3s to see if things happen
            Thread.sleep(3000);

            Trigger lastTrigger = this.triggerState.findLast(trigger).get();

            // Nothing changed because nothing happened
            assertThat(lastTrigger.getNextExecutionDate().truncatedTo(ChronoUnit.HOURS).isEqual(now), is(true));
        }
    }

    @Test
    void stopAfterSchedule() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        Schedule schedule = createScheduleTrigger("Europe/Paris", "* * * * *", "stopAfter", false)
            .stopAfter(List.of(State.Type.SUCCESS))
            .build();
        FlowWithSource flow = createFlow(Collections.singletonList(schedule));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        flowRepository.create(flow, flow.generateSource(), flow);
        // to avoid waiting too much before a trigger execution, we add a last trigger with a date now - 1m.
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("stopAfter")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now().minusMinutes(1L))
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(2);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, throwConsumer(either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getInputs().get("testInputs"), is("test-inputs"));
                assertThat(execution.getInputs().get("def"), is("awesome"));
                assertThat(execution.getFlowId(), is(flow.getId()));

                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    executionQueue.emit(execution.withState(State.Type.SUCCESS));
                }
                queueCount.countDown();
            }));

            scheduler.run();

            queueCount.await(1, TimeUnit.MINUTES);
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));

            // Assert that the trigger is now disabled.
            // It needs to await on assertion as it will be disabled AFTER we receive a success execution.
            Trigger trigger = Trigger.of(flow, schedule);
            Await.until(() -> this.triggerState.findLast(trigger).map(t -> t.getDisabled()).orElse(false).booleanValue(), Duration.ofMillis(100), Duration.ofSeconds(10));
        }
    }

    @Test
    void failedEvaluationTest() {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        Schedule schedule = createScheduleTrigger("Europe/Paris", "* * * * *", "failedEvaluation", false)
            .conditions(
                List.of(
                    Expression.builder()
                        .type(Expression.class.getName())
                        .expression("{{ trigger.date | date() < now() }}")
                        .build()
                )
            )
            .build();
        FlowWithSource flow = createFlow(Collections.singletonList(schedule));
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // to avoid waiting too much before a trigger execution, we add a last trigger with a date now - 1m.
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId("failedEvaluation")
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now().minusMinutes(1L))
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getFlowId(), is(flow.getId()));
                assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

                queueCount.countDown();
            });

            scheduler.run();

            queueCount.await(1, TimeUnit.MINUTES);
            // needed for RetryingTest to work since there is no context cleaning between method => we have to clear assertion receiver manually
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void recoverLASTLongRunningExecution() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        String triggerId = "recoverLASTLongRunningExecution";
        Schedule schedule = Schedule.builder().id(triggerId).type(Schedule.class.getName()).cron("*/5 * * * * *").withSeconds(true).build();
        FlowWithSource flow = createLongRunningFlow(
            Collections.singletonList(schedule),
            List.of(
                PluginDefault.builder()
                    .type(Schedule.class.getName())
                    .values(Map.of("recoverMissedSchedules", "LAST"))
                    .build()
            )
        );
        flowRepository.create(flow, flow.generateSource(), flow);
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // to avoid waiting too much before a trigger execution, we add a last trigger with a date now - 1m.
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId(triggerId)
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now().minusMinutes(1L))
            .nextExecutionDate(ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES))
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, throwConsumer(either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getFlowId(), is(flow.getId()));

                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    Thread.sleep(11000);
                    executionQueue.emit(execution.withState(State.Type.SUCCESS)
                        .toBuilder()
                        .taskRunList(List.of(TaskRun.builder()
                            .id("test")
                            .executionId(execution.getId())
                            .state(State.of(State.Type.SUCCESS,
                                List.of(new State.History(
                                    State.Type.SUCCESS,
                                    lastTrigger.getNextExecutionDate().plusMinutes(3).toInstant()
                                ))))
                            .build()))
                        .build()
                    );
                }
                queueCount.countDown();
            }));

            scheduler.run();

            queueCount.await(3, TimeUnit.MINUTES);
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));

            Trigger trigger = Trigger.of(flow, schedule);
            Await.until(() -> this.triggerState.findLast(trigger).map(t -> t.getNextExecutionDate().isAfter(lastTrigger.getNextExecutionDate().plusSeconds(10))).orElse(false).booleanValue(), Duration.ofMillis(100), Duration.ofSeconds(20));
        }
    }

    @Test
    void recoverNONELongRunningExecution() throws Exception {
        // mock flow listeners
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);
        String triggerId = "recoverNONELongRunningExecution";
        Schedule schedule = Schedule.builder().id(triggerId).type(Schedule.class.getName()).cron("*/5 * * * * *").withSeconds(true).build();
        FlowWithSource flow = createLongRunningFlow(
            Collections.singletonList(schedule),
            List.of(
                PluginDefault.builder()
                    .type(Schedule.class.getName())
                    .values(Map.of("recoverMissedSchedules", "LAST"))
                    .build()
            )
        );
        flowRepository.create(flow, flow.generateSource(), flow);
        doReturn(List.of(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // to avoid waiting too much before a trigger execution, we add a last trigger with a date now - 1m.
        Trigger lastTrigger = Trigger
            .builder()
            .triggerId(triggerId)
            .flowId(flow.getId())
            .namespace(flow.getNamespace())
            .date(ZonedDateTime.now().minusMinutes(1L))
            .nextExecutionDate(ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES))
            .build();
        triggerState.create(lastTrigger);

        CountDownLatch queueCount = new CountDownLatch(1);

        // scheduler
        try (AbstractScheduler scheduler = scheduler(flowListenersServiceSpy, executionState)) {
            // wait for execution
            Flux<Execution> receive = TestsUtils.receive(executionQueue, throwConsumer(either -> {
                Execution execution = either.getLeft();
                assertThat(execution.getFlowId(), is(flow.getId()));

                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    Thread.sleep(10000);
                    executionQueue.emit(execution.withState(State.Type.SUCCESS)
                        .toBuilder()
                        .taskRunList(List.of(TaskRun.builder()
                            .id("test")
                            .executionId(execution.getId())
                            .state(State.of(State.Type.SUCCESS,
                                List.of(new State.History(
                                    State.Type.SUCCESS,
                                    lastTrigger.getNextExecutionDate().plusMinutes(3).toInstant()
                                ))))
                            .build()))
                        .build()
                    );
                }
                queueCount.countDown();
            }));

            scheduler.run();

            queueCount.await(3, TimeUnit.MINUTES);
            receive.blockLast();

            assertThat(queueCount.getCount(), is(0L));

            Trigger trigger = Trigger.of(flow, schedule);
            Await.until(() -> this.triggerState.findLast(trigger).map(t -> t.getNextExecutionDate().isAfter(lastTrigger.getNextExecutionDate().plusSeconds(10))).orElse(false).booleanValue(), Duration.ofMillis(100), Duration.ofSeconds(20));
        }
    }
}