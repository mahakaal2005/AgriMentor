package com.example.agrimentor_innogeeks

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.agrimentor_innogeeks.databinding.FragmentEditProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class EditProfile : Fragment(R.layout.fragment_edit_profile) {

    private lateinit var binding: FragmentEditProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null
    private var originalEmail: String = ""

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.profileImage.setImageURI(uri)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentEditProfileBinding.bind(view)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Enable email editing (it was disabled in the XML)
        binding.emailEditText.isEnabled = true

        // Set up toolbar back navigation
        binding.toolbar?.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        // Set up change photo button
        binding.fabChangePhoto.setOnClickListener {
            openGallery()
        }

        // Load user data
        loadUserData()

        // Set up save button
        binding.saveProfileButton.setOnClickListener {
            saveUserData()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImage.launch(intent)
    }

    private fun loadUserData() {
        binding.progressBar?.visibility = View.VISIBLE

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            // Set email (from Firebase Auth)
            originalEmail = currentUser.email ?: ""
            binding.emailEditText.setText(originalEmail)

            // Load profile photo if available
//            currentUser.photoUrl?.let { photoUrl ->
//                Glide.with(requireContext())
//                    .load(photoUrl)
//                    .placeholder(R.drawable.ic_launcher_background)
//                    .into(binding.profileImage)
//            }

            // Get additional user data from Firestore
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    binding.progressBar?.visibility = View.GONE
                    if (document.exists()) {
                        binding.fullNameEditText.setText(document.getString("fullName"))
                        binding.phoneNumberEditText.setText(document.getString("phoneNumber"))
                        binding.bioEditText.setText(document.getString("bio"))
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar?.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            binding.progressBar?.visibility = View.GONE
        }
    }

    private fun saveUserData() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid
        val fullName = binding.fullNameEditText.text.toString().trim()
        val phoneNumber = binding.phoneNumberEditText.text.toString().trim()
        val bio = binding.bioEditText.text.toString().trim()
        val newEmail = binding.emailEditText.text.toString().trim()

        // Validate inputs
        if (fullName.isEmpty()) {
            binding.fullNameLayout.error = "Full name cannot be empty"
            return
        } else {
            binding.fullNameLayout.error = null
        }

        if (phoneNumber.isEmpty()) {
            binding.phoneNumberLayout.error = "Phone number cannot be empty"
            return
        } else {
            binding.phoneNumberLayout.error = null
        }

        // Create user data map
        val userData = hashMapOf(
            "fullName" to fullName,
            "phoneNumber" to phoneNumber,
            "bio" to bio,
            "email" to newEmail,
            "userId" to userId
        )

        // Check if email has changed
        if (newEmail != originalEmail) {
            // Email has changed, prompt for password reauthentication
            promptForPassword(newEmail, userData)
        } else {
            // No email change, just update profile
            updateProfile(userData)
        }
    }

    private fun promptForPassword(newEmail: String, userData: HashMap<String, String>) {
        val dialogView = layoutInflater.inflate(R.layout.password_dialog, null)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Password")
            .setMessage("Please enter your current password to update your email address")
            .setView(dialogView)
            .setPositiveButton("Confirm") { dialog, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    reauthenticateAndUpdateEmail(password, newEmail, userData)
                } else {
                    Toast.makeText(requireContext(), "Password cannot be empty", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun reauthenticateAndUpdateEmail(password: String, newEmail: String, userData: HashMap<String, String>) {
        binding.progressBar?.visibility = View.VISIBLE
        val user = auth.currentUser ?: return

        // Reauthenticate with current credentials
        val credential = EmailAuthProvider.getCredential(originalEmail, password)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                // Update email
                user.updateEmail(newEmail)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Email updated successfully", Toast.LENGTH_SHORT).show()
                        // Update the rest of the profile
                        updateProfile(userData)
                    }
                    .addOnFailureListener { e ->
                        binding.progressBar?.visibility = View.GONE
                        Toast.makeText(requireContext(), "Failed to update email: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar?.visibility = View.GONE
                Toast.makeText(requireContext(), "Authentication failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfile(userData: HashMap<String, String>) {
        binding.progressBar?.visibility = View.VISIBLE
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        // First upload image if selected
        if (selectedImageUri != null) {
            val storageRef = storage.reference.child("profile_images/$userId")

            storageRef.putFile(selectedImageUri!!)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { uri ->
                        // Add photo URL to user data
                        userData["photoUrl"] = uri.toString()

                        // Update profile photo URL in Firebase Auth
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setPhotoUri(uri)
                            .build()

                        currentUser.updateProfile(profileUpdates)
                            .addOnCompleteListener {
                                // Save user data to Firestore
                                saveToFirestore(userId, userData)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar?.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Still save other data even if image upload fails
                    saveToFirestore(userId, userData)
                }
        } else {
            // No new image, just save user data
            saveToFirestore(userId, userData)
        }
    }

    private fun saveToFirestore(userId: String, userData: HashMap<String, String>) {
        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                binding.progressBar?.visibility = View.GONE
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressed()
            }
            .addOnFailureListener { e ->
                binding.progressBar?.visibility = View.GONE
                Log.e("EditProfile", "Error updating profile", e)
                Toast.makeText(requireContext(), "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}