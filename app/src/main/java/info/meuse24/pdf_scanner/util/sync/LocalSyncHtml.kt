package info.meuse24.pdf_scanner.util.sync

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.model.Document

/**
 * Self-contained design system for the PC-Sync web UI: everything (fonts, icons, scripts) is
 * inlined because this page is served entirely from the phone's local HTTP server with no
 * internet access and must not depend on any CDN.
 */
private val SHARED_STYLE = """
    :root{
      color-scheme:light dark;
      --bg-0:#eef1fc;--bg-1:#f8f4fd;--surface:#ffffff;--surface-alpha:rgba(255,255,255,.7);
      --border:rgba(15,23,42,.08);--text:#161826;--muted:#5b5f75;
      --primary:#5b5bf5;--primary-2:#9333ea;--accent:#0f9d8e;
      --danger:#dc2626;--danger-bg:#fef2f2;--success:#15803d;--success-bg:#f0fdf4;
      --shadow:0 24px 60px -24px rgba(30,27,75,.35);
      --r-lg:26px;--r-md:16px;--r-sm:10px;
    }
    @media (prefers-color-scheme:dark){
      :root{
        --bg-0:#0a0b12;--bg-1:#15101f;--surface:#181925;--surface-alpha:rgba(24,25,37,.6);
        --border:rgba(255,255,255,.08);--text:#f1f2f8;--muted:#9a9db1;
        --primary:#8d8dff;--primary-2:#dd93ff;--accent:#2ee6cf;
        --danger:#f87171;--danger-bg:rgba(248,113,113,.14);--success:#4ade80;--success-bg:rgba(74,222,128,.14);
        --shadow:0 24px 60px -24px rgba(0,0,0,.65);
      }
    }
    @view-transition{navigation:auto;}
    *{box-sizing:border-box;}
    html,body{height:100%;}
    svg{display:block;flex-shrink:0;}
    button,input{font-family:inherit;}
    body{
      margin:0;min-height:100vh;color:var(--text);background-color:var(--bg-0);
      font-family:-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;
      -webkit-font-smoothing:antialiased;text-rendering:optimizeLegibility;
    }
    @supports (background: color-mix(in srgb, red 50%, blue)){
      body{
        background:
          radial-gradient(1100px 700px at 12% -10%, color-mix(in srgb, var(--primary) 24%, transparent), transparent),
          radial-gradient(900px 650px at 110% 8%, color-mix(in srgb, var(--primary-2) 18%, transparent), transparent),
          radial-gradient(700px 500px at 50% 120%, color-mix(in srgb, var(--accent) 12%, transparent), transparent),
          linear-gradient(180deg, var(--bg-0), var(--bg-1));
        background-attachment:fixed;
      }
    }
    h1{font-size:1.5rem;margin:.2rem 0 .4rem;letter-spacing:-.01em;}
    h2{font-size:1.05rem;margin:0 0 .3rem;}
    p{line-height:1.5;}
    .muted{color:var(--muted);margin-top:0;}
    .page{display:flex;flex-direction:column;align-items:center;padding:32px 16px 56px;}
    .page-center{min-height:100vh;justify-content:center;}
    .page-wide{width:100%;max-width:640px;gap:20px;margin:0 auto;}
    .card{
      width:100%;background:var(--surface-alpha);border:1px solid var(--border);
      border-radius:var(--r-lg);box-shadow:var(--shadow);padding:28px 26px;
    }
    @supports (backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)){
      .card{backdrop-filter:blur(20px) saturate(160%);-webkit-backdrop-filter:blur(20px) saturate(160%);}
    }
    .card-auth{max-width:400px;text-align:center;}
    .card-danger{border-color:color-mix(in srgb, var(--danger) 35%, var(--border));}
    .brand{display:flex;align-items:center;gap:10px;justify-content:center;margin-bottom:14px;}
    .top-bar{width:100%;display:flex;align-items:center;justify-content:space-between;padding:2px 4px;}
    .top-bar .brand{justify-content:flex-start;margin-bottom:0;}
    .brand-name{font-weight:700;letter-spacing:-.01em;font-size:1.02rem;}
    .status-dot{width:11px;height:11px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 4px color-mix(in srgb, var(--accent) 22%, transparent);}
    @media (prefers-reduced-motion:no-preference){.status-dot{animation:pulse 2.2s ease-in-out infinite;}}
    @keyframes pulse{0%,100%{opacity:1;}50%{opacity:.45;}}
    .icon-badge{width:56px;height:56px;border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 12px;background:var(--danger-bg);color:var(--danger);}
    .alert{border-radius:var(--r-sm);padding:10px 14px;font-size:.92rem;margin:12px 0;text-align:start;}
    .alert-danger{background:var(--danger-bg);color:var(--danger);}
    .alert-success{background:var(--success-bg);color:var(--success);}
    .pin-form{margin-top:18px;}
    .otp{display:flex;gap:10px;justify-content:center;margin-bottom:18px;}
    .otp-box{
      width:56px;height:64px;text-align:center;font-size:1.8rem;font-variant-numeric:tabular-nums;
      border-radius:var(--r-md);border:1.5px solid var(--border);background:var(--surface);color:var(--text);
      transition:border-color .15s, box-shadow .15s, transform .15s;
    }
    .otp-box:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 4px color-mix(in srgb, var(--primary) 22%, transparent);}
    .otp-fallback-input{
      width:100%;font-size:1.8rem;letter-spacing:.5rem;text-align:center;padding:14px;
      border-radius:var(--r-md);border:1.5px solid var(--border);background:var(--surface);color:var(--text);margin-bottom:16px;
    }
    @media (prefers-reduced-motion:no-preference){.otp-shake{animation:shake .4s;}}
    @keyframes shake{
      10%,90%{transform:translateX(-1px);}20%,80%{transform:translateX(2px);}
      30%,50%,70%{transform:translateX(-4px);}40%,60%{transform:translateX(4px);}
    }
    .btn{appearance:none;border:none;cursor:pointer;border-radius:999px;padding:13px 22px;font-size:1rem;font-weight:600;transition:transform .15s, opacity .15s;}
    .btn:active{transform:scale(.97);}
    .btn-block{width:100%;}
    .btn-primary{color:#fff;background:linear-gradient(135deg, var(--primary), var(--primary-2));box-shadow:0 12px 24px -10px color-mix(in srgb, var(--primary) 60%, transparent);}
    .btn-primary:hover{opacity:.94;}
    .btn-primary:focus-visible{outline:3px solid color-mix(in srgb, var(--primary) 50%, transparent);outline-offset:2px;}
    .doc-list{list-style:none;margin:6px 0 0;padding:0;display:flex;flex-direction:column;gap:10px;}
    .doc-card{
      display:flex;align-items:center;gap:12px;padding:12px 14px;border:1px solid var(--border);
      border-radius:var(--r-md);background:color-mix(in srgb, var(--surface) 70%, transparent);
      transition:border-color .15s, transform .15s;
    }
    .doc-card:hover{border-color:color-mix(in srgb, var(--primary) 40%, var(--border));}
    .doc-icon{color:var(--primary);}
    .doc-info{display:flex;flex-direction:column;min-width:0;flex:1;}
    .doc-name{font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
    .doc-meta{font-size:.82rem;color:var(--muted);}
    .doc-download{
      flex-shrink:0;width:38px;height:38px;border-radius:50%;display:flex;align-items:center;justify-content:center;
      color:var(--primary);background:color-mix(in srgb, var(--primary) 12%, transparent);text-decoration:none;
      transition:background .15s, transform .15s;
    }
    .doc-download:hover{background:color-mix(in srgb, var(--primary) 22%, transparent);transform:scale(1.05);}
    .empty-state{text-align:center;color:var(--muted);padding:26px 10px;display:flex;flex-direction:column;align-items:center;gap:10px;}
    .empty-state svg{opacity:.5;width:34px;height:34px;}
    .visually-hidden{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;}
    .dropzone{margin-top:14px;border:1.6px dashed var(--border);border-radius:var(--r-md);padding:22px 16px;text-align:center;transition:border-color .15s, background .15s;}
    .dropzone.drag-active{border-color:var(--primary);background:color-mix(in srgb, var(--primary) 8%, transparent);}
    .dropzone-inner{display:flex;flex-direction:column;align-items:center;gap:8px;cursor:pointer;color:var(--muted);border-radius:var(--r-sm);}
    .dropzone-inner svg{color:var(--primary);}
    #fileInput:focus-visible + .dropzone-inner{outline:2px solid var(--primary);outline-offset:6px;}
    .dropzone-filename{font-weight:600;color:var(--text);min-height:1.2em;}
    .dropzone button{margin-top:16px;}
    @media (max-width:380px){.otp-box{width:48px;height:56px;font-size:1.5rem;}.card{padding:22px 18px;}}
""".trimIndent()

private val OTP_SCRIPT = """
    document.getElementById('pinFallback').style.display='none';
    document.getElementById('pinForm').style.display='';
    (function(){
      var boxes=[].slice.call(document.querySelectorAll('[data-otp]'));
      var hidden=document.getElementById('pinValue');
      var form=document.getElementById('pinForm');
      function sync(){
        hidden.value=boxes.map(function(b){return b.value;}).join('');
        if(hidden.value.length===boxes.length){form.submit();}
      }
      boxes.forEach(function(box,i){
        box.addEventListener('input',function(){
          box.value=box.value.replace(/[^0-9]/g,'').slice(0,1);
          if(box.value&&boxes[i+1]){boxes[i+1].focus();}
          sync();
        });
        box.addEventListener('keydown',function(e){
          if(e.key==='Backspace'&&!box.value&&boxes[i-1]){boxes[i-1].focus();}
        });
        box.addEventListener('paste',function(e){
          var text=(e.clipboardData||window.clipboardData).getData('text').replace(/[^0-9]/g,'');
          if(!text){return;}
          e.preventDefault();
          text.split('').slice(0,boxes.length).forEach(function(ch,j){if(boxes[j]){boxes[j].value=ch;}});
          sync();
          var next=boxes[Math.min(text.length,boxes.length-1)];
          if(next){next.focus();}
        });
      });
      if(boxes[0]){boxes[0].focus();}
    })();
""".trimIndent()

private val DROPZONE_SCRIPT = """
    (function(){
      var form=document.getElementById('uploadForm');
      var input=document.getElementById('fileInput');
      var nameEl=form.querySelector('[data-filename]');
      function updateName(){
        if(input.files&&input.files[0]){nameEl.textContent=input.files[0].name;}
      }
      input.addEventListener('change',updateName);
      ['dragenter','dragover'].forEach(function(evt){
        form.addEventListener(evt,function(e){e.preventDefault();form.classList.add('drag-active');});
      });
      ['dragleave','drop'].forEach(function(evt){
        form.addEventListener(evt,function(e){e.preventDefault();form.classList.remove('drag-active');});
      });
      form.addEventListener('drop',function(e){
        var files=e.dataTransfer.files;
        if(files&&files.length){input.files=files;updateName();}
      });
    })();
""".trimIndent()

private fun htmlShell(lang: String, dir: String, title: String, bodyContent: String): String = """
    <!DOCTYPE html>
    <html lang="$lang" dir="$dir">
    <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>$title</title>
    <style>$SHARED_STYLE</style>
    </head>
    <body>
    $bodyContent
    </body>
    </html>
""".trimIndent()

private fun brandMark(resourceProvider: ResourceProvider): String = """
    <div class="brand">
        <svg viewBox="0 0 48 48" width="36" height="36" aria-hidden="true">
            <defs>
                <linearGradient id="brandGradient" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" style="stop-color:var(--primary)" />
                    <stop offset="1" style="stop-color:var(--primary-2)" />
                </linearGradient>
            </defs>
            <rect width="48" height="48" rx="14" fill="url(#brandGradient)" />
            <path d="M13 30c0-7.7 4.9-14 11-14s11 6.3 11 14" fill="none" stroke="white" stroke-width="3.2" stroke-linecap="round" />
            <circle cx="24" cy="34" r="2.6" fill="white" />
        </svg>
        <span class="brand-name">${resourceProvider.getString(R.string.app_label).escapeHtml()}</span>
    </div>
""".trimIndent()

private fun iconPdf(): String = """
    <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
        <path d="M6 2h8l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
        <path d="M14 2v5h5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>
    </svg>
""".trimIndent()

private fun iconDownload(): String = """
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <path d="M12 4v11m0 0 4-4m-4 4-4-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M5 18h14" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
    </svg>
""".trimIndent()

private fun iconUpload(): String = """
    <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true">
        <path d="M12 16V4m0 0 4.5 4.5M12 4 7.5 8.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
""".trimIndent()

private fun iconBlocked(): String = """
    <svg viewBox="0 0 24 24" width="30" height="30" aria-hidden="true">
        <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="1.7"/>
        <path d="M7 7l10 10" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
    </svg>
""".trimIndent()

internal fun renderLoginPage(resourceProvider: ResourceProvider, error: String?): String {
    val errorHtml = error?.let {
        """<div class="alert alert-danger" role="alert">${it.escapeHtml()}</div>"""
    }.orEmpty()
    val shakeClass = if (error != null) " otp-shake" else ""
    val connectLabel = resourceProvider.getString(R.string.local_sync_web_login_button).escapeHtml()
    val pinLabel = resourceProvider.getString(R.string.local_sync_pin_label).escapeHtml()
    val otpBoxCount = 4
    val otpBoxesHtml = (1..otpBoxCount).joinToString("\n") { index ->
        val digitLabel = resourceProvider.getString(R.string.local_sync_web_pin_digit_label, index, otpBoxCount).escapeHtml()
        val autocompleteAttr = if (index == 1) " autocomplete=\"one-time-code\"" else ""
        """<input class="otp-box" type="tel" inputmode="numeric"$autocompleteAttr maxlength="1" data-otp aria-label="$digitLabel" />"""
    }

    val content = """
        <main class="page page-center">
            <div class="card card-auth">
                ${brandMark(resourceProvider)}
                <h1>${resourceProvider.getString(R.string.local_sync_web_login_heading).escapeHtml()}</h1>
                <p class="muted">${resourceProvider.getString(R.string.local_sync_web_login_instructions).escapeHtml()}</p>
                $errorHtml
                <form id="pinForm" method="post" action="/login" class="pin-form" style="display:none" novalidate>
                    <div class="otp$shakeClass" role="group" aria-label="$pinLabel">
                        $otpBoxesHtml
                    </div>
                    <input type="hidden" name="pin" id="pinValue" />
                    <button type="submit" class="btn btn-primary btn-block">$connectLabel</button>
                </form>
                <form method="post" action="/login" class="pin-form" id="pinFallback">
                    <input type="text" name="pin" inputmode="numeric" pattern="[0-9]{4}" maxlength="4" aria-label="$pinLabel" class="otp-fallback-input" autofocus />
                    <button type="submit" class="btn btn-primary btn-block">$connectLabel</button>
                </form>
            </div>
        </main>
        <script>$OTP_SCRIPT</script>
    """.trimIndent()

    return htmlShell(
        lang = resourceProvider.htmlLangAttr(),
        dir = resourceProvider.htmlDirAttr(),
        title = resourceProvider.getString(R.string.local_sync_web_login_title).escapeHtml(),
        bodyContent = content
    )
}

internal fun renderLockedOutPage(resourceProvider: ResourceProvider): String {
    val content = """
        <main class="page page-center">
            <div class="card card-auth card-danger">
                <div class="icon-badge">${iconBlocked()}</div>
                <h1>${resourceProvider.getString(R.string.local_sync_web_locked_out_heading).escapeHtml()}</h1>
                <p class="muted">${resourceProvider.getString(R.string.local_sync_web_locked_out_message).escapeHtml()}</p>
            </div>
        </main>
    """.trimIndent()

    return htmlShell(
        lang = resourceProvider.htmlLangAttr(),
        dir = resourceProvider.htmlDirAttr(),
        title = resourceProvider.getString(R.string.local_sync_web_locked_out_title).escapeHtml(),
        bodyContent = content
    )
}

/** Distinguishes a successful upload from a rejected one so the UI can pick the right alert style and ARIA role. */
internal sealed interface UploadFeedback {
    val text: String
    data class Success(override val text: String) : UploadFeedback
    data class Error(override val text: String) : UploadFeedback
}

internal fun renderDocumentsPage(
    resourceProvider: ResourceProvider,
    documents: List<Document>,
    feedback: UploadFeedback? = null
): String {
    val messageHtml = feedback?.let {
        val alertClass = if (it is UploadFeedback.Error) "alert-danger" else "alert-success"
        val role = if (it is UploadFeedback.Error) "alert" else "status"
        """<div class="alert $alertClass" role="$role">${it.text.escapeHtml()}</div>"""
    }.orEmpty()

    val listHtml = if (documents.isEmpty()) {
        """
        <div class="empty-state">
            ${iconPdf()}
            <p>${resourceProvider.getString(R.string.local_sync_web_empty_state).escapeHtml()}</p>
        </div>
        """.trimIndent()
    } else {
        val items = documents.joinToString("\n") { document ->
            val meta = resourceProvider.getString(
                R.string.local_sync_web_document_meta,
                document.pageCount,
                formatFileSize(document.fileSize)
            )
            val downloadName = "${document.filename}.pdf"
            val downloadLabel = resourceProvider.getString(R.string.local_sync_web_download_label, downloadName).escapeHtml()
            """
            <li class="doc-card">
                <span class="doc-icon">${iconPdf()}</span>
                <span class="doc-info">
                    <span class="doc-name">${downloadName.escapeHtml()}</span>
                    <span class="doc-meta">${meta.escapeHtml()}</span>
                </span>
                <a class="doc-download" href="/documents/${document.id}/download" aria-label="$downloadLabel">${iconDownload()}</a>
            </li>
            """.trimIndent()
        }
        """<ul class="doc-list">$items</ul>"""
    }

    val content = """
        <main class="page page-wide">
            <header class="top-bar">
                ${brandMark(resourceProvider)}
                <span class="status-dot" aria-hidden="true"></span>
            </header>
            <section class="card">
                <h1>${resourceProvider.getString(R.string.local_sync_web_documents_heading).escapeHtml()}</h1>
                $messageHtml
                $listHtml
            </section>
            <section class="card">
                <h2>${resourceProvider.getString(R.string.local_sync_web_upload_heading).escapeHtml()}</h2>
                <p class="muted">${resourceProvider.getString(R.string.local_sync_web_upload_hint).escapeHtml()}</p>
                <form method="post" action="/documents/upload" enctype="multipart/form-data" id="uploadForm" class="dropzone">
                    <input type="file" name="file" id="fileInput" accept="application/pdf" required class="visually-hidden" />
                    <label for="fileInput" class="dropzone-inner">
                        ${iconUpload()}
                        <span class="dropzone-filename" data-filename>${
        resourceProvider.getString(R.string.local_sync_web_upload_dropzone_label).escapeHtml()
    }</span>
                    </label>
                    <button type="submit" class="btn btn-primary btn-block">${
        resourceProvider.getString(R.string.local_sync_web_upload_button).escapeHtml()
    }</button>
                </form>
            </section>
        </main>
        <script>$DROPZONE_SCRIPT</script>
    """.trimIndent()

    return htmlShell(
        lang = resourceProvider.htmlLangAttr(),
        dir = resourceProvider.htmlDirAttr(),
        title = resourceProvider.getString(R.string.local_sync_web_documents_title).escapeHtml(),
        bodyContent = content
    )
}

internal fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024.0)
}

/** Best-effort BCP-47-ish lang attribute derived from the app's current locale string resource. */
private fun ResourceProvider.htmlLangAttr(): String =
    getString(R.string.local_sync_web_html_lang)

private fun ResourceProvider.htmlDirAttr(): String =
    if (htmlLangAttr().startsWith("ar")) "rtl" else "ltr"
