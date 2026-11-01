package dev.redmi9i.files.viewer

import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import dev.redmi9i.files.R
import dev.redmi9i.files.storage.FileRef

/** Shared shell for every viewer: a toolbar with a back arrow, and a [content] area below it. */
abstract class ViewerActivity : AppCompatActivity() {
    protected lateinit var content: FrameLayout
    protected lateinit var toolbar: Toolbar
    protected lateinit var ref: FileRef

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(Opener.EXTRA_PATH)
        if (path == null) { finish(); return }
        ref = FileRef("local", path)

        toolbar = Toolbar(this).apply {
            setBackgroundResource(R.drawable.bg_toolbar)
            setTitleTextAppearance(this@ViewerActivity, R.style.GrandLineToolbarTitle)
            title = ref.name
            elevation = 4f
        }
        content = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(toolbar, ViewGroup.LayoutParams.MATCH_PARENT, (56 * resources.displayMetrics.density).toInt())
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        onReady(savedInstanceState)
    }

    protected abstract fun onReady(savedInstanceState: Bundle?)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { onBackPressedDispatcher.onBackPressed(); return true }
        return super.onOptionsItemSelected(item)
    }
}
