# Star2D Remote — guide for AI assistants

You were given this link because the user wants you to build or edit a game in **Star2D**, a 2D game engine and editor that runs on **their Android phone**. You control it through tools that this server relays to the phone.

Server: `{{SERVER}}`

## How you connect

The user's app shows a private connector URL. It looks like this:

- **MCP (Streamable HTTP):** `{{SERVER}}/mcp/<secret>` — add it as a custom connector (Claude: Settings → Connectors → Add custom connector; no login needed, the secret in the URL is the key).
- **Plain HTTP, for AIs that can make web requests but not MCP:** `POST {{SERVER}}/api/<secret>/<tool>` with the tool arguments as the JSON body. The answer is JSON: `{"content":[...],"isError":false}`. Example: `POST {{SERVER}}/api/<secret>/status` with body `{}`.

Treat the URL like a password. Never print it back to other people.

## Rules

{{CORE}}

Before anything else read `status.frame` (orientation and size of the open scene, y is up) and design for exactly that frame. Pass `orientation:"portrait"` to `scenes create` for vertical games. Use `physics_info` before choosing jump power, gravity, speeds or platform gaps, and `layout_check` after building (it also finds a background covering other elements).

Also: call `set_plan` before working, `update_task` as tasks start/finish, `finish_work` at the end. The server has a daily call budget shared by all users, so batch.

## Tools (names)

`status` · `wait_for_app` · `get_guide` · `read_docs` · `set_plan` · `update_task` · `finish_work` · `project_info` · `scene_dump` · `get_elements` · `scenes` · `elements` · `templates` · `buttons` · `physics_info` · `layout_check` · `joints` · `prefabs` · `variables` · `block_catalog` · `events` · `define_blocks` · `files` · `create_image` · `icons` · `create_sound` · `import_asset` · `editor` · `build` · `run_game` · `stop_game` · `screenshot` · `game_input` · `game_state` · `run_test` · `game_logs` · `git`

## Privacy

This server stores no project files. It relays messages between you and the phone, keeps a tiny per-day call counter, and deletes even that automatically when the phone has been disconnected for a while.
