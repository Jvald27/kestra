package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest(startRunner = true)
class EmbeddedSubflowTest {
    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    @ExecuteFlow("flows/valids/embedded-flow.yaml")
    void shouldEmbedTasks(Execution execution) throws Exception {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("embeddedFlow"), notNullValue());
        assertThat(execution.findTaskRunsByTaskId("date"), notNullValue());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml", "flows/valids/embedded-flow.yaml"})
    @ExecuteFlow("flows/valids/embedded-parent.yaml")
    void shouldEmbedTasksRecursively(Execution execution) throws Exception {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.findTaskRunsByTaskId("embeddedParent"), notNullValue());
        assertThat(execution.findTaskRunsByTaskId("embeddedFlow"), notNullValue());
        assertThat(execution.findTaskRunsByTaskId("date"), notNullValue());
    }
}