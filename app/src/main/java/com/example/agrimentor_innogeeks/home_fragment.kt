package com.example.agrimentor_innogeeks

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agrimentor_innogeeks.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.annotations.SerializedName
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okio.IOException
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Calendar
import java.util.concurrent.TimeUnit

class home_fragment : Fragment(R.layout.fragment_home) {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var recommendationAdapter: RecommendationAdapter
    private lateinit var cropAdapter: CropAdapter
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val recommendationList = mutableListOf<Recommendation>()
    private val cropList = mutableListOf<Crop>()
    private lateinit var cropApi: CropApi

    // Define the API interface for Retrofit
    interface CropApi {
        @GET("api/v1/crops")
        fun getCrops(@Query("filter") filter: String): retrofit2.Call<CropResponse>
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentHomeBinding.bind(view)
        setWeatherViewsVisibility(View.VISIBLE)
        checkLocationPermissions()
        setupRetrofit()
        setupUserProfile()
        setupRecommendationsRecyclerView()
        setupCropsRecyclerView()
        loadRecommendations()
        loadDailyTip()
        setupWeatherCardClick()
        fetchCropsForCurrentMonth()
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
            hasLocationPermissions() -> loadWeatherData()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                showPermissionRationaleDialog()
            else -> requestLocationPermissions()
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
            .setPositiveButton("OK") { _, _ -> requestLocationPermissions() }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(context, "Location permission denied. Using default location.",
                    Toast.LENGTH_SHORT).show()
                loadWeatherData()
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
                Toast.makeText(context, "Location permissions granted", Toast.LENGTH_SHORT).show()
                loadWeatherData()
            } else {
                val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                if (!shouldShowRationale) {
                    showOpenSettingsDialog()
                } else {
                    Toast.makeText(context, "Location permission denied. Using default location.",
                        Toast.LENGTH_SHORT).show()
                    loadWeatherData()
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
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", requireActivity().packageName, null)
                )
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(context, "Using default location for weather updates.",
                    Toast.LENGTH_SHORT).show()
                loadWeatherData()
            }
            .create()
            .show()
    }

    override fun onResume() {
        super.onResume()
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

            val lastLocation = getLastKnownLocation(locationManager)
            if (lastLocation != null && System.currentTimeMillis() - lastLocation.time < 10 * 60 * 1000) {
                fetchWeatherData(lastLocation.latitude, lastLocation.longitude)
                return
            }

            var locationUpdateRequested = false
            val locationListener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    fetchWeatherData(location.latitude, location.longitude)
                    try {
                        locationManager.removeUpdates(this)
                    } catch (se: SecurityException) {}
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                try {
                    locationManager.requestLocationUpdates(
                        android.location.LocationManager.NETWORK_PROVIDER,
                        0, 0f, locationListener
                    )
                    locationUpdateRequested = true
                } catch (se: SecurityException) {}
            }

            if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                try {
                    locationManager.requestLocationUpdates(
                        android.location.LocationManager.GPS_PROVIDER,
                        0, 0f, locationListener
                    )
                    locationUpdateRequested = true
                } catch (se: SecurityException) {}
            }

            if (!locationUpdateRequested) {
                fetchWeatherData(defaultLatitude, defaultLongitude)
                return
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    locationManager.removeUpdates(locationListener)
                    if (binding.temperatureTextView.text == "Loading...") {
                        fetchWeatherData(defaultLatitude, defaultLongitude)
                    }
                } catch (e: Exception) {}
            }, 15000)
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

    private fun fetchWeatherData(latitude: Double, longitude: Double) {
        val client = OkHttpClient()
        val apiKey = "8a5e10dc90a04e9e92d100819252103"
        val url = "https://api.weatherapi.com/v1/current.json?key=$apiKey&q=$latitude,$longitude"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
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

    private fun updateWeatherIcon(condition: String) {
        val iconResource = when {
            condition.contains("clear") -> R.drawable.sun_icon
            condition.contains("cloud") -> R.drawable.cloud_icon
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
            FirebaseFirestore.getInstance().collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("fullName")
                        binding.welcomeTextView.text = "Welcome, ${name ?: "User"}!"

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
            Toast.makeText(requireContext(), "Selected: ${recommendation.title}", Toast.LENGTH_SHORT).show()
        }

        binding.recommendationsRecyclerView.apply {
            adapter = recommendationAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupCropsRecyclerView() {
        cropAdapter = CropAdapter(cropList) { crop ->
            Toast.makeText(requireContext(), "Selected crop: ${crop.name}", Toast.LENGTH_SHORT).show()
        }

        binding.cropsRecyclerView.apply {
            adapter = cropAdapter
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
                Toast.makeText(requireContext(), "Error loading recommendations: ${exception.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDailyTip() {
        FirebaseFirestore.getInstance().collection("farming_tips")
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    binding.tipContentTextView.text = document.getString("content") ?:
                            "Rotating your crops helps prevent soil-borne diseases and pest problems."
                }
            }
    }

    private fun setupRetrofit() {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            this.level = HttpLoggingInterceptor.Level.BODY
        }

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://openfarm.cc/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()

        cropApi = retrofit.create(CropApi::class.java)
    }

    // Update fetchCropsForCurrentMonth to properly map API response
    private fun fetchCropsForCurrentMonth() {
        // Show loading state
        binding.cropsRecyclerView.visibility = View.VISIBLE

        // Get current month for more relevant API query
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val currentMonthName = monthNames[currentMonth]

        // Update subtitle with current month
        binding.cropCalendarSubtitle?.text = "Best crops to plant in $currentMonthName"

        // Clear existing crops
        cropList.clear()

        Log.d("CropAPI", "Fetching crops for $currentMonthName")

        // Add month to query for relevant results
        val filterQuery = "popular+$currentMonthName"

        cropApi.getCrops(filterQuery).enqueue(object : retrofit2.Callback<CropResponse> {
            override fun onResponse(call: retrofit2.Call<CropResponse>, response: retrofit2.Response<CropResponse>) {
                Log.d("CropAPI", "Response received: success=${response.isSuccessful}, code=${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val cropDataList = response.body()?.data
                    Log.d("CropAPI", "Crops received: ${cropDataList?.size ?: 0}")

                    // Log first crop details for debugging
                    if (!cropDataList.isNullOrEmpty() && cropDataList.size > 0) {
                        val firstCrop = cropDataList[0]
                        Log.d("CropAPI", "First crop: id=${firstCrop.id}, type=${firstCrop.type}")
                        Log.d("CropAPI", "Attributes: name=${firstCrop.attributes.name}, " +
                                "image=${firstCrop.attributes.mainImagePath?.take(30)}...")
                    }

                    activity?.runOnUiThread {
                        if (isAdded) {
                            cropList.clear()

                            if (!cropDataList.isNullOrEmpty()) {
                                // Map API response to our simplified Crop model
                                cropDataList.forEach { cropData ->
                                    val crop = Crop(
                                        id = cropData.id,
                                        name = cropData.attributes.name ?: "Unknown Crop",
                                        binomial_name = cropData.attributes.binomialName,
                                        description = cropData.attributes.description,
                                        sun_requirements = cropData.attributes.sunRequirements,
                                        sowing_method = cropData.attributes.sowingMethod,
                                        main_image_path = cropData.attributes.mainImagePath
                                    )
                                    cropList.add(crop)
                                }

                                Log.d("CropAPI", "Mapped ${cropList.size} crops to adapter model")
                                cropAdapter.notifyDataSetChanged()
                            } else {
                                showNoDataMessage(currentMonthName)
                            }
                        }
                    }
                } else {
                    Log.e("CropAPI", "API error: ${response.code()} - ${response.message()}")
                    val errorBody = response.errorBody()?.string()
                    Log.e("CropAPI", "Error body: $errorBody")
                    showNoDataMessage(currentMonthName)
                }
            }

            override fun onFailure(call: retrofit2.Call<CropResponse>, t: Throwable) {
                Log.e("CropAPI", "API call failed: ${t.message}", t)
                showNoDataMessage(currentMonthName)
            }
        })
    }

    private fun showNoDataMessage(month: String) {
        activity?.runOnUiThread {
            if (isAdded) {
                // Clear existing data
                cropList.clear()

                // Add a placeholder to display message
                cropList.add(
                    Crop(
                        id = "no_data",
                        name = "No crops found",
                        binomial_name = null,
                        description = "We couldn't find any crops for $month. Try again later.",
                        sun_requirements = null,
                        sowing_method = null,
                        main_image_path = null
                    )
                )

                cropAdapter.notifyDataSetChanged()

                // Show toast message
                Toast.makeText(
                    requireContext(),
                    "Unable to fetch crops for $month",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Data classes for OpenFarm API
    data class Crop(
        val id: String,
        val name: String,
        val binomial_name: String?,
        val description: String?,
        val sun_requirements: String?,
        val sowing_method: String?,
        val main_image_path: String?
    )

    data class CropResponse(
        @SerializedName("data")
        val data: List<CropData> = emptyList()
    )

    data class CropData(
        @SerializedName("id")
        val id: String = "",
        @SerializedName("type")
        val type: String = "",
        @SerializedName("attributes")
        val attributes: CropAttributes = CropAttributes()
    )

    data class CropAttributes(
        @SerializedName("name")
        val name: String? = null,
        @SerializedName("binomial_name")
        val binomialName: String? = null,
        @SerializedName("description")
        val description: String? = null,
        @SerializedName("sun_requirements")
        val sunRequirements: String? = null,
        @SerializedName("sowing_method")
        val sowingMethod: String? = null,
        @SerializedName("spread")
        val spread: Int? = null,
        @SerializedName("row_spacing")
        val rowSpacing: Int? = null,
        @SerializedName("height")
        val height: Int? = null,
        @SerializedName("growing_degree_days")
        val growingDegreeDays: Int? = null,
        @SerializedName("main_image_path")
        val mainImagePath: String? = null
    )



    // Improved adapter for displaying crops
    inner class CropAdapter(
        private val crops: List<Crop>,
        private val onItemClick: (Crop) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<CropAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val cropNameTextView: android.widget.TextView = itemView.findViewById(R.id.cropName)
            val cropImageView: android.widget.ImageView = itemView.findViewById(R.id.cropImage)
            val cropDescriptionTextView: android.widget.TextView = itemView.findViewById(R.id.cropDescription)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_crop, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val crop = crops[position]

            // Set crop name with fallback
            holder.cropNameTextView.text = crop.name.ifEmpty { "Unknown Crop" }

            // Set description with fallback - prioritize most informative content
            val description = when {
                !crop.description.isNullOrBlank() -> crop.description
                !crop.binomial_name.isNullOrBlank() -> "Scientific name: ${crop.binomial_name}"
                !crop.sun_requirements.isNullOrBlank() -> "Needs: ${crop.sun_requirements}"
                !crop.sowing_method.isNullOrBlank() -> "Sowing: ${crop.sowing_method}"
                else -> "No description available"
            }
            holder.cropDescriptionTextView.text = description

            // Log image loading attempt
            Log.d("CropAdapter", "Loading image for ${crop.name}, URL: ${crop.main_image_path?.take(30)}")

            // Load image with fallback
            if (!crop.main_image_path.isNullOrBlank()) {
                Glide.with(holder.itemView.context)
                    .load(crop.main_image_path)
                    .placeholder(R.drawable.agriculture)
                    .error(R.drawable.agriculture)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.cropImageView)
            } else {
                holder.cropImageView.setImageResource(R.drawable.agriculture)
            }

            // Set click listener only for real crops
            if (crop.id != "no_data") {
                holder.itemView.setOnClickListener { onItemClick(crop) }
            }
        }

        override fun getItemCount(): Int = crops.size
    }
}