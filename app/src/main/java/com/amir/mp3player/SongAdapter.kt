package com.amir.mp3player

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.amir.mp3player.databinding.ItemSongBinding

class SongAdapter(private val onClick: (Int) -> Unit) :
    RecyclerView.Adapter<SongAdapter.VH>() {

    private var songs: List<Song> = emptyList()
    private var currentId: Long = -1L

    fun submit(list: List<Song>) {
        songs = list
        notifyDataSetChanged()
    }

    fun setCurrent(id: Long) {
        currentId = id
        notifyDataSetChanged()
    }

    class VH(val b: ItemSongBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = songs.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = songs[position]
        holder.b.title.text = s.title
        holder.b.subtitle.text = s.artist + " - " + s.album
        holder.b.title.setTextColor(
            if (s.id == currentId) Color.parseColor("#BB86FC") else Color.WHITE
        )
        holder.b.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos)
        }
    }
}
