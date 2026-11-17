package com.bettercontent.tests.suite.checkpoints

import com.bettercontent.tests.suite.drivers.DriverAction
import com.bettercontent.tests.suite.shaPattern

internal const val SAMPLING_LEDGER_BINDING = "@sampling-ledger-sha256"

/** The only runtime substitution: an already sealed, externally retained sampling-ledger prefix. */
internal fun resolveExecutionAction(action: DriverAction, ledgerSha256: String?): DriverAction {
    if (action.id != "scale.begin") return action
    require(action.parameters.path("ledger_sha256").asText() == SAMPLING_LEDGER_BINDING &&
        ledgerSha256 != null && ledgerSha256.matches(shaPattern)) { "scale must bind its actual preclaim ledger, not an authored guessed digest" }
    return action.copy(parameters = action.parameters.deepCopy().put("ledger_sha256", ledgerSha256))
}
