package com.xdoubleu.watchyourlanguage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.text.format.DateFormat.is24HourFormat
import android.view.SurfaceHolder
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.*


/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

class WatchYourLanguage   : CanvasWatchFaceService(){

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: WatchYourLanguage.Engine) : Handler() {
        private val mWeakReference: WeakReference<WatchYourLanguage.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false

        private val thinSFOP = resources.getFont(R.font.slate_thin)
        private val regSFOP = resources.getFont(R.font.slate_reg)

        private lateinit var textThinWhite: Paint
        private lateinit var textBoldWhite: Paint
        private lateinit var textThinWhiteDate: Paint

        /*WatchFace Data*/

        /*Time*/
        private val timeWords = arrayOf("Twelve","One","Two","Three","Four","Five","Six","Seven",
                "Eight","Nine","Ten","Eleven","Twelve","Thirteen","Fourteen","Fifteen","Sixteen",
                "Seventeen","Eighteen","Nineteen", "Twenty","Twenty One","Twenty Two","Twenty Three",
                "Twenty Four","Twenty Five","Twenty Six","Twenty Seven","Twenty Eight","Twenty Nine",
                "Thirty", "Thirty One", "Thirty Two", "Thirty Three", "Thirty Four", "Thirty Five",
                "Thirty Six", "Thirty Seven", "Thirty Eight", "Thirty Nine", "Forty", "Forty One",
                "Forty Two", "Forty Three", "Forty Four", "Forty Five", "Forty Six", "Forty Seven",
                "Forty Eight", "Forty Nine", "Fifty", "Fifty One", "Fifty Two", "Fifty Three",
                "Fifty Four", "Fifty Five", "Fifty Six", "Fifty Seven", "Fifty Eight", "Fifty Nine")
        private var hours = " "
        private var minutesInt = 0
        private var minutesString = " "

        /*Date*/
        private val days = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        private val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        private var dayOfWeek = " "
        private var day = " "
        private var month = " "

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()

                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            mCalendar = Calendar.getInstance()

            initializeTextColors()
        }

        private fun initializeTextColors() {
            textThinWhite = Paint().apply {
                color = Color.WHITE
                typeface = thinSFOP
                textSize = 55F
                isAntiAlias = true
            }

            textBoldWhite = Paint().apply {
                color = Color.WHITE
                typeface = regSFOP
                textSize = 55F
                isAntiAlias = true
            }

            textThinWhiteDate = Paint().apply {
                color = Color.WHITE
                typeface = thinSFOP
                textSize = 35F
                isAntiAlias = true
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                textBoldWhite.alpha = if (inMuteMode) 100 else 255
                textThinWhite.alpha = if (inMuteMode) 100 else 255
                textThinWhiteDate.alpha = if (inMuteMode) 100 else 255
                invalidate()
            }

        }

        private fun time(){
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            hours = timeWords[mCalendar.get(Calendar.HOUR)]
            minutesInt = mCalendar.get(Calendar.MINUTE)

            if(minutesInt == 0){
                minutesString = "O' Clock"
            }
            else if(minutesInt < 10){
                minutesString = "Oh "+timeWords[minutesInt]
            }
            else{
                minutesString = timeWords[minutesInt]
            }


            dayOfWeek = days[mCalendar.get(Calendar.DAY_OF_WEEK)-1]
            day = mCalendar.get(Calendar.DAY_OF_MONTH).toString()
            month = months[mCalendar.get(Calendar.MONTH)]

        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            /*Background*/
            canvas.drawColor(Color.BLACK)
            drawText(canvas)
        }

        private fun drawText(canvas: Canvas){
            /*Data*/
            time()

            canvas.drawText(
                    "It's",
                    50f,
                    130f,
                    textThinWhite)

            canvas.drawText(
                    hours,
                    50f,
                    200f,
                    textBoldWhite)

            canvas.drawText(
                    minutesString,
                    50f,
                    270f,
                    textBoldWhite)

            canvas.drawText(
                    "$dayOfWeek, $month $day",
                    55f,
                    330f,
                    textThinWhiteDate)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@WatchYourLanguage.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@WatchYourLanguage.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}


