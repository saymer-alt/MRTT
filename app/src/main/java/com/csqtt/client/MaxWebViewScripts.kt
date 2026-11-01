package com.csqtt.client

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
                window.__csqttrColorScheme = scheme;
                function apply() {
                    var root = document.documentElement;
                    if (root.dataset.colorScheme !== window.__csqttrColorScheme) {
                        root.dataset.colorScheme = window.__csqttrColorScheme;
                    }
                    root.style.colorScheme = window.__csqttrColorScheme;
                }
                apply();
                if (!window.__csqttrThemeObserver) {
                    window.__csqttrThemeObserver = new MutationObserver(apply);
                    window.__csqttrThemeObserver.observe(document.documentElement, {
                        attributes: true, attributeFilter: ['data-color-scheme']
                    });
                }
            })();
        """.trimIndent()
    }
}
