package com.example.agrimentor_innogeeks.utils

import android.content.Context
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class DatabaseSeeder(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    fun seedDatabase() {
        seedRecommendations()
        seedFarmingTips()
        // Only seed user if authenticated
        FirebaseAuth.getInstance().currentUser?.let {
            seedUserProfile(it.uid)
        }
    }

    private fun seedRecommendations() {
        val recommendations = listOf(
            mapOf(
                "title" to "Crop Rotation Guide",
                "description" to "Improve soil health and reduce pests with proper crop rotation techniques",
                "imageUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project-id.appspot.com/o/recommendation_images%2Fcrop_rotation.jpg?alt=media",
                "category" to "Crops",
                "createdAt" to com.google.firebase.Timestamp.now()
            ),
            mapOf(
                "title" to "Water Conservation",
                "description" to "Smart irrigation methods to save water and increase crop yield",
                "imageUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project-id.appspot.com/o/recommendation_images%2Fwater_conservation.jpg?alt=media",
                "category" to "Irrigation",
                "createdAt" to com.google.firebase.Timestamp.now()
            ),
            mapOf(
                "title" to "Organic Pest Control",
                "description" to "Natural solutions for common garden pests without chemicals",
                "imageUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project-id.appspot.com/o/recommendation_images%2Fpest_control.jpg?alt=media",
                "category" to "Protection",
                "createdAt" to com.google.firebase.Timestamp.now()
            ),
            mapOf(
                "title" to "Soil Testing Kit",
                "description" to "Analyze soil nutrients and pH to optimize your crops",
                "imageUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project-id.appspot.com/o/recommendation_images%2Fsoil_testing.jpg?alt=media",
                "category" to "Tools",
                "createdAt" to com.google.firebase.Timestamp.now()
            ),
            mapOf(
                "title" to "Seasonal Planting",
                "description" to "What to plant now in your region for maximum success",
                "imageUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project-id.appspot.com/o/recommendation_images%2Fseasonal_planting.jpg?alt=media",
                "category" to "Planning",
                "createdAt" to com.google.firebase.Timestamp.now()
            )
        )

        // Check if collection is empty before adding data
        db.collection("recommendations").get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                // Add recommendations one by one
                for (recommendation in recommendations) {
                    db.collection("recommendations").add(recommendation)
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error seeding recommendations: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                Toast.makeText(context, "Sample recommendations added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun seedFarmingTips() {
        val tips = listOf(
            mapOf(
                "content" to "Rotating your crops helps prevent soil-borne diseases and pest problems. Follow a three-year rotation plan for best results.",
                "title" to "Crop Rotation",
                "category" to "Soil Health"
            ),
            mapOf(
                "content" to "Mulch your garden with organic material to conserve water, suppress weeds, and improve soil health.",
                "title" to "Mulching",
                "category" to "Water Conservation"
            ),
            mapOf(
                "content" to "Plant companion crops like marigolds and basil near vegetables to naturally repel pests.",
                "title" to "Companion Planting",
                "category" to "Pest Management"
            ),
            mapOf(
                "content" to "Test your soil pH regularly. Most vegetables prefer slightly acidic soil with a pH between 6.0 and 7.0.",
                "title" to "Soil Testing",
                "category" to "Soil Health"
            ),
            mapOf(
                "content" to "Water plants early in the morning to reduce evaporation and prevent fungal diseases that thrive in wet overnight conditions.",
                "title" to "Watering Tips",
                "category" to "Water Management"
            )
        )

        db.collection("farming_tips").get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                for (tip in tips) {
                    db.collection("farming_tips").add(tip)
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error seeding farming tips: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                Toast.makeText(context, "Sample farming tips added", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun seedUserProfile(userId: String) {
        // Only create user profile if it doesn't exist
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val userData = mapOf(
                        "fullName" to "Demo User",
                        "email" to (FirebaseAuth.getInstance().currentUser?.email ?: "user@example.com"),
                        "phoneNumber" to "1234567890",
                        "bio" to "I am a passionate farmer growing organic vegetables.",
                        "photoUrl" to "https://firebasestorage.googleapis.com/v0/b/your-project-id.appspot.com/o/profile_images%2Fdefault_profile.jpg?alt=media"
                    )

                    db.collection("users").document(userId).set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "User profile created", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Error creating user profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }
}