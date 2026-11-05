# Chrome Web Store listing draft

**Name:** Download Video Helper

**Short description:** Send a video link from your browser to the Downloader Linux desktop app.

**Category:** Productivity

**Detailed description:**

Download Video Helper connects a Chromium browser to the Downloader desktop app for Linux. Right-click a video and choose “Send video to Downloader” to open that link in the desktop app. Review the formats provided by the source, then choose a video resolution or audio-only MP3 option in the app.

The extension requires the Downloader desktop app to be installed. The browser may show a confirmation when opening the app. For YouTube pages, the extension passes the page link so the desktop app can inspect available formats. For other sites, it passes a direct video URL when available, or the page link.

Only download content you own or are authorized to save. This extension does not bypass DRM or sign-in restrictions. Availability depends on the website and the desktop app's supported media sources.

**Single purpose:** Send a video or media-page link selected by the user to the installed Downloader desktop app.

**Permission justification — contextMenus:** Used to show the user-invoked “Send video to Downloader” item on video elements.

**Remote code:** None. The extension does not fetch or execute remote code.

**Data use disclosure:** The extension reads the clicked video's source URL and page URL only when the user selects its context-menu item. It opens the desktop application using that URL. The extension developer does not receive or store this information. The desktop app processes the URL locally and communicates with the source website as needed to analyze/download media. Do not publish until these statements are checked against the released application and the Web Store disclosure form.

**Required store artwork:** Upload a 128×128 PNG icon and at least one accurate screenshot of the working extension/app flow. The extension package includes its icon. Capture the screenshot after testing the browser context-menu to app handoff on a supported Chromium browser; do not use a mockup as a product screenshot.

**Publication note:** Google reviews every submission. Store availability is not guaranteed, particularly for use cases that could facilitate downloading copyrighted media without authorization. Keep the user-authorized-content limitation prominent and follow the current Chrome Web Store policies.
