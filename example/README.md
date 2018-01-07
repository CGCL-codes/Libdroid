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
