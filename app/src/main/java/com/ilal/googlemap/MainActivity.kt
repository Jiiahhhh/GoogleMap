package com.ilal.googlemap

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.os.AsyncTask
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.data.DataBufferSafeParcelable
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.ilal.googlemap.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOError
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback
//LocationListener, GoogleMap.OnCameraMoveListener, GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener
{

    private var mMap: GoogleMap? = null
    lateinit var mapView: MapView
    private val MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey"
    private lateinit var binding: ActivityMainBinding
    private val DEFAULT_ZOOM = 15f
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    lateinit var tvCurrentLocation: TextView
    lateinit var btnSearch: Button

    val FINE_LOCATION_RQ = 101
    val CAMERA_RQ = 102

    var end_latitude = 0.0
    var end_longitude = 0.0
    var origin: MarkerOptions? = null
    var destination: MarkerOptions? = null
    var latitude = 0.0
    var longitude = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapView = binding.maps
        tvCurrentLocation = binding.tvAdd
        btnSearch = binding.btnSearch

        checkForPermissions(Manifest.permission.ACCESS_FINE_LOCATION, "location", FINE_LOCATION_RQ)

        var mapViewBundle: Bundle? = null
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY)
        }

        mapView.onCreate(mapViewBundle)
        mapView.getMapAsync(this)

        btnSearch.setOnClickListener {
            searchArea()
        }

        binding.btnClear.setOnClickListener {
            mapView.onCreate(mapViewBundle)
            mapView.getMapAsync(this)
        }
    }

    private fun searchArea() {
        val tf_location = binding.edtLocation
        val location = tf_location.text.toString()
        var addressList: List<Address>? = null
        val markerOptions = MarkerOptions()
        Log.d("location = ", location)
        if (location != "") {
            val geocoder = Geocoder(applicationContext)
            try {
                addressList = geocoder.getFromLocationName(location, 5)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (addressList != null) {
                for (i in addressList.indices) {
                    val myAddress = addressList[i]
                    val latlng = LatLng(myAddress.latitude, myAddress.longitude)
                    markerOptions.position(latlng)
                    mMap!!.addMarker(markerOptions)
                    end_latitude = myAddress.latitude
                    end_longitude = myAddress.longitude
                    mMap!!.animateCamera(CameraUpdateFactory.newLatLng(latlng))
                    val mo = MarkerOptions()
                    mo.title("Distance")
                    val results = FloatArray(10)
                    Location.distanceBetween(
                        latitude, longitude, end_latitude, end_longitude, results
                    )
                    val s = String.format("%.1f", results[0] / 1000)

                    //Setting marker to draw route between two points
                    origin = MarkerOptions().position(LatLng(latitude, longitude))
                        .title("HSD Layout").snippet("origin")
                    destination =
                        MarkerOptions().position(LatLng(end_latitude, end_longitude))
                            .title(tf_location.text.toString())
                            .snippet("Distance = $s KM")
                    mMap!!.addMarker(destination!!)
                    mMap!!.addMarker(origin!!)
                    Toast.makeText(this@MainActivity, "Distance = $s KM", Toast.LENGTH_SHORT).show()

                    tvCurrentLocation!!.setText("Distance = $s KM")

                    val url: String = getDirectionUrl(origin!!.position, destination!!.position)!!
                    val downloadTask: DownloadTask = DownloadTask()

                    //start download api
                    downloadTask.execute(url)
                }
            }
        }
    }

    inner class DownloadTask :
        AsyncTask<String?, Void?, String>() {

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            val parserTask = ParserTask()
            parserTask.execute(result)

        }

        override fun doInBackground(vararg url: String?): String {
            var data = ""
            try {
                data = downloadUrl(url[0].toString()).toString()
            } catch (e: java.lang.Exception) {
                Log.d("Background Task", e.toString())
            }
            return data
        }
    }

    inner class ParserTask :
        AsyncTask<String?, Int?, List<List<HashMap<String, String>>>?>() {
        //Parsing the data in non-ui thread
        override fun doInBackground(vararg jsonData: String?): List<List<HashMap<String, String>>>? {
            val jObjects: JSONObject
            var routes: List<List<HashMap<String, String>>>? = null
            try {
                jObjects = JSONObject(jsonData[0])
                val parser = DataParser()
                routes = parser.parse(jObjects)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            return routes
        }

        override fun onPostExecute(result: List<List<HashMap<String, String>>>?) {
            val points = ArrayList<LatLng?>()
            val lineOptions = PolylineOptions()
            for (i in result!!.indices) {
                val path =
                    result[i]
                for (j in path.indices) {
                    val point = path[j]
                    val lat = point["lat"]!!.toDouble()
                    val lng = point["lng"]!!.toDouble()
                    val position = LatLng(lat, lng)
                    points.add(position)
                }
                lineOptions.addAll(points)
                lineOptions.width(8f)
                lineOptions.color(Color.BLUE)
                lineOptions.geodesic(true)
            }
            if (points.size != 0)
                mMap!!.addPolyline(lineOptions)
        }
    }

    //A Method to download json data from url
    @kotlin.jvm.Throws(IOException::class)
    private fun downloadUrl(stringUrl: String): String? {
        var data = ""
        var iStream: InputStream? = null
        var urlConnection: HttpURLConnection? = null
        try {
            val url = URL(stringUrl)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.connect()
            iStream = urlConnection!!.inputStream
            val br = BufferedReader(InputStreamReader(iStream))
            val sb = StringBuffer()
            var line: String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
            }
            data = sb.toString()
            br.close()
        } catch (e: java.lang.Exception) {
            Log.d("Exception", e.toString())
        } finally {
            iStream!!.close()
            urlConnection!!.disconnect()
        }
        return data
    }


    private fun getDirectionUrl(origin: LatLng, dest: LatLng): String? {
        //origin route
        val str_origin = "origin" + origin.latitude + "," + origin.longitude
        //destination route
        val str_dest = "destination" + dest.latitude + "," + dest.longitude
        //setting transportation mode
        val mode = "mode-driving"
        //building the parameters to the web services
        val parameters = "$str_origin&$str_dest&$mode"
        //output format
        val output = "json"
        //Building the url to the web service
        return "https://maps.googleapis.com/maps/api/directions/$output?$parameters&key=AIzaSyC__iu3N4N6YPGTzU5lmEcUgnO3IMCVJRE"
    }

    override fun onMapReady(p0: GoogleMap) {
        mapView.onResume()
        mMap = p0

        if (ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mMap!!.isMyLocationEnabled()
//        mMap!!.setOnCameraMoveListener(this)
//        mMap!!.setOnCameraMoveStartedListener(this)
//        mMap!!.setOnCameraIdleListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        checkForPermissions(Manifest.permission.ACCESS_FINE_LOCATION, "location", FINE_LOCATION_RQ)
        var mapViewBundle = outState.getBundle(MAP_VIEW_BUNDLE_KEY)
        if (mapViewBundle == null) {
            mapViewBundle = Bundle()
            outState.putBundle(MAP_VIEW_BUNDLE_KEY, mapViewBundle)
        }
        mapView.onSaveInstanceState(mapViewBundle)
    }

    private fun getCurrentLocation() {
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this@MainActivity)

        try {
            @SuppressLint("MissingPermission") val location =
                fusedLocationProviderClient!!.getLastLocation()
            location.addOnCompleteListener(object : OnCompleteListener<Location> {
                override fun onComplete(loc: Task<Location>) {
                    if (loc.isSuccessful) {
                        val currentLocation = loc.result as Location?
                        if (currentLocation != null) {
                            moveCamera(
                                LatLng(currentLocation.latitude, currentLocation.longitude),
                                DEFAULT_ZOOM
                            )
                            latitude = currentLocation.latitude
                            longitude = currentLocation.longitude
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity, "Lokasi saat ini tidak diketahui", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
        } catch (se: java.lang.Exception) {
            Log.e("TAG", "Security Exception")
        }
    }

    private fun moveCamera(latLng: LatLng, zoom: Float) {
        mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    private fun checkForPermissions(permission: String, name: String, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    applicationContext, permission
                ) == PackageManager.PERMISSION_GRANTED -> {
                    getCurrentLocation()
                    Toast.makeText(
                        applicationContext, "$name permission granted", Toast.LENGTH_SHORT
                    ).show()
                }
                shouldShowRequestPermissionRationale(permission) -> showDialog(
                    permission, name, requestCode
                )

                else -> ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        fun innerCheck(name: String) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(applicationContext, "$name permission refuesd", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(applicationContext, "$name permission granted", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        when (requestCode) {
            FINE_LOCATION_RQ -> innerCheck("location")
            CAMERA_RQ -> innerCheck("camera")
        }
    }

    private fun showDialog(permission: String, name: String, requestCode: Int) {
        val builder = AlertDialog.Builder(this)

        builder.apply {
            setMessage("Permission to access your $name is required to use this app")
            setTitle("Permission Required")
            setPositiveButton("OK") { dialog, which ->
                ActivityCompat.requestPermissions(
                    this@MainActivity, arrayOf(permission), requestCode
                )
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

//    override fun onLocationChanged(location: Location) {
//        val geocoder = Geocoder(this, Locale.getDefault())
//        var addresses: List<Address>? = null
//        try {
//            addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//        setAddress(addresses!![0])
//    }
//
//    private fun setAddress(addresses: Address) {
//        if (addresses != null) {
//            if (addresses.getAddressLine(0) != null) {
//                tvCurrentLocation!!.setText(addresses.getAddressLine(0))
//            }
//            if (addresses.getAddressLine(1) != null) {
//                tvCurrentLocation!!.setText(
//                    tvCurrentLocation.text.toString() + addresses.getAddressLine(1)
//                )
//            }
//        }
//    }
//
//    override fun onCameraMove() {
//
//    }
//
//    override fun onCameraMoveStarted(p0: Int) {
//
//    }
//
//    override fun onCameraIdle() {
//        var addresses: List<Address>? = null
//        val geocoder = Geocoder(this, Locale.getDefault())
//        try {
//            addresses = geocoder.getFromLocation(
//                mMap!!.cameraPosition.target.latitude,
//                mMap!!.cameraPosition.target.longitude,
//                1
//            )
//            setAddress(addresses!![0])
//        } catch (e: java.lang.IndexOutOfBoundsException) {
//            e.printStackTrace()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
}
