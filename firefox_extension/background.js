browser.browserAction.onClicked.addListener((tab) => {
  if (tab.url.includes("youtube.com/watch") || tab.url.includes("youtu.be/")) {
    browser.storage.local.get('serverUrl').then((result) => {
      const serverUrl = result.serverUrl || "http://192.168.1.254:5000";
      const targetUrl = `${serverUrl}/set_youtube_url?url=${encodeURIComponent(tab.url)}`;
      fetch(targetUrl)
        .then(response => console.log("Sent URL to Glass Server", tab.url))
        .catch(error => console.error("Error sending to Glass Server:", error));
    });
  } else {
    console.log("Not a YouTube video page!");
  }
});
