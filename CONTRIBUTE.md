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
  - name: SERVICE_NAME
    required: true
  - name: DATABASE_TYPE
    default: "POSTGRESQL"
    options: ["POSTGRESQL", "MYSQL", "NONE"]
```

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

Use the existing templates under `backend/templates` as examples. Keep placeholders compatible with the generator, including values such as `{{SERVICE_NAME}}`, `{{PACKAGE_NAME}}`, `{{CLASS_NAME}}`, `{{DESCRIPTION}}`, and `{{DATABASE_TYPE}}`. Use `.template` on files whose names contain placeholders, for example:

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
