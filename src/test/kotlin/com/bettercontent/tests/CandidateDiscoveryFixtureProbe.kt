package com.bettercontent.tests

import java.nio.file.Path

/** Isolate synthetic discovery from the real bound candidate in a cumulative tier. */
object CandidateDiscoveryFixtureProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(System.getenv("BC_CANDIDATE_BINDING").isNullOrBlank())
        require(System.getenv("BC_PACK_TEST_HANDOFF").isNullOrBlank())
        try {
            CandidateLocator.locate(Path.of(args.single()))
            error("partial declared synthetic release was accepted")
        } catch (failure: IllegalArgumentException) {
            require(failure.message.orEmpty().contains("current Build 2 candidate directories")) { "unexpected rejection: $failure" }
            println(failure.message)
        }
    }
}
