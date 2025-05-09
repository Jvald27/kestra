package io.kestra.core.docs;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.tasks.logs.LogExporter;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.plugins.PluginClassAndMetadata;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.runners.pebble.Extension;
import io.kestra.core.runners.pebble.JsonWriter;
import io.kestra.core.runners.pebble.filters.*;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Slugify;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
public class DocumentationGenerator {
    private static final PebbleEngine PEBBLE_ENGINE;

    @Inject
    JsonSchemaGenerator jsonSchemaGenerator;

    static {
        ClasspathLoader classpathLoader = new ClasspathLoader();
        classpathLoader.setPrefix("docs/");

        PEBBLE_ENGINE = new PebbleEngine.Builder()
            .newLineTrimming(false)
            .loader(classpathLoader)
            .extension(new AbstractExtension() {
                @Override
                public Map<String, Filter> getFilters() {
                    Map<String, Filter> filters = new HashMap<>();
                    filters.put("json", new ToJsonFilter());
                    return filters;
                }
            })
            .autoEscaping(false)
            .extension(new Extension())
            .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Document> generate(RegisteredPlugin registeredPlugin) throws Exception {
        ArrayList<Document> result = new ArrayList<>();

        result.addAll(index(registeredPlugin));

        result.addAll(this.generate(registeredPlugin, registeredPlugin.getTasks(), Task.class, "tasks"));
        result.addAll(this.generate(registeredPlugin, registeredPlugin.getTriggers(), AbstractTrigger.class, "triggers"));
        result.addAll(this.generate(registeredPlugin, registeredPlugin.getConditions(), Condition.class, "conditions"));
        //noinspection unchecked
        result.addAll(this.generate(registeredPlugin, registeredPlugin.getTaskRunners(), (Class) TaskRunner.class, "task-runners"));
        result.addAll(this.generate(registeredPlugin, registeredPlugin.getLogExporters(), (Class) LogExporter.class, "log-exporters"));

        result.addAll(guides(registeredPlugin));

        return result;
    }

    private static List<Document> index(RegisteredPlugin plugin) throws IOException {
        Map<SubGroup, Map<String, List<ClassPlugin>>> groupedClass = DocumentationGenerator.indexGroupedClass(plugin);


        if (groupedClass.isEmpty()) {
            return Collections.emptyList();
        }

        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();

        builder.put("title", plugin.title().replace("plugin-", ""));

        if (plugin.description() != null) {
            builder.put("description", plugin.description());
        }

        if (plugin.license() != null) {
            builder.put("docLicense", plugin.license());
        }

        if (plugin.longDescription() != null) {
            builder.put("longDescription", plugin.longDescription());
        }

        builder.put("group", plugin.group());
        builder.put("classPlugins", groupedClass);

        if (plugin.icon("plugin-icon") != null) {
            builder.put("icon", plugin.icon("plugin-icon"));
        }

        if(!plugin.getGuides().isEmpty()) {
            builder.put("guides", plugin.getGuides());
        }

        return Collections.singletonList(new Document(
            docPath(plugin),
            render("index", builder.build()),
            plugin.icon("plugin-icon"),
            null
        ));
    }

    private static Map<SubGroup, Map<String, List<ClassPlugin>>> indexGroupedClass(RegisteredPlugin plugin) {
        return plugin.allClassGrouped()
            .entrySet()
            .stream()
            .filter(r -> !r.getKey().equals("controllers") && !r.getKey().equals("storages"))
            .flatMap(entry -> entry.getValue()
                .stream()
                .map(cls -> {
                    ClassPlugin.ClassPluginBuilder builder = ClassPlugin.builder()
                        .name(cls.getName())
                        .simpleName(cls.getSimpleName())
                        .type(entry.getKey());
                    if (cls.getPackageName().startsWith(plugin.group())) {
                        var pluginSubGroup = cls.getPackage().getDeclaredAnnotation(PluginSubGroup.class);
                        var subGroupName =  cls.getPackageName().length() > plugin.group().length() ?
                            cls.getPackageName().substring(plugin.group().length() + 1) : "";
                        var subGroupTitle = pluginSubGroup != null ? pluginSubGroup.title() : subGroupName;
                        var subGroupDescription = pluginSubGroup != null ? pluginSubGroup.description() : null;
                        // hack to avoid adding the subgroup in the task URL when it's the group to keep search engine indexes
                        var subgroupIsGroup = cls.getPackageName().length() <= plugin.group().length();
                        var subGroupIcon = plugin.icon(cls.getPackageName());
                        var subgroup = new SubGroup(subGroupName, subGroupTitle, subGroupDescription, subGroupIcon, subgroupIsGroup);
                        builder.subgroup(subgroup);
                    } else {
                        // should never occur
                        builder.subgroup(new SubGroup(""));
                    }

                    return builder.build();
                }))
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.comparing(ClassPlugin::getSubgroup)
                .thenComparing(ClassPlugin::getType)
                .thenComparing(ClassPlugin::getName)
            )
            .collect(Collectors.groupingBy(
                ClassPlugin::getSubgroup,
                Collectors.groupingBy(classPlugin -> Slugify.toStartCase(classPlugin.getType()))
            ));
    }


    @AllArgsConstructor
    @Getter
    @Builder
    public static class ClassPlugin {
        String name;
        String simpleName;
        SubGroup subgroup;
        String group;
        String type;
    }

    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode(of = "name")
    public static class SubGroup implements Comparable<SubGroup>{
        String name;
        String title;
        String description;
        String icon;

        boolean subgroupIsGroup;

        SubGroup(String name) {
            this.name = name;
        }

        @Override
        public int compareTo(SubGroup o) {
            return name.compareTo(o.getName());
        }
    }

    private static List<Document> guides(RegisteredPlugin plugin) throws Exception {
        String pluginName = Slugify.of(plugin.title());

        return plugin
            .guides()
            .entrySet()
            .stream()
            .map(throwFunction(e -> new Document(
                pluginName + "/guides/" + e.getKey()  + ".md",
                e.getValue(),
                null,
                null
            )))
            .toList();
    }

    private <T> List<Document> generate(RegisteredPlugin registeredPlugin, List<Class<? extends T>> cls, Class<T> baseCls, String type) {
        return cls
            .stream()
            .map(pluginClass -> {
                PluginClassAndMetadata<T> metadata = PluginClassAndMetadata.create(
                    registeredPlugin,
                    pluginClass,
                    baseCls,
                    null
                );
                return ClassPluginDocumentation.of(jsonSchemaGenerator, metadata, true);
            })
            .map(pluginDocumentation -> {
                try {
                    return new Document(
                        docPath(registeredPlugin, type, pluginDocumentation),
                        render(pluginDocumentation),
                        pluginDocumentation.getIcon(),
                        new Schema(pluginDocumentation.getPropertiesSchema(), pluginDocumentation.getOutputsSchema(), pluginDocumentation.getDefs())
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .toList();
    }

    private static String docPath(RegisteredPlugin registeredPlugin) {
        String pluginName = Slugify.of(registeredPlugin.path());

        return pluginName + "/index.md";
    }

    private static <T> String docPath(RegisteredPlugin registeredPlugin, String type, ClassPluginDocumentation<T> classPluginDocumentation) {
        String pluginName = Slugify.of(registeredPlugin.path());

        return pluginName + "/" + type + "/" +
            (classPluginDocumentation.getSubGroup() != null ? classPluginDocumentation.getSubGroup() + "/" : "") +
            classPluginDocumentation.getCls() + ".md";
    }

    public static String render(ClassPluginDocumentation<?> classPluginDocumentation) throws IOException {
        return render("task", JacksonMapper.toMap(classPluginDocumentation));
    }

    public static String render(AbstractClassDocumentation classInputDocumentation) throws IOException {
        return render("task", JacksonMapper.toMap(classInputDocumentation));
    }

    public static String render(String templateName, Map<String, Object> vars) throws IOException {
        String pebbleTemplate = IOUtils.toString(
            Objects.requireNonNull(DocumentationGenerator.class.getClassLoader().getResourceAsStream("docs/" + templateName + ".peb")),
            StandardCharsets.UTF_8
        );

        PebbleTemplate compiledTemplate = PEBBLE_ENGINE.getLiteralTemplate(pebbleTemplate);

        Writer writer = new JsonWriter();
        compiledTemplate.evaluate(writer, vars);
        String renderer = writer.toString();

        // vuepress {{ }} evaluation
        Pattern pattern = Pattern.compile("`\\{\\{(.*?)\\}\\}`", Pattern.MULTILINE);
        renderer = pattern.matcher(renderer).replaceAll("<code v-pre>{{ $1 }}</code>");

        return renderer;
    }
}
