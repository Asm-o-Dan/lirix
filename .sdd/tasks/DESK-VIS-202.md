# DESK-VIS-202: desktopReadableForeground

Status: READY FOR COORDINATOR SELECTION, not implemented.
Spec: .sdd/specs/media-ui/desktop_visual_corrections_v1.md.
Function: desktopReadableForeground.
File: desktop/ui/theme/DesktopTheme.kt (new) (desktop productionpaths relative to desktop/src/main/kotlin/com/lirix/app).
Dependencies: None.

Pure actual-background contrast adjustment, unchangedpassingcolour, HSLlightnessonly adjustment, endpoint/alpha contract persectionB. Reuse shared calculationfunctions, no sharedsourcechanges.

DoR: spec accepted, dependency completed, pinned API checked, no unrelatedfileedits. One behaviouralfunction percard; supportingimports/types allowed.

DoD: relevant specacceptance, coordinator-run integrated tests and exactframeevidence; no visualPASS inferredfromcompilation. Foreground4.5 / large3 / essentialoutline3 whereapplicable. Preserve6themes and Androidpalette. Do not modify teleprompterpending settledframes or claim unsaveddraftguarddone. No commits ofotherworkerfiles or Gradleruns withoutcoordinatorsequencing.
