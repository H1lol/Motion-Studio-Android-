package com.motionstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionstudio.part5.EffectParameter
import com.motionstudio.part5.ParameterType
import kotlin.math.roundToInt

/**
 * Motion Studio — real dynamic effect inspector.
 *
 * The parameter schema is the actual Part 5 EffectParameter model:
 * FLOAT, INT, BOOL, COLOR, VECTOR2, VECTOR3, STRING and ENUM.
 *
 * This file deliberately does not invent an EffectParameter subclass or a
 * second effect schema. The application layer supplies the selected effect's
 * real parameters and receives changed values as Any?.
 *
 * Value conventions:
 *  FLOAT   -> Double
 *  INT     -> Int
 *  BOOL    -> Boolean
 *  COLOR   -> Int ARGB
 *  VECTOR2 -> List<Double> of size 2
 *  VECTOR3 -> List<Double> of size 3
 *  STRING  -> String
 *  ENUM    -> String (or the application's selected enum value)
 */
@Composable
fun EffectInspector(
    effectName: String,
    parameters: List<EffectParameter>,
    values: Map<String, Any?>,
    onParameterChange: (parameterId: String, value: Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(effectName, style = MaterialTheme.typography.titleMedium)

        if (parameters.isEmpty()) {
            Text(
                "This effect has no exposed parameters.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            parameters.forEach { parameter ->
                EffectParameterEditor(
                    parameter = parameter,
                    currentValue = values[parameter.id] ?: parameter.default,
                    onValueChange = { onParameterChange(parameter.id, it) }
                )
            }
        }
    }
}

@Composable
private fun EffectParameterEditor(
    parameter: EffectParameter,
    currentValue: Any?,
    onValueChange: (Any?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (parameter.type) {
            ParameterType.FLOAT -> FloatParameter(
                parameter = parameter,
                value = asDouble(currentValue, parameter.default),
                onChange = onValueChange
            )

            ParameterType.INT -> IntParameter(
                parameter = parameter,
                value = asInt(currentValue, parameter.default),
                onChange = onValueChange
            )

            ParameterType.BOOL -> {
                var checked by remember(parameter.id, currentValue) {
                    mutableStateOf(asBoolean(currentValue, parameter.default))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(parameter.name)
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            onValueChange(it)
                        }
                    )
                }
            }

            ParameterType.COLOR -> ColorParameter(
                parameter = parameter,
                value = asInt(currentValue, parameter.default),
                onChange = onValueChange
            )

            ParameterType.VECTOR2 -> VectorParameter(
                parameter = parameter,
                size = 2,
                value = asVector(currentValue, parameter.default, 2),
                onChange = onValueChange
            )

            ParameterType.VECTOR3 -> VectorParameter(
                parameter = parameter,
                size = 3,
                value = asVector(currentValue, parameter.default, 3),
                onChange = onValueChange
            )

            ParameterType.STRING -> {
                var value by remember(parameter.id, currentValue) {
                    mutableStateOf(currentValue?.toString() ?: parameter.default?.toString().orEmpty())
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        onValueChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(parameter.name) },
                    singleLine = true
                )
            }

            ParameterType.ENUM -> {
                // EffectParameter carries the actual enum type but not an
                // option-list field. Therefore the inspector edits the
                // selected value without fabricating enum choices.
                var value by remember(parameter.id, currentValue) {
                    mutableStateOf(currentValue?.toString() ?: parameter.default?.toString().orEmpty())
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        onValueChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(parameter.name) },
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun FloatParameter(
    parameter: EffectParameter,
    value: Double,
    onChange: (Any?) -> Unit
) {
    val min = parameter.min
    val max = parameter.max

    if (min != null && max != null && max > min) {
        val clamped = value.coerceIn(min, max)
        Text("${parameter.name}: ${formatNumber(clamped)}")
        Slider(
            value = clamped.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = max > min
        )
    } else {
        var text by remember(parameter.id, value) { mutableStateOf(formatNumber(value)) }
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toDoubleOrNull()?.let(onChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(parameter.name) },
            singleLine = true
        )
    }

    if (parameter.keyframable) {
        Text(
            "Keyframable",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IntParameter(
    parameter: EffectParameter,
    value: Int,
    onChange: (Any?) -> Unit
) {
    val min = parameter.min?.roundToInt()
    val max = parameter.max?.roundToInt()

    if (min != null && max != null && max > min) {
        val clamped = value.coerceIn(min, max)
        Text("${parameter.name}: $clamped")
        Slider(
            value = clamped.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat()
        )
    } else {
        var text by remember(parameter.id, value) { mutableStateOf(value.toString()) }
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toIntOrNull()?.let(onChange)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(parameter.name) },
            singleLine = true
        )
    }
}

@Composable
private fun ColorParameter(
    parameter: EffectParameter,
    value: Int,
    onChange: (Any?) -> Unit
) {
    // The Part 5 schema stores colors as Int. We expose the actual ARGB
    // integer through a hexadecimal editor rather than depending on a
    // non-standard Material color-picker API.
    var text by remember(parameter.id, value) {
        mutableStateOf(String.format("%08X", value))
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val clean = input.removePrefix("#").take(8)
            text = clean
            clean.toLongOrNull(16)?.let { parsed ->
                onChange(parsed.toInt())
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("${parameter.name} (ARGB hex)") },
        singleLine = true
    )
}

@Composable
private fun VectorParameter(
    parameter: EffectParameter,
    size: Int,
    value: List<Double>,
    onChange: (Any?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(size) { index ->
            var text by remember(parameter.id, index, value[index]) {
                mutableStateOf(formatNumber(value[index]))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    val parsed = input.toDoubleOrNull() ?: return@OutlinedTextField
                    val next = value.toMutableList()
                    next[index] = parsed
                    onChange(next.toList())
                },
                modifier = Modifier.weight(1f),
                label = { Text("${parameter.name} ${axisName(index)}") },
                singleLine = true
            )
        }
    }
}

private fun asDouble(value: Any?, fallback: Any?): Double =
    when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Number -> value.toDouble()
        else -> when (fallback) {
            is Number -> fallback.toDouble()
            else -> 0.0
        }
    }

private fun asInt(value: Any?, fallback: Any?): Int =
    when (value) {
        is Int -> value
        is Number -> value.toInt()
        else -> when (fallback) {
            is Number -> fallback.toInt()
            else -> 0
        }
    }

private fun asBoolean(value: Any?, fallback: Any?): Boolean =
    value as? Boolean ?: (fallback as? Boolean ?: false)

private fun asVector(value: Any?, fallback: Any?, size: Int): List<Double> {
    fun convert(source: Any?): List<Double>? {
        return when (source) {
            is List<*> -> source.mapNotNull {
                (it as? Number)?.toDouble()
            }.takeIf { it.size == size }
            is FloatArray -> source.map { it.toDouble() }.takeIf { it.size == size }
            is DoubleArray -> source.toList().takeIf { it.size == size }
            else -> null
        }
    }

    return convert(value)
        ?: convert(fallback)
        ?: List(size) { 0.0 }
}

private fun axisName(index: Int): String =
    when (index) {
        0 -> "X"
        1 -> "Y"
        else -> "Z"
    }

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else "%.4f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
