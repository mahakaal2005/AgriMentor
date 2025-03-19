package com.example.agrimentor_innogeeks

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.agrimentor_innogeeks.databinding.ActivityFrameLayoutBinding
import com.example.agrimentor_innogeeks.databinding.ActivityMainBinding
import com.example.agrimentor_innogeeks.databinding.FragmentForumBinding

class frame_layout : AppCompatActivity() {
    private lateinit var binding: ActivityFrameLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFrameLayoutBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home -> loadfragment(home_fragment())
                R.id.forum -> loadfragment(Forum())
                R.id.profile -> loadfragment(Profile())
                R.id.scan -> loadfragment(scan())

            }
            true
        }


        if (savedInstanceState == null) {
            loadfragment(home_fragment())
        }


    }


    private fun loadfragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_layout, fragment)
            .commit()
        Log.d("fragmentLoader", "Fragment Loaded")

    }
}