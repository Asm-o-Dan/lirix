# Accessibility in Lirix

Lirix aims to make lyrics, chords and playback information usable by people with different visual, motor and reading needs. Accessibility improvements and reports are welcome for Android and desktop.

## What we aim to support

- Readable lyrics and controls across supported themes, text sizes and screen sizes.
- Screen reader labels and a sensible reading order for interactive controls.
- Keyboard navigation and visible focus on desktop.
- Controls that do not rely on color alone, and respect for system motion preferences.

These are design and review goals, not a claim that every screen has been verified. No complete accessibility audit or WCAG conformance assessment has been completed for this statement. Automated UI checks alone do not establish accessibility.

## Environments and limitations

See [README](README.md) and [Releases](https://github.com/Asm-o-Dan/lirix/releases) for supported platforms and available builds. Reports using Android TalkBack, system font scaling, Windows screen readers, keyboard navigation and reduced motion settings are especially helpful.

Lyrics and chord sheets can contain dense formatting or timed scrolling. Third-party content and optional floating overlays need particular attention during testing. Their accessibility may vary with the provider, operating system and assistive technology.

## Report a barrier

Open a [bug report](https://github.com/Asm-o-Dan/lirix/issues/new?template=bug_report.yml) and select accessibility as the affected area. Include your app version, platform, assistive technology and settings, the affected screen, reproduction steps, and what would make the task possible.

If the report contains personal information, email [dgandapas1@gmail.com](mailto:dgandapas1@gmail.com) instead. Remove private information from recordings and screenshots. We will use reports to prioritize improvements; this volunteer project cannot guarantee a resolution date.
