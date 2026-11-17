package com.bettercontent.tests.suite.v2.contracts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AcquisitionContractsTest {
    @TempDir lateinit var root: Path
    private val mapper = jacksonObjectMapper()
    private fun manifest(): com.fasterxml.jackson.databind.JsonNode {
        val tree = mapper.readTree("""{"schema":"bc.playtest_manifest.v2","authoring":{"state":"reviewed","reviewer":"human","reviewed_at":"2020-01-01T00:00:00Z","blockers":[]},"candidate":{"client":{"path":"client.zip","sha256":"${"a".repeat(64)}"},"server":{"path":"server.zip","sha256":"${"b".repeat(64)}"}},"artifact_locks":[],"acquisitions":[{"id":"test:wood","kind":"harvest","stage":0,"coverage":["wood"],"prerequisites":[],"routes":[]},{"id":"test:fire","kind":"crafted","stage":0,"coverage":["fire"],"prerequisites":[],"routes":[{"id":"bootstrap","mechanism":"craft","execution_case":"fire","stage":0,"prerequisites":[],"ingredients":[["test:wood","test:loop"]]}]},{"id":"test:loop","kind":"crafted","stage":0,"coverage":["loop"],"prerequisites":[],"routes":[{"id":"recycle","mechanism":"craft","execution_case":"loop","stage":0,"prerequisites":[],"ingredients":[["test:fire"]]}]}]}""")

        for (side in listOf("client", "server")) {
            val bytes = side.toByteArray()
            java.nio.file.Files.write(root.resolve("$side.zip"), bytes)
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            (tree.path("candidate").path(side) as com.fasterxml.jackson.databind.node.ObjectNode).put("sha256", sha)
        }
        return tree
    }

    @Test fun bootstrappedAlternativeCanResolveCycleWithoutSpuriousBlockage() {
        assertEquals(emptyList<String>(), validateV2Draft(root, manifest()))
    }

    @Test fun emptyDraftMayDiscoverBeforeAcquisitionsAreAuthored() {
        val tree = manifest() as com.fasterxml.jackson.databind.node.ObjectNode
        (tree.path("authoring") as com.fasterxml.jackson.databind.node.ObjectNode).put("state", "draft")
        tree.set<com.fasterxml.jackson.databind.JsonNode>("acquisitions", mapper.createArrayNode())
        assertEquals(emptyList<String>(), validateV2Draft(root, tree))
        assertTrue(validateV2Reviewed(root, tree).any { it.contains("acquisition ledger") })
    }

    @Test fun absoluteCandidateBindingsAreAllowedButRelativeTraversalIsNot() {
        val tree = manifest()
        val client = tree.path("candidate").path("client") as com.fasterxml.jackson.databind.node.ObjectNode
        client.put("path", root.resolve("client.zip").toAbsolutePath().toString())
        assertEquals(emptyList<String>(), validateV2Draft(root, tree))
        client.put("path", "../client.zip")
        assertTrue(validateV2Draft(root, tree).any { it.contains("candidate.client") })
    }

    @Test fun mutatedCandidateBytesInvalidateAnOtherwiseValidDraft() {
        val tree = manifest()
        java.nio.file.Files.writeString(root.resolve("client.zip"), "changed")
        assertTrue(validateV2Draft(root, tree).any { it.contains("candidate.client") })
    }

    @Test fun allRoutesInCycleWithoutBootstrapAreUnreachable() {
        val tree = manifest()
        (tree.path("acquisitions")[1].path("routes")[0].path("ingredients")[0] as com.fasterxml.jackson.databind.node.ArrayNode).remove(0)
        assertTrue(validateV2Draft(root, tree).any { it.contains("unreachable") })
    }

    @Test fun twoRequiredIngredientGroupsCannotBeTreatedAsAlternatives() {
        val tree = manifest()
        val groups = tree.path("acquisitions")[1].path("routes")[0].path("ingredients") as com.fasterxml.jackson.databind.node.ArrayNode
        groups.add(mapper.readTree("[\"test:loop\"]"))
        assertTrue(validateV2Draft(root, tree).any { it.contains("unreachable") })
    }

    @Test fun exclusionRequiresReviewedEvidenceBeforeItCanBeAccepted() {
        val tree = manifest()
        val rows = tree.path("acquisitions") as com.fasterxml.jackson.databind.node.ArrayNode
        val excluded = mapper.readTree("""{"id":"test:excluded","kind":"intentional-unobtainable","stage":0,"coverage":["exclude"],"prerequisites":[],"routes":[],"exclusion":{"reason":"not shipped","evidence":{"path":"review.txt","sha256":"${"a".repeat(64)}"},"review":{"state":"reviewed","reviewer":"human","reviewed_at":"2020-01-01T00:00:00Z"}}}""")
        rows.add(excluded)
        assertTrue(validateV2Draft(root, tree).any { it.contains("evidence unbound") })
        val bytes = "intentional exclusion review".toByteArray()
        java.nio.file.Files.write(root.resolve("review.txt"), bytes)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        (excluded.path("exclusion").path("evidence") as com.fasterxml.jackson.databind.node.ObjectNode).put("sha256", sha)
        assertEquals(emptyList<String>(), validateV2Draft(root, tree))
    }

    @Test fun draftConversionPreservesOldSourceWithoutUpgradingReview() {
        val input = mapper.readTree("""{"schema":"bc.playtest_manifest.v1","authoring":{"state":"reviewed"},"candidate":{},"acquisitions":[{"id":"test:fire","kind":"crafted","stage":0,"routes":[{"variants":["a"]}]}],"cases":[{"state":"passed"}]}""")
        val result = convertV1Draft(input)
        assertEquals(input, result.path("original_v1"))
        assertEquals("draft", result.path("authoring").path("state").asText())
        assertTrue(result.path("conversion_pending").asBoolean())
        assertTrue(result.path("acquisitions").isEmpty)
        assertEquals("not_run", result.path("cases")[0].path("state").asText()) // old state remains only in original_v1
        assertTrue(validateV2Reviewed(root, result).isNotEmpty())
    }
}
