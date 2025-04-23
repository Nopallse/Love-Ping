package com.example.loveping

import android.content.Context
import android.util.Log
import com.google.gson.Gson

class PreferenceManager(context: Context) {
    private val prefs = context.getSharedPreferences("LovePing", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PARTNER_TOKEN = "partner_token"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_PARTNER_DATA = "partner_data"
        private const val KEY_LAST_HEARTBEAT_TIME = "last_heartbeat_time"
    }

    fun savePartnerToken(token: String?) {
        prefs.edit().putString(KEY_PARTNER_TOKEN, token).apply()
    }

    fun getPartnerToken(): String? {
        return prefs.getString(KEY_PARTNER_TOKEN, null)
    }

    fun saveUserData(data: dataUser?) {
        val json = gson.toJson(data)
        prefs.edit().putString(KEY_USER_DATA, json).apply()
    }

    fun getUserData(): dataUser? {
        val json = prefs.getString(KEY_USER_DATA, null)
        return if (json != null) {
            gson.fromJson(json, dataUser::class.java)
        } else null
    }

    fun savePartnerData(data: dataUser?) {
        val json = gson.toJson(data)
        prefs.edit().putString(KEY_PARTNER_DATA, json).apply()
    }

    fun getPartnerData(): dataUser? {
        val json = prefs.getString(KEY_PARTNER_DATA, null)
        return if (json != null) {
            gson.fromJson(json, dataUser::class.java)
        } else null
    }

    // Method to specifically clear partner data
    fun clearPartnerData() {
        prefs.edit()
            .remove(KEY_PARTNER_TOKEN)
            .remove(KEY_PARTNER_DATA)
            .apply()
    }

    fun saveLastHeartbeatTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_HEARTBEAT_TIME, timestamp).apply()
    }





    fun clearAll() {
        prefs.edit().clear().apply()
    }
}