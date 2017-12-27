package org.witness.securesmartcam.detect;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.util.Log;

public class GoogleFaceDetection implements FaceDetection {

	public static final String LOGTAG = "GoogleFaceDetection";

	public static int MAX_FACES = 100;

	Face[] faces = new Face[MAX_FACES];
	FaceDetector faceDetector;

	int numFaces = 0;

	public final static float CONFIDENCE_FILTER = .15f;

	public GoogleFaceDetection(int width, int height) {
		faceDetector = new FaceDetector(width, height, MAX_FACES);
	}

	public int findFaces(Bitmap bmp) {

		for(int m = 0; m < 3; m++){
			numFaces = faceDetector.findFaces(bmp, faces);
		}
		
		/*boolean symmetry = true;
		
		 * for (int i = 0; i < numFaces; i++) { if (faces[i].confidence() >
		 * CONFIDENCE_FILTER) { RectF facea = getRect(faces[i]); // detect the
		 * face intersect for (int j = (int) facea.left; j < (int) facea.right;
		 * j++) { for (int k = (int) facea.top; k < (int) facea.bottom; k++) {
		 * int pixel = bmp.getPixel(j, k);// ARGB int red = Color.red(pixel); //
		 * same as (pixel >> 16) int green = Color.green(pixel); // same as
		 * (pixel >> 8) int blue = Color.blue(pixel); // same as (pixel & 0xff)
		 * if (GetRGBDiff(red, green, blue) > 50) { blackwhite = false; } } } }
		 * }
		 
		for (int i = 0; i < bmp.getWidth(); i++) {
			for (int j = 0; j < bmp.getHeight(); j++) {
				int pixel = bmp.getPixel(i, j);// ARGB
				int red = Color.red(pixel); // same as (pixel >> 16)
				int green = Color.green(pixel); // same as (pixel >> 8)
				int blue = Color.blue(pixel); // same as (pixel & 0xff)
				int pixel2 = bmp.getPixel(bmp.getWidth() - i - 1, j);
				int red2 = Color.red(pixel2); // same as (pixel >> 16)
				int green2 = Color.green(pixel); // same as (pixel >> 8)
				int blue2 = Color.blue(pixel2); // same as (pixel & 0xff)
				if (red != red2 || green != green2 || blue != blue2) {
					symmetry = false;
				}
			}
		}
		if (symmetry) {
			Log.d(LOGTAG, "The picture is black and white");
		}*/
		return numFaces;
	}

	public int GetRGBDiff(int r, int g, int b) {
		return Math.max(Math.max(Math.abs(r - g), Math.abs(r - b)),
				Math.abs(g - b));
	}

	private RectF getRect(Face face) {
		PointF midPoint = new PointF();

		float eyeDistance = face.eyesDistance();
		face.getMidPoint(midPoint);

		// Create Rectangle

		float widthBuffer = eyeDistance * 1.5f;
		float heightBuffer = eyeDistance * 2f;
		RectF faceRect = new RectF((midPoint.x - widthBuffer),
				(midPoint.y - heightBuffer), (midPoint.x + widthBuffer),
				(midPoint.y + heightBuffer));
		// Log.d(LOGTAG, (midPoint.x - widthBuffer) + " " +
		// (midPoint.y-heightBuffer) + " " + (midPoint.x+widthBuffer) + " " +
		// (midPoint.y+heightBuffer));

		return faceRect;
	}

	@Override
	public ArrayList<DetectedFace> getFaces(int numberFound) {
		// TODO Auto-generated method stub
		return null;
	}

}
