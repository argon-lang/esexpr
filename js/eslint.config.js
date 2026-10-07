import js from "@eslint/js";
import { defineConfig, globalIgnores } from "eslint/config";
import tseslint from "typescript-eslint";

export default defineConfig([
    globalIgnores(["lib/**"]),
    {
        files: ["src/**/*.ts"],
        extends: [js.configs.recommended, tseslint.configs.recommended],
        rules: {
            // Namespaces organize the public codec API alongside its types.
            "@typescript-eslint/no-namespace": "off",
            "@typescript-eslint/no-unused-vars": ["error", {
                argsIgnorePattern: "^_",
                varsIgnorePattern: "^_",
                caughtErrorsIgnorePattern: "^_",
                destructuredArrayIgnorePattern: "^_",
            }],
        },
    },
]);
