document.addEventListener('DOMContentLoaded', restoreOptions);
document.getElementById('saveBtn').addEventListener('click', saveOptions);

function saveOptions() {
    let url = document.getElementById('serverUrl').value || "http://192.168.1.254:5000";
    if (url.endsWith('/')) { url = url.slice(0, -1); }
    if (!url.startsWith('http')) { url = 'http://' + url; }
    
    browser.storage.local.set({ serverUrl: url }).then(() => {
        const status = document.getElementById('status');
        status.textContent = 'Options saved!';
        setTimeout(() => { status.textContent = ''; }, 2000);
    });
}

function restoreOptions() {
    browser.storage.local.get('serverUrl').then((result) => {
        if (result.serverUrl) {
            document.getElementById('serverUrl').value = result.serverUrl;
        }
    });
}
