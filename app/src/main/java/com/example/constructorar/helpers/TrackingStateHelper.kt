package com.example.constructorar.helpers

import android.app.Activity
import android.view.WindowManager
import com.example.constructorar.R
import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

/*
* Copyright 2019 Google LLC
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
/** Gets human readibly tracking failure reasons and suggested actions.  */
class TrackingStateHelper(private val activity: Activity) {
    private var previousTrackingState: TrackingState? = null

    /** Keep the screen unlocked while tracking, but allow it to lock when tracking stops.  */
    fun updateKeepScreenOnFlag(trackingState: TrackingState) {
        if (trackingState == previousTrackingState) {
            return
        }
        previousTrackingState = trackingState
        when (trackingState) {
            TrackingState.PAUSED, TrackingState.STOPPED -> activity.runOnUiThread {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            TrackingState.TRACKING -> activity.runOnUiThread {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    companion object {

        fun getTrackingFailureReasonString(camera: Camera): Int {
            return when (camera.trackingFailureReason) {
                TrackingFailureReason.NONE -> R.string.empty_string
                TrackingFailureReason.BAD_STATE -> R.string.BAD_STATE_MESSAGE
                TrackingFailureReason.INSUFFICIENT_LIGHT -> R.string.INSUFFICIENT_LIGHT_MESSAGE
                TrackingFailureReason.EXCESSIVE_MOTION -> R.string.EXCESSIVE_MOTION_MESSAGE
                TrackingFailureReason.INSUFFICIENT_FEATURES -> R.string.INSUFFICIENT_FEATURES_MESSAGE
                TrackingFailureReason.CAMERA_UNAVAILABLE -> R.string.CAMERA_UNAVAILABLE_MESSAGE
                else -> R.string.Unknown_tracking_state_failure
            }
        }
    }
}