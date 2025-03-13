package com.example.agrimentor_innogeeks

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.auth

class MainActivity : AppCompatActivity() {

        private lateinit var auth: FirebaseAuth

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // Initialize Firebase Auth
            auth = FirebaseAuth.getInstance()

            val emailEditText = findViewById<EditText>(R.id.emailEditText)
            val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
            val loginButton = findViewById<Button>(R.id.loginButton)
            val registerButton = findViewById<Button>(R.id.registerButton)

            // Login button functionality
            loginButton.setOnClickListener {
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()

                if (email.isNotEmpty() && password.isNotEmpty()) {
                    signInWithEmailPassword(email, password)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }

            // Register button functionality
            registerButton.setOnClickListener {
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()

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
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
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