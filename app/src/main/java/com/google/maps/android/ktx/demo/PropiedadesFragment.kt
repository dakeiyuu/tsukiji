package com.google.maps.android.ktx.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout

data class PropiedadItem(
    val nombre: String,
    val direccion: String,
    val precio: String,
    val tipo: String,
    val fotos: List<Uri> = emptyList()
)

class PropiedadesFragment : Fragment() {

    private val propiedades = mutableListOf(
        PropiedadItem("Local Comercial Centro", "Av. Constitución 100, Monterrey", "$15,000 MXN/mes", "Renta"),
        PropiedadItem("Oficina San Pedro", "Blvd. Antonio L. Rodríguez 3000", "$25,000 MXN/mes", "Renta"),
        PropiedadItem("Bodega Industrial", "Carretera a Laredo km 12", "$2,500,000 MXN", "Venta"),
        PropiedadItem("Local en Plaza", "Plaza Fiesta San Agustín", "$18,000 MXN/mes", "Renta"),
        PropiedadItem("Terreno Comercial", "Av. Eugenio Garza Sada", "$8,000,000 MXN", "Venta")
    )

    private lateinit var adapter: PropiedadAdapter
    private var currentFilter = "Todas"

    private val fotosSeleccionadas = mutableListOf<Uri>()
    private var slotFotoActual = 0

    private var ivFotos = arrayOfNulls<ImageView>(4)
    private var placeholders = arrayOfNulls<LinearLayout>(4)
    private var btnRemoves = arrayOfNulls<ImageView>(4)
    private var frameFotos = arrayOfNulls<FrameLayout>(4)

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val index = slotFotoActual - 1
            if (index < fotosSeleccionadas.size) {
                fotosSeleccionadas[index] = uri
            } else {
                fotosSeleccionadas.add(uri)
            }
            mostrarFotoEnSlot(index, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_propiedades, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_propiedades)
        val tabFilter = view.findViewById<TabLayout>(R.id.tab_filter)
        val btnAgregar = view.findViewById<ImageView>(R.id.btn_agregar_propiedad)

        adapter = PropiedadAdapter(getFilteredList()) { position ->
            val filtered = getFilteredList()
            val toRemove = filtered[position]
            propiedades.remove(toRemove)
            adapter.updateData(getFilteredList())
            Toast.makeText(requireContext(), "Propiedad eliminada", Toast.LENGTH_SHORT).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = tab?.text?.toString() ?: "Todas"
                adapter.updateData(getFilteredList())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnAgregar.setOnClickListener { showAddPropertyDialog() }
    }

    private fun getFilteredList(): List<PropiedadItem> = when (currentFilter) {
        "Renta" -> propiedades.filter { it.tipo == "Renta" }
        "Venta" -> propiedades.filter { it.tipo == "Venta" }
        else -> propiedades.toList()
    }

    private fun showAddPropertyDialog() {
        fotosSeleccionadas.clear()

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_agregar_propiedad, null)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val toggleGroup = dialogView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggle_tipo)
        val etNombre = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_nombre)
        val etDireccion = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_direccion)
        val etPrecio = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_precio)
        val btnPublicar = dialogView.findViewById<android.widget.Button>(R.id.btn_publicar)

        ivFotos[0] = dialogView.findViewById(R.id.iv_foto_1)
        ivFotos[1] = dialogView.findViewById(R.id.iv_foto_2)
        ivFotos[2] = dialogView.findViewById(R.id.iv_foto_3)
        ivFotos[3] = dialogView.findViewById(R.id.iv_foto_4)

        placeholders[0] = dialogView.findViewById(R.id.placeholder_1)
        placeholders[1] = dialogView.findViewById(R.id.placeholder_2)
        placeholders[2] = dialogView.findViewById(R.id.placeholder_3)
        placeholders[3] = dialogView.findViewById(R.id.placeholder_4)

        btnRemoves[0] = dialogView.findViewById(R.id.btn_remove_1)
        btnRemoves[1] = dialogView.findViewById(R.id.btn_remove_2)
        btnRemoves[2] = dialogView.findViewById(R.id.btn_remove_3)
        btnRemoves[3] = dialogView.findViewById(R.id.btn_remove_4)

        frameFotos[0] = dialogView.findViewById(R.id.frame_foto_1)
        frameFotos[1] = dialogView.findViewById(R.id.frame_foto_2)
        frameFotos[2] = dialogView.findViewById(R.id.frame_foto_3)
        frameFotos[3] = dialogView.findViewById(R.id.frame_foto_4)

        for (i in 0..3) {
            val slot = i + 1
            frameFotos[i]?.setOnClickListener {
                if (fotosSeleccionadas.size >= 4 && i >= fotosSeleccionadas.size) {
                    Toast.makeText(requireContext(), "Máximo 4 fotos", Toast.LENGTH_SHORT).show()
                } else if (i <= fotosSeleccionadas.size) {
                    abrirGaleria(slot)
                } else {
                    Toast.makeText(requireContext(), "Agrega las fotos en orden", Toast.LENGTH_SHORT).show()
                }
            }
            btnRemoves[i]?.setOnClickListener { eliminarFoto(i) }
        }

        toggleGroup.check(R.id.btn_venta)

        btnPublicar.setOnClickListener {
            val nombre = etNombre.text?.toString()?.trim() ?: ""
            val direccion = etDireccion.text?.toString()?.trim() ?: ""
            val precio = etPrecio.text?.toString()?.trim() ?: ""

            if (nombre.isEmpty() || direccion.isEmpty() || precio.isEmpty()) {
                Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tipoSeleccionado = when (toggleGroup.checkedButtonId) {
                R.id.btn_renta -> "Renta"
                else -> "Venta"
            }

            val nuevaPropiedad = PropiedadItem(
                nombre = nombre,
                direccion = direccion,
                precio = "$$precio MXN${if (tipoSeleccionado == "Renta") "/mes" else ""}",
                tipo = tipoSeleccionado,
                fotos = fotosSeleccionadas.toList()
            )

            propiedades.add(index = 0, element = nuevaPropiedad)
            adapter.updateData(getFilteredList())
            dialog.dismiss()
            Toast.makeText(requireContext(), "Propiedad publicada", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun abrirGaleria(slot: Int) {
        slotFotoActual = slot
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }

    private fun mostrarFotoEnSlot(index: Int, uri: Uri) {
        ivFotos[index]?.setImageURI(uri)
        ivFotos[index]?.visibility = View.VISIBLE
        placeholders[index]?.visibility = View.GONE
        btnRemoves[index]?.visibility = View.VISIBLE
    }

    private fun eliminarFoto(index: Int) {
        if (index >= fotosSeleccionadas.size) return
        fotosSeleccionadas.removeAt(index)
        for (i in 0..3) {
            if (i < fotosSeleccionadas.size) {
                mostrarFotoEnSlot(i, fotosSeleccionadas[i])
            } else {
                ivFotos[i]?.setImageURI(null)
                ivFotos[i]?.visibility = View.GONE
                placeholders[i]?.visibility = View.VISIBLE
                btnRemoves[i]?.visibility = View.GONE
            }
        }
    }

    inner class PropiedadAdapter(
        private var items: List<PropiedadItem>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<PropiedadAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNombre: TextView = view.findViewById(R.id.tv_nombre)
            val tvPrecio: TextView = view.findViewById(R.id.tv_precio)
            val tvTipo: TextView = view.findViewById(R.id.tv_tipo)
            val btnEliminar: ImageView = view.findViewById(R.id.btn_eliminar)
        }

        fun updateData(newItems: List<PropiedadItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_propiedad, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvNombre.text = item.nombre
            holder.tvPrecio.text = "Precio: ${item.precio}"
            holder.tvTipo.text = if (item.tipo == "Renta") "Renta 🏠" else "Venta 🏢"
            holder.tvTipo.setBackgroundResource(
                if (item.tipo == "Renta") R.drawable.chip_renta_bg else R.drawable.chip_venta_bg
            )
            holder.btnEliminar.setOnClickListener { onDelete(holder.adapterPosition) }
        }

        override fun getItemCount() = items.size
    }
}