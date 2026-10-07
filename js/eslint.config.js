import js from "@eslint/js";
import stylistic from "@stylistic/eslint-plugin";
import { defineConfig, globalIgnores } from "eslint/config";
import tseslint from "typescript-eslint";

export default defineConfig([
	globalIgnores(["lib/**"]),
	{
		files: ["**/*.js", "src/**/*.ts"],
		extends: [stylistic.configs.customize({
			indent: "tab",
			quotes: "double",
			semi: true,
			braceStyle: "stroustrup",
			jsx: false,
		})],
		rules: {
			// Preserve the existing layout of unions, expressions, and blocks.
			"@stylistic/arrow-parens": ["error", "as-needed"],
			"@stylistic/indent-binary-ops": "off",
			"@stylistic/no-mixed-spaces-and-tabs": ["error", "smart-tabs"],
			// Extra spaces may align adjacent declarations and comments.
			"@stylistic/no-multi-spaces": "off",
			"@stylistic/member-delimiter-style": ["error", {
				multiline: { delimiter: "semi", requireLast: true },
				singleline: { delimiter: "comma", requireLast: false },
				overrides: {
					interface: { singleline: { delimiter: "semi", requireLast: true } },
				},
			}],
			"@stylistic/no-multiple-empty-lines": ["error", { max: 2, maxBOF: 1, maxEOF: 1 }],
			"@stylistic/operator-linebreak": ["error", "after", {
				overrides: { "?": "before", ":": "before", "|": "before", "&": "before" },
			}],
			"@stylistic/padded-blocks": "off",
		},
	},
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
