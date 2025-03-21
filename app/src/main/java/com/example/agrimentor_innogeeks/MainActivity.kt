package com.example.agrimentor_innogeeks

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.airbnb.lottie.LottieAnimationView
import com.example.agrimentor_innogeeks.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.auth

class MainActivity : AppCompatActivity() {

        private lateinit var auth: FirebaseAuth
        private lateinit var binding: ActivityMainBinding

        override fun onCreate(savedInstanceState: Bundle?) {

            binding = ActivityMainBinding.inflate(layoutInflater)
            super.onCreate(savedInstanceState)

            setContentView(binding.root)

            // Initialize Firebase
            FirebaseApp.initializeApp(this)

            // Initialize Firebase Auth
            auth = FirebaseAuth.getInstance()


            // Check if user is signed in (non-null)
            val currentUser = auth.currentUser

            // Check if user is already signed in
            if (currentUser != null) {
                // User is already signed in, redirect to frame_layout activity
                startActivity(Intent(this, frame_layout::class.java))
                finish() // Close this activity so user can't go back to login screen
                return
            }

            binding.helloLottie.repeatCount=0

            // Login button functionality
            binding.loginButton.setOnClickListener {
                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    signInWithEmailPassword(email, password)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }

            // Register button functionality
            binding.registerButton.setOnClickListener {
                val email = binding.emailEditText.text.toString().trim()
                val password = binding.passwordEditText.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    createAccount(email, password)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Function to create a new account
        private fun createAccount(email: String, password: String) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign-up success
                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT)
                            .show()

                    } else {
                        // Sign-up failed
                        try {
                            throw task.exception!!
                        } catch (e: FirebaseAuthUserCollisionException) {
                            Toast.makeText(
                                this,
                                "This email is already registered.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this,
                                "Registration failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
        }

        // Function to sign in with email and password
        private fun signInWithEmailPassword(email: String, password: String) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Login success
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT)
                            .show()
                        startActivity(Intent(this, frame_layout::class.java))
                    } else {
                        // Login failed
                        try {
                            throw task.exception!!
                        } catch (e: FirebaseAuthInvalidUserException) {
                            Toast.makeText(
                                this,
                                "No account found with this email.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(
                                this,
                                "Invalid password. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
        }
}