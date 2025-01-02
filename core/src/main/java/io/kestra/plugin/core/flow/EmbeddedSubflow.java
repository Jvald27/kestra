package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.hierarchies.AbstractGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.*;
import io.kestra.core.utils.GraphUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Embeds subflow tasks into this flow."
)
@Plugin(
    examples = {
        @Example(
            title = "Embeds subflow tasks.",
            full = true,
            code = """
                id: parent_flow
                namespace: company.team

                tasks:
                  - id: embed_subflow
                    type: io.kestra.plugin.core.flow.EmbeddedSubflow
                    namespace: company.team
                    flowId: subflow
                """
        )
    }
)
public class EmbeddedSubflow extends Task implements FlowableTask<EmbeddedSubflow.Output>, ChildFlowInterface {
    static final String PLUGIN_FLOW_OUTPUTS_ENABLED = "outputs.enabled";

    // FIXME no other choice for now as getErrors() and allChildTasks() has no context
    //  maybe refacto getErrors() and allChildTasks() to take tenantId as parameter
    @Schema(
        title = "The tenantId of the subflow to be embedded."
    )
    @PluginProperty
    private String tenantId;

    @NotEmpty
    @Schema(
        title = "The namespace of the subflow to be embedded."
    )
    @PluginProperty
    private String namespace;

    @NotNull
    @Schema(
        title = "The identifier of the subflow to be embedded."
    )
    @PluginProperty
    private String flowId;

    @Schema(
        title = "The revision of the subflow to be embedded.",
        description = "By default, the last, i.e. the most recent, revision of the subflow is embedded."
    )
    @PluginProperty
    @Min(value = 1)
    private Integer revision;

    // TODO list:
    //  - inputs ?
    //  - unique taskId via value ?
    //  - dedicated run context ?

    @Override
    @JsonIgnore
    public List<Task> getErrors() {
        Flow subflow = fetchSubflow();

        return subflow.getErrors();
    }

    @Override
    public AbstractGraph tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        Flow subflow = fetchSubflow();

        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            subflow.getTasks(),
            subflow.getErrors(),
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        Flow subflow = fetchSubflow();

        return Stream
            .concat(
                subflow.getTasks() != null ? subflow.getTasks().stream() : Stream.empty(),
                subflow.getErrors() != null ? subflow.getErrors().stream() : Stream.empty()
            )
            .toList();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        Flow subflow = fetchSubflow(runContext);

        return FlowableUtils.resolveTasks(subflow.getTasks(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    @Override
    public Output outputs(RunContext runContext) throws Exception {
        final Output.OutputBuilder builder = Output.builder();
        Flow subflow = fetchSubflow(runContext);

        final Optional<Map<String, Object>> subflowOutputs = Optional
            .ofNullable(subflow.getOutputs())
            .map(outputs -> outputs
                .stream()
                .collect(Collectors.toMap(
                    io.kestra.core.models.flows.Output::getId,
                    io.kestra.core.models.flows.Output::getValue)
                )
            );

        if (subflowOutputs.isPresent() && runContext.getVariables().get("outputs") != null) {
            Map<String, Object> outputs = runContext.render(subflowOutputs.get());
            FlowInputOutput flowInputOutput = ((DefaultRunContext)runContext).getApplicationContext().getBean(FlowInputOutput.class); // this is hacking
            if (subflow.getOutputs() != null && flowInputOutput != null) {
                // to be able to use FILE Input, we need the execution info, so we create a fake execution with what's needed here
                RunContext.FlowInfo flowInfo = runContext.flowInfo();
                String executionId = (String) ((Map<String, Object>) runContext.getVariables().get("execution")).get("id");
                Execution fake = Execution.builder()
                    .id(executionId)
                    .tenantId(flowInfo.tenantId())
                    .namespace(flowInfo.namespace())
                    .flowId(flowInfo.id())
                    .build();
                outputs = flowInputOutput.typedOutputs(subflow, fake, outputs);
            }
            builder.outputs(outputs);
        }
        return builder.build();
    }

    // This method should only be used when getSubflow(RunContext) cannot be used.
    private Flow fetchSubflow() {
        ApplicationContext applicationContext = ContextHelper.context();
        FlowExecutorInterface flowExecutor = applicationContext.getBean(FlowExecutorInterface.class);
        FlowWithSource subflow = flowExecutor.findById(tenantId, namespace, flowId, Optional.ofNullable(revision)).orElseThrow(() -> new IllegalArgumentException("Unable to find flow " + namespace + "." + flowId));

        if (subflow.isDisabled()) {
            throw new IllegalStateException("Cannot execute a flow which is disabled");
        }

        if (subflow instanceof FlowWithException fwe) {
            throw new IllegalStateException("Cannot execute an invalid flow: " + fwe.getException());
        }

        return subflow;
    }

    // This method is preferred as getSubflow() as it checks current flow and subflow and allowed namespaces
    private Flow fetchSubflow(RunContext runContext) {
        // we check that the task tenant is the current tenant to avoid accessing flows from another tenant
        if (!Objects.equals(tenantId, runContext.flowInfo().tenantId())) {
            throw new IllegalArgumentException("Cannot embeds a flow from a different tenant");
        }

        ApplicationContext applicationContext = ContextHelper.context();
        FlowExecutorInterface flowExecutor = applicationContext.getBean(FlowExecutorInterface.class);
        RunContext.FlowInfo flowInfo = runContext.flowInfo();

        FlowWithSource flow = flowExecutor.findById(flowInfo.tenantId(), flowInfo.namespace(), flowInfo.id(), Optional.of(flowInfo.revision()))
            .orElseThrow(() -> new IllegalArgumentException("Unable to find flow " + flowInfo.namespace() + "." + flowInfo.id()));
        return ExecutableUtils.getSubflow(tenantId, namespace, flowId, Optional.ofNullable(revision), flowExecutor, flow);
    }

    /**
     * Ugly hack to provide the ApplicationContext on {{@link #allChildTasks }} &amp; {{@link #tasksTree }}
     * We need to inject a way to fetch embedded subflows ...
     */
    @Singleton
    static class ContextHelper {
        @Inject
        private ApplicationContext applicationContext;

        private static ApplicationContext context;

        static ApplicationContext context() {
            return ContextHelper.context;
        }

        @EventListener
        void onStartup(final StartupEvent event) {
            ContextHelper.context = this.applicationContext;
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The extracted outputs from the embedded subflow."
        )
        private final Map<String, Object> outputs;
    }
}
