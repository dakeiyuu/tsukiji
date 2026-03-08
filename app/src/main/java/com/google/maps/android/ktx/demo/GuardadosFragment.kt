package com.google.maps.android.ktx.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class GuardadosFragment : Fragment() {

    private val savedLocations = mutableListOf<SavedLocation>()
    private lateinit var adapter: SavedLocationAdapter
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_guardados, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyView = view.findViewById(R.id.tv_empty)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_saved_locations)

        adapter = SavedLocationAdapter(savedLocations) { position ->
            savedLocations.removeAt(position)
            adapter.notifyItemRemoved(position)
            updateEmptyView()
            Toast.makeText(requireContext(), "Ubicación eliminada", Toast.LENGTH_SHORT).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        updateEmptyView()
    }

    fun addSavedLocation(location: SavedLocation) {
        savedLocations.add(0, location)
        if (::adapter.isInitialized) {
            adapter.notifyItemInserted(0)
            updateEmptyView()
        }
    }

    private fun updateEmptyView() {
        if (::emptyView.isInitialized) {
            emptyView.visibility = if (savedLocations.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    inner class SavedLocationAdapter(
        private val items: MutableList<SavedLocation>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<SavedLocationAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tv_location_title)
            val tvDescription: TextView = view.findViewById(R.id.tv_location_description)
            val tvCategory: TextView = view.findViewById(R.id.tv_location_category)
            val tvScore: TextView = view.findViewById(R.id.tv_location_score)
            val tvDate: TextView = view.findViewById(R.id.tv_location_date)
            val btnDelete: ImageView = view.findViewById(R.id.btn_delete_location)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_saved_location, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvDescription.text = item.description
            holder.tvCategory.text = getCategoryLabel(item.category)
            holder.tvScore.text = "Índice: ${String.format("%.1f", item.score)}"
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(item.timestamp))
            holder.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
        }

        override fun getItemCount() = items.size

        private fun getCategoryLabel(category: String): String = when (category) {
            "restaurant" -> "🍽️ Restaurante"
            "retail" -> "🛍️ Tienda"
            "service" -> "🔧 Servicio"
            "entertainment" -> "🎬 Entretenimiento"
            else -> category
        }
    }
}