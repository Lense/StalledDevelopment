package com.stalled.bathroam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements
    OnMapReadyCallback,
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

    private float mMinRating;
    private ArrayList<Bathroom> mLocalBathrooms = new ArrayList<Bathroom>();
    private GoogleMap mMap;
    private GoogleApiClient mClient;
    private static final String TAG = "MapActivity";
    private LatLng mLastLocation;
    private Bathroom mNearestBathroom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Populate the Action Bar with the custom layout
        ActionBar mActionBar = getSupportActionBar();
        LayoutInflater mInflater = LayoutInflater.from(this);
        View mCustomView = mInflater.inflate(R.layout.slider, null);
        mActionBar.setCustomView(mCustomView);
        mActionBar.setDisplayShowCustomEnabled(true);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
            .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Initialize the SeekBar listener for minimum bathroom ratings
        mMinRating = 0;
        SeekBar minRatingSlider = (SeekBar) findViewById(R.id.rating);
        minRatingSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Nothing to do here
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Nothing to do here
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Update the displayed minimum rating in the layout
                mMinRating = (float) progress / 10;
                ((TextView) findViewById(R.id.rating_value)).setText(String.valueOf(mMinRating));

                // Only display bathrooms meeting the minimum rating requirement
                mMap.clear();
                mMap.addMarker(new MarkerOptions().position(mLastLocation).title("Here"));
                for (int i = 0; i < mLocalBathrooms.size(); i++) {
                    Bathroom bathroom = mLocalBathrooms.get(i);
                    if (bathroom.getRating() >= mMinRating) {
                        mMap.addMarker(new MarkerOptions()
                            .position(bathroom.getLocation())
                            .title(String.valueOf(bathroom.getRating()))
                        );
                    }
                }

            }

        });

        // Build the GoogleAPIClient
        buildGoogleApiClient();
        if (mClient != null) {
            mClient.connect();
        } else
            Toast.makeText(this, "Not connected...", Toast.LENGTH_SHORT).show();

        // Set the intent for the new bathroom FAB
        FloatingActionButton fab1 = (FloatingActionButton) findViewById(R.id.new_bathroom_fab);
        fab1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent1 = new Intent(MainActivity.this, NewBathroomActivity.class);
                startActivity(intent1);
            }
        });

        // Set the intent for the find nearest bathroom FAB
        FloatingActionButton fab2 = (FloatingActionButton) findViewById(R.id.nearest_bathroom_fab);
        fab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Build the URL for the request
                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http")
                        .authority("104.131.49.58")
                        .appendPath("api")
                        .appendPath("bathrooms")
                        .appendPath("nearest")
                        .appendQueryParameter("lat", String.valueOf(mLastLocation.latitude))
                        .appendQueryParameter("lon", String.valueOf(mLastLocation.longitude));
                String url = builder.build().toString();
                findNearestBathroom(url);
                mMap.addMarker(new MarkerOptions().position(mNearestBathroom.getLocation()).title(String.valueOf(mNearestBathroom.getRating())));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mNearestBathroom.getLocation(), 18));
            }
        });

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Open the bathroom drilldown when a marker is clicked
        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Intent intent1 = new Intent(MainActivity.this, DrilldownActivity.class);
                String title = marker.getTitle();
                intent1.putExtra("markertitle", title);
                startActivity(intent1);
            }
        });

        // Check for more bathrooms when the camera is moved
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                // Build the URL for the request
                LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http")
                        .authority("104.131.49.58")
                        .appendPath("api")
                        .appendPath("bathrooms")
                        .appendPath("nearby")
                        .appendQueryParameter("ne_lat", String.valueOf(bounds.northeast.latitude))
                        .appendQueryParameter("ne_long", String.valueOf(bounds.northeast.longitude))
                        .appendQueryParameter("sw_lat", String.valueOf(bounds.southwest.latitude))
                        .appendQueryParameter("sw_long", String.valueOf(bounds.southwest.longitude));
                String url = builder.build().toString();
                GetLocalBathrooms(url);
            }
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        mClient.connect();

    }

    @Override
    public void onStop() {
        super.onStop();
        mClient.disconnect();
    }

    private void GetLocalBathrooms(String url) {

        JsonArrayRequest jsArrReq = new JsonArrayRequest(Request.Method.GET, url, null, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {
                for (int i = 0; i < response.length(); i++) {
                    try {
                        // Parse the JSON object for bathroom information
                        JSONObject jo = response.getJSONObject(i);
                        int id = jo.getInt("id");
                        JSONArray locArray = jo.getJSONArray("loc");
                        Double lat = (Double)locArray.get(0);
                        Double lon = (Double)locArray.get(1);
                        LatLng loc = new LatLng(lat, lon);
                        float rating = (float) jo.getDouble("rating");

                        // Construct the new bathroom and add it if necessary
                        Bathroom newBathroom = new Bathroom(id, loc, rating);
                        if(!mLocalBathrooms.contains(newBathroom)){
                            mLocalBathrooms.add(newBathroom);
                            if(rating >= mMinRating){
                                mMap.addMarker(new MarkerOptions().position(loc).title(String.valueOf(rating)));
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.w(TAG, error);
            }
        });

        RequestHandler.getInstance().addToReqQueue(jsArrReq, "jreq", getApplicationContext());
    }


    @Override
    public void onConnected(Bundle arg0) {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        Location myLocation = LocationServices.FusedLocationApi.getLastLocation(mClient);
        if (myLocation != null) {
            mLastLocation = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mLastLocation, 18));
            mMap.addMarker(new MarkerOptions().position(mLastLocation).title("Here"));
        }

    }

    @Override
    public void onConnectionSuspended(int arg0) {
        Toast.makeText(this, "Connection suspended...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult arg0) {
        Toast.makeText(this, "Failed to connect...", Toast.LENGTH_SHORT).show();
    }

    protected synchronized void buildGoogleApiClient() {
        mClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void findNearestBathroom(String url) {
        JsonObjectRequest jsObjReq = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    // Parse the JSON object for bathroom information
                    int id = response.getInt("id");
                    JSONArray locArray = response.getJSONArray("loc");
                    Double lat = (Double)locArray.get(0);
                    Double lon = (Double)locArray.get(1);
                    LatLng loc = new LatLng(lat, lon);
                    float rating = (float) response.getDouble("rating");
                    mNearestBathroom = new Bathroom(id, loc, rating);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.w(TAG, error);
            }
        });

        RequestHandler.getInstance().addToReqQueue(jsObjReq, "jreq", getApplicationContext());
    }
}
