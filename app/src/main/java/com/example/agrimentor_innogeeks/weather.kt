package com.example.agrimentor_innogeeks

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agrimentor_innogeeks.databinding.FragmentWeatherBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class weather : Fragment(R.layout.fragment_weather) {

    private lateinit var binding: FragmentWeatherBinding
    private var latitude: Double = 28.6139  // Default coordinates (New Delhi)
    private var longitude: Double = 77.2090

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentWeatherBinding.bind(view)

        // Get user location
        getUserLocation()

        // Initial fetch with default coordinates
        fetchWeatherData()
    }

    // Check if we have location permission
    private fun hasLocationPermission(): Boolean {
        return android.content.pm.PackageManager.PERMISSION_GRANTED == androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) || android.content.pm.PackageManager.PERMISSION_GRANTED == androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    // Request location permission
    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, get location
                getUserLocation()
            } else {
                Toast.makeText(
                    context,
                    "Location permission denied. Using default location.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private fun getUserLocation() {
        try {
            if (hasLocationPermission()) {
                val locationManager =
                    requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

                // Check if location services are enabled
                val isGPSEnabled =
                    locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                val isNetworkEnabled =
                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

                if (!isGPSEnabled && !isNetworkEnabled) {
                    // No location provider enabled
                    Toast.makeText(context, "Please enable location services", Toast.LENGTH_LONG).show()
                    return
                }

                // Request location updates if location is not available immediately
                val locationListener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        latitude = location.latitude
                        longitude = location.longitude
                        // Remove updates after getting location
                        locationManager.removeUpdates(this)
                        // Fetch weather with new coordinates
                        fetchWeatherData()
                    }

                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle) {}
                }

                // Try to get last known location first
                var location: android.location.Location? = null

                if (isGPSEnabled) {
                    location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                }

                if (location == null && isNetworkEnabled) {
                    location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                }

                if (location != null) {
                    // Use the last known location
                    latitude = location.latitude
                    longitude = location.longitude
                    fetchWeatherData()
                } else {
                    // Request location updates
                    if (isGPSEnabled) {
                        locationManager.requestLocationUpdates(
                            android.location.LocationManager.GPS_PROVIDER,
                            0L, 0f, locationListener
                        )
                    }

                    if (isNetworkEnabled) {
                        locationManager.requestLocationUpdates(
                            android.location.LocationManager.NETWORK_PROVIDER,
                            0L, 0f, locationListener
                        )
                    }
                }
            } else {
                requestLocationPermission()
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission is required", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error getting location: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchWeatherData() {
        val client = OkHttpClient()
        val apiKey = "8a5e10dc90a04e9e92d100819252103"
        // Add aqi=yes parameter to get air quality data
        val url =
            "https://api.weatherapi.com/v1/forecast.json?key=$apiKey&q=$latitude,$longitude&days=7&aqi=yes&alerts=yes"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Failed to fetch weather data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "API Error: ${response.code}", Toast.LENGTH_SHORT)
                            .show()
                    }
                    return
                }

                try {
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody)

                    activity?.runOnUiThread {
                        try {
                            val currentWeatherData = jsonObject.getJSONObject("current")
                            val forecastData = jsonObject.getJSONObject("forecast")
                            val locationData = jsonObject.getJSONObject("location")

                            updateUIWithWeatherData(currentWeatherData, forecastData, locationData)

                            // Check for weather alerts
                            if (jsonObject.has("alerts") && !jsonObject.isNull("alerts")) {
                                val alertsObject = jsonObject.getJSONObject("alerts")
                                if (alertsObject.has("alert") && alertsObject.getJSONArray("alert")
                                        .length() > 0
                                ) {
                                    displayWeatherAlerts(alertsObject.getJSONArray("alert"))
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Error parsing weather data: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    activity?.runOnUiThread {
                        Toast.makeText(
                            context,
                            "Failed to parse weather data: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun updateUIWithWeatherData(
        currentWeatherData: JSONObject,
        forecastData: JSONObject,
        locationData: JSONObject
    ) {
        try {
            // Location info
            val locationName = locationData.getString("name")
            val region = locationData.getString("region")
            binding.locationTextView.text = locationName

            // Current date
            val localTime = locationData.getString("localtime")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = dateFormat.parse(localTime)
            val dayFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            binding.currentDateTextView.text = date?.let { dayFormat.format(it) } ?: "Unknown date"

            // Current temperature and condition
            val tempC = currentWeatherData.getDouble("temp_c").roundToInt()
            val feelsLikeC = currentWeatherData.getDouble("feelslike_c").roundToInt()
            val condition = currentWeatherData.getJSONObject("condition").getString("text")
            val conditionIcon = currentWeatherData.getJSONObject("condition").getString("icon")

            binding.currentTempTextView.text = "$tempC°"
            binding.feelsLikeTextView.text = "Feels like ${feelsLikeC}°"
            binding.weatherDescriptionTextView.text = condition

            // Load weather icon
            val iconUrl = "https:$conditionIcon"
            loadWeatherIcon(iconUrl, binding.currentWeatherIcon)

            // High and low temperatures from forecast
            val todayForecast = forecastData.getJSONArray("forecastday").getJSONObject(0)
            val dayForecast = todayForecast.getJSONObject("day")
            val maxTempC = dayForecast.getDouble("maxtemp_c").roundToInt()
            val minTempC = dayForecast.getDouble("mintemp_c").roundToInt()

            binding.highTempChip.text = "H: $maxTempC°"
            binding.lowTempChip.text = "L: $minTempC°"

            // Weather details
            val humidity = currentWeatherData.getInt("humidity")
            val windKph = currentWeatherData.getDouble("wind_kph").roundToInt()
            val windDir = currentWeatherData.getString("wind_dir")
            val uvIndex = currentWeatherData.getDouble("uv")
            val pressureMb = currentWeatherData.getDouble("pressure_mb").roundToInt()

            binding.humidityValueTextView.text = "$humidity%"
            binding.windValueTextView.text = "$windKph km/h $windDir"

            val uvDescription = when {
                uvIndex < 3 -> "Low"
                uvIndex < 6 -> "Moderate"
                uvIndex < 8 -> "High"
                uvIndex < 11 -> "Very High"
                else -> "Extreme"
            }
            binding.uvIndexValueTextView.text = "${uvIndex.roundToInt()} ($uvDescription)"
            binding.pressureValueTextView.text = "$pressureMb hPa"

            // Astronomy data
            val astronomy = todayForecast.getJSONObject("astro")
            val sunrise = astronomy.getString("sunrise")
            val sunset = astronomy.getString("sunset")

            binding.sunriseTimeTextView.text = sunrise
            binding.sunsetTimeTextView.text = sunset
            binding.sunriseIndicatorTextView.text = sunrise
            binding.sunsetIndicatorTextView.text = sunset

            // Current time for day progress
            val currentTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val currentTime = currentTimeFormat.format(Date())
            binding.currentTimeTextView.text = "Current: $currentTime"

            // Day length calculation
            val dayLength = calculateDayLength(sunrise, sunset)
            binding.dayLengthTextView.text = "Day length: $dayLength"

            // Update sun position indicator
            updateSunPositionIndicator(sunrise, sunset)

            // Moon phase
            val moonPhase = astronomy.getString("moon_phase")
            binding.moonPhaseTextView.text = moonPhase
            updateMoonPhaseImage(moonPhase)

            // Moonrise and moonset
            val moonrise = astronomy.getString("moonrise")
            val moonset = astronomy.getString("moonset")
            binding.moonriseTimeTextView.text = moonrise
            binding.moonsetTimeTextView.text = moonset

            // Update AQI information
            updateAirQualityInfo(currentWeatherData)

            // Environmental conditions
            updateEnvironmentalConditions(currentWeatherData, condition, windKph)

            // Hourly forecast
            val hourlyForecast = todayForecast.getJSONArray("hour")
            setupHourlyForecast(hourlyForecast)

            // 7-day forecast
            val forecastDays = forecastData.getJSONArray("forecastday")
            setupDailyForecast(forecastDays)

        } catch (e: Exception) {
            Toast.makeText(context, "Error updating UI: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun updateAirQualityInfo(currentWeatherData: JSONObject) {
        try {
            var aqiValue = 0
            var aqiDescription = "Unknown"
            var colorCode = Color.GRAY

            // Try to get AQI data from the API response
            if (currentWeatherData.has("air_quality") && !currentWeatherData.isNull("air_quality")) {
                val airQuality = currentWeatherData.getJSONObject("air_quality")

                // First try to get US EPA AQI index
                if (airQuality.has("us-epa-index") && !airQuality.isNull("us-epa-index")) {
                    val epaIndex = airQuality.getInt("us-epa-index")

                    // EPA index is 1-6 scale, convert to AQI range for display
                    aqiValue = when (epaIndex) {
                        1 -> 25  // Good
                        2 -> 75  // Moderate
                        3 -> 125 // Unhealthy for Sensitive Groups
                        4 -> 175 // Unhealthy
                        5 -> 250 // Very Unhealthy
                        6 -> 350 // Hazardous
                        else -> 0
                    }
                }
                // If no EPA index, try to calculate from PM2.5
                else if (airQuality.has("pm2_5") && !airQuality.isNull("pm2_5")) {
                    val pm25 = airQuality.getDouble("pm2_5").toFloat()
                    aqiValue = calculateAQIFromPM25(pm25)
                }

                // Get AQI description and color
                val (description, color) = getAQIDescriptionAndColor(aqiValue)
                aqiDescription = description
                colorCode = color

                // Update UI
                binding.aqiValueTextView.text = "$aqiValue - $aqiDescription"
                binding.aqiValueTextView.setTextColor(colorCode)

                // Update progress bar
                val progressValue = getAQIProgressValue(aqiValue)
                binding.aqiProgressIndicator.progress = progressValue
                binding.aqiProgressIndicator.setIndicatorColor(colorCode)
            } else {
                // If no AQI data, estimate based on weather conditions
                val condition =
                    currentWeatherData.getJSONObject("condition").getString("text").lowercase()
                val visibility = currentWeatherData.getDouble("vis_km")

                aqiValue = when {
                    condition.contains("fog") || condition.contains("mist") || visibility < 2 -> 125
                    condition.contains("haze") || visibility < 5 -> 75
                    condition.contains("clear") && visibility > 10 -> 25
                    else -> 50
                }

                val (description, color) = getAQIDescriptionAndColor(aqiValue)
                aqiDescription = description
                colorCode = color

                binding.aqiValueTextView.text = "$aqiValue - $aqiDescription (Estimated)"
                binding.aqiValueTextView.setTextColor(colorCode)

                val progressValue = getAQIProgressValue(aqiValue)
                binding.aqiProgressIndicator.progress = progressValue
                binding.aqiProgressIndicator.setIndicatorColor(colorCode)
            }
        } catch (e: Exception) {
            // Fallback to estimated value if something goes wrong
            val estimatedAQI = 50
            val (description, color) = getAQIDescriptionAndColor(estimatedAQI)

            binding.aqiValueTextView.text = "$estimatedAQI - $description (Estimated)"
            binding.aqiValueTextView.setTextColor(color)

            val progressValue = getAQIProgressValue(estimatedAQI)
            binding.aqiProgressIndicator.progress = progressValue
            binding.aqiProgressIndicator.setIndicatorColor(color)

            e.printStackTrace()
        }
    }

    // Calculate AQI from PM2.5 values using EPA standard (simplified)
    private fun calculateAQIFromPM25(pm25: Float): Int {
        return when {
            pm25 <= 12.0 -> ((50 - 0) / (12.0 - 0.0) * (pm25 - 0.0) + 0).toInt()
            pm25 <= 35.4 -> ((100 - 51) / (35.4 - 12.1) * (pm25 - 12.1) + 51).toInt()
            pm25 <= 55.4 -> ((150 - 101) / (55.4 - 35.5) * (pm25 - 35.5) + 101).toInt()
            pm25 <= 150.4 -> ((200 - 151) / (150.4 - 55.5) * (pm25 - 55.5) + 151).toInt()
            pm25 <= 250.4 -> ((300 - 201) / (250.4 - 150.5) * (pm25 - 150.5) + 201).toInt()
            pm25 <= 500.4 -> ((500 - 301) / (500.4 - 250.5) * (pm25 - 250.5) + 301).toInt()
            else -> 500
        }
    }

    // Get AQI description and color based on value
    private fun getAQIDescriptionAndColor(aqiValue: Int): Pair<String, Int> {
        return when {
            aqiValue <= 50 -> Pair("Good", Color.parseColor("#4CAF50"))
            aqiValue <= 100 -> Pair("Moderate", Color.parseColor("#FFEB3B"))
            aqiValue <= 150 -> Pair("Unhealthy for Sensitive Groups", Color.parseColor("#FF9800"))
            aqiValue <= 200 -> Pair("Unhealthy", Color.parseColor("#F44336"))
            aqiValue <= 300 -> Pair("Very Unhealthy", Color.parseColor("#9C27B0"))
            else -> Pair("Hazardous", Color.parseColor("#7B1FA2"))
        }
    }

    // Scale the AQI value to a progress value for the progress indicator (0-100)
    private fun getAQIProgressValue(aqiValue: Int): Int {
        // Most progress bars need a value from 0-100
        return when {
            aqiValue <= 50 -> aqiValue // 0-50 -> 0-50
            aqiValue <= 100 -> 50 + (aqiValue - 50) / 2 // 51-100 -> 51-75
            aqiValue <= 150 -> 75 + (aqiValue - 100) / 5 // 101-150 -> 76-85
            aqiValue <= 200 -> 85 + (aqiValue - 150) / 10 // 151-200 -> 86-90
            aqiValue <= 300 -> 90 + (aqiValue - 200) / 20 // 201-300 -> 91-95
            else -> 95 + (aqiValue - 300) / 40 // 301-500 -> 96-100
        }.coerceAtMost(100)
    }

    private fun updateEnvironmentalConditions(
        currentWeatherData: JSONObject,
        condition: String,
        windSpeed: Int
    ) {
        // Pollen levels - simplified estimation based on weather conditions
        val pollenLevel = when {
            condition.contains("rain") -> "Low"
            condition.contains("cloud") -> "Moderate"
            condition.contains("clear") || condition.contains("sunny") -> "High"
            else -> "Moderate"
        }

        binding.pollenLevelsTextView.text = pollenLevel
        binding.pollenLevelsTextView.setTextColor(
            when (pollenLevel) {
                "Low" -> Color.parseColor("#4CAF50")
                "Moderate" -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
        )

        // Running conditions
        val humidity = currentWeatherData.getInt("humidity")
        val tempC = currentWeatherData.getDouble("temp_c").toInt()

        val runningCondition = when {
            tempC > 30 || humidity > 85 -> "Poor"
            tempC > 25 || humidity > 70 || condition.contains("rain") -> "Fair"
            else -> "Good"
        }

        binding.runningConditionsTextView.text = runningCondition
        binding.runningConditionsTextView.setTextColor(
            when (runningCondition) {
                "Good" -> Color.parseColor("#4CAF50")
                "Fair" -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
        )

        // Driving difficulty
        val (drivingDifficulty, difficultyColor) = estimateDrivingDifficulty(condition, windSpeed)
        binding.drivingDifficultyTextView.text = drivingDifficulty
        binding.drivingDifficultyTextView.setTextColor(difficultyColor)

        // Agriculture advice
        binding.agricultureAdviceTextView.text = generateAgricultureAdvice(
            condition, tempC, humidity, pollenLevel
        )
    }

    private fun estimateDrivingDifficulty(condition: String, windSpeed: Int): Pair<String, Int> {
        return when {
            condition.contains("fog") || condition.contains("mist") ->
                Pair("Difficult", Color.parseColor("#F44336"))

            condition.contains("snow") || condition.contains("sleet") || condition.contains("ice") ->
                Pair("Hazardous", Color.parseColor("#7B1FA2"))

            condition.contains("rain") && condition.contains("heavy") ->
                Pair("Moderate", Color.parseColor("#FF9800"))

            condition.contains("rain") || windSpeed > 50 ->
                Pair("Slightly Difficult", Color.parseColor("#FFEB3B"))

            windSpeed > 30 ->
                Pair("Caution", Color.parseColor("#FFEB3B"))

            else ->
                Pair("Good", Color.parseColor("#4CAF50"))
        }
    }

    private fun generateAgricultureAdvice(
        condition: String,
        temperature: Int,
        humidity: Int,
        pollenLevel: String
    ): String {
        return when {
            temperature > 35 ->
                "Extreme heat conditions. Increase irrigation frequency and consider shade for sensitive crops. Monitor for heat stress symptoms."

            temperature > 30 && humidity < 40 ->
                "Hot and dry conditions. Ensure adequate irrigation and moisture retention. Consider mulching to preserve soil moisture."

            condition.contains("rain") && condition.contains("heavy") ->
                "Heavy rainfall may lead to soil erosion and waterlogging. Ensure proper drainage and postpone fertilizer application."

            condition.contains("rain") ->
                "Rainfall conditions are good for natural irrigation. Consider delaying artificial irrigation and monitor soil moisture."

            pollenLevel == "High" && !condition.contains("rain") ->
                "High pollen count and dry conditions. Good time for pollination but monitor disease development in susceptible crops."

            temperature < 5 ->
                "Near freezing temperatures. Protect sensitive crops with covers and avoid irrigation close to evening hours."

            else ->
                "Current conditions are generally favorable for most agricultural activities. Follow standard care and maintenance practices."
        }
    }

    private fun setupHourlyForecast(hourlyForecastData: JSONArray) {
        val hourlyForecastList = mutableListOf<HourlyForecast>()
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val currentTime = Calendar.getInstance().timeInMillis

        try {
            // Get time formatter
            val timeParser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val displayFormat = SimpleDateFormat("h a", Locale.getDefault())

            // Collect forecasts for next 12 hours
            for (i in 0 until hourlyForecastData.length()) {
                val hourData = hourlyForecastData.getJSONObject(i)
                val timeString = hourData.getString("time")
                val hourTime = timeParser.parse(timeString)

                // Only include hours that are current or future
                if (hourTime != null && hourTime.time >= currentTime) {
                    val temp = hourData.getDouble("temp_c").roundToInt()
                    val condition = hourData.getJSONObject("condition").getString("text")
                    val iconUrl = hourData.getJSONObject("condition").getString("icon")
                    val displayTime = displayFormat.format(hourTime)

                    hourlyForecastList.add(
                        HourlyForecast(
                            time = displayTime,
                            temperature = temp,
                            condition = condition,
                            iconUrl = "https:$iconUrl"
                        )
                    )

                    // Stop once we have 12 hours
                    if (hourlyForecastList.size >= 12) break
                }
            }

            // Set up adapter
            val adapter = HourlyForecastAdapter(requireContext(), hourlyForecastList)
            binding.hourlyForecastRecyclerView.adapter = adapter
            binding.hourlyForecastRecyclerView.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun setupDailyForecast(forecastDaysData: JSONArray) {
        val dailyForecastList = mutableListOf<DailyForecast>()

        for (i in 0 until forecastDaysData.length()) {
            val dayData = forecastDaysData.getJSONObject(i)
            val date = dayData.getString("date")
            val dayObj = dayData.getJSONObject("day")

            val maxTemp = dayObj.getDouble("maxtemp_c").roundToInt()
            val minTemp = dayObj.getDouble("mintemp_c").roundToInt()
            val condition = dayObj.getJSONObject("condition").getString("text")
            val iconUrl = dayObj.getJSONObject("condition").getString("icon")
            val rainChance = dayObj.getDouble("daily_chance_of_rain").roundToInt()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateObj = dateFormat.parse(date)
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            val dayOfWeek = dateObj?.let { dayFormat.format(it) } ?: "Unknown"
            dailyForecastList.add(
                DailyForecast(
                    day = if (i == 0) "Today" else dayOfWeek,
                    maxTemp = maxTemp,
                    minTemp = minTemp,
                    condition = condition,
                    iconUrl = "https:$iconUrl",
                    rainChance = rainChance
                )
            )
        }

        val adapter = DailyForecastAdapter(requireContext(), dailyForecastList)
        binding.dailyForecastRecyclerView.adapter = adapter
        binding.dailyForecastRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun displayWeatherAlerts(alertsArray: JSONArray) {
        if (alertsArray.length() > 0) {
            val alertMessage = StringBuilder()

            for (i in 0 until minOf(alertsArray.length(), 3)) { // Display up to 3 alerts
                val alert = alertsArray.getJSONObject(i)
                val headline = alert.getString("headline")
                alertMessage.append("• $headline\n")
            }

            if (alertsArray.length() > 3) {
                alertMessage.append("• Plus ${alertsArray.length() - 3} more alerts")
            }

            binding.weatherAlertsCard.visibility = View.VISIBLE
            binding.weatherAlertsTextView.text = alertMessage.toString().trim()
        } else {
            binding.weatherAlertsCard.visibility = View.GONE
        }
    }

    private fun calculateDayLength(sunrise: String, sunset: String): String {
        try {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val sunriseTime = timeFormat.parse(sunrise) ?: return "Unknown"
            val sunsetTime = timeFormat.parse(sunset) ?: return "Unknown"

            var diff = sunsetTime.time - sunriseTime.time

            val hours = diff / (1000 * 60 * 60)
            val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)

            return "${hours}h ${minutes}m"
        } catch (e: Exception) {
            e.printStackTrace()
            return "Unknown"
        }
    }

    private fun updateSunPositionIndicator(sunrise: String, sunset: String) {
        try {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            timeFormat.timeZone = TimeZone.getDefault()

            val sunriseTime = timeFormat.parse(sunrise) ?: return
            val sunsetTime = timeFormat.parse(sunset) ?: return
            val currentTime = Date()

            // Calculate sun's journey as a percentage of the day
            val totalDayLength = sunsetTime.time - sunriseTime.time
            val elapsed = currentTime.time - sunriseTime.time

            // Ensure we're between sunrise and sunset
            val progress = when {
                currentTime.before(sunriseTime) -> 0.0
                currentTime.after(sunsetTime) -> 1.0
                else -> (elapsed.toDouble() / totalDayLength).coerceIn(0.0, 1.0)
            }

            // Update day progress bar
            binding.dayProgressBar.progress = (progress * 100).toInt()

            // Position the sun indicator
            val layoutParams =
                binding.sunPositionIndicator.layoutParams as FrameLayout.LayoutParams
            layoutParams.gravity = Gravity.START or Gravity.TOP

            // Calculate arc path (simplified)
            val width = binding.sunPositionIndicator.width
            val containerWidth = (binding.sunPositionIndicator.parent as ViewGroup).width
            if (containerWidth > 0) {
                val horizontalPosition = (progress * containerWidth.toDouble()).toInt()
                layoutParams.marginStart = horizontalPosition

                // Calculate vertical position (parabolic path)
                val maxHeight = 50 // Maximum height of arc in dp
                val verticalPosition =
                    (-(4 * maxHeight) * (progress - 0.5) * (progress - 0.5) + maxHeight).toInt()
                layoutParams.topMargin = dpToPx(verticalPosition)

                binding.sunPositionIndicator.layoutParams = layoutParams
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateMoonPhaseImage(moonPhase: String) {
        val moonPhaseResourceId = when {
            moonPhase.contains("New Moon") -> R.drawable.moon_icon
            moonPhase.contains("Waxing Crescent") -> R.drawable.moon_icon
            moonPhase.contains("First Quarter") -> R.drawable.moon_icon
            moonPhase.contains("Waxing Gibbous") -> R.drawable.moon_icon
            moonPhase.contains("Full Moon") -> R.drawable.moon_icon
            moonPhase.contains("Waning Gibbous") -> R.drawable.moon_icon
            moonPhase.contains("Last Quarter") -> R.drawable.moon_icon
            moonPhase.contains("Waning Crescent") -> R.drawable.moon_icon
            else -> R.drawable.moon_icon
        }

        binding.moonPhaseImage.setImageResource(moonPhaseResourceId)
    }

    private fun loadWeatherIcon(iconUrl: String, imageView: android.widget.ImageView) {
        try {
            com.bumptech.glide.Glide.with(this)
                .load(iconUrl)
                .placeholder(R.drawable.sun_icon)
                .into(imageView)
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.sun_icon)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    // Data classes for adapters
    data class HourlyForecast(
        val time: String,
        val temperature: Int,
        val condition: String,
        val iconUrl: String
    )

    data class DailyForecast(
        val day: String,
        val maxTemp: Int,
        val minTemp: Int,
        val condition: String,
        val iconUrl: String,
        val rainChance: Int
    )

    // Adapters
    inner class HourlyForecastAdapter(
        private val context: Context,
        private val hourlyList: List<HourlyForecast>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<HourlyForecastAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val timeTextView: android.widget.TextView =
                itemView.findViewById(R.id.hourlyTimeTextView)
            val tempTextView: android.widget.TextView =
                itemView.findViewById(R.id.hourlyTempTextView)
            val conditionImageView: android.widget.ImageView =
                itemView.findViewById(R.id.hourlyConditionIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(context).inflate(
                R.layout.item_hourly_forecast, parent, false
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val forecast = hourlyList[position]

            holder.timeTextView.text = forecast.time
            holder.tempTextView.text = "${forecast.temperature}°"

            // Load weather icon
            try {
                com.bumptech.glide.Glide.with(context)
                    .load(forecast.iconUrl)
                    .placeholder(R.drawable.sun_icon)
                    .into(holder.conditionImageView)
            } catch (e: Exception) {
                holder.conditionImageView.setImageResource(R.drawable.sun_icon)
            }
        }

        override fun getItemCount(): Int = hourlyList.size
    }

    inner class DailyForecastAdapter(
        private val context: Context,
        private val dailyList: List<DailyForecast>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<DailyForecastAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val dayTextView: android.widget.TextView = itemView.findViewById(R.id.dayOfWeekTextView)
            val conditionImageView: android.widget.ImageView = itemView.findViewById(R.id.dailyConditionIcon)
            val highTempTextView: android.widget.TextView = itemView.findViewById(R.id.maxTempTextView)
            val lowTempTextView: android.widget.TextView = itemView.findViewById(R.id.minTempTextView)
            val rainChanceTextView: android.widget.TextView = itemView.findViewById(R.id.rainChanceTextView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(context).inflate(
                R.layout.item_daily_forecast, parent, false
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val forecast = dailyList[position]

            holder.dayTextView.text = forecast.day
            holder.highTempTextView.text = "H: ${forecast.maxTemp}°"
            holder.lowTempTextView.text = "L: ${forecast.minTemp}°"
            holder.rainChanceTextView.text = "${forecast.rainChance}% rain"

            // Load weather icon
            try {
                com.bumptech.glide.Glide.with(context)
                    .load(forecast.iconUrl)
                    .placeholder(R.drawable.sun_icon)
                    .into(holder.conditionImageView)
            } catch (e: Exception) {
                holder.conditionImageView.setImageResource(R.drawable.sun_icon)
            }
        }

        override fun getItemCount(): Int = dailyList.size
    }
}