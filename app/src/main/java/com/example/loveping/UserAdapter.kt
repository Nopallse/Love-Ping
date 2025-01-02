package com.example.loveping

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.loveping.databinding.ItemUserBinding

class UserAdapter(
    private val users: MutableList<dataUser> = mutableListOf(),
    private val onPairClick: (dataUser) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        with(holder.binding) {
            userName.text = user.name
            userEmail.text = user.email
            pairButton.setOnClickListener { onPairClick(user) }
        }
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<dataUser>) {
        users.clear()
        users.addAll(newUsers)
        notifyDataSetChanged()
    }
}