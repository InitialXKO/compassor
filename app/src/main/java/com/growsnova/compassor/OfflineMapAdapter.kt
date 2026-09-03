package com.growsnova.compassor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.offlinemap.OfflineMapCity
import com.amap.api.maps.offlinemap.OfflineMapStatus
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

data class OfflineMapItem(
    val cityName: String,
    val cityCode: String,
    val size: Long,
    val state: Int,
    val completeCode: Int,
    val isProvinceHeader: Boolean = false,
    val provinceName: String = ""
)

class OfflineMapAdapter(
    private val context: Context,
    private val onDownloadClicked: (OfflineMapItem) -> Unit,
    private val onPauseClicked: (OfflineMapItem) -> Unit,
    private val onDeleteClicked: (OfflineMapItem) -> Unit
) : RecyclerView.Adapter<OfflineMapAdapter.ViewHolder>() {

    private val items = mutableListOf<OfflineMapItem>()

    fun submitList(newList: List<OfflineMapItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_offline_map, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cityNameText: TextView = itemView.findViewById(R.id.cityNameText)
        private val sizeText: TextView = itemView.findViewById(R.id.sizeText)
        private val actionButton: MaterialButton = itemView.findViewById(R.id.actionButton)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
        private val progressIndicator: LinearProgressIndicator = itemView.findViewById(R.id.progressIndicator)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val progressContainer: View = itemView.findViewById(R.id.progressContainer)

        fun bind(item: OfflineMapItem) {
            val displayName = if (item.provinceName.isNotEmpty() && item.provinceName != item.cityName) {
                "${item.provinceName} - ${item.cityName}"
            } else {
                item.cityName
            }
            cityNameText.text = displayName
            sizeText.text = formatSize(item.size)

            actionButton.setOnClickListener(null)
            deleteButton.setOnClickListener(null)

            when (item.state) {
                OfflineMapStatus.SUCCESS -> {
                    progressContainer.visibility = View.GONE
                    actionButton.visibility = View.VISIBLE
                    actionButton.text = context.getString(R.string.offline_map_completed)
                    actionButton.isEnabled = false
                    deleteButton.visibility = View.VISIBLE
                    deleteButton.setOnClickListener { onDeleteClicked(item) }
                }
                OfflineMapStatus.LOADING -> {
                    progressContainer.visibility = View.VISIBLE
                    progressIndicator.isIndeterminate = false
                    progressIndicator.progress = item.completeCode
                    statusText.text = context.getString(R.string.offline_map_downloading, item.completeCode)
                    actionButton.visibility = View.VISIBLE
                    actionButton.isEnabled = true
                    actionButton.text = context.getString(R.string.offline_map_pause)
                    actionButton.setOnClickListener { onPauseClicked(item) }
                    deleteButton.visibility = View.GONE
                }
                OfflineMapStatus.PAUSE -> {
                    progressContainer.visibility = View.VISIBLE
                    progressIndicator.isIndeterminate = false
                    progressIndicator.progress = item.completeCode
                    statusText.text = context.getString(R.string.offline_map_paused)
                    actionButton.visibility = View.VISIBLE
                    actionButton.isEnabled = true
                    actionButton.text = context.getString(R.string.offline_map_resume)
                    actionButton.setOnClickListener { onDownloadClicked(item) }
                    deleteButton.visibility = View.VISIBLE
                    deleteButton.setOnClickListener { onDeleteClicked(item) }
                }
                OfflineMapStatus.UNZIP -> {
                    progressContainer.visibility = View.VISIBLE
                    progressIndicator.isIndeterminate = true
                    statusText.text = context.getString(R.string.offline_map_unzipping)
                    actionButton.visibility = View.GONE
                    deleteButton.visibility = View.GONE
                }
                OfflineMapStatus.WAITING -> {
                    progressContainer.visibility = View.VISIBLE
                    progressIndicator.isIndeterminate = true
                    statusText.text = context.getString(R.string.offline_map_waiting)
                    actionButton.visibility = View.GONE
                    deleteButton.visibility = View.GONE
                }
                OfflineMapStatus.EXCEPTION_NETWORK_LOADING,
                OfflineMapStatus.EXCEPTION_AMAP,
                OfflineMapStatus.EXCEPTION_SDCARD,
                OfflineMapStatus.ERROR -> {
                    progressContainer.visibility = View.VISIBLE
                    progressIndicator.isIndeterminate = false
                    progressIndicator.progress = item.completeCode
                    statusText.text = context.getString(R.string.offline_map_error)
                    actionButton.visibility = View.VISIBLE
                    actionButton.isEnabled = true
                    actionButton.text = context.getString(R.string.offline_map_download)
                    actionButton.setOnClickListener { onDownloadClicked(item) }
                    deleteButton.visibility = View.VISIBLE
                    deleteButton.setOnClickListener { onDeleteClicked(item) }
                }
                OfflineMapStatus.NEW_VERSION -> {
                    progressContainer.visibility = View.GONE
                    actionButton.visibility = View.VISIBLE
                    actionButton.isEnabled = true
                    actionButton.text = context.getString(R.string.offline_map_update)
                    actionButton.setOnClickListener { onDownloadClicked(item) }
                    deleteButton.visibility = View.VISIBLE
                    deleteButton.setOnClickListener { onDeleteClicked(item) }
                }
                else -> {
                    // Not downloaded
                    progressContainer.visibility = View.GONE
                    actionButton.visibility = View.VISIBLE
                    actionButton.isEnabled = true
                    actionButton.text = context.getString(R.string.offline_map_download)
                    actionButton.setOnClickListener { onDownloadClicked(item) }
                    deleteButton.visibility = View.GONE
                }
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes <= 0) return ""
            val mb = bytes.toDouble() / (1024 * 1024)
            return "%.1f MB".format(mb)
        }
    }
}
