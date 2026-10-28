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
        return startRecordingAsync(soruId, onAutoStop, blocking = false)
    }

    /**
     * PERF: MediaRecorder.prepare() ana iplikte blokluyordu. Varsayılan
     * async yolda IO'da hazırlanır; çağrı anında true döner, hata olursa
     * kayıt sessizce iptal edilir. Testler/senkron çağrılar için
     * `blocking=true` eski davranışı korur.
     */
    fun startRecordingAsync(
        soruId: Long,
        onAutoStop: (String) -> Unit,
        blocking: Boolean = false
    ): Boolean {
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
            _isRecording.value = true
            _recordingSeconds.value = 0

            if (blocking) {
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
                beginRecordingTimer(onAutoStop)
            } else {
                // PERF: prepare()+start() IO'da; ana iplik bloklanmaz.
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        if (!_isRecording.value || mediaRecorder !== recorder) {
                            try { recorder.release() } catch (_: Exception) {}
                            try { outputFile.delete() } catch (_: Exception) {}
                            return@launch
                        }
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
                        if (!_isRecording.value || mediaRecorder !== recorder) {
                            try { recorder.stop() } catch (_: Exception) {}
                            try { recorder.release() } catch (_: Exception) {}
                            try { outputFile.delete() } catch (_: Exception) {}
                            return@launch
                        }
                        beginRecordingTimer(onAutoStop)
                    } catch (e: Exception) {
                        Log.e(TAG, "Kayıt başlatılamadı", e)
                        try { recorder.release() } catch (_: Exception) {}
                        try { outputFile.delete() } catch (_: Exception) {}
                        cancelRecording()
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Kayıt başlatılamadı", e)
            cancelRecording()
            return false
        }
    }

    private fun beginRecordingTimer(onAutoStop: (String) -> Unit) {
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

            // PERF: prepare() ana iplikte blokluyordu; prepareAsync + IO.
            val player = MediaPlayer()
            player.setDataSource(actualFile.absolutePath)
            player.setOnPreparedListener { p ->
                if (mediaPlayer !== p) {
                    try { p.release() } catch (_: Exception) {}
                    return@setOnPreparedListener
                }
                try {
                    _durationMs.value = p.duration
                    _currentPositionMs.value = 0
                    p.start()
                    _isPlaying.value = true
                    progressJob?.cancel()
                    progressJob = coroutineScope.launch(Dispatchers.Main) {
                        while (isActive && _isPlaying.value) {
                            mediaPlayer?.let { pl ->
                                if (pl.isPlaying) {
                                    _currentPositionMs.value = pl.currentPosition
                                }
                            }
                            delay(200)
                        }
                    }
                } catch (_: Exception) {
                    stopPlaying()
                }
            }
            player.setOnCompletionListener {
                // PERF: Bitişte oynatıcı serbest bırakılır (önceden release yoktu).
                _isPlaying.value = false
                _currentPositionMs.value = 0
                progressJob?.cancel()
                progressJob = null
                try { player.release() } catch (_: Exception) {}
                if (mediaPlayer === player) mediaPlayer = null
            }
            player.setOnErrorListener { _, _, _ ->
                stopPlaying()
                true
            }
            mediaPlayer = player
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (mediaPlayer !== player) {
                        try { player.release() } catch (_: Exception) {}
                        return@launch
                    }
                    player.prepareAsync()
                } catch (_: Exception) {
                    stopPlaying()
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
