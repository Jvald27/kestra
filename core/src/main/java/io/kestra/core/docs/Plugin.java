package io.kestra.core.docs;

import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.core.plugins.RegisteredPlugin;
import io.micronaut.core.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@NoArgsConstructor
@Data
public class Plugin {
    private String name;
    private String title;
    private String description;
    private String license;
    private String longDescription;
    private String group;
    private String version;
    private Map<String, String> manifest;
    private List<String> tasks;
    private List<String> triggers;
    private List<String> conditions;
    private List<String> controllers;
    private List<String> storages;
    private List<String> secrets;
    private List<String> taskRunners;
    private List<String> guides;
    private List<String> aliases;
    private List<String> apps;
    private List<String> appBlocks;
    private List<String> charts;
    private List<String> dataFilters;
    private List<String> logExporters;
    private List<PluginSubGroup.PluginCategory> categories;
    private String subGroup;

    public static Plugin of(RegisteredPlugin registeredPlugin, @Nullable String subgroup) {
        return Plugin.of(registeredPlugin, subgroup, true);
    }

    public static Plugin of(RegisteredPlugin registeredPlugin, @Nullable String subgroup, boolean includeDeprecated) {
        Plugin plugin = new Plugin();
        plugin.name = registeredPlugin.name();
        PluginSubGroup subGroupInfos = null;
        if (subgroup == null) {
            plugin.title = registeredPlugin.title();
        } else {
            subGroupInfos = registeredPlugin.allClass().stream()
                .filter(c -> c.getPackageName().contains(subgroup))
                .min(Comparator.comparingInt(a -> a.getPackageName().length()))
                .map(clazz -> clazz.getPackage().getDeclaredAnnotation(PluginSubGroup.class))
                .orElseThrow();
            plugin.title = !subGroupInfos.title().isEmpty() ? subGroupInfos.title() : subgroup.substring(subgroup.lastIndexOf('.') + 1);
        }
        plugin.group = registeredPlugin.group();
        plugin.description = subGroupInfos != null && !subGroupInfos.description().isEmpty() ? subGroupInfos.description() : registeredPlugin.description();
        plugin.license = registeredPlugin.license();
        plugin.longDescription = registeredPlugin.longDescription();
        plugin.version = registeredPlugin.version();
        plugin.guides = registeredPlugin.getGuides();
        plugin.aliases = registeredPlugin.getAliases().values().stream().map(Map.Entry::getKey).toList();
        plugin.manifest = registeredPlugin
            .getManifest()
            .getMainAttributes()
            .entrySet()
            .stream()
            .map(e -> new AbstractMap.SimpleEntry<>(
                e.getKey().toString(),
                e.getValue().toString()
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        plugin.categories = subGroupInfos != null ?
            Arrays.stream(subGroupInfos.categories()).toList() :
            registeredPlugin
                .allClass()
                .stream()
                .map(clazz -> clazz.getPackage().getDeclaredAnnotation(PluginSubGroup.class))
                .filter(Objects::nonNull)
                .flatMap(r -> Arrays.stream(r.categories()))
                .distinct()
                .toList();

        plugin.subGroup = subgroup;

        Predicate<Class<?>> packagePredicate = c -> subgroup == null || c.getPackageName().equals(subgroup);
        plugin.tasks = filterAndGetClassName(registeredPlugin.getTasks(), includeDeprecated, packagePredicate).stream().toList();
        plugin.triggers = filterAndGetClassName(registeredPlugin.getTriggers(), includeDeprecated, packagePredicate).stream().toList();
        plugin.conditions = filterAndGetClassName(registeredPlugin.getConditions(), includeDeprecated, packagePredicate).stream().toList();
        plugin.storages = filterAndGetClassName(registeredPlugin.getStorages(), includeDeprecated, packagePredicate).stream().toList();
        plugin.secrets = filterAndGetClassName(registeredPlugin.getSecrets(), includeDeprecated, packagePredicate).stream().toList();
        plugin.taskRunners = filterAndGetClassName(registeredPlugin.getTaskRunners(), includeDeprecated, packagePredicate).stream().toList();
        plugin.apps = filterAndGetClassName(registeredPlugin.getApps(), includeDeprecated, packagePredicate).stream().toList();
        plugin.appBlocks = filterAndGetClassName(registeredPlugin.getAppBlocks(), includeDeprecated, packagePredicate).stream().toList();
        plugin.charts = filterAndGetClassName(registeredPlugin.getCharts(), includeDeprecated, packagePredicate).stream().toList();
        plugin.dataFilters = filterAndGetClassName(registeredPlugin.getDataFilters(), includeDeprecated, packagePredicate).stream().toList();
        plugin.logExporters = filterAndGetClassName(registeredPlugin.getLogExporters(), includeDeprecated, packagePredicate).stream().toList();

        return plugin;
    }

    /**
     * Filters the given list of class all internal Plugin, as well as, all legacy org.kestra classes.
     * Those classes are only filtered from the documentation to ensure backward compatibility.
     *
     * @param list              The list of classes?
     * @param includeDeprecated whether to include deprecated plugins or not
     * @return a filtered streams.
     */
    private static List<String> filterAndGetClassName(final List<? extends Class<?>> list, boolean includeDeprecated, Predicate<Class<?>> clazzFilter) {
        return list
            .stream()
            .filter(not(io.kestra.core.models.Plugin::isInternal))
            .filter(p -> includeDeprecated || !io.kestra.core.models.Plugin.isDeprecated(p))
            .filter(clazzFilter)
            .map(Class::getName)
            .filter(c -> !c.startsWith("org.kestra."))
            .toList();
    }
}
