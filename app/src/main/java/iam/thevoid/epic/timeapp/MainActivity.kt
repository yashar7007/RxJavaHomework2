package iam.thevoid.epic.timeapp

import android.annotation.SuppressLint
import android.graphics.Insets.add
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.text.format.DateUtils
import android.util.Log
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.PackageManagerCompat.LOG_TAG
import com.google.android.material.textfield.TextInputEditText
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.internal.util.BackpressureHelper.add
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.schedulers.Schedulers.io
import java.sql.Time
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

// Дан экран с готовой разметкой
// Реализовать при помощи RxJava
// 1) Отображение часов,начинают работать при старте, показывают время в любом удобном формате.
//    Как пример можно использовать формат из разметки
// 2) Таймер
//    а) Пользователь вводит количество секунд в поле
//    б) По нажатию на "Старт" начинается обратный отсчёт
//    в) (не обязательно) По окончании таймер каким либо образом сигнализирует об окончании,
//       например область таймера вспыхивает ярким цветом
// 3) Секундомер
//    а) Пользователь нажимает на "Старт", начинается отсчёт времени. В соответствующие текстовые
//       поля выводится количество прошедшего времени (отдельно время с точностью до секунд,
//       отдельно миллисекунды)
//    б) По нажатию на паузу отсчёт времени останавливается. Кнопка "Пауза" превращается в кнопку
//       "Сброс".
//    в) По нажатию на "Сброс" отстчёт времени сбрасывается в 0. "Старт" продолжает приостановленный
//       отсчёт
//    г) (не обязательно) Можно сделать изменение состояние кнопки "Старт" на "Продолжить" для
//       состояния паузы

class MainActivity : AppCompatActivity() {

    // Часы:
    private lateinit var clockText: TextView

    // Обратный отсчёт
    private lateinit var countdownText: TextView
    private lateinit var countdownSecondsEditText: EditText
    private lateinit var countdownStartButton: Button

    // Секундомер
    private lateinit var stopwatchText: TextView
    private lateinit var stopwatchMillisText: TextView
    private lateinit var stopwatchStartButton: Button
    private lateinit var stopwatchEndButton: Button

    private var disposableStopwatch: Disposable? = null
    private var cur_ms: Long = 0L

    // состояние обратного отсчета
    enum class StopwatchState {
        STOPPED, PAUSED, RUNNING
    }

    private var stopwatchState: StopwatchState = StopwatchState.STOPPED

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


//часы
        clockText = findViewById(R.id.clockText)

        Clock_fun()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                clockText.text = it
            }


//таймер
        countdownText = findViewById(R.id.countdownText)
        countdownSecondsEditText = findViewById(R.id.countdownEditText)
        countdownStartButton = findViewById(R.id.countdownStartButton)
        var disposableCountdownTimer: Disposable? = null

        fun disposeCountdown() {
            disposableCountdownTimer?.dispose()
            disposableCountdownTimer = null
        }

        countdownStartButton.setOnClickListener {
            disposeCountdown()
            var total_time: Long = countdownSecondsEditText.text.toString().toLong()
            timer(total_time)
            disposableCountdownTimer = timer(total_time)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { it ->
                    countdownText.text = it
                }
        }

//секундомер

        stopwatchText = findViewById(R.id.stopwatchText)
        stopwatchMillisText = findViewById(R.id.stopwatchMillisText)
        stopwatchStartButton = findViewById(R.id.stopwatchStartButton)
        stopwatchEndButton = findViewById(R.id.stopwatchEndButton)

    }

    @SuppressLint("SetTextI18n")
    override fun onStop() {
        super.onStop()
        if (stopwatchState == StopwatchState.RUNNING) {
            disposeStopwatch()
            stopwatchState = StopwatchState.PAUSED
            stopwatchStartButton.text = "Resume"
            stopwatchEndButton.text = "Stop"
        }
    }

    private fun disposeStopwatch() {
        disposableStopwatch?.dispose()
        disposableStopwatch = null
    }

    private fun setStopwatchFirstTime() {
        val texts = stopwatch_format(0L)
        stopwatchText.text = texts.first
        stopwatchMillisText.text = texts.second
    }

    @SuppressLint("SetTextI18n")
    override fun onStart() {
        super.onStart()

        //секундомер
        stopwatchStartButton.setOnClickListener {
            when (stopwatchState) {
                StopwatchState.PAUSED, StopwatchState.STOPPED -> {
                    stopwatchState = StopwatchState.RUNNING
                    stopwatchStartButton.text = "Start"
                    stopwatchEndButton.text = "Pause"
                }
                StopwatchState.RUNNING -> {
                    cur_ms = 0L
                }
            }

            disposeStopwatch()
            disposableStopwatch = stopwatchtimer(cur_ms)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { item ->
                    stopwatchText.text = item.first
                    stopwatchMillisText.text = item.second
                    cur_ms = item.third
                }
        }

        stopwatchEndButton.setOnClickListener {
            disposeStopwatch()
            when (stopwatchState) {
                StopwatchState.RUNNING -> {
                    stopwatchState = StopwatchState.PAUSED
                    stopwatchStartButton.text = "Resume"
                    stopwatchEndButton.text = "Stop"
                }
                StopwatchState.PAUSED -> {
                    stopwatchState = StopwatchState.STOPPED
                   cur_ms = 0L
                    stopwatchStartButton.text = "Start"
                    stopwatchEndButton.text = "Pause"

                    setStopwatchFirstTime()
                }
                else -> {
                    Log.d(LOG_TAG, stopwatchState.toString())
                }
            }
        }

    }


//часы

    @RequiresApi(Build.VERSION_CODES.O)
    fun Clock_fun(): Observable<String> {
        val format_time = DateTimeFormatter.ofPattern("HH:mm:ss")
        fun time(): String? {
            return LocalDateTime.now().format(format_time)
        }
        return Observable.interval(1, TimeUnit.SECONDS).map { time() }
    }


//таймер

    var DisposibleTimer: Disposable? = null

    @RequiresApi(Build.VERSION_CODES.O)
    fun timer(total_time: Long): @NonNull Observable<String> {
        fun countdownTime(total_time: Long, t: Long): String? {
            return DateUtils.formatElapsedTime(total_time - t)
        }
        return Observable.interval(1, TimeUnit.SECONDS)

            .takeWhile { it <= total_time.toLong() }
            .map { t -> countdownTime(total_time.toLong(), t) }
    }


//секундомер

    // Секундомер - форматирование
    private fun stopwatch_format(millisec: Long): Triple<String, String, Long> {
        val hours = TimeUnit.MILLISECONDS.toHours(millisec)
        val mins = TimeUnit.MILLISECONDS.toMinutes(millisec) - TimeUnit.HOURS.toMinutes(
            TimeUnit.MILLISECONDS.toHours(millisec)
        )
        val sec = TimeUnit.MILLISECONDS.toSeconds(millisec) - TimeUnit.MINUTES.toSeconds(
            TimeUnit.MILLISECONDS.toMinutes(millisec)
        )
        val millisecend = millisec - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(millisec))

        val hms = String.format("%02d:%02d:%02d", hours, mins, sec)
        val ms = String.format("%02d", millisecend)
        return Triple(hms, ms, millisec)
    }

    // Секундомер - счетчик
    private fun stopwatchtimer(cur_ms: Long): Observable<Triple<String, String, Long>> {
        return Observable.interval(1, TimeUnit.MILLISECONDS)
            .map { t -> stopwatch_format(t * 1 + cur_ms) }
    }

    companion object {
        private val LOG_TAG = MainActivity::class.simpleName
    }
}






