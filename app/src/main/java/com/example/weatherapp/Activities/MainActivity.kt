package com.example.weatherapp.Activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.Models.WeatherResponse
import com.example.weatherapp.Network.WeatherService
import com.example.weatherapp.R
import com.example.weatherapp.url.Constants
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.Callback
import retrofit.GsonConverterFactory
import retrofit.Response
import retrofit.Retrofit
import java.text.SimpleDateFormat
import java.util.*
import java.util.prefs.AbstractPreferences

class MainActivity : AppCompatActivity() {
    private lateinit var mFusedLocationClient : FusedLocationProviderClient
    private var makeShowDialog:Dialog? = null
    private lateinit var mSharePreferences :SharedPreferences

    private val retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URI)
        .addConverterFactory(GsonConverterFactory.create())
        .build()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharePreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        if(!isLocationEnabled()){
           Toast.makeText(this,"Your location is not granted ,you can change this in your setting",Toast.LENGTH_SHORT).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object  : MultiplePermissionsListener{
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if(report!!.areAllPermissionsGranted()){
                            //request location
                            requestLocationData()
                        }else if(report.isAnyPermissionPermanentlyDenied){
                            Toast.makeText(this@MainActivity,"Your have denied your location.It is needed to allow to continue app",Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        p0: MutableList<PermissionRequest>?,
                        p1: PermissionToken?
                    ) {
                       showRationalPermissionDialog()
                    }

                }).onSameThread().check()
        }

        bt_search.setOnClickListener{
            val cityName = et_cityName.text.toString()
            getLocationWeatherDetailsByCityName(cityName)
        }

        bt_search_weather.setOnClickListener{
            val cityZipCode = et_cityZipCode.text.toString()
            getLocationWeatherDetailsByZipCode(cityZipCode!!.toInt())
        }

    }


    private fun isLocationEnabled() : Boolean{
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    private fun showRationalPermissionDialog(){
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton("GO TO SETTING") {
                _,_ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val  uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }

            }
            .setNegativeButton("Canceled"){
                dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")

            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constants.isNetworkAvailable(this)){


            val service = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall = service.getWeather(latitude,longitude,Constants.METRIC_UNIT,Constants.API_KEY)

            showMakeProgressDialog()
            listCall.enqueue(object  :Callback<WeatherResponse>{
                override fun onFailure(t: Throwable?) {
                    hideProgressDialog()
                    Log.e("Error",t!!.message.toString())

                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideProgressDialog()
                        val weatherList = response.body()

                        val weatherResponseToJsonString = Gson().toJson(weatherList)
                        val editor = mSharePreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseToJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("WeatherList",weatherList.toString())


                    }else{
                        val code = response.code()
                        when(code) {
                            400 ->
                                Log.e("400 error", "Your connection is so bad")
                            404 ->
                                Log.e("404 error", "404 Error")
                            else ->
                                Log.e("Other error", "Error")
                        }

                    }
                }

            })


        }else{
            Toast.makeText(this,"Your network is not available "
                ,Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocationWeatherDetailsByCityName(cityName:String){
        if(Constants.isNetworkAvailable(this)){


            val service = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall = service.getWeatherByCityName(cityName,Constants.METRIC_UNIT,Constants.API_KEY)

            showMakeProgressDialog()
            listCall.enqueue(object  :Callback<WeatherResponse>{
                override fun onFailure(t: Throwable?) {
                    hideProgressDialog()
                    Log.e("Error",t!!.message.toString())

                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideProgressDialog()
                        val weatherList = response.body()

                        val weatherResponseToJsonString = Gson().toJson(weatherList)
                        val editor = mSharePreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseToJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("WeatherList",weatherList.toString())


                    }else{
                        val code = response.code()
                        when(code) {
                            400 ->
                                Log.e("400 error", "Your connection is so bad")
                            404 ->
                                Log.e("404 error", "404 Error")
                            else ->
                                Log.e("Other error", "Error")
                        }

                    }
                }

            })


        }else{
            Toast.makeText(this,"Your network is not available "
                ,Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocationWeatherDetailsByZipCode(cityZipCode:Int){
        if(Constants.isNetworkAvailable(this)){


            val service = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall = service.getWeatherByCityZipCode(cityZipCode,Constants.METRIC_UNIT,Constants.API_KEY)

            showMakeProgressDialog()
            listCall.enqueue(object  :Callback<WeatherResponse>{
                override fun onFailure(t: Throwable?) {
                    hideProgressDialog()
                    Log.e("Error",t!!.message.toString())

                }

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if(response!!.isSuccess){
                        hideProgressDialog()
                        val weatherList = response.body()

                        val weatherResponseToJsonString = Gson().toJson(weatherList)
                        val editor = mSharePreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseToJsonString)
                        editor.apply()
                        setupUI()
                        Log.i("WeatherList",weatherList.toString())


                    }else{
                        val code = response.code()
                        when(code) {
                            400 ->
                                Log.e("400 error", "Your connection is so bad")
                            404 ->
                                Log.e("404 error", "404 Error")
                            else ->
                                Log.e("Other error", "Error")
                        }

                    }
                }

            })


        }else{
            Toast.makeText(this,"Your network is not available "
                ,Toast.LENGTH_SHORT).show()
        }
    }
    private fun showMakeProgressDialog(){
        makeShowDialog = Dialog(this)
        makeShowDialog!!.setContentView(R.layout.show_progress_dialog)
        makeShowDialog!!.show()
    }

    private fun hideProgressDialog(){
        if(makeShowDialog!= null){
            makeShowDialog!!.dismiss()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI(){
        val weatherResponseJsonString = mSharePreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            var weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)
            for(i in weatherList.weather.indices){
                Log.i("Weather name",weatherList.weather.toString())

                tv_main.text = weatherList.weather[i].main
                tv_main_description.text = weatherList.weather[i].description
                tv_temp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_min.text = weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.locales.toString())
                tv_max.text = weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.locales.toString())
                et_name.setText(weatherList.name)
                tv_country.text = weatherList.sys.country
                tv_speed.text = weatherList.wind.speed.toString()

                tv_sunrise_time.text = unixTime(weatherList.sys.sunrise.toLong())
                tv_sunset_time.text = unixTime((weatherList.sys.sunset.toLong()))

                when(weatherList.weather[i].icon){
                    "01d" -> iv_main.setImageResource(R.drawable.sunny)
                    "02d" -> iv_main.setImageResource(R.drawable.cloud)
                    "03d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04d" -> iv_main.setImageResource(R.drawable.cloud)
                    "04n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10d" -> iv_main.setImageResource(R.drawable.rain)
                    "11d" -> iv_main.setImageResource(R.drawable.storm)
                    "13d" -> iv_main.setImageResource(R.drawable.snowflake)
                    "01n" -> iv_main.setImageResource(R.drawable.cloud)
                    "02n" -> iv_main.setImageResource(R.drawable.cloud)
                    "03n" -> iv_main.setImageResource(R.drawable.cloud)
                    "10n" -> iv_main.setImageResource(R.drawable.cloud)
                    "11n" -> iv_main.setImageResource(R.drawable.rain)
                    "13n" -> iv_main.setImageResource(R.drawable.snowflake)
                }

            }
        }




    }

    private fun getUnit(value: String):String?{
        var value= "°C"
        if("US" == value || "LR" == value||"MM" == value){
            value = " °F"
        }
        return value
    }

    private fun unixTime(timex : Long) : String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("hh : mm ")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_refresh,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.menu_refresh ->{
                requestLocationData()
                true
            }else -> return super.onOptionsItemSelected(item)
        }

    }

}

