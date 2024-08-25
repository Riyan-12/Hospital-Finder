package com.example.wecare;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener{

    Location currentLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    private static final int REQUEST_CODE = 101;
    private GoogleMap mMap;
    GoogleApiClient googleApiClient;
    Location lastLocation;
    LocationRequest locationRequest;

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference HospitalRef;
    private DatabaseReference CustomerRequestRef;
    private Button LogoutButton;
    private Button NearestHospitalButton;
    private String CustomerID;
    private LatLng CustomerLocation;
    private boolean currentLogoutCustomerStatus=false;
    private int radius=1;
    private boolean HospitalFound = false;
    private String HospitalFoundID;
    Marker HospitalMarker;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        CustomerID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        HospitalRef = FirebaseDatabase.getInstance().getReference().child("Hospitals Available");
        CustomerRequestRef = FirebaseDatabase.getInstance().getReference().child("Customer Requests");
        LogoutButton = (Button) findViewById(R.id.logout_btn);
        NearestHospitalButton = (Button) findViewById(R.id.nearest_hospital);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        fetchLastLocation();
        LogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                currentLogoutCustomerStatus = true;
                mAuth.signOut();
                Intent welcomeIntent = new Intent(MapsActivity.this,loginpage.class);
                startActivity(welcomeIntent);
            }
        });

        NearestHospitalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                GeoFire geoFire = new GeoFire(CustomerRequestRef);
                geoFire.setLocation(CustomerID, new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {

                    }
                });
                CustomerLocation = new LatLng(lastLocation.getLatitude(),lastLocation.getLongitude());
                mMap.addMarker(new MarkerOptions().position(CustomerLocation).title("You're Here.."));

                NearestHospitalButton.setText("Searching nearest hospital...");
                GetClosestHospital();


            }
        });
    }

    private void GetClosestHospital()
    {
        GeoFire geoFire = new GeoFire(HospitalRef);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(CustomerLocation.latitude,CustomerLocation.longitude),radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(!HospitalFound)
                {
                    HospitalFound = true;
                    HospitalFoundID = key;



                    GettingHospitalLocation();
                    NearestHospitalButton.setText("Finding the Location...");


                }

            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady()
            {
                if(!HospitalFound)
                {
                    radius = radius+1;
                    GetClosestHospital();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void GettingHospitalLocation()
    {
        HospitalRef.child(HospitalFoundID).child("l")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot)
                    {
                        if(dataSnapshot.exists())
                        {
                            List<Object> hospitalLocationMap = (List<Object>) dataSnapshot.getValue();
                            double LocationLat = 0;
                            double LocationLng = 0;
                            NearestHospitalButton.setText("Hospital Found");

                            if(hospitalLocationMap.get(0)!= null)
                            {
                                LocationLat = Double.parseDouble(hospitalLocationMap.get(0).toString());


                            }
                            if(hospitalLocationMap.get(1)!= null)
                            {
                                LocationLng = Double.parseDouble(hospitalLocationMap.get(1).toString());


                            }
                            LatLng HospitalLatLng = new LatLng(LocationLat,LocationLng);
                            if(HospitalMarker != null)
                            {
                                HospitalMarker.remove();
                            }

                            Location location1 = new Location("");
                            location1.setLatitude(CustomerLocation.latitude);
                            location1.setLongitude((CustomerLocation.longitude));

                            Location location2 = new Location("");
                            location2.setLatitude(HospitalLatLng.latitude);
                            location2.setLongitude((HospitalLatLng.longitude));

                            float Distance = location1.distanceTo(location2);
                            NearestHospitalButton.setText("Hospital Found :"+String.valueOf(Distance));


                            HospitalMarker = mMap.addMarker(new MarkerOptions().position(HospitalLatLng).title("Nearest Hospital..."));


                        }

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });


    }

    private void fetchLastLocation() {
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]
                    {Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE);
            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if(location != null){
                    currentLocation = location;
                    Toast.makeText(getApplicationContext(),currentLocation.getLatitude()+""+currentLocation.getLongitude(), Toast.LENGTH_SHORT).show();
                    SupportMapFragment supportMapFragment = (SupportMapFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.google_map);

                    supportMapFragment.getMapAsync(MapsActivity.this);

                }
            }
        });

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        LatLng latLng = new LatLng(currentLocation.getLatitude(),currentLocation.getLongitude());
       // MarkerOptions markerOptions = new MarkerOptions().position(latLng)
         //       .title("You're Here...");
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,10));
        //googleMap.addMarker(markerOptions);



        mMap = googleMap;
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);






    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case REQUEST_CODE:
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    fetchLastLocation();

                }
                break;
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(10000);
        locationRequest.setPriority(locationRequest.PRIORITY_HIGH_ACCURACY);

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!= PackageManager.PERMISSION_GRANTED){

            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient,locationRequest,this);


    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(18));

        /*String userID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        GeoFire geoFireAvailability = new GeoFire(HospitalRef);
        geoFireAvailability.setLocation(userID, new GeoLocation(location.getLatitude(), location.getLongitude()), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {

            }
        });


        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        */







    }
    protected synchronized void buildGoogleApiClient()
    {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }




}