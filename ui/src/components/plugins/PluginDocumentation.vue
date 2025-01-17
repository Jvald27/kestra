<template>
    <div class="plugin-doc">
        <template v-if="editorPlugin">
            <div class="d-flex gap-3 mb-3 align-items-center">
                <task-icon
                    class="plugin-icon"
                    :cls="editorPlugin.cls"
                    only-icon
                    :icons="icons"
                />
                <h4 class="mb-0">
                    {{ pluginName }}
                </h4>
            </div>
            <Suspense>
                <schema-to-html class="plugin-schema" :schema="editorPlugin.schema" :plugin-type="editorPlugin.cls">
                    <template #markdown="{content}">
                        <markdown font-size-var="font-size-base" :source="content" />
                    </template>
                </schema-to-html>
            </Suspense>
        </template>
        <markdown v-else :source="introContent" />
    </div>
</template>

<script>
    import Markdown from "../layout/Markdown.vue";
    import {SchemaToHtml, TaskIcon} from "@kestra-io/ui-libs";
    import {mapState} from "vuex";
    import intro from "../../assets/documentations/basic.md?raw"

    export default {
        components: {Markdown, SchemaToHtml, TaskIcon},
        props: {
            overrideIntro: {
                type: String,
                default: null
            }
        },
        computed: {
            ...mapState("plugin", ["editorPlugin", "icons"]),
            introContent () {
                return this.overrideIntro ?? intro
            },
            pluginName() {
                const split = this.editorPlugin.cls.split(".");
                return split[split.length - 1];
            }
        },
        created() {
            this.$store.dispatch("plugin/list");
        }
    }
</script>

<style scoped lang="scss">
    @use "@kestra-io/ui-libs/src/scss/color-palette" as color-palette;

    .plugin-doc {
        background-color: var(--ks-background-body) !important;

        :deep(.plugin-title) {
            font-size: 1.25em;
        }

        .plugin-icon {
            width: 25px;
            height: 25px;
        }

        .plugin-schema {
            :deep(.code-block) {
                background-color: var(--ks-background-card);
                border: 1px solid var(--ks-border-primary)
            }

            :deep(.language) {
                color: var(--ks-content-tertiary);
            }

            :deep(.plugin-section) {
                p {
                    margin-bottom: 0;
                }
                .collapse-button {
                    padding: 3px 0;
                    font-size: var(--font-size-lg);
                    line-height: 1.5rem;

                    & .material-design-icon, & .material-design-icon * {
                        height: 1.5rem;
                        width: 1.5rem;
                    }
                }

                > .collapse-button:not(.collapsed) {
                    color: color-palette.$base-purple-200;
                }

                .property:not(:has(.collapsed)) {
                    background-color: color-palette.$base-gray-900;
                }
            }
        }
    }
</style>