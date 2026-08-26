# AGENTS.md

## Research workflow

- Before implementing or changing Paper behavior, search current official Paper documentation and Javadocs.
- Save every useful research result as Markdown under `docs/`. Update an existing topic file when one already covers the subject; create a focused new file otherwise.
- Every research file must include source URLs, confirmed API behavior, alternatives considered, operational decision, and known limitations.
- Prefer official Paper docs/Javadocs. Use forums and public repositories only as secondary evidence.
- If external source code is needed to understand a pattern, clone the repository with shallow history into `docs/reference-repos/<repository>/`.
- Check repository license before using code. Study patterns only; do not copy incompatible or substantial protected code.
- Keep `docs/reference-repos/` out of Git. Research Markdown remains local when `docs/` is ignored.

## Paper plugin conventions

- Follow global `mc-plugin` skill.
- Use modern Paper API for exact target version; avoid NMS unless public API cannot meet requirement.
- Keep main plugin class limited to lifecycle wiring.
- Organize code into responsibility packages: `command`, `config`, `inventory`, `listener`, `model`, `service`, `storage`.
- Keep `config.yaml` for settings and `data/<player-uuid>.yaml` for runtime state.
- Centralize settings in validated immutable configuration object; reuse same loader for reload.
- Use custom `InventoryHolder` for plugin inventories.
- Keep one canonical live inventory per vault to prevent duplication.
- Add concise Javadoc only for lifecycle choices, non-obvious Paper API behavior, persistence, concurrency, and anti-dupe rules.
- Keep source, messages, logs, configuration, README, and research documentation in English.

## Verification

- Run focused tests after each logical change.
- Run `gradlew.bat clean test build` before completion on Windows.
- Compilation does not prove runtime behavior; document manual Paper checks for inventories, hoppers, persistence, disconnects, and restarts.
