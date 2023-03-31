package com.b22712.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.jtransforms.fft.DoubleFFT_1D
import java.io.FileWriter
import java.io.IOException
import java.util.*
import java.util.stream.IntStream.range
import kotlin.math.*


class AudioSensor(val context: Context, val listener: AudioSensorListener) {

    companion object {
        const val LOGNAME: String = "AudioSensor"
        data class AudioData(
            val time: Long,
            val buffer: ShortArray,
            val fft: DoubleArray
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as AudioData

                if (!buffer.contentEquals(other.buffer)) return false

                return true
            }

            override fun hashCode(): Int {
                return buffer.contentHashCode()
            }
        }
    }

    var queue: LinkedList<AudioData> = LinkedList()
    private set
    fun resetQueue(){queue = LinkedList()}
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    lateinit var audioRecord: AudioRecord
    private var buffer:ShortArray = ShortArray(bufferSize)

    private var isRecoding: Boolean = false
    private var run: Boolean = false

    fun start() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord.startRecording()

        isRecoding = true
        if (!run) recodingFrequency(10)
    }

    // 8000Hzでこの処理を回すのはやばいんで指定period[ms]ごとに処理
    private fun recoding(period: Int) {
        val hnd0 = Handler(Looper.getMainLooper())
        run = true
        // こいつ(rnb0) が何回も呼ばれる
        val rnb0 = object : Runnable {
            override fun run() {
                // こっから自由
                // bufferReadResultは使わない．bufferにデータが入るのでこれを使う
                var bufferReadResult = audioRecord.read(buffer,0,bufferSize)
                // 振幅が出る
                Log.d(LOGNAME,"${buffer[100]}, ${buffer[300]}, ${buffer.size}")
                queue.add(AudioData(System.currentTimeMillis(), buffer.clone(), doubleArrayOf()))
                listener.getChangeSensor(AudioData(
                    System.currentTimeMillis(),
                    buffer,
                    doubleArrayOf()
                ))
                // stop用のフラグ
                if (run) {
                    // 指定時間後に自分自身を呼ぶ
                    hnd0.postDelayed(this, period.toLong())
                }
            }
        }
        // 初回の呼び出し
        hnd0.post(rnb0)
    }

    // デシベル変換したやつを出力
    private fun recodingDB(period: Int) {
        val hnd0 = Handler(Looper.getMainLooper())
        run = true
        val rnb0 = object : Runnable {
            override fun run() {
                var bufferReadResult = audioRecord.read(buffer,0,bufferSize)

                // 最大音量を解析
                val sum = buffer.sumOf { it.toDouble() * it.toDouble() }
                val rms = sqrt(sum / bufferSize)
                // デシベル変換
                val db = (20.0 * log10(rms)).toInt()
                Log.d(LOGNAME,"db = $db")

                if (run) {
                    hnd0.postDelayed(this, period.toLong())
                }
            }
        }
        hnd0.post(rnb0)
    }

    private fun recodingFrequency(period: Int){
        var volume: Int = 0 // デシベル変換後の値が入る
        val hnd0 = Handler(Looper.getMainLooper())
        run = true
        // こいつ(rnb0) が何回も呼ばれる
        val rnb0 = object : Runnable {
            override fun run() {
                // こっから自由
                var max: Short = 0
                // bufferReadResultは使わない．bufferにデータが入るのでこれを使う
                var bufferReadResult = audioRecord.read(buffer,0,bufferSize)
                val sum = buffer.sumOf { it.toDouble() * it.toDouble() }
                val rms = sqrt(sum / bufferSize)

                // 振幅が出る
                Log.d(LOGNAME,"${buffer[100]}, ${buffer[300]}, ${buffer.size}")

                // 最大振幅をデシベル変換する(多分よくない)
                for (num in buffer) {
                    if (max < num) max = num
                }
                // デシベル変換
                val db = (20.0 * log10(rms)).toInt()
                Log.d(LOGNAME,"db = $db")

                volume = (20* log10(max.toDouble())).toInt()
                if (volume < 0) {volume = 0}
                Log.d(LOGNAME,"volume = $volume")

                // FFT
                val fft = DoubleFFT_1D(bufferSize.toLong())
                val fftBuffer = DoubleArray(bufferSize * 2)
                val doubleBuffer: DoubleArray = buffer.map { it.toDouble() }.toDoubleArray()
                System.arraycopy(doubleBuffer, 0, fftBuffer, 0, bufferSize)
                fft.realForward(fftBuffer)

//                Log.d(LOGNAME, "fftBuffer = ${fftBuffer.toList()}")
                Log.d(LOGNAME, "fftBuffer.size = ${fftBuffer.size}")

                queue.add(AudioData(System.currentTimeMillis(), buffer.clone(), fftBuffer))

                //解析
//                val targetFrequency = 10000 // 特定の周波数（Hz）
//                val index = (targetFrequency * fft.size / sampleRate).toInt()
//                val amplitude = sqrt((fft[index] * fft[index] + fft[index + 1] * fft[index + 1]).toDouble())

                //音量が最大の周波数の解析
                var maxAmplitude = 0.0
                var maxIndex = 0
                for(index in range(0, fftBuffer.size-1)){
                    val tmp = sqrt((fftBuffer[index] * fftBuffer[index] + fftBuffer[index + 1] * fftBuffer[index + 1]))
                    if (maxAmplitude < tmp){
                            maxAmplitude = tmp
                            maxIndex = index
                    }
                }
                val maxFrequency: Int = (maxIndex * sampleRate / fftBuffer.size)
                Log.d(LOGNAME, "maxFrequency = $maxFrequency")

                listener.getChangeSensor(AudioData(
                    System.currentTimeMillis(),
                    buffer,
                    fftBuffer
                ))
                // stop用のフラグ
                if (run) {
                    // 指定時間後に自分自身を呼ぶ
                    hnd0.postDelayed(this, period.toLong())
                }
            }
        }
        // 初回の呼び出し
        hnd0.post(rnb0)
    }

    fun stop() {
        run = false
    }

    fun csvWriter(path: String, fileName: String): Boolean {
        //CSVファイルの書き出し
        try{
            //書込み先指定
            val writer = FileWriter("${path}/${fileName}-sound.csv")

            //書き込み準備
            val csvPrinter = CSVPrinter(
                writer, CSVFormat.DEFAULT
                    .withHeader(
                        "time",
                        "buffer",
                        "fft"
                    )
            )
            //書き込み開始
            for(data in queue){
                //データ保存
                csvPrinter.printRecord(
                    data.time.toString(),
                    data.buffer.toList().toString(),
                    data.fft.toList().toString()
                )
            }
            //データ保存の終了処理
            csvPrinter.flush()
            csvPrinter.close()
            return true
        }catch (e: IOException){
            //エラー処理
            Log.d(LOGNAME, "${e}:${e.message!!}")
            return false
        }
    }

    interface AudioSensorListener{
        fun getChangeSensor(data: AudioData)
    }

}