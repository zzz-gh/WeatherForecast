package com.example.weatherapp.url

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {

        const val BASE_URI = "http://api.openweathermap.org/data/"
        const val API_KEY = "76397fc864a3d60d1b5d2de5c39f2e33"
        const val METRIC_UNIT = "metric"
        const val PREFERENCE_NAME = "WeatherAppPreferenceName"
        const val WEATHER_RESPONSE_DATA = "WeatherResponseData"


    fun isNetworkAvailable(context: Context) : Boolean{
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            val nerWork = connectivityManager.activeNetwork ?:return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(nerWork) ?: return false
            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        }else{
            val netWorkInfo = connectivityManager.activeNetworkInfo
            return netWorkInfo!= null && netWorkInfo.isConnectedOrConnecting
        }


    }
}