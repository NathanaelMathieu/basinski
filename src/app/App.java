package app;

import processing.core.*;
import processing.video.*;
import ddf.minim.*;
import ddf.minim.analysis.*;

import java.util.Random;

import com.hamoid.*;

import app.components.*;

public class App extends PApplet {

	Random r = new Random();

	Minim minim;
	AudioPlayer dlp;
	AudioInput mic;
	AudioRecorder micRecording;
	VideoExport screenRecording;
	Movie source;
	Movie previous;
	Capture cam;
	FFT fft;

	float micThreshold = 0f;
	long[] rgbThreshold;
	long[] rgb = new long[] { 0, 0, 0 };
	boolean thresholdingNeeded = true;
	int screenRecordingIndex = 0;
	int dlpStart = 13000; // start time in millis
	int i = 0;
	long j = 0;
	float levelSum = 0;

	public void settings() {
		fullScreen(P3D);
		System.setProperty("gstreamer.library.path", "C:\\Program Files\\processing\\video-beta\\library\\windows64");
	}

	public void setup() {
		frameRate(30);
		minim = new Minim(new MinimHelper());

		{// start playing source files
			dlp = minim.loadFile("audio\\source\\01 dlp 1.1.wav");
			dlp.play(dlpStart);

			source = new Movie(this, "src\\video\\source\\sunset.mov");
			source.loop();
		}

		cam = getCamera("USB Camera", 30);
		cam.start();

		screenRecording = new VideoExport(this, "src\\video\\output\\screenRecording" + screenRecordingIndex + ".mov");
		screenRecording.startMovie();

		mic = minim.getLineIn(Minim.STEREO, 2048);
		micRecording = minim.createRecorder(mic, "audio\\output\\mic.wav");
	}

	public void draw() {
		// noise here?
//		tint(255, 255);//blur the video's frames
		image(cam, 0, 0);
		if (thresholdingNeeded && i % 10 == 0)
			doThresholding(5000);
		screenRecording.saveFrame();
		i++;
	}

	public void loopHandler() {
	}

	public void doThresholding(int length) {
		System.out.printf("time=%f, duration=%f\n", source.time(), source.duration());

		if (source.time() + 0.5f < source.duration()) {
			loadPixels();
			for (int pixel : pixels) {
				rgb[0] += red(pixel);
				rgb[1] += green(pixel);
				rgb[2] += blue(pixel);
				j++;
			}
			levelSum += mic.mix.level();
//			i++;
		} else {
			thresholdingNeeded = false;
			micThreshold = levelSum / i;
			for (int i = 0; i < rgb.length; i++) {
				rgb[i] /= j;
			}
			rgbThreshold = rgb;
			System.out.printf("Thresholding complete. Values are:\nrgb: [%d,%d,%d], mic = %f", rgbThreshold[0],
					rgbThreshold[1], rgbThreshold[2], micThreshold);
		}

	}

	public void backgroundNoise() {

	}

	public void movieEvent(Movie m) {
		m.read();
	}

	public Capture getCamera(String name, int framerate) {
		Capture cam = null;
		String[] cameras = Capture.list();

		if (framerate > 30 || framerate < 1) {// 0 < framerate < 31
			framerate = 30;// default to 30
		}

		if (cameras.length == 0) {
			println("There are no cameras available for capture.");
			exit();
		} else {
			for (int i = 0; i < cameras.length; i++) {
				if (cameras[i].equals(name)) {
					cam = new Capture(this, width, height, cameras[i], framerate);
					break;
				}
			}
		}
		return cam;
	}

	public void video(Movie vid) {
		if (vid.available()) {
		}
	}

	public void webcam() {
		if (cam.available()) {
			cam.read();
		}
		image(cam, 0, 0);
	}

	public void stop() {
		screenRecording.endMovie();

		micRecording.endRecord();
		micRecording.save();

		dlp.close();

		minim.stop();

		cam.stop();
	}

	static public void main(final String[] args) {
		PApplet.main(new String[] { App.class.getName() });
	}
}
