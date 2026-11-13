package org.qosp.notes.ui.html

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.components.html.HtmlLibraryStore
import org.qosp.notes.components.html.HtmlRules
import org.qosp.notes.databinding.FragmentHtmlViewerBinding
import org.qosp.notes.ui.common.BaseFragment

/**
 * Read-only HTML viewer: the page's scripts run (quizzes, buttons...), the source cannot be edited, and progress
 * is saved automatically in private storage (see assets/html/bp-boot.js and docs/DECISIONS.md).
 */
class HtmlViewerFragment : BaseFragment(R.layout.fragment_html_viewer) {
    private val model: HtmlViewerViewModel by viewModel()
    private val store: HtmlLibraryStore by inject()
    private val binding get() = FragmentHtmlViewerBinding.bind(requireView())
    override val toolbar: Toolbar get() = binding.viewerToolbar
    override val toolbarTitle: String get() = model.doc?.title.orEmpty()
    override val hasMenu = true

    private var server: HtmlDocServer? = null
    private var resetting = false
    private var missingShown = false
    private val BACK = 0x4801
    private val FORWARD = 0x4802
    private val RELOAD = 0x4803
    private val RESET = 0x4804
    private val CLOSE = 0x4805

    private val webBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val web = view?.let { binding.viewerWeb }
            if (web != null && web.canGoBack()) web.goBack() else findNavController().popBackStack()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        fun add(id: Int, title: Int, icon: Int?, always: Boolean) = menu.add(Menu.NONE, id, Menu.NONE, title).apply {
            icon?.let(::setIcon)
            setShowAsAction(if (always) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER)
        }
        add(BACK, R.string.html_back, R.drawable.ic_back, true)
        add(FORWARD, R.string.html_forward, R.drawable.ic_chevron_right, true)
        add(RELOAD, R.string.html_reload, R.drawable.ic_sync, true)
        add(CLOSE, R.string.html_close, R.drawable.ic_close, true)
        add(RESET, R.string.html_reset, null, false)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val web = view?.let { binding.viewerWeb }
        menu.findItem(BACK)?.isEnabled = web?.canGoBack() == true
        menu.findItem(FORWARD)?.isEnabled = web?.canGoForward() == true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val web = binding.viewerWeb
        when (item.itemId) {
            BACK -> if (web.canGoBack()) web.goBack()
            FORWARD -> if (web.canGoForward()) web.goForward()
            // Reload keeps the answers: the page is asked to save first, and the newest progress is served again.
            RELOAD -> web.evaluateJavascript(FLUSH) { web.reload() }
            CLOSE -> findNavController().popBackStack()
            RESET -> MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.html_reset_confirm, model.doc?.title.orEmpty()))
                .setPositiveButton(R.string.html_reset) { _, _ -> startReset() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, webBack)
        val web = binding.viewerWeb
        web.settings.apply {
            javaScriptEnabled = true // only for documents the user imported; see sandbox settings below
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        web.webViewClient = viewerClient
        web.webChromeClient = chromeClient
        web.addJavascriptInterface(Bridge(), "BlackPearlProgress") // single method: save(json), see Bridge

        val id = requireArguments().getString("docId").orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val doc = model.open(id)
            if (doc == null) {
                Snackbar.make(binding.root, R.string.html_doc_missing, Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return@launch
            }
            (activity as? org.qosp.notes.ui.MainActivity)?.supportActionBar?.title = doc.title
            val boot = requireContext().assets.open("html/bp-boot.js").use { it.readBytes().toString(Charsets.UTF_8) }
            server = HtmlDocServer(
                store = store,
                doc = doc,
                bootJs = boot,
                progress = { model.progress },
                onMissing = { name -> web.post { warnMissing(name) } },
            )
            web.loadUrl(server!!.entryUrl())
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.savedAt.collect { t ->
                    (activity as? org.qosp.notes.ui.MainActivity)?.supportActionBar?.subtitle =
                        if (t > 0) getString(R.string.html_saved_at, DateFormat.getTimeFormat(requireContext()).format(t)) else null
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.viewerWeb.onResume()
    }

    override fun onPause() {
        // App going to background or screen closing: ask the page to save right now.
        binding.viewerWeb.evaluateJavascript(FLUSH, null)
        binding.viewerWeb.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        (activity as? org.qosp.notes.ui.MainActivity)?.supportActionBar?.subtitle = null
        val web = binding.viewerWeb
        (web.parent as? android.view.ViewGroup)?.removeView(web)
        // The page lives up to 2 s more (below): its events (page finished, alert...) must not reach this closed
        // screen, whose view no longer exists.
        web.webViewClient = WebViewClient()
        web.webChromeClient = null
        // Destroy only after the page has handed over its last save (callback), with a fallback timeout.
        web.evaluateJavascript(FLUSH) { runCatching { web.destroy() } }
        web.postDelayed({ runCatching { web.destroy() } }, 2000)
        server = null
        super.onDestroyView()
    }

    private fun startReset() {
        resetting = true
        model.blockSaves = true
        binding.viewerWeb.loadUrl("about:blank")
    }

    private fun warnMissing(name: String) {
        if (missingShown || view == null) return
        missingShown = true
        Snackbar.make(binding.root, getString(R.string.html_missing_resource, name), Snackbar.LENGTH_LONG).show()
    }

    private fun openExternally(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
    }

    /** The only thing a page can do natively: hand over its own progress. The document id is fixed on our side. */
    private inner class Bridge {
        @JavascriptInterface
        fun save(json: String?) {
            if (json != null && json.length <= HtmlRules.MAX_PROGRESS_BYTES) model.onProgress(json)
        }
    }

    private val viewerClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            server?.intercept(request)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (server?.host == request.url.host && request.url.scheme == "https") return false
            if (request.url.scheme == "https" || request.url.scheme == "http") {
                Snackbar.make(binding.root, R.string.html_external_blocked, Snackbar.LENGTH_LONG)
                    .setAction(R.string.html_open_browser) { openExternally(url) }.show()
            }
            return true // everything else (file:, content:, intent:...) is refused silently
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            binding.viewerProgress.isVisible = true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            binding.viewerProgress.isVisible = false
            webBack.isEnabled = view.canGoBack()
            activity?.invalidateOptionsMenu()
            if (resetting && url == "about:blank") {
                // Old page is gone: erase saved progress and the page's own web storage, then open it fresh.
                viewLifecycleOwner.lifecycleScope.launch {
                    model.reset()
                    resetting = false
                    model.blockSaves = false
                    server?.let { view.loadUrl(it.entryUrl()) }
                    Snackbar.make(binding.root, R.string.html_reset_done, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        @android.annotation.TargetApi(26)
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            findNavController().popBackStack()
            return true
        }
    }

    private val chromeClient = object : WebChromeClient() {
        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            MaterialAlertDialogBuilder(requireContext()).setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            MaterialAlertDialogBuilder(requireContext()).setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onJsPrompt(view: WebView, url: String?, message: String?, default: String?, result: JsPromptResult): Boolean {
            val field = EditText(requireContext()).apply { setText(default) }
            MaterialAlertDialogBuilder(requireContext()).setMessage(message).setView(field, 48, 16, 48, 0)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(field.text.toString()) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        // Never grant camera, microphone, location or other device access to a document.
        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) =
            callback.invoke(origin, false, false)

        override fun onShowFileChooser(
            webView: WebView,
            callback: android.webkit.ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            callback.onReceiveValue(null)
            return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?) = false
    }

    private companion object {
        const val FLUSH = "window.__bpFlush&&window.__bpFlush()"
    }
}
