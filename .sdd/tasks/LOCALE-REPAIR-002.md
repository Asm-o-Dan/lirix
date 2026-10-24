# LOCALE-REPAIR-002 — LanguageManager.setLanguage

Function: setLanguage(context, selection). Existing preference retained; observable selection updated; safe external platform synchronization on33+ when needed.
DoD: tests newmanager coldstart reads oldpref; repeatedwrites idempotent; invalidselection default; no AppCompatdelegate dependency. Root executes RED then GREEN.
