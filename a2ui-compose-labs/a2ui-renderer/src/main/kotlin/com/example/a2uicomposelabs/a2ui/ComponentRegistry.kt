package com.example.a2uicomposelabs.a2ui

import androidx.compose.runtime.Composable

/** A registered component factory: code the app wrote and approved. */
typealias A2uiComponentFactory =
    @Composable (node: ComponentNode, scope: BindingScope, renderChild: @Composable (String) -> Unit) -> Unit

/**
 * The catalog allowlist, enforced in code.
 * Unknown components are skipped — never crash, never guess.
 */
class ComponentRegistry(private val factories: Map<String, A2uiComponentFactory>) {

    /** Everything this registry can draw — the other half of the catalog it is paired with. */
    val names: Set<String> get() = factories.keys

    operator fun plus(extra: Map<String, A2uiComponentFactory>): ComponentRegistry =
        ComponentRegistry(factories + extra)

    @Composable
    fun Render(
        node: ComponentNode,
        scope: BindingScope,
        renderChild: @Composable (String) -> Unit,
    ) {
        val factory = factories[node.component] ?: return
        factory(node, scope, renderChild)
    }
}
