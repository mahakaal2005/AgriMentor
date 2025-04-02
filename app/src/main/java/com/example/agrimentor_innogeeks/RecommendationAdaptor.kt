// RecommendationAdapter.kt
package com.example.agrimentor_innogeeks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class RecommendationAdapter(
    private val recommendations: List<Recommendation>,
    private val onItemClick: (Recommendation) -> Unit
) : RecyclerView.Adapter<RecommendationAdapter.RecommendationViewHolder>() {

    inner class RecommendationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.recommendationImage)
        val title: TextView = view.findViewById(R.id.recommendationTitle)
        val description: TextView = view.findViewById(R.id.recommendationDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommendation, parent, false)
        return RecommendationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        val recommendation = recommendations[position]

        holder.title.text = recommendation.title
        holder.description.text = recommendation.description

        Glide.with(holder.itemView.context)
            .load(recommendation.imageUrl)
            .placeholder(R.drawable.agriculture)
            .into(holder.image)

        holder.itemView.setOnClickListener {
            onItemClick(recommendation)
        }
    }

    override fun getItemCount() = recommendations.size
}

data class Recommendation(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val detailUrl: String? = null
)