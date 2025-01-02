package com.example.loveping

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.loveping.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupToolbar()
        setupGenderDropdown()
        loadUserProfile()
        setupSaveButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupGenderDropdown() {
        val genders = arrayOf("Male", "Female", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        (binding.genderInput as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""

        // Set email (non-editable)
        binding.emailInput.setText(email)

        // Load existing profile data
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    binding.nameInput.setText(document.getString("name") ?: "")
                    binding.genderInput.setText(document.getString("gender") ?: "", false)
                }
            }
            .addOnFailureListener { e ->
                showError("Failed to load profile: ${e.message}")
            }
    }

    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val email = auth.currentUser?.email ?: ""
            val name = binding.nameInput.text.toString().trim()
            val gender = binding.genderInput.text.toString().trim()

            // Validation
            if (name.isEmpty()) {
                binding.nameLayout.error = "Name is required"
                return@setOnClickListener
            }
            if (gender.isEmpty()) {
                binding.genderLayout.error = "Gender is required"
                return@setOnClickListener
            }

            // Show loading state
            binding.saveButton.isEnabled = false
            binding.saveButton.text = "Saving..."

            // Create user data
            val userData = hashMapOf(
                "id" to uid,
                "email" to email,
                "name" to name,
                "gender" to gender
            )

            // Save to Firestore
            db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile saved successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    showError("Failed to save profile: ${e.message}")
                    binding.saveButton.isEnabled = true
                    binding.saveButton.text = "Save Profile"
                }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}