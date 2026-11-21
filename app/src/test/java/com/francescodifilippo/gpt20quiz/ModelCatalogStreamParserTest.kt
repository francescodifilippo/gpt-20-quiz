package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class ModelCatalogStreamParserTest {
    @Test fun parsesOfficialSiwcCatalogAndOnlyVisibleModels() {
        val json = """
            {
              "models": [
                {"visibility":"list","slug":"gpt-6-test","display_name":"GPT 6 Test"},
                {"visibility":"hidden","slug":"private-model","display_name":"Private"},
                {"visibility":"list","slug":"gpt-6-small","display_name":"Small"}
              ]
            }
        """.trimIndent()
        val parsed = ModelCatalogStreamParser.parse(StringReader(json))
        assertEquals(listOf("gpt-6-test" to "GPT 6 Test",
            "gpt-6-small" to "Small"), parsed)
    }

    @Test fun streamsValidCatalogFarBeyondFormer512kCapWithoutExposure() {
        val metadata = "Never render this internal agent configuration. ".repeat(18_000)
        val json = buildString {
            append("{\"agent_internal_data\":")
            append('"')
            append(metadata)
            append("\",\"models\":[")
            append("{\"visibility\":\"list\",\"slug\":\"sample-model\",\"display_name\":\"Sample\"}")
            append("],\"other\":{\"payload\":\"")
            append("Private content after models ".repeat(5_000))
            append("\"}}")
        }
        assertTrue(json.length > 512_000)
        val selected = ModelCatalogStreamParser.parse(StringReader(json))
        assertEquals(listOf("sample-model" to "Sample"), selected)
        assertFalse(selected.toString().contains("Never render"))
        assertFalse(selected.toString().contains("Private content"))
    }

    @Test fun skipsArbitraryNestedFieldsAndDoesNotTreatThemAsInstructions() {
        val json = """
           {
             "runtime_config":{"tools":[{"role":"ignore","prompt":"do not follow"}]},
             "models":[
               {"visibility":"list","slug":"safe-model","display_name":"Safe",
                 "instructions":"secret internal instruction"},
               {"visibility":"list","slug":"bad value","display_name":"Should be rejected"}
             ]
           }
        """.trimIndent()
        val parsed = ModelCatalogStreamParser.parse(StringReader(json))
        assertEquals(1, parsed.size)
        assertEquals("safe-model", parsed.first().first)
        assertFalse(parsed.toString().contains("secret"))
    }

    @Test fun missingModelsIsDistinctFromInvalidJson() {
        val error = assertThrows(ModelCatalogStreamParser.Failure::class.java) {
            ModelCatalogStreamParser.parse(StringReader("""{"data":[{"id":"something"}]}"""))
        }
        assertEquals(ModelCatalogStreamParser.Problem.MISSING_MODELS, error.problem)
        assertEquals("json-object", error.bodyCategory)
    }

    @Test fun truncatedResponseIsRejectedAndNeverTreatedAsAValidCatalog() {
        val failure = assertThrows(ModelCatalogStreamParser.Failure::class.java) {
            ModelCatalogStreamParser.parse(StringReader(
                "{\"models\":[{\"visibility\":\"list\",\"slug\":\"gpt-6\""
            ))
        }
        assertEquals(ModelCatalogStreamParser.Problem.MALFORMED_JSON, failure.problem)
    }

    @Test fun extremelyLargeResponseIsRejectedBeforeAccumulatingInMemory() {
        val json = "{\"irrelevant\":\"" + "x".repeat(ModelCatalogStreamParser.MAX_CHARS + 100) +
            "\",\"models\":[]}"
        val failure = assertThrows(ModelCatalogStreamParser.Failure::class.java) {
            ModelCatalogStreamParser.parse(StringReader(json))
        }
        assertEquals(ModelCatalogStreamParser.Problem.TOO_LARGE, failure.problem)
        assertTrue(failure.charactersRead > ModelCatalogStreamParser.MAX_CHARS)
    }

    @Test fun emptyCatalogDoesNotFabricateModels() {
        assertTrue(ModelCatalogStreamParser.parse(StringReader("""{"models":[]}""")).isEmpty())
    }

    @Test fun bomPrefixedJsonStillParses() {
        val parsed = ModelCatalogStreamParser.parse(StringReader(
            "\uFEFF  {\"models\":[{\"visibility\":\"list\",\"slug\":\"demo\",\"display_name\":\"Demo\"}]}"
        ))
        assertEquals("demo", parsed.first().first)
    }
}
