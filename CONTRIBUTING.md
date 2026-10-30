# Contributing to DevForge

Contributions are welcome. You can add templates, improve the platform, fix bugs, improve documentation, or suggest new features.

## Adding a template

Add each new template inside:

```text
backend/templates/<template-folder>/
```

The folder name becomes the template's stable key. Use a clear, unique folder name, for example:

```text
backend/templates/python_FastAPI_Poetry_v2/
```

Every template **must** contain a `template.yaml` file with this format:

```yaml
id: "optional-manifest-id"
name: "Human-readable template name"
stableKey: "python_FastAPI_Poetry"
language: "PYTHON"
framework: "FASTAPI"
buildTool: "POETRY"
version: "2.0.0"
description: "Short description of the generated project"

variables:
  - name: PYTHON_VERSION
    default: "3.12"
    options: ["3.11", "3.12"]
```

Database, messaging and other optional parts are declared as `features` (see [Schema v2 templates](#schema-v2-templates-features-and-dependencies-recommended)), not as variables.

### Manifest rules

- `name`, `language`, `framework`, `buildTool`, `version`, and `description` must be present.
- `stableKey` must exactly match the template folder name.
- `language`, `framework`, and `buildTool` must use values supported by the backend enums.
- `version` must use a meaningful template version such as `1.0.0`.
- Keep the display `name` at 20 characters or fewer.
- Use unique folder names and stable keys.
- Keep variable names consistent with the files in the template.
- Do not put passwords, tokens, API keys, or other secrets in a template.

The backend reads and validates the manifest when an administrator runs template synchronization. Invalid templates are rejected and reported instead of being added to the catalog.

## Template contents

A useful template should include the files needed to create and run a working project, such as:

- Source code and a small smoke test.
- The correct dependency and build files.
- A `Dockerfile` when containerization is supported.
- A `.gitignore`.
- A `.github/workflows/ci.yml` workflow that installs dependencies, runs tests, builds the project, and validates the Docker image.
- A `README.md` explaining how to run and customize the generated project.

Use the existing templates under `backend/templates` as examples. Directory and file names may contain the path tokens `__PACKAGE_PATH__`, `__CLASS_NAME__` and `__SERVICE_NAME__`.

### Schema v2 templates: features and dependencies (recommended)

Set `schemaVersion: 2` to let users pick features (database, messaging, extras) when they create a service. `spring_Boot_Maven` is the reference example.

```text
backend/templates/<folder>/
  template.yaml
  base/                     # always rendered
  features/<optionId>/      # optional overlay, rendered only when that option is selected
```

- Only files ending in `.tmpl` are templates (the suffix is removed). Every other file is copied byte for byte, so GitHub Actions `${{ }}` expressions never need escaping.
- Templates use Go `text/template` with `[[ ]]` delimiters. Available data: `.ServiceName`, `.ClassName`, `.PackageName`, `.PackagePath`, `.Description`, `.Vars.<NAME>`, `.Features` and `.Dependencies`. Available functions: `has "<option-or-group-id>"`, `selected "<group>"`, `quote` (JSON/YAML string), `xml` (escape), `lower`, `upper`, `pascal`, `camel`, `kebab`, `snake`, and `gradleConfiguration <scope>`. Referencing an undefined variable is an error.
- A `.tmpl` file that renders to only whitespace is left out, so a base file can exist only for some combinations (see `TestcontainersConfiguration.java.tmpl`).
- When an overlay file has the same path as a base file, the overlay replaces it.

Declare the choices in `template.yaml`:

```yaml
schemaVersion: 2
variables:
  - name: JAVA_VERSION          # use it as [[ .Vars.JAVA_VERSION ]]
    options: ["17", "21"]       # options make it a select; omit for free text (optional `pattern`)
    default: "21"
features:
  - id: database
    label: Database
    kind: choice                # choice = at most one, multi = any subset
    required: false
    options:
      - id: postgresql          # also the overlay folder name: features/postgresql/
        label: PostgreSQL
        dependencies:           # rendered by the build file via [[ range .Dependencies ]]
          - { group: org.postgresql, artifact: postgresql, scope: runtime }
  - id: extras
    kind: multi
    options:
      - id: flyway
        requires: [database]    # option or group ids that must also be selected
        conflicts: [mongodb]    # option or group ids that must not be selected
ci:                             # feature combinations the repository CI generates and builds
  - name: postgresql
    features: { database: [postgresql], extras: [flyway] }
```

- Feature group and option ids share one namespace, must be unique, and use lowercase letters, digits and hyphens.
- Dependency scopes: `compile` (default), `runtime`, `provided`, `test`, `test-runtime`, `annotation-processor`, `development` (local run only), and `dev` (npm devDependencies).
- `SERVICE_NAME`, `CLASS_NAME`, `PACKAGE_NAME`, `PACKAGE_PATH` and `DESCRIPTION` are reserved: DevForge always sets them, and users cannot override them.

Try a combination locally from `backend/provisioner`:

```bash
go run ./cmd/template-gen -templates-root ../templates -template spring_Boot_Maven \
  -output /tmp/out -feature database=postgresql -feature extras=flyway -var JAVA_VERSION=21
```

### Legacy (schema v1) templates

Without `schemaVersion: 2`, every file in the folder is processed and `{{SERVICE_NAME}}`, `{{PACKAGE_NAME}}`, `{{CLASS_NAME}}`, `{{DESCRIPTION}}` and any manifest variable are replaced as plain text. A `.template` suffix is removed from file names, for example:

```text
src/main/java/__PACKAGE_PATH__/__CLASS_NAME__Application.java.template
```

## Missing languages, frameworks, or build tools

If the language, framework, or build tool you need is not supported:

1. Add the value to the appropriate backend enum.
2. Add the new template and its `template.yaml`.
3. Add or update tests and documentation.
4. Run template synchronization as an administrator.

Please discuss larger enum or architecture changes before opening a pull request so related templates and API behavior can be updated consistently.

## Improving DevForge

Ideas are welcome. If you have an enhancement in mind, open an issue or pull request with:

- The problem or user experience it improves.
- The proposed behavior.
- Any affected templates, backend APIs, or frontend screens.
- Tests or documentation for the change.


## Before submitting a pull request

- Confirm the template has a valid `template.yaml`.
- Confirm `stableKey` matches the folder name.
- Confirm all referenced enum values exist.
- Test the generated project and its CI workflow where possible.
- Run the relevant backend and frontend checks.
- Update documentation when behavior or contributor workflow changes.
