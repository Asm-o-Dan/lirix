package com.example.npc.pipeline.compiler.diagnostic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CompilationDiagnosticTest {

    @Test
    fun textSpan_validRange() {
        val span = TextSpan(5, 10)
        assertEquals(5, span.start)
        assertEquals(10, span.end)
    }

    @Test
    fun textSpan_zeroWidthAllowed() {
        val span = TextSpan(3, 3)
        assertEquals(3, span.start)
        assertEquals(3, span.end)
    }

    @Test
    fun textSpan_throwsOnNegativeOrInverted() {
        assertThrows(IllegalArgumentException::class.java) {
            TextSpan(-1, 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TextSpan(5, 3)
        }
    }

    @Test
    fun compilationDiagnostic_validConstruction() {
        val diag = CompilationDiagnostic(
            code = "P1001",
            severity = DiagnosticSeverity.ERROR,
            message = "Unsupported schemaVersion",
            messageKey = "error.schema_version.unsupported",
            location = SourceLocation(jsonPath = "$['schemaVersion']", target = Target.VALUE)
        )
        assertEquals("P1001", diag.code)
        assertEquals(DiagnosticSeverity.ERROR, diag.severity)
        assertEquals("$['schemaVersion']", diag.location.jsonPath)
    }

    @Test
    fun compilationDiagnostic_throwsOnBlankCodeOrMessage() {
        assertThrows(IllegalArgumentException::class.java) {
            CompilationDiagnostic(
                code = "",
                severity = DiagnosticSeverity.ERROR,
                message = "Msg",
                messageKey = "key",
                location = SourceLocation("$")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompilationDiagnostic(
                code = "P1001",
                severity = DiagnosticSeverity.ERROR,
                message = " ",
                messageKey = "key",
                location = SourceLocation("$")
            )
        }
    }
}
