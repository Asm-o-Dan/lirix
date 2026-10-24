# LOCALE-REPAIR-003 — LocalizedAppContent

Function: composable LocalizedAppContent(content). Reacts language/system Configuration; provides localizedresources without resetting tree; preserves configfields/activityowner.
DoD: RU/EN/System changes real stringResource+getString; retain rememberedstate; unwrapActivity rootintegration. SDK26/32/33/35 covered, platformrecreation distinguished from process restart.
