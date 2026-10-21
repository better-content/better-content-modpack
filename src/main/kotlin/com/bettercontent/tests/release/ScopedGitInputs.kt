package com.bettercontent.tests.release

/**
 * Enumerate a conservative superset BEFORE bounded capture. Callers still apply their original
 * matcher/filter: this changes neither the fingerprint domain nor glob semantics. An unanchored
 * glob deliberately falls back to the full listing; no declared input is silently dropped.
 */
internal fun scopedGitInputArgs(patterns: List<String>): List<String> {
    require(patterns.all { !it.startsWith("/") && ".." !in it.split('/') }) { "unsafe verification input pattern" }
    val base = listOf("ls-files", "-z", "--cached", "--others", "--exclude-standard")
    if (patterns.isEmpty()) return base + listOf("--", ":(exclude)**")
    val roots = patterns.map { pattern ->
        val meta = pattern.indexOfFirst { it in "*?[{\\" }
        if (meta < 0) pattern else {
            val literal = pattern.substring(0, meta)
            literal.substring(0, literal.lastIndexOf('/') + 1)
        }
    }.distinct()
    if (roots.any { it.isEmpty() }) return base
    return base + "--" + roots.map { ":(top,literal)$it" }
}
