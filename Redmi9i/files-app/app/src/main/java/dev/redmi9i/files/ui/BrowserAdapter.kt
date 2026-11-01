package dev.redmi9i.files.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.redmi9i.files.R
import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.storage.Kind
import dev.redmi9i.files.storage.Volume
import kotlinx.coroutines.Job

private fun kindColor(kind: Kind): Int = when (kind) {
    Kind.DIR -> 0xFFF2B632.toInt()
    Kind.IMAGE -> 0xFF3FA7D6.toInt()
    Kind.VIDEO -> 0xFFD9232D.toInt()
    Kind.AUDIO -> 0xFFF28C28.toInt()
    Kind.PDF -> 0xFFE5534B.toInt()
    Kind.TEXT -> 0xFF9FB3C8.toInt()
    Kind.DOCX -> 0xFF4A7BD0.toInt()
    Kind.XLSX -> 0xFF45A66B.toInt()
    Kind.ZIP -> 0xFFB08B5A.toInt()
    Kind.OTHER -> 0xFF7F93A8.toInt()
}

/** Folder or search-result list with optional multi-select. */
class BrowserAdapter(
    private val thumbs: ThumbnailLoader,
    private val onClick: (FileInfo) -> Unit,
    private val onLongClick: (FileInfo) -> Unit,
) : RecyclerView.Adapter<BrowserAdapter.VH>() {

    var items: List<FileInfo> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    val selected = LinkedHashSet<String>()
    var showPath = false

    fun toggle(info: FileInfo) {
        if (!selected.add(info.ref.path)) selected.remove(info.ref.path)
        notifyDataSetChanged()
    }

    fun clearSelection() { selected.clear(); notifyDataSetChanged() }
    fun selectAll() { items.forEach { selected += it.ref.path }; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val sub: TextView = v.findViewById(R.id.sub)
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val info = items[position]
        val kind = info.kind()
        h.job?.cancel()
        h.name.text = info.name
        h.sub.text = when {
            showPath -> info.ref.parent?.path.orEmpty()
            info.isDir -> Format.date(info.modified)
            else -> Format.size(info.size) + "  ·  " + Format.date(info.modified)
        }
        h.icon.setImageResource(if (info.isDir) R.drawable.ic_folder else R.drawable.ic_file)
        h.icon.setColorFilter(kindColor(kind), PorterDuff.Mode.SRC_IN)
        h.icon.tag = info.ref.path
        if (kind == Kind.IMAGE || kind == Kind.VIDEO) {
            val hit = thumbs.cached(info.ref.path)
            if (hit != null) {
                h.icon.clearColorFilter(); h.icon.setImageBitmap(hit)
            } else {
                h.job = thumbs.load(info.ref.path, kind) { bmp ->
                    if (bmp != null && h.icon.tag == info.ref.path) {
                        h.icon.clearColorFilter(); h.icon.setImageBitmap(bmp)
                    }
                }
            }
        }
        h.itemView.setBackgroundColor(if (info.ref.path in selected) 0x33F2B632 else Color.TRANSPARENT)
        h.itemView.setOnClickListener { onClick(info) }
        h.itemView.setOnLongClickListener { onLongClick(info); true }
    }

    override fun onViewRecycled(h: VH) { h.job?.cancel() }
}

sealed class HomeEntry {
    data class Vol(val volume: Volume) : HomeEntry()
    data class Header(val text: String) : HomeEntry()
    data class Pin(val ref: FileRef, val label: String, val note: String?) : HomeEntry()
}

/** Home screen: one card per mounted volume, then the pinned folders. */
class HomeAdapter(
    private val onVolume: (Volume) -> Unit,
    private val onPin: (FileRef) -> Unit,
    private val onPinLong: (FileRef) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var entries: List<HomeEntry> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    private class VolVH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.vol_name)
        val bar: ProgressBar = v.findViewById(R.id.vol_bar)
        val info: TextView = v.findViewById(R.id.vol_info)
    }

    private class HeaderVH(v: View) : RecyclerView.ViewHolder(v) { val text: TextView = v.findViewById(R.id.header_text) }

    private class PinVH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val sub: TextView = v.findViewById(R.id.sub)
    }

    override fun getItemViewType(position: Int) = when (entries[position]) {
        is HomeEntry.Vol -> 0
        is HomeEntry.Header -> 1
        is HomeEntry.Pin -> 2
    }

    override fun getItemCount() = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> VolVH(inf.inflate(R.layout.item_volume, parent, false))
            1 -> HeaderVH(inf.inflate(R.layout.item_header, parent, false))
            else -> PinVH(inf.inflate(R.layout.item_file, parent, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        when (val e = entries[position]) {
            is HomeEntry.Vol -> {
                val v = e.volume
                h as VolVH
                h.name.text = v.label + if (v.removable && !v.writable) "  (read-only until you allow access)" else ""
                val used = (v.total - v.free).coerceAtLeast(0)
                h.bar.max = 1000
                h.bar.progress = if (v.total > 0) (used * 1000 / v.total).toInt() else 0
                h.info.text = "${Format.size(v.free)} free of ${Format.size(v.total)}"
                h.itemView.setOnClickListener { onVolume(v) }
            }
            is HomeEntry.Header -> (h as HeaderVH).text.text = e.text
            is HomeEntry.Pin -> {
                h as PinVH
                h.icon.setImageResource(R.drawable.ic_folder)
                h.icon.setColorFilter(0xFFF2B632.toInt(), PorterDuff.Mode.SRC_IN)
                h.name.text = e.label
                h.sub.text = e.note ?: e.ref.path
                h.itemView.setBackgroundColor(Color.TRANSPARENT)
                h.itemView.setOnClickListener { onPin(e.ref) }
                h.itemView.setOnLongClickListener { onPinLong(e.ref); true }
            }
        }
    }
}
