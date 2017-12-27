package xiaowang.filebrowser.biz;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Expand;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import de.innosystec.unrar.Archive;
import de.innosystec.unrar.rarfile.FileHeader;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Toast;
import xiaowang.filebrowser.bean.FileBrowser;
import xiaowang.filebrowser.bean.R;

public class FileOperator {
	public final static int NAME_ASC=0;
	public final static int NAME_DESC=1;
	public final static int SIZE_ASC=2;
	public final static int SIZE_DESC=3;
	public final static int SUFFIX_ASC=4;
	public final static int SUFFIX_DESC=5;
	
	public static List<File> fileList;
	/**
	 * @param context 上下文对象
	 * @param path 目录路径
	 * @param fs1 文件夹排序方式
	 * @param fs2 文件排序方式
	 * @param firstSort 文件和文件夹谁排先
	 * @return ArrayList
	 */
	public static ArrayList<HashMap<String, Object>> fileList(Context context,File path,int fs1,int fs2,boolean firstSort){
		File[] files = path.listFiles();
		fileList = new ArrayList<File>();
		List<File> dir=null;
		List<File> file=null;
		if(fs1!=-1){
			dir=new ArrayList<File>();
			file=new ArrayList<File>();
			for(File f:files){
				if(f.isDirectory()){
					dir.add(f);
				}else {
					file.add(f);
				}
			}
			if(firstSort){
				dirSort(dir,fs1);
				for(File fd:dir){
					fileList.add(fd);
				}
				fileSort(file,fs2);
				for(File ff:file){
					fileList.add(ff);
				}
			}else {
				fileSort(file,fs2);
				for(File ff:file){
					fileList.add(ff);
				}
				dirSort(dir,fs1);
				for(File fd:dir){
					fileList.add(fd);
				}
			}
			
		}else{
			for(File f:files){
				fileList.add(f);
			}
			fileSort(fileList, fs2);
		}
		
		int i=0;
		int  total=files.length;
		files=new File[total];
		
		List<Object> fileIcon=new ArrayList<Object>();
		for(;i<total;i++){
			files[i]=fileList.get(i);
		}
		fileList.clear();
		for(i=0;i<total;i++){
			String end=files[i].getName().substring(files[i].getName().lastIndexOf(".")+1).toLowerCase();
			if(files[i].isDirectory()){
				fileIcon.add(R.drawable.directory);
			}else if(end.equals("txt") || end.equals("java") || end.equals("xml")
					|| end.equals("html") || end.equals("css")||end.equals("properties")){
				fileIcon.add(R.drawable.txt_file);
			}else if(end.equals("umd")){
				fileIcon.add(R.drawable.umd);
			}else if(end.equals("epub")){
				fileIcon.add(R.drawable.epub);
			}else if(end.equals("doc")||end.equals("docx")){
				fileIcon.add(R.drawable.word_icon);
			}else if(end.equals("zip")||end.equals("rar")||end.equals("7z")){
				fileIcon.add(R.drawable.zip_icon);
			}else if(end.equals("pdf")){
				fileIcon.add(R.drawable.pdf_icon);
			}else if(end.equals("mp3")||end.equals("mid")){
				fileIcon.add(R.drawable.music);
			}else if(end.equals("rmvb")||end.equals("wmv")||end.equals("avi")||end.equals("dat")
					||end.equals("ts")||end.equals("mp4")||end.equals("rm")||end.equals("3gp")
					||end.equals("mpeg")||end.equals("mov")||end.equals("vob")||end.equals("mkv")
					||end.equals("flv")||end.equals("f4v")){
				fileIcon.add(R.drawable.video_player);
			}else if(end.equals("apk")){
				Drawable drawable=ApkTools.geTApkIcon(context, files[i].getAbsolutePath());
				fileIcon.add(drawable);
				drawable=null;
			}else if(end.equals("jpeg")||end.equals("png")||end.equals("bmp")||end.equals("jpg")){
				
				fileIcon.add(files[i].getAbsolutePath());
			}else{
				fileIcon.add(R.drawable.others);
			}
			fileList.add(files[i]);
		}
		int sub_files=0;
		ArrayList<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();
		for(i=0;i<fileList.size();i++){
			HashMap<String, Object> hashMap=new HashMap<String, Object>();
			hashMap.put("fileIcon",fileIcon.get(i));
			
			if(fileList.get(i).isFile()){
				hashMap.put("fileName",fileList.get(i).getName());
				hashMap.put("fileInfo", new Date(fileList.get(i).lastModified())+"  "+DataFormat.sizeFormat(fileList.get(i).length()));
				hashMap.put("checkBox", false);
			}else{
				if(fileList.get(i).canRead()){
					sub_files=fileList.get(i).listFiles().length;
				}
				hashMap.put("fileName", fileList.get(i).getName()+"(子文件"+sub_files+"个)");
				hashMap.put("fileInfo", new Date(fileList.get(i).lastModified()));
				hashMap.put("checkBox", false);
			}
			
			data.add(hashMap);
		}
		return data;
		
	}
	
	public static List<List<File>> fileCategory(File[] files) {
		List<List<File>> fileList = new ArrayList<List<File>>();
		
		List<File> dir=new ArrayList<File>();
		List<File> file=new ArrayList<File>();
		
		for(File f:files){
			if(f.isDirectory()){
				dir.add(f);
			}else {
				file.add(f);
			}
		}
		Collections.sort(dir);
		Collections.sort(file);
		fileList.add(dir);
		fileList.add(file);
		return fileList;
	}
	
//	public enum FileSort{
//		name_asc,name_desc,size_asc,size_desc,suffix_asc,suffix_desc
//	}
	
	public static void fileSort(List<File> list,int fs){
		switch (fs) {
		case NAME_ASC:
			Collections.sort(list);
			break;
		case NAME_DESC:
			Collections.sort(list);
			Collections.reverse(list);
			break;
		case SIZE_ASC:
			Collections.sort(list, new Comparator<File>() {

				public int compare(File f1, File f2) {
					int i=f1.length()>f2.length()?1:f1.length()<f2.length()?-1:0;
					return i;
				}

				
			});
			break;
		case SIZE_DESC:
			Collections.sort(list, new Comparator<File>(){

				public int compare(File f1, File f2) {
					int i=f1.length()>f2.length()?-1:f1.length()<f2.length()?1:0;
					return i;
				}
				
			});
			break;
		case SUFFIX_ASC:
			Collections.sort(list, new Comparator<File>(){

				public int compare(File f1, File f2) {
					String suffix1=getSuffix(f1);
					String suffix2=getSuffix(f2);
					return suffix1.compareToIgnoreCase(suffix2);
				}
				
			});
			break;
		case SUFFIX_DESC:
			Collections.sort(list, new Comparator<File>() {

				public int compare(File f1, File f2) {
					String suffix1=getSuffix(f1);
					String suffix2=getSuffix(f2);
					return suffix2.compareToIgnoreCase(suffix1);
				}
			});
			break;
		default:
			break;
		}
	}
	
	public static String getSuffix(File file){
		return file.getName().substring(file.getName().lastIndexOf(".")+1);
	}
	
	public static void dirSort(List<File> list,int fs){
		switch(fs){
		case NAME_ASC:
			Collections.sort(list);
			break;
		case NAME_DESC:
			Collections.sort(list);
			Collections.reverse(list);
			break;
		case SIZE_ASC:
			Collections.sort(list, new Comparator<File>() {

				public int compare(File d1, File d2) {
					int i=-1;
					if(d1.canRead()&&d2.canRead()){
						int d1_size=d1.listFiles().length;
						int d2_size=d2.listFiles().length;
						i=d1_size>d2_size?1:d1_size<d2_size?-1:0;
					}
					
					return i;
				}
			});
			break;
		case SIZE_DESC:
			Collections.sort(list, new Comparator<File>() {

				public int compare(File d1, File d2) {
					int i=1;
					if(d1.canRead()&&d2.canRead()){
						int d1_size=d1.listFiles().length;
						int d2_size=d2.listFiles().length;
						i=d1_size>d2_size?-1:d1_size<d2_size?1:0;
					}
					
					return i;
				}
			});
			break;
		}
	}
	public static void mkDir(File file,String name,FileBrowser fb){
		File dir=new File(file.getParentFile().getAbsolutePath()+"/"+name);
		if(!dir.exists()){
			dir.mkdirs();
		}
		fb.fileList(file.getParentFile());
	}
	public static void delete(File file, FileBrowser fb){
		if(file.canWrite()){
			CMDExecutor.run("/system/bin", new String[]{"/system/bin/rm","-r",file.getAbsolutePath()});
			Toast.makeText(fb, file.getName()+"已删除", 2000).show();
		}else{
			Toast.makeText(fb, file.getName()+"无法删除", 2000).show();
		}
	}
	public static void move(File file1,File file2){
		if(file2.isDirectory()){
			CMDExecutor.run("/system/bin", new String[]{"/system/bin/mv",file1.getAbsolutePath(),file2.getAbsolutePath()});
		}else{
			CMDExecutor.run("/system/bin", new String[]{"/system/bin/mv",file1.getAbsolutePath(),file2.getParent()});
		}
	}
	
	public static String copy(File file1,File file2){
		String info;
		if(file2.isDirectory()){
			info=CMDExecutor.run("/system/xbin", new String[]{"/system/xbin/cp","-r",file1.getAbsolutePath(),file2.getAbsolutePath()});
		}else {
			info=CMDExecutor.run("/system/xbin", new String[]{"/system/xbin/cp","-r",file1.getAbsolutePath(),file2.getParent()});
		}
		return info;
	}
	
	/** 
	   * 复制文件（夹）到一个目标文件夹 
	   * 
	   * @param resFile      源文件（夹） 
	   * @param objFolderFile 目标文件夹 
	   * @throws IOException 异常时抛出 
	   */ 
	public static void copy2(File resFile, File objFolderFile) throws IOException { 
	          if (!resFile.exists()) return; 
	          if (!objFolderFile.exists()) objFolderFile.mkdirs(); 
	          if (resFile.isFile()) { 
	                   File objFile = new File(objFolderFile.getPath() + File.separator + resFile.getName()); 
	                  //复制文件到目标地 
	                   InputStream ins = new FileInputStream(resFile); 
	                   FileOutputStream outs = new FileOutputStream(objFile); 
	                  byte[] buffer = new byte[1024 * 512]; 
	                  int length; 
	                  while ((length = ins.read(buffer)) != -1) { 
	                           outs.write(buffer, 0, length); 
	                   } 
	                   ins.close(); 
	                   outs.flush(); 
	                   outs.close(); 
	           } else { 
	                   String objFolder = objFolderFile.getPath() + File.separator + resFile.getName(); 
	                   File _objFolderFile = new File(objFolder); 
	                   _objFolderFile.mkdirs(); 
	                  for (File sf : resFile.listFiles()) { 
	                           copy2(sf, new File(objFolder)); 
	                   } 
	           } 
	   }

	/**
	 * 解压zip格式压缩包
	 * 对应的是ant.jar
	 */
	private static void unzip(String sourceZip,String destDir) throws Exception{
		try{
			Project p = new Project();
			Expand e = new Expand();
			e.setProject(p);
			e.setSrc(new File(sourceZip));
			e.setOverwrite(false);
			e.setDest(new File(destDir));
			/*
			ant下的zip工具默认压缩编码为UTF-8编码，
			而winRAR软件压缩是用的windows默认的GBK或者GB2312编码
			所以解压缩时要制定编码格式
			*/
			e.setEncoding("gbk");
			e.execute();
		}catch(Exception e){
			throw e;
		}
	}
	/**
	 * 解压rar格式压缩包。
	 * 对应的是java-unrar-0.3.jar
	 */
	private static void unrar(String sourceRar,String destDir) throws Exception{
		Archive a = null;
		FileOutputStream fos = null;
		try{
			a = new Archive(new File(sourceRar));
			FileHeader fh = a.nextFileHeader();
			while(fh!=null){
				if(!fh.isDirectory()){
					//1 根据不同的操作系统拿到相应的 destDirName 和 destFileName
					String compressFileName = fh.getFileNameString().trim();
					String destFileName = "";
					String destDirName = "";
					//非windows系统
					if(File.separator.equals("/")){
						destFileName = destDir + compressFileName.replaceAll("\\\\", "/");
						destDirName = destFileName.substring(0, destFileName.lastIndexOf("/"));
					//windows系统	
					}else{
						destFileName = destDir + compressFileName.replaceAll("/", "\\\\");
						destDirName = destFileName.substring(0, destFileName.lastIndexOf("\\"));
					}
					//2创建文件夹
					File dir = new File(destDirName);
					if(!dir.exists()||!dir.isDirectory()){
						dir.mkdirs();
					}
					//3解压缩文件
					fos = new FileOutputStream(new File(destFileName));
					a.extractFile(fh, fos);
					fos.close();
					fos = null;
				}
				fh = a.nextFileHeader();
			}
			a.close();
			a = null;
		}catch(Exception e){
			throw e;
		}finally{
			if(fos!=null){
				try{fos.close();fos=null;}catch(Exception e){e.printStackTrace();}
			}
			if(a!=null){
				try{a.close();a=null;}catch(Exception e){e.printStackTrace();}
			}
		}
	}
	/**
	 * 解压缩
	 */
	public static void deCompress(String sourceFile,String destDir) throws Exception{
		//保证文件夹路径最后是"/"或者"\"
		
		//根据类型，进行相应的解压缩
		String type = sourceFile.substring(sourceFile.lastIndexOf(".")+1);
		if(type.equals("zip")){
			FileOperator.unzip(sourceFile, destDir);
		}else if(type.equals("rar")){
			char lastChar = destDir.charAt(destDir.length()-1);
			if(lastChar!='/'&&lastChar!='\\'){
				destDir += File.separator;
			}
			FileOperator.unrar(sourceFile, destDir);
		}else{
			throw new Exception("只支持zip和rar格式的压缩包！");
		}
	}
	public static void CreateZipFile(String filePath, String zipFilePath) {
		   FileOutputStream fos = null;
		   ZipOutputStream zos = null;
		   try {
		    fos = new FileOutputStream(zipFilePath);
		    zos = new ZipOutputStream(fos);
		    //设置压缩过程中文件/路径名的字符编码格式
		    zos.setEncoding("GBK");
		    writeZipFile(new File(filePath), zos, "");
		   } catch (FileNotFoundException e) {
		    e.printStackTrace();
		   } finally {
		    try {
		     if (zos != null)
		      zos.close();
		    } catch (IOException e) {
		     e.printStackTrace();
		    }
		    try {
		     if (fos != null)
		      fos.close();
		    } catch (IOException e) {
		     e.printStackTrace();
		    }
		   }

		}

		private static void writeZipFile(File f, ZipOutputStream zos, String hiberarchy) {
		   if (f.exists()) {
		    if (f.isDirectory()) {
		     hiberarchy += f.getName() + "/";
		     File[] fif = f.listFiles();
		     for (int i = 0; i < fif.length; i++) {
		      writeZipFile(fif[i], zos, hiberarchy);
		     }

		    } else {
		     FileInputStream fis = null;
		     try {
		      fis = new FileInputStream(f);
		      ZipEntry ze = new ZipEntry(hiberarchy + f.getName());
		      zos.putNextEntry(ze);
		      byte[] b = new byte[1024];
		      while (fis.read(b) != -1) {
		       zos.write(b);
		       b = new byte[1024];
		      }
		     } catch (FileNotFoundException e) {
		      e.printStackTrace();
		     } catch (IOException e) {
		      e.printStackTrace();
		     } finally {
		      try {
		       if (fis != null)
		        fis.close();
		      } catch (IOException e) {
		       e.printStackTrace();
		      }
		     }

		    }
		   }
		}
		/**
		 * @param srcFile 源文件（夹）
		 * @param dirFile 目标文件夹
		 */
		public static void copy3(File srcFile,File dirFile){
			if(srcFile.isFile()){
				try {
					FileUtils.copyFileToDirectory(srcFile, dirFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else {
				try {
					FileUtils.copyDirectoryToDirectory(srcFile, dirFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}		
		}
		
}