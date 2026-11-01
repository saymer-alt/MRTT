package com.mrtt.client

internal object MaxWebViewScripts {
    fun theme(dark: Boolean): String {
        val scheme = if (dark) "dark" else "light"
        return """
            (function() {
                var scheme = '$scheme';
                try {
                    var theme = JSON.parse(localStorage.getItem('__oneme_theme') || '{}');
                    if (theme.colorScheme !== scheme) {
                        theme.colorScheme = scheme;
                        localStorage.setItem('__oneme_theme', JSON.stringify(theme));
                    }
                } catch (e) {}
                window.__mrttColorScheme = scheme;
                function apply() {
                    var root = document.documentElement;
                    if (root.dataset.colorScheme !== window.__mrttColorScheme) {
                        root.dataset.colorScheme = window.__mrttColorScheme;
                    }
                    root.style.colorScheme = window.__mrttColorScheme;
                }
                apply();
                if (!window.__mrttThemeObserver) {
                    window.__mrttThemeObserver = new MutationObserver(apply);
                    window.__mrttThemeObserver.observe(document.documentElement, {
                        attributes: true, attributeFilter: ['data-color-scheme']
                    });
                }
            })();
        """.trimIndent()
    }
}
