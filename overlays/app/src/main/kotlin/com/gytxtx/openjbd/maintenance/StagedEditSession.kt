package com.gytxtx.openjbd.maintenance

/** Result returned by both staged-field and operation-level validation hooks. */
sealed interface ValidationResult {
    object Valid : ValidationResult

    data class Invalid(val reason: String) : ValidationResult {
        init {
            require(reason.isNotBlank()) { "Validation failure reason must not be blank" }
        }
    }
}

data class StagedChangeDraft(
    val fieldKey: String,
    val humanLabel: String,
    val currentDisplayValue: String,
    val proposedDisplayValue: String
)

fun interface StagedChangeValidator {
    fun validate(change: StagedChangeDraft): ValidationResult
}

data class StagedChange(
    val fieldKey: String,
    val humanLabel: String,
    val currentDisplayValue: String,
    val proposedDisplayValue: String,
    val validation: ValidationResult
)

data class ChangeReviewItem(
    val fieldKey: String,
    val humanLabel: String,
    val currentDisplayValue: String,
    val proposedDisplayValue: String,
    val validation: ValidationResult
) {
    fun summary(): String = ChangeReviewFormatter.format(this)
}

/** Pure formatter for review rows such as `Cell OVP 4.250 V → 4.200 V`. */
object ChangeReviewFormatter {
    fun format(item: ChangeReviewItem): String =
        "${item.humanLabel} ${item.currentDisplayValue} → ${item.proposedDisplayValue}"
}

/**
 * Session-scoped working set used before a technician confirms Apply.
 * Replacing an existing key keeps its original review position; removing and staging it again
 * moves it to the end. Returned lists are snapshots and cannot mutate the session.
 */
class StagedEditSession(
    private val validator: StagedChangeValidator = StagedChangeValidator { ValidationResult.Valid }
) {
    private val changesByKey = linkedMapOf<String, StagedChange>()

    val size: Int
        get() = changesByKey.size

    val isEmpty: Boolean
        get() = changesByKey.isEmpty()

    fun stage(
        fieldKey: String,
        humanLabel: String,
        currentDisplayValue: String,
        proposedDisplayValue: String
    ): StagedChange {
        require(fieldKey.isNotBlank()) { "fieldKey must not be blank" }
        require(humanLabel.isNotBlank()) { "humanLabel must not be blank" }

        val draft = StagedChangeDraft(
            fieldKey = fieldKey,
            humanLabel = humanLabel,
            currentDisplayValue = currentDisplayValue,
            proposedDisplayValue = proposedDisplayValue
        )
        val change = StagedChange(
            fieldKey = fieldKey,
            humanLabel = humanLabel,
            currentDisplayValue = currentDisplayValue,
            proposedDisplayValue = proposedDisplayValue,
            validation = validator.validate(draft)
        )
        changesByKey[fieldKey] = change
        return change
    }

    fun remove(fieldKey: String): StagedChange? = changesByKey.remove(fieldKey)

    fun clear() {
        changesByKey.clear()
    }

    fun stagedChanges(): List<StagedChange> = changesByKey.values.toList()

    fun reviewItems(): List<ChangeReviewItem> = changesByKey.values.map { change ->
        ChangeReviewItem(
            fieldKey = change.fieldKey,
            humanLabel = change.humanLabel,
            currentDisplayValue = change.currentDisplayValue,
            proposedDisplayValue = change.proposedDisplayValue,
            validation = change.validation
        )
    }
}
