package work.spyglee.ladger.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptPromptTest {
    @Test
    fun `reads a plain JSON reply`() {
        val reply = """{"merchant": "Konzum", "total": "12.49", "date": "2026-10-07", "category": "Hrana"}"""
        assertEquals(GeneratedReceipt("Konzum", "12.49", "2026-10-07", "Hrana"), ReceiptPrompt.parseResponse(reply))
    }

    @Test
    fun `reads JSON wrapped in a code fence with numbers and escapes`() {
        val reply = "Here you go:\n```json\n{\n  \"merchant\": \"Pizzeria \\\"Šiško\\\"\",\n  \"total\": 45.5,\n  \"date\": \"\"\n}\n```"
        val receipt = ReceiptPrompt.parseResponse(reply)
        assertEquals("Pizzeria \"Šiško\"", receipt?.merchant)
        assertEquals("45.5", receipt?.total)
        assertEquals("", receipt?.date)
        assertNull(receipt?.category)
    }

    @Test
    fun `decodes unicode escapes`() {
        assertEquals("Сільпо", ReceiptPrompt.parseResponse("""{"merchant": "\u0421\u0456\u043b\u044c\u043f\u043e"}""")?.merchant)
    }

    @Test
    fun `no JSON or no known fields gives null`() {
        assertNull(ReceiptPrompt.parseResponse(null))
        assertNull(ReceiptPrompt.parseResponse("I can't read this receipt."))
        assertNull(ReceiptPrompt.parseResponse("""{"store": "Konzum"}"""))
    }

    @Test
    fun `instructions list each category once and the prompt is capped`() {
        val instructions = ReceiptPrompt.instructions(listOf("Hrana", "Prijevoz", "Hrana"))
        assertTrue(instructions.contains("exactly one of \"Hrana\", \"Prijevoz\""))
        assertTrue(!ReceiptPrompt.instructions(emptyList()).contains("\"category\""))
        assertTrue(ReceiptPrompt.prompt("x".repeat(10_000)).length < 6_100)
    }
}
