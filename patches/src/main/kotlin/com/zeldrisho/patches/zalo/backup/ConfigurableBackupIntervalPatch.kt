package com.zeldrisho.patches.zalo.backup

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.zeldrisho.patches.zalo.shared.Constants.COMPATIBILITY_ZALO

private const val INTERVAL_KEY = "SERVER_CONFIG_SYNC_MESSAGE_INTERVAL_"
private const val INTERVAL_GETTER = "Lu40/p0;->Y(JZLjava/lang/String;)J"
private val intervalHours = setOf("1", "3", "6", "12")

/**
 * Replaces the native interval getter result with [hours] converted to milliseconds.
 *
 * Accepts only 1, 3, 6, or 12 hours. Requires a single known getter, the account-specific
 * key before it, and a wide result immediately after it; validation fails before mutation.
 * The remaining scheduler instructions, including native backup guards, are preserved.
 */
internal fun overrideBackupInterval(method: MutableMethod, hours: String) {
    require(hours in intervalHours) { "Backup interval must be one of 1, 3, 6, or 12 hours" }
    val instructions = method.implementation?.instructions?.toList()
        ?: error("Zalo backup interval: scheduler has no implementation")
    val getterIndices = instructions.indices.filter { index ->
        val reference = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
        instructions[index].opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
            reference?.let { "${it.definingClass}->${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}" } == INTERVAL_GETTER
    }
    check(getterIndices.size == 1) { "Zalo backup interval: expected exactly one interval getter" }
    val getterIndex = getterIndices.single()
    val keyExists = instructions.take(getterIndex).any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? StringReference
        instruction.opcode == Opcode.CONST_STRING && reference?.string == INTERVAL_KEY
    }
    check(keyExists) { "Zalo backup interval: account-specific interval key not found before getter" }
    val resultIndex = getterIndex + 1
    check(resultIndex < instructions.size && instructions[resultIndex].opcode == Opcode.MOVE_RESULT_WIDE) {
        "Zalo backup interval: interval getter is not followed by move-result-wide"
    }
    val register = (instructions[resultIndex] as? OneRegisterInstruction)?.registerA
        ?: error("Zalo backup interval: result register not found")
    val millis = hours.toLong() * 3_600_000L
    method.replaceInstruction(resultIndex, "const-wide/32 v$register, $millis")
}

@Suppress("unused")
val configurableZaloBackupIntervalPatch = bytecodePatch(
    name = "Configurable native backup interval",
    description = "Overrides only Zalo's native auto-backup interval (1, 3, 6, or 12 hours). " +
        "Native opt-in, account, network, and backup guards remain in place.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_ZALO)

    val hours by stringOption(
        key = "hours",
        default = "6",
        title = "Backup interval (hours)",
        description = "Choose 1, 3, 6, or 12 hours.",
        required = true,
    )

    execute {
        overrideBackupInterval(BackupScheduler.method, hours!!)
    }
}
