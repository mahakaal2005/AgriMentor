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
    private val recommendationList = mutableListOf<Recommendation>()
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentHomeBinding.bind(view)

        // Request permissions
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
        // Initialize views and make them visible
        binding.temperatureTextView.visibility = View.VISIBLE
        binding.weatherConditionTextView.visibility = View.VISIBLE
        binding.humidityTextView.visibility = View.VISIBLE
        binding.windTextView.visibility = View.VISIBLE
        binding.locationTextView.visibility = View.VISIBLE
        binding.weatherIconView.visibility = View.VISIBLE

        setupUserProfile()
        setupRecommendationsRecyclerView()
        loadRecommendations()
        loadDailyTip()
        loadWeatherData()

        // Initialize views and make them visible

        loadDailyTip()
        loadWeatherData()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, load weather data
                // Initialize views and make them visible
                binding.temperatureTextView.visibility = View.VISIBLE
                binding.weatherConditionTextView.visibility = View.VISIBLE
                binding.humidityTextView.visibility = View.VISIBLE
                binding.windTextView.visibility = View.VISIBLE
                binding.locationTextView.visibility = View.VISIBLE
                binding.weatherIconView.visibility = View.VISIBLE

                setupUserProfile()
                setupRecommendationsRecyclerView()
                loadRecommendations()
                loadDailyTip()
                loadWeatherData()

            } else {
                // Permission denied, show a message
                Toast.makeText(
                    context,
                    "Location permission denied. Using default location.",
                    Toast.LENGTH_SHORT
                ).show()
                loadWeatherData() // Load with default coordinates
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    override fun onResume() {
        super.onResume()
        // Reload weather data when fragment resumes
        loadWeatherData()
    }

    private fun loadWeatherData() {
        // Set loading state
        binding.temperatureTextView.text = "Loading..."
        binding.weatherConditionTextView.text = ""
        binding.humidityTextView.text = "--"
        binding.windTextView.text = "--"
        binding.locationTextView.text = "Fetching location..."

        // Use OkHttp for network requests
        val client = OkHttpClient()

        // API key from OpenWeatherMap
        // You need to replace this with a valid API key
        val apiKey = "YOUR_VALID_API_KEY_HERE" // Get a new key from openweathermap.org

        // Default coordinates (can be replaced with the user's actual location)
        val latitude = 28.6139  // Example: New Delhi
        val longitude = 77.2090

        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&units=metric&appid=$apiKey"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle network failures
                e.printStackTrace()
                println("Weather API call failed: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Weather update failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Set default values
                    binding.temperatureTextView.text = "--°C"
                    binding.weatherConditionTextView.text = "Unknown"
                    binding.humidityTextView.text = "--%"
                    binding.windTextView.text = "-- km/h"
                    binding.locationTextView.text = "Location unavailable"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    // Log detailed error information
                    activity?.runOnUiThread {
                        val errorMessage = "API Error: ${response.code} - ${response.message}"
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        println("Weather API Error: $errorMessage")
                    }
                    onFailure(call, IOException("Unexpected response: ${response.code}"))
                    return
                }

                try {
                    val responseString = response.body?.string()
                    if (responseString != null) {
                        val jsonObject = JSONObject(responseString)

                        // Extract weather information
                        val main = jsonObject.getJSONObject("main")
                        val temperature = main.getDouble("temp").toInt()
                        val humidity = main.getInt("humidity")

                        val weather = jsonObject.getJSONArray("weather").getJSONObject(0)
                        val condition = weather.getString("main")

                        val wind = jsonObject.getJSONObject("wind")
                        val windSpeed = wind.getDouble("speed")

                        val location = jsonObject.getString("name")

                        activity?.runOnUiThread {
                            binding.temperatureTextView.text = "${temperature}°C"
                            binding.weatherConditionTextView.text = condition
                            binding.humidityTextView.text = "$humidity%"
                            binding.windTextView.text = "${windSpeed.toInt()} km/h"
                            binding.locationTextView.text = location

                            // Update weather icon based on condition
                            updateWeatherIcon(condition.lowercase())
                        }
                    } else {
                        onFailure(call, IOException("Empty response body"))
                    }
                } catch (e: Exception) {
                    // Handle JSON parsing errors
                    e.printStackTrace()
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Failed to parse weather data", Toast.LENGTH_SHORT)
                            .show()
                        binding.temperatureTextView.text = "--°C"
                    }
                }
            }

            private fun isApiKeyValid(apiKey: String): Boolean {
                // Simple validation check - API keys for OpenWeatherMap are typically 32 characters
                return apiKey.length >= 32 && !apiKey.equals("YOUR_VALID_API_KEY_HERE")
            }
        })
    }

    // Helper function to update the weather icon based on condition
    private fun updateWeatherIcon(condition: String) {
        val iconResource = when {
            condition.contains("clear") -> R.drawable.weather_sunny
            condition.contains("cloud") -> R.drawable.weather_cloudy // Updated drawable names
            condition.contains("rain") -> R.drawable.weather_rainy
            condition.contains("thunder") -> R.drawable.weather_thunder
            condition.contains("snow") -> R.drawable.weather_snowy
            condition.contains("mist") || condition.contains("fog") -> R.drawable.weather_foggy
            else -> R.drawable.weather_sunny
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