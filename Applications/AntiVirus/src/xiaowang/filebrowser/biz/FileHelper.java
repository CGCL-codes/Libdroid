package xiaowang.filebrowser.biz;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

public class FileHelper {

	private Context context;
	
	public static ArrayList<String> zipList=new ArrayList<String>();

	/** SD卡是否存在 **/

	private boolean hasSD = false;

	/** SD卡的路径 **/

	private String SDPATH;
	private static final int buffer = 2048;   

	
	/** 当前程序包的路径 **/

	private String FILESPATH;

	public FileHelper(Context context) {

		this.context = context;

		hasSD = Environment.getExternalStorageState().equals(

		android.os.Environment.MEDIA_MOUNTED);

		SDPATH = Environment.getExternalStorageDirectory().getPath();

		FILESPATH = this.context.getFilesDir().getPath();

	}
	/*
	 * 取得sd卡上所以的文件
	 * 
	 */
	public  ArrayList<String> getAllFiles(File root){
		File files[] = root.listFiles();
		for(File f:files){
			if(f.isDirectory()&&f.listFiles()!=null){
				getAllFiles(f);
			}
			else{
				if (f.getPath().contains("/zip")) {
					Log.d("haha", f.getPath());
					zipList.add(f.getPath());
				}
				
			}
		}
		return zipList;
    }
	/*
	 * 解压缩文件
	 * 
	 */
	 public static void unZip(String path)   
	    {  
	        int count = -1;  
	        int index = -1;  
	          
	        String savepath = "";  
	          
	        savepath = path.substring(0, path.lastIndexOf("."));  
	        try   
	        {  
	            BufferedOutputStream bos = null;  
	            ZipEntry entry = null;  
	            FileInputStream fis = new FileInputStream(path);   
	            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));  
	              
	            while((entry = zis.getNextEntry()) != null)   
	            {  
	                byte data[] = new byte[buffer];   
	  
	                String temp = entry.getName();  
	                index = temp.lastIndexOf("/");  
	                if(index > -1)  
	                    temp = temp.substring(index+1);  
	                String tempDir = savepath +"/zip/";  
	                File dir=new File(tempDir);
	                dir.mkdirs();
	                temp=tempDir+ temp;
	                File f = new File(temp);  
	                f.createNewFile();  
	  
	                FileOutputStream fos = new FileOutputStream(f);  
	                bos = new BufferedOutputStream(fos, buffer);  
	                  
	                while((count = zis.read(data, 0, buffer)) != -1)   
	                {  
	                    bos.write(data, 0, count);  
	                }  
	  
	                bos.flush();  
	                bos.close();  
	            }  
	  
	            zis.close();  
	  
	        } catch (Exception e) {  
	            e.printStackTrace();  
	        }  
	    }  

	/**
	 * 
	 * 在SD卡上创建文件
	 * 
	 * 
	 * 
	 * @throws IOException
	 */

	public File createSDFile(String fileName) throws IOException {

		File file = new File(SDPATH + "//" + fileName);

		if (!file.exists()) {

			file.createNewFile();

		}

		return file;

	}
	/*
	 * 复制文件
	 */
	public void fileCopy(String sFile, String oFile) {
		  File file = new File(sFile);

		  if (!file.exists()) {
		   System.out.println(sFile + " not have");
		   return;
		  }
		  File fileb = new File(oFile);

		  if (file.isFile()) {
		   FileInputStream fis = null;
		   FileOutputStream fos = null;
		   try {
		    fis = new FileInputStream(file);
		    fos = new FileOutputStream(fileb);

		    byte[] bb = new byte[ (int) file.length()];
		    fis.read(bb);
		    fos.write(bb);

		   } catch (IOException e) {
		    e.printStackTrace();
		   } finally {
		    try {
		     fis.close();
		     fos.close();
		    } catch (IOException e) {
		     e.printStackTrace();
		    }
		   }
		  } else if (file.isDirectory()) {
		   if (!fileb.exists()) {
		    fileb.mkdir();
		   }
		   String[] fileList;
		   fileList = file.list();
		   for (int i = 0; i < fileList.length; i++) {
		    fileCopy(sFile + "/" + fileList[i], oFile + "/" + fileList[i]);
		   }
		  }
		 }

	/**
	 * 
	 * 删除SD卡上的文件
	 * 
	 * 
	 * 
	 * @param fileName
	 */

	public boolean deleteSDFile(String filepath) {

		File file = new File(filepath);
        if (file == null || !file.exists() ) {
        	return false;
		}
		if ( file.isDirectory()){
			delFolder(file.getPath());
			return true;
		}
		return file.delete();

	}
	//删除文件夹
	 //param folderPath 文件夹完整绝对路径
	 
	     public  void delFolder(String folderPath) {
	      try {
	         delAllFile(folderPath); //删除完里面所有内容
	         String filePath = folderPath;
	         filePath = filePath.toString();
	         java.io.File myFilePath = new java.io.File(filePath);
	         myFilePath.delete(); //删除空文件夹
	      } catch (Exception e) {
	        e.printStackTrace(); 
	      }
	 }
	 
	//删除指定文件夹下所有文件
	 //param path 文件夹完整绝对路径
	    public  boolean delAllFile(String path) {
	        boolean flag = false;
	        File file = new File(path);
	        if (!file.exists()) {
	          return flag;
	        }
	        if (!file.isDirectory()) {
	          return flag;
	        }
	        String[] tempList = file.list();
	        File temp = null;
	        for (int i = 0; i < tempList.length; i++) {
	           if (path.endsWith(File.separator)) {
	              temp = new File(path + tempList[i]);
	           } else {
	               temp = new File(path + File.separator + tempList[i]);
	           }
	           if (temp.isFile()) {
	              temp.delete();
	           }
	           if (temp.isDirectory()) {
	              delAllFile(path + "/" + tempList[i]);//先删除文件夹里面的文件
	              delFolder(path + "/" + tempList[i]);//再删除空文件夹
	              flag = true;
	           }
	        }
	        return flag;
	      }

	/*
	 * 文件重命名
	 */
	public boolean renameFile(String filePath, String newName) {

		File file = new File(filePath);
		return file.renameTo(new File(filePath.substring(0, filePath.lastIndexOf("/")+1)+newName));

	}

	/*
	 * 计算文件或文件夹大小
	 */
	public long getFileSize(File f) {

		long size = 0;

		if (f.isDirectory()) {
			File flist[] = f.listFiles();
			for (int i = 0; i < flist.length; i++) {

				if (flist[i].isDirectory()) {

					size = size + getFileSize(flist[i]);

				} else {

					size = size + flist[i].length();

				}

			}
		} else {
			size = f.length();
		}

		return size;

	}

	/**
	 * 
	 * 读取SD卡中文本文件
	 * 
	 * 
	 * 
	 * @param fileName
	 * 
	 * @return
	 */

	public String readSDFile(String fileName) {

		StringBuffer sb = new StringBuffer();

		File file = new File(SDPATH + "//" + fileName);

		try {

			FileInputStream fis = new FileInputStream(file);

			int c;

			while ((c = fis.read()) != -1) {

				sb.append((char) c);

			}

			fis.close();

		} catch (FileNotFoundException e) {

			e.printStackTrace();

		} catch (IOException e) {

			e.printStackTrace();

		}

		return sb.toString();

	}

	public String getFILESPATH() {

		return FILESPATH;

	}

	public String getSDPATH() {

		return SDPATH;

	}

	public boolean hasSD() {

		return hasSD;

	}

}