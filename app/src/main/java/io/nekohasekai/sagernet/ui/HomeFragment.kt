package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.util.TypedValue
import android.text.format.Formatter
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : ToolbarFragment(R.layout.layout_home) {

  private val client by lazy { OkHttpClient() }
  private val timeFormat by lazy { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

  private lateinit var statusText: android.widget.TextView
  private lateinit var nodeText: android.widget.TextView
  private lateinit var speedText: android.widget.TextView
  private lateinit var latencyText: android.widget.TextView
  private lateinit var latencyTestBtn: MaterialButton
  private lateinit var connectBtn: MaterialButton

  private lateinit var ipText: android.widget.TextView
  private lateinit var ipProxyText: android.widget.TextView
  private lateinit var ipDetailText: android.widget.TextView
  private lateinit var ipUpdatedText: android.widget.TextView
  private lateinit var ipRefreshBtn: MaterialButton

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    toolbar.title = getString(R.string.app_name)

    // 主页不显示左上角设置按钮
    toolbar.navigationIcon = null
    toolbar.setNavigationOnClickListener(null)

    // toolbar 需要把状态栏高度算进自己的高度，否则标题容易偏下
    val actionBarSize = run {
      val tv = TypedValue()
      val ok = requireContext().theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)
      if (ok) TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics) else 0
    }
    ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      val lp = v.layoutParams
      if (lp != null) {
        lp.height = actionBarSize + bars.top
        v.layoutParams = lp
      }
      v.updatePadding(top = bars.top)
      insets
    }

    val root = view.findViewById<View>(R.id.homeRoot)
    val basePaddingBottom = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      // 顶部由 toolbar 处理，这里只处理底部
      v.updatePadding(bottom = basePaddingBottom + bars.bottom)
      insets
    }

    statusText = view.findViewById(R.id.home_status)
    nodeText = view.findViewById(R.id.home_node)
    speedText = view.findViewById(R.id.home_speed)
    latencyText = view.findViewById(R.id.home_latency)
    latencyTestBtn = view.findViewById(R.id.home_latency_test)
    connectBtn = view.findViewById(R.id.home_connect_btn)
    ipText = view.findViewById(R.id.home_ip)
    ipProxyText = view.findViewById(R.id.home_ip_proxy)
    ipDetailText = view.findViewById(R.id.home_ip_detail)
    ipUpdatedText = view.findViewById(R.id.home_ip_updated)
    ipRefreshBtn = view.findViewById(R.id.home_ip_refresh)

    connectBtn.setOnClickListener {
      (activity as? MainActivity)?.requestToggleService()
    }

    latencyTestBtn.setOnClickListener {
      testLatency()
    }

    ipRefreshBtn.setOnClickListener {
      refreshIp()
    }

    // 首次进入刷新一次 IP
    refreshIp()

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
          refreshStatusUi()
          delay(800L)
        }
      }
    }
  }

  private suspend fun refreshStatusUi() {
    val (status, nodeName, canStop) = onDefaultDispatcher {
      val state = DataStore.serviceState
      val statusText = when {
        state.connected -> getString(R.string.home_status_connected)
        state.started -> getString(R.string.connecting)
        else -> getString(R.string.home_status_disconnected)
      }

      val currentId = DataStore.selectedProxy
      val node = if (currentId > 0L) {
        ProfileManager.getProfile(currentId)?.displayName() ?: "-"
      } else {
        "-"
      }
      Triple(statusText, node, state.canStop)
    }

    onMainDispatcher {
      statusText.text = status
      nodeText.text = getString(R.string.home_current_node, nodeName)
      connectBtn.text = if (canStop) getString(R.string.stop) else getString(R.string.start)

      val tx = DataStore.dashboardTxRateProxy
      val rx = DataStore.dashboardRxRateProxy
      speedText.text = getString(
        R.string.home_speed,
        Formatter.formatFileSize(requireContext(), tx),
        Formatter.formatFileSize(requireContext(), rx)
      )

      val delayMs = DataStore.dashboardLastDelayMs
      latencyText.text = if (delayMs >= 0) {
        getString(R.string.home_latency, delayMs)
      } else {
        getString(R.string.home_latency_unknown)
      }
      latencyTestBtn.isEnabled = DataStore.serviceState.connected
    }
  }

  private fun testLatency() {
    val act = activity as? MainActivity ?: return
    if (!DataStore.serviceState.connected) return
    latencyTestBtn.isEnabled = false
    latencyText.text = getString(R.string.connection_test_testing)
    viewLifecycleOwner.lifecycleScope.launch {
      val result = onDefaultDispatcher {
        try {
          act.urlTest()
        } catch (e: Exception) {
          Logs.w(e)
          -1
        }
      }
      DataStore.dashboardLastDelayMs = result
      latencyTestBtn.isEnabled = true
    }
  }

  private fun refreshIp() {
    viewLifecycleOwner.lifecycleScope.launch {
      ipRefreshBtn.isEnabled = false
      ipUpdatedText.text = getString(R.string.loading)

      val result = onDefaultDispatcher {
        try {
          fun fetchTrace(url: String): Map<String, String> {
            val req = Request.Builder()
              .url(url)
              .header("User-Agent", USER_AGENT)
              .build()
            client.newCall(req).execute().use { resp ->
              val body = resp.body?.string().orEmpty()
              if (!resp.isSuccessful || body.isBlank()) {
                error("http ${resp.code}")
              }
              val map = LinkedHashMap<String, String>()
              body.split('\n').forEach { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@forEach
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty()) map[k] = v
              }
              return map
            }
          }

          val direct = fetchTrace("https://www.cloudflare.com/cdn-cgi/trace")
          val directIp = direct["ip"].orEmpty().ifBlank { "-" }
          val loc = direct["loc"].orEmpty()
          val colo = direct["colo"].orEmpty()
          val warp = direct["warp"].orEmpty()
          val detail = listOf(
            loc.takeIf { it.isNotBlank() },
            colo.takeIf { it.isNotBlank() },
            warp.takeIf { it.isNotBlank() }
          ).filterNotNull().joinToString(" · ").ifBlank { "-" }

          // proxy ip: try use local socks5 proxy if service is running
          val proxyIp = try {
            if (DataStore.serviceState.started) {
              val proxyClient = OkHttpClient.Builder()
                .proxy(java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", DataStore.mixedPort)))
                .build()
              val req = Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .header("User-Agent", USER_AGENT)
                .build()
              proxyClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) error("http ${resp.code}")
                val map = LinkedHashMap<String, String>()
                body.split('\n').forEach { line ->
                  val idx = line.indexOf('=')
                  if (idx <= 0) return@forEach
                  val k = line.substring(0, idx).trim()
                  val v = line.substring(idx + 1).trim()
                  if (k.isNotEmpty()) map[k] = v
                }
                map["ip"].orEmpty().ifBlank { "-" }
              }
            } else {
              "-"
            }
          } catch (_: Exception) {
            "-"
          }

          listOf(directIp, proxyIp, detail, "")
        } catch (e: Exception) {
          Logs.w(e)
          listOf("-", "-", "-", e.message ?: e.toString())
        }
      }

      ipText.text = getString(R.string.home_ip_direct, result[0])
      ipProxyText.text = getString(R.string.home_ip_proxy, result[1])
      ipDetailText.text = result[2]
      ipUpdatedText.text = if (result[3].isBlank()) {
        getString(R.string.updated_at, timeFormat.format(Date()))
      } else {
        getString(R.string.service_failed) + result[3]
      }
      ipRefreshBtn.isEnabled = true
    }
  }
}
