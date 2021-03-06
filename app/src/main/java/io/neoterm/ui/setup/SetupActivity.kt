package io.neoterm.ui.setup

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast
import com.igalata.bubblepicker.BubblePickerListener
import com.igalata.bubblepicker.adapter.BubblePickerAdapter
import com.igalata.bubblepicker.model.BubbleGradient
import com.igalata.bubblepicker.model.PickerItem
import com.igalata.bubblepicker.rendering.BubblePicker
import io.neoterm.R
import io.neoterm.backend.TerminalSession
import io.neoterm.customize.pm.NeoPackageManager
import io.neoterm.customize.pm.NeoPackageManagerUtils
import io.neoterm.customize.setup.BaseFileInstaller
import io.neoterm.preference.NeoPreference
import io.neoterm.preference.NeoTermPath
import io.neoterm.utils.PackageUtils
import io.neoterm.frontend.floating.TerminalDialog
import java.util.*


/**
 * @author kiva
 */
class SetupActivity : AppCompatActivity() {
    companion object {
        private val DEFAULT_PACKAGES = arrayOf(
                "zsh", "neoterm-core", "tmux", "nodejs",
                "fish", "make", "gdb", "clang", "vim", "emacs", "nano",
                "curl", "git", "python", "p7zip", "oh-my-zsh")
    }

    lateinit var picker: BubblePicker
    lateinit var toast: Toast
    var aptUpdated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ui_setup)
        picker = findViewById(R.id.bubble_picker) as BubblePicker
        val nextButton = findViewById(R.id.setup_next) as Button
        nextButton.setOnClickListener {
            if (aptUpdated) {
                val packageList = mutableListOf("apt", "install", "-y")
                var withShell: String? = null
                picker.selectedItems
                        .filterNotNull()
                        .forEach {
                            val name = it.title ?: ""
                            packageList.add(name)
                            if (name == "zsh" || name == "fish" || name == "bash"
                                    || name == "mosh" || name == "dash") {
                                withShell = name
                            }
                        }
                if (packageList.size == 0) {
                    return@setOnClickListener
                }

                TerminalDialog(this@SetupActivity)
                        .onFinish(object : TerminalDialog.SessionFinishedCallback {
                            override fun onSessionFinished(dialog: TerminalDialog, finishedSession: TerminalSession?) {
                                if (finishedSession?.exitStatus == 0) {
                                    dialog.dismiss()
                                    if (withShell != null) {
                                        NeoPreference.store(R.string.key_general_shell, withShell!!)
                                    }
                                } else {
                                    dialog.setTitle(getString(R.string.error))
                                }
                            }
                        })
                        .execute(NeoTermPath.APT_BIN_PATH, packageList.toTypedArray())
                        .show(getString(R.string.installer_message))
            } else {
                finish()
            }
        }
        setupBubbles()
        installBaseFiles()
    }

    private fun installBaseFiles() {
        var resultListener: BaseFileInstaller.ResultListener? = null
        resultListener = BaseFileInstaller.ResultListener { error ->
            if (error == null) {
                setResult(Activity.RESULT_OK)
                PackageUtils.syncSource()
                executeAptUpdate()
            } else {
                AlertDialog.Builder(this@SetupActivity)
                        .setTitle(R.string.error)
                        .setMessage(error.toString())
                        .setNegativeButton(R.string.use_system_shell, { _, _ ->
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        })
                        .setPositiveButton(R.string.retry, { dialog, _ ->
                            dialog.dismiss()
                            BaseFileInstaller.installBaseFiles(this@SetupActivity, resultListener)
                        }).show()
            }
        }
        BaseFileInstaller.installBaseFiles(this, resultListener)
    }

    private fun executeAptUpdate() {
        PackageUtils.executeApt(this, "update", { exitStatus, dialog ->
            if (exitStatus == 0) {
                dialog.dismiss()
                aptUpdated = true
                executeAptUpgrade()
            } else {
                dialog.setTitle(getString(R.string.error))
            }
        })
    }

    private fun executeAptUpgrade() {
        PackageUtils.executeApt(this, "upgrade", { exitStatus, dialog ->
            if (exitStatus == 0) {
                dialog.dismiss()
            } else {
                dialog.setTitle(getString(R.string.error))
            }
        })
    }

    @SuppressLint("ShowToast")
    private fun setupBubbles() {
        val titles =
                if (intent.getBooleanExtra("setup", false))
                    DEFAULT_PACKAGES
                else
                    randomPackageList()
        val colors = resources.obtainTypedArray(R.array.bubble_colors)

        toast = Toast.makeText(this, null, Toast.LENGTH_LONG)

        picker.bubbleSize = 25
        picker.adapter = object : BubblePickerAdapter {
            override val totalCount = titles.size
            override fun getItem(position: Int): PickerItem {
                return PickerItem().apply {
                    title = titles[position]
                    textColor = ContextCompat.getColor(this@SetupActivity, android.R.color.white)
                    gradient = BubbleGradient(colors.getColor((position * 2) % 8, 0),
                            colors.getColor((position * 2) % 8 + 1, 0), BubbleGradient.VERTICAL)
                }
            }
        }
        picker.listener = object : BubblePickerListener {
            override fun onBubbleSelected(item: PickerItem) {
                val packageName = item.title
                val pm = NeoPackageManager.get()
                val packageInfo = pm.getPackageInfo(packageName)
                if (packageInfo != null) {
                    val packageDesc = packageInfo.description
                    toast.cancel()
                    toast.setText(packageDesc)
                    toast.show()
                }
            }

            override fun onBubbleDeselected(item: PickerItem) {
                toast.cancel()
            }
        }
        colors.recycle()
    }

    private fun randomPackageList(): Array<String> {
        val list = mutableListOf<String>()
        val pm = NeoPackageManager.get()

        val sourceFiles = NeoPackageManagerUtils.detectSourceFiles()
        pm.clearPackages()
        for (index in sourceFiles.indices) {
            pm.refreshPackageList(sourceFiles[index], false)
        }

        val limit = DEFAULT_PACKAGES.size
        val packageNames = pm.packages.keys
        val packageCount = packageNames.size
        val random = Random()

        var i = 0
        while (i < limit) {
            val randomIndex = Math.abs(random.nextInt()) % packageCount
            val packageName = packageNames.elementAt(randomIndex)
            if (packageName.startsWith("lib") || packageName.endsWith("-dev")) {
                continue
            }
            list.add(packageName)
            ++i
        }
        return list.toTypedArray()
    }

    override fun onResume() {
        super.onResume()
        picker.onResume()
    }

    override fun onPause() {
        super.onPause()
        picker.onPause()
    }
}