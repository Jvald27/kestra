package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.schedulers.*;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@JdbcRunnerEnabled
@Singleton
@Slf4j
public class JdbcScheduler extends AbstractScheduler {
    private final QueueInterface<Execution> executionQueue;
    private final TriggerRepositoryInterface triggerRepository;

    private final FlowRepositoryInterface flowRepository;
    private final JooqDSLContextWrapper dslContextWrapper;
    private final ConditionService conditionService;

    private final ScheduledExecutorService scheduleExecutor = Executors.newSingleThreadScheduledExecutor();

    @SuppressWarnings("unchecked")
    @Inject
    public JdbcScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListeners
    ) {
        super(applicationContext, flowListeners);

        executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));
        triggerRepository = applicationContext.getBean(AbstractJdbcTriggerRepository.class);
        triggerState = applicationContext.getBean(SchedulerTriggerStateInterface.class);
        executionState = applicationContext.getBean(SchedulerExecutionState.class);
        conditionService = applicationContext.getBean(ConditionService.class);
        flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        dslContextWrapper = applicationContext.getBean(JooqDSLContextWrapper.class);
    }

    @Override
    public void run() {
        super.run();


        ScheduledFuture<?> handle = scheduleExecutor.scheduleAtFixedRate(
            this::handle,
            0,
            1,
            TimeUnit.SECONDS
        );

        // look at exception on the main thread
        Thread thread = new Thread(
            () -> {
                Await.until(handle::isDone);

                try {
                    handle.get();
                } catch (CancellationException ignored) {

                } catch (ExecutionException | InterruptedException e) {
                    log.error("Scheduler fatal exception", e);
                    close();
                    applicationContext.close();
                }
            },
            "scheduler-listener"
        );
        thread.start();

        this.receiveCancellations.addFirst(executionQueue.receive(
            Scheduler.class,
            either -> {
                if (either.isRight()) {
                    log.error("Unable to deserialize an execution: {}", either.getRight().getMessage());
                    return;
                }

                Execution execution = either.getLeft();
                if (execution.getTrigger() != null) {
                    var flow = flowRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId()).orElse(null);
                    if (execution.isDeleted() || conditionService.isTerminatedWithListeners(flow, execution)) {
                        // reset scheduler trigger at end
                        triggerRepository
                            .findByExecution(execution)
                            .ifPresent(trigger -> {
                                this.triggerState.update(trigger.resetExecution(execution.getState().getCurrent()));
                            });
                    }
                }
            }
        ));

        // remove trigger on flow update
        this.flowListeners.listen((flow, previous) -> {
            if (flow.isDeleted()) {
                ListUtils.emptyOnNull(flow.getTriggers())
                    .forEach(abstractTrigger -> triggerRepository.delete(Trigger.of(flow, abstractTrigger)));
            } else if (previous != null) {
                FlowService
                    .findRemovedTrigger(flow, previous)
                    .forEach(abstractTrigger -> triggerRepository.delete(Trigger.of(flow, abstractTrigger)));
            }
        });
    }

    protected void handle() {
        if (!isReady()) {
            log.warn("Scheduler is not ready, waiting");
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        JdbcSchedulerContext schedulerContext = new JdbcSchedulerContext(this.dslContextWrapper);

        schedulerContext.doInTransaction(scheduleContextInterface -> {
            List<Trigger> triggers = this.triggerState.findByNextExecutionDateReadyForAllTenants(now, scheduleContextInterface);
            Map<String, FlowWithSource> flows = this.flowListeners.flows()
                .stream()
                .collect(Collectors.toMap(FlowWithSource::uidWithoutRevision, Function.identity()));

            List<TriggerWithFlow> list = triggers.stream()
                .map(trigger -> {
                    FlowWithSource flowWithSource = flows.get(trigger.flowUid());
                    return flowWithSource == null ? null : new TriggerWithFlow(trigger, flowWithSource);
                })
                .filter(Objects::nonNull)
                .toList();

            processTriggers(list, schedulerContext, now);
        });
    }

    @Override
    @PreDestroy
    public void close() {
        this.scheduleExecutor.shutdown();
    }
}
