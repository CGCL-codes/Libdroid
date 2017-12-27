package org.meicorl.unikernel.worker;

/**
 * Created by meicorl on 2017/5/16.
 */
public class ControlMessages
{
     public static final int        APK_REGISTER                            = 21;
     public static final int        APK_PRESENT 			     			= 22;
     public static final int        APK_REQUEST 						    = 23;
     public static final int        PHONE_DISCONNECTION                     = 32;
     public static final int		PHONE_COMPUTATION_REQUEST		        = 40;
     public static final int		PHONE_COMPUTATION_REQUEST_WITH_FILE		= 41;
     public static final int        CONNECTION_RELEASED                     = -1;
     // the apk path in the unikernel
     public static final String     DIRSERVICE_APK_DIR			= "/app_hub/apks/";
     // the file path in the unikernel
     public static final String     DIRSERVICE_RESOURCE_DIR     = "/apb_hub/resources/";
}
