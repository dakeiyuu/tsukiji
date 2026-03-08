package com.google.maps.android.ktx.demo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedLocationsAdapter(
    private val locations: List<SavedLocation>,
    private val onDelete: (SavedLocation) -> Unit,
    private val onClick: (SavedLocation) -> Unit
) : RecyclerView.Adapter<SavedLocationsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_location_title)
        val description: TextView = view.findViewById(R.id.tv_location_description)
        val category: TextView = view.findViewById(R.id.tv_location_category)
        val score: TextView = view.findViewById(R.id.tv_location_score)
        val date: TextView = view.findViewById(R.id.tv_location_date)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_location)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_location, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val location = locations[position]
        holder.title.text = location.title
        holder.description.text = location.description
        holder.category.text = location.category
        holder.score.text = "Índice: ${String.format("%.1f", location.score)}"
        holder.date.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(location.timestamp))

        holder.itemView.setOnClickListener { onClick(location) }
        holder.btnDelete.setOnClickListener { onDelete(location) }
    }

    override fun getItemCount() = locations.size
}