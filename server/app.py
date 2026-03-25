from flask import Flask, send_file, request, redirect, jsonify
import yt_dlp
import os

app = Flask(__name__)

# Start with no video
CURRENT_YOUTUBE_URL = ""

@app.route('/video')
def video():
    video_path = os.path.join(os.path.dirname(__file__), 'sample.mp4')
    if not os.path.exists(video_path):
        return "sample.mp4 not found in server directory", 404
    
    # send_file with conditional=True automatically handles Range requests
    return send_file(video_path, mimetype='video/mp4', as_attachment=False, conditional=True)

@app.route('/set_youtube_url')
def set_youtube_url():
    global CURRENT_YOUTUBE_URL
    url = request.args.get('url')
    if url:
        CURRENT_YOUTUBE_URL = url
        print(f"Firefox extension updated Glass video URL to {url}")
        return f"Updated glass video URL to {url}", 200
    return "Missing url parameter", 400

@app.route('/get_youtube_url')
def get_youtube_url():
    # Android will poll this to know if the video changed
    return jsonify({"url": CURRENT_YOUTUBE_URL})

@app.route('/youtube')
def youtube():
    # Use the globally set URL or fallback to the provided one
    video_url = request.args.get('url', CURRENT_YOUTUBE_URL)
    
    # '22/18/b' means try format 22 (720p mp4), then 18 (360p mp4), then the best pre-merged single file
    ydl_opts = {
        'format': '22/18/b', 
        'quiet': True,
        'no_warnings': True
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(video_url, download=False)
            stream_url = info['url']
            print(f"Redirecting Android device to YouTube stream: {stream_url[:50]}...")
            return redirect(stream_url)
    except Exception as e:
        return f"Error extracting video: {str(e)}", 500

if __name__ == '__main__':
    # Run on all interfaces so the Android device can connect
    app.run(host='0.0.0.0', port=5000, debug=True)
