<template>
    <div @click="emits('add', props.what)" class="py-2 adding">
        {{ t("no_code.adding", {what: props.what}) }}
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const emits = defineEmits(["add"]);
    const props = defineProps({what: {type: String, required: true}});
</script>

<style scoped lang="scss">
@import "../styles/code.scss";

.adding {
    cursor: pointer;
    color: $code-gray-700;
    font-size: $code-font-sm;
}
</style>
