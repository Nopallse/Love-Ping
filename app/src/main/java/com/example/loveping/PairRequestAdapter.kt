package com.example.loveping

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.loveping.databinding.ItemPairRequestBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PairRequestAdapter(
    private val requests: MutableList<PairRequest> = mutableListOf(),
    private val onAccept: (PairRequest) -> Unit,
    private val onReject: (PairRequest) -> Unit
) : RecyclerView.Adapter<PairRequestAdapter.RequestViewHolder>() {

    class RequestViewHolder(val binding: ItemPairRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemPairRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val request = requests[position]
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        with(holder.binding) {
            userName.text = request.sender.name
            userEmail.text = request.sender.email
            requestTime.text = "Requested: ${dateFormat.format(request.timestamp)}"

            acceptButton.setOnClickListener { onAccept(request) }
            rejectButton.setOnClickListener { onReject(request) }
        }
    }

    override fun getItemCount() = requests.size

    fun updateRequests(newRequests: List<PairRequest>) {
        requests.clear()
        requests.addAll(newRequests)
        notifyDataSetChanged()
    }
}