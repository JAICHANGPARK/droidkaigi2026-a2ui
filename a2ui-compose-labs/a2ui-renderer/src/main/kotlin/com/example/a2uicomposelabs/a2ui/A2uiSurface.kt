package com.example.a2uicomposelabs.a2ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

private const val MAX_DEPTH = 24

/** Shared so a surface rendered without a catalog does not rebuild its scope every frame. */
private val EmptyEvaluator = A2uiDynamicEvaluator()

/**
 * Set by template lists (see `List` in [BasicCatalog]) while rendering one item,
 * so the item subtree resolves relative paths against `<listPath>/<index>`.
 */
val LocalA2uiItemScope = compositionLocalOf<BindingScope?> { null }

/**
 * A surface is just a composable. The app decides where it appears;
 * the agent only fills the box it is given.
 */
@Composable
fun A2uiSurface(
    state: SurfaceState,
    registry: ComponentRegistry,
    onAction: (A2uiAction) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The catalog whose functions `{"call": ...}` properties may name. Pass the same catalog
     * the client validates against; leaving it out means literals and bindings only.
     */
    catalog: A2uiCatalog? = null,
) {
    val evaluator = catalog?.evaluator ?: EmptyEvaluator
    val scope = remember(state, onAction, evaluator) {
        BindingScope(state, onAction, evaluator = evaluator)
    }
    Box(modifier) {
        RenderNode(id = "root", state = state, registry = registry, scope = scope, depth = 0)
    }
}

@Composable
private fun RenderNode(
    id: String,
    state: SurfaceState,
    registry: ComponentRegistry,
    scope: BindingScope,
    depth: Int,
) {
    if (depth > MAX_DEPTH) return          // bounded depth
    val node = state.components[id] ?: return  // not arrived yet → progressive rendering
    val effectiveScope = LocalA2uiItemScope.current ?: scope
    registry.Render(node, effectiveScope) { childId ->
        RenderNode(childId, state, registry, scope, depth + 1)
    }
}
