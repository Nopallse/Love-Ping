package com.example.loveping

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loveping.databinding.ActivityPairSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class PairSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPairSettingsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userAdapter: UserAdapter
    private lateinit var pairRequestsAdapter: PairRequestAdapter
    private lateinit var preferenceManager: PreferenceManager
    private var pairRequestsListener: ListenerRegistration? = null
    private var currentPairListener: ListenerRegistration? = null
    private var allUsers = listOf<dataUser>() // Store all users for filtering

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
        setupRecyclerViews()
        setupSearchField()
        loadCurrentPartner()
        loadPairRequests()
        loadAvailableUsers()
    }

    override fun onDestroy() {
        super.onDestroy()
        pairRequestsListener?.remove()
        currentPairListener?.remove()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerViews() {
        // Setup adapter for available users
        userAdapter = UserAdapter { user ->
            if (currentPartner != null) {
                showMustUnpairFirstDialog()
            } else {
                sendPairRequest(user)
            }
        }
        binding.availableUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PairSettingsActivity)
            adapter = userAdapter
        }

        // Setup adapter for pair requests
        pairRequestsAdapter = PairRequestAdapter(
            onAccept = { request -> acceptPairRequest(request) },
            onReject = { request -> rejectPairRequest(request) }
        )
        binding.pairRequestsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PairSettingsActivity)
            adapter = pairRequestsAdapter
        }
    }

    private fun setupSearchField() {
        // Show the search button initially, hide search field
        binding.searchButton.visibility = View.VISIBLE
        binding.searchContainer.visibility = View.GONE

        // Setup search button click
        binding.searchButton.setOnClickListener {
            binding.searchContainer.visibility = View.VISIBLE
            binding.searchButton.visibility = View.GONE
            binding.emailSearchField.requestFocus()
        }

        // Setup close search button
        binding.closeSearchButton.setOnClickListener {
            binding.searchContainer.visibility = View.GONE
            binding.searchButton.visibility = View.VISIBLE
            binding.emailSearchField.setText("")
            filterUsers("") // Clear filter
        }

        // Setup search button action
        binding.searchEmailButton.setOnClickListener {
            val email = binding.emailSearchField.text.toString().trim()
            if (email.isNotEmpty()) {
                searchUserByEmail(email)
            } else {
                Toast.makeText(this, "Please enter an email to search", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup text change listener for real-time filtering
        binding.emailSearchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterUsers(s.toString().trim())
            }
        })
    }

    private fun filterUsers(query: String) {
        if (query.isEmpty()) {
            // Hide recycler view when not searching
            binding.availableUsersRecyclerView.visibility = View.GONE
            binding.noUsersFoundText.visibility = View.GONE
            return
        }

        val filteredUsers = allUsers.filter {
            it.email.contains(query, ignoreCase = true) && it.id != currentPartner?.id
        }

        userAdapter.updateUsers(filteredUsers)

        // Show/hide appropriate views
        binding.availableUsersRecyclerView.visibility = if (filteredUsers.isNotEmpty()) View.VISIBLE else View.GONE
        binding.noUsersFoundText.visibility = if (filteredUsers.isEmpty() && query.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun searchUserByEmail(email: String) {
        if (email.isEmpty()) return

        // Show loading indicator
        binding.searchProgress.visibility = View.VISIBLE
        binding.availableUsersRecyclerView.visibility = View.GONE
        binding.noUsersFoundText.visibility = View.GONE

        val currentUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                binding.searchProgress.visibility = View.GONE

                if (documents.isEmpty) {
                    binding.noUsersFoundText.visibility = View.VISIBLE
                    binding.availableUsersRecyclerView.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val users = documents.mapNotNull { document ->
                    val userData = document.data
                    val userId = document.getString("id") ?: ""

                    // Don't show current user or current partner
                    if (userId == currentUid || userId == currentPartner?.id) {
                        null
                    } else {
                        dataUser(
                            email = userData["email"] as? String ?: "",
                            name = userData["name"] as? String ?: "",
                            id = userData["id"] as? String ?: "",
                            gender = userData["gender"] as? String ?: ""
                        )
                    }
                }

                if (users.isEmpty()) {
                    binding.noUsersFoundText.visibility = View.VISIBLE
                    binding.availableUsersRecyclerView.visibility = View.GONE
                } else {
                    userAdapter.updateUsers(users)
                    binding.availableUsersRecyclerView.visibility = View.VISIBLE
                    binding.noUsersFoundText.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                binding.searchProgress.visibility = View.GONE
                binding.noUsersFoundText.visibility = View.VISIBLE
                Toast.makeText(this, "Error searching: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadCurrentPartner() {
        val currentUid = auth.currentUser?.uid ?: return

        currentPairListener = db.collection("pairs")
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

    private fun loadPairRequests() {
        val currentUid = auth.currentUser?.uid ?: return

        // Clear previous listener if any
        pairRequestsListener?.remove()

        // Listen for incoming pair requests
        pairRequestsListener = db.collection("pair_requests")
            .whereEqualTo("receiver", currentUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                val requests = mutableListOf<PairRequest>()

                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val senderId = doc.getString("sender") ?: continue

                        // Fetch sender details
                        db.collection("users").document(senderId).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc != null && userDoc.exists()) {
                                    val sender = dataUser(
                                        email = userDoc.getString("email") ?: "",
                                        name = userDoc.getString("name") ?: "",
                                        id = userDoc.getString("id") ?: "",
                                        gender = userDoc.getString("gender") ?: ""
                                    )

                                    requests.add(PairRequest(
                                        id = doc.id,
                                        sender = sender,
                                        timestamp = doc.getTimestamp("timestamp")?.toDate() ?: java.util.Date()
                                    ))

                                    // Update adapter with the latest requests
                                    pairRequestsAdapter.updateRequests(requests)
                                    updatePairRequestsUI(requests.isNotEmpty())
                                }
                            }
                    }
                } else {
                    // No requests
                    pairRequestsAdapter.updateRequests(emptyList())
                    updatePairRequestsUI(false)
                }
            }
    }

    private fun updatePairRequestsUI(hasRequests: Boolean) {
        binding.pairRequestsSection.visibility = if (hasRequests) View.VISIBLE else View.GONE
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

        // Load all users but don't show them immediately (for search)
        db.collection("users")
            .whereNotEqualTo("id", currentUid)
            .get()
            .addOnSuccessListener { documents ->
                allUsers = documents.mapNotNull { document ->
                    document.data.let {
                        dataUser(
                            email = it["email"] as? String ?: "",
                            name = it["name"] as? String ?: "",
                            id = it["id"] as? String ?: "",
                            gender = it["gender"] as? String ?: ""
                        )
                    }
                }.filter { it.id != currentPartner?.id }

                // Don't show users until search
                binding.availableUsersRecyclerView.visibility = View.GONE
            }
    }

    private fun sendPairRequest(selectedUser: dataUser) {
        val currentUid = auth.currentUser?.uid ?: return
        val currentUserName = preferenceManager.getUserData()?.name ?: "Someone"

        // Check if a request already exists
        db.collection("pair_requests")
            .whereEqualTo("sender", currentUid)
            .whereEqualTo("receiver", selectedUser.id)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Create new request
                    val requestData = hashMapOf(
                        "sender" to currentUid,
                        "receiver" to selectedUser.id,
                        "status" to "pending",
                        "timestamp" to FieldValue.serverTimestamp()
                    )

                    db.collection("pair_requests")
                        .add(requestData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Pair request sent to ${selectedUser.name}", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to send request: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "You already sent a request to ${selectedUser.name}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun acceptPairRequest(request: PairRequest) {
        val currentUid = auth.currentUser?.uid ?: return

        // First check if user already has a partner
        db.collection("pairs")
            .whereArrayContains("users", currentUid)
            .get()
            .addOnSuccessListener { pairDocuments ->
                if (pairDocuments.isEmpty) {
                    // Create new pair
                    val pairData = hashMapOf(
                        "users" to listOf(currentUid, request.sender.id),
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    db.collection("pairs")
                        .add(pairData)
                        .addOnSuccessListener {
                            // Update request status
                            db.collection("pair_requests")
                                .document(request.id)
                                .update("status", "accepted")
                                .addOnSuccessListener {
                                    Toast.makeText(this, "You are now paired with ${request.sender.name}", Toast.LENGTH_SHORT).show()

                                    // Save new partner data to preferences
                                    preferenceManager.savePartnerToken(request.sender.id)
                                    currentPartner = request.sender
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to create pair: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    showMustUnpairFirstDialog()
                }
            }
    }

    private fun rejectPairRequest(request: PairRequest) {
        db.collection("pair_requests")
            .document(request.id)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(this, "Request from ${request.sender.name} rejected", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to reject request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun unpair() {
        val currentUid = auth.currentUser?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Unpair Confirmation")
            .setMessage("Are you sure you want to unpair with ${currentPartner?.name}?")
            .setPositiveButton("Yes") { _, _ ->
                // Show a progress dialog
                val progressDialog = AlertDialog.Builder(this)
                    .setMessage("Unpairing...")
                    .setCancelable(false)
                    .create()

                progressDialog.show()

                db.collection("pairs")
                    .whereArrayContains("users", currentUid)
                    .get()
                    .addOnSuccessListener { documents ->
                        // Delete all pair documents
                        var deletedCount = 0
                        val totalDocuments = documents.size()

                        if (totalDocuments == 0) {
                            // No pairs found, just clear local data
                            finishUnpairing(progressDialog)
                            return@addOnSuccessListener
                        }

                        for (document in documents) {
                            document.reference.delete()
                                .addOnSuccessListener {
                                    deletedCount++
                                    if (deletedCount >= totalDocuments) {
                                        // All documents deleted
                                        finishUnpairing(progressDialog)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    progressDialog.dismiss()
                                    Toast.makeText(this, "Failed to unpair: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "Failed to unpair: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun finishUnpairing(progressDialog: AlertDialog) {
        // Clear partner data from preferences
        preferenceManager.savePartnerToken(null)
        preferenceManager.savePartnerData(null) // Explicitly clear partner data
        preferenceManager.saveLastHeartbeatTime(0) // Reset the last heartbeat time

        // Ensure PreferenceManager has completely cleared the data
        preferenceManager.clearPartnerData()
        preferenceManager.getPartnerData()
Log.d("PreferenceManager", "getPartnerData: ${preferenceManager.getPartnerData()}")
        // Update UI and dismiss dialog
        currentPartner = null
        progressDialog.dismiss()

        // Refresh main activity to ensure it updates
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        // Finish the current activity
        finish()

        Toast.makeText(this, "Successfully unpaired", Toast.LENGTH_SHORT).show()
    }

    private fun updateCurrentPartnerUI() {
        with(binding) {
            if (currentPartner != null) {
                currentPartnerSection.visibility = View.VISIBLE
                currentPartnerName.text = currentPartner?.name ?: "No partner connected"
                currentPartnerEmail.text = currentPartner?.email ?: ""
                unpairButton.visibility = View.VISIBLE
                unpairButton.setOnClickListener { unpair() }

                // Update search instruction
                searchInstruction.text = "Search for another user (you'll need to unpair first)"
            } else {
                currentPartnerSection.visibility = View.GONE
                unpairButton.visibility = View.GONE

                // Update search instruction
                searchInstruction.text = "Search for a user by email to connect"
            }
        }
    }

    private fun showNoPartner() {
        // Clear partner data from preferences
        preferenceManager.savePartnerToken(null)
        preferenceManager.savePartnerData(null)
        currentPartner = null
    }

    private fun showMustUnpairFirstDialog() {
        AlertDialog.Builder(this)
            .setTitle("Already Paired")
            .setMessage("You must unpair from your current partner before connecting with someone else.")
            .setPositiveButton("Unpair Now") { _, _ -> unpair() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}