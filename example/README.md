# Step 1
Package the Libdroid and Unikernel-Server into [jar format](https://github.com/CGCL-codes/Libdroid/blob/master/example/DynamicLinker)
```
$ jar cvf Libdroid.jar Libdroid
$ jar cvf Unikernel-Server.jar Unikernel-Server
```
# Step 2
Create a configuration file [module.py](https://github.com/CGCL-codes/Libdroid/blob/master/example/DynamicLinker/module.py)
```
from osv.modules import api

api.require('java')

default = api.run('/java.so -jar /Unikernel-Server.jar')
```
This script specifies that the JVM runtime is required when building unikernel and also specialfies the setup command to run Unikernel-Server.

# Step 3
Create a mapping file [usr.manifest](https://github.com/CGCL-codes/Libdroid/blob/master/example/DynamicLinker/usr.manifest)
```
/Unikernel-Server.jar: ${MODULE_DIR}/Unikernel-Server.jar
/libAndroid/Libdroid.jar: ${MODULE_DIR}/Libdroid.jar
/app_hub/apks/Readme.txt: ${MODULE_DIR}/Readme.txt
/app_hub/resources/Readme.txt: ${MODULE_DIR}/Readme.txt
```
This file is uesed to specify where to place the server programe and the extended Android libraries.

# Step 4
Create a application in OSv app-hub (osv/apps/), and put all of above files into this folder. Run:
```
sudo ./scritpts/build image=DynamickerLinker
```
Then you will get a defualt kvm image (osv/build/usr.img) which is the unikernel image that contains DynamicLinker and Libdroid.
