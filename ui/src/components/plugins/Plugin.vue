<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo?.breadcrumb" />
    <template v-if="!pluginIsSelected">
        <plugin-home v-if="plugins" :plugins="plugins" />
    </template>
    <docs-layout v-else>
        <template #menu>
            <Toc @router-change="onRouterChange" v-if="plugins" :plugins="plugins.filter(p => !p.subGroup)" />
        </template>
        <template #content>
            <div class="plugin-doc">
                <div class="versions" v-if="versions?.length > 0">
                    <el-select
                        v-model="version"
                        placeholder="Version"
                        size="small"
                        :disabled="versions?.length === 1"
                        @change="selectVersion(version)"
                    >
                        <template #label="{value}">
                            <span>Version: </span>
                            <span style="font-weight: bold">{{ value }}</span>
                        </template>
                        <el-option
                            v-for="item in versions"
                            :key="item"
                            :label="item"
                            :value="item"
                        />
                    </el-select>
                </div>
                <div class="d-flex gap-3 mb-3 align-items-center">
                    <task-icon
                        class="plugin-icon"
                        :cls="$route.params.cls"
                        only-icon
                        :icons="icons"
                    />
                    <h4 class="mb-0">
                        {{ pluginName }}
                    </h4>
                </div>
                <Suspense v-loading="isLoading">
                    <schema-to-html class="plugin-schema" :dark-mode="theme === 'dark'" :schema="plugin.schema" :props-initially-expanded="true" :plugin-type="$route.params.cls">
                        <template #markdown="{content}">
                            <markdown font-size-var="font-size-base" :source="content" />
                        </template>
                    </schema-to-html>
                </Suspense>
            </div>
        </template>
    </docs-layout>
</template>

<script setup>
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {SchemaToHtml} from "@kestra-io/ui-libs";
    import DocsLayout from "../docs/DocsLayout.vue";
    import PluginHome from "./PluginHome.vue";
    import Markdown from "../layout/Markdown.vue"
    import Toc from "./Toc.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import {mapState, mapGetters} from "vuex";

    export default {
        mixins: [RouteContext],
        computed: {
            ...mapState("plugin", ["plugin", "plugins",  "icons", "versions"]),
            ...mapGetters("misc", ["theme"]),
            routeInfo() {
                return {
                    title: this.$route.params.cls ? this.$route.params.cls : this.$t("plugins.names"),
                    breadcrumb: !this.$route.params.cls ? undefined : [
                        {
                            label: this.$t("plugins.names"),
                            link: {
                                name: "plugins/list"
                            }
                        }
                    ]
                }
            },
            pluginName() {
                const split = this.$route.params.cls.split(".");
                return split[split.length - 1];
            },
            pluginIsSelected() {
                return this.plugin && this.$route.params.cls
            }
        },
        data() {
            return {
                isLoading: false,
                version: undefined
            };
        },
        created() {
            this.loadToc();
            this.loadPlugin()
        },
        watch: {
            $route(newValue, _oldValue) {
                if (newValue.name.startsWith("plugins/")) {
                    this.onRouterChange();
                }
            }
        },
        methods: {
            loadToc() {
                this.$store.dispatch("plugin/listWithSubgroup", {
                    includeDeprecated: false
                })
            },

            selectVersion(version) {
                this.$router.push({name: "plugins/view", params: {cls: this.$route.params.cls, version: version}});
            },

            loadPlugin() {
                if (this.$route.params.version) {
                    this.version = this.$route.params.version;
                }
                const params = {...this.$route.params};
                if (params.cls) {
                    this.isLoading = true;
                    Promise.all([
                        this.$store.dispatch("plugin/load", params),
                        this.$store.dispatch("plugin/loadVersions", params)
                            .then(data => {
                                if (data.versions && data.versions.length > 0) {
                                    if (this.version === undefined) {
                                        this.version = data.versions[0];
                                    }
                                }
                            })
                    ]).finally(() => {
                        this.isLoading = false
                    });
                }
            },

            onRouterChange() {
                window.scroll({
                    top: 0,
                    behavior: "smooth"
                })
                this.loadPlugin();
            }
        }
    };
</script>

<style scoped lang="scss">
    @import "../../styles/components/plugin-doc";
    .versions {
      min-width: 200px;
      display: inline-grid;
      float: right;
    }
</style>
