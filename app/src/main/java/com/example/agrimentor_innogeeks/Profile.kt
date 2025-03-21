package com.example.agrimentor_innogeeks

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import com.example.agrimentor_innogeeks.databinding.FragmentProfileBinding
import com.example.agrimentor_innogeeks.utils.DatabaseSeeder
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.BuildConfig

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView
import java.util.UUID

class Profile : Fragment(R.layout.fragment_profile) {

    private lateinit var auth: FirebaseAuth

    // UI components
    private lateinit var profileImage: CircleImageView
    private lateinit var emailTextView: TextView
    private lateinit var userIdTextView: TextView
    private lateinit var editProfileOption: LinearLayout
    private lateinit var changePasswordOption: LinearLayout
    private lateinit var logoutButton: MaterialButton
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var devOptionsButton: MaterialButton

    private lateinit var binding: FragmentProfileBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        binding = FragmentProfileBinding.bind(view)

        // Initialize all UI components
        profileImage = binding.profileImage
        emailTextView = binding.emailTextView
        userIdTextView = binding.userIdTextView
        editProfileOption = binding.editProfileOption
        changePasswordOption = binding.changePasswordOption
        progressBar = binding.progressBar
        logoutButton = binding.logoutButton
        devOptionsButton = binding.devOptionsButton


        // Only show dev options in debug builds
        if (BuildConfig.DEBUG) {
            devOptionsButton.visibility = View.VISIBLE
            devOptionsButton.setOnClickListener {
                showDeveloperOptions()
            }
        }

        // Load user profile information
        loadUserProfile()

        // Setup click listeners
        setupClickListeners()

        // Logout button listener
        logoutButton.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), MainActivity::class.java)).also {
                requireActivity().finish()
            }
        }
    }


    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            // Get user data from Firestore
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Update UI with user data
                        binding.emailTextView.text = document.getString("email")
                        binding.userIdTextView.text = userId

                        // Load profile image from the stored URL in Firestore
                        val photoUrl = document.getString("photoUrl")
                        if (!photoUrl.isNullOrEmpty()) {
                            // Add Glide dependency in build.gradle if not already added
                            // implementation 'com.github.bumptech.glide:glide:4.15.1'
                            com.bumptech.glide.Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.agriculture)
                                .into(binding.profileImage)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Failed to load profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupClickListeners() {
        // Edit Profile option click listener
        editProfileOption.setOnClickListener {
            // Navigate to edit profile screen
            //Toast.makeText(requireContext(), "Edit Profile clicked", Toast.LENGTH_SHORT).show()
            // Redirect to EditProfileFragment would go here
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_layout, EditProfile())
                .addToBackStack(null)
                .commit()
        }

        // Change Password option click listener
        changePasswordOption.setOnClickListener {
            // Navigate to change password screen
            //Toast.makeText(requireContext(), "Change Password clicked", Toast.LENGTH_SHORT).show()
            // Intent to ChangePasswordActivity would go here
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_layout, ChangePassword())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun navigateToLogin() {
        // Redirect to login activity - replace LoginActivity with your actual login activity
        startActivity(Intent(requireContext(), MainActivity::class.java)).also {
            requireActivity().finish()
        }
    }

    private var imageUrl: String = ""

    private fun uploadImageToFirebase(imageUri: Uri?) {
        if (imageUri == null) return

        // Show progress
        progressBar.visibility = View.VISIBLE

        // Create a unique file name for the image
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            return
        }

        // Create storage reference with proper path
        val storageRef = FirebaseStorage.getInstance().reference
        val profileImagesRef = storageRef.child("profile_images/$userId/${UUID.randomUUID()}")

        // Upload file to Firebase Storage
        profileImagesRef.putFile(imageUri)
            .addOnSuccessListener {
                // Get download URL and save to Firestore
                profileImagesRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    imageUrl = downloadUri.toString()
                    // Now save to Firestore
                    saveToFirestore(userId)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveToFirestore(userId: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)

        userRef.update("photoUrl", imageUrl)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Profile image updated successfully", Toast.LENGTH_SHORT).show()
                loadUserProfile() // Reload profile to show the new image
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // In your Settings or Profile fragment
    private fun showDeveloperOptions() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Developer Options")

        val options = arrayOf("Seed Database", "Clear Database Cache", "Reset App Preferences")

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    val seeder = DatabaseSeeder(requireContext())
                    seeder.seedDatabase()
                }
                1 -> {
                    // Clear cache code
                    FirebaseFirestore.getInstance().clearPersistence()
                    Toast.makeText(requireContext(), "Cache cleared", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    // Reset preferences
                    val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    Toast.makeText(requireContext(), "Preferences reset", Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.show()
    }

}