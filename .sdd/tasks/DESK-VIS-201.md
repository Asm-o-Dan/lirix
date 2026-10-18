# DESK-VIS-201: DesktopLibraryPanel

Status: READY FOR COORDINATOR SELECTION, not implemented.
Spec: .sdd/specs/media-ui/desktop_visual_corrections_v1.md.
Function: DesktopLibraryPanel.
File: desktop/ui/DesktopWorkspace.kt (desktop productionpaths relative to desktop/src/main/kotlin/com/lirix/app).
Dependencies: None.

Replace only detail actionRow with FlowRow per sectionA; preserve whole labels andcallbacks; six-theme/twosize/bothfavoritestate frameacceptance.

DoR: spec accepted, dependency completed, pinned API checked, no unrelatedfileedits. One behaviouralfunction percard; supportingimports/types allowed.

DoD: relevant specacceptance, coordinator-run integrated tests and exactframeevidence; no visualPASS inferredfromcompilation. Foreground4.5 / large3 / essentialoutline3 whereapplicable. Preserve6themes and Androidpalette. Do not modify teleprompterpending settledframes or claim unsaveddraftguarddone. No commits ofotherworkerfiles or Gradleruns withoutcoordinatorsequencing.
