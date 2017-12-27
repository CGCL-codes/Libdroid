package org.witness.sscphase1;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import java.lang.reflect.Method;

import org.jason.lxcoff.lib.ExecutionController;
import org.jason.lxcoff.lib.Remoteable;
import org.witness.securesmartcam.detect.GoogleFaceDetection;

/**
 *
 * @author jason
 */
public class OffDetect extends Remoteable {
	transient private static String TAG = "OffDetect";
	transient private ExecutionController controller;
	
	public OffDetect(ExecutionController controller){
		this.controller = controller;
	}
	
	public int GetFace(String fileName){
		Method toExecute;
		Class<?>[] paramTypes = {String.class};
		Object[] paramValues = {fileName};
		
		int result = 0;
		
		long starttime = System.nanoTime();
		try {
			toExecute = this.getClass().getDeclaredMethod("localGetFace", paramTypes);
			// I need to send file first, so invoke the 4-params-version execute
			result = (Integer) controller.execute(toExecute, paramValues, this, fileName);
		} catch (SecurityException e) {
			// Should never get here
			e.printStackTrace();
			throw e;
		} catch (NoSuchMethodException e) {
			// Should never get here
			e.printStackTrace();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		long dura = System.nanoTime()-starttime;
		Log.d(TAG, "FaceDetect Duration in OffDetect is " + dura/1000000 + "ms");

		return result;
	}
	
	public int localGetFace(String fileName){
		int numFaces = 0;
		BitmapFactory.Options bfo = new BitmapFactory.Options();
		bfo.inPreferredConfig = Bitmap.Config.RGB_565;

		Bitmap imageBitmap = BitmapFactory.decodeFile(fileName, bfo);

		try {
			Bitmap bProc = toGrayscale(imageBitmap);
			GoogleFaceDetection gfd = new GoogleFaceDetection(bProc.getWidth(),bProc.getHeight());
			long starttime = System.nanoTime();
			numFaces = gfd.findFaces(bProc);
			long dura = System.nanoTime() - starttime;
	        Log.d(TAG,"Num Faces Found: " + numFaces + ". Cost "+ dura/1000000 + " ms."); 
	        
		} catch(NullPointerException e) {
			numFaces = -1;
		}
		return numFaces;
	}   
	
	public Bitmap toGrayscale(Bitmap bmpOriginal)
	{        
	    int width, height;
	    height = bmpOriginal.getHeight();
	    width = bmpOriginal.getWidth();    

	    Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
	    Canvas c = new Canvas(bmpGrayscale);
	    Paint paint = new Paint();
	    ColorMatrix cm = new ColorMatrix();
	    cm.setSaturation(0);
	    
	    ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
	    
	    paint.setColorFilter(f);
	 
	    c.drawBitmap(bmpOriginal, 0, 0, paint);
	    return bmpGrayscale;
	}
	
    
	@Override
	public void copyState(Remoteable state) {

	}
}
