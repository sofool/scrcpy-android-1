package top.saymzx.scrcpy.android

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.Intent.*
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.KeyEvent.*
import android.view.MotionEvent.*
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.*
import java.util.*

@SuppressLint("StaticFieldLeak")
lateinit var appData: AppData

class MainActivity : Activity(), ViewModelStoreOwner {

  companion object {
    var VIEWMODEL_STORE: ViewModelStore? = null
  }

  // 创建界面
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    appData = ViewModelProvider(this).get(AppData::class.java)
    appData.main = this
    if (!appData.isInit) appData.init()
    appData.publicTools.setStatusAndNavBar(this)
    // 如果第一次使用展示介绍信息
    if (appData.settings.getBoolean("FirstUse", true)) startActivityForResult(
      Intent(
        this, ShowAppActivity::class.java
      ), 1
    )
    // 读取数据库并展示设备列表
    setDevicesList()
    // 添加按钮监听
    setAddDeviceListener()
    // 设置按钮监听
    setSetButtonListener()
    // 检查更新
    checkUpdate()
  }

  override fun onResume() {
    // 检查权限
    if (checkPermission()) {
      // 仅在第一次启动默认设备
      if (!appData.isShowDefultDevice) {
        appData.isShowDefultDevice = true
        // 启动默认设备
        val defalueDevice = appData.settings.getString("DefaultDevice", "")
        if (defalueDevice != "") {
          for (i in appData.devices) {
            if (i.name == defalueDevice) {
              if (i.status == -1) {
                i.scrcpy = Scrcpy(i)
                i.scrcpy!!.start()
              }
              break
            }
          }
        }
      }
    }
    super.onResume()
  }

  // 其他页面回调
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    // ShowApp页面回调
    if (requestCode == 1) {
      if (resultCode == 1) {
        appData.settings.edit().apply {
          putBoolean("FirstUse", false)
          apply()
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, data)
  }

  // 检查权限
  private fun checkPermission(): Boolean {
    // 检查悬浮窗权限
    if (!Settings.canDrawOverlays(this)) {
      val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
      intent.data = Uri.parse("package:$packageName")
      startActivity(intent)
      Toast.makeText(appData.main, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
      return false
    }
    return true
  }

  // 读取数据库并展示设备列表
  private fun setDevicesList() {
    val devicesList = findViewById<ListView>(R.id.devices_list)
    devicesList.adapter = appData.deviceListAdapter
  }

  // 添加设备监听
  private fun setAddDeviceListener() {
    findViewById<TextView>(R.id.add_device).setOnClickListener {
      // 显示添加界面
      val addDeviceView = LayoutInflater.from(this).inflate(R.layout.add_device, null, false)
      val builder: AlertDialog.Builder = AlertDialog.Builder(this)
      builder.setView(addDeviceView)
      builder.setCancelable(false)
      val dialog = builder.create()
      dialog.setCanceledOnTouchOutside(true)
      dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
      // 设置默认值
      addDeviceView.findViewById<Spinner>(R.id.add_device_max_size).setSelection(
        appData.publicTools.getStringIndex(
          "1600", resources.getStringArray(R.array.maxSizeItems)
        )
      )
      addDeviceView.findViewById<Spinner>(R.id.add_device_fps).setSelection(
        appData.publicTools.getStringIndex(
          "60", resources.getStringArray(R.array.fpsItems)
        )
      )
      addDeviceView.findViewById<Spinner>(R.id.add_device_video_bit).setSelection(
        appData.publicTools.getStringIndex(
          "8000000", resources.getStringArray(R.array.videoBitItems1)
        )
      )
      addDeviceView.findViewById<Spinner>(R.id.add_device_videoCodec).setSelection(
        appData.publicTools.getStringIndex(
          appData.settings.getString("setVideoCodec", "h264")!!,
          resources.getStringArray(R.array.videoCodecItems)
        )
      )
      addDeviceView.findViewById<Spinner>(R.id.add_device_audioCodec).setSelection(
        appData.publicTools.getStringIndex(
          appData.settings.getString("setAudioCodec", "opus")!!,
          resources.getStringArray(R.array.audioCodecItems)
        )
      )
      addDeviceView.findViewById<Switch>(R.id.add_device_set_resolution).isChecked =
        appData.settings.getBoolean("setSetResolution", true)
      addDeviceView.findViewById<Switch>(R.id.add_device_default_full).isChecked =
        appData.settings.getBoolean("setDefaultFull", true)
      // 是否显示高级选项
      addDeviceView.findViewById<CheckBox>(R.id.add_device_is_options).setOnClickListener {
        addDeviceView.findViewById<LinearLayout>(R.id.add_device_options).visibility =
          if (addDeviceView.findViewById<CheckBox>(R.id.add_device_is_options).isChecked) View.VISIBLE
          else View.GONE
      }
      // 完成添加设备
      addDeviceView.findViewById<Button>(R.id.add_device_ok).setOnClickListener {
        // 名字不能为空
        if (addDeviceView.findViewById<EditText>(R.id.add_device_name).text.toString() != "") {
          appData.deviceListAdapter.newDevice(
            addDeviceView.findViewById<EditText>(R.id.add_device_name).text.toString(),
            addDeviceView.findViewById<EditText>(R.id.add_device_address).text.toString(),
            addDeviceView.findViewById<EditText>(R.id.add_device_port).text.toString().toInt(),
            addDeviceView.findViewById<Spinner>(R.id.add_device_videoCodec).selectedItem.toString(),
            addDeviceView.findViewById<Spinner>(R.id.add_device_audioCodec).selectedItem.toString(),
            addDeviceView.findViewById<Spinner>(R.id.add_device_max_size).selectedItem.toString()
              .toInt(),
            addDeviceView.findViewById<Spinner>(R.id.add_device_fps).selectedItem.toString()
              .toInt(),
            resources.getStringArray(R.array.videoBitItems1)[addDeviceView.findViewById<Spinner>(R.id.add_device_video_bit).selectedItemPosition].toInt(),
            addDeviceView.findViewById<Switch>(R.id.add_device_set_resolution).isChecked,
            addDeviceView.findViewById<Switch>(R.id.add_device_default_full).isChecked
          )
          dialog.cancel()
        }
      }
      dialog.show()
    }
  }

  // 设置按钮监听
  private fun setSetButtonListener() {
    findViewById<ImageView>(R.id.set).setOnClickListener {
      startActivity(Intent(this, SetActivity::class.java))
    }
  }

  // 检查更新
  private fun checkUpdate() {
    appData.mainScope.launch {
      withContext(Dispatchers.IO) {
        val request: Request = Request.Builder()
          .url("https://github.saymzx.top/api/repos/mingzhixian/scrcpy/releases/latest").build()
        try {
          appData.okhttpClient.newCall(request).execute().use { response ->
            val json = JSONObject(response.body!!.string())
            val newVersionCode = json.getInt("tag_name")
            if (newVersionCode > appData.versionCode) withContext(Dispatchers.Main) {
              Toast.makeText(appData.main, "已发布新版本，可前往更新", Toast.LENGTH_LONG).show()
            }
          }
        } catch (_: Exception) {
        }
      }
    }
  }

  // 强制清理
  // ViewModel
  override fun getViewModelStore(): ViewModelStore {
    if (VIEWMODEL_STORE == null) {
      VIEWMODEL_STORE = ViewModelStore()
    }
    return VIEWMODEL_STORE!!
  }

}