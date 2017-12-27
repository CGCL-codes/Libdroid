package org.meicorl.unikernel.worker;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.icedrobot.daneel.loader.DaneelClassLoader;

import com.google.gson.Gson;

/**
 * A worker is a server program that runs in the form of unikernel
 * @author MeiCorl
 * @date  2017.05.18
 */
public class DynamicLinker {
    private final static int              Port              = 6034;
	private static ObjectInputStream      reader            = null;
	private static ObjectOutputStream     writer            = null;
    private static DaneelClassLoader      classLoader       = null;
    private static final String libAndroid_path="/libAndroid/Libdroid.jar";
    private static ServerSocket server = null;
    private static Socket client = null;

    public static void main(String[] args) throws IOException{

        /** load self-defined android libaray(Libdroid) **/
        URL[] url = { new URL(libAndroid_path)};
        URLClassLoader urlClassLoader = new URLClassLoader(url, DynamicLinker.class.getClassLoader());

        /** enable verification by default */
        System.setProperty("daneel.verify", "true");

        /** create server */
        server= new ServerSocket(Port);
        log("Waiting for client at port 6034:");

        /** waiting for client */
        client = server.accept();
        log("a client form " + client.getRemoteSocketAddress() + " has connected!");

        try{
	        InputStream is = client.getInputStream();
	        OutputStream os = client.getOutputStream();
            reader=new ObjectInputStream(is);
	        writer=new ObjectOutputStream(client.getOutputStream());
	        int command;
	        while(true)
	        {
	        	command=is.read();
	        	System.out.println("command:" + command);
	        	switch(command)
                {
	        	case ControlMessages.CONNECTION_RELEASED:
	        	case ControlMessages.PHONE_DISCONNECTION:
                    System.out.println("Connection has been released!");
                    System.out.println("goodbye! ^-^");
	        		return;

                case ControlMessages.APK_REGISTER:
                    /** read the url of apk file */
                    String appName = (String)reader.readObject();
                    String apkFilePath = ControlMessages.DIRSERVICE_APK_DIR + appName;

                    if (filePresent(apkFilePath)) {
                        log("APK present :" + appName);
                        os.write(ControlMessages.APK_PRESENT);
                    } else {
                        log("request APK :" + appName);
                        os.write(ControlMessages.APK_REQUEST);

                        // receive apk
                        receiveFile(reader, apkFilePath, true);
                        log("received APK");
                    }
                    classLoader = new DaneelClassLoader(urlClassLoader, new File(apkFilePath));
                    break;

	        	case ControlMessages.PHONE_COMPUTATION_REQUEST:
	        		receiveAndExcute();
	        		break;
	        		
	        	case ControlMessages.PHONE_COMPUTATION_REQUEST_WITH_FILE:
	        		reveiveFiles();
	        		receiveAndExcute();
	        		break;

	        	default:
	        		log("Unknown command,please try again!");
	        		break;
	        	}
	        }
        }catch(IOException | ClassNotFoundException e){
        	e.printStackTrace();
        }finally {
            releaseConnection();
        }
    }

    /**
     * Method to receive an apk or common files of an application that needs to be executed
     * @param objIn
     *            Object input stream to simplify retrieval of data
     * @return the file where the apk package is stored
     * @throws IOException
     *             throw up an exception thrown if socket fails
     * @author MeiCorl
     */
    private static void receiveFile(ObjectInputStream objIn, String apkFilePath, boolean isApk)
            throws IOException
    {
        // Get the length of the file receiving
        int fileLength = objIn.readInt();
        if(isApk)
            System.out.println("Read apkLength: " + fileLength);
        else
            System.out.println("Read fileLength: " + fileLength);

        // read file content
        byte[] tempArray = new byte[fileLength];
        objIn.readFully(tempArray);

        // Write it to the filesystem
        File file = new File(apkFilePath);
        if(!file.exists())
            file.createNewFile();
        FileOutputStream fout = new FileOutputStream(file);
        BufferedOutputStream bout = new BufferedOutputStream(fout);
        bout.write(tempArray);
        bout.close();
    }

    private static Class<?> resolveClassName(String className)
    {
	     try {
             return Class.forName(className,true,classLoader);
         }
	     catch (ClassNotFoundException e) {
	         e.printStackTrace();
	     }
	     return null;
    }
    
    private static void log(String s)
    {
    	System.out.println(s);
    }

    /**
     * @return
     * 		return true if file exists,otherwise return false
     */
    private static boolean filePresent(String filename) {
        File file = new File(filename);
        return file.exists();
    }

    private static void receiveAndExcute()
    {
    	Gson gson = new Gson();
        try {
            // read ClassName
            String className=(String)reader.readObject();
            log("Successfully read the className!");
            Class<?> clazz = classLoader.loadClass(className);

            String objStr = (String) reader.readObject();
            log("Successfully read the objToExecute!");
            Object objToExecute = gson.fromJson(objStr,clazz);

            // read MethodName
            String methodName = (String)reader.readObject();
            log("Successfully read the methodName");

            // read parameter types
            String[] tempTypes = (String[])reader.readObject();
            log("Successfully read the parameter types!");
            Class<?>[] pTypes = new Class[tempTypes.length];
            for (int i = 0; i < tempTypes.length; i++) {
              if (tempTypes[i].equals("int")) {
                pTypes[i] = Integer.TYPE;
              } else if (tempTypes[i].equals("long")) {
                pTypes[i] = Long.TYPE;
              } else if (tempTypes[i].equals("boolean")) {
                pTypes[i] = Boolean.TYPE;
              } else {
                pTypes[i] = resolveClassName(tempTypes[i]);
              }
            }

            // read parameters
            Object[]  pValues =new Object[pTypes.length];
            String tempValues = (String) reader.readObject();
            log("Successfully read the parameters!");
            JsonParser parser = new JsonParser();
            JsonArray jsonArray = parser.parse(tempValues).getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement el = jsonArray.get(i);
                pValues[i]  = gson.fromJson(el, pTypes[i]);
            }
           
            // get the method
            Method method = clazz.getMethod(methodName, pTypes);
            
            log("<@-@> doing computation tasks...please wait! ^-^");
            Object result= method.invoke(objToExecute,pValues);
            
            // send result back
            Class<?> retClass = method.getReturnType();
            String retClassName = retClass.getName();
            String resStr = gson.toJson(result);
            log("begin to send result back!");
            writer.writeObject(retClassName);
            writer.writeObject(resStr);
            log("Successfuly send result back!");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e){
            e.printStackTrace();
        } catch (IllegalAccessException e){
            e.printStackTrace();
        }  catch(NoSuchMethodException e){
            e.printStackTrace();
        } catch (IllegalArgumentException e){
            e.printStackTrace();
        } catch(InvocationTargetException e){
            e.printStackTrace();
        }
    }

    /** Close the Stream and Socket */
    private static void releaseConnection() {
        try {
            if(reader != null)
                reader.close();
            if(writer != null)
                writer.close();
            client.close();
            server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void reveiveFiles()
    {
    	
    }
}