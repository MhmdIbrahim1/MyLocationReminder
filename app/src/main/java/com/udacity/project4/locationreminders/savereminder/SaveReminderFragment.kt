package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit


class SaveReminderFragment : BaseFragment() {
    private lateinit var reminderDataItem: ReminderDataItem
    //Get the view model this time as a single to be shared with the another fragment
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSaveReminderBinding
    lateinit var geofencingClient: GeofencingClient
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
    private val runningOnQOrLater = android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.Q

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        setDisplayHomeAsUpEnabled(true)

        binding.viewModel = _viewModel

        _viewModel.savedReminder.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                checkingPermissionsAndStartGeofencing()
            }
        })

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this
        binding.selectLocation.setOnClickListener {
            //            Navigate to another fragment to get the user location
            _viewModel.navigationCommand.value =
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        geofencingClient = LocationServices.getGeofencingClient(requireActivity())

        binding.saveReminder.setOnClickListener {
            reminderDataItem = ReminderDataItem(
                _viewModel.reminderTitle.value,
                _viewModel.reminderDescription.value,
                _viewModel.reminderSelectedLocationStr.value,
                _viewModel.latitude.value,
                _viewModel.longitude.value
            )
            if (_viewModel.validateEnteredData(reminderDataItem)) {
                checkingPermissionsAndStartGeofencing()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofenceForClue() {
        val geofence = Geofence.Builder()
            .setRequestId(reminderDataItem.id)
            .setCircularRegion(
                reminderDataItem.latitude!!,
                reminderDataItem.longitude!!,
                100f
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
            addOnSuccessListener {
                _viewModel.validateAndSaveReminder(reminderDataItem)
            }
            addOnFailureListener {
                _viewModel.showErrorMessage.postValue(getString(R.string.error_adding_geofence))
            }
        }
    }

    /*
       *  Determines whether the app has the appropriate permissions across Android 10+ and all other
       *  Android versions.
       */
    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {

        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ))
        val backgroundPermissionApproved =
            if (runningOnQOrLater) {
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved

    }

    @TargetApi(29)
    private fun requestingForegroundAndBackgroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved())
            return
        //The permissionsArray contains the permissions that are going to be requested. Initially, add ACCESS_FINE_LOCATION since that will be needed on all API levels.
        var permissionsArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        //     //Below it, you will need a resultCode. The code will be different depending on if the device is running Q or later and will inform
//     us if you need to check for one permission (fine location) or multiple permissions (fine and background location) when the user returns from the
//     permission request screen. Add a when statement to check the version running and assign result code to
//     REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE if the device is running Q or later and
//     REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE if not.
        val resultCode = when {
            runningOnQOrLater -> {
                permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            }
            else -> REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
        }
        requestPermissions(
            permissionsArray,
            resultCode
        )
    }

    /**
     * check if these below permissions is granted or not
     * * ACCESS_FINE_LOCATION
     * * ACCESS_BACKGROUND_LOCATION
     */

    private fun checkingPermissionsAndStartGeofencing() {
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkingDeviceLocationSettingsAndStartGeofence()
        } else {
            requestingForegroundAndBackgroundLocationPermissions()
        }
    }


    /*
     * In all cases, we need to have the location permission.  On Android 10+ (Q) we need to have
     * the background permission as well.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Permissions can be denied in a few ways:
        //If the grantResults array is empty, then the interaction was interrupted and the permission request was cancelled.
        //If the grantResults array’s value at the LOCATION_PERMISSION_INDEX has a PERMISSION_DENIED it means that the user denied foreground permissions.
        //If the request code equals REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE and the BACKGROUND_LOCATION_PERMISSION_INDEX is denied it means that the device is running API 29 or above and that background permissions were denied.

        if (
            grantResults.isEmpty() ||
            grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED ||
            (requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE &&
                    grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] ==
                    PackageManager.PERMISSION_DENIED)
        ) {
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                R.string.permission_denied_explanation,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.settings) {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }.show()
        } else {
            checkingDeviceLocationSettingsAndStartGeofence()
        }
    }

    private fun checkingDeviceLocationSettingsAndStartGeofence(
        resolve: Boolean = true
    ) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val settingsClient = LocationServices.getSettingsClient(requireContext())
        val locationSettingsResponseTask =
            settingsClient.checkLocationSettings(builder.build())
        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve) {
                startIntentSenderForResult(
                    exception.resolution.intentSender,
                    REQUEST_TURN_DEVICE_LOCATION_ON,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkingDeviceLocationSettingsAndStartGeofence()
                }.show()
            }
        }
        locationSettingsResponseTask.addOnCompleteListener {
            if (it.isSuccessful) {
                addGeofenceForClue()
            }
        }
    }

    // Replace the code below in the onActivityResult() method. After the user
    // chooses whether to accept or deny device location permissions, this checks
    // if the user has chosen to accept the permissions. If not, it will ask again.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
            checkingDeviceLocationSettingsAndStartGeofence(false)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        _viewModel.onClear()
    }

    companion object {
        internal const val ACTION_GEOFENCE_EVENT =
            "HuntMainActivity.treasureHunt.action.ACTION_GEOFENCE_EVENT"
    }

}


private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
private const val LOCATION_PERMISSION_INDEX = 0
private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1

