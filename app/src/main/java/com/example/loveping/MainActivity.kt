package com.example.loveping

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.loveping.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var vibrator: Vibrator
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var preferenceManager: PreferenceManager
    private var currentPartner: dataUser?
        get() = preferenceManager.getPartnerData()
        set(value) {
            preferenceManager.savePartnerData(value)
        }
    // Properties now backed by PreferenceManager
    private var partnerToken: String?
        get() = preferenceManager.getPartnerToken()
        set(value) = preferenceManager.savePartnerToken(value)

    private var dataUser: dataUser?
        get() = preferenceManager.getUserData()
        set(value) = preferenceManager.saveUserData(value)

    private var dataPartner: dataUser?
        get() = preferenceManager.getPartnerData()
        set(value) = preferenceManager.savePartnerData(value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Initialize vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Start background service
        startHeartbeatService()

        auth.currentUser?.uid?.let { currentUid ->
            getPartnerToken(currentUid)
            getPartnerData(currentUid)
        }

        // Set click listener for heart button
        binding.heartButton.setOnClickListener {
            sendHeartbeat()
            animateHeart()
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {

                    auth.signOut()
                    preferenceManager.savePartnerToken(null)
                    currentPartner = null

                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                R.id.action_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                R.id.action_pair_settings -> {
                    startActivity(Intent(this, PairSettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Listen for heartbeats while app is in foreground
        listenToHeartbeats()
    }

    @SuppressLint("SetTextI18n")
    private fun getPartnerData(uid: String) {
        db.collection("users")
            .whereNotEqualTo("id", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && !snapshot.isEmpty) {
                    val partner = snapshot.documents[0]
                    Log.d("MainActivity", "Partner: $partner")

                    // Update dataPartner property which will automatically save to preferences
                    dataPartner = partner.data?.let {
                        dataUser(
                            email = it["email"] as? String ?: "",
                            name = it["name"] as? String ?: "",
                            id = it["id"] as? String ?: "",
                            gender = it["gender"] as? String ?: ""
                        )
                    }
                }
            }
    }

    private fun getPartnerToken(currentUid: String) {
        db.collection("pairs")
            .whereArrayContains("users", currentUid)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                if (snapshot != null && !snapshot.isEmpty) {
                    val pair = snapshot.documents[0]
                    val users = pair.get("users") as? List<String>

                    // Update partnerToken property which will automatically save to preferences
                    partnerToken = users?.find { it != currentUid }
                }
            }
    }

    private fun startHeartbeatService() {
        val serviceIntent = Intent(this, HeartbeatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun animateHeart() {
        // Heart beat animation
        val scaleX = ObjectAnimator.ofFloat(binding.heartButton, "scaleX", 1f, 1.4f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.heartButton, "scaleY", 1f, 1.4f, 1f)
        val rotation = ObjectAnimator.ofFloat(binding.heartButton, "rotation", 0f, -8f, 8f, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, rotation)
            duration = 700
            interpolator = OvershootInterpolator()
            start()
        }

        // Ripple effect
        animateRipple(binding.rippleCircle1, 0)
        animateRipple(binding.rippleCircle2, 150)

        // Animate floating hearts
        animateFloatingHeart()

        // Update last heartbeat time
        updateLastHeartbeatTime()
    }

    private fun animateRipple(view: View, delay: Long) {
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f

        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 2f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 2f)
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            startDelay = delay
            duration = 1000
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun animateFloatingHeart() {
        val heart = if (Math.random() > 0.5) binding.backgroundHeart1 else binding.backgroundHeart2

        // Reset position and make visible
        heart.visibility = View.VISIBLE
        heart.translationX = (Math.random() * 500 - 250).toFloat()
        heart.translationY = binding.heartButton.y
        heart.alpha = 0.3f
        heart.scaleX = 0f
        heart.scaleY = 0f

        val moveY = ObjectAnimator.ofFloat(heart, "translationY", heart.translationY, heart.translationY - 500f)
        val alpha = ObjectAnimator.ofFloat(heart, "alpha", 0.3f, 0f)
        val scaleX = ObjectAnimator.ofFloat(heart, "scaleX", 0f, 1.5f)
        val scaleY = ObjectAnimator.ofFloat(heart, "scaleY", 0f, 1.5f)
        val rotation = ObjectAnimator.ofFloat(heart, "rotation", 0f, (Math.random() * 180 - 90).toFloat())

        AnimatorSet().apply {
            playTogether(moveY, alpha, scaleX, scaleY, rotation)
            duration = 2000
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    heart.visibility = View.INVISIBLE
                }
            })
            start()
        }
    }

    private fun updateLastHeartbeatTime() {
        binding.lastHeartbeatText.text = "Last heartbeat: Just now"
        binding.lastHeartbeatText.alpha = 1f

        ObjectAnimator.ofFloat(binding.lastHeartbeatText, "alpha", 1f, 0.6f).apply {
            duration = 1000
            startDelay = 1000
            start()
        }
    }

    private fun listenToHeartbeats() {
        val currentUser = auth.currentUser?.uid
        db.collection("heartbeats")
            .whereEqualTo("receiver", currentUser)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                snapshots?.documentChanges?.forEach { doc ->
                    if (doc.type == DocumentChange.Type.ADDED) {
                        vibrate()
                        animateHeart()
                    }
                }
            }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1500)
        }
    }

    private fun sendHeartbeat() {
        partnerToken?.let { token ->
            db.collection("heartbeats").add(
                hashMapOf(
                    "sender" to auth.currentUser?.uid,
                    "receiver" to token,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Service will continue running in background
        // It will only stop if explicitly stopped or if the system kills it
    }
}