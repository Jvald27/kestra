<template>
    <div class="p-4">
        <template v-if="!route.query.section && !route.query.identifier">
            <component
                v-for="([k, v], index) in Object.entries(fields)"
                :key="index"
                :is="v.component"
                v-model="v.value"
                v-bind="trimmed(v)"
                @update:model-value="emits('updateMetadata', k, v.value)"
            />

            <hr class="m-0 mt-3">

            <Collapse :items="sections" creation />
        </template>

        <Task
            v-else
            :flow
            :creation
            @update-task="(yaml) => emits('updateTask', yaml)"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, shallowRef} from "vue";

    import {Field, Fields, CollapseItem} from "../utils/types";

    import Collapse from "../components/collapse/Collapse.vue";
    import InputText from "../components/inputs/InputText.vue";
    import InputSwitch from "../components/inputs/InputSwitch.vue";
    import InputPair from "../components/inputs/InputPair.vue";

    import Editor from "../../inputs/Editor.vue";
    import MetadataInputs from "../../flows/MetadataInputs.vue";

    import Task from "./Task.vue";

    // const CONCURRENCY = "io.kestra.core.models.flows.Concurrency";

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const emits = defineEmits(["updateTask", "updateMetadata"]);

    const props = defineProps({
        creation: {type: Boolean, default: false},
        flow: {type: String, required: true},
        metadata: {type: Object, required: true},
        schemas: {type: Object, required: true},
    });

    const trimmed = (field: Field) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const {component, value, ...rest} = field;

        return rest;
    };

    const fields = ref<Fields>({
        id: {
            component: shallowRef(InputText),
            value: props.metadata.id,
            label: t("no_code.fields.main.flow_id"),
            required: true,
            disabled: !props.creation,
        },
        namespace: {
            component: shallowRef(InputText),
            value: props.metadata.namespace,
            label: t("no_code.fields.main.namespace"),
            required: true,
            disabled: !props.creation,
        },
        description: {
            component: shallowRef(InputText),
            value: props.metadata.description,
            label: t("no_code.fields.main.description"),
        },
        retry: {
            component: shallowRef(Editor),
            value: props.metadata.retry,
            label: t("no_code.fields.general.retry"),
            navbar: false,
            input: true,
            lang: "yaml",
            style: {height: "100px"},
        },
        labels: {
            component: shallowRef(InputPair),
            value: props.metadata.labels,
            label: t("no_code.fields.general.labels"),
            property: t("no_code.labels.label"),
        },
        inputs: {
            component: shallowRef(MetadataInputs),
            value: props.metadata.inputs,
            label: t("no_code.fields.general.inputs"),
            inputs: props.metadata.inputs,
        },
        outputs: {
            component: shallowRef(Editor),
            value: props.metadata.outputs,
            label: t("no_code.fields.general.outputs"),
            navbar: false,
            input: true,
            lang: "yaml",
            style: {height: "100px"},
        },
        variables: {
            component: shallowRef(InputPair),
            value: props.metadata.variables,
            label: t("no_code.fields.general.variables"),
            property: t("no_code.labels.variable"),
        },
        // concurrency: {
        //     component: shallowRef(InputSwitch), // TODO: To improve slot content
        //     value: props.metadata.concurrency,
        //     label: t("no_code.fields.general.concurrency"),
        //     schema: props.schemas?.definitions?.[CONCURRENCY] ?? {},
        //     root: "concurrency",
        // },
        pluginDefaults: {
            component: shallowRef(Editor),
            value: props.metadata.pluginDefaults,
            label: t("no_code.fields.general.plugin_defaults"),
            navbar: false,
            input: true,
            lang: "yaml",
            style: {height: "100px"},
        },
        disabled: {
            component: shallowRef(InputSwitch),
            value: props.metadata.disabled,
            label: t("no_code.fields.general.disabled"),
        },
    });

    import YamlUtils from "../../../utils/yamlUtils";
    const getSectionTitle = (label: string, elements = []) => {
        const title = t(`no_code.sections.${label}`);
        return {title, elements};
    };
    const sections = ref<CollapseItem[]>([
        getSectionTitle("tasks", YamlUtils.parse(props.flow).tasks ?? []),
        getSectionTitle("triggers", YamlUtils.parse(props.flow).triggers ?? []),
        getSectionTitle("error_handlers", YamlUtils.parse(props.flow).errors ?? []),
    ]);
</script>
