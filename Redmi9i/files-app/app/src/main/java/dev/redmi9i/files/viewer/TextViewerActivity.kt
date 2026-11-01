package dev.redmi9i.files.viewer

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import dev.redmi9i.files.storage.FsRouter
import dev.redmi9i.files.storage.NeedsGrantException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Plain-text viewer. Files up to 1 MB are editable; larger ones show their first 2 MB read-only. */
class TextViewerActivity : ViewerActivity() {
    private lateinit var edit: EditText
    private lateinit var router: FsRouter
    private var charset: Charset = Charsets.UTF_8
    private var editable = true
    private var dirty = false
    private var loaded = false

    override fun onReady(savedInstanceState: Bundle?) {
        router = FsRouter(applicationContext)
        val pad = (16 * resources.displayMetrics.density).toInt()
        edit = EditText(this).apply {
            background = null
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setPadding(pad, pad, pad, pad)
            isVerticalScrollBarEnabled = true
        }
        content.addView(edit)
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (loaded && !dirty) { dirty = true; invalidateOptionsMenu() }
            }
        })
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { read(File(ref.path)) } }
            result.onSuccess { (text, cs, truncated) ->
                charset = cs
                edit.setText(text)
                editable = !truncated
                if (!editable) {
                    edit.keyListener = null
                    edit.setTextIsSelectable(true)
                    toolbar.subtitle = "Read-only: first 2 MB of a large file"
                }
                loaded = true
            }.onFailure { edit.setText("Cannot read this file: ${it.message}"); edit.keyListener = null }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!dirty) { finish(); return }
                AlertDialog.Builder(this@TextViewerActivity).setTitle("Unsaved changes")
                    .setPositiveButton("Save") { _, _ -> save(thenFinish = true) }
                    .setNegativeButton("Discard") { _, _ -> finish() }
                    .setNeutralButton("Keep editing", null).show()
            }
        })
    }

    private fun read(f: File): Triple<String, Charset, Boolean> {
        val limit = 2 * 1024 * 1024
        val truncated = f.length() > limit || f.length() > 1024 * 1024
        val want = minOf(f.length(), limit.toLong()).toInt()
        val bytes = ByteArray(want)
        f.inputStream().use { input ->
            var off = 0
            while (off < want) {
                val n = input.read(bytes, off, want - off)
                if (n < 0) break
                off += n
            }
        }
        val tolerant = f.length() > limit // a cut-off multibyte tail must not fail the decode
        return try {
            val dec = Charsets.UTF_8.newDecoder()
                .onMalformedInput(if (tolerant) CodingErrorAction.REPLACE else CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            Triple(dec.decode(ByteBuffer.wrap(bytes)).toString(), Charsets.UTF_8, truncated)
        } catch (e: CharacterCodingException) {
            Triple(String(bytes, Charsets.ISO_8859_1), Charsets.ISO_8859_1, truncated)
        }
    }

    private fun save(thenFinish: Boolean = false) {
        val text = edit.text.toString()
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { router.fs.openWrite(ref).use { it.write(text.toByteArray(charset)) } }
            }
            r.onSuccess {
                dirty = false; invalidateOptionsMenu()
                Toast.makeText(this@TextViewerActivity, "Saved", Toast.LENGTH_SHORT).show()
                if (thenFinish) finish()
            }.onFailure {
                val msg = if (it is NeedsGrantException) "Allow access to this drive from the main screen first" else (it.message ?: "Save failed")
                Toast.makeText(this@TextViewerActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SAVE, 0, "Save").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_SAVE)?.isVisible = dirty && editable
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SAVE) { save(); return true }
        return super.onOptionsItemSelected(item)
    }

    private companion object { const val MENU_SAVE = 1 }
}
