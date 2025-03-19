package com.example.agrimentor_innogeeks

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.agrimentor_innogeeks.databinding.ActivityFrameLayoutBinding
import com.example.agrimentor_innogeeks.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth


class Profile : Fragment(R.layout.fragment_profile) {

    private lateinit var auth: FirebaseAuth

    private lateinit var binding: FragmentProfileBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding= FragmentProfileBinding.bind(view)
        auth=FirebaseAuth.getInstance()

        binding.logoutButton.setOnClickListener{

            auth.signOut()

            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show() //In a Fragment, you cannot use this directly to refer to the Context because a Fragment is not a Context by itself, unlike an Activity.

            startActivity(Intent(requireContext(), MainActivity::class.java)).also {
                requireActivity().finish()  //I can see the issue with the code. In a Fragment, you can't directly call finish() as that's an Activity method. Instead, you need to call requireActivity().finish() to finish the parent Activity.
            }
        }

    }

}