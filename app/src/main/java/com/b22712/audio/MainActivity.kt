package com.b22712.audio

import android.Manifest
import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.b22712.audio.databinding.ActivityMainBinding
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class MainActivity : AppCompatActivity(), AudioSensor.AudioSensorListener {
    lateinit var binding: ActivityMainBinding
    lateinit var externalFilePath: String
    lateinit var audioSensor: AudioSensor

    var queue: LinkedList<AudioSensor.Companion.AudioData> = LinkedList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalFilePath = this.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO
        )

        if (!EasyPermissions.hasPermissions(this, *permissions)) {
            // パーミッションが許可されていない時の処理
            EasyPermissions.requestPermissions(this, "パーミッションに関する説明", 0, *permissions)
        }

        audioSensor = AudioSensor(this, this)
        audioSensor.start()

        binding.csvButton.setOnClickListener {
            audioSensor.csvWriter(externalFilePath, "testFFT").let {
                when(it){
                    true ->{
                        Toast.makeText(
                            this,
                            "csv success",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    false -> {
                        Toast.makeText(
                            this,
                            "csv defeat",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d("csvWrite","defeat")
                    }
                }
            }
        }
        binding.csvResetButton.setOnClickListener {
            audioSensor.resetQueue()
            Toast.makeText(
                this,
                "queue reset",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        audioSensor.apply {
            audioRecord.release()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun getChangeSensor(data: AudioSensor.Companion.AudioData) {
        //queue.add(data)
        binding.textView.text = "${data.buffer[100]}, ${data.buffer[300]}"
    }
}