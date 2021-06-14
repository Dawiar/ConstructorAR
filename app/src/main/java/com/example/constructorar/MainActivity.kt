package com.example.constructorar


import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.constructorar.helpers.SnackbarHelper
import com.example.constructorar.helpers.TrackingStateHelper
import com.google.ar.core.*
import com.google.ar.core.ArCoreApk.InstallStatus
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), BaseArFragment.OnTapArPlaneListener {

    private val TAG: String = MainActivity::class.java.simpleName

    private val SEARCHING_PLANE_MESSAGE = "Searching for surfaces..."
    private val WAITING_FOR_TAP_MESSAGE = "Tap on a surface "

    private val refDistance = listOf(9.0, 30.5)

    private val augImagesNames = listOf(listOf("frame.png", "second.png"), listOf("frame.png", "earth.jpg"))

    private val tooltips = listOf("Do as instruction shows. Scan QR-codes on front of printer and on back of printer's access door to verify this step",
            "Do as instruction shows. Scan QR-codes on front of printer and on cartridge")

    private val STEPS = 2
    private var currStep = 0

    private val messageSnackbarHelper = SnackbarHelper()

    private var installRequested = true

    private var arFragment: ArFragment? = null

    private var shouldConfigureSession = false

    private val trackingStateHelper = TrackingStateHelper(this)

    private var models: Renderable? = null

    private lateinit var model: TransformableNode

    private var isModelRendered = false

    private var session: Session? = null

    private var arSceneView: ArSceneView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment.id == R.id.arFragment) {
                arFragment = fragment as ArFragment
                arFragment!!.setOnTapArPlaneListener(this@MainActivity)
            }
        }
        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                supportFragmentManager.beginTransaction()
                        .add(R.id.arFragment, ArFragment::class.java, null)
                        .commit()
            }
        }
        loadModels(currStep)
        messageSnackbarHelper.setMaxLines(3)
    }

    /** Every time new image is processed by ARCore and ready, this method is called */
    private fun onUpdateFrame(frameTime: FrameTime) {
        if (currStep >= STEPS) {
            messageSnackbarHelper.showMessage(this, "Congratulations on completing instruction!")
            return
        }
        val frame = arFragment?.arSceneView?.arFrame
        val updatedAugmentedImages = frame?.getUpdatedTrackables(AugmentedImage::class.java)
        if (updatedAugmentedImages != null) {
            for (augImage in updatedAugmentedImages) {
                Log.i("augImage ${augImage.name} coords: ", augImage.centerPose.toString())
            }
            if (updatedAugmentedImages.filter { augImagesNames[currStep].contains(it.name) }.size == 2) {
                val distance = calculateDistanceInCM(updatedAugmentedImages.first(), updatedAugmentedImages.last())
                if (abs(distance - refDistance[currStep]) < refDistance[currStep] * 0.15) {
                    currStep++
                    loadModels(currStep)
                    return
                }
            }
        }

        val camera = frame?.camera
        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera!!.trackingState)
        // Show a message based on whether tracking has failed, if planes are detected
        // or current tooltip message if the user has placed instruction
        val message = if (!isModelRendered) {
            if (camera.trackingState == TrackingState.PAUSED) {
                if (camera.trackingFailureReason == TrackingFailureReason.NONE)
                    SEARCHING_PLANE_MESSAGE
                else
                    TrackingStateHelper.getTrackingFailureReasonString(camera)
            } else if (hasTrackingPlane()) {
                WAITING_FOR_TAP_MESSAGE
            } else {
                SEARCHING_PLANE_MESSAGE
            }
        } else {
            tooltips[currStep]
        }
        messageSnackbarHelper.showMessage(this, message)
    }

    fun calculateDistanceInCM(first: AugmentedImage, second: AugmentedImage): Float {
        val first = first.centerPose
        val second = second.centerPose
        val distanceX = first.tx() - second.tx()
        val distanceY = first.ty() - second.ty()
        val distanceZ = first.tz() - second.tz()
        val distance = sqrt(distanceX.pow(2) + distanceY.pow(2) + distanceZ.pow(2))
        val distanceInCm = distance * 1000 / 10f
        Log.i(TAG, "Distance between 2 images is $distanceInCm cm")
        return distanceInCm
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            arSceneView?.pause()
            session?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            arSceneView = arFragment?.arSceneView
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    InstallStatus.INSTALLED -> {
                    }
                }

                session = Session(this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: Exception) {
                message = "This device does not support AR"
                exception = e
            }
            if (message != null) {
                Log.d(TAG, message)
                Log.e(TAG, "Exception creating session", exception)
                return
            }
            shouldConfigureSession = true
        }

        if (shouldConfigureSession) {
            configureSession()
            shouldConfigureSession = false
            arSceneView?.setupSession(session)
        }

        // Note that order matters the reverse OnPause() order applies here.
        try {
            session?.resume()
            configureSession()
            arSceneView?.resume()
        } catch (e: CameraNotAvailableException) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.")
            session = null
            return
        }
    }

    private fun configureSession() {
        val config = Config(session)
        if (!setupAugmentedImageDatabase(config)) {
            Log.d(TAG, "Could not setup augmented image database")
        }

        /** 30fps camera configuration enabling auto focus on Pixel 3
        //    val filter = CameraConfigFilter(session)
        //    filter.targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)
        //   val cameraConfigList = session?.getSupportedCameraConfigs(filter)
        //    session?.cameraConfig = cameraConfigList?.get(0)
         **/

        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        config.focusMode = Config.FocusMode.AUTO
        session?.configure(config)
    }

    fun loadModels(number: Int) {
        val weakActivity = WeakReference(this)
        ModelRenderable.builder()
                .setSource(
                        this,
                        Uri.parse("models/model$number.glb")
                )
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept { model: ModelRenderable? ->
                    val activity = weakActivity.get()
                    if (activity != null) {
                        activity.models = model
                        activity.model.renderable = model
                        activity.model.renderableInstance?.animate(true)?.start()
                    }
                }
                .exceptionally {
                    Toast.makeText(this, "Unable to load model", Toast.LENGTH_LONG).show()
                    null
                }
    }


    override fun onTapPlane(
            hitResult: HitResult,
            plane: Plane,
            motionEvent: MotionEvent
    ) {
        if (models == null) {
            Toast.makeText(this, "Loading...", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the Anchor.
        val anchor = hitResult.createAnchor()
        val anchorNode =
                AnchorNode(anchor)
        anchorNode.setParent(arFragment?.arSceneView?.scene)

        if (!isModelRendered) {
            // Create the transformable model and add it to the anchor.
            model = TransformableNode(arFragment?.transformationSystem)
            model.setParent(anchorNode)
            model.renderable = this.models
            model.renderableInstance?.animate(true)?.start()
            model.select()
            isModelRendered = true
        } else {
            model.setParent(anchorNode)
        }

        arFragment?.planeDiscoveryController?.hide();
        arFragment?.planeDiscoveryController?.setInstructionView(null);
        arFragment?.arSceneView?.planeRenderer?.isEnabled = false;
    }


    /** Loads augmented image's database  */
    private fun setupAugmentedImageDatabase(config: Config): Boolean {
        var augmentedImageDatabase: AugmentedImageDatabase

        // load a pre-existing augmented image database.
        try {
            assets.open("sample.imgdb").use { `is` ->
                augmentedImageDatabase = AugmentedImageDatabase.deserialize(
                        session,
                        `is`
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "IO exception loading augmented image database.", e)
            return false
        }
        config.augmentedImageDatabase = augmentedImageDatabase
        return true
    }

    /** Checks if we detected at least one plane.  */
    private fun hasTrackingPlane(): Boolean {
        for (plane in arSceneView?.session!!.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState == TrackingState.TRACKING) {
                return true
            }
        }
        return false
    }
}

