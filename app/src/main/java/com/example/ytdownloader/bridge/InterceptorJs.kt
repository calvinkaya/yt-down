package com.example.ytdownloader.bridge

/**
 * Injected JavaScript — faithful port of ytproject/src/preload/youtubePreload.js:
 * the capture-phase click interceptor (which does NOT preventDefault, so the
 * watch page still opens) plus the watch-page autoplay guard (pause/mute any
 * non-user-gesture play). Calls `AndroidBridge.onVideoTap(id)`.
 */
object InterceptorJs {

    val SCRIPT: String = """
(function() {
  // ------------------------------------------------------------ autoplay guard
  var lastPlayerGesture = 0;

  function neutralizeVideo(v) {
    if (!v || v.tagName !== 'VIDEO') return;
    try { v.pause(); } catch (e) {}
    v.muted = true;
    v.removeAttribute('autoplay');
  }

  function neutralizeAllVideos() {
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) neutralizeVideo(vs[i]);
  }

  function isPlayerGestureTarget(el) {
    return !!(el && typeof el.closest === 'function' &&
      el.closest('.html5-video-player, video, .ytp-chrome-bottom, .ytp-large-play-button, .ytp-pause-overlay'));
  }

  document.addEventListener('pointerdown', function(e) {
    if (isPlayerGestureTarget(e.target)) lastPlayerGesture = Date.now();
  }, true);

  document.addEventListener('play', function(e) {
    var v = e.target;
    if (!v || v.tagName !== 'VIDEO') return;
    if (Date.now() - lastPlayerGesture < 1500) {
      v.muted = false;
      return;
    }
    neutralizeVideo(v);
  }, true);

  function startAutoplayGuard() {
    if (!document.documentElement) return;
    new MutationObserver(function(muts) {
      for (var i = 0; i < muts.length; i++) {
        var added = muts[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          var n = added[j];
          if (n.nodeType !== 1) continue;
          if (n.tagName === 'VIDEO') neutralizeVideo(n);
          else if (typeof n.querySelectorAll === 'function') {
            var vs = n.querySelectorAll('video');
            for (var k = 0; k < vs.length; k++) neutralizeVideo(vs[k]);
          }
        }
      }
    }).observe(document.documentElement, { childList: true, subtree: true });
    neutralizeAllVideos();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startAutoplayGuard, { once: true });
  } else {
    startAutoplayGuard();
  }

  // ------------------------------------------------------------ click interceptor
  var HOST_RE = /(?:^|\.)youtube\.com${'$'}/i;

  function videoIdFromHref(href) {
    try {
      var u = new URL(href, location.href);
      if (!HOST_RE.test(u.hostname)) return null;
      if (u.pathname.indexOf('/watch') === 0) return u.searchParams.get('v');
      var m = u.pathname.match(/^\/(?:shorts|live)\/([\w-]{6,})/);
      return m ? m[1] : null;
    } catch (e) { return null; }
  }

  document.addEventListener('click', function(e) {
    var target = e.target;
    if (!target || typeof target.closest !== 'function') return;
    var anchor = target.closest('a[href]');
    if (!anchor) return;
    var videoId = videoIdFromHref(anchor.href);
    if (!videoId) return;
    // Start the download, but let YouTube's router handle the click so the
    // watch page actually opens (no preventDefault / stopPropagation).
    try { window.AndroidBridge.onVideoTap(videoId); } catch (err) {}
  }, true);
})();
    """.trimIndent()
}
