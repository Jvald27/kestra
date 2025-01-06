<template>
    <div data-component="FILENAME_PLACEHOLDER">
        <span class="markdown" v-html="markdownRenderer" />
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, watch} from "vue";
    import * as Markdown from "../../utils/markdown";

    const props = withDefaults(
        defineProps<{
            watches?: string[];
            source?: string;
            permalink?: boolean;
            fontSizeVar?: string;
        }>(),
        {
            watches: () => ["source", "show", "toc"],
            source: "",
            permalink: false,
            fontSizeVar: "font-size-sm"
        })


    const markdownRenderer = ref<string | null>(null);

    onMounted(async ()=> {
        markdownRenderer.value = await renderMarkdown();
    })

    watch(() => props.source, async () => {

        markdownRenderer.value = await renderMarkdown();

    })

    async function renderMarkdown() {
        return  await Markdown.render(props.source, {
            permalink: props.permalink,
        });
    }

    const fontSizeCss = computed(() =>{
        return `var(--${props.fontSizeVar})`;
    })
    const permalinkCss = computed(() => {
        return props.permalink ? "-20px" : "0";
    })
</script>

<style lang="scss">
    .markdown {
        font-size: v-bind(fontSizeCss);

        table {
            border-collapse: collapse;
            width: 100%;
            color: var(--ks-content-primary);
        }

        table,
        th {
            border-bottom: 2px solid var(--ks-border-primary);
        }

        th,
        td {
            padding: 0.5em;
        }

        th {
            text-align: left;
        }

        a.header-anchor {
            color: var(--ks-content-secondary);
            font-size: var(--font-size-base);
            font-weight: normal;
        }

        .warning {
            background-color: var(--ks-background-warning);
            border: 1px solid var(--ks-border-warning);
            color: var(--ks-content-warning);
            padding: 8px 16px;
            border-radius: var(--el-border-radius-base);
            margin-bottom: 1rem;

            p:last-child {
                margin-bottom: 0;
            }
        }

        .info {
            background-color: var(--ks-background-info);
            border: 1px solid var(--ks-border-info);
            color: var(--ks-content-info);
            padding: 8px 16px;
            border-radius: var(--el-border-radius-base);
            margin-bottom: 1rem;

            p:last-child {
                margin-bottom: 0;
            }
        }

        pre {
            border-radius: var(--bs-border-radius-lg);
            border: 1px solid var(--ks-border-primary);
        }

        blockquote {
            margin-top: 0;
        }

        mark {
            background: var(--ks-background-success);
            color: var(--ks-content-success);
            font-size: var(--font-size-sm);
            padding: 2px 8px 2px 8px;
            border-radius: var(--bs-border-radius-sm);

            * {
                color: var(--ks-content-success) !important;
            }
        }

        h2 {
            margin-top: 2rem;
        }

        h3 {
            margin-top: 1.5rem;
        }

        h4 {
            margin-top: 1.25rem;
        }

        h2, h3, h4, h5 {
            margin-left: v-bind(permalinkCss);

            .header-anchor {
                opacity: 0;
                transition: all ease 0.2s;
            }

            &:hover {
                .header-anchor {
                    opacity: 1;
                }
            }
            padding: 5px ;
            border-left: 4px solid var(--ks-border-primary);
        }

        strong > code,
        li > code,
        td > code,
        p > code{
            border-radius: var(--bs-border-radius-sm);
            border: 1px solid var(--ks-border-primary);
            color: var(--ks-content-primary);
        }

        h3, h4, h5 {
            code {
                background: var(--ks-background-card);
                font-size: 0.65em;
                padding: 2px 8px;
                font-weight: 400;
                border-radius: var(--bs-border-radius-sm);
                border: 1px solid var(--ks-border-primary);
                color: var(--ks-content-primary);
            }
        }
    }
    .markdown-tooltip {
        *:last-child {
            margin-bottom: 0;
        }
        line-height: 15px;
        padding: 5px;
    }
</style>
