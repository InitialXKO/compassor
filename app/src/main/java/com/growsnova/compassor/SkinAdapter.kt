package com.growsnova.compassor

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class SkinAdapter(
    private val themes: List<SkinTheme>,
    private var selectedKey: String,
    private val onSkinSelected: (SkinTheme) -> Unit
) : RecyclerView.Adapter<SkinAdapter.SkinViewHolder>() {

    class SkinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.skinCard)
        val swatchBg: View = itemView.findViewById(R.id.swatchBg)
        val swatchRing: View = itemView.findViewById(R.id.swatchRing)
        val swatchTarget: View = itemView.findViewById(R.id.swatchTarget)
        val title: TextView = itemView.findViewById(R.id.skinTitle)
        val description: TextView = itemView.findViewById(R.id.skinDescription)
        val checkIcon: ImageView = itemView.findViewById(R.id.selectedCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkinViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_skin_option, parent, false)
        return SkinViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkinViewHolder, position: Int) {
        val theme = themes[position]
        val context = holder.itemView.context

        holder.title.text = context.getString(theme.nameResId)
        holder.description.text = context.getString(theme.descResId)

        holder.swatchBg.backgroundTintList = ColorStateList.valueOf(theme.skin.backgroundColor)
        holder.swatchRing.backgroundTintList = ColorStateList.valueOf(theme.skin.compassRingColor)
        holder.swatchTarget.backgroundTintList = ColorStateList.valueOf(theme.skin.targetColor)

        val isSelected = theme.key.equals(selectedKey, ignoreCase = true) ||
                (selectedKey == "Forest" && theme.key == "EmeraldForest") ||
                (selectedKey == "Ocean" && theme.key == "DeepSeaAbyss")

        if (isSelected) {
            holder.card.strokeColor = theme.skin.compassRingColor
            holder.card.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
            holder.checkIcon.visibility = View.VISIBLE
            holder.checkIcon.imageTintList = ColorStateList.valueOf(theme.skin.compassRingColor)
        } else {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)
            val strokeColor = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else Color.LTGRAY
            holder.card.strokeColor = strokeColor
            holder.card.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
            holder.checkIcon.visibility = View.GONE
        }

        holder.card.setOnClickListener {
            selectedKey = theme.key
            notifyDataSetChanged()
            onSkinSelected(theme)
        }
    }

    override fun getItemCount(): Int = themes.size

    fun setSelectedKey(key: String) {
        selectedKey = key
        notifyDataSetChanged()
    }
}
