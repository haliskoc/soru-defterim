package com.sorudefteri.ui.ortak.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Sorular için 60 saniyeye kadar sesli not/çözüm kaydetme ve oynatma yöneticisi.
 */
class AudioMemoManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioMemoManager"
        const val MAX_RECORDING_SECONDS = 60
    }

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFile: File? = null
    private var progressJob: Job? = null
    private var recordingTimerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    fun startRecording(soruId: Long, onAutoStop: (String) -> Unit): Boolean {
        try {
            stopPlaying()

            val dir = File(context.filesDir, "audio_memos").apply { mkdirs() }
            val outputFile = File(dir, "memo_${soruId}_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            // prepare()/start() patlarsa referans mediaRecorder'a atanmadığı için
            // cancelRecording() onu göremeyip native kaynak sızdırırdı.
            mediaRecorder = recorder
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            _isRecording.value = true
            _recordingSeconds.value = 0

            recordingTimerJob?.cancel()
            recordingTimerJob = coroutineScope.launch(Dispatchers.Main) {
                while (isActive && _isRecording.value) {
                    delay(1000)
                    _recordingSeconds.value += 1
                    if (_recordingSeconds.value >= MAX_RECORDING_SECONDS) {
                        // Bu job KENDİSİNİ çağırdığı için stopRecording() içindeki
                        // recordingTimerJob?.cancel() mevcut coroutine'ı iptal ediyordu.
                        // Kaydı iptal etmeden bitirip callback'i güvenle çağırıyoruz.
                        val path = finishRecording()
                        if (path != null) {
                            onAutoStop(path)
                        }
                        break
                    }
                }
                // Kendi job'ımızı temizle (artık cancel() çağırıp kendimizi iptal etmeyelim).
                if (recordingTimerJob === currentCoroutineContext()[Job]) {
                    recordingTimerJob = null
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Kayıt başlatılamadı", e)
            cancelRecording()
            return false
        }
    }

    fun stopRecording(): String? {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        return finishRecording()
    }

    /**
     * Kayıt cihazını kapatır ve kaydedilen dosyanın yolunu döndürür.
     * Zamanlayıcı job'ına dokunmaz; hem [stopRecording] hem 60sn otomatik
     * durdurma yolu tarafından güvenle çağrılabilir.
     */
    private fun finishRecording(): String? {
        return try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
            mediaRecorder = null
            _isRecording.value = false
            _recordingSeconds.value = 0

            // Kayıt TAMAMLANDI: dosya artık kalıcıdır. Dosya referansını temizle ki
            // ekrandan çıkarken release() -> cancelRecording() bu dosyayı SİLMESİN.
            val savedPath = currentRecordingFile?.absolutePath
            currentRecordingFile = null
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Kayıt durdurulamadı", e)
            mediaRecorder = null
            _isRecording.value = false
            // Hata durumunda kayıt geçersiz sayılır, dosya artıkta bırakılır.
            try { currentRecordingFile?.delete() } catch (_: Exception) {}
            currentRecordingFile = null
            null
        }
    }

    fun cancelRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        try {
            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        _isRecording.value = false
        _recordingSeconds.value = 0
        try {
            currentRecordingFile?.delete()
        } catch (_: Exception) {}
        currentRecordingFile = null
    }

    fun startPlaying(filePath: String) {
        try {
            stopPlaying()

            val file = File(filePath)
            val actualFile = if (file.exists()) file else File(context.filesDir, filePath)
            if (!actualFile.exists()) return

            mediaPlayer = MediaPlayer().apply {
                setDataSource(actualFile.absolutePath)
                prepare()
                _durationMs.value = duration
                _currentPositionMs.value = 0
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0
                    progressJob?.cancel()
                }
                start()
            }

            _isPlaying.value = true

            progressJob?.cancel()
            progressJob = coroutineScope.launch(Dispatchers.Main) {
                while (isActive && _isPlaying.value) {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _currentPositionMs.value = player.currentPosition
                        }
                    }
                    delay(200)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ses oynatılamadı", e)
            stopPlaying()
        }
    }

    fun togglePlayPause(filePath: String) {
        if (_isPlaying.value) {
            pausePlaying()
        } else {
            if (mediaPlayer != null) {
                resumePlaying()
            } else {
                startPlaying(filePath)
            }
        }
    }

    fun pausePlaying() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (_: Exception) {}
    }

    fun resumePlaying() {
        try {
            mediaPlayer?.start()
            _isPlaying.value = true
        } catch (_: Exception) {}
    }

    fun stopPlaying() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        } catch (_: Exception) {}
    }

    /**
     * Ekran kapanırken çağrılır.
     *
     * DİKKAT: Artık tamamlanmış kayıtları SİLMEZ. [stopRecording] dosya referansını
     * zaten temizlediği için burada yalnızca yarım kalmış kayıtlar (currentRecordingFile
     * dolu olan) temizlenir. Daha önce burada `currentRecordingFile?.delete()`
     * çağrıldığı için, kaydedilmiş ses dosyası ekrandan çıkıldığı anda siliniyor ve
     * veritabanındaki audioPath ölü bir dosyayı gösteriyordu.
     */
    fun release() {
        cancelRecording()   // yalnızca tamamlanmamış kayıtları temizler
        stopPlaying()
    }
}
