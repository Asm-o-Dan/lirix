# Contributing to Lirix

Bug reports, documentation, accessibility improvements, translations and code contributions are welcome. You may write issues and pull requests in English or Russian.

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md). Report security vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## Before opening an issue

Search [existing issues](https://github.com/Asm-o-Dan/lirix/issues) and check the [latest release](https://github.com/Asm-o-Dan/lirix/releases). Use the bug report or feature request form. For playback or lyrics problems, include your Lirix version, operating system, music player, track artist/title, selected mode and source, and steps to reproduce. Include expected and actual behavior.

Remove personal information from logs and screenshots. Never attach access tokens, signing keys, private library databases or full copyrighted lyrics. A track name, source URL and small relevant diagnostic excerpt are usually enough.

## Development setup

1. Fork the repository and create a branch for a focused change.
2. Follow the Building from Source section in [README](README.md) for the current JDK and Android SDK requirements.
3. Read [AGENTS.md](AGENTS.md) when using an automated coding agent, and the [documentation index](docs/README.md) for project context.
4. Discuss changes to storage, permissions, device pairing or public behavior in an issue before starting a large implementation.

Run these commands from the repository root (use `./gradlew` on macOS/Linux):

```powershell
# Android regression checks and debug package
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug

# Shared logic and desktop changes, where those modules are present
.\gradlew.bat :shared:desktopTest :desktop:test
```

Do not use release signing credentials for a development build. Test changes on the affected platform when possible; state clearly when device checks were not performed. For documentation-only changes, check links, spelling and any YAML syntax instead of building the app.

## Pull requests

Keep one purpose per pull request. Explain the problem, the resulting behavior and how you verified it. Link related issues. Include screenshots for visual changes and regression coverage for behavior changes. Document database migrations and compatibility implications when relevant.

Preserve local user data, source attribution and existing translations. Do not commit generated builds, local configuration, credentials or unrelated formatting changes. New dependencies and permissions need a clear explanation.

Maintainers may request changes before merging. Contributions are distributed under the repository's existing [GPL v3 license](LICENSE); only submit material you have the right to contribute.
