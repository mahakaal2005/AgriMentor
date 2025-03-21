package com.example.agrimentor_innogeeks

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.agrimentor_innogeeks.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide

class home_fragment : Fragment(R.layout.fragment_home) {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var recommendationAdapter: RecommendationAdapter
    private val recommendationList = mutableListOf<Recommendation>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentHomeBinding.bind(view)

        setupUserProfile()
        setupRecommendationsRecyclerView()
        loadRecommendations()
        loadDailyTip()

        

        binding.viewAllButton.setOnClickListener {
            // Navigate to all recommendations
            Toast.makeText(requireContext(), "View all recommendations", Toast.LENGTH_SHORT).show()
        }
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