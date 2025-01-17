import {defineComponent} from "vue";
import type {RouteRecordName, RouteParams} from "vue-router";

export type Schemas = {
    $ref?: string;
    $schema?: string;
    definitions?: {
        [key: string]: object;
    };
};

export type Field = {
    component: ReturnType<typeof defineComponent>;
    value: any;
    label: string;
    required?: boolean;
    disabled?: boolean;
};

export type PairField = Omit<Field, "value"> & {
    value: Record<string, string>;
    property: string;
};

type InputField = Field & {
    inputs: any[];
};

type ConcurrencyField = Field & {
    root: string;
    schema: object;
};

type EditorField = Field & {
    navbar: boolean;
    input: boolean;
    lang: string;
    style: {
        height: string;
    };
};

export type Fields = {
    id: Field;
    namespace: Field;
    description: Field;
    retry: EditorField;
    labels: PairField;
    inputs: InputField;
    outputs: EditorField;
    variables: PairField;
    concurrency?: ConcurrencyField; // TODO: Make it not optional
    pluginDefaults: EditorField;
    disabled: Field;
};

export type Breadcrumb = {
    label: string;
    to: {
        name: RouteRecordName;
        params: RouteParams;
    };
};

export type CollapseItem = {
    title: string;
    elements?: Record<string, any>[];
};
