package com.bettercontent.tests

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/** Classifies only the harness supplement in a disposable copy; enforcement remains blocking. */
object RuntimeDiagnosticPolicy {
    const val NAMESPACE = "better_runtime_test_support"
    private val mapper = jacksonObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    fun classify(original: String): String {
        val policy = mapper.readTree(original)
        require(policy is ObjectNode && policy.path("schema").asText() == "bc.crafting_policy.v1") { "invalid fixture policy" }
        require(policy.path("enforcement").path("unknown_loaded_namespaces").asText() == "blocking") {
            "fixture policy must retain blocking unknown namespaces"
        }
        val namespaces = policy.path("namespaces")
        require(namespaces is ObjectNode && !namespaces.has(NAMESPACE)) { "support classification already present or invalid namespace map" }
        val patched = policy.deepCopy()
        (patched.path("namespaces") as ObjectNode).set<ObjectNode>(NAMESPACE, mapper.createObjectNode()
            .put("primary_role", "infrastructure").put("support_state", "not_applicable"))
        val check = patched.deepCopy()
        (check.path("namespaces") as ObjectNode).remove(NAMESPACE)
        require(check == policy) { "fixture policy patch changed more than the support namespace" }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(patched) + "\n"
    }
}
