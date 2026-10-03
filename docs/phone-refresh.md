# Phone refresh

The user approved the complete accumulated phone backlog and requested a fixed-signature
APK installed on the physical Pixel. This work includes earlier accepted changes, not only
the latest icon selection. The current product is Android phone-only; shared
Java models and policies support the phone app.

## Required behavior

- Phone application ID, namespace and component classes: `me.pipi.codexmeter`.
- Phone version: `0.1`, code `1`; compile and target SDK `37`.
- Keep the pinned signing identity and existing private Desktop recovery backup.
- Keep Clear as the only card style. One-row 2x1 dials keep upstream geometry,
  remaining percentages and the same system palette; do not allow horizontal resizing.
- Keep every content switch visible when disabled. The one-row editor offers five-hour,
  weekly and reset meters. Larger cards also offer remaining credits, rendered without a bar.
- Use compact aligned title/value rows, preserve reset details and show available reset count
  below the reset bar. The reset-meter value and the followed live notification's
  reset meter use compact countdowns such as `6d 11h`; widget usage details retain
  exact `MM/dd HH:mm` dates.
  Wide 4x2/5x2 cards show successful-refresh `HH:mm` before refresh; 2x2/3x2 do not.
- Widget headers show bold plan labels: Free, Plus, Team, Pro 100, Pro 200 and Pro 500.
  The static picker uses Plus. Pro aliases map to the selected 100/200/500 names;
  the former 20X alias is unsupported. These numbers are display names, not price claims.
  The Codex mark follows the selected app/notification source. Capsule text follows
  upstream English.
- Expanded live notification title is Codex Meter; hide five-hour content when hidden on
  Home and follow the most recently saved widget content selection.
- Launcher uses the requested blue gradient background and enlarged white foreground;
  the monochrome layer reuses that foreground. Notification identity reuses the app icon.
- Apply the reviewed CodexBar-informed Chinese copy, preserving exact feature distinctions:
  app-owned Now Bar is 实时通知, existing monitor remains 实时监控.
  Use 剩余额度 consistently for credit-balance labels. Insert one space between
  Chinese and Latin text and between compact durations and adjacent Chinese copy.
- Settings groups, inline account card and direct account actions, Home-display editor,
  appearance descriptions,
  four-character titles, About description, linked developer/avatars, names-only credits
  and dependencies follow the accepted previews.
- Remove additional model limits from phone cards, display settings, ordering and settings
  transfer. Legacy widget model selections migrate to standard meters.
- Updates check HotKids/Codex-Meter. More options starts with update channel; 测试版 is
  a disabled placeholder. Remove version-history entry points. Never offer old-package APKs.
- Privacy has two aligned read-only icon rows and primary-color titles. App action/info
  icons use official Material Symbols Outlined (24,400,fill0,grade0); keep brand identities.

## Validation and delivery

Check phone behavior with core/unit tests and rendered widget previews. Build only the phone
release with the fixed signer, verify APK package, component namespace, version, SDK and
certificate, then install on the verified physical Pixel and inspect affected pages/widgets.
Do not call source checks or mockups physical-device acceptance. If the phone is unavailable,
retain the tested signed APK and report device installation as unverified.

Package migration creates a separate Android application; do not copy credential stores.
Local Pixel validation does not itself authorize Git pushes, tags or public releases;
publication requires an explicit maintainer request.
