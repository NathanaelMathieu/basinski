package app;

import java.util.Random;

import com.hamoid.VideoExport;

import app.components.MinimHelper;
import ddf.minim.AudioInput;
import ddf.minim.AudioPlayer;
import ddf.minim.AudioRecorder;
import ddf.minim.Minim;
import ddf.minim.analysis.FFT;
import processing.core.PApplet;
import processing.core.PImage;
import processing.video.Capture;
import processing.video.Movie;

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

	float micThreshold = 0.019675f;
	long[] rgbThreshold = new long[] { 139, 140, 141 };
	long[] rgb = new long[] { 0, 0, 0 };
	boolean thresholdingNeeded = true;
	boolean setup = false;
	int screenRecordingIndex = 0;
	int dlpStart = 13000; // start time in millis
	int i = 0;
	int uvi = 0;
	long j = 0;
	int currentAlpha = 0;
	int alphaMax = 255;
	int alphaMin = 0;
	float levelSum = 0;
	float dlpCompletion;
	float sunsetCompletion;

	public void init() {
		frame.removeNotify();
		frame.setUndecorated(true);
		frame.addNotify();
	}

	public void settings() {
		fullScreen(P3D, 1);
		System.setProperty("gstreamer.library.path", "C:\\Program Files\\processing\\video-beta\\library\\windows64");
	}

	public void setup() {
		frameRate(30);
		thread("loadFiles");
	}

	public void loadFiles() {

		{// start playing source video and streaming camera
			cam = getCamera("USB Camera", 30);
			cam.start();

			source = new Movie(this, "src\\video\\source\\sunset.mov");
			source.loop();
		}

		minim = new Minim(new MinimHelper());

		screenRecording = new VideoExport(this, "src\\video\\output\\screenRecording" + screenRecordingIndex + ".mov");
		screenRecording.startMovie();

		mic = minim.getLineIn(Minim.STEREO, 2048);
		micRecording = minim.createRecorder(mic, "audio\\output\\mic.wav");

		dlp = minim.loadFile("audio\\source\\01 dlp 1.1.wav");
		dlp.play(dlpStart);

		setup = true;
		background(0);
		return;
	}

	public synchronized void draw() {
		if (setup) {
			PImage webcam = webcam();
			PImage video = source.get();

			image(video, 0, 0);
			distortion(webcam);

			updateVariables();

		} else {
			background(0);
			fill(255);
			text("Loading...", width / 2 - textWidth("Loading...") / 2, height / 2 - 25);
		}
	}

	public void updateVariables() {
		if (thresholdingNeeded && uvi % 10 == 0)
			doThresholding(cam, 5000);
		uvi++;
		dlpCompletion = (float) dlp.position() / dlp.length();
		sunsetCompletion = source.time() / source.duration();
	}

	public void doThresholding(PImage cam, int length) {
//		System.out.printf("time=%f\n", (float) (dlp.position() - dlpStart) / 1000);

		if (dlp.position() < length + dlpStart) {
			cam.loadPixels();
			for (int pixel : cam.pixels) {
				rgb[0] += red(pixel);
				rgb[1] += green(pixel);
				rgb[2] += blue(pixel);
			}
			j += cam.pixels.length;
			levelSum += mic.mix.level();
			i++;
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

	public long[] getPixelThreshold(PImage p) {
		long[] rgbAverage = new long[] { 0, 0, 0 };
		p.loadPixels();
		for (int pixel : p.pixels) {
			rgbAverage[0] += red(pixel);
			rgbAverage[1] += green(pixel);
			rgbAverage[2] += blue(pixel);
		}
		for (int i = 0; i < rgbAverage.length; i++) {
			rgbAverage[i] /= p.pixels.length;
		}
		return rgbAverage;
	}

	public void distortion(PImage p) {

		r = new Random();
//		System.out.println(dlpCompletion);
		if (r.nextFloat() < dlpCompletion && alphaMin < 255) {
			alphaMin += r.nextInt(2);
		}

		if (observed(p)) {
			if (currentAlpha < alphaMax)
				currentAlpha += r.nextInt(2);
		} else {
			if (currentAlpha > alphaMin)
				currentAlpha -= r.nextInt(2);
		}

//		System.out.printf("alpha: %d, alphaMin: %d, alphaMax: %d\n", currentAlpha, alphaMin, alphaMax);

		p = alpha(p, currentAlpha);
		blend(p, 0, 0, p.pixelWidth, p.pixelHeight, 0, 0, width, height, BURN);
	}

	public boolean observed(PImage p) {
		if (mic.mix.level() > micThreshold * 3.5) {
//			System.out.printf("Mic Observed at %f with threshold %f\n", mic.mix.level(), micThreshold * 3.5);
			return true;
		} else {
			long[] pixelAverage = getPixelThreshold(p);
			for (int i = 0; i < pixelAverage.length; i++) {
				if (pixelAverage[i] > rgbThreshold[i] * 1.1 || pixelAverage[i] < rgbThreshold[i] * 0.9) {
//					System.out.printf("Cam Observed at %d with threshold %d\n", pixelAverage[i], rgbThreshold[i]);
					return true;
				}
			}
		}
		return false;
	}

	public void movieEvent(Movie m) {
		m.read();
	}

	public PImage alpha(PImage p, int alpha) {
		if (alpha > 255)
			alpha = 255;
		else if (alpha < 0)
			alpha = 0;
		p.loadPixels();
		for (int i = 0; i < p.pixels.length; i++) {
			p.pixels[i] = color(red(p.pixels[i]), green(p.pixels[i]), blue(p.pixels[i]), alpha);
		}
		p.updatePixels();
		return p;
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
			if (cam == null)
				cam = new Capture(this, width, height, cameras[0], framerate);

		}
		return cam;
	}

	public PImage webcam() {// draws the webcam's current frame
		if (cam.available()) {
			cam.read();
		}
		return cam;
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
