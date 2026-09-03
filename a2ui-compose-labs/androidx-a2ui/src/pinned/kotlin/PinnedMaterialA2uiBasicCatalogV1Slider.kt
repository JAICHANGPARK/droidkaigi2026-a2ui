/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material3.a2ui.catalog

// Upstream's catalog/MaterialA2uiBasicCatalogV1Slider.kt at androidx-main ac85854 (2 Sep 2026,
// re-synced through the ktfmt 0.64 reformat in 7ac433e),
// with exactly one change: the stateful SliderState(trackRange = ...) constructor is swapped for
// the stateless Slider(value, onValueChange, valueRange, steps, ...) overload, because trackRange
// is still unpublished (newest on Maven is material3 1.5.0-alpha27). Everything else — the
// min > max guard, coerceIn, the header row, the hidden step ticks — is byte-for-byte upstream.
//
// This replaces the older PinnedMaterialSliderComponent.kt. On 1 Sep 2026 upstream deleted the
// standalone MaterialSliderComponent and moved Slider into the A2uiBasicCatalogV1 contract
// (6198d65), so the pin has to be contract-shaped now: MaterialA2uiBasicCatalogV1.kt wires this
// object into MaterialA2uiBasicCatalogV1Defaults.slider by name.
//
// Delete this file and drop the exclude in androidx-a2ui/build.gradle.kts when a published
// material3 ships SliderState(trackRange = ...).

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.protocol.A2uiException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A Jetpack Compose Material 3 implementation of the A2UI Basic Catalog `"Slider"` component. */
internal object MaterialA2uiBasicCatalogV1Slider : A2uiBasicCatalogV1.Slider {

    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String?,
        min: Float,
        max: Float,
        value: Float,
        onValueChange: (Float) -> Unit,
        enabled: Boolean,
        modifier: Modifier,
    ) {
        // TODO(b/549060875): Figure out how this should be reflected in the UI: switch back to the
        //  loading state or show some kind of error.
        if (min > max) {
            SideEffect(min, max) {
                reportError(
                    A2uiException.A2uiRuntimeException(
                        "Min value cannot be greater than max value."
                    )
                )
            }
        } else {
            val coercedValue = value.coerceIn(min, max)
            val valueRange = min..max
            val steps = (max.toInt() - min.toInt() - 1).coerceAtLeast(0)

            Column(
                modifier = modifier.then(SliderBottomPaddingModifier),
                verticalArrangement = SliderVerticalArrangement,
            ) {
                Row(
                    modifier = SliderHeaderRowModifier,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (label != null) {
                        Text(text = label, modifier = Modifier.weight(1f, fill = false))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Text(text = coercedValue.roundToInt().toString())
                }

                // Upstream: SliderState(value = coercedValue, steps = steps, trackRange = range).
                Slider(
                    value = coercedValue,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                    enabled = enabled,
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            enabled = enabled,
                            drawTick = EmptySliderTrack,
                        )
                    },
                )
            }
        }
    }
}

private val SliderVerticalArrangement = Arrangement.spacedBy(4.dp)
private val SliderHeaderRowModifier = Modifier.fillMaxWidth()
private val SliderBottomPaddingModifier = Modifier.padding(bottom = 8.dp)
private val EmptySliderTrack: DrawScope.(Offset, Color) -> Unit = { _, _ ->
    /* no-op to hide step dots */
}
