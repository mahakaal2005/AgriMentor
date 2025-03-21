package com.example.agrimentor_innogeeks

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.agrimentor_innogeeks.databinding.FragmentChangePasswordBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePassword : Fragment(R.layout.fragment_change_password) {

    private lateinit var binding: FragmentChangePasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentChangePasswordBinding.bind(view)
        auth = FirebaseAuth.getInstance()

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        binding.changePasswordButton.setOnClickListener {
            changePassword()
        }
    }

    private fun changePassword() {
        val currentPassword = binding.currentPasswordEditText.text.toString()
        val newPassword = binding.newPasswordEditText.text.toString()
        val confirmPassword = binding.confirmPasswordEditText.text.toString()

        // Basic validation
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(requireContext(), "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(requireContext(), "New passwords don't match", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        val user = auth.currentUser
        if (user != null && user.email != null) {
            // Re-authenticate before changing password
            val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)

            user.reauthenticate(credential)
                .addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        // User re-authenticated, now change password
                        user.updatePassword(newPassword)
                            .addOnCompleteListener { updateTask ->
                                binding.progressBar.visibility = View.GONE

                                if (updateTask.isSuccessful) {
                                    Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show()
                                    requireActivity().onBackPressed()
                                } else {
                                    Toast.makeText(requireContext(), "Failed to update password: ${updateTask.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Current password is incorrect", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}