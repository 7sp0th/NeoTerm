package io.neoterm.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.content.WakefulBroadcastReceiver
import io.neoterm.R
import io.neoterm.backend.EmulatorDebug
import io.neoterm.backend.TerminalSession
import io.neoterm.frontend.ShellParameter
import io.neoterm.ui.term.NeoTermActivity
import io.neoterm.utils.TerminalUtils
import java.util.*


/**
 * @author kiva
 */

class NeoTermService : Service() {
    inner class NeoTermBinder : Binder() {
        var service = this@NeoTermService
    }

    private val neoTermBinder = NeoTermBinder()
    private val mTerminalSessions = ArrayList<TerminalSession>()
    private var mWakeLock: PowerManager.WakeLock? = null
    private var mWifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent): IBinder? {
        return neoTermBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action
        when (action) {
            ACTION_SERVICE_STOP -> {
                for (i in mTerminalSessions.indices)
                    mTerminalSessions[i].finishIfRunning()
                stopSelf()
            }

            ACTION_ACQUIRE_LOCK -> acquireLock()

            ACTION_RELEASE_LOCK -> releaseLock()
        }

        if (flags and Service.START_FLAG_REDELIVERY == 0) {
            // Service is started by WBR, not restarted by system, so release the WakeLock from WBR.
            WakefulBroadcastReceiver.completeWakefulIntent(intent)
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)

        for (i in mTerminalSessions.indices)
            mTerminalSessions[i].finishIfRunning()
        mTerminalSessions.clear()
    }

    val sessions: List<TerminalSession>
        get() = mTerminalSessions

    fun createTermSession(parameter: ShellParameter): TerminalSession {
        val session = TerminalUtils.createShellSession(this, parameter)
        mTerminalSessions.add(session)
        updateNotification()
        return session
    }

    fun removeTermSession(sessionToRemove: TerminalSession): Int {
        val indexOfRemoved = mTerminalSessions.indexOf(sessionToRemove)
        if (indexOfRemoved >= 0) {
            mTerminalSessions.removeAt(indexOfRemoved)
            updateNotification()
        }
        return indexOfRemoved
    }

    private fun updateNotification() {
        if (sessions.isNotEmpty()) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createNotification(): Notification {
        val notifyIntent = Intent(this, NeoTermActivity::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0)

        val sessionCount = mTerminalSessions.size
        var contentText = getString(R.string.service_status_text, sessionCount)

        val lockAcquired = mWakeLock != null
        if (lockAcquired) contentText += getString(R.string.service_lock_acquired)

        val builder = Notification.Builder(this)
        builder.setContentTitle(getText(R.string.app_name))
        builder.setContentText(contentText)
        builder.setSmallIcon(R.drawable.ic_terminal_running)
        builder.setContentIntent(pendingIntent)
        builder.setOngoing(true)
        builder.setShowWhen(false)
        builder.setColor(0xFF000000.toInt())

        builder.setPriority(if (lockAcquired) Notification.PRIORITY_HIGH else Notification.PRIORITY_MIN)

        val exitIntent = Intent(this, NeoTermService::class.java).setAction(ACTION_SERVICE_STOP)
        builder.addAction(android.R.drawable.ic_delete, getString(R.string.exit), PendingIntent.getService(this, 0, exitIntent, 0))

        val newWakeAction = if (lockAcquired) ACTION_RELEASE_LOCK else ACTION_ACQUIRE_LOCK
        val toggleWakeLockIntent = Intent(this, NeoTermService::class.java).setAction(newWakeAction)
        val actionTitle = getString(
                if (lockAcquired)
                    R.string.service_release_lock
                else
                    R.string.service_acquire_lock)
        val actionIcon = if (lockAcquired) android.R.drawable.ic_lock_idle_lock else android.R.drawable.ic_lock_lock
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(this, 0, toggleWakeLockIntent, 0))

        return builder.build()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLock() {
        if (mWakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, EmulatorDebug.LOG_TAG)
            mWakeLock!!.acquire()

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, EmulatorDebug.LOG_TAG)
            mWifiLock!!.acquire()

            updateNotification()
        }
    }

    private fun releaseLock() {
        if (mWakeLock != null) {
            mWakeLock!!.release()
            mWakeLock = null

            mWifiLock!!.release()
            mWifiLock = null

            updateNotification()
        }
    }

    companion object {
        val ACTION_SERVICE_STOP = "neoterm.action.termService.stop"
        val ACTION_ACQUIRE_LOCK = "neoterm.action.termService.lock.acquire"
        val ACTION_RELEASE_LOCK = "neoterm.action.termService.lock.release"
        private val NOTIFICATION_ID = 52019
    }
}
