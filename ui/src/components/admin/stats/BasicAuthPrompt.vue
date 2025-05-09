<template>
    <div class="d-flex" v-if="!configs.isBasicAuthEnabled">
        <el-text>
            <span v-html="$t('data_not_protected')" />
        </el-text>
        <el-button class="ms-auto" @click="promptForCredentials">
            <b>{{ $t('activate_basic_auth') }}</b>
        </el-button>

        <el-dialog v-if="promptOAuthCredentials" v-model="promptOAuthCredentials" destroy-on-close :append-to-body="true">
            <template #header>
                <span v-html="$t('configure basic auth')" />
            </template>
            <el-form label-position="top" :rules="rules" :model="form" ref="form" @submit.prevent="false">
                <el-form-item
                    :label="$t('email')"
                    required
                    prop="email"
                >
                    <el-input v-model="form.email" />
                </el-form-item>
                <el-form-item
                    :label="$t('password')"
                    required
                    prop="password"
                >
                    <el-input v-model="form.password" type="password" show-password />
                </el-form-item>
                <el-form-item
                    :label="$t('confirm password')"
                    required
                    prop="confirmPassword"
                >
                    <el-input v-model="form.confirmPassword" type="password" show-password />
                </el-form-item>

                <div class="bottom-buttons">
                    <div class="right-align">
                        <el-form-item class="submit">
                            <el-button @click="onSubmit($refs.form)" type="primary" native-type="submit">
                                {{ $t('save') }}
                            </el-button>
                        </el-form-item>
                    </div>
                </div>
            </el-form>
        </el-dialog>
    </div>
</template>
<script>
    import {mapGetters} from "vuex";

    export default {
        data() {
            return {
                form: {
                    email: undefined,
                    password: undefined,
                    confirmPassword: undefined
                },
                rules: {
                    email: [
                        {
                            message: "Please input correct email address",
                            trigger: ["blur"],
                            pattern: "^$|^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$"
                        },
                        {
                            validator: (rule, value, callback) => {
                                if (value && value.length > 256) {
                                    callback(new Error(this.$t("email length constraint")));
                                } else {
                                    callback();
                                }
                            },
                            trigger: ["blur", "change"]
                        }
                    ],
                    password: [
                        {
                            validator: (rule, value, callback) => {
                                if (value && value.length > 256) {
                                    callback(new Error(this.$t("password length constraint")));
                                } else if (value && value.trim() === "") {
                                    callback(new Error(this.$t("password empty constraint")));
                                } else {
                                    callback();
                                }
                            },
                            trigger: ["blur", "change"]
                        }
                    ],
                    confirmPassword: [
                        {
                            validator: (rule, value, callback) => {
                                if (value !== this.form.password) {
                                    callback(new Error(this.$t("passwords do not match")));
                                } else {
                                    callback();
                                }
                            },
                            trigger: "blur"
                        }
                    ]
                },
                promptOAuthCredentials: false
            }
        },
        computed: {
            ...mapGetters("misc", ["configs"])
        },
        methods: {
            onSubmit(formRef) {
                return formRef.validate(async (valid) => {
                    if (!valid) {
                        return false;
                    }

                    await this.$store.dispatch("misc/addBasicAuth", {
                        username: this.form.email,
                        password: this.form.password
                    });

                    location.reload();
                });
            },
            promptForCredentials() {
                this.promptOAuthCredentials = true;
            }
        }
    };
</script>

<style scoped>
    .no-hover {
        outline: 0;
        border: 0;
    }
</style>