package org.meicorl.unikernel.Scheduler;

/**
 * Created by meicorl on 2017/5/16.
 */
class ControlMessages
{
    public static final int UnikernelStatus_available           = 0;
    public static final int UnikernelStatus_unAvailable         = 1;

    public static final int PING								= 11;
    public static final int PONG								= 12;

    // Communication Phone <-> Clone
    public static final int APK_REGISTER 						= 21;
    public static final int APK_PRESENT 						= 22;
    public static final int APK_REQUEST 						= 23;

    public static final int 	PHONE_CONNECTION 				= 30;
    public static final int 	PHONE_AUTHENTICATION			= 31;
    public static final int     PHONE_DISCONNECTION             = 32;
    public static final int     CONNECTION_RELEASED             = -1;

    public static final int		PHONE_COMPUTATION_REQUEST		        = 40;
    public static final int		PHONE_COMPUTATION_REQUEST_WITH_FILE		= 41;
    public static final int		SEND_FILE_REQUEST			            = 42;

    public static final String  IMAGE_HUB                       = "/image_hub/";
    public static final String  DIRSERVICE_RESOURCE_DIR         = "/opt/lampp/htdocs/interpub/resources/";
    public static final String  LOG_FILE_PATH                   = "/opt/lampp/htdocs/interpub/ExecRecord/";
}
