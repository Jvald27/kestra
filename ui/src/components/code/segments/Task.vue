<template>
    <TaskEditor v-model="yaml" :section @update:model-value="validateTask" />

    <template v-if="yaml">
        <hr>

        <div class="d-flex justify-content-between">
            <div class="d-flex align-items-center">
                <ValidationError :errors link />
            </div>

            <Save @click="saveTask" :what="route.query.section?.toString()" />
        </div>
    </template>
</template>

<script setup lang="ts">
    import {ref, watch, computed} from "vue";

    const emits = defineEmits(["updateTask"]);
    const props = defineProps({
        flow: {type: String, required: true},
        creation: {type: Boolean, default: false},
    });

    import {useRoute} from "vue-router";
    const route = useRoute();

    import {SECTIONS} from ".././../../utils/constants";
    const section = ref(SECTIONS.TASKS);

    import TaskEditor from "../../../components/flows/TaskEditor.vue";
    import YamlUtils from "../../../utils/yamlUtils";

    const yaml = ref(
        YamlUtils.extractTask(props.flow, route.query.identifier)?.toString() || "",
    );

    watch(
        () => route.query.section,
        (value) => {
            section.value = SECTIONS[value === "triggers" ? "TRIGGERS" : "TASKS"];
        },
        {immediate: true},
    );

    watch(
        () => route.query.identifier,
        (value) => {
            if (value === "new") {
                yaml.value = "";
            } else {
                yaml.value =
                    YamlUtils.extractTask(props.flow, value)?.toString() || "";
            }
        },
        {immediate: true},
    );

    import ValidationError from "../../../components/flows/ValidationError.vue";

    const CURRENT = ref(null);
    const validateTask = (task) => {
        store
            .dispatch("flow/validateTask", {task, section: section.value})
            .then(() => (yaml.value = task));

        CURRENT.value = task;
    };

    import {useStore} from "vuex";
    const store = useStore();

    const errors = computed(() => store.getters["flow/taskError"]);

    import Save from "../components/Save.vue";
    const saveTask = () => {
        const source = props.flow;

        const task = YamlUtils.extractTask(
            yaml.value,
            YamlUtils.parse(yaml.value).id,
        );

        if (route.query.section === SECTIONS.TRIGGERS.toLowerCase()) {
            const existingTask = YamlUtils.checkTaskAlreadyExist(
                source,
                CURRENT.value,
            );
            if (existingTask) {
                store.dispatch("core/showMessage", {
                    variant: "error",
                    title: "Trigger Id already exist",
                    message: `Trigger Id ${existingTask} already exist in the flow.`,
                });
                return;
            }

            emits("updateTask", YamlUtils.insertTrigger(source, CURRENT.value));
            CURRENT.value = null;
        } else {
            const action = props.creation
                ? YamlUtils.insertTask(
                    source,
                    YamlUtils.getLastTask(source),
                    task,
                    "after",
                )
                : YamlUtils.replaceTaskInDocument(
                    source,
                    route.query.identifier,
                    task,
                );

            emits("updateTask", action);
        }
    };
</script>
