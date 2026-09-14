package com.gytxtx.openjbd.settings

import com.gytxtx.openjbd.maintenance.ValidationResult
import java.math.BigDecimal

data class BackupDeviceIdentity(
    val name: String,
    val address: String,
    val serial: String? = null,
    val model: String? = null
)

data class BackupFieldEntry(
    val key: String,
    val raw: Int,
    val display: String,
    val unit: String
)

data class ConfigurationBackup(
    val formatVersion: Int = BackupFormat.FORMAT_VERSION,
    val exportedAtUtc: String,
    val appVersion: String,
    val device: BackupDeviceIdentity,
    val fields: List<BackupFieldEntry>
)

enum class BackupIssueKind {
    MALFORMED_JSON,
    UNKNOWN_KEY,
    UNKNOWN_FIELD,
    MISSING_VALUE,
    INVALID_VALUE,
    UNSUPPORTED_FORMAT,
    DUPLICATE_FIELD,
    MISSING_GROUP,
    CURRENT_VALUE_UNAVAILABLE
}

data class BackupIssue(
    val kind: BackupIssueKind,
    val path: String,
    val detail: String = ""
)

data class BackupParseResult(
    val backup: ConfigurationBackup?,
    val issues: List<BackupIssue>,
    val missingGroups: Set<SettingsGroup>
)

data class RestoreFieldDiff(
    val field: SettingField,
    val oldRaw: Int,
    val newRaw: Int
)

data class RestoreDiffResult(
    val changes: List<RestoreFieldDiff>,
    val issues: List<BackupIssue>
)

/** Pure JSON codec and restore comparison for the version-one configuration backup schema. */
object BackupFormat {
    const val FORMAT_VERSION = 1

    val configurationGroups: Set<SettingsGroup> = linkedSetOf(
        SettingsGroup.BALANCE,
        SettingsGroup.PROTECTION,
        SettingsGroup.TEMPERATURE,
        SettingsGroup.CAPACITY
    )

    val configurationFields: List<SettingField> = SettingField.values().filter {
        it.group in configurationGroups
    }

    private val fieldsByKey = configurationFields.associateBy(SettingField::key)

    fun serialize(backup: ConfigurationBackup): String = buildString {
        append('{')
        append("\"formatVersion\":").append(backup.formatVersion)
        append(",\"exportedAtUtc\":").appendJsonString(backup.exportedAtUtc)
        append(",\"appVersion\":").appendJsonString(backup.appVersion)
        append(",\"device\":{")
        append("\"name\":").appendJsonString(backup.device.name)
        append(",\"address\":").appendJsonString(backup.device.address)
        backup.device.serial?.let { append(",\"serial\":").appendJsonString(it) }
        backup.device.model?.let { append(",\"model\":").appendJsonString(it) }
        append("},\"fields\":[")
        backup.fields.forEachIndexed { index, field ->
            if (index > 0) append(',')
            append('{')
            append("\"key\":").appendJsonString(field.key)
            append(",\"raw\":").append(field.raw)
            append(",\"display\":").appendJsonString(field.display)
            append(",\"unit\":").appendJsonString(field.unit)
            append('}')
        }
        append("]}")
    }

    fun parse(json: String): BackupParseResult {
        val root = try {
            JsonParser(json).parse() as? JsonValue.ObjectValue
                ?: return malformed("$", "Root must be an object")
        } catch (error: JsonParseException) {
            return malformed("$", error.message.orEmpty())
        }

        val issues = mutableListOf<BackupIssue>()
        reportUnknownKeys(
            root.values,
            setOf("formatVersion", "exportedAtUtc", "appVersion", "device", "fields"),
            "$",
            issues
        )
        val formatVersion = root.requiredInt("formatVersion", "$", issues)
        val exportedAtUtc = root.requiredString("exportedAtUtc", "$", issues)
        val appVersion = root.requiredString("appVersion", "$", issues)
        val deviceValue = root.values["device"] as? JsonValue.ObjectValue
        if (deviceValue == null) {
            issues += valueIssue(root.values, "device", "$.device", "object")
        }
        val device = deviceValue?.let { parseDevice(it, issues) }
        val fieldArray = root.values["fields"] as? JsonValue.ArrayValue
        if (fieldArray == null) {
            issues += valueIssue(root.values, "fields", "$.fields", "array")
        }
        val fields = fieldArray?.let { parseFields(it, issues) }.orEmpty()
        if (formatVersion != null && formatVersion != FORMAT_VERSION) {
            issues += BackupIssue(
                BackupIssueKind.UNSUPPORTED_FORMAT,
                "$.formatVersion",
                formatVersion.toString()
            )
        }

        val groupsPresent = fields.mapNotNullTo(linkedSetOf()) { entry ->
            fieldsByKey[entry.key]?.group
        }
        val missingGroups = configurationGroups - groupsPresent
        missingGroups.forEach { group ->
            issues += BackupIssue(BackupIssueKind.MISSING_GROUP, "$.fields", group.name)
        }

        val backup = if (
            formatVersion == null || exportedAtUtc == null || appVersion == null ||
            device == null || fieldArray == null
        ) {
            null
        } else {
            ConfigurationBackup(formatVersion, exportedAtUtc, appVersion, device, fields)
        }
        return BackupParseResult(backup, issues, missingGroups)
    }

    /**
     * Compares parsed values with a freshly read configuration snapshot. Range and relational
     * validation intentionally happen here, immediately before restore, rather than in [parse].
     */
    fun buildRestoreDiff(
        parsed: BackupParseResult,
        currentValues: Map<SettingField, Int>
    ): RestoreDiffResult {
        val backup = parsed.backup ?: return RestoreDiffResult(emptyList(), parsed.issues)
        val issues = parsed.issues.toMutableList()
        val entries = backup.fields.mapNotNull { entry ->
            fieldsByKey[entry.key]?.let { field -> field to entry }
        }

        val rangeValid = linkedMapOf<SettingField, Int>()
        entries.forEach { (field, entry) ->
            val simpleValidation = validateField(field, entry.raw)
            if (simpleValidation is ValidationResult.Invalid) {
                issues += BackupIssue(
                    BackupIssueKind.INVALID_VALUE,
                    "$.fields.${entry.key}.raw",
                    simpleValidation.reason
                )
            } else {
                rangeValid[field] = entry.raw
            }
        }

        val relatedValues = currentValues + rangeValid
        val changes = mutableListOf<RestoreFieldDiff>()
        entries.forEach { (field, entry) ->
            if (rangeValid[field] == null) return@forEach
            val oldRaw = currentValues[field]
            if (oldRaw == null) {
                issues += BackupIssue(
                    BackupIssueKind.CURRENT_VALUE_UNAVAILABLE,
                    "$.fields.${entry.key}",
                    entry.key
                )
                return@forEach
            }
            val validation = validateField(field, entry.raw, relatedValues)
            if (validation is ValidationResult.Invalid) {
                issues += BackupIssue(
                    BackupIssueKind.INVALID_VALUE,
                    "$.fields.${entry.key}.raw",
                    validation.reason
                )
            } else if (oldRaw != entry.raw) {
                changes += RestoreFieldDiff(field, oldRaw, entry.raw)
            }
        }
        return RestoreDiffResult(changes, issues.distinct())
    }

    private fun parseDevice(
        value: JsonValue.ObjectValue,
        issues: MutableList<BackupIssue>
    ): BackupDeviceIdentity? {
        reportUnknownKeys(value.values, setOf("name", "address", "serial", "model"), "$.device", issues)
        val name = value.requiredString("name", "$.device", issues)
        val address = value.requiredString("address", "$.device", issues)
        val serial = value.optionalString("serial", "$.device", issues)
        val model = value.optionalString("model", "$.device", issues)
        return if (name == null || address == null) null else {
            BackupDeviceIdentity(name, address, serial, model)
        }
    }

    private fun parseFields(
        value: JsonValue.ArrayValue,
        issues: MutableList<BackupIssue>
    ): List<BackupFieldEntry> {
        val fields = mutableListOf<BackupFieldEntry>()
        val seen = mutableSetOf<String>()
        value.values.forEachIndexed { index, item ->
            val path = "$.fields[$index]"
            val objectValue = item as? JsonValue.ObjectValue
            if (objectValue == null) {
                issues += BackupIssue(BackupIssueKind.INVALID_VALUE, path, "object required")
                return@forEachIndexed
            }
            reportUnknownKeys(
                objectValue.values,
                setOf("key", "raw", "display", "unit"),
                path,
                issues
            )
            val key = objectValue.requiredString("key", path, issues)
            val raw = objectValue.requiredInt("raw", path, issues)
            val display = objectValue.requiredString("display", path, issues)
            val unit = objectValue.requiredString("unit", path, issues)
            if (key == null || raw == null || display == null || unit == null) {
                return@forEachIndexed
            }
            if (fieldsByKey[key] == null) {
                issues += BackupIssue(BackupIssueKind.UNKNOWN_FIELD, "$path.key", key)
                return@forEachIndexed
            }
            if (!seen.add(key)) {
                issues += BackupIssue(BackupIssueKind.DUPLICATE_FIELD, "$path.key", key)
                return@forEachIndexed
            }
            fields += BackupFieldEntry(key, raw, display, unit)
        }
        return fields
    }

    private fun malformed(path: String, detail: String) = BackupParseResult(
        backup = null,
        issues = listOf(BackupIssue(BackupIssueKind.MALFORMED_JSON, path, detail)),
        missingGroups = configurationGroups
    )

    private fun reportUnknownKeys(
        values: Map<String, JsonValue>,
        known: Set<String>,
        path: String,
        issues: MutableList<BackupIssue>
    ) {
        (values.keys - known).forEach { key ->
            issues += BackupIssue(BackupIssueKind.UNKNOWN_KEY, "$path.$key", key)
        }
    }

    private fun JsonValue.ObjectValue.requiredString(
        key: String,
        path: String,
        issues: MutableList<BackupIssue>
    ): String? {
        val value = values[key]
        if (value == null) {
            issues += BackupIssue(BackupIssueKind.MISSING_VALUE, "$path.$key", "string required")
            return null
        }
        return (value as? JsonValue.StringValue)?.value ?: run {
            issues += BackupIssue(BackupIssueKind.INVALID_VALUE, "$path.$key", "string required")
            null
        }
    }

    private fun JsonValue.ObjectValue.optionalString(
        key: String,
        path: String,
        issues: MutableList<BackupIssue>
    ): String? {
        val value = values[key] ?: return null
        if (value is JsonValue.NullValue) return null
        return (value as? JsonValue.StringValue)?.value ?: run {
            issues += BackupIssue(BackupIssueKind.INVALID_VALUE, "$path.$key", "string required")
            null
        }
    }

    private fun JsonValue.ObjectValue.requiredInt(
        key: String,
        path: String,
        issues: MutableList<BackupIssue>
    ): Int? {
        val value = values[key]
        if (value == null) {
            issues += BackupIssue(BackupIssueKind.MISSING_VALUE, "$path.$key", "integer required")
            return null
        }
        val literal = (value as? JsonValue.NumberValue)?.literal
        val parsed = try {
            literal?.let { BigDecimal(it).intValueExact() }
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
        if (parsed == null) {
            issues += BackupIssue(BackupIssueKind.INVALID_VALUE, "$path.$key", "integer required")
        }
        return parsed
    }

    private fun valueIssue(
        values: Map<String, JsonValue>,
        key: String,
        path: String,
        expected: String
    ): BackupIssue = if (values.containsKey(key)) {
        BackupIssue(BackupIssueKind.INVALID_VALUE, path, "$expected required")
    } else {
        BackupIssue(BackupIssueKind.MISSING_VALUE, path, "$expected required")
    }

    private fun StringBuilder.appendJsonString(value: String): StringBuilder {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        return append('"')
    }
}

private sealed interface JsonValue {
    data class ObjectValue(val values: LinkedHashMap<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val literal: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    object NullValue : JsonValue
}

private class JsonParseException(message: String) : IllegalArgumentException(message)

private class JsonParser(private val source: String) {
    private var position = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (position != source.length) fail("Unexpected trailing content")
        return value
    }

    private fun parseValue(): JsonValue {
        if (position >= source.length) fail("Unexpected end of input")
        return when (source[position]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.StringValue(parseString())
            't' -> parseLiteral("true", JsonValue.BooleanValue(true))
            'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
            'n' -> parseLiteral("null", JsonValue.NullValue)
            '-', in '0'..'9' -> JsonValue.NumberValue(parseNumber())
            else -> fail("Unexpected character '${source[position]}'")
        }
    }

    private fun parseObject(): JsonValue.ObjectValue {
        expect('{')
        val values = linkedMapOf<String, JsonValue>()
        skipWhitespace()
        if (consume('}')) return JsonValue.ObjectValue(values)
        while (true) {
            skipWhitespace()
            if (position >= source.length || source[position] != '"') {
                fail("Object key must be a string")
            }
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            values[key] = parseValue()
            skipWhitespace()
            if (consume('}')) break
            expect(',')
        }
        return JsonValue.ObjectValue(values)
    }

    private fun parseArray(): JsonValue.ArrayValue {
        expect('[')
        val values = mutableListOf<JsonValue>()
        skipWhitespace()
        if (consume(']')) return JsonValue.ArrayValue(values)
        while (true) {
            skipWhitespace()
            values += parseValue()
            skipWhitespace()
            if (consume(']')) break
            expect(',')
        }
        return JsonValue.ArrayValue(values)
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (position < source.length) {
            val character = source[position++]
            when {
                character == '"' -> return result.toString()
                character == '\\' -> {
                    if (position >= source.length) fail("Incomplete escape sequence")
                    when (val escaped = source[position++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> result.append(parseUnicodeEscape())
                        else -> fail("Invalid escape sequence \\$escaped")
                    }
                }
                character.code < 0x20 -> fail("Unescaped control character")
                else -> result.append(character)
            }
        }
        fail("Unterminated string")
    }

    private fun parseUnicodeEscape(): Char {
        if (position + 4 > source.length) fail("Incomplete unicode escape")
        val literal = source.substring(position, position + 4)
        position += 4
        return literal.toIntOrNull(16)?.toChar() ?: fail("Invalid unicode escape")
    }

    private fun parseNumber(): String {
        val start = position
        consume('-')
        if (consume('0')) {
            if (position < source.length && source[position].isDigit()) fail("Leading zero")
        } else {
            requireDigits()
        }
        if (consume('.')) requireDigits()
        if (position < source.length && source[position] in "eE") {
            position += 1
            if (position < source.length && source[position] in "+-") position += 1
            requireDigits()
        }
        return source.substring(start, position)
    }

    private fun requireDigits() {
        val start = position
        while (position < source.length && source[position].isDigit()) position += 1
        if (position == start) fail("Expected digit")
    }

    private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
        if (!source.startsWith(literal, position)) fail("Invalid literal")
        position += literal.length
        return value
    }

    private fun expect(expected: Char) {
        if (!consume(expected)) fail("Expected '$expected'")
    }

    private fun consume(expected: Char): Boolean {
        if (position < source.length && source[position] == expected) {
            position += 1
            return true
        }
        return false
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position] in " \t\r\n") position += 1
    }

    private fun fail(message: String): Nothing =
        throw JsonParseException("$message at character $position")
}
