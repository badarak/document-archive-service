package com.badarak.archive.domain.port.inbound

import com.badarak.archive.domain.model.DocumentId

/**
 * What one reconciliation call did with its batch of stalled documents. Each document is handled on its own: one that
 * fails stays in its status for the next call, without holding back the others. After several failures in a row the call
 * is [interrupted], since the storage or the document store is then most likely unreachable.
 *
 * @property completed the documents this call moved on, leaving out those another run handled meanwhile
 * @property failures the documents this call could not move on, each with the cause of its failure
 */
data class ReconciliationOutcome(val completed: List<DocumentId>, val failures: List<ReconciliationFailure>, val interrupted: Boolean)

data class ReconciliationFailure(val documentId: DocumentId, val cause: RuntimeException)
