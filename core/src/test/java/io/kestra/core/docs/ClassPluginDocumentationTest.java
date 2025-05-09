package io.kestra.core.docs;

import io.kestra.core.Helpers;
import io.kestra.core.models.property.DynamicPropertyExampleTask;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.triggers.TriggerInterface;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.plugin.core.runner.Process;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.plugins.RegisteredPlugin;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ClassPluginDocumentationTest {
    @SuppressWarnings("unchecked")
    @Test
    void tasks() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            Path plugins = Paths.get(Objects.requireNonNull(ClassPluginDocumentationTest.class.getClassLoader().getResource("plugins")).toURI());

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            List<RegisteredPlugin> scan = pluginScanner.scan(plugins);

            assertThat(scan.size(), is(1));
            assertThat(scan.getFirst().getTasks().size(), is(1));

            PluginClassAndMetadata<Task> metadata = PluginClassAndMetadata.create(scan.getFirst(), scan.getFirst().getTasks().getFirst(), Task.class, null);
            ClassPluginDocumentation<? extends Task> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, false);

            assertThat(doc.getDocExamples().size(), is(2));
            assertThat(doc.getIcon(), is(notNullValue()));
            assertThat(doc.getInputs().size(), is(5));
            assertThat(doc.getDocLicense(), is("EE"));

            // simple
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("type"), is("string"));
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("default"), is("{}"));
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("pattern"), is(".*"));
            assertThat(((Map<String, String>) doc.getInputs().get("format")).get("description"), containsString("of this input"));

            // definitions
            assertThat(doc.getDefs().size(), is(5));

            // enum
            Map<String, Object> enumProperties = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput")).get("properties")).get("childEnum");
            assertThat(((List<String>) enumProperties.get("enum")).size(), is(2));
            assertThat(((List<String>) enumProperties.get("enum")), containsInAnyOrder("VALUE_1", "VALUE_2"));

            Map<String, Object> childInput = (Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput")).get("properties");

            // array
            Map<String, Object> childInputList = (Map<String, Object>) childInput.get("list");
            assertThat((String) (childInputList).get("type"), is("array"));
            assertThat((String) (childInputList).get("title"), is("List of string"));
            assertThat((Integer) (childInputList).get("minItems"), is(1));
            assertThat(((Map<String, String>) (childInputList).get("items")).get("type"), is("string"));

            // map
            Map<String, Object> childInputMap = (Map<String, Object>) childInput.get("map");
            assertThat((String) (childInputMap).get("type"), is("object"));
            assertThat((Boolean) (childInputMap).get("$dynamic"), is(true));
            assertThat(((Map<String, String>) (childInputMap).get("additionalProperties")).get("type"), is("number"));

            // output
            Map<String, Object> childOutput = (Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.AbstractTask-OutputChild")).get("properties");
            assertThat(((Map<String, String>) childOutput.get("value")).get("type"), is("string"));
            assertThat(((Map<String, Object>) childOutput.get("outputChildMap")).get("type"), is("object"));
            assertThat(((Map<String, String>)((Map<String, Object>) childOutput.get("outputChildMap")).get("additionalProperties")).get("$ref"), containsString("OutputMap"));

            // required
            Map<String, Object> propertiesChild = (Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.ExampleTask-PropertyChildInput");
            assertThat(((List<String>) propertiesChild.get("required")).size(), is(3));

            // output ref
            Map<String, Object> outputMap = ((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.plugin.templates.AbstractTask-OutputMap")).get("properties"));
            assertThat(outputMap.size(), is(2));
            assertThat(((Map<String, Object>) outputMap.get("code")).get("type"), is("integer"));
        }));
    }

    @SuppressWarnings("unchecked")
    @Test
    void trigger() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            RegisteredPlugin scan = pluginScanner.scan();

            PluginClassAndMetadata<AbstractTrigger> metadata = PluginClassAndMetadata.create(scan, Schedule.class, AbstractTrigger.class, null);
            ClassPluginDocumentation<? extends AbstractTrigger> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, true);

            assertThat(doc.getDefs().size(), is(1));
            assertThat(doc.getDocLicense(), nullValue());

            assertThat(((Map<String, Object>) doc.getDefs().get("io.kestra.core.models.tasks.WorkerGroup")).get("type"), is("object"));
            assertThat(((Map<String, Object>) ((Map<String, Object>) doc.getDefs().get("io.kestra.core.models.tasks.WorkerGroup")).get("properties")).size(), is(2));
        }));
    }

    @Test
    void taskRunner() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            RegisteredPlugin scan = pluginScanner.scan();

            PluginClassAndMetadata<? extends TaskRunner<?>> metadata = PluginClassAndMetadata.create(scan, Process.class, Process.class, null);
            ClassPluginDocumentation<? extends TaskRunner<?>> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, false);

            assertThat((Map<?, ?>) doc.getPropertiesSchema().get("properties"), anEmptyMap());
            assertThat(doc.getCls(), is("io.kestra.plugin.core.runner.Process"));
            assertThat(doc.getPropertiesSchema().get("title"), is("Task runner that executes a task as a subprocess on the Kestra host."));
            assertThat(doc.getDefs(), anEmptyMap());
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dynamicProperty() throws URISyntaxException {
        Helpers.runApplicationContext(throwConsumer((applicationContext) -> {
            JsonSchemaGenerator jsonSchemaGenerator = applicationContext.getBean(JsonSchemaGenerator.class);

            PluginScanner pluginScanner = new PluginScanner(ClassPluginDocumentationTest.class.getClassLoader());
            RegisteredPlugin scan = pluginScanner.scan();

            PluginClassAndMetadata<DynamicPropertyExampleTask> metadata = PluginClassAndMetadata.create(scan, DynamicPropertyExampleTask.class, DynamicPropertyExampleTask.class, null);
            ClassPluginDocumentation<? extends DynamicPropertyExampleTask> doc = ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, true);

            assertThat(doc.getCls(), is("io.kestra.core.models.property.DynamicPropertyExampleTask"));
            assertThat(doc.getDefs(), aMapWithSize(6));
            Map<String, Object> properties = (Map<String, Object>) doc.getPropertiesSchema().get("properties");
            assertThat(properties, aMapWithSize(20));

            Map<String, Object> number = (Map<String, Object>) properties.get("number");
            assertThat(number.get("oneOf"), notNullValue());
            List<Map<String, Object>> oneOf = (List<Map<String, Object>>) number.get("oneOf");
            assertThat(oneOf, hasSize(2));
            assertThat(oneOf.getFirst().get("type"), is("integer"));
            assertThat(oneOf.getFirst().get("$dynamic"), is(true));
            assertThat(oneOf.get(1).get("type"), is("string"));
//            assertThat(oneOf.get(1).get("pattern"), is(".*{{.*}}.*"));

            Map<String, Object> withDefault = (Map<String, Object>) properties.get("withDefault");
            assertThat(withDefault.get("type"), is("string"));
            assertThat(withDefault.get("default"), is("Default Value"));
            assertThat(withDefault.get("$dynamic"), is(true));
        }));
    }
}
