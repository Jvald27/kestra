package io.kestra.webserver.controllers.api;

import io.kestra.core.Helpers;
import io.kestra.core.docs.DocumentationWithSchema;
import io.kestra.core.docs.InputType;
import io.kestra.core.docs.Plugin;
import io.kestra.core.docs.PluginIcon;
import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.log.Log;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PluginControllerTest {

    @BeforeAll
    public static void beforeAll() {
        Helpers.loadExternalPluginsFromClasspath();
    }

    @Test
    void plugins() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());

            List<Plugin> list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(Plugin.class)
            );

            assertThat(list.size(), is(2));

            Plugin template = list.stream()
                .filter(plugin -> plugin.getTitle().equals("plugin-template-test"))
                .findFirst()
                .orElseThrow();

            assertThat(template.getTitle(), is("plugin-template-test"));
            assertThat(template.getGroup(), is("io.kestra.plugin.templates"));
            assertThat(template.getDescription(), is("Plugin template for Kestra"));

            assertThat(template.getTasks().size(), is(1));
            assertThat(template.getTasks().getFirst(), is("io.kestra.plugin.templates.ExampleTask"));

            assertThat(template.getGuides().size(), is(2));
            assertThat(template.getGuides().getFirst(), is("authentication"));

            Plugin core = list.stream()
                .filter(plugin -> plugin.getTitle().equals("core"))
                .findFirst()
                .orElseThrow();

            assertThat(core.getCategories(), containsInAnyOrder(
                PluginSubGroup.PluginCategory.STORAGE,
                PluginSubGroup.PluginCategory.CORE
            ));

            // classLoader can lead to duplicate plugins for the core, just verify that the response is still the same
            list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(Plugin.class)
            );

            assertThat(list.size(), is(2));
        });
    }

    @Test
    void icons() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());

            Map<String, PluginIcon> list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/icons"),
                Argument.mapOf(String.class, PluginIcon.class)
            );

            assertThat(list.entrySet().stream().filter(e -> e.getKey().equals(Log.class.getName())).findFirst().orElseThrow().getValue().getIcon(), is(notNullValue()));
            // test an alias
            assertThat(list.entrySet().stream().filter(e -> e.getKey().equals("io.kestra.core.tasks.log.Log")).findFirst().orElseThrow().getValue().getIcon(), is(notNullValue()));
        });
    }


    @SuppressWarnings("unchecked")
    @Test
    void returnTask() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());

            DocumentationWithSchema doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/" + Return.class.getName()),
                DocumentationWithSchema.class
            );

            assertThat(doc.getMarkdown(), containsString("io.kestra.plugin.core.debug.Return"));
            assertThat(doc.getMarkdown(), containsString("Return a value for debugging purposes."));
            assertThat(doc.getMarkdown(), containsString("The templated string to render"));
            assertThat(doc.getMarkdown(), containsString("The generated string"));
            assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size(), is(1));
            assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size(), is(1));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void docs() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());

            DocumentationWithSchema doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/io.kestra.plugin.templates.ExampleTask"),
                DocumentationWithSchema.class
            );

            assertThat(doc.getMarkdown(), containsString("io.kestra.plugin.templates.ExampleTask"));
            assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size(), is(5));
            assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size(), is(1));
        });
    }

    @Test
    void docWithAlert() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());

            DocumentationWithSchema doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/io.kestra.plugin.core.state.Set"),
                DocumentationWithSchema.class
            );

            assertThat(doc.getMarkdown(), containsString("io.kestra.plugin.core.state.Set"));
            assertThat(doc.getMarkdown(), containsString("::: warning\n"));
        });
    }


    @SuppressWarnings("unchecked")
    @Test
    void taskWithBase() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());

            DocumentationWithSchema doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/io.kestra.plugin.templates.ExampleTask?all=true"),
                DocumentationWithSchema.class
            );

            Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) doc.getSchema().getProperties().get("properties");

            assertThat(doc.getMarkdown(), containsString("io.kestra.plugin.templates.ExampleTask"));
            assertThat(properties.size(), is(17));
            assertThat(properties.get("id").size(), is(4));
            assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size(), is(1));
        });
    }

    @Test
    void flow() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());
            Map<String, Object> doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/schemas/flow"),
                Argument.mapOf(String.class, Object.class)
            );

            assertThat(doc.get("$ref"), is("#/definitions/io.kestra.core.models.flows.Flow"));
        });
    }

    @Test
    void template() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());
            Map<String, Object> doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/schemas/template"),
                Argument.mapOf(String.class, Object.class)
            );

            assertThat(doc.get("$ref"), is("#/definitions/io.kestra.core.models.templates.Template"));
        });
    }

    @Test
    void task() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());
            Map<String, Object> doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/schemas/task"),
                Argument.mapOf(String.class, Object.class)
            );

            assertThat(doc.get("$ref"), is("#/definitions/io.kestra.core.models.tasks.Task"));
        });
    }

    @Test
    void inputs() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());
            List<InputType> doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/inputs"),
                Argument.listOf(InputType.class)
            );

            assertThat(doc.size(), is(18));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void input() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL());
            DocumentationWithSchema doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/inputs/STRING"),
                DocumentationWithSchema.class
            );

            assertThat(doc.getSchema().getProperties().size(), is(3));
            Map<String, Object> properties = (Map<String, Object>) doc.getSchema().getProperties().get("properties");
            assertThat(properties.size(), is(8));
//            assertThat(((Map<String, Object>) properties.get("name")).get("$deprecated"), is(true));
        });
    }
}
