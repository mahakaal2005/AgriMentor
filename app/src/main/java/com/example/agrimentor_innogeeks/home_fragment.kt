package com.example.agrimentor_innogeeks

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agrimentor_innogeeks.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

class home_fragment : Fragment(R.layout.fragment_home) {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var recommendationAdapter: RecommendationAdapter
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val recommendationList = mutableListOf<Recommendation>()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentHomeBinding.bind(view)

        // Set initial visibility states
        setWeatherViewsVisibility(View.VISIBLE)

        // Check and request permissions if needed
        checkLocationPermissions()

        setupUserProfile()
        setupRecommendationsRecyclerView()
        loadRecommendations()
        loadDailyTip()
        setupWeatherCardClick()
    }

    private fun setWeatherViewsVisibility(visibility: Int) {
        binding.temperatureTextView.visibility = visibility
        binding.weatherConditionTextView.visibility = visibility
        binding.humidityTextView.visibility = visibility
        binding.windTextView.visibility = visibility
        binding.locationTextView.visibility = visibility
        binding.weatherIconView.visibility = visibility
    }

    private fun checkLocationPermissions() {
        when {
            hasLocationPermissions() -> {
                // Permissions already granted
                loadWeatherData()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // Show rationale and request permissions
                showPermissionRationaleDialog()
            }
            else -> {
                // Request permissions directly
                requestLocationPermissions()
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return locationPermissions.all {
            context?.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showPermissionRationaleDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Location Permission Required")
            .setMessage("We need location permissions to provide accurate weather information for your area.")
            .setPositiveButton("OK") { _, _ ->
                requestLocationPermissions()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(
                    context,
                    "Location permission denied. Using default location.",
                    Toast.LENGTH_SHORT
                ).show()
                loadWeatherData() // Load with default coordinates
            }
            .create()
            .show()
    }

    private fun requestLocationPermissions() {
        requestPermissions(locationPermissions, LOCATION_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted
                Toast.makeText(context, "Location permissions granted", Toast.LENGTH_SHORT).show()
                loadWeatherData()
            } else {
                // Check if we should show the "never ask again" rationale
                val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

                if (!shouldShowRationale) {
                    // User selected "Never ask again"
                    showOpenSettingsDialog()
                } else {
                    // Permission denied but user didn't select "Never ask again"
                    Toast.makeText(
                        context,
                        "Location permission denied. Using default location.",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadWeatherData() // Load with default coordinates
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun showOpenSettingsDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Permission Required")
            .setMessage("Location permission is required for accurate weather data. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                // Open app settings
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", requireActivity().packageName, null)
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(
                    context,
                    "Using default location for weather updates.",
                    Toast.LENGTH_SHORT
                ).show()
                loadWeatherData() // Load with default coordinates
            }
            .create()
            .show()
    }


    override fun onResume() {
        super.onResume()
        // Reload weather data when fragment resumes
        loadWeatherData()
    }

    private fun setupWeatherCardClick() {
        binding.weatherCard.setOnClickListener {
            val weatherFragment = weather()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_layout, weatherFragment)
                .addToBackStack("weatherFragment")
                .commit()
        }
    }

    private fun loadWeatherData() {
        binding.temperatureTextView.text = "Loading..."

        // Default coordinates to fall back on
        val defaultLatitude = 28.6139
        val defaultLongitude = 77.2090

        if (!hasLocationPermissions()) {
            fetchWeatherData(defaultLatitude, defaultLongitude)
            return
        }

        try {
            val locationManager = context?.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager == null) {
                fetchWeatherData(defaultLatitude, defaultLongitude)
                return
            }

            // First try to get a cached location from any provider
            val lastLocation = getLastKnownLocation(locationManager)
            if (lastLocation != null && System.currentTimeMillis() - lastLocation.time < 10 * 60 * 1000) { // Use if less than 10 minutes old
                fetchWeatherData(lastLocation.latitude, lastLocation.longitude)
                return
            }

            // No recent location, request fresh updates with priority for network first (faster)
            var locationUpdateRequested = false
            val locationListener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    fetchWeatherData(location.latitude, location.longitude)
                    try {
                        locationManager.removeUpdates(this)
                    } catch (se: SecurityException) {
                        // Ignore
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            // Try network provider first (faster but less accurate)
            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                try {
                    locationManager.requestLocationUpdates(
                        android.location.LocationManager.NETWORK_PROVIDER,
                        0, 0f, locationListener
                    )
                    locationUpdateRequested = true
                } catch (se: SecurityException) {
                    // Fall through to next provider
                }
            }

            // Also use GPS provider for better accuracy
            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                try {
                    locationManager.requestLocationUpdates(
                        android.location.LocationManager.GPS_PROVIDER,
                        0, 0f, locationListener
                    )
                    locationUpdateRequested = true
                } catch (se: SecurityException) {
                    // Fall through
                }
            }

            // If we couldn't request any location updates, use default location
            if (!locationUpdateRequested) {
                fetchWeatherData(defaultLatitude, defaultLongitude)
                return
            }

            // Set a longer timeout for location updates
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    locationManager.removeUpdates(locationListener)
                    if (binding.temperatureTextView.text == "Loading...") {
                        fetchWeatherData(defaultLatitude, defaultLongitude)
                    }
                } catch (e: Exception) {
                    // Ignore any exceptions during cleanup
                }
            }, 15000) // 15 seconds timeout instead of 10
        } catch (e: Exception) {
            e.printStackTrace()
            fetchWeatherData(defaultLatitude, defaultLongitude)
        }
    }

    private fun getLastKnownLocation(locationManager: android.location.LocationManager): android.location.Location? {
        if (!hasLocationPermissions()) return null

        try {
            val providers = locationManager.getProviders(true)
            var bestLocation: android.location.Location? = null

            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider) ?: continue

                if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                    bestLocation = location
                }
            }

            return bestLocation
        } catch (se: SecurityException) {
            return null
        }
    }

    private fun createLocationListener(locationManager: android.location.LocationManager): android.location.LocationListener {
        return object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                fetchWeatherData(location.latitude, location.longitude)
                try {
                    locationManager.removeUpdates(this)
                } catch (se: SecurityException) {
                    // Permission might have been revoked during execution
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                fetchWeatherData(28.6139, 77.2090)
            }
        }
    }

    private fun fetchWeatherData(latitude: Double, longitude: Double) {
        val client = OkHttpClient()
        val apiKey = "8a5e10dc90a04e9e92d100819252103"
        val url = "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=$latitude,$longitude"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle error
                activity?.runOnUiThread {
                    Toast.makeText(context, "Weather update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.temperatureTextView.text = "--°C"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onFailure(call, IOException("Unexpected response: ${response.code}"))
                    return
                }

                try {
                    val responseString = response.body?.string()
                    val jsonObject = JSONObject(responseString)

                    // Parse weather data
                    val current = jsonObject.getJSONObject("current")
                    val temperature = current.getDouble("temp_c").toInt()
                    val humidity = current.getInt("humidity")
                    val condition = current.getJSONObject("condition").getString("text")
                    val windSpeed = current.getDouble("wind_kph")
                    val location = jsonObject.getJSONObject("location").getString("name")

                    activity?.runOnUiThread {
                        binding.temperatureTextView.text = "${temperature}°C"
                        binding.weatherConditionTextView.text = condition
                        binding.humidityTextView.text = "$humidity%"
                        binding.windTextView.text = "${windSpeed.toInt()} km/h"
                        binding.locationTextView.text = location
                        updateWeatherIcon(condition.lowercase())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Failed to parse weather data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Helper function to update the weather icon based on condition
    private fun updateWeatherIcon(condition: String) {
        val iconResource = when {
            condition.contains("clear") -> R.drawable.sun_icon
            condition.contains("cloud") -> R.drawable.cloud_icon // Updated drawable names
            condition.contains("rain") -> R.drawable.rain_icon
            condition.contains("thunder") -> R.drawable.thunder_icon
            condition.contains("snow") -> R.drawable.snow_icon
            condition.contains("mist") || condition.contains("fog") -> R.drawable.cloud_icon
            else -> R.drawable.sun_icon
        }

        binding.weatherIconView.setImageResource(iconResource)
    }

    private fun setupUserProfile() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        currentUser?.let { user ->
            // Load user data from Firestore
            FirebaseFirestore.getInstance().collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Set welcome message with user's name
                        val name = document.getString("fullName")
                        binding.welcomeTextView.text = "Welcome, ${name ?: "User"}!"

                        // Load profile image
                        val photoUrl = document.getString("photoUrl")
                        if (!photoUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.agriculture)
                                .into(binding.userProfileImage)
                        }
                    }
                }
        }
    }

    private fun setupRecommendationsRecyclerView() {
        recommendationAdapter = RecommendationAdapter(recommendationList) { recommendation ->
            // Handle recommendation click
            Toast.makeText(requireContext(), "Selected: ${recommendation.title}", Toast.LENGTH_SHORT).show()
        }

        binding.recommendationsRecyclerView.apply {
            adapter = recommendationAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun loadRecommendations() {
        FirebaseFirestore.getInstance().collection("recommendations")
            .limit(5)
            .get()
            .addOnSuccessListener { documents ->
                recommendationList.clear()

                for (document in documents) {
                    val recommendation = Recommendation(
                        id = document.id,
                        title = document.getString("title") ?: "",
                        description = document.getString("description") ?: "",
                        imageUrl = document.getString("imageUrl") ?: "",
                        detailUrl = document.getString("detailUrl")
                    )
                    recommendationList.add(recommendation)
                }

                recommendationAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error loading recommendations: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDailyTip() {
        // Load a random tip from Firestore
        FirebaseFirestore.getInstance().collection("farming_tips")
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    binding.tipContentTextView.text = document.getString("content") ?:
                            "Rotating your crops helps prevent soil-borne diseases and pest problems. Follow a three-year rotation plan for best results."
                }
            }
    }
}