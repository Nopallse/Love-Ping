package com.example.loveping

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loveping.databinding.ActivityPairSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PairSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPairSettingsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userAdapter: UserAdapter
    private lateinit var preferenceManager: PreferenceManager

    private var currentPartner: dataUser?
        get() = preferenceManager.getPartnerData()
        set(value) {
            preferenceManager.savePartnerData(value)
            updateCurrentPartnerUI()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        preferenceManager = PreferenceManager(this)

        setupToolbar()
        setupRecyclerView()
        loadCurrentPartner()
        loadAvailableUsers()
    }


    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter { user ->
            createPair(user)
        }
        binding.availableUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PairSettingsActivity)
            adapter = userAdapter
        }
    }

    private fun loadCurrentPartner() {
        val currentUid = auth.currentUser?.uid ?: return

        db.collection("pairs")
            .whereArrayContains("users", currentUid)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && !snapshot.isEmpty) {
                    val pair = snapshot.documents[0]
                    val users = pair.get("users") as? List<String>
                    val partnerId = users?.find { it != currentUid }

                    if (partnerId != null) {
                        // Save partner token to preferences
                        preferenceManager.savePartnerToken(partnerId)
                        loadPartnerDetails(partnerId)
                    } else {
                        showNoPartner()
                    }
                } else {
                    showNoPartner()
                }
            }
    }

    private fun loadPartnerDetails(partnerId: String) {
        db.collection("users")
            .document(partnerId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Update currentPartner which automatically updates preferences
                    currentPartner = dataUser(
                        email = document.getString("email") ?: "",
                        name = document.getString("name") ?: "",
                        id = document.getString("id") ?: "",
                        gender = document.getString("gender") ?: ""
                    )
                }
            }
    }

    private fun loadAvailableUsers() {
        val currentUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereNotEqualTo("id", currentUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { document ->
                        document.data?.let {
                            dataUser(
                                email = it["email"] as? String ?: "",
                                name = it["name"] as? String ?: "",
                                id = it["id"] as? String ?: "",
                                gender = it["gender"] as? String ?: ""
                            )
                        }
                    }
                    userAdapter.updateUsers(users.filter { it.id != currentPartner?.id })
                }
            }
    }

    private fun createPair(selectedUser: dataUser) {
        val currentUid = auth.currentUser?.uid ?: return
        val pairData = hashMapOf(
            "users" to listOf(currentUid, selectedUser.id),
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("pairs")
            .add(pairData)
            .addOnSuccessListener {
                // Save new partner data to preferences
                preferenceManager.savePartnerToken(selectedUser.id)
                currentPartner = selectedUser
                Toast.makeText(this, "Successfully paired with ${selectedUser.name}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create pair: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun unpair() {
        val currentUid = auth.currentUser?.uid ?: return

        db.collection("pairs")
            .whereArrayContains("users", currentUid)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.delete()
                }
                // Clear partner data from preferences
                preferenceManager.savePartnerToken(null)
                currentPartner = null
                Toast.makeText(this, "Successfully unpaired", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to unpair: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCurrentPartnerUI() {
        with(binding) {
            currentPartnerName.text = currentPartner?.name ?: "No partner connected"
            currentPartnerEmail.text = currentPartner?.email ?: ""
            unpairButton.visibility = if (currentPartner != null) View.VISIBLE else View.GONE
            unpairButton.setOnClickListener { unpair() }
        }
    }

    private fun showNoPartner() {
        // Clear partner data from preferences
        preferenceManager.savePartnerToken(null)
        currentPartner = null
    }
}