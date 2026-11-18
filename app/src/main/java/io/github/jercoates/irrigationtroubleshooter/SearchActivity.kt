package io.github.jercoates.irrigationtroubleshooter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Native symptom search over assets/search-index.json (generated from the guide by tools/extract.mjs). */
class SearchActivity : AppCompatActivity() {

    private var entries: List<SearchEntry> = emptyList()
    private var results: List<SearchEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applyInsets()

        entries = runCatching {
            SearchIndex.parse(assets.open("search-index.json").bufferedReader().use { it.readText() })
        }.getOrDefault(emptyList())

        val list = findViewById<ListView>(R.id.results)
        val empty = findViewById<TextView>(R.id.empty)
        list.emptyView = empty
        val adapter = ResultsAdapter()
        list.adapter = adapter

        list.setOnItemClickListener { _, _, pos, _ ->
            val hit = results[pos]
            setResult(Activity.RESULT_OK, Intent().putStringArrayListExtra(EXTRA_PATH, ArrayList(hit.path)))
            finish()
        }

        findViewById<EditText>(R.id.query).apply { requestFocus() }.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                results = SearchIndex.search(entries, q)
                empty.setText(if (q.isBlank()) R.string.search_hint_empty else R.string.search_no_results)
                adapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
        })
    }

    private fun applyInsets() {
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val appBar = findViewById<View>(R.id.appbar)
        val content = findViewById<View>(R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            appBar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            content.updatePadding(left = bars.left, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private inner class ResultsAdapter : BaseAdapter() {
        override fun getCount() = results.size
        override fun getItem(position: Int) = results[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_result, parent, false)
            val e = results[position]
            v.findViewById<TextView>(R.id.title).text = e.title
            v.findViewById<TextView>(R.id.trail).text =
                (e.trail + e.crumb).filter { it.isNotBlank() }.joinToString(" › ")
            return v
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
