package com.demo.backgrounderaser

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.huawei.hms.mlsdk.MLAnalyzerFactory
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.imgseg.MLImageSegmentation
import com.huawei.hms.mlsdk.imgseg.MLImageSegmentationAnalyzer
import com.huawei.hms.mlsdk.imgseg.MLImageSegmentationScene
import com.huawei.hms.mlsdk.imgseg.MLImageSegmentationSetting
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BE_MainActivity"
        private const val IMAGE_REQUEST_CODE = 58
        private const val BACKGROUND_REQUEST_CODE = 32
    }

    private lateinit var mAnalyzer: MLImageSegmentationAnalyzer

    private var mBackgroundFill: Bitmap? = null
    private var mSelectedBitmap: Bitmap? = null
    private var mProcessedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            init()
        else
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 0 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            init()
    }

    private fun init() {
        initView()
        createAnalyzer()
    }

    private fun initView() {
        buttonPickImage.setOnClickListener { getImage(IMAGE_REQUEST_CODE) }
        buttonSelectBackground.setOnClickListener { getImage(BACKGROUND_REQUEST_CODE) }
    }

    private fun createAnalyzer(): MLImageSegmentationAnalyzer {
        val analyzerSetting = MLImageSegmentationSetting.Factory()
            .setExact(true)
            .setAnalyzerType(MLImageSegmentationSetting.BODY_SEG)
            .setScene(MLImageSegmentationScene.FOREGROUND_ONLY)
            .create()

        return MLAnalyzerFactory.getInstance().getImageSegmentationAnalyzer(analyzerSetting).also {
            mAnalyzer = it
        }
    }

    private fun analyse(bitmap: Bitmap) {
        val mlFrame = MLFrame.fromBitmap(bitmap)
        mAnalyzer.asyncAnalyseFrame(mlFrame)
            .addOnSuccessListener {
                addSelectedBackground(it)
            }
            .addOnFailureListener {
                Log.e(TAG, "analyse -> asyncAnalyseFrame: ", it)
            }
    }

    private fun addSelectedBackground(mlImageSegmentation: MLImageSegmentation) {
        if (mBackgroundFill == null) {
            Toast.makeText(applicationContext, "Please select a background image!", Toast.LENGTH_SHORT).show()
        } else {

            var mutableBitmap = if (mBackgroundFill!!.isMutable) {
                mBackgroundFill
            } else {
                mBackgroundFill!!.copy(Bitmap.Config.ARGB_8888, true)
            }

            if (mutableBitmap != null) {

                /*
                 *  If background image size is different than our selected image,
                 *  we change our background image's size according to selected image.
                 */
                if (mutableBitmap.width != mlImageSegmentation.original.width ||
                        mutableBitmap.height != mlImageSegmentation.original.height) {
                    mutableBitmap = Bitmap.createScaledBitmap(
                        mutableBitmap,
                        mlImageSegmentation.original.width,
                        mlImageSegmentation.original.height,
                        false)
                }

                val canvas = mutableBitmap?.let { Canvas(it) }
                canvas?.drawBitmap(mlImageSegmentation.foreground, 0F, 0F, null)
                mProcessedBitmap = mutableBitmap
                ivProcessedBitmap.setImageBitmap(mProcessedBitmap)
            }
        }
    }

    private fun getImage(requestCode: Int) {
        Intent(Intent.ACTION_GET_CONTENT).also {
            it.type = "image/*"
            startActivityForResult(it, requestCode)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            data?.data?.also {

                when(requestCode) {
                    IMAGE_REQUEST_CODE -> {
                        mSelectedBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)

                        if (mSelectedBitmap != null) {
                            ivSelectedBitmap.setImageBitmap(mSelectedBitmap)
                            analyse(mSelectedBitmap!!)
                        }
                    }
                    BACKGROUND_REQUEST_CODE -> {
                        mBackgroundFill = MediaStore.Images.Media.getBitmap(contentResolver, it)

                        if (mBackgroundFill != null) {
                            ivBackgroundFill.setImageBitmap(mBackgroundFill)
                            mSelectedBitmap?.let { analyse(it) }
                        }
                    }
                }


            }
        }

    }
}