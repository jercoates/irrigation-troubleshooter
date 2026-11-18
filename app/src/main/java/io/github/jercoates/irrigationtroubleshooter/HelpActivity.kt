package io.github.jercoates.irrigationtroubleshooter

import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Native meter reference, rendered from assets/help.json (generated from the guide by tools/extract.mjs). */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val appBar = findViewById<View>(R.id.appbar)
        val scroll = findViewById<View>(R.id.scroll)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            appBar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val container = findViewById<LinearLayout>(R.id.sections)
        val sections = runCatching {
            HelpData.parse(assets.open("help.json").bufferedReader().use { it.readText() })
        }.getOrDefault(emptyList())

        for (s in sections) {
            container.addView(textView(R.style.HelpHeading).apply {
                text = s.title
                ViewCompat.setAccessibilityHeading(this, true)
            })
            for (p in s.body) {
                container.addView(textView(R.style.HelpBody).apply {
                    text = Html.fromHtml(p, Html.FROM_HTML_MODE_COMPACT)
                })
            }
        }
    }

    private fun textView(style: Int) = TextView(this, null, 0, style).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
